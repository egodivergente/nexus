package com.vidadomu.nexus.assistant

import android.util.AtomicFile
import androidx.work.*
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.tools.AgentSession
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.agent.core.tools.ToolInputException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay

/** Separate local editing queue; never touches the generation or billing queues. */
internal class MediaEditJobs(private val app:NexusApplication) {
    private val folder=File(app.filesDir,"media_edit_jobs").apply {mkdirs()}
    fun list():List<MediaEditJob> = folder.listFiles().orEmpty().filter {it.extension=="json"}
        .sortedByDescending {it.lastModified()}.take(50).mapNotNull {runCatching {read(it.nameWithoutExtension)}.getOrNull()}
    fun read(id:String):MediaEditJob {
        requireId(id)
        return synchronized(LOCK) {
            val file=AtomicFile(File(folder,"$id.json"))
            try {Json.decodeFromString<MediaEditJob>(file.openRead().use {it.readBytes().decodeToString()})}
            catch(e:Exception) {throw ToolInputException("Encargo de edición desconocido.")}
        }
    }
    fun write(job:MediaEditJob) = synchronized(LOCK) {
        requireId(job.id)
        val file=AtomicFile(File(folder,"${job.id}.json"))
        val stream=file.startWrite()
        try {stream.write(Json.encodeToString(job).toByteArray());file.finishWrite(stream)}
        catch(e:Exception) {file.failWrite(stream);throw e}
    }
    suspend fun start(action:String,input:JsonObject,session:AgentSession):JsonObject {
        val spec=MediaEditSpec.parse(action,input,session.activeProjectId)
        val engine=AgentVideoOperations(app)
        engine.validate(spec) // all sources, interval and tracks checked before enqueuing
        val job=MediaEditJob(UUID.randomUUID().toString(),spec)
        write(job)
        val work=OneTimeWorkRequestBuilder<MediaEditWorker>().setInputData(workDataOf("edit_job_id" to job.id)).build()
        try {
            WorkManager.getInstance(app).enqueueUniqueWork("nexus-local-media-edits",ExistingWorkPolicy.APPEND_OR_REPLACE,work).result.get()
        } catch(e:Exception) {
            write(job.copy(state="FAILED",error="No se pudo iniciar la edición. El original se conserva."))
            throw ToolInputException("No se pudo iniciar la edición.")
        }
        return status(job.id,session=session)
    }
    suspend fun status(id:String,waitSeconds:Int=0,session:AgentSession):JsonObject {
        var job=read(id)
        val deadline=System.nanoTime()+waitSeconds.coerceIn(0,20)*1_000_000_000L
        while(job.state in setOf("QUEUED","PROCESSING") && System.nanoTime()<deadline) {delay(250);job=read(id)}
        val assets=AssistantRuntime.get(app).assets
        val outputAssets=job.outputAssetIds.mapNotNull(assets::asset)
        outputAssets.forEach { asset ->
            val mime=asset.mimeType ?: when(asset.type) {
                com.vidadomu.nexus.agent.core.assets.AssetType.IMAGE -> "image/jpeg"
                com.vidadomu.nexus.agent.core.assets.AssetType.VIDEO -> "video/mp4"
                com.vidadomu.nexus.agent.core.assets.AssetType.AUDIO -> "audio/*"
                else -> "application/octet-stream"
            }
            session.showMedia(Attachment(asset.id,asset.uri,mime,asset.displayName,asset.width,asset.height,asset.durationSec,asset.id))
        }
        return buildJsonObject {
            put("edit_job_id",job.id);put("action",job.spec.action);put("state",job.state);put("progress",job.progress)
            put("originals_preserved",true);put("provider_charge",false)
            put("outputs",JsonArray(outputAssets.map {asset -> buildJsonObject {
                put("asset_id",asset.id);put("name",asset.displayName);put("uri",asset.uri);put("type",asset.type.name)
            }}))
            if(job.spec.action in setOf("transcribe","analyze_audio") && job.state=="COMPLETED") {
                job.outputAssetIds.mapNotNull(assets::asset).firstOrNull {it.mimeType=="text/plain"}?.let {asset ->
                    val file=File(android.net.Uri.parse(asset.uri).path.orEmpty())
                    val text=file.bufferedReader().use {reader ->
                        val chars=CharArray(16001);var count=0
                        while(count<chars.size) {val n=reader.read(chars,count,chars.size-count);if(n<0) break;count+=n}
                        String(chars,0,count)
                    }
                    put(if(job.spec.action=="transcribe") "transcript" else "analysis",text.take(16000));put("transcript_may_be_truncated",text.length>16000)
                }
            }
            job.error?.let {put("error",it)}
            if(job.state in setOf("QUEUED","PROCESSING")) put("next_action","Consulta get_media_edit_job; no vuelvas a crear el encargo para consultar su estado.")
        }
    }
    companion object {
        private val LOCK=Any()
        private fun requireId(id:String) {if(!id.matches(Regex("[0-9a-f-]{36}"))) throw ToolInputException("Identificador de edición no válido.")}
    }
}
