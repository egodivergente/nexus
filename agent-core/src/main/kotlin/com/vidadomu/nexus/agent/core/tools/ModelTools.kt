package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.registry.ModelSummary
import com.vidadomu.nexus.agent.core.router.TaskSpec
import com.vidadomu.nexus.agent.core.schema.ParameterValidator
import com.vidadomu.nexus.agent.core.skills.Skill
import com.vidadomu.nexus.agent.core.skills.SkillKind
import com.vidadomu.nexus.agent.core.skills.TASK_TEMPLATE_KEYS
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun strings(values: Collection<String>) = JsonArray(values.map { JsonPrimitive(it) })

internal fun ModelSummary.compact(skill: Skill?): JsonObject = buildJsonObject {
    put("provider_id", ref.providerId)
    put("model_id", ref.modelId)
    put("name", name)
    put("modalities", strings(modalities))
    if (capabilities.isNotEmpty()) put("capabilities", strings(capabilities))
    inputMode?.let { put("input_mode", it) }
    val inputs = buildList {
        if (supportsFirstFrame) add("first_frame")
        if (supportsLastFrame) add("last_frame")
        if (supportsReferenceImages) add("reference_images")
        if (supportsVideoInput) add("video")
    }
    if (inputs.isNotEmpty()) put("inputs", strings(inputs))
    if (supportsAudio) put("audio", true)
    releaseMonth?.let { put("release", it) }
    if (isFlagship) put("flagship", true)
    skill?.let { put("skill", it.id) }
    if (com.vidadomu.nexus.agent.core.cost.TieredTariffs.covers(ref.modelId)) {
        // El precio base del catálogo es solo el nivel más bajo: nunca lo presentes como «el» precio.
        put("price_note", com.vidadomu.nexus.agent.core.cost.TieredTariffs.summary(ref.modelId))
    } else basePriceUsd?.let { put("base_price_usd", it); priceUnit?.let { u -> put("price_unit", u + if (priceUnitVerified) "" else " (sin verificar)") } }
}

private val MODALITIES = listOf("VIDEO", "IMAGE", "AUDIO", "CHAT", "THREE_D")
private val TASKS = listOf(
    "text_to_video", "image_to_video", "reference_to_video", "start_end_frame", "video_edit", "video_extend",
    "motion_control", "lipsync", "avatar", "text_to_image", "image_edit", "multi_reference_image", "upscale", "layer_decomposition",
)
private val TRAITS = listOf(
    "face_consistency", "identity_preservation", "subtle_performance", "natural_motion", "dynamic_action", "camera_control",
    "cinematic_look", "prompt_adherence", "text_rendering", "photorealism", "native_audio", "multi_shot", "long_duration",
    "fast", "low_cost", "high_resolution", "physics", "multi_character",
)

private fun SchemaBuilder.modelRef() {
    string("model_id", "Id exacto del modelo tal como lo devuelve el registro.", required = true)
    string("provider_id", "Proveedor exacto del registro actual. Obligatorio si el id existe en varios proveedores.")
}

class ListModelsTool(private val d: ToolDeps) : BaseTool(
    "list_models", ToolCategory.READ,
    "Lista modelos del registro ACTUAL de Nexus (los únicos que se pueden usar). Filtra por modalidad, capacidad o proveedor. Paginado.",
    schema {
        string("modality", "Modalidad.", enum = MODALITIES)
        string("capability", "Capacidad del registro, p. ej. IMAGE_TO_VIDEO, TEXT_TO_IMAGE, IMAGE_EDITING, REFERENCE_TO_VIDEO.")
        string("provider_id", "Proveedor exacto del registro actual, incluido cualquier proveedor conectado localmente.")
        integer("offset", "Desde qué posición (0 por defecto).")
        integer("limit", "Cuántos; respeta el límite configurable del usuario. 0 = todos.")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val modality = input.str("modality")?.uppercase()
        val cap = input.str("capability")?.uppercase()
        val prov = input.str("provider_id")
        val all = d.registry.list().filter { m ->
            (modality == null || modality in m.modalities) && (cap == null || cap in m.capabilities) && (prov == null || m.ref.providerId == prov)
        }.sortedWith(compareByDescending<ModelSummary> { it.releaseMonth ?: "" }.thenBy { it.ref.modelId })
        val offset = (input.int("offset") ?: 0).coerceAtLeast(0)
        val limit = d.limits().queryLimit(input.int("limit"),20)
        return ok {
            put("total", all.size)
            put("offset", offset)
            put("items", JsonArray(all.drop(offset).take(limit).map { it.compact(d.skills.forModel(it.ref.modelId)) }))
        }
    }
}

