package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.agent.AttachmentImageLoader
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.llm.AntigravityProvider
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.tools.AgentTool
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.CutTools
import com.vidadomu.nexus.agent.core.tools.NexusToolkit
import com.vidadomu.nexus.agent.core.tools.ToolCategory
import com.vidadomu.nexus.agent.core.tools.ToolDeps
import com.vidadomu.nexus.agent.core.tools.ToolOutcome
import com.vidadomu.nexus.agent.core.tools.turnContext
import com.vidadomu.nexus.cut.command.CoreCapabilities
import com.vidadomu.nexus.cut.command.CutRegistry
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.model.ClipKind
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.model.MediaRef
import com.vidadomu.nexus.cut.model.TrackRole
import com.vidadomu.nexus.cut.storage.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.awt.Color
import java.awt.Polygon
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.text.Normalizer
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Banco de ACIERTO del agente real contra el puente AXEL, en las tareas complejas de Nexus: guion con reglas del
 * proyecto, conversación larga, montaje en Nexus Cut y revisión de resultados con imagen. Cada caso se comprueba
 * con criterios automáticos sobre el estado real (planes, montaje) o el texto. Nunca envía generaciones.
 * Solo con -Dnexus.bench.endpoint (ver BridgeLatencyBench).
 */
class AgentQualityBench {
    private data class Brain(val label: String, val engine: AntigravityProvider.BridgeEngine?, val model: String, val effort: String? = null) {
        /** null = no es del puente: va por Bedrock con las credenciales del equipo (~/.aws). */
        val bedrock: Boolean get() = engine == null
    }
    private val brains = listOf(
        Brain("GPT-6.1 Terra (medio)", AntigravityProvider.BridgeEngine.CODEX, "gpt-6.1-terra", "medium"),
        Brain("GPT-6.1 Sol (medio)", AntigravityProvider.BridgeEngine.CODEX, "gpt-6.1-sol", "medium"),
        Brain("Claude Sonnet 5.5 (AGY)", AntigravityProvider.BridgeEngine.ANTIGRAVITY, "claude-sonnet-5-5"),
        Brain("Claude Opus 5.5 (AGY)", AntigravityProvider.BridgeEngine.ANTIGRAVITY, "claude-opus-5-5"),
        Brain("Grok 4.6", AntigravityProvider.BridgeEngine.GROK, "grok-4.6"),
        Brain("Claude Haiku 5.5 (Bedrock)", null, "us.anthropic.claude-haiku-5-5"),
        Brain("Gemini 4 Hydrogen (medio)", AntigravityProvider.BridgeEngine.ANTIGRAVITY, "gemini-4-hydrogen-medium"),
        Brain("Gemini 4 Argon (medio)", AntigravityProvider.BridgeEngine.ANTIGRAVITY, "gemini-4-argon-medium"),
    )

    private class Run(val brain: Brain, val endpoint: String, val run: Int) {
        val h = Harness()
        val dir: File = Files.createTempDirectory("bench-cut").toFile()
        val cut = CutWorkspace(ProjectStore(dir), CutRegistry().register(CoreCapabilities.all()), io = Dispatchers.Unconfined)
        val deps = ToolDeps(h.registry, h.skills, h.cost, h.router, h.guard, h.executor, h.assets, clock = { h.now }, probeFrameRate = { h.frameRate }, cut = cut)
        val trace = mutableListOf<JsonObject>()
        val writes = NexusToolkit.all(deps).filter { it.category == ToolCategory.ORGANIZE }.map { it.name }.toSet()
        val images = mutableMapOf<String, ByteArray>()
        val conversation = "q-${brain.model}-$run-${System.nanoTime()}"
        val orchestrator: AgentOrchestrator = run {
            val tools = NexusToolkit.all(deps).map { t -> object : AgentTool by t {
                override suspend fun run(input: JsonObject, session: AgentSession): ToolOutcome = t.run(input, session).also { out ->
                    trace += buildJsonObject { put("tool", t.name); put("error", out.isError); put("result", out.result.toString().take(240)) }
                }
            } }
            AgentOrchestrator(
                brain.engine?.let { BenchProviders.bridge(endpoint, it, conversation, brain.effort) } ?: BenchProviders.bedrock(),
                AgentConfig(provider = brain.engine?.providerId ?: "bedrock-direct", modelId = brain.model, region = "-", endpoint = endpoint, mode = AgentMode.AGENT, sendImagesToLlm = true, maxToolRounds = 10),
                tools, h.skills, h.assets,
                images = AttachmentImageLoader { a -> images[a.id]?.let { ContentBlock.Image("png", it) } },
                uiContext = { listOfNotNull(deps.orders.turnContext(h.guard), CutTools.context(cut)).joinToString("\n\n") },
            )
        }
    }

