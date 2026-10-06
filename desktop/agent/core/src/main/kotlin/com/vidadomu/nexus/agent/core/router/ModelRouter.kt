package com.vidadomu.nexus.agent.core.router

import com.vidadomu.nexus.agent.core.cost.CostEstimate
import com.vidadomu.nexus.agent.core.cost.CostEstimator
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.registry.ModelRegistry
import com.vidadomu.nexus.agent.core.registry.ModelSchema
import com.vidadomu.nexus.agent.core.registry.ModelSummary
import com.vidadomu.nexus.agent.core.registry.ParamSpec
import com.vidadomu.nexus.agent.core.schema.ParameterValidator
import com.vidadomu.nexus.agent.core.skills.Skill
import com.vidadomu.nexus.agent.core.skills.SkillStore
import kotlin.math.abs
import kotlinx.serialization.Serializable

/** Requisitos extraídos de la intención del usuario (los rellena el LLM con vocabulario cerrado). */
@Serializable
data class TaskSpec(
    val modality: String,
    val task: String,
    val durationSec: Int? = null,
    val resolution: String? = null,
    val aspectRatio: String? = null,
    val imageInputs: Int = 0,
    val needsLastFrame: Boolean = false,
    val needsAudio: Boolean? = null,
    val priorities: List<String> = emptyList(),
    /** low | medium | high */
    val budget: String? = null,
    val maxCostUsd: Double? = null,
    val preferProviders: List<String> = emptyList(),
    val excludeModels: List<String> = emptyList(),
    val limit: Int = 3,
)

@Serializable
data class RouteCandidate(
    val ref: ModelRef,
    val name: String,
    val score: Double,
    val reasons: List<String>,
    val warnings: List<String>,
    val skillId: String?,
    val estimate: CostEstimate?,
    /** Parámetros ya ajustados a opciones reales del schema (duración, resolución, aspecto). */
    val suggestedParams: Map<String, String>,
    val schemaVerified: Boolean,
)

@Serializable
data class RouteResult(
    val task: TaskSpec,
    val recommended: RouteCandidate?,
    val alternatives: List<RouteCandidate>,
    val consideredCount: Int,
    val rejected: Map<String, Int>,
    val notes: List<String>,
)

/**
 * Router determinista. Cruza: intención (TaskSpec) + registro actual de Nexus + schema real +
 * skills + precio. El LLM no elige por intuición: elige entre lo que devuelve esto y lo explica.
 */