class SearchModelsTool(private val d: ToolDeps) : BaseTool(
    "search_models", ToolCategory.READ,
    "Busca modelos del registro de Nexus por texto (nombre, familia, organización, propósito). Úsalo para comprobar que un modelo existe antes de proponerlo.",
    schema {
        string("query", "Texto, p. ej. «kling o3 image to video» o «seedance».", required = true)
        string("modality", "Restringe a una modalidad.", enum = MODALITIES)
        integer("limit", "Cantidad, con el límite configurable del usuario; 0 = todos.")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val words = AssetIndex.norm(input.req("query")).split(' ').filter { it.isNotBlank() }
        val modality = input.str("modality")?.uppercase()
        val limit = d.limits().queryLimit(input.int("limit"),10)
        val hits = d.registry.list().asSequence()
            .filter { modality == null || modality in it.modalities }
            .map { m ->
                val hay = AssetIndex.norm(listOfNotNull(m.ref.modelId, m.name, m.organization, m.family, m.purpose, m.strength, m.capabilities.joinToString(" ")).joinToString(" "))
                val idHay = AssetIndex.norm(m.ref.modelId + " " + m.name)
                m to words.sumOf { w -> (if (idHay.contains(w)) 2 else 0) + (if (hay.contains(w)) 1 else 0).toInt() }
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<ModelSummary, Int>> { it.second }.thenByDescending { it.first.releaseMonth ?: "" })
            .take(limit).toList()
        return ok {
            put("count", hits.size)
            if (com.vidadomu.nexus.agent.core.agent.ExternalServices.mentionsFlow(input.req("query"))) put("external_service", buildJsonObject {
                put("name", "Google Flow")
                put("generation_available_in_nexus", true)
                put("requires_explicit_confirmation", false)
                put("authentication_status", "check_in_app")
                put("details", com.vidadomu.nexus.agent.core.agent.ExternalServices.FLOW_INFO)
            })
            put("items", JsonArray(hits.map { it.first.compact(d.skills.forModel(it.first.ref.modelId)) }))
            if (hits.isEmpty()) put("note", "Ningún modelo del registro coincide. No propongas modelos que no estén aquí.")
        }
    }
}

class GetModelTool(private val d: ToolDeps) : BaseTool(
    "get_model", ToolCategory.READ,
    "Ficha de un modelo del registro + resumen de su skill (fortalezas, debilidades, tareas ideales, rasgos sin verificar).",
    schema { modelRef() },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val m = d.resolveModel(input)
        val skill = d.skills.forModel(m.ref.modelId)
        return ok {
            put("model", m.toJson())
            if (skill != null) put("skill", buildJsonObject {
                put("id", skill.id); put("title", skill.title); put("version", skill.version)
                put("strengths", strings(skill.strengths)); put("weaknesses", strings(skill.weaknesses))
                put("ideal_tasks", strings(skill.idealTasks)); put("avoid_tasks", strings(skill.avoidTasks))
                put("unverified", strings(skill.unverifiedTags))
            }) else put("skill", "Sin skill para esta familia.")
            put("note", "Duraciones y resoluciones del catálogo son orientativas: manda el schema (get_model_schema).")
        }
    }
}

class GetModelSchemaTool(private val d: ToolDeps) : BaseTool(
    "get_model_schema", ToolCategory.READ,
    "Schema REAL de parámetros y medios del modelo (el mismo que usa el formulario de Nexus). Es la única fuente válida de parámetros.",
    schema { modelRef() },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val m = d.resolveModel(input)
        val s = runCatching { d.registry.schema(m.ref) }.getOrElse { return fail("No se pudo cargar el schema: ${it.message ?: it.javaClass.simpleName}") }
        return ok(s.toJson())
    }
}