    @Test fun bench() {
        val endpoint = System.getProperty("nexus.bench.endpoint") ?: return
        val runs = System.getProperty("nexus.bench.runs")?.toIntOrNull() ?: 2
        val only = System.getProperty("nexus.bench.only")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        val cases = System.getProperty("nexus.bench.cases")?.split(',')?.map { it.trim() } ?: listOf("guion", "larga", "cut", "revisar")
        val out = File(System.getProperty("nexus.bench.out") ?: "build/quality-bench.jsonl").apply { parentFile?.mkdirs(); delete() }
        val active = brains.filter { b -> only == null || only.any { it in b.model } }
        for (run in 1..runs) for (case in cases) for (brain in if (run % 2 == 1) active else active.reversed()) {
            val r = Run(brain, endpoint, run)
            val started = System.nanoTime()
            val line = try {
                val (checks, extra) = runBlocking { withTimeout(9 * 60_000L) { when (case) {
                    "guion" -> guion(r); "larga" -> larga(r); "cut" -> cut(r); "revisar" -> revisar(r)
                    else -> error("caso desconocido $case")
                } } }
                buildJsonObject {
                    put("run", run); put("case", case); put("brain", brain.label); put("model", brain.model)
                    put("wall_ms", (System.nanoTime() - started) / 1_000_000)
                    put("passed", checks.count { it.value }); put("total", checks.size)
                    put("checks", JsonObject(checks.mapValues { JsonPrimitive(it.value) }))
                    put("tools", JsonArray(r.trace.map { JsonPrimitive(it["tool"].toString().trim('"') + if (it["error"].toString() == "true") "!" else "") }))
                    for ((k, v) in extra) put(k, v)
                }
            } catch (e: Throwable) {
                buildJsonObject { put("run", run); put("case", case); put("brain", brain.label); put("model", brain.model); put("wall_ms", (System.nanoTime() - started) / 1_000_000); put("error", (e.message ?: e.javaClass.simpleName).take(300)) }
            } finally { r.dir.deleteRecursively() }
            out.appendText(line.toString() + "\n")
            println("QBENCH $line")
        }
    }

