package com.vidadomu.nexus.agent.core.agent

import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.AssetRole
import com.vidadomu.nexus.agent.core.assets.AssetSource
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.assets.GenerationRecord
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.GuardedExecutor
import com.vidadomu.nexus.agent.core.spend.JobStatus
import com.vidadomu.nexus.agent.core.spend.SpendGuard
import com.vidadomu.nexus.agent.core.spend.SpendViolation
import com.vidadomu.nexus.agent.core.spend.SubmissionUncertain
import com.vidadomu.nexus.agent.core.spend.SubmitResult

/**
 * Del botón GENERAR al asset importado: confirma (ticket de un uso), envía, registra el lineage
 * y, al terminar, importa los resultados asociados al proyecto/plano con su keyframe de origen.
 *
 * [confirmAndSubmit] es para la UI. Ninguna herramienta del LLM tiene acceso a esta clase salvo
 * [sync] (consultar estado), que no gasta.
 */
class GenerationLifecycle(
    private val guard: SpendGuard,
    private val executor: GuardedExecutor,
    private val assets: AssetIndex,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Llamar SOLO desde el toque del usuario en GENERAR del diálogo de confirmación de ese plan.
     * [expectedFingerprint]: huella del plan mostrado en el diálogo (ver SpendGuard.confirmByUser).
     */
    suspend fun confirmAndSubmit(planId: String, expectedFingerprint: String? = null): SubmitResult {
        val plan = guard.plan(planId) ?: throw IllegalArgumentException("Plan desconocido: $planId")
        val ticket = guard.confirmByUser(planId, expectedFingerprint)
        val result = try {
            executor.submit(planId, ticket)
        } catch (e: SubmissionUncertain) {
            guard.markUncertain(planId)   // pudo cobrarse: nunca reabrir GENERAR para este plan
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            guard.markUncertain(planId)
            throw e
        } catch (e: SpendViolation) {
            throw e                        // el guard ya dejó el estado correcto
        } catch (e: Exception) {
            guard.markSubmitFailed(planId)
            throw e
        }
        // El envío ya ocurrió: un fallo del índice local no puede convertirse en «no se envió».
        runCatching { record(guard.plan(planId) ?: plan, result) }
            .onFailure { return result.copy(note = listOfNotNull(result.note, "No se pudo registrar el lineage (${it.javaClass.simpleName}).").joinToString(" ")) }
        return result
    }

    private suspend fun record(plan: GenerationPlan, result: SubmitResult): GenerationRecord {
        val projectId = plan.projectId
        val scene = if (projectId != null && plan.sceneCode != null) assets.ensureScene(projectId, plan.sceneCode) else null
        val shotId = plan.shotId ?: if (projectId != null && plan.shotCode != null) assets.ensureShot(projectId, plan.shotCode, scene?.id).id else null
        val characterIds = plan.characters.map { assets.ensureCharacter(it, projectId).id }
        val refIds = plan.media.map { m ->
            val existing = m.assetId?.let { assets.asset(it) } ?: assets.assetByUri(m.uri)
            if (existing != null) {
                // Completar contexto que faltara, sin pisar lo que ya tenía.
                if (existing.projectId == null && projectId != null) {
                    assets.updateAsset(existing.id) { it.copy(projectId = projectId, sceneId = it.sceneId ?: scene?.id, shotId = it.shotId ?: shotId, characterIds = (it.characterIds + characterIds).distinct()) }
                }
                existing.id
            } else {
                assets.addAsset(
                    Asset(
                        id = "", uri = m.uri, type = typeOfMime(m.mimeType),
                        role = if (m.role == "first_frame" || m.role == "last_frame") AssetRole.KEYFRAME else if (m.role == "video") AssetRole.SOURCE else AssetRole.REFERENCE,
                        source = AssetSource.IMPORTED, displayName = m.uri.substringAfterLast('/').substringAfterLast("%2F").ifBlank { m.role },
                        mimeType = m.mimeType, projectId = projectId, sceneId = scene?.id, shotId = shotId,
                        characterIds = characterIds, createdAt = clock(),
                    ),
                ).id
            }
        }
        return assets.upsertGeneration(
            GenerationRecord(
                id = AssetIndex.newId("gen"),
                nexusJobId = result.jobId,
                providerJobId = result.providerJobId,
                providerId = plan.ref.providerId,
                modelId = plan.ref.modelId,
                modality = plan.modality,
                prompt = plan.prompt,
                negativePrompt = plan.negativePrompt,
                params = plan.params,
                seed = plan.params["seed"],
                referenceAssetIds = refIds,
                referenceUris = plan.media.map { it.uri },
                estimatedCostUsd = plan.estimate?.amountUsd,
                status = "SUBMITTED",
                parentGenerationId = plan.parentGenerationId,
                agentPlanId = plan.id,
                projectId = projectId,
                shotId = shotId,
                createdAt = clock(),
            ),
        )
    }

    /** Consulta el estado y, si ha terminado, importa las salidas (idempotente). */
    suspend fun sync(jobId: String): JobStatus {
        val st = executor.status(jobId)
        val rec = assets.generation(jobId) ?: return st
        val outputIds = if (st.status == "COMPLETED") importOutputs(rec, st.outputs) else emptyList()
        assets.upsertGeneration(
            rec.copy(
                status = st.status,
                actualCostUsd = st.actualCostUsd ?: rec.actualCostUsd,
                outputAssetIds = outputIds,
                completedAt = if (st.status in FINAL_STATES) (rec.completedAt ?: clock()) else rec.completedAt,
            ),
        )
        return st
    }

    private suspend fun importOutputs(rec: GenerationRecord, outputs: List<String>): List<String> {
        val parent = rec.referenceAssetIds.firstOrNull()?.let { assets.asset(it) }
        val shot = rec.shotId?.let { id -> assets.snapshot().shots.firstOrNull { it.id == id } }
        val type = when (rec.modality.uppercase()) { "VIDEO" -> AssetType.VIDEO; "IMAGE" -> AssetType.IMAGE; "AUDIO" -> AssetType.AUDIO; else -> AssetType.OTHER }
        return outputs.map { uri ->
            assets.assetByUri(uri)?.id ?: assets.addAsset(
                Asset(
                    id = "", uri = uri, type = type, role = AssetRole.OUTPUT, source = AssetSource.GENERATED,
                    displayName = buildString {
                        append(shot?.let { "Plano ${it.code}" } ?: rec.modelId.substringAfterLast('/'))
                        append(" · ").append(type.name.lowercase())
                    },
                    projectId = rec.projectId, sceneId = shot?.sceneId ?: parent?.sceneId, shotId = rec.shotId,
                    characterIds = parent?.characterIds.orEmpty(), generationId = rec.id, parentAssetId = parent?.id,
                    createdAt = clock(),
                ),
            ).id
        }
    }

    companion object {
        val FINAL_STATES = setOf("COMPLETED", "FAILED", "CANCELLED")
        fun typeOfMime(mime: String?): AssetType = when {
            mime == null -> AssetType.OTHER
            mime.startsWith("image/") -> AssetType.IMAGE
            mime.startsWith("video/") -> AssetType.VIDEO
            mime.startsWith("audio/") -> AssetType.AUDIO
            else -> AssetType.OTHER
        }
    }
}
