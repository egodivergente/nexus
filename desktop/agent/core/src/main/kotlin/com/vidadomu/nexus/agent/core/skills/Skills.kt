package com.vidadomu.nexus.agent.core.skills

import java.io.File
import kotlinx.serialization.Serializable
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

@Serializable
enum class SkillKind { MODEL, EXPERTISE }

@Serializable
data class SkillSourceRef(val url: String, val title: String? = null, val accessed: String? = null)

/** Una skill: cabecera estructurada (para el router) + cuerpo Markdown (para el LLM). */
@Serializable
data class Skill(
    val id: String,
    val kind: SkillKind,
    val title: String,
    val version: Int,
    val updated: String?,
    val match: List<String> = emptyList(),
    val modalities: List<String> = emptyList(),
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList(),
    val idealTasks: List<String> = emptyList(),
    val avoidTasks: List<String> = emptyList(),
    val unverifiedTags: List<String> = emptyList(),
    val appliesTo: List<String> = emptyList(),
    val triggers: List<String> = emptyList(),
    val sources: List<SkillSourceRef> = emptyList(),
    val body: String,
    /** De dónde se cargó (assets, files/skills…), para depurar actualizaciones. */
    val origin: String = "",
) {
    private val regexes by lazy { match.mapNotNull { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() } }

    fun matchesModel(modelId: String): Boolean = regexes.any { it.containsMatchIn(modelId) }

    fun isVerified(tag: String): Boolean = tag !in unverifiedTags

    /** Primera línea útil del resumen, para el índice que ve el LLM. */
    fun oneLiner(): String = body.lineSequence().map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }?.take(180) ?: title

    /** Sección del cuerpo por título (## Título), para cargar solo lo necesario. */
    fun section(titleStartsWith: String): String? {
        val lines = body.lines()
        val start = lines.indexOfFirst { it.startsWith("#") && it.trimStart('#', ' ').startsWith(titleStartsWith, ignoreCase = true) }
        if (start < 0) return null
        val level = lines[start].takeWhile { it == '#' }.length
        val end = (start + 1 until lines.size).firstOrNull { i ->
            lines[i].startsWith("#") && lines[i].takeWhile { it == '#' }.length <= level
        } ?: lines.size
        return lines.subList(start, end).joinToString("\n").trim()
    }

    /**
     * Subsección (### …) dentro de una sección (## …) cuyo título contenga alguna de las claves
     * (sin distinguir mayúsculas, guiones ni espacios). Sirve para dar al LLM solo la plantilla de su tarea.
     */
    fun subsection(parentTitle: String, keys: List<String>): String? {
        val parent = section(parentTitle) ?: return null
        val norm = { x: String -> x.lowercase().replace(Regex("[^a-z0-9áéíóúñ]"), "") }
        val wanted = keys.map(norm).filter { it.isNotEmpty() }
        val blocks = Regex("(?m)^### ").split(parent).drop(1).map { norm(it.lineSequence().first()) to it }
        // Claves en orden de prioridad (las específicas primero), así «edit» no gana a «image edit».
        for (k in wanted) blocks.firstOrNull { k in it.first }?.let { return "### " + it.second.trim() }
        return null
    }
}

/** Claves de título de «Plantillas por tarea» para cada tarea del vocabulario controlado. */
val TASK_TEMPLATE_KEYS: Map<String, List<String>> = mapOf(
    "text_to_video" to listOf("text-to-video", "texto a vídeo", "t2v"),
    "image_to_video" to listOf("image-to-video", "imagen a vídeo", "i2v"),
    "reference_to_video" to listOf("reference-to-video", "referencia a vídeo", "referencias", "reference"),
    "start_end_frame" to listOf("start/end", "start-end", "first-last", "fotograma inicial", "keyframes"),
    "video_edit" to listOf("video edit", "video-edit", "edición de vídeo", "video-to-video", "v2v"),
    "video_extend" to listOf("extend", "extensión", "extender"),
    "motion_control" to listOf("motion control", "motion-control", "control de movimiento"),
    "lipsync" to listOf("lipsync", "lip-sync", "avatar", "doblaje"),
    "avatar" to listOf("avatar", "cabeza parlante", "lipsync"),
    "text_to_image" to listOf("text-to-image", "texto a imagen", "t2i"),
    "image_edit" to listOf("image edit", "image-edit", "edición de imagen", "edición", "edit"),
    "multi_reference_image" to listOf("multi-referencia", "multireferencia", "multi-reference", "reference-to-image", "secuencia", "sequential"),
    "upscale" to listOf("upscale", "escalado"),
    "layer_decomposition" to listOf("layer", "capas"),
)

class SkillParseException(message: String) : Exception(message)

object SkillParser {