    private fun fold(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    private fun noWrites(r: Run) = r.trace.none { it["tool"].toString().trim('"') in r.writes }

    // ---------------------------------------------------------------- 1. guion con las reglas del proyecto

    private suspend fun guion(r: Run): Pair<Map<String, Boolean>, Map<String, JsonPrimitive>> {
        val p = r.h.assets.createProject("Corto de Mateo")
        r.h.assets.updateProjectMemory(p.id) { it.copy(
            synopsis = "Mateo, un niño de 8 años, se pierde en las fiestas de Madrid y lo encuentra su hermana Lucía.",
            tone = "Tierno y luminoso, nunca de miedo", visualStyle = "Luz de día, colores cálidos",
            rules = listOf("Mateo siempre lleva su chaqueta verde.", "Ninguna escena ocurre de noche.", "Lucía tiene 15 años y lleva gafas redondas."),
        ) }
        r.h.assets.ensureCharacter("Mateo", p.id, "8 años, pelo rizado castaño, chaqueta verde")
        r.h.assets.ensureCharacter("Lucía", p.id, "15 años, gafas redondas")
        val o = r.orchestrator
        val res = o.send(o.newConversation(r.conversation, AgentMode.AGENT, activeProjectId = p.id),
            "Escribe aquí en texto la escena 3 del corto: Mateo y Lucía se separan entre la gente en la pradera de San Isidro y se reencuentran. Unas 120 palabras. No crees ni guardes nada, solo escríbela.")
        val t = fold(res.text)
        val words = res.text.split(Regex("\\s+")).count { it.any(Char::isLetter) }
        return mapOf(
            "chaqueta_verde" to ("chaqueta" in t && "verde" in t),
            "gafas_lucia" to ("gafas" in t),
            "nunca_de_noche" to !Regex("noche|anochec|nocturn|luz de la luna").containsMatchIn(t),
            "san_isidro" to ("san isidro" in t),
            "longitud_70_220" to (words in 70..220),
            "no_guarda_nada" to noWrites(r),
        ) to mapOf("words" to JsonPrimitive(words), "text" to JsonPrimitive(res.text.replace('\n', ' ').take(400)))
    }

    // ---------------------------------------------------------------- 2. conversación larga con reglas iniciales

    private suspend fun larga(r: Run): Pair<Map<String, Boolean>, Map<String, JsonPrimitive>> {
        val o = r.orchestrator
        val conv = o.newConversation(r.conversation, AgentMode.AGENT)
        val times = mutableListOf<Long>()
        suspend fun say(text: String, attach: List<Attachment> = emptyList()) = run {
            val s = System.nanoTime(); o.send(conv, text, attach).also { times += (System.nanoTime() - s) / 1_000_000 }
        }
        say("Regla para todo este chat: los vídeos siempre de 10 segundos y en 1080p, y Mateo lleva chaqueta verde. Responde solo «entendido».")
        say("Prepara una imagen de Mateo en el Retiro con Seedream 5.0 Pro, en formato horizontal. No la envíes.")
        say("Ahora prepara un vídeo de esa escena con Seedance 2.0 a partir de esta foto. No lo envíes.", listOf(Attachment("foto1", "file:///bench/mateo.jpg", "image/jpeg", "foto1.jpg", 1920, 1080)))
        val last = say("¿Qué reglas te di al principio? Contesta en una línea.")
        val plans = r.h.guard.plans()
        val seedream = plans.lastOrNull { it.ref == SEEDREAM }
        val seedance = plans.lastOrNull { it.ref == SEEDANCE }
        val t = fold(last.text)
        return mapOf(
            "imagen_seedream_horizontal" to (seedream?.params?.get("size") == "2560*1440"),
            "video_seedance_10s" to (seedance?.params?.get("duration") == "10"),
            "video_seedance_1080p" to (seedance?.params?.get("resolution") == "1080p"),
            "recuerda_10s" to ("10" in t),
            "recuerda_1080p" to ("1080" in t),
            "recuerda_chaqueta_verde" to ("verde" in t),
            "nada_enviado" to r.h.backend.submitted.isEmpty(),
        ) to mapOf("turn_ms" to JsonPrimitive(times.joinToString("/")), "last" to JsonPrimitive(last.text.replace('\n', ' ').take(200)))
    }

    // ---------------------------------------------------------------- 3. montaje de varios pasos en Nexus Cut

    private suspend fun cut(r: Run): Pair<Map<String, Boolean>, Map<String, JsonPrimitive>> {
        val created = r.cut.create("Mateo", nexusProjectId = null)
        r.cut.store.save(created.state.value.project.copy(media = listOf(
            MediaRef("m1", "content://1", "Mateo PP toma 1", MediaKind.VIDEO, 8_000_000, 1920, 1080, hasVideo = true, hasAudio = true),
            MediaRef("m2", "content://2", "Mateo PP toma 2", MediaKind.VIDEO, 6_000_000, 1920, 1080, hasVideo = true, hasAudio = true),
            MediaRef("m3", "content://3", "Tema principal", MediaKind.AUDIO, 60_000_000, hasAudio = true),
        )))
        r.cut.close(created.projectId); r.cut.setActive(created.projectId)
        val session = r.cut.session(created.projectId)
        val o = r.orchestrator
        o.send(o.newConversation(r.conversation, AgentMode.AGENT),
            "En el montaje abierto de Nexus Cut: coloca las dos tomas de Mateo en la pista de imagen con la toma 2 primero, quita el tramo entre los segundos 3 y 5 de la línea de tiempo, pon el rótulo «Mateo» al principio durante 2 segundos y añade la música «Tema principal» en la pista de música a -12 dB. Hazlo todo como un único cambio que yo pueda deshacer de una vez.")
        val st = session.state.value
        val tl = st.project.timeline
        val v1 = tl.track("V1")?.clips.orEmpty().filter { it.kind != ClipKind.TEXT }
        val order = v1.map { it.mediaId }.fold(listOf<String>()) { acc, m -> if (acc.lastOrNull() == m) acc else acc + m }
        val title = tl.videoTracks.flatMap { it.clips }.firstOrNull { it.kind == ClipKind.TEXT }
        val music = tl.audioTracks.firstOrNull { it.role == TrackRole.MUSIC }?.clips.orEmpty().firstOrNull { it.mediaId == "m3" }
        val edits = r.trace.count { it["tool"].toString().trim('"') == "cut_edit" }
        val failed = r.trace.count { it["tool"].toString().trim('"') == "cut_edit" && it["error"].toString() == "true" }
        return mapOf(
            "orden_toma2_toma1" to (order == listOf("m2", "m1")),
            "quita_3_a_5_dura_12s" to (kotlin.math.abs((tl.track("V1")?.endUs ?: 0) - 12_000_000) <= 40_000),
            "rotulo_mateo_0_a_2s" to (title?.text?.text?.trim()?.trim('«', '»', '"') == "Mateo" && title.timelineStartUs == 0L && kotlin.math.abs(title.durationUs - 2_000_000) <= 40_000),
            "musica_menos12db" to (music != null && kotlin.math.abs(music.audio.gainDb + 12.0) <= 0.5),
            "un_solo_paso" to (st.revision == 1L),
        ) to mapOf("cut_edits" to JsonPrimitive(edits), "cut_edit_errors" to JsonPrimitive(failed), "revision" to JsonPrimitive(st.revision),
            "v1" to JsonPrimitive(v1.joinToString { "${it.mediaId}@${it.timelineStartUs / 1000}+${it.durationUs / 1000}" }))
    }

    // ---------------------------------------------------------------- 4. revisar resultados con imagen

    private fun png(draw: (java.awt.Graphics2D) -> Unit): ByteArray {
        val img = BufferedImage(768, 512, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics(); draw(g); g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }

    private val boat by lazy { png { g ->
        g.color = Color(0xF2, 0x8C, 0x38); g.fillRect(0, 0, 768, 260)                 // cielo de atardecer
        g.color = Color(0xFF, 0xD2, 0x5A); g.fillOval(520, 150, 140, 140)              // sol bajo
        g.color = Color(0x1E, 0x5A, 0xA8); g.fillRect(0, 260, 768, 252)                // mar
        g.color = Color(0x0A, 0x2E, 0x8C); g.fillPolygon(Polygon(intArrayOf(220, 520, 470, 270), intArrayOf(300, 300, 360, 360), 4)) // casco azul
        g.color = Color.WHITE; g.fillPolygon(Polygon(intArrayOf(370, 370, 470), intArrayOf(120, 295, 295), 3))                 // vela
        g.color = Color(0x55, 0x33, 0x22); g.fillRect(366, 110, 6, 190)                                                            // mástil
    } }
    private val car by lazy { png { g ->
        g.color = Color(0x9C, 0xD3, 0xF0); g.fillRect(0, 0, 768, 220)                  // cielo de día
        g.color = Color(0x4C, 0xA0, 0x46); g.fillRect(0, 220, 768, 120)                // hierba
        g.color = Color(0x55, 0x55, 0x58); g.fillRect(0, 340, 768, 172)                // carretera
        g.color = Color(0xD2, 0x1E, 0x1E); g.fillRect(230, 270, 300, 90); g.fillRect(290, 220, 170, 60) // coche rojo
        g.color = Color.BLACK; g.fillOval(260, 335, 60, 60); g.fillOval(440, 335, 60, 60)
    } }

    private suspend fun revisar(r: Run): Pair<Map<String, Boolean>, Map<String, JsonPrimitive>> {
        // El orden se alterna entre rondas: el acierto no puede depender de la posición. Nombres neutros.
        val boatFirst = r.run % 2 == 1
        r.images["img1"] = if (boatFirst) boat else car
        r.images["img2"] = if (boatFirst) car else boat
        val o = r.orchestrator
        val res = o.send(o.newConversation(r.conversation, AgentMode.AGENT),
            "Revisa estas dos imágenes contra el encargo «un barco azul en el mar al atardecer». Responde exactamente en dos líneas: «Imagen 1: CUMPLE o NO CUMPLE — motivo» e «Imagen 2: CUMPLE o NO CUMPLE — motivo». No generes nada.",
            listOf(Attachment("img1", "file:///bench/imagen1.png", "image/png", "imagen1.png", 768, 512), Attachment("img2", "file:///bench/imagen2.png", "image/png", "imagen2.png", 768, 512)))
        val t = fold(res.text)
        fun verdict(n: Int): Boolean? = Regex("imagen\\s*$n\\W{0,6}(no cumple|cumple)").find(t)?.groupValues?.get(1)?.let { it == "cumple" }
        val v1 = verdict(1); val v2 = verdict(2)
        return mapOf(
            "barco_cumple" to ((if (boatFirst) v1 else v2) == true),
            "coche_no_cumple" to ((if (boatFirst) v2 else v1) == false),
            "nada_enviado" to r.h.backend.submitted.isEmpty(),
        ) to mapOf("boat_first" to JsonPrimitive(boatFirst), "text" to JsonPrimitive(res.text.replace('\n', ' ').take(300)))
    }
}
