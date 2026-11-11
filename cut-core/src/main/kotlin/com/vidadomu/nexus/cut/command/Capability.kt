package com.vidadomu.nexus.cut.command

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.Normalizer

enum class CapabilityKind {
    /** Consulta: no cambia nada. */
    READ,
    /** Cambia el proyecto dentro de una transacción; queda en el historial compartido y se puede deshacer. */
    EDIT,
    /** Mueve el cabezal o la selección: se ve al instante, no entra en el historial. */
    VIEW,
    /** Trabajo largo (exportar, analizar): devuelve un trabajo con progreso y cancelación. */
    JOB,
}

enum class Permission {
    /** Local, gratis y reversible o inocuo. */
    FREE,
    /** Local pero no reversible (borrar un proyecto, sobrescribir un archivo): requiere que el usuario lo haya pedido. */
    CONFIRM,
    /** Consume créditos de un proveedor: nunca sin confirmación expresa del usuario. */
    SPEND,
}

sealed interface Handler {
    fun interface Read : Handler { suspend fun read(state: CutState, args: Args): JsonObject }
    /** Sin suspender ni E/S: se ejecuta dentro de la transacción. Sirve para EDIT y VIEW. */
    fun interface Edit : Handler { fun apply(draft: Draft, args: Args): JsonObject }
    fun interface Job : Handler { suspend fun start(session: CutSession, args: Args, origin: Origin): JsonObject }
    data object Undo : Handler
    data object Redo : Handler
}

/**
 * Una capacidad de Nexus Cut: lo mismo que puede hacer una persona con los dedos o el S Pen, descrito
 * para que también lo pida un agente. El id es estable; si cambia el contrato, sube [version].
 */
class Capability(
    val id: String,
    val kind: CapabilityKind,
    /** Qué hace y cuándo usarla, en una o dos frases. */
    val summary: String,
    val input: JsonObject = Schema.EMPTY,
    val output: JsonObject = Schema.ANY,
    val version: Int = 1,
    val preconditions: List<String> = emptyList(),
    val errors: Set<ErrorCode> = emptySet(),
    /** Capacidades del dispositivo o del motor que necesita (p. ej. «codec.hevc.encode»). */
    val requires: Set<String> = emptySet(),
    val permission: Permission = Permission.FREE,
    /** Coste estimado cuando lo hay, en texto para mostrar. */
    val cost: String? = null,
    val handler: Handler,
) {
    init {
        require(ID.matches(id)) { "id de capacidad no válido: $id" }
        require((kind == CapabilityKind.READ) == (handler is Handler.Read)) { "$id: el tipo y el manejador no coinciden" }
        require((kind == CapabilityKind.JOB) == (handler is Handler.Job)) { "$id: el tipo y el manejador no coinciden" }
    }

    val domain: String get() = id.substringBefore('.')
    /** Los cambios de proyecto se deshacen con el historial compartido; consultas y vista no dejan nada que deshacer. */
    val reversible: Boolean get() = kind == CapabilityKind.EDIT && permission == Permission.FREE

    fun brief(available: Boolean): JsonObject = buildJsonObject {
        put("id", id); put("kind", kind.name.lowercase()); put("summary", summary)
        if (!available) put("available", false)
    }

    fun spec(available: Boolean): JsonObject = buildJsonObject {
        put("id", id); put("version", version); put("kind", kind.name.lowercase()); put("summary", summary)
        put("input", input); put("output", output)
        put("reversible", reversible); put("permission", permission.name.lowercase())
        if (preconditions.isNotEmpty()) put("preconditions", Outcome.strings(preconditions))
        put("errors", Outcome.strings((errors + COMMON_ERRORS).map { it.name }))
        if (requires.isNotEmpty()) put("requires", Outcome.strings(requires))
        cost?.let { put("cost", it) }
        put("available", available)
    }

    companion object {
        private val ID = Regex("[a-z]+\\.[a-z][a-z0-9_]*")
        private val COMMON_ERRORS = setOf(ErrorCode.INVALID_ARGUMENT, ErrorCode.INTERNAL)
    }
}

/**
 * Catálogo descubrible. El agente no recibe todas las capacidades en su contexto: pregunta por dominio o
 * por intención ([catalog]), pide el contrato solo de las que va a usar ([describe]) y las ejecuta por id.
 */
class CutRegistry(private val features: () -> Set<String> = { emptySet() }) {
    private val byId = LinkedHashMap<String, Capability>()

    fun register(vararg capabilities: Capability): CutRegistry = register(capabilities.asList())

    fun register(capabilities: Iterable<Capability>): CutRegistry {
        for (c in capabilities) {
            check(byId.put(c.id, c) == null) { "capacidad duplicada: ${c.id}" }
        }
        return this
    }

