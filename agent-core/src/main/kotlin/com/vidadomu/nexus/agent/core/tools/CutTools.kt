package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.cut.command.CapabilityKind
import com.vidadomu.nexus.cut.command.CutFailure
import com.vidadomu.nexus.cut.command.CutJson
import com.vidadomu.nexus.cut.command.CutSession
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.command.Op
import com.vidadomu.nexus.cut.command.Origin
import com.vidadomu.nexus.cut.command.Outcome
import com.vidadomu.nexus.cut.command.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Nexus Cut para el agente. No recibe las capacidades del editor en cada turno: explora el catálogo,
 * pide el contrato de las que va a usar y las ejecuta por id en el mismo bus de comandos que la interfaz.
 * Lo que cambia se ve al instante en la línea de tiempo del usuario y comparte su «Deshacer».
 */
object CutTools {
    fun all(w: CutWorkspace): List<AgentTool> = listOf(CutCatalogTool(w), CutDescribeTool(w), CutReadTool(w), CutEditTool(w), CutCreateTool(w))

    /** Una línea para el contexto del turno cuando hay un montaje abierto; null si no lo hay. */
    suspend fun context(w: CutWorkspace): String? {
        val s = w.activeSession()?.state?.value ?: return null
        val p = s.project
        return "Montaje abierto en Nexus Cut: «${p.name}» (project_id=${p.id}, revisión ${p.revision}, ${p.timeline.durationUs / 1000} ms, " +
            "${p.timeline.allTracks.sumOf { it.clips.size }} clips, cabezal en ${s.view.playheadUs / 1000} ms" +
            (if (s.view.selection.isEmpty()) "" else ", seleccionados: ${s.view.selection.joinToString()}") + "). Se edita con cut_edit; el detalle, con cut_read."
    }

    internal const val NO_SESSION = "No hay ningún montaje abierto. Indica project_id (cut_catalog lista los montajes) o crea uno con cut_create."

    internal suspend fun session(w: CutWorkspace, input: JsonObject): CutSession = try {
        input.str("project_id")?.let { w.session(it) } ?: w.activeSession() ?: throw ToolInputException(NO_SESSION)
    } catch (e: CutFailure) {
        throw ToolInputException(e.message ?: "Montaje no disponible.")
    }

    internal fun ops(input: JsonObject): List<Op> {
        val raw = input["ops"] as? JsonArray ?: throw ToolInputException("Falta «ops»: lista de {id, args}.")
        if (raw.isEmpty()) throw ToolInputException("«ops» está vacío.")
        return raw.mapIndexed { i, el ->
            val o = el as? JsonObject ?: throw ToolInputException("ops[$i] debe ser un objeto {id, args}.")
            val args = when (val a = o["args"]) {
                null -> JsonObject(emptyMap())
                is JsonObject -> a
                // Algunos modelos mandan los argumentos como texto JSON: se acepta si es un objeto válido.
                is JsonPrimitive -> runCatching { Json.parseToJsonElement(a.content) as JsonObject }.getOrNull() ?: throw ToolInputException("ops[$i].args debe ser un objeto.")
                else -> throw ToolInputException("ops[$i].args debe ser un objeto.")
            }
            Op(o.req("id"), args)
        }
    }

    internal fun result(outcome: Outcome): ToolOutcome = when (outcome) {
        is Outcome.Done -> ok(outcome.toJson())
        is Outcome.Failed -> fail(outcome.error.message) {
            put("code", outcome.error.code.name)
            outcome.error.opIndex?.let { put("op_index", it) }; outcome.error.opId?.let { put("op", it) }
            outcome.error.currentRevision?.let { put("current_revision", it) }
            put("changed", false)
        }
    }

    internal fun kinds(w: CutWorkspace, ops: List<Op>): List<CapabilityKind?> = ops.map { w.registry[it.id]?.kind }
}

private fun SchemaBuilder.opsField(description: String) = objectArray("ops", description, required = true) {
    string("id", "Id de la capacidad, p. ej. «clip.split».", required = true)
    freeObject("args", "Argumentos según su contrato (cut_describe). Tiempos en microsegundos.")
}

class CutCatalogTool(private val w: CutWorkspace) : BaseTool(
    "cut_catalog", ToolCategory.READ,
    "Explora lo que Nexus Cut (el editor de vídeo integrado) sabe hacer y qué montajes hay. Filtra por dominio o describe la intención con palabras («quitar silencios», «subtítulos», «color»); devuelve ids y resúmenes, no los contratos. Úsalo al empezar un trabajo de montaje.",
    schema {
        string("domain", "Dominio.", enum = listOf("project", "media", "timeline", "clip", "audio", "effects", "keyframe", "subtitle", "story", "ai", "render", "sync"))
        string("query", "Intención o palabras clave.")
        string("kind", "Tipo de capacidad.", enum = listOf("read", "edit", "view", "job"))
        integer("limit", "Máximo de capacidades; por defecto 40.")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val kind = input.str("kind")?.let { k -> CapabilityKind.entries.firstOrNull { it.name.equals(k, true) } ?: throw ToolInputException("«kind» no admite «$k».") }
        val catalog = w.registry.catalog(input.str("domain"), input.str("query"), kind?.let(::setOf), input.int("limit") ?: 40)
        val active = w.active.value
        val montages = w.list()
        return ok(buildJsonObject {
            for ((k, v) in catalog) put(k, v)
            active?.let { put("active_project_id", it) }
            put("montages", buildJsonArray {
                for (m in montages.take(30)) add(buildJsonObject {
                    put("project_id", m.id); put("name", m.name); put("duration_us", m.durationUs); put("clips", m.clips)
                    m.nexusProjectId?.let { put("nexus_project_id", it) }; m.sceneId?.let { put("scene_id", it) }; m.error?.let { put("note", it) }
                })
            })
            put("next", "Pide con cut_describe el contrato de las capacidades que vayas a usar; lee con cut_read y cambia con cut_edit.")
        })
    }
}