class RecommendModelsTool(private val d: ToolDeps) : BaseTool(
    "recommend_models", ToolCategory.READ,
    "Router de modelos: cruza la tarea con el registro actual, el schema real, las skills y el precio. Devuelve recomendado + alternativas con motivos, avisos, parámetros ajustados al schema y coste estimado. Úsalo siempre antes de elegir modelo.",
    schema {
        string("modality", "Modalidad de salida.", required = true, enum = MODALITIES)
        string("task", "Tarea.", required = true, enum = TASKS)
        integer("duration_sec", "Duración deseada en segundos (vídeo/audio).")
        string("resolution", "p. ej. 720p, 1080p, 4k.")
        string("aspect_ratio", "p. ej. 16:9, 9:16, 1:1, 2.39:1.")
        integer("image_inputs", "Cuántas imágenes se van a enviar como entrada.")
        boolean("needs_last_frame", "Si hay fotograma final.")
        boolean("needs_audio", "true si quiere audio nativo; false si lo quiere sin audio.")
        stringArray("priorities", "Rasgos prioritarios. Valores: ${TRAITS.joinToString()}.")
        string("budget", "Sensibilidad al coste.", enum = listOf("low", "medium", "high"))
        number("max_cost_usd", "Coste máximo por generación.")
        stringArray("prefer_providers", "Proveedores preferidos.")
        stringArray("exclude_models", "Ids a excluir.")
        integer("limit", "Candidatos totales, según el límite del usuario. 0 = todos.")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val spec = TaskSpec(
            modality = input.req("modality").uppercase(),
            task = input.req("task").lowercase(),
            durationSec = input.int("duration_sec"),
            resolution = input.str("resolution"),
            aspectRatio = input.str("aspect_ratio"),
            imageInputs = input.int("image_inputs") ?: session.attachments.count { it.mimeType.startsWith("image/") },
            needsLastFrame = input.bool("needs_last_frame") ?: false,
            needsAudio = input.bool("needs_audio"),
            priorities = input.strList("priorities").filter { it in TRAITS },
            budget = input.str("budget"),
            maxCostUsd = input.dbl("max_cost_usd"),
            preferProviders = input.strList("prefer_providers"),
            excludeModels = input.strList("exclude_models"),
            limit = d.limits().boundedCount(input.int("limit"),3,d.limits().maxRecommendations),
        )
        if (spec.task !in TASKS) throw ToolInputException("Tarea desconocida «${spec.task}».")
        return ok(d.router.route(spec).toJson())
    }
}

class ValidateParametersTool(private val d: ToolDeps) : BaseTool(
    "validate_parameters", ToolCategory.READ,
    "Valida parámetros contra el schema real. Devuelve los normalizados, los errores y los efectivos (con defaults). No inventes claves: solo las del schema.",
    schema {
        modelRef()
        freeObject("params", "Parámetros clave→valor (sin prompt ni medios).", required = true)
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val m = d.resolveModel(input)
        val s = runCatching { d.registry.schema(m.ref) }.getOrElse { return fail("No se pudo cargar el schema: ${it.message}") }
        val v = ParameterValidator.validate(s, input.paramMap("params"))
        return ok {
            put("ok", v.ok)
            put("normalized", v.normalized.toJson())
            put("issues", v.issues.toJson())
            if (v.dropped.isNotEmpty()) put("dropped", strings(v.dropped))
            put("effective", ParameterValidator.effective(s, v.normalized).toJson())
        }
    }
}

class EstimateCostTool(private val d: ToolDeps) : BaseTool(
    "estimate_generation_cost", ToolCategory.READ,
    "Estima el coste de UNA generación con esos parámetros, indicando la confianza de la estimación y su base.",
    schema {
        modelRef()
        freeObject("params", "Parámetros del modelo.")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val m = d.resolveModel(input)
        val s = runCatching { d.registry.schema(m.ref) }.getOrNull()
        val raw = input.paramMap("params")
        val effective = if (s != null) {
            val v = ParameterValidator.validate(s, raw)
            if (!v.ok) return fail("Parámetros no válidos.") { put("issues", v.issues.toJson()) }
            ParameterValidator.effective(s, v.normalized)
        } else raw.filterValues { !ParameterValidator.isBlankLike(it) }.mapValues { it.value!! }
        val estimate = d.cost.estimate(m, effective)
        return ok {
            put("estimate", estimate.toJson())
            put("effective_params", effective.toJson())
        }
    }
}

class ListSkillsTool(private val d: ToolDeps) : BaseTool(
    "list_skills", ToolCategory.READ,
    "Índice de skills disponibles (expertos y modelos) con versión y fecha.",
    schema { },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome = ok {
        put("skills", JsonArray(d.skills.all().map { s ->
            buildJsonObject { put("id", s.id); put("kind", s.kind.name.lowercase()); put("title", s.title); put("version", s.version); s.updated?.let { put("updated", it) }; put("summary", s.oneLiner()) }
        }))
        if (d.skills.errors.isNotEmpty()) put("load_errors", strings(d.skills.errors))
    }
}