    fun parse(text: String, origin: String = ""): Skill {
        val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n")
        if (!normalized.startsWith("---\n")) throw SkillParseException("$origin: falta la cabecera YAML (---).")
        val end = normalized.indexOf("\n---", 4)
        if (end < 0) throw SkillParseException("$origin: cabecera YAML sin cerrar.")
        val yaml = normalized.substring(4, end)
        val body = normalized.substring(end + 4).trimStart('-', '\n').trim()

        @Suppress("UNCHECKED_CAST")
        val h = (Load(LoadSettings.builder().build()).loadFromString(yaml) as? Map<String, Any?>)
            ?: throw SkillParseException("$origin: cabecera YAML vacía.")

        fun str(k: String): String? = h[k]?.toString()?.takeIf { it.isNotBlank() }
        fun list(k: String): List<String> = when (val v = h[k]) {
            null -> emptyList()
            is List<*> -> v.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            else -> listOf(v.toString())
        }

        val id = str("id") ?: throw SkillParseException("$origin: falta id.")
        val kind = when (str("kind")?.lowercase()) {
            "model" -> SkillKind.MODEL
            "expertise" -> SkillKind.EXPERTISE
            else -> throw SkillParseException("$origin: kind debe ser model o expertise.")
        }
        val sources = (h["sources"] as? List<*>).orEmpty().mapNotNull { s ->
            when (s) {
                is Map<*, *> -> s["url"]?.toString()?.let { SkillSourceRef(it, s["title"]?.toString(), s["accessed"]?.toString()) }
                is String -> SkillSourceRef(s)
                else -> null
            }
        }
        val skill = Skill(
            id = id,
            kind = kind,
            title = str("title") ?: id,
            version = str("version")?.toIntOrNull() ?: 1,
            updated = str("updated"),
            match = list("match"),
            modalities = list("modalities").map { it.uppercase() },
            strengths = list("strengths"),
            weaknesses = list("weaknesses"),
            idealTasks = list("ideal_tasks"),
            avoidTasks = list("avoid_tasks"),
            unverifiedTags = list("unverified_tags"),
            appliesTo = list("applies_to").map { it.uppercase() },
            triggers = list("triggers"),
            sources = sources,
            body = body,
            origin = origin,
        )
        if (kind == SkillKind.MODEL && skill.match.isEmpty()) throw SkillParseException("$origin: skill de modelo sin match.")
        skill.match.forEach { runCatching { Regex(it) }.onFailure { _ -> throw SkillParseException("$origin: regex no válida «$it».") } }
        return skill
    }
}

/** Origen de ficheros SKILL.md. En Android: assets empaquetados y `files/skills` actualizable. */
fun interface SkillSource {
    /** Pares (origen legible, contenido). */
    fun load(): List<Pair<String, String>>
}

class DirectorySkillSource(private val root: File, private val label: String = root.path) : SkillSource {
    override fun load(): List<Pair<String, String>> =
        if (!root.isDirectory) emptyList()
        else root.walkTopDown().filter { it.isFile && it.name == "SKILL.md" }
            .sortedBy { it.path }
            .map { "$label/${it.relativeTo(root).invariantSeparatorsPath}" to it.readText(Charsets.UTF_8) }
            .toList()
}

/**
 * Índice de skills. Si una skill aparece en varias fuentes gana la de mayor `version`
 * (así un bundle actualizado en disco sustituye a la empaquetada sin recompilar).
 * Las que no parsean se apartan en [errors] sin tumbar el resto.
 */
class SkillStore(sources: List<SkillSource>, active: Collection<String> = emptyList()) {

    val errors: List<String>
    private val skills: Map<String, Skill>

    init {
        val errs = mutableListOf<String>()
        val byId = linkedMapOf<String, Skill>()
        for (src in sources) {
            for ((origin, text) in runCatching { src.load() }.getOrElse { errs += "Fuente ilegible: ${it.message}"; emptyList() }) {
                val s = runCatching { SkillParser.parse(text, origin) }.getOrElse { errs += it.message ?: origin; null } ?: continue
                val prev = byId[s.id]
                if (prev == null || s.version >= prev.version) byId[s.id] = s
            }
        }
        val filter = active.toSet()
        skills = if (filter.isEmpty()) byId else byId.filterKeys { it in filter }
        errors = errs
    }

    fun all(): List<Skill> = skills.values.toList()
    fun get(id: String): Skill? = skills[id]
    fun models(): List<Skill> = skills.values.filter { it.kind == SkillKind.MODEL }
    fun expertise(): List<Skill> = skills.values.filter { it.kind == SkillKind.EXPERTISE }

    /** Skill de modelo más específica (regex más larga que case) para un id del registro. */
    fun forModel(modelId: String): Skill? = models()
        .mapNotNull { s -> s.match.filter { runCatching { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(modelId) }.getOrDefault(false) }.maxOfOrNull { it.length }?.let { s to it } }
        .maxByOrNull { it.second }?.first

    /** Índice compacto para el prompt de sistema: el LLM carga el cuerpo bajo demanda. */
    fun catalogText(): String = buildString {
        expertise().forEach { appendLine("- ${it.id} (experto): ${it.oneLiner()}") }
        models().forEach { appendLine("- ${it.id} (modelo, v${it.version}, ${it.updated ?: "s/f"}): ${it.oneLiner()}") }
    }.trim()
}