class CutDescribeTool(private val w: CutWorkspace) : BaseTool(
    "cut_describe", ToolCategory.READ,
    "Contrato completo de capacidades de Nexus Cut: argumentos (JSON Schema), salida, precondiciones, errores posibles, si se puede deshacer y si está disponible en este dispositivo. Pídelo solo de las que vayas a ejecutar.",
    schema { stringArray("ids", "Ids del catálogo, hasta 12 por llamada.", required = true) },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val ids = input.strList("ids").ifEmpty { throw ToolInputException("Falta «ids».") }
        if (ids.size > 12) throw ToolInputException("Pide como mucho 12 contratos por llamada.")
        return ok(w.registry.describe(ids))
    }
}

class CutReadTool(private val w: CutWorkspace) : BaseTool(
    "cut_read", ToolCategory.READ,
    "Consulta un montaje de Nexus Cut sin cambiar nada: ejecuta una o varias capacidades de lectura (project.context, timeline.get, timeline.find_clips, clip.get, media.list…). Sin project_id usa el montaje que el usuario tiene abierto. Lee siempre antes de editar.",
    schema {
        string("project_id", "Montaje; vacío = el abierto.")
        opsField("Lecturas que hacer, en orden.")
    },
) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val ops = CutTools.ops(input)
        CutTools.kinds(w, ops).forEachIndexed { i, k ->
            if (k != null && k != CapabilityKind.READ) throw ToolInputException("«${ops[i].id}» no es una lectura: cambia el montaje y va por cut_edit.")
        }
        return CutTools.result(CutTools.session(w, input).execute(Request(ops, Origin.AGENT)))
    }
}

class CutEditTool(private val w: CutWorkspace) : BaseTool(
    "cut_edit", ToolCategory.ORGANIZE,
    "Edita un montaje de Nexus Cut. Todas las operaciones de la llamada son UNA transacción: o entran todas y quedan como un solo paso de «Deshacer» para el usuario, o no cambia nada y se devuelve cuál falló y por qué. El usuario ve el cambio al instante en su línea de tiempo. Con dry_run se ensaya sin tocar nada. Es local y gratis; no genera ni exporta.",
    schema {
        string("project_id", "Montaje; vacío = el abierto.")
        opsField("Operaciones de edición, en orden; cada una ve el resultado de la anterior.")
        string("label", "Nombre corto del paso que verá el usuario en Deshacer, p. ej. «Quitar pausas».")
        boolean("dry_run", "Ensayar: valida y devuelve lo que cambiaría sin confirmarlo.")
        integer("expected_revision", "Revisión que leíste; si el montaje ya cambió se rechaza para que vuelvas a leer.")
        string("idempotency_key", "Clave propia de esta edición: si repites la llamada no se aplica dos veces.")
    },
) {
    override fun describe(input: JsonObject): String =
        input.str("label")?.let { "Editar el montaje: $it" } ?: ("Editar el montaje (" + runCatching { CutTools.ops(input).joinToString { it.id } }.getOrDefault("operaciones") + ")")

    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val ops = CutTools.ops(input)
        if (CutTools.kinds(w, ops).any { it == CapabilityKind.JOB }) throw ToolInputException("Los trabajos largos (exportar, analizar) no van en una edición: tienen su propia herramienta.")
        val revision = (input["expected_revision"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        return CutTools.result(CutTools.session(w, input).execute(Request(ops, Origin.AGENT, input.str("label"), input.bool("dry_run") ?: false, revision, input.str("idempotency_key"))))
    }
}

class CutCreateTool(private val w: CutWorkspace) : BaseTool(
    "cut_create", ToolCategory.ORGANIZE,
    "Crea un montaje nuevo y vacío en Nexus Cut, vinculado al proyecto audiovisual activo (y a una escena si se indica), con pistas de imagen, capa, diálogo y música. Devuelve su project_id para cut_edit.",
    schema {
        string("name", "Nombre del montaje.", required = true)
        string("nexus_project_id", "Proyecto de Nexus al que pertenece; por defecto el activo.")
        string("scene_id", "Escena del guion que monta.")
    },
) {
    override val additive: Boolean get() = true
    override fun describe(input: JsonObject): String = "Crear el montaje «${input.str("name") ?: "nuevo"}»"

    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome {
        val created = w.create(input.req("name"), input.str("nexus_project_id") ?: session.activeProjectId, input.str("scene_id"))
        return ok(CutJson.project(created.state.value))
    }
}
