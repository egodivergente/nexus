package com.vidadomu.nexus

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.assistant.*
import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.tools.AgentSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class AgentExperienceDeviceTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
    @Test fun localBudgetIsOptInAndHistoryArchivingPreservesTheJob()=runBlocking {
        check(BuildConfig.OFFLINE_QA)
        app.ready.await()
        val runtime=AssistantRuntime.get(app)
        runtime.settings.saveLlmLimits(3.0,9.0)
        runtime.settings.setBudgetEnabled(false)
        assertEquals(Double.MAX_VALUE,runtime.settings.llmLimits().dailyUsd,0.0)
        assertEquals(Int.MAX_VALUE,runtime.settings.llmLimits().perTurnCalls)
        assertEquals(9.0,runtime.settings.configuredLlmLimits().dailyUsd,0.0)
        runtime.settings.setBudgetEnabled(true)
        assertEquals(9.0,runtime.settings.llmLimits().dailyUsd,0.0)
        runtime.settings.setBudgetEnabled(false)
        val failed=com.vidadomu.nexus.core.model.GenerationJob("qa-failed-archive",providerId="mock_provider",modelId="fixture",
            modality=com.vidadomu.nexus.core.model.Modality.IMAGE,prompt="Failed QA",status=com.vidadomu.nexus.core.model.GenerationStatus.FAILED)
        app.generationRepository.updateJob(failed)
        kotlinx.coroutines.withTimeout(5000) {while(app.generationRepository.allJobs.value.none {it.id==failed.id}) kotlinx.coroutines.delay(50)}
        NexusNativeOperations(app,runtime).run("archive_failed_generations",buildJsonObject {},AgentSession("archive-qa",AgentMode.AGENT))
        assertTrue(app.getSharedPreferences("history_archive_v1",0).getStringSet("ids",emptySet())!!.contains(failed.id))
        assertEquals(failed,app.generationRepository.getJob(failed.id))
    }
    @Test fun pdfWithImageAndScriptAreRealIndexedReadableFiles()=runBlocking {
        check(BuildConfig.OFFLINE_QA)
        app.ready.await()
        val runtime=AssistantRuntime.get(app);val ops=NexusNativeOperations(app,runtime)
        val session=AgentSession("artifact-qa",AgentMode.AGENT)
        val folder=File(app.filesDir,"agent_media").apply {mkdirs()}
        val image=File(folder,"test-${UUID.randomUUID()}.png")
        Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.RED);image.outputStream().use {compress(Bitmap.CompressFormat.PNG,100,it)};recycle()}
        val source=runtime.assets.addAsset(Asset("",Uri.fromFile(image).toString(),AssetType.IMAGE,displayName="Red reference",mimeType="image/png",createdAt=System.currentTimeMillis()))
        val pdf=ops.run("create_document",buildJsonObject {
            put("title","Prueba PDF");put("format","pdf");put("blocks_json",buildJsonArray {
                add(buildJsonObject {put("text","Texto real de prueba.\n".repeat(100))})
                add(buildJsonObject {put("image_asset_id",source.id);put("caption","Imagen roja insertada")})
            }.toString())
        },session)
        val pdfFile=File(Uri.parse(pdf["uri"]!!.jsonPrimitive.content).path!!)
        assertTrue(pdfFile.readBytes().take(5).toByteArray().decodeToString().startsWith("%PDF"))
        ParcelFileDescriptor.open(pdfFile,ParcelFileDescriptor.MODE_READ_ONLY).use {fd -> PdfRenderer(fd).use {renderer ->
            assertTrue(renderer.pageCount>1)
            val page=renderer.openPage(renderer.pageCount-1);val bmp=Bitmap.createBitmap(595,842,Bitmap.Config.ARGB_8888)
            page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            assertTrue((0 until bmp.height step 5).any {y -> (0 until bmp.width step 5).any {x -> val c=bmp.getPixel(x,y);Color.red(c)>200 && Color.green(c)<80 && Color.blue(c)<80}})
            bmp.recycle();page.close()
        }}
        val script=ops.run("create_document",buildJsonObject {put("title","guion");put("format","py");put("content","print('Nexus')\n")},session)
        val scriptId=script["asset_id"]!!.jsonPrimitive.content
        val text=ops.run("read_library_text",buildJsonObject {put("asset_id",scriptId)},session)
        assertEquals("print('Nexus')",text["text"]!!.jsonPrimitive.content.trim())
        ops.run("view_library_asset",buildJsonObject {put("asset_id",source.id)},session)
        assertEquals(source.id,session.attachments.last().assetId)
        val external=File(app.getExternalFilesDir(null),"qa-library-${UUID.randomUUID()}.png")
        image.copyTo(external)
        try {
            val externalAsset=runtime.assets.addAsset(source.copy(id="",uri=Uri.fromFile(external).toString()))
            val converted=ops.run("convert_image_asset",buildJsonObject {put("asset_id",externalAsset.id);put("format","jpeg");put("max_dimension",64)},session)
            val outputId=converted["asset_id"]!!.jsonPrimitive.content
            val bitmap=BitmapFactory.decodeFile(Uri.parse(runtime.assets.asset(outputId)!!.uri).path)
            assertNotNull(bitmap)
            assertEquals(64,bitmap!!.width)
            bitmap.recycle()
        } finally {external.delete()}
        val deleted=ops.run("delete_library_assets",buildJsonObject {put("asset_ids",buildJsonArray {add(scriptId)})},session)
        assertEquals(1,deleted["removed"]!!.jsonPrimitive.int)
        assertTrue(File(Uri.parse(script["uri"]!!.jsonPrimitive.content).path!!).exists())
        val list=ops.run("list_library_assets",buildJsonObject {put("query","guion")},session)
        assertTrue(list["assets"]!!.jsonArray.none {it.jsonObject["asset_id"]?.jsonPrimitive?.content==scriptId})
    }
}
