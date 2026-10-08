package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.cost.CostConfidence
import com.vidadomu.nexus.agent.core.cost.CostEstimator
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.registry.ModelRegistry
import com.vidadomu.nexus.agent.core.registry.ModelSchema
import com.vidadomu.nexus.agent.core.registry.ModelSummary
import com.vidadomu.nexus.agent.core.schema.ParameterValidator
import com.vidadomu.nexus.agent.core.schema.ValidationIssue
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.MediaRef

/** Medio pedido por el LLM o la UI antes de resolverlo contra el schema. */
data class MediaRequest(val role: String, val uri: String, val mimeType: String?, val assetId: String?)

/** Qué cambiar de un plan (edición desde la tarjeta o update_plan). null = no tocar. */
data class PlanEdit(
    val prompt: String? = null,
    val negativePrompt: String? = null,
    /** Valor null o «–» = quitar el parámetro. */
    val params: Map<String, String?> = emptyMap(),
)

sealed interface PlanBuild {
    data class Ready(val plan: GenerationPlan) : PlanBuild
    data class Invalid(val issues: List<ValidationIssue>) : PlanBuild
}

/**
 * Construye planes de generación SOLO a partir del schema real: valida parámetros, coloca cada
 * medio en el campo que el modelo declara y adjunta la estimación de coste. No envía nada.
 */
