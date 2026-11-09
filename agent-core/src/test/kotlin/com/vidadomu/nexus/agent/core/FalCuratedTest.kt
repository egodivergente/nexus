package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.media.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class FalCuratedTest {
    private val config get() = FalCuratedModels.config
    private val video get() = config.models.first { it.id == "topaz/upscale/video/generative" }
    private fun json(s: String) = Json.parseToJsonElement(s)
    private fun response(host: String = "queue.fal.run") = json("""{"request_id":"job-1","status_url":"https://$host/topaz/upscale/requests/job-1/status","response_url":"https://$host/topaz/upscale/requests/job-1"}""")

    @Test fun `curated template is small usable and has no invented prices`() {
        val all = MediaTemplates.all // also checks initialization from template-first access
        assertEquals(1, all.count { it.config.id.startsWith("fal") })
        assertEquals(4, config.models.size)
        assertFalse(config.models.any { "turbo" in it.id.lowercase() })
        assertFalse(config.models.flatMap { it.params }.flatMap { it.options }.any { "Fast" in it })
        assertEquals(emptyList(), config.problems())
        assertTrue(config.models.all { it.falQueue && it.priceUsd == null })
        assertFalse(config.models.any { "kling" in it.id || "seedance" in it.id || "schnell" in it.id })
    }
    @Test fun `video payload uses selected model numeric scale and omits unset fps`() {
        val call = MediaEngine.buildCreate(config, video, MediaInput("", videoUrl = "https://example.test/source.mp4"))
        assertEquals("https://queue.fal.run/topaz/upscale/video/generative", call.url)
        assertEquals("Starlight Precise 2.6", call.body!!["model"]!!.jsonPrimitive.content)
        assertEquals(2.0, call.body!!["upscale_factor"]!!.jsonPrimitive.double)
        assertFalse(call.body!!.containsKey("target_fps"))
        assertFalse(call.body!!.containsKey("prompt"))
    }
    @Test fun `queue persists status and result separately then fetches svg`() {
        val model = config.models.last()
        val task = assertIs<CreateOutcome.Async>(MediaEngine.parseCreate(model, response())).taskId
        // The token survives persistence without depending on a process-local map.
        val persisted = Json.decodeFromString<String>(Json.encodeToString(task))
        assertEquals("https://queue.fal.run/topaz/upscale/requests/job-1/status", MediaEngine.buildPoll(config, model, persisted).url)
        assertIs<PollOutcome.Running>(MediaEngine.parsePoll(model, json("""{"status":"IN_QUEUE"}""")))
        assertNull(MediaEngine.resultCall(model, persisted, json("""{"status":"IN_PROGRESS"}""")))
        val result = MediaEngine.resultCall(model, persisted, json("""{"status":"COMPLETED"}"""))!!
        assertEquals("GET", result.method)
        assertEquals("https://queue.fal.run/topaz/upscale/requests/job-1", result.url)
        val done = assertIs<PollOutcome.Done>(MediaEngine.parseResult(model, json("""{"images":[{"url":"https://cdn.example.test/result.svg"}]}""")))
        assertEquals(listOf("https://cdn.example.test/result.svg"), done.urls)
    }
    @Test fun `queue rejects credential exfiltration and missing result location`() {
        assertIs<CreateOutcome.Failed>(MediaEngine.parseCreate(video, response("evil.example")))
        assertIs<CreateOutcome.Failed>(MediaEngine.parseCreate(video, json("""{"request_id":"job-1"}""")))
        val bad = response().jsonObject.toMutableMap().apply { put("response_url", JsonPrimitive("https://queue.fal.run/topaz/requests/other")) }
        assertIs<CreateOutcome.Failed>(MediaEngine.parseCreate(video, JsonObject(bad)))
    }
    @Test fun `completed provider error never becomes successful result fetch`() {
        val task = assertIs<CreateOutcome.Async>(MediaEngine.parseCreate(video, response())).taskId
        val status = json("""{"status":"COMPLETED","error":"model failed"}""")
        assertNull(MediaEngine.resultCall(video, task, status))
        assertIs<PollOutcome.Failed>(MediaEngine.parsePoll(video, status))
        assertIs<PollOutcome.Failed>(MediaEngine.parseResult(video, json("""{"detail":"failure"}""")))
    }
    @Test fun `range checks reject invalid fps and scale before submission`() {
        for (params in listOf(mapOf("target_fps" to "120"), mapOf("target_fps" to "23.5"), mapOf("upscale_factor" to "NaN"), mapOf("upscale_factor" to "5")))
            assertTrue(MediaEngine.inputProblems(video, MediaInput("", params, videoUrl = "https://example.test/v.mp4")).isNotEmpty())
        assertTrue(MediaEngine.inputProblems(video, MediaInput("", videoUrl = "https://example.test/v.mp4")).isEmpty())
    }
    @Test fun `minimax preserves selected variant and validates contract before spending`() {
        for (m in config.models.filter { FalCuratedModels.isMiniMax(it.id) }) {
            val input = MediaInput("Camera moves slowly", imageUrls = listOf("https://example.test/first.png"))
            assertTrue(MediaEngine.inputProblems(m, input).isEmpty())
            val call = MediaEngine.buildCreate(config, m, input)
            assertEquals("https://queue.fal.run/${m.id}", call.url)
            assertEquals(8.0, call.body!!["duration"]!!.jsonPrimitive.double)
            assertEquals("768P", call.body!!["resolution"]!!.jsonPrimitive.content)
            assertEquals("disabled", call.body!!["prompt_expansion_mode"]!!.jsonPrimitive.content)
            assertFalse(call.body!!.containsKey("seed"))
            assertTrue(MediaEngine.inputProblems(m, input.copy(prompt = "")).isNotEmpty())
            assertTrue(MediaEngine.inputProblems(m, input.copy(prompt = "x".repeat(50001))).isNotEmpty())
            for (params in listOf(mapOf("duration" to "16"), mapOf("duration" to "0.5"), mapOf("resolution" to "2K"), mapOf("prompt_expansion_mode" to "")))
                assertTrue(MediaEngine.inputProblems(m, input.copy(params = params)).isNotEmpty())
            assertTrue(MediaEngine.inputProblems(m, input.copy(imageUrls = input.imageUrls + "https://example.test/last.png")).isNotEmpty())
            assertTrue("31/10/2026" in m.description)
        }
    }
    @Test fun `legacy sync provider remains compatible`() {
        val m = MediaTemplates.all.first { it.config.models.first().mode == MediaMode.SYNC }.config.models.first()
        assertFalse(m.falQueue)
        assertIs<CreateOutcome.Done>(MediaEngine.parseCreate(m, json("""{"data":[{"url":"https://example.test/a.png"}]}""")))
    }
}