class LoadSkillTool(private val d: ToolDeps) : BaseTool(
    "load_skill", ToolCategory.READ,
    "Carga el contenido de una skill (o una sección). Hazlo antes de escribir el prompt para un modelo concreto.",
    schema {
        string("skill_id", "Id de la skill (p. ej. kling, prompt_engineer).", required = true)
        string("section", "Título de sección (## …) para cargar solo esa parte, p. ej. «Estructura de prompt».")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val id = input.req("skill_id")
        val s = d.skills.get(id) ?: return fail("Skill desconocida «$id». Disponibles: ${d.skills.all().joinToString { it.id }}.")
        val section = input.str("section")
        val text = if (section != null) s.section(section) ?: return fail("La skill $id no tiene sección «$section».") else s.body
        return ok {
            put("id", s.id); put("version", s.version); s.updated?.let { put("updated", it) }
            put("content", if (text.length > MAX_SKILL_CHARS) text.take(MAX_SKILL_CHARS) + "\n…(recortado; pide una sección)" else text)
            if (s.unverifiedTags.isNotEmpty()) put("unverified", strings(s.unverifiedTags))
            put("sources", JsonArray(s.sources.map { JsonPrimitive(it.url) }))
        }
    }

    companion object { const val MAX_SKILL_CHARS = 40_000 }
}

/**
 * Revisión del prompt para un modelo concreto. El LLM escribe el prompt; esta herramienta aporta
 * la guía de la skill del modelo y avisa de lo que debe ir en parámetros y no en el texto.
 */
class CreateGenerationPromptTool(private val d: ToolDeps) : BaseTool(
    "create_generation_prompt", ToolCategory.READ,
    "Revisa un borrador de prompt para un modelo concreto: devuelve la estructura recomendada por su skill y avisos (duración, relación de aspecto o resolución escritas en el texto, longitud, prompt vacío). Úsalo antes de prepare_generation.",
    schema {
        modelRef()
        string("prompt", "Borrador del prompt en el idioma que mejor siga el modelo.", required = true)
        string("negative_prompt", "Prompt negativo, si el schema lo admite.")
        string("task", "Tarea (image_to_video…).")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val m = d.resolveModel(input)
        val prompt = input.req("prompt")
        val skill = d.skills.forModel(m.ref.modelId)
        val schema = runCatching { d.registry.schema(m.ref) }.getOrNull()
        val warnings = buildList {
            if (prompt.length > 2_500) add("Prompt largo (${prompt.length} caracteres): muchos modelos ignoran el final; condensa.")
            if (prompt.length < 40) add("Prompt muy corto: describe sujeto, acción, cámara, luz y estilo.")
            if (DURATION_IN_TEXT.containsMatchIn(prompt)) add("La duración va en el parámetro de duración, no en el texto.")
            if (RATIO_IN_TEXT.containsMatchIn(prompt)) add("La relación de aspecto va en su parámetro, no en el texto.")
            if (RES_IN_TEXT.containsMatchIn(prompt)) add("La resolución va en su parámetro, no en el texto.")
            val neg = input.str("negative_prompt")
            if (neg != null && schema != null && schema.param("negative_prompt") == null) add("Este modelo no tiene negative_prompt en su schema: incorpora las restricciones al prompt en positivo.")
            if (input.str("task") == "image_to_video") add("En image-to-video describe el MOVIMIENTO y la cámara; no vuelvas a describir la apariencia de los personajes (la aporta el keyframe) para no alterar identidades.")
        }
        return ok {
            put("prompt", prompt)
            input.str("negative_prompt")?.let { put("negative_prompt", it) }
            put("warnings", strings(warnings))
            if (skill != null) {
                put("skill", skill.id)
                (skill.section("Estructura de prompt") ?: skill.section("Recomendaciones de prompting"))?.let { put("structure", it.take(6_000)) }
                val task = input.str("task") ?: guessTask(m)
                val template = task?.let { tk -> TASK_TEMPLATE_KEYS[tk]?.let { keys -> skill.subsection("Plantillas por tarea", keys) } }
                if (template != null) put("task_template", template.take(6_000))
                else skill.section("Plantillas por tarea")?.let { put("task_templates", it.take(8_000)) }
                skill.section("Idioma")?.let { put("language_and_negative", it.take(2_500)) }
                skill.section("Errores habituales")?.let { put("common_mistakes", it.take(2_500)) }
            } else put("skill", "Sin skill de modelo: usa prompt_engineer.")
        }
    }

    private fun guessTask(m: ModelSummary): String? {
        val id = m.ref.modelId.lowercase()
        return when {
            Regex("motion[-_]?control").containsMatchIn(id) -> "motion_control"
            Regex("start[-_]?end|first[-_]?last|keyframes").containsMatchIn(id) -> "start_end_frame"
            Regex("reference-to-video|/ref\\b").containsMatchIn(id) -> "reference_to_video"
            Regex("video-edit|edit-video|/v2v|video-to-video").containsMatchIn(id) -> "video_edit"
            Regex("extend").containsMatchIn(id) -> "video_extend"
            Regex("avatar|lipsync|infinitetalk|omni-human").containsMatchIn(id) -> "avatar"
            Regex("image-to-video|/i2v|-i2v").containsMatchIn(id) -> "image_to_video"
            Regex("text-to-video|/t2v|-t2v").containsMatchIn(id) -> "text_to_video"
            Regex("sequential|reference-to-image|blend").containsMatchIn(id) -> "multi_reference_image"
            Regex("/edit|image-edit").containsMatchIn(id) -> "image_edit"
            "IMAGE" in m.modalities -> "text_to_image"
            else -> null
        }
    }

    companion object {
        private val DURATION_IN_TEXT = Regex("\\b\\d{1,2}\\s?(s|sec|secs|seconds|segundos)\\b", RegexOption.IGNORE_CASE)
        private val RATIO_IN_TEXT = Regex("\\b(16:9|9:16|4:3|3:4|21:9|1:1|2\\.39:1)\\b")
        private val RES_IN_TEXT = Regex("\\b(480p|720p|1080p|2160p|4k)\\b", RegexOption.IGNORE_CASE)
    }
}