class PlanBuilder(
    private val registry: ModelRegistry,
    private val cost: CostEstimator,
    private val assets: AssetIndex,
    private val clock: () -> Long,
    private val adaptPrompt: (suspend (String, ModelSchema, String) -> String)? = null,
) {

    suspend fun build(
        id: String,
        model: ModelSummary,
        prompt: String,
        negativePrompt: String?,
        params: Map<String, String?>,
        media: List<MediaRequest>,
        task: String? = null,
        rationale: String? = null,
        alternatives: List<ModelRef> = emptyList(),
        projectId: String? = null,
        sceneCode: String? = null,
        shotCode: String? = null,
        characters: List<String> = emptyList(),
        parentGenerationId: String? = null,
        originalPrompt: String? = null,
    ): PlanBuild {
        val schema = runCatching { registry.schema(model.ref) }.getOrElse {
            return PlanBuild.Invalid(listOf(ValidationIssue("schema", "Nexus no pudo cargar el schema de ${model.ref.modelId}: ${it.message ?: it.javaClass.simpleName}. Sin schema no se prepara la generación.")))
        }
        val issues = mutableListOf<ValidationIssue>()
        if (schema.promptRequired && prompt.isBlank()) issues += ValidationIssue("prompt", "El modelo exige prompt.")
        val placed = placeMedia(schema, model, media, issues)

        // En Nexus el prompt negativo viaja como parámetro del schema; si el modelo no lo tiene, no se puede enviar.
        val negative = negativePrompt?.trim()?.takeUnless { ParameterValidator.isBlankLike(it) }
        val allParams = if (negative != null) {
            if (schema.param("negative_prompt") == null) {
                issues += ValidationIssue("negative_prompt", "${model.name} no admite prompt negativo: expresa esas restricciones en el prompt.")
                params
            } else params + ("negative_prompt" to (params["negative_prompt"]?.takeUnless { ParameterValidator.isBlankLike(it) } ?: negative))
        } else params
        val v = ParameterValidator.validate(schema, allParams)
        issues += v.issues
        if (issues.any { it.severity == ValidationIssue.Severity.ERROR }) return PlanBuild.Invalid(issues)

        val fittedPrompt = try { adaptPrompt?.invoke(prompt, schema, model.name) ?: prompt.trim() }
        catch (e: Exception) { return PlanBuild.Invalid(listOf(ValidationIssue("prompt", e.message ?: "No se pudo adaptar el prompt; el original se conserva."))) }

        val effective = ParameterValidator.effective(schema, v.normalized)
        val estimate = runCatching { cost.estimate(model, effective) }.getOrNull()
        val warnings = buildList {
            issues.filter { it.severity == ValidationIssue.Severity.WARNING }.forEach { add(it.message) }
            when (estimate?.confidence) {
                null, CostConfidence.UNKNOWN -> add("Sin estimación de coste: el proveedor descontará créditos; revisa el saldo.")
                CostConfidence.BASE_ONLY -> add("Coste orientativo: ${estimate.basis}.")
                else -> Unit
            }
            if (v.dropped.isNotEmpty()) add("Descartados por vacíos: ${v.dropped.joinToString()}.")
        }
        val shotId = if (projectId != null && shotCode != null) {
            assets.snapshot().shots.firstOrNull { it.projectId == projectId && AssetIndex.norm(it.code) == AssetIndex.norm(shotCode) }?.id
        } else null
        return PlanBuild.Ready(
            GenerationPlan(
                id = id,
                ref = model.ref,
                modelName = model.name,
                modality = model.modalities.firstOrNull() ?: "UNKNOWN",
                task = task,
                prompt = fittedPrompt,
                originalPrompt = if (fittedPrompt != prompt.trim()) prompt else originalPrompt,
                negativePrompt = v.normalized["negative_prompt"],
                params = v.normalized,
                effectiveParams = effective,
                media = placed,
                estimate = estimate,
                rationale = rationale,
                alternatives = alternatives,
                projectId = projectId,
                shotId = shotId,
                sceneCode = sceneCode,
                shotCode = shotCode,
                characters = characters,
                parentGenerationId = parentGenerationId,
                warnings = warnings,
                createdAt = clock(),
            ),
        )
    }

    /** Reconstruye un plan editado manteniendo id, medios y contexto. */
    suspend fun rebuild(plan: GenerationPlan, edit: PlanEdit): PlanBuild {
        val model = registry.get(plan.ref) ?: return PlanBuild.Invalid(listOf(ValidationIssue("model", "${plan.ref} ya no está en el registro.")))
        val params: MutableMap<String, String?> = plan.params.toMutableMap()
        if (edit.negativePrompt != null) params.remove("negative_prompt")
        edit.params.forEach { (k, v) -> if (ParameterValidator.isBlankLike(v)) params.remove(k) else params[k] = v }
        return build(
            id = plan.id, model = model,
            prompt = edit.prompt ?: plan.prompt,
            negativePrompt = edit.negativePrompt ?: params["negative_prompt"],
            params = params,
            media = plan.media.map { MediaRequest(it.role, it.uri, it.mimeType, it.assetId) },
            task = plan.task, rationale = plan.rationale, alternatives = plan.alternatives,
            projectId = plan.projectId, sceneCode = plan.sceneCode, shotCode = plan.shotCode,
            characters = plan.characters, parentGenerationId = plan.parentGenerationId,
            originalPrompt = if (edit.prompt == null || edit.prompt == plan.prompt) plan.originalPrompt else null,
        )
    }

    private fun placeMedia(schema: ModelSchema, model: ModelSummary, media: List<MediaRequest>, issues: MutableList<ValidationIssue>): List<MediaRef> {
        val m = schema.media
        if (media.isEmpty()) {
            if (m.mediaRequired) issues += ValidationIssue("media", "${model.name} necesita un archivo de entrada (${m.inputMode ?: "imagen/vídeo"}).")
            return emptyList()
        }
        val out = mutableListOf<MediaRef>()
        val byRole = media.groupBy { it.role }
        byRole.keys.filter { it !in ROLES }.forEach { issues += ValidationIssue("media", "Rol de medio desconocido «$it». Usa: ${ROLES.joinToString()}.") }
        for (single in listOf("first_frame", "last_frame", "video")) {
            if ((byRole[single]?.size ?: 0) > 1) issues += ValidationIssue("media", "Solo puede haber un medio con rol $single.")
        }
        for (r in media) {
            val isVideo = r.mimeType?.startsWith("video/") == true
            val field = when (r.role) {
                "first_frame" -> m.firstFrameField ?: m.inputImageField ?: m.imagesArrayField
                "last_frame" -> m.lastFrameField
                "reference" -> m.imagesArrayField ?: m.inputImageField.takeIf { m.firstFrameField == null }
                "video" -> m.videoField
                else -> continue
            }
            if (field == null) {
                issues += ValidationIssue("media", "${model.name} no admite un medio con rol ${r.role} según su schema.")
                continue
            }
            if (r.role == "video" && !isVideo && r.mimeType != null) issues += ValidationIssue("media", "El rol video requiere un archivo de vídeo.")
            if (r.role != "video" && isVideo) issues += ValidationIssue("media", "Un vídeo no puede ir como ${r.role}.")
            out += MediaRef(r.role, r.uri, r.mimeType, r.assetId, field)
        }
        val images = out.count { it.role != "video" }
        if (m.maxImages > 0 && images > m.maxImages) issues += ValidationIssue("media", "${model.name} admite como máximo ${m.maxImages} imágenes.")
        return out
    }

    companion object {
        val ROLES = listOf("first_frame", "last_frame", "reference", "video")
    }
}
