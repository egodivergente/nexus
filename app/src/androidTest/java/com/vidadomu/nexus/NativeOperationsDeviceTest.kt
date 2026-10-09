package com.vidadomu.nexus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.assistant.NexusNativeOperations
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.tools.ToolInputException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class NativeOperationsDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication

    @Test fun nativeRenameAndConversionPreserveSourceAndOldUris() = runBlocking {
        check(BuildConfig.OFFLINE_QA) { "Only isolated QA package." }
        val runtime = AssistantRuntime.get(app)
        val operations = NexusNativeOperations(app, runtime)
        val folder = File(app.filesDir, "agent_media").apply { mkdirs() }
        val token = UUID.randomUUID().toString()
        val original = File(folder, "qa_$token.png")
        val named = File(folder, "renamed_$token.png")
        val image = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.BLUE)
        original.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
        val originalBytes = original.readBytes()
        val asset = runtime.assets.addAsset(Asset("", Uri.fromFile(original).toString(), AssetType.IMAGE,
            displayName = original.name, mimeType = "image/png", width = 320, height = 240, createdAt = System.currentTimeMillis()))
        var exported: File? = null
        try {
            val result = operations.run("rename_asset_file", buildJsonObject { put("asset_id", asset.id); put("file_name", named.name) }, AgentSession("qa", AgentMode.AGENT))
            assertTrue(result["file_renamed"]!!.jsonPrimitive.boolean)
            assertTrue(result["old_uri_preserved"]!!.jsonPrimitive.boolean)
            assertArrayEquals(originalBytes, original.readBytes())
            assertArrayEquals(originalBytes, named.readBytes())
            assertEquals(Uri.fromFile(named).toString(), runtime.assets.asset(asset.id)!!.uri)
            val converted = operations.run("convert_image_asset", buildJsonObject {
                put("asset_id", asset.id); put("format", "jpeg"); put("max_dimension", 128); put("aspect_ratio", "1:1")
            }, AgentSession("qa", AgentMode.AGENT))
            exported = File(Uri.parse(converted["uri"]!!.jsonPrimitive.content).path!!)
            val decoded = BitmapFactory.decodeFile(exported!!.path)
            assertEquals(128, decoded.width); assertEquals(128, decoded.height); decoded.recycle()
            assertTrue(converted["original_preserved"]!!.jsonPrimitive.boolean)
            assertArrayEquals(originalBytes, named.readBytes())
            val before = folder.listFiles()!!.map { it.name }.toSet()
            try {
                operations.run("rename_asset_file", buildJsonObject { put("asset_id", asset.id); put("file_name", "../outside.png") }, AgentSession("qa", AgentMode.AGENT))
                fail("Path traversal must be rejected")
            } catch (_: ToolInputException) {}
            assertEquals(before, folder.listFiles()!!.map { it.name }.toSet())
        } finally { original.delete(); named.delete(); exported?.delete() }
    }

    @Test fun conversionRespectsJpegOrientation() = runBlocking {
        check(BuildConfig.OFFLINE_QA)
        val runtime = AssistantRuntime.get(app)
        val operations = NexusNativeOperations(app, runtime)
        val file = File(File(app.filesDir, "agent_media").apply { mkdirs() }, "qa_${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        android.media.ExifInterface(file.path).apply {
            setAttribute(android.media.ExifInterface.TAG_ORIENTATION, "6"); saveAttributes()
        }
        val asset = runtime.assets.addAsset(Asset("", Uri.fromFile(file).toString(), AssetType.IMAGE,
            displayName = file.name, mimeType = "image/jpeg", createdAt = System.currentTimeMillis()))
        var output: File? = null
        try {
            val converted = operations.run("convert_image_asset", buildJsonObject {
                put("asset_id", asset.id); put("format", "png"); put("max_dimension", 128)
            }, AgentSession("qa", AgentMode.AGENT))
            output = File(Uri.parse(converted["uri"]!!.jsonPrimitive.content).path!!)
            val decoded = BitmapFactory.decodeFile(output!!.path)
            assertEquals(96, decoded.width); assertEquals(128, decoded.height); decoded.recycle()
            assertTrue(file.isFile)
        } finally { file.delete(); output?.delete() }
    }

    @Test fun recoveryChecksTheOriginalProviderAndDoesNotSubmitFlowQueue() = runBlocking {
        check(BuildConfig.OFFLINE_QA)
        val runtime = AssistantRuntime.get(app)
        val operations = NexusNativeOperations(app, runtime)
        val id = "qa_${UUID.randomUUID()}"
        val job = com.vidadomu.nexus.core.model.GenerationJob(id = id, providerJobId = "mock_existing",
            providerId = "mock_provider", modelId = "fixture", modality = com.vidadomu.nexus.core.model.Modality.VIDEO,
            prompt = "QA fixture", status = com.vidadomu.nexus.core.model.GenerationStatus.GENERATING)
        try {
            app.generationRepository.updateJob(job)
            assertEquals("COMPLETED", runtime.backend.status(id).status)
            app.generationRepository.updateJob(job.copy(status = com.vidadomu.nexus.core.model.GenerationStatus.FAILED))
            val recovered = operations.run("recover_generation", buildJsonObject { put("job_id", id) }, AgentSession("qa", AgentMode.AGENT))
            assertTrue(recovered["provider_checked"]!!.jsonPrimitive.boolean)
            assertFalse(recovered["resubmitted"]!!.jsonPrimitive.boolean)
            assertEquals("COMPLETED", recovered["job"]!!.jsonObject["status"]!!.jsonPrimitive.content)
            app.generationRepository.updateJob(job.copy(providerId = "missing_provider"))
            assertTrue(app.generationRepository.pollJob(id).isFailure)
            assertEquals(com.vidadomu.nexus.core.model.GenerationStatus.GENERATING, app.generationRepository.getJob(id)!!.status)
            app.generationRepository.updateJob(job.copy(providerId = "google_flow", providerJobId = "queue_$id",
                status = com.vidadomu.nexus.core.model.GenerationStatus.QUEUED))
            val queued = operations.run("recover_generation", buildJsonObject { put("job_id", id) }, AgentSession("qa", AgentMode.AGENT))
            assertFalse(queued["resubmitted"]!!.jsonPrimitive.boolean)
            assertFalse(queued["tracking_restored"]!!.jsonPrimitive.boolean)
            assertEquals("QUEUED", queued["job"]!!.jsonObject["status"]!!.jsonPrimitive.content)
        } finally { app.generationRepository.deleteJob(id) }
    }

    @Test fun operationalSettingsCannotWriteCredentialsAndValidateBeforeChanging() = runBlocking {
        check(BuildConfig.OFFLINE_QA)
        val runtime = AssistantRuntime.get(app)
        val operations = NexusNativeOperations(app, runtime)
        val prefs = app.getSharedPreferences("assistant_config_v1", 0)
        val saved = runtime.settings.load()
        val keyBefore = app.secureStorage.getAtlasApiKey()
        val awsBefore = runtime.settings.awsCredentials()
        try {
            operations.run("update_nexus_settings", buildJsonObject { put("agent_model", "sonnet-4.6"); put("max_tokens", 5000) }, AgentSession("qa", AgentMode.AGENT))
            assertEquals("us.anthropic.claude-sonnet-4-6", prefs.getString("model_id", ""))
            assertEquals(5000, prefs.getInt("max_tokens", 0))
            try {
                operations.run("update_nexus_settings", buildJsonObject { put("agent_model", "opus-4.6"); put("max_tokens", -1) }, AgentSession("qa", AgentMode.AGENT))
                fail("Invalid tokens must reject the whole mutation")
            } catch (_: ToolInputException) {}
            assertEquals("us.anthropic.claude-sonnet-4-6", prefs.getString("model_id", ""))
            assertEquals(keyBefore, app.secureStorage.getAtlasApiKey())
            assertEquals(awsBefore, runtime.settings.awsCredentials())
            val read = operations.run("get_nexus_settings", JsonObject(emptyMap()), AgentSession("qa", AgentMode.AGENT))
            assertFalse(read.keys.any { "key" in it || "secret" in it || "token" == it })
        } finally {
            runtime.settings.updateOperational(saved.modelId, saved.maxTokens, saved.sendImages, saved.excludedFamilies)
        }
    }
}