/**
 * Comparativa editorial entre familias (skills de modelo): rasgos, tareas y la sección
 * «Cuándo elegirlo y cuándo no» de cada una, para justificar una recomendación frente a otras.
 */
class CompareModelFamiliesTool(private val d: ToolDeps) : BaseTool(
    "compare_model_families", ToolCategory.READ,
    "Compara familias de modelos (ids de skill de modelo, p. ej. kling, seedance, wan) con su criterio editorial: rasgos fuertes/débiles, tareas ideales, cuándo elegir cada una y su plantilla para la tarea. Úsalo para explicar POR QUÉ recomiendas un generador frente a otros; para disponibilidad y precio usa recommend_models.",
    schema {
        stringArray("families", "Ids de skill de modelo a comparar (2-6). Vacío = todas las de la modalidad.")
        string("modality", "VIDEO o IMAGE, si no das familias.")
        string("task", "Tarea (text_to_video, image_to_video, text_to_image…) para incluir la plantilla de cada una.")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val wanted = input.strList("families").map { it.lowercase() }
        val modality = input.str("modality")?.uppercase()
        val task = input.str("task")
        val unknown = wanted.filter { d.skills.get(it)?.kind != SkillKind.MODEL }
        if (unknown.isNotEmpty()) return fail("Familias desconocidas: ${unknown.joinToString()}. Disponibles: ${d.skills.models().joinToString { it.id }}.")
        val list = if (wanted.isNotEmpty()) wanted.mapNotNull { d.skills.get(it) }
        else d.skills.models().filter { modality == null || modality in it.modalities }
        val perFamily = if (list.size > 6) 1_200 else 4_500
        return ok {
            put("families", JsonArray(list.map { s ->
                buildJsonObject {
                    put("id", s.id); put("title", s.title); put("version", s.version)
                    put("summary", s.oneLiner())
                    put("strengths", strings(s.strengths)); put("weaknesses", strings(s.weaknesses))
                    put("ideal_tasks", strings(s.idealTasks)); put("avoid_tasks", strings(s.avoidTasks))
                    if (s.unverifiedTags.isNotEmpty()) put("unverified", strings(s.unverifiedTags))
                    task?.let { put("task_supported", it in s.idealTasks && it !in s.avoidTasks) }
                    s.section("Cuándo elegirlo")?.let { put("when_to_choose", it.take(perFamily)) }
                    if (list.size <= 6) {
                        task?.let { tk -> TASK_TEMPLATE_KEYS[tk]?.let { s.subsection("Plantillas por tarea", it) } }?.let { put("task_template", it.take(2_500)) }
                        s.section("Linaje")?.let { put("lineage", it.take(2_500)) }
                    }
                }
            }))
            put("note", "Criterio editorial de las skills. Antes de recomendar, comprueba disponibilidad, schema y coste reales con recommend_models / get_model_schema / estimate_generation_cost. Respeta las familias excluidas en ajustes.")
        }
    }
}
