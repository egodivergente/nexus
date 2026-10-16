package com.vidadomu.nexus

import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.assistant.*
import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.config.AgentMode
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import androidx.test.core.app.ActivityScenario
import java.io.File
import java.util.UUID

/** Runs only on isolated OfflineQa, with bundled synthetic clips/public speech. No LLM/provider calls. */
class LocalMediaEditingTest {
    @Test fun audioAnalysisMeasuresBundledToneAndCreatesReadableReportWithoutWhisper() = runBlocking {
        val source=asset("red.mp4")
        val report=run("analyze_audio",listOf(source)).single()
        val measured=Json.parseToJsonElement(File(android.net.Uri.parse(report.uri).path!!).readText()).jsonObject
        assertTrue(measured["duration_seconds"]!!.jsonPrimitive.double in 2.8..3.3)
        val bands=measured["bands"]!!.jsonArray
        assertTrue(bands[1].jsonObject["relative_energy_percent"]!!.jsonPrimitive.double>95)
        assertTrue(measured["rms_dbfs"]!!.jsonPrimitive.double in -45.0..-3.0)
        assertTrue(measured["limits"]!!.jsonPrimitive.content.contains("No mide LUFS"))
    }
    @Test fun failedEditDoesNotBlockTheNextQueuedEdit()=runBlocking {
        app.ready.await()
        val source=asset("red.mp4")
        val store=MediaEditJobs(app)
        // Model an input that disappeared after validation, before the worker read it.
        val missing=MediaEditJob(UUID.randomUUID().toString(),MediaEditSpec.parse("trim",input("""{"asset_id":"missing-after-validation","end_ms":1000}"""),null))
        store.write(missing)
        val first=androidx.work.OneTimeWorkRequestBuilder<MediaEditWorker>().setInitialDelay(5,java.util.concurrent.TimeUnit.SECONDS)
            .setInputData(androidx.work.workDataOf("edit_job_id" to missing.id)).build()
        androidx.work.WorkManager.getInstance(app).enqueueUniqueWork("nexus-local-media-edits",androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,first).result.get()
        val next=store.start("trim",input("""{"asset_id":"${source.id}","end_ms":1000}"""),com.vidadomu.nexus.agent.core.tools.AgentSession("media-qa-chain",com.vidadomu.nexus.agent.core.config.AgentMode.AGENT))
        val nextId=next["edit_job_id"]!!.jsonPrimitive.content
        assertEquals(androidx.work.WorkInfo.State.ENQUEUED,requireNotNull(androidx.work.WorkManager.getInstance(app).getWorkInfoById(first.id).get()).state)
        withTimeout(30000) {
            while(store.read(nextId).state in setOf("QUEUED","PROCESSING")) delay(100)
        }
        assertEquals("FAILED",store.read(missing.id).state)
        assertEquals("COMPLETED",store.read(nextId).state)
        assertTrue(File(android.net.Uri.parse(source.uri).path!!).isFile)
    }
    private var activity:ActivityScenario<MainActivity>?=null
    @Before fun foreground() {
        check(BuildConfig.OFFLINE_QA && listOf(".qa",".mediaqa",".uiqa").any(app.packageName::endsWith))
        if(InstrumentationRegistry.getArguments().getString("background")!="true")
            activity=ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun closeActivity() {activity?.close()}
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
    private suspend fun asset(name:String):Asset {
        check(BuildConfig.OFFLINE_QA && listOf(".qa",".mediaqa",".uiqa").any(app.packageName::endsWith))
        app.ready.await()
        val file=File(app.filesDir,"agent_media/qa_${UUID.randomUUID()}_$name").apply {parentFile!!.mkdirs()}
        InstrumentationRegistry.getInstrumentation().context.assets.open("media-edit/$name").use {input ->file.outputStream().use {out ->input.copyTo(out)}}
        return AssistantRuntime.get(app).assets.addAsset(Asset("",android.net.Uri.fromFile(file).toString(),
            if(name.endsWith("wav")) AssetType.AUDIO else AssetType.VIDEO,displayName=name,createdAt=System.currentTimeMillis()))
    }
    private fun input(raw:String)=Json.parseToJsonElement(raw).jsonObject
    private suspend fun run(action:String,sources:List<Asset>,extra:String=""):List<Asset> {
        val json=input("""{"asset_ids":[${sources.joinToString {"\"${it.id}\""}}]$extra}""")
        val spec=MediaEditSpec.parse(action,json,null)
        val engine=AgentVideoOperations(app)
        val ids=engine.execute(MediaEditJob(UUID.randomUUID().toString(),spec)) {}
        val outputs=ids.map {AssistantRuntime.get(app).assets.asset(it)!!}
        println("MEDIA_QA $action "+outputs.joinToString {it.uri})
        assertTrue(outputs.all {File(android.net.Uri.parse(it.uri).path!!).isFile})
        return outputs
    }
    private fun mimes(asset:Asset)=AgentVideoOperations(app).inspect(asset.id)["tracks"]!!.jsonArray.map {it.jsonObject["mime"]!!.jsonPrimitive.content}

    /** Decode the output and measure actual 440/660 Hz fixture tones, rather than just track presence. */
    private fun tones(asset:Asset):Pair<Double,Double> {
        val extractor=android.media.MediaExtractor();var decoder:android.media.MediaCodec?=null
        try {
            extractor.setDataSource(app,android.net.Uri.parse(asset.uri),null)
            val track=(0 until extractor.trackCount).first {extractor.getTrackFormat(it).getString("mime")!!.startsWith("audio/")}
            extractor.selectTrack(track)
            val format=extractor.getTrackFormat(track)
            val codec=android.media.MediaCodec.createDecoderByType(format.getString("mime")!!);decoder=codec
            codec.configure(format,null,null,0);codec.start()
            var rate=format.getInteger("sample-rate");var channels=format.getInteger("channel-count");var floating=false
            var ended=false;var inputEnded=false;var count=0;var used=0
            val re=DoubleArray(2);val im=DoubleArray(2);val frequencies=listOf(440.0,660.0)
            val info=android.media.MediaCodec.BufferInfo();val deadline=System.nanoTime()+30_000_000_000L
            while(!ended && count<rate) {
                check(System.nanoTime()<deadline) {"Audio decode timeout"}
                if(!inputEnded) {
                    val index=codec.dequeueInputBuffer(10000)
                    if(index>=0) {
                        val buffer=codec.getInputBuffer(index)!!;val size=extractor.readSampleData(buffer,0)
                        if(size<0) {codec.queueInputBuffer(index,0,0,0,android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true}
                        else {codec.queueInputBuffer(index,0,size,extractor.sampleTime,0);extractor.advance()}
                    }
                }
                val index=codec.dequeueOutputBuffer(info,10000)
                if(index==android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val output=codec.outputFormat;rate=output.getInteger("sample-rate");channels=output.getInteger("channel-count")
                    val encoding=if(output.containsKey("pcm-encoding")) output.getInteger("pcm-encoding") else 2
                    check(encoding==2 || encoding==4);floating=encoding==4
                } else if(index>=0) {
                    val buffer=codec.getOutputBuffer(index)!!.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset);buffer.limit(info.offset+info.size)
                    while(buffer.remaining()>=(if(floating) 4 else 2)*channels) {
                        var sample=0.0
                        repeat(channels) {sample+=if(floating) buffer.float.toDouble() else buffer.short/32768.0}
                        sample/=channels
                        if(count in rate/5 until rate) {
                            frequencies.forEachIndexed {i,hz ->
                                val angle=2*Math.PI*hz*count/rate
                                re[i]+=sample*kotlin.math.cos(angle);im[i]+=sample*kotlin.math.sin(angle)
                            };used++
                        }
                        count++
                    }
                    ended=info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM !=0
                    codec.releaseOutputBuffer(index,false)
                }
            }
            check(used>0)
            fun amplitude(i:Int)=2*kotlin.math.sqrt(re[i]*re[i]+im[i]*im[i])/used
            return amplitude(0) to amplitude(1)
        } finally {decoder?.let {runCatching {it.stop()};it.release()};extractor.release()}
    }

    @Test fun durableTrimAndConcatenationProduceRealMediaAndKeepOriginalBytes() = runBlocking {
        val red=asset("red.mp4");val blue=asset("blue.mp4")
        val original=File(android.net.Uri.parse(red.uri).path!!).readBytes()
        val runtime=AssistantRuntime.get(app)
        val jobId="qa_${UUID.randomUUID()}";val remote="https://qa.invalid/$jobId.mp4"
        val record=runtime.assets.upsertGeneration(GenerationRecord(id="gen_$jobId",nexusJobId=jobId,
            providerId="qa-local",modelId="fixture",modality="VIDEO",prompt="fixture",status="COMPLETED",createdAt=System.currentTimeMillis()))
        val indexedRemote=runtime.assets.addAsset(red.copy(id="",uri=remote,generationId=record.id))
        val generation=com.vidadomu.nexus.core.model.GenerationJob(jobId,providerId="qa-local",modelId="fixture",
            modality=com.vidadomu.nexus.core.model.Modality.VIDEO,prompt="fixture",status=com.vidadomu.nexus.core.model.GenerationStatus.COMPLETED,
            outputs=listOf(com.vidadomu.nexus.core.model.GenerationOutput("o",jobId,com.vidadomu.nexus.core.model.Modality.VIDEO,remote,red.uri)))
        app.generationRepository.updateJob(generation) // isolated QA fixture only; no submission
        withTimeout(5000) {while(app.generationRepository.allJobs.value.none {it.id==jobId}) delay(50)}
        val store=MediaEditJobs(app)
        val started=store.start("trim",input("""{"asset_ids":["${indexedRemote.id}"],"start_ms":500,"end_ms":2000}"""),AgentSession("qa",AgentMode.AGENT))
        val id=started["edit_job_id"]!!.jsonPrimitive.content
        val final=withTimeout(180000) {while(true) {val status=store.read(id);if(status.state=="COMPLETED" || status.state=="FAILED") return@withTimeout status;delay(500)};error("unreachable")}
        assertEquals(final.error,"COMPLETED",final.state)
        val trimmed=AssistantRuntime.get(app).assets.asset(final.outputAssetIds.single())!!
        val duration=AgentVideoOperations(app).inspect(trimmed.id)["duration_ms"]!!.jsonPrimitive.long
        assertTrue("Trim duration=$duration",duration in 1300..1700)
        val joined=run("concat",listOf(red,blue)).single()
        val joinedDuration=AgentVideoOperations(app).inspect(joined.id)["duration_ms"]!!.jsonPrimitive.long
        assertTrue("Joined duration=$joinedDuration",joinedDuration in 5800..6400)
        val reader=android.media.MediaMetadataRetriever()
        try {
            reader.setDataSource(app,android.net.Uri.parse(joined.uri))
            val first=reader.getFrameAtTime(300000,android.media.MediaMetadataRetriever.OPTION_CLOSEST)!!
            val last=reader.getFrameAtTime(4500000,android.media.MediaMetadataRetriever.OPTION_CLOSEST)!!
            assertTrue(android.graphics.Color.red(first.getPixel(100,100))>200)
            assertTrue(android.graphics.Color.blue(last.getPixel(100,100))>200)
            first.recycle();last.recycle()
        } finally {reader.release()}
        assertArrayEquals(original,File(android.net.Uri.parse(red.uri).path!!).readBytes())
        assertEquals(remote,runtime.assets.asset(indexedRemote.id)!!.uri)
        assertEquals(generation,app.generationRepository.getJob(jobId))
    }
    @Test fun removalReplacementMixingAndExtractionChangeTracksWithoutDestroyingInputs() = runBlocking {
        val video=asset("red.mp4");val additional=asset("blue.mp4")
        val original=File(android.net.Uri.parse(video.uri).path!!).readBytes()
        val muted=run("remove_audio",listOf(video)).single()
        assertTrue(mimes(muted).any {it.startsWith("video/")});assertFalse(mimes(muted).any {it.startsWith("audio/")})
        for(action in listOf("replace_audio","mix_audio")) {
            val edited=run(action,listOf(video),",\"audio_asset_id\":\"${additional.id}\"").single()
            assertTrue(mimes(edited).any {it.startsWith("audio/")});assertTrue(mimes(edited).any {it.startsWith("video/")})
            val (originalTone,addedTone)=tones(edited)
            println("AUDIO_QA $action 440Hz=$originalTone 660Hz=$addedTone")
            if(action=="replace_audio") assertTrue("Replacement retained the old sound",addedTone>0.01 && addedTone>originalTone*10)
            else assertTrue("Mix is missing a sound",originalTone>0.01 && addedTone>0.01)
        }
        val audio=run("extract_audio",listOf(video)).single()
        assertEquals(AssetType.AUDIO,audio.type);assertTrue(mimes(audio).all {it.startsWith("audio/")})
        val (originalTone,otherTone)=tones(audio)
        assertTrue(originalTone>0.01 && originalTone>otherTone*10)
        assertArrayEquals(original,File(android.net.Uri.parse(video.uri).path!!).readBytes())
    }
    @Test fun framesAreRealImagesAndInvalidTimesProduceNoExport() = runBlocking {
        val video=asset("red.mp4")
        val outputs=run("frames",listOf(video),",\"timestamps_ms\":[500,2000]")
        assertEquals(2,outputs.size)
        outputs.forEach {image ->
            val bitmap=android.graphics.BitmapFactory.decodeFile(android.net.Uri.parse(image.uri).path)
            assertNotNull(bitmap);assertTrue(android.graphics.Color.red(bitmap!!.getPixel(100,100))>200);bitmap.recycle()
        }
        val folder=File(app.filesDir,"agent_exports")
        val count=folder.listFiles()!!.size
        try {run("frames",listOf(video),",\"timestamps_ms\":[99000]");fail("Invalid timestamp accepted")}
        catch(e:com.vidadomu.nexus.agent.core.tools.ToolInputException) {assertEquals(count,folder.listFiles()!!.size)}
    }
    @Test fun preloadedWhisperTranscribesPublicSpeechAndProducesLocalTextAndSubtitles() = runBlocking {
        check(BuildConfig.OFFLINE_QA && listOf(".qa",".mediaqa",".uiqa").any(app.packageName::endsWith))
        // OfflineQa deliberately has no INTERNET permission: preload public weights before running.
        assertTrue("Preload the pinned ggml-base.bin in files/whisper_models before this test",WhisperModelStore(app).verifyExisting())
        assertTrue(WhisperModelStore(app).ready())
        app.getSharedPreferences("whisper_model_v1",0).edit().remove("verified_sha").commit()
        assertFalse(WhisperModelStore(app).ready())
        assertTrue(WhisperModelStore(app).verifyExisting()) // adopts only the exact locally verified model
        val speech=asset("jfk.wav")
        val started=android.os.SystemClock.elapsedRealtime()
        val outputs=withTimeout(240000) {run("transcribe",listOf(speech))}
        println("WHISPER_QA_ELAPSED_MS ${android.os.SystemClock.elapsedRealtime()-started}")
        val text=outputs.single {it.mimeType=="text/plain"}
        val content=File(android.net.Uri.parse(text.uri).path!!).readText()
        println("WHISPER_QA_TRANSCRIPT $content")
        assertTrue(content.lowercase().contains("country"))
        val subtitles=File(android.net.Uri.parse(outputs.single {it.mimeType=="application/x-subrip"}.uri).path!!).readText()
        assertTrue(subtitles.contains(" --> "));assertTrue(subtitles.contains("country",ignoreCase=true))
    }
}
