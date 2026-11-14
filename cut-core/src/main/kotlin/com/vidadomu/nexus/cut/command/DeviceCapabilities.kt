package com.vidadomu.nexus.cut.command

import com.vidadomu.nexus.cut.command.CapabilityKind.EDIT
import com.vidadomu.nexus.cut.command.CapabilityKind.JOB
import com.vidadomu.nexus.cut.command.CapabilityKind.READ
import com.vidadomu.nexus.cut.model.ExportResolution
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.model.VideoCodec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Archivo de la Biblioteca de Nexus listo para el montaje: el medio ya analizado y dónde encaja en el guion. */
class LibraryMedia(val media: MediaRef, val sceneId: String? = null, val shotId: String? = null, val warnings: List<String> = emptyList())

/** La Biblioteca vista desde el editor. La implementa la app (analiza el archivo real); en pruebas, un doble. */
fun interface CutLibrary {
    /**
     * Analiza un archivo de la Biblioteca sin copiarlo. El id del medio lo pone el montaje.
     * Lanza [CutFailure] NOT_FOUND si no existe o INVALID_ARGUMENT si no es un vídeo, audio o imagen utilizable.
     */
    suspend fun probe(assetId: String): LibraryMedia
}

/** El motor de exportación del dispositivo. Una exportación a la vez; el resultado entra en la Biblioteca. */
interface CutRenderer {
    /** Pone en marcha la exportación del montaje tal como está ahora. Devuelve el estado inicial. */
    suspend fun start(session: CutSession, overrides: ExportOverrides): JsonObject
    fun status(): JsonObject
    fun cancel(): Boolean
}

data class ExportOverrides(val resolution: ExportResolution? = null, val codec: VideoCodec? = null,
    val clipIds: Set<String> = emptySet(), val eachClip: Boolean = false)

/** Capacidades que dependen del dispositivo: entrar archivos de la Biblioteca y exportar. */
object DeviceCapabilities {
    const val LIBRARY = "nexus.library"
    const val EXPORT = "engine.export"
    private const val MAX_IMPORT = 30

    fun all(library: CutLibrary, renderer: CutRenderer): List<Capability> = listOf(import(library)) + render(renderer)

    private fun import(library: CutLibrary) = Capability("media.import", EDIT,
        "Trae archivos de la Biblioteca de Nexus al montaje sin copiarlos y, por defecto, los coloca en la línea de tiempo en el orden dado. Un archivo que ya estaba en el montaje no se duplica: se reutiliza. Los planos del guion de cada archivo pasan a sus clips.",
        input = Schema.obj {
            ids("asset_ids", "Archivos de la Biblioteca (search_assets o list_library_assets), en el orden en que deben ir; hasta 30.", required = true)
            bool("place", "Colocarlos en la línea de tiempo; por defecto true. Con false solo entran en los medios del montaje.")
            us("at_us", "Dónde empieza el primero; por defecto al final"); str("mode", "Cómo entran.", enum = listOf("insert", "overwrite"))
            bool("with_audio", "Colocar también el sonido de los vídeos; por defecto true.")
        },
        output = Schema.obj { ids("media_ids", "Medios del montaje, en el mismo orden."); ids("clip_ids", "Clips creados."); int("reused", "Archivos que ya estaban.") },
        requires = setOf(LIBRARY), errors = setOf(ErrorCode.NOT_FOUND, ErrorCode.REJECTED_BY_ENGINE, ErrorCode.PRECONDITION_FAILED),
        handler = Handler.Staged { state, a ->
            val ids = a.reqIds("asset_ids").toList()
            if (ids.size > MAX_IMPORT) a.bad("como mucho $MAX_IMPORT archivos por vez")
            val known = state.project.media.filter { it.assetId != null }.associateBy { it.assetId!! }
            // Se analiza fuera de la transacción: puede tardar con vídeos largos.
            val probed = ids.filter { it !in known }.associateWith { library.probe(it) }
            val place = a.bool("place", true)
            val passthrough = a.json.filterKeys { it in setOf("at_us", "mode", "with_audio") };
            { d: Draft ->
                val inDraft = d.project.media.filter { it.assetId != null }.associateBy { it.assetId!! }.toMutableMap()
                val added = ArrayList<MediaRef>()
                for ((assetId, lib) in probed) {
                    if (assetId in inDraft) continue
                    val ref = lib.media.copy(id = d.ids.next("media"), assetId = assetId)
                    inDraft[assetId] = ref; added += ref
                    d.warnings += lib.warnings
                }
                if (added.isNotEmpty()) d.header("Importar") { it.copy(media = it.media + added) }
                val mediaIds = ids.map { inDraft.getValue(it).id }
                val clips = if (!place) emptyList() else {
                    val items = JsonArray(ids.map { assetId ->
                        val lib = probed[assetId]
                        buildJsonObject {
                            put("media_id", inDraft.getValue(assetId).id)
                            lib?.sceneId?.let { put("scene_id", it) }; lib?.shotId?.let { put("shot_id", it) }
                        }
                    })
                    val placed = CoreCapabilities.place(d, Args(JsonObject(passthrough + ("items" to items))))
                    (placed["clip_ids"] as? JsonArray)?.map { (it as JsonPrimitive).content }.orEmpty()
                }
                buildJsonObject {
                    put("media_ids", Outcome.strings(mediaIds)); put("clip_ids", Outcome.strings(clips))
                    put("imported", added.size); put("reused", ids.size - probed.size)
                }
            }
        })