class ModelRouter(
    private val registry: ModelRegistry,
    private val skills: SkillStore,
    private val cost: CostEstimator,
    /** Cuántos candidatos se verifican contra su schema real (cada uno puede ser una petición de red). */
    private val schemaChecks: Int = 8,
    /**
     * Familias que el usuario no quiere que se le recomienden (p. ej. «veo», porque lo tiene gratis en otro sitio).
     * Se lee en cada recomendación, así un cambio en Ajustes vale al momento. Coincide con el id de skill o con el
     * inicio de cualquier tramo del model id («veo» → google/veo3.1/…). Si el usuario pide ese modelo por su nombre,
     * prepare_generation lo sigue aceptando: solo deja de proponerse.
     */
    private val excludedFamilies: () -> Set<String> = { emptySet() },
) {

    suspend fun route(task: TaskSpec): RouteResult {
        val all = registry.list()
        val rejected = linkedMapOf<String, Int>()
        fun reject(reason: String) { rejected[reason] = (rejected[reason] ?: 0) + 1 }

        val modality = task.modality.uppercase()
        val excluded = excludedFamilies().map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val pre = all.filter { m ->
            when {
                modality !in m.modalities -> { reject("otra modalidad"); false }
                isExcludedFamily(m, excluded) -> { reject("familia excluida en tus ajustes"); false }
                m.ref.modelId in task.excludeModels -> { reject("excluido por el usuario"); false }
                !supportsTask(m, task.task) -> { reject("no admite la tarea ${task.task}"); false }
                task.needsLastFrame && !m.supportsLastFrame -> { reject("sin último fotograma"); false }
                task.imageInputs > 1 && m.maxImages in 1 until task.imageInputs -> { reject("admite menos imágenes de las adjuntas"); false }
                task.imageInputs > 0 && task.task in TEXT_ONLY_TASKS -> { reject("tarea solo texto"); false }
                else -> true
            }
        }

        // Generación más reciente de cada familia presente en el catálogo filtrado (p. ej. Seedance 2.5 frente a v1.5).
        val latest = pre.mapNotNull { m -> generationOf(m.ref.modelId)?.let { g -> familyKeyOf(m) to g } }
            .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.max() }
        val scored = pre.map { m -> preliminary(m, task, latest) }.sortedByDescending { it.score }
        // Verificar con el schema real a los mejores, intentando cubrir familias distintas.
        val toCheck = diversify(scored, schemaChecks)
        val checked = toCheck.mapNotNull { c -> verify(c, task)?.also { } ?: run { reject("schema incompatible"); null } }
            .let { list -> applyCost(list, task) { reject(it) } }
            .sortedByDescending { it.score }

        val recommended = checked.firstOrNull()
        val alternatives = buildList {
            if (recommended != null) {
                val rest = checked.drop(1)
                val otherFamilies = rest.filter { familyKey(it) != familyKey(recommended) }
                    .distinctBy { familyKey(it) }
                addAll(otherFamilies.take((task.limit - 1).coerceAtLeast(0)))
                if (size < task.limit - 1) addAll(rest.filter { it !in this }.take(task.limit - 1 - size))
            }
        }
        val notes = buildList {
            if (pre.isEmpty()) add("Ningún modelo del registro actual de Nexus cumple la tarea; revisa modalidad o adjuntos.")
            if (checked.any { !it.schemaVerified }) add("Algún candidato no pudo verificarse contra su schema; Nexus lo validará al preparar.")
            if (skills.models().isEmpty()) add("No hay skills de modelo cargadas: la elección se basa solo en el registro.")
        }
        return RouteResult(task, recommended, alternatives, pre.size, rejected, notes)
    }

    // ---- filtros por tarea con datos del registro de Nexus ----

    private fun supportsTask(m: ModelSummary, task: String): Boolean {
        val caps = m.capabilities
        val id = m.ref.modelId.lowercase()
        return when (task) {
            "text_to_video" -> "TEXT_TO_VIDEO" in caps
            "image_to_video" -> "IMAGE_TO_VIDEO" in caps && (m.supportsFirstFrame || m.supportsImageInput)
            "reference_to_video" -> "REFERENCE_TO_VIDEO" in caps || (m.supportsReferenceImages && "VIDEO" in m.modalities)
            "start_end_frame" -> m.supportsFirstFrame && m.supportsLastFrame
            "video_edit" -> "VIDEO_EDIT" in caps || "VIDEO_TO_VIDEO" in caps
            "video_extend" -> "VIDEO_EXTEND" in caps || "extend" in id
            "motion_control" -> MOTION.containsMatchIn(id) || skills.forModel(m.ref.modelId)?.idealTasks?.contains("motion_control") == true && m.supportsVideoInput
            "lipsync", "avatar" -> "AUDIO_TO_VIDEO" in caps
            "text_to_image" -> "TEXT_TO_IMAGE" in caps
            "image_edit" -> "IMAGE_EDITING" in caps || "IMAGE_TO_IMAGE" in caps
            "multi_reference_image" -> ("IMAGE_EDITING" in caps || "IMAGE_TO_IMAGE" in caps) && (m.maxImages == 0 || m.maxImages > 1)
            "upscale" -> "upscal" in id
            "layer_decomposition" -> "layer" in id
            else -> false
        }
    }

    // ---- puntuación preliminar (sin red) ----

    private data class Draft(val m: ModelSummary, val skill: Skill?, val score: Double, val reasons: List<String>, val warnings: List<String>)

    private fun preliminary(m: ModelSummary, task: TaskSpec, latest: Map<String, Double> = emptyMap()): Draft {
        var score = 0.0
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val skill = skills.forModel(m.ref.modelId)
        if (skill == null) {
            score -= 1.0
            warnings += "Sin skill para esta familia: criterio editorial limitado."
        } else {
            for (p in task.priorities) {
                if (p in skill.strengths) {
                    val verified = skill.isVerified(p)
                    score += if (verified) 3.0 else 1.5
                    reasons += "Skill ${skill.id}: fuerte en ${label(p)}" + if (verified) "" else " [sin verificar]"
                }
                if (p in skill.weaknesses) {
                    score -= 3.0
                    warnings += "Skill ${skill.id}: débil en ${label(p)}"
                }
            }
            if (task.task in skill.idealTasks) { score += 2.0; reasons += "Skill ${skill.id}: tarea ideal (${task.task})" }
            if (task.task in skill.avoidTasks) { score -= 4.0; warnings += "Skill ${skill.id}: desaconsejado para ${task.task}" }
        }
        val id = m.ref.modelId.lowercase()
        val wantsSpeedOrSaving = "fast" in task.priorities || "low_cost" in task.priorities || task.budget == "low"
        val wantsQuality = task.priorities.any { it in QUALITY_TRAITS }
        if (CHEAP_TIER.containsMatchIn(id)) {
            if (wantsSpeedOrSaving) { score += 1.0; reasons += "Variante rápida/económica" } else if (wantsQuality) score -= 1.0
        }
        if (TOP_TIER.containsMatchIn(id) && wantsQuality) { score += 0.75; reasons += "Variante de máxima calidad" }
        if (m.isFlagship) score += 0.5
        if (m.ref.providerId in task.preferProviders) { score += 1.0; reasons += "Proveedor preferido" }
        m.releaseMonth?.let { if (it >= RECENT_CUTOFF) score += 0.5 }
        // La generación pesa más que el sufijo «pro»/«max»: una versión vieja «pro» no debe ganar a la actual.
        val gen = generationOf(m.ref.modelId)
        val newest = latest[familyKeyOf(m)]
        if (gen != null && newest != null) {
            if (gen < newest) {
                score -= OLD_GENERATION_PENALTY
                warnings += "Generación anterior (${fmt(gen)}); en el catálogo hay ${fmt(newest)}"
            } else reasons += "Generación más reciente de su familia (${fmt(gen)})"
        }
        return Draft(m, skill, score, reasons, warnings)
    }

    private fun diversify(list: List<Draft>, n: Int): List<Draft> {
        val out = mutableListOf<Draft>()
        val seen = mutableSetOf<String>()
        for (d in list) { if (out.size >= n) break; if (seen.add(d.skill?.id ?: familyOf(d.m))) out += d }
        for (d in list) { if (out.size >= n) break; if (d !in out) out += d }
        return out
    }

    // ---- verificación con el schema real ----

    private suspend fun verify(d: Draft, task: TaskSpec): RouteCandidate? {
        val schema = runCatching { registry.schema(d.m.ref) }.getOrNull()
        val reasons = d.reasons.toMutableList()
        val warnings = d.warnings.toMutableList()
        var score = d.score
        val params = linkedMapOf<String, String>()
        if (schema == null) {
            warnings += "No se pudo cargar el schema; parámetros sin verificar."
            return RouteCandidate(d.m.ref, d.m.name, score - 2.0, reasons, warnings, d.skill?.id, null, params, false)
        }
        if (schema.unsupportedRequired.isNotEmpty()) return null

        task.durationSec?.let { want ->
            val p = durationParam(schema)
            if (p == null) {
                score -= 1.0; warnings += "El schema no permite elegir duración."
            } else {
                val pick = pickNumeric(p, want.toDouble())
                if (pick == null) return null
                params[p.key] = pick.first
                if (pick.second) { score += 1.0; reasons += "Duración ${pick.first} s admitida por el schema" }
                else { score -= 1.5; warnings += "Duración ajustada a ${pick.first} s (el schema no admite $want s)" }
            }
        }
        task.resolution?.let { want ->
            val p = schema.parameters.firstOrNull { it.uiType == "RESOLUTION" || it.key in RESOLUTION_KEYS }
            val opt = p?.options?.firstOrNull { it.equals(want, true) } ?: p?.options?.firstOrNull { it.contains(want.trimEnd('p', 'P'), true) }
            if (p != null && opt != null) params[p.key] = opt
            else if (p != null && p.options.isNotEmpty()) warnings += "Resolución $want no disponible; opciones: ${p.options.joinToString()}"
        }
        task.aspectRatio?.let { want ->
            val p = schema.parameters.firstOrNull { it.uiType == "ASPECT_RATIO" || it.key in ASPECT_KEYS }
            val opt = p?.options?.firstOrNull { it == want }
            if (p != null && opt != null) params[p.key] = opt
            else if (p != null && p.options.isNotEmpty()) warnings += "Relación $want no disponible; opciones: ${p.options.joinToString()}"
        }
        task.needsAudio?.let { want ->
            val p = schema.parameters.firstOrNull { it.key in AUDIO_KEYS && (it.nativeType ?: it.uiType).equals("boolean", true) }
            when {
                p != null -> params[p.key] = want.toString()
                want && !d.m.supportsAudio -> { score -= 1.5; warnings += "Nexus no indica audio para este modelo." }
            }
        }
        // Lo que sugerimos debe pasar el mismo validador que usará la preparación.
        val v = ParameterValidator.validate(schema, params)
        if (!v.ok) return null
        return RouteCandidate(d.m.ref, d.m.name, score, reasons, warnings, d.skill?.id, null, v.normalized, true)
    }

    private suspend fun applyCost(list: List<RouteCandidate>, task: TaskSpec, reject: (String) -> Unit): List<RouteCandidate> {
        val withCost = list.map { c ->
            val m = registry.get(c.ref)!!
            val schema = runCatching { registry.schema(c.ref) }.getOrNull()
            val effective = if (schema != null) ParameterValidator.effective(schema, c.suggestedParams) else c.suggestedParams
            c.copy(estimate = runCatching { cost.estimate(m, effective) }.getOrNull())
        }.filter { c ->
            val amount = c.estimate?.amountUsd
            val over = task.maxCostUsd != null && amount != null && amount > task.maxCostUsd
            if (over) reject("supera el coste máximo")
            !over
        }
        val known = withCost.mapNotNull { it.estimate?.amountUsd }
        if (known.isEmpty()) return withCost
        val min = known.min(); val max = known.max()
        val weight = when (task.budget) { "low" -> 3.0; "medium" -> 1.5; "high" -> 0.3; else -> 0.8 }
        return withCost.map { c ->
            val a = c.estimate?.amountUsd
            if (a == null) {
                c.copy(score = c.score - if (task.budget == "low") 1.0 else 0.4, warnings = c.warnings + "Coste no calculable con los datos de Nexus.")
            } else {
                val rel = if (max > min) (a - min) / (max - min) else 0.0
                val r = if (rel < 0.15) c.reasons + "Entre los más económicos (${"%.2f".format(java.util.Locale.ROOT, a)} $)" else c.reasons
                c.copy(score = c.score - weight * rel, reasons = r)
            }
        }
    }

    private fun durationParam(s: ModelSchema): ParamSpec? =
        s.parameters.firstOrNull { it.key == "duration" } ?: s.parameters.firstOrNull { it.uiType == "DURATION" }

    /** (valor elegido, exacto). Con opciones elige la más cercana; con rango, recorta. */
    private fun pickNumeric(p: ParamSpec, want: Double): Pair<String, Boolean>? {
        if (p.options.isNotEmpty()) {
            val nums = p.options.mapNotNull { o -> o.trim().removeSuffix("s").toDoubleOrNull()?.let { o to it } }
            if (nums.isEmpty()) return null
            val best = nums.minBy { abs(it.second - want) }
            return best.first to (best.second == want)
        }
        val lo = p.min; val hi = p.max
        if (lo == null && hi == null) return fmt(want) to true
        val clamped = want.coerceIn(lo ?: want, hi ?: want)
        return fmt(clamped) to (clamped == want)
    }

    private fun familyKey(c: RouteCandidate) = c.skillId ?: c.ref.modelId.split('/').take(2).joinToString("/")
    private fun familyOf(m: ModelSummary) = m.ref.modelId.split('/').take(2).joinToString("/")
    private fun familyKeyOf(m: ModelSummary) = skills.forModel(m.ref.modelId)?.id ?: familyOf(m)
    private fun isExcludedFamily(m: ModelSummary, excluded: Set<String>): Boolean {
        if (excluded.isEmpty()) return false
        if (skills.forModel(m.ref.modelId)?.id?.lowercase() in excluded) return true
        val segments = m.ref.modelId.lowercase().split('/')
        return excluded.any { fam -> segments.any { it.startsWith(fam) } }
    }
    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
    private fun label(tag: String) = TRAIT_LABELS[tag] ?: tag

    companion object {
        private val TEXT_ONLY_TASKS = setOf("text_to_video", "text_to_image")
        private val MOTION = Regex("motion[-_]?control", RegexOption.IGNORE_CASE)
        private val CHEAP_TIER = Regex("(-|_|/)(fast|turbo|lite|mini|flash|developer|std|standard|schnell)(\\b|/|-|_|$)")
        private val TOP_TIER = Regex("(-|_|/)(pro|max|master|ultra|quality)(\\b|/|-|_|$)")
        private val QUALITY_TRAITS = setOf("face_consistency", "identity_preservation", "subtle_performance", "photorealism", "cinematic_look", "natural_motion", "prompt_adherence")
        private val RESOLUTION_KEYS = setOf("resolution", "quality_resolution")
        private val ASPECT_KEYS = setOf("aspect_ratio", "ratio")
        private val AUDIO_KEYS = setOf("sound", "generate_audio", "audio", "enable_audio", "with_audio")
        const val RECENT_CUTOFF = "2026-04"
        /** Mayor que «pro» (+0,75) + flagship (+0,5) + reciente (+0,5) juntos. */
        const val OLD_GENERATION_PENALTY = 2.5
        // Primer número de versión del slug del modelo: «seedance-v1.5-pro» → 1.5, «seedance-2.5» → 2.5,
        // «veo3.1» → 3.1, «kling-video-o3-pro» → 3. Ignora resoluciones (720p) y marcas tipo i2v/t2v.
        private val GENERATION = Regex("(?<![0-9.])v?(\\d{1,2}(?:\\.\\d{1,2})?)(?![0-9]|p(?:$|[-_/])|v(?:$|[-_/]))")
        fun generationOf(modelId: String): Double? {
            val slug = modelId.split('/').let { if (it.size >= 2) it[1] else it[0] }.lowercase()
            return GENERATION.find(slug)?.groupValues?.get(1)?.toDoubleOrNull()
        }
        val TRAIT_LABELS = mapOf(
            "face_consistency" to "continuidad facial", "identity_preservation" to "preservar identidad",
            "subtle_performance" to "interpretación sutil", "natural_motion" to "movimiento natural",
            "dynamic_action" to "acción dinámica", "camera_control" to "control de cámara",
            "cinematic_look" to "look cinematográfico", "prompt_adherence" to "fidelidad al prompt",
            "text_rendering" to "texto en imagen", "photorealism" to "fotorrealismo", "native_audio" to "audio nativo",
            "multi_shot" to "multiplano", "long_duration" to "duración larga", "fast" to "rapidez", "low_cost" to "bajo coste",
            "high_resolution" to "alta resolución", "physics" to "física", "multi_character" to "varios personajes",
        )
    }
}
