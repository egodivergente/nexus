import com.vidadomu.corte.model.*
import com.vidadomu.corte.timeline.*
import com.vidadomu.corte.storage.AtomicFile
import com.vidadomu.corte.storage.Waveform
import com.vidadomu.corte.storage.WaveformBuilder
import com.vidadomu.corte.storage.Silences
import com.vidadomu.corte.timeline.render.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID

private val cj = Json { encodeDefaults = true; ignoreUnknownKeys = false }
private fun JsonObject.s(k: String, default: String = "") = this[k]?.jsonPrimitive?.contentOrNull ?: default
private fun JsonObject.l(k: String, default: Long = 0) = this[k]?.jsonPrimitive?.longOrNull ?: default
@Serializable data class CorteEdit(val id: String, val label: String, val before: Timeline, val after: Timeline)
@Serializable data class CorteReceipt(val fingerprint: String, val reply: JsonObject)
@Serializable data class CorteDocument(
    val schema: Int = 1, val project: Project, val revision: Long = 0,
    val undo: List<CorteEdit> = emptyList(), val redo: List<CorteEdit> = emptyList(),
    val receipts: Map<String, CorteReceipt> = emptyMap(),
    val links: Map<String, JsonObject> = emptyMap(),
    val playheadUs: Long = 0, val selection: Set<String> = emptySet(),
)