    private fun render(r: CutRenderer) = listOf(
        Capability("render.export", JOB,
            "Exporta el montaje o cada clip por separado a MP4 en segundo plano. each_clip=true exporta todos los segmentos visuales; clip_ids elige algunos. Cada intervalo conserva el audio y las capas del montaje. Usa una instantánea inmutable: NO hay que borrar clips, hacer ripple ni deshacer para exportar. Cola secuencial con notificación, cancelar y resultados en Biblioteca. Usa los ajustes del montaje salvo overrides. Local y gratis.",
            input = Schema.obj {
                str("resolution", "Resolución de salida.", enum = ExportResolution.entries.map { it.label })
                str("codec", "Códec de vídeo; h265 solo si el dispositivo lo codifica.", enum = listOf("h264", "h265"))
                ids("clip_ids", "Clips concretos que se exportan a archivos independientes, con su audio. Los vinculados se agrupan.")
                bool("each_clip", "true: un archivo por cada segmento visual del montaje, sin modificarlo.")
            },
            output = Schema.obj { str("state", "running, done, failed, cancelled o idle."); int("progress", "Porcentaje."); int("file_count", "Número de archivos del lote.") },
            preconditions = listOf("La línea de tiempo tiene imagen.", "No hay otra exportación en curso."), requires = setOf(EXPORT),
            errors = setOf(ErrorCode.PRECONDITION_FAILED),
            handler = Handler.Job { session, a, _ ->
                val res = a.str("resolution")?.let { v -> ExportResolution.entries.firstOrNull { it.label.equals(v, true) } ?: a.bad("«resolution» no admite «$v»") }
                r.start(session, ExportOverrides(res, a.enum<VideoCodec>("codec"), a.ids("clip_ids"), a.bool("each_clip", false)))
            }),
        Capability("render.status", READ, "Estado de la exportación: en curso con su progreso, terminada (con el archivo de la Biblioteca), fallida con el motivo, o cancelada.",
            output = Schema.obj { str("state", "Estado."); int("progress", "Porcentaje total."); str("asset_id", "Último archivo exportado."); ids("asset_ids", "Todos los archivos ya guardados, también si se cancela o falla el resto."); int("file_count", "Archivos del lote."); int("item", "Archivo actual.") },
            requires = setOf(EXPORT), handler = Handler.Read { _, _ -> r.status() }),
        Capability("render.cancel", JOB, "Cancela la exportación en curso. No deja ningún archivo a medias.",
            requires = setOf(EXPORT), handler = Handler.Job { _, _, _ -> buildJsonObject { put("cancelled", r.cancel()) } }),
    )
}