    operator fun get(id: String): Capability? = byId[id]
    val all: List<Capability> get() = byId.values.toList()
    val size: Int get() = byId.size
    val domains: Map<String, Int> get() = byId.values.groupingBy { it.domain }.eachCount()

    fun available(c: Capability): Boolean = c.requires.isEmpty() || features().containsAll(c.requires)

    /** Búsqueda por dominio, tipo y texto libre (id y descripción, sin distinguir acentos). Ordena por coincidencia. */
    fun search(domain: String? = null, query: String? = null, kinds: Set<CapabilityKind>? = null, limit: Int = 40): List<Capability> {
        val terms = query?.let(::fold)?.split(Regex("[^a-z0-9_.]+"))?.filter { it.length > 2 && it !in STOPWORDS }.orEmpty()
        return byId.values.asSequence()
            .filter { domain == null || it.domain == domain }
            .filter { kinds == null || it.kind in kinds }
            .map { c ->
                val id = fold(c.id); val text = fold(c.summary)
                // Raíz aproximada: «dividir» encuentra «divide», «subtítulos» encuentra «subtítulo».
                c to if (terms.isEmpty()) 1 else terms.sumOf { t ->
                    val stem = if (t.length >= 6) t.dropLast(2) else t
                    (if (t in id) 3 else 0) + (if (stem in text) 1 else 0)
                }
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }   // estable: a igual puntuación, orden de registro
            .take(limit.coerceIn(1, 200)).map { it.first }.toList()
    }

    fun catalog(domain: String? = null, query: String? = null, kinds: Set<CapabilityKind>? = null, limit: Int = 40): JsonObject = buildJsonObject {
        val found = search(domain, query, kinds, limit)
        put("total", size)
        put("domains", buildJsonObject { for ((d, n) in domains) put(d, n) })
        put("capabilities", buildJsonArray { for (c in found) add(c.brief(available(c))) })
    }

    fun describe(ids: Collection<String>): JsonObject = buildJsonObject {
        put("capabilities", buildJsonArray { for (id in ids) byId[id]?.let { add(it.spec(available(it))) } })
        val unknown = ids.filter { it !in byId }
        if (unknown.isNotEmpty()) put("unknown", Outcome.strings(unknown))
    }

    private val STOPWORDS = setOf("los", "las", "del", "una", "uno", "unos", "unas", "que", "con", "por", "para", "the", "and", "este", "esta", "como", "todo", "todos", "todas")

    private fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}

/** Constructor mínimo de JSON Schema para los contratos de entrada y salida. */
object Schema {
    val EMPTY: JsonObject = buildJsonObject { put("type", "object"); put("properties", JsonObject(emptyMap())); put("additionalProperties", false) }
    val ANY: JsonObject = buildJsonObject { put("type", "object") }

    fun obj(block: Props.() -> Unit): JsonObject = Props().apply(block).build()

    class Props {
        private val props = LinkedHashMap<String, JsonObject>()
        private val required = ArrayList<String>()

        fun raw(name: String, schema: JsonObject, required: Boolean = false) { props[name] = schema; if (required) this.required += name }
        private fun simple(name: String, type: String, description: String, required: Boolean, extra: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()) =
            raw(name, JsonObject(mapOf("type" to JsonPrimitive(type), "description" to JsonPrimitive(description)) + extra), required)

        fun str(name: String, description: String, required: Boolean = false, enum: List<String>? = null) =
            simple(name, "string", description, required, enum?.let { mapOf("enum" to Outcome.strings(it)) }.orEmpty())
        /** Tiempo o duración en microsegundos enteros de la línea de tiempo. */
        fun us(name: String, description: String, required: Boolean = false) = simple(name, "integer", "$description (microsegundos)", required)
        fun int(name: String, description: String, required: Boolean = false) = simple(name, "integer", description, required)
        fun num(name: String, description: String, required: Boolean = false, min: Double? = null, max: Double? = null) =
            simple(name, "number", description, required, buildMap { min?.let { put("minimum", JsonPrimitive(it)) }; max?.let { put("maximum", JsonPrimitive(it)) } })
        fun bool(name: String, description: String, required: Boolean = false) = simple(name, "boolean", description, required)
        fun ids(name: String, description: String, required: Boolean = false) = raw(name, buildJsonObject {
            put("type", "array"); put("description", description); put("items", buildJsonObject { put("type", "string") })
        }, required)
        fun list(name: String, description: String, items: JsonObject, required: Boolean = false) = raw(name, buildJsonObject {
            put("type", "array"); put("description", description); put("items", items)
        }, required)

        fun build(): JsonObject = buildJsonObject {
            put("type", "object"); put("properties", JsonObject(props))
            if (required.isNotEmpty()) put("required", JsonArray(required.map { JsonPrimitive(it) }))
            put("additionalProperties", false)
        }
    }
}