/** Windows boundary. All timeline decisions are delegated to Corte's actual Kotlin engine. */
class CorteDesktop(private val root: File) {
    private fun file(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Identificador de proyecto inválido" }
        return File(root, "$id/edit.json")
    }
    private fun load(id: String): CorteDocument {
        val f = file(id)
        return runCatching { cj.decodeFromString<CorteDocument>(f.readText()) }.getOrElse { primary ->
            val backup = AtomicFile.backupOf(f)
            if (backup.exists()) cj.decodeFromString<CorteDocument>(backup.readText()) else throw primary
        }
    }
    private fun save(d: CorteDocument) = AtomicFile.write(file(d.project.id), cj.encodeToString(d).toByteArray()) {
        cj.decodeFromString<CorteDocument>(it.toString(Charsets.UTF_8))
    }
    private fun result(d: CorteDocument, transaction: String? = null): JsonObject = buildJsonObject {
        put("ok", true); put("protocol", 3); put("revision", d.revision)
        put("project_id", d.project.id); put("project", cj.encodeToJsonElement(d.project))
        put("duration_us", d.project.timeline.durationUs); put("playhead_us", d.playheadUs)
        put("selection", cj.encodeToJsonElement(d.selection)); put("links", cj.encodeToJsonElement(d.links))
        put("undo_id", d.undo.lastOrNull()?.id); put("redo_id", d.redo.lastOrNull()?.id)
        put("transaction_id", transaction)
    }
    @Synchronized fun command(op: String, a: JsonObject): JsonObject {
        if(op=="media_analysis") {
            val cache=File(a.s("waveform_path"))
            val w=if(cache.exists()) Waveform.readFrom(cache) else {
                val builder=WaveformBuilder(16000,1)
                File(a.s("pcm_path")).inputStream().buffered().use { stream ->
                    val bytes=ByteArray(16384)
                    while(true) {
                        val n=stream.read(bytes);if(n<0) break
                        require(n%2==0) { "PCM incompleto" }
                        val buffer=java.nio.ByteBuffer.wrap(bytes,0,n).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val samples=ShortArray(n/2);buffer.get(samples);builder.feed(samples)
                    }
                }
                builder.build().also { it.writeTo(cache) }
            }
            val pixels=a.l("pixels",200).toInt().coerceIn(1,4096)
            val lo=FloatArray(pixels);val hi=FloatArray(pixels);w.fill(0,w.totalFrames,pixels,lo,hi)
            val clip=a["clip"]?.let { cj.decodeFromJsonElement<Clip>(it) }
            val ranges=if(clip != null) Silences.inTimeline(clip,w,a["threshold_db"]?.jsonPrimitive?.doubleOrNull ?: -40.0,a.l("min_ms",400)*1000,a.l("pad_ms",120)*1000) else emptyList()
            return buildJsonObject { put("ok",true);put("min",cj.encodeToJsonElement(lo.toList()));put("max",cj.encodeToJsonElement(hi.toList()));put("duration_us",w.durationUs);put("ranges",JsonArray(ranges.map { JsonArray(listOf(JsonPrimitive(it.startUs),JsonPrimitive(it.endUs))) })) }
        }
        if (op == "status") return buildJsonObject {
            put("ok", true); put("protocol", 3); put("engine", "Corte JVM local")
            put("operations", cj.encodeToJsonElement(AgentOps.NAMES))
            put("requires_revision", true); put("persistent_receipts", true)
        }
        if (op == "list_projects") return buildJsonObject {
            put("ok", true); put("projects", JsonArray(root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull {
                runCatching { val d = load(it.name); buildJsonObject { put("id", d.project.id); put("name", d.project.name); put("revision", d.revision) } }.getOrNull()
            }))
        }
        val id = a.s("project_id"); val target = file(id)
        if (op == "create_project") {
            if (target.exists()) return result(load(id))
            val now = System.currentTimeMillis()
            val t = Timeline(videoTracks = listOf(Track("v1", TrackKind.VIDEO, "Vídeo", TrackRole.PICTURE)),
                audioTracks = listOf(Track("a1", TrackKind.AUDIO, "Audio", TrackRole.DIALOGUE)))
            val settings=a["settings"]?.let { cj.decodeFromJsonElement<ProjectSettings>(it) } ?: ProjectSettings()
            val d = CorteDocument(project = Project(id = id, name = a.s("name", "Proyecto Nexus"), settings=settings, timeline = t, createdAtMs = now, modifiedAtMs = now))
            save(d); return result(d)
        }
        val d = load(id)
        if (op == "read_project") return result(d)
        if (op == "render_samples") {
            val source=a["render_project"]?.let { cj.decodeFromJsonElement<Project>(it) } ?: d.project
            require(source.id==id) { "Proyecto de render distinto" }
            if(a["render_project"] == null) require(a.l("expected_revision", -1) == d.revision) { "Montaje modificado antes de renderizar" }
            val t=source.timeline
            val plan=RenderPlanner.plan(t) { mid -> source.media(mid)?.let { File(java.net.URI(it.uri)).isFile } == true }
            val video=VideoEvaluator(t,plan); val audio=AudioEvaluator(t); val titles=TitleEvaluator(t)
            val times=a["times"]!!.jsonArray; require(times.size<=1000)
            return buildJsonObject {
                put("ok",true)
                put("samples",JsonArray(times.map { e ->
                    val time=e.jsonPrimitive.long
                    buildJsonObject {
                        put("time_us",time)
                        put("layers",JsonArray(plan.videoLanes.indices.reversed().mapNotNull { i ->
                            val seg=plan.videoLanes[i].segmentAt(time) ?: return@mapNotNull null
                            val c=t.clip(seg.clipId ?: return@mapNotNull null) ?: return@mapNotNull null
                            val state=video.evaluate(i,time)
                            buildJsonObject {
                                put("clip_id",c.id);put("media_id",c.mediaId)
                                put("source_us",seg.sourceStartUs+seg.speed.scale(time-seg.timelineStartUs))
                                put("alpha",state.alpha);put("x",state.positionX);put("y",state.positionY)
                                put("scale",state.scale);put("rotation",state.rotationDeg)
                                put("props",cj.encodeToJsonElement(c.video))
                                put("color_matrix",ColorMath.matrix(c.video.color)?.let { cj.encodeToJsonElement(it.toList()) } ?: JsonNull)
                            }
                        }))
                        put("titles",JsonArray(titles.visibleAt(time).map { title -> buildJsonObject {
                            put("text",cj.encodeToJsonElement(title.text));put("alpha",title.layer.alpha)
                            put("x",title.layer.positionX);put("y",title.layer.positionY);put("scale",title.layer.scale);put("rotation",title.layer.rotationDeg)
                        } }))
                        put("audio",JsonArray(plan.audioLanes.mapNotNull { lane ->
                            val seg=lane.segmentAt(time) ?: return@mapNotNull null
                            val cid=seg.clipId ?: return@mapNotNull null
                            val pan=audio.panOf(cid)
                            buildJsonObject { put("clip_id",cid);put("media_id",seg.mediaId);put("source_us",seg.sourceStartUs+seg.speed.scale(time-seg.timelineStartUs));put("gain",audio.gainAt(cid,time));put("left",pan.first);put("right",pan.second) }
                        }))
                    }
                }))
            }
        }
        val request = a.s("request_id"); require(request.isNotBlank()) { "Falta request_id; no se puede repetir una edición incierta" }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest((op + a.toString()).toByteArray()).joinToString("") { "%02x".format(it) }
        d.receipts[request]?.let {
            require(it.fingerprint == fingerprint) { "request_id reutilizado con otros parámetros" }
            // Return current state, never regress the UI to an old project snapshot.
            return JsonObject(result(d,it.reply.s("transaction_id")).toMap()+mapOf(
                "replayed" to JsonPrimitive(true), "applied_revision" to (it.reply["revision"] ?: JsonNull)))
        }
        require(a["expected_revision"] != null && a.l("expected_revision", -1) == d.revision) { "Proyecto modificado; vuelve a leerlo antes de editar" }
        val ctx = EditContext({ mediaId -> d.project.media(mediaId) })
        var next = d
        var edit: EditResult? = null
        when (op) {
            "apply_ops" -> edit = AgentOps.apply(d.project.timeline, ctx, a["ops"]!!.jsonArray, a.s("label", "Edición"))
            "import_media" -> {
                val media = cj.decodeFromJsonElement<List<MediaRef>>(a["media"]!!)
                require(media.isNotEmpty() && media.size <= 100) { "Selecciona de 1 a 100 medios" }
                val all = (d.project.media + media).distinctBy { it.id }
                next = d.copy(project = d.project.copy(media = all), links = d.links + (a["links"]?.jsonObject?.mapValues { it.value.jsonObject } ?: emptyMap()))
                if (a["place"]?.jsonPrimitive?.booleanOrNull != false) {
                    var offset = 0L
                    val placements = media.flatMap { m ->
                        val duration = m.durationUs ?: 5_000_000
                        val list = buildList {
                            if (m.hasVideo || m.kind == MediaKind.IMAGE) add(Placement(a.s("track_id", "v1"), if(m.kind == MediaKind.IMAGE) ClipKind.IMAGE else ClipKind.VIDEO, m.id, 0, duration, offset, name=m.displayName, linkKey=m.id))
                            if (m.hasAudio) add(Placement("a1", ClipKind.AUDIO, m.id, 0, duration, offset, name=m.displayName, linkKey=m.id))
                        }
                        offset += duration; list
                    }
                    edit = TimelineEditor.place(d.project.timeline, EditContext({ mid -> all.firstOrNull { it.id == mid } }), placements,
                        a.l("at_us", d.project.timeline.durationUs), if(a.s("mode") == "overwrite") EditMode.OVERWRITE else EditMode.INSERT)
                }
            }
            "undo", "redo" -> {
                val entry = (if(op == "undo") d.undo else d.redo).lastOrNull() ?: error("No hay edición para $op")
                require(a.s("transaction_id") == entry.id) { "La última edición es otra; no se deshará silenciosamente" }
                next = if(op == "undo") d.copy(project = d.project.copy(timeline=entry.before), undo=d.undo.dropLast(1), redo=d.redo+entry)
                    else d.copy(project=d.project.copy(timeline=entry.after), undo=d.undo+entry, redo=d.redo.dropLast(1))
            }
            "focus" -> next = d.copy(playheadUs = a.l("playhead_us").coerceIn(0, d.project.timeline.durationUs), selection = a["selection"]?.jsonArray?.map { it.jsonPrimitive.content }?.filter { d.project.timeline.clip(it) != null }?.toSet().orEmpty())
            "relink" -> {
                val m = cj.decodeFromJsonElement<MediaRef>(a["media"]!!)
                require(d.project.media(m.id) != null) { "Medio desconocido" }
                next=d.copy(project=d.project.copy(media=d.project.media.map { if(it.id==m.id) m else it }))
            }
            else -> error("Operación Windows no implementada: $op")
        }
        when(val r = edit) {
            is EditResult.Rejected -> error(r.reason)
            is EditResult.Applied -> next=next.copy(project=next.project.copy(timeline=r.timeline),
                undo=(d.undo+CorteEdit(request, r.label, d.project.timeline, r.timeline)).takeLast(100), redo=emptyList(),
                playheadUs=AgentFocus.timeOf(r.changes,r.timeline) ?: d.playheadUs, selection=AgentFocus.touchedClips(r.changes,r.timeline))
            null -> Unit
        }
        next = next.copy(revision=d.revision+(if(op=="focus") 0 else 1), project=next.project.copy(modifiedAtMs=System.currentTimeMillis()))
        val reply=result(next, request)
        // Receipts live with the edit itself: a crash cannot save one without the other.
        // Durable deduplication metadata stays small; history is independently bounded.
        val receipt=JsonObject(reply.filterKeys { it in setOf("ok","protocol","revision","project_id","transaction_id") })
        next=next.copy(receipts=next.receipts+(request to CorteReceipt(fingerprint,receipt)))
        save(next); return reply
    }
}

object CorteDesktopMain {
    @JvmStatic fun main(args: Array<String>) {
        val engine=CorteDesktop(File(args[0]))
        System.`in`.bufferedReader().forEachLine { line ->
            val reply=runCatching { val r=cj.parseToJsonElement(line).jsonObject; engine.command(r.s("op"),r["args"]!!.jsonObject) }
                .getOrElse { buildJsonObject { put("ok",false);put("error",it.message ?: it.javaClass.simpleName) } }
            println(reply)
        }
    }
}
