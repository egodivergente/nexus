package com.vidadomu.nexus.provider.custom

import com.vidadomu.nexus.agent.core.media.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BytePlusMediaPresetTest {
    private val config = BytePlusMediaPreset.config()
    private val text = config.models.single { it.id == "seedance-2-5" }
    private val image = config.models.single { it.id == "seedance-2-5-i2v" }

    @Test fun validPresetAndOfficialRoute() {
        assertTrue(config.problems().toString(), config.problems().isEmpty())
        assertEquals(2, config.models.count { it.modelValue == BytePlusMediaPreset.SEEDANCE_25_MODEL })
        val call = MediaEngine.buildCreate(config, text, MediaInput("A walk", mapOf("duration" to "30")))
        assertEquals("POST", call.method)
        assertTrue(call.url.endsWith("/api/v3/contents/generations/tasks"))
        assertEquals("dreamina-seedance-2-5-260628", call.body!!["model"]!!.jsonPrimitive.content)
        assertEquals(30, call.body!!["duration"]!!.jsonPrimitive.int)
        assertFalse(call.body!!["duration"]!!.jsonPrimitive.isString)
        assertTrue(call.body!!["generate_audio"]!!.jsonPrimitive.boolean)
        assertNull(text.priceUsd)
    }

    @Test fun firstFrameUsesRoleAndAdaptiveRatio() {
        val input = MediaInput("", imageUrls = listOf("https://example.org/frame.png"))
        assertTrue(MediaEngine.inputProblems(image, input).isEmpty())
        val body = MediaEngine.buildCreate(config, image, input).body!!
        assertEquals("adaptive", body["ratio"]!!.jsonPrimitive.content)
        val frame = body["content"]!!.jsonArray[1].jsonObject
        assertEquals("first_frame", frame["role"]!!.jsonPrimitive.content)
        assertEquals(input.imageUrls.single(), frame["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
        assertFalse(MediaEngine.inputProblems(image, input.copy(params = mapOf("ratio" to "16:9"))).isEmpty())
        assertFalse(MediaEngine.inputProblems(image, input.copy(imageUrls = emptyList())).isEmpty())
        assertFalse(MediaEngine.inputProblems(image, input.copy(imageUrls = input.imageUrls + "https://example.org/extra.png")).isEmpty())
    }

    @Test fun durationBoundsAreValidatedBeforeAnyNetworkRequest() {
        listOf("4", "5", "30").forEach { value ->
            assertTrue(value, MediaEngine.inputProblems(text, MediaInput("A walk", mapOf("duration" to value))).isEmpty())
        }
        listOf("3", "31", "NaN", "5.5", "oops").forEach { value ->
            assertFalse(value, MediaEngine.inputProblems(text, MediaInput("A walk", mapOf("duration" to value))).isEmpty())
        }
    }

    @Test fun migrationAddsOnly25PreservingEditsAndRemovedLegacyModels() {
        val kept = config.models.single { it.id == "seedance-2-0" }.copy(name = "My endpoint", providerModelId = "ep-private")
        val old = config.copy(name = "Personal", extraHeaders = mapOf("X-Region" to "user"), models = listOf(kept))
        val next = BytePlusMediaPreset.withSeedance25(old)
        assertEquals(3, next.models.size)
        assertEquals(kept, next.models.first())
        assertEquals(old.copy(models = next.models), next)
        assertFalse(next.models.any { "fast" in it.id || "seedream" in it.id })
        assertEquals(next, BytePlusMediaPreset.withSeedance25(next))
    }

    @Test fun migrationPreservesExisting25AndDoesNotCreateDeletedProvider() {
        val edited = image.copy(providerModelId = "ep-custom", name = "Personal 2.5")
        val next = BytePlusMediaPreset.withSeedance25(config.copy(models = listOf(edited)))
        assertEquals(edited, next.models.first())
        assertEquals(2, next.models.size)
        val unrelated = config.copy(id = "my-other-provider", models = listOf(edited))
        assertEquals(unrelated, BytePlusMediaPreset.withSeedance25(unrelated))
        assertTrue(emptyList<MediaProviderConfig>().map(BytePlusMediaPreset::withSeedance25).isEmpty())
    }

    @Test fun asynchronousResultContractRetrievesExistingTask() {
        assertEquals(CreateOutcome.Async("task-25"), MediaEngine.parseCreate(text, Json.parseToJsonElement("""{"id":"task-25"}""")))
        val poll = MediaEngine.buildPoll(config, image, "task-25")
        assertEquals("GET", poll.method)
        assertTrue(poll.url.endsWith("/contents/generations/tasks/task-25"))
        assertEquals(PollOutcome.Done(listOf("https://example.org/video.mp4")), MediaEngine.parsePoll(image,
            Json.parseToJsonElement("""{"status":"succeeded","content":{"video_url":"https://example.org/video.mp4"}}""")))
        assertEquals(PollOutcome.Failed("quota"), MediaEngine.parsePoll(image,
            Json.parseToJsonElement("""{"status":"failed","error":{"message":"quota"}}""")))
    }
}
