package com.vidadomu.nexus.assistant

import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaCodec
import android.media.MediaMuxer
import java.nio.ByteBuffer
import android.net.Uri
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.transformer.*
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.agent.core.tools.ToolInputException
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.OutputFiles
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device Media3 edits. New files only; no provider, shell, arbitrary filters or uploads. */
internal class AgentVideoOperations(private val app:NexusApplication) {
    private val assets get()=AssistantRuntime.get(app).assets
    internal fun source(id:String):Asset {
        var asset=assets.asset(id) ?: throw ToolInputException("Archivo desconocido; usa search_assets primero.")
        // A completed generation may have been indexed before its download finished.
        // Resolve its current trusted local output without rewriting history or fetching any URL.
        if(Uri.parse(asset.uri).scheme in setOf("http","https")) {
            val record=assets.generationOfAsset(id)
            val jobs=app.generationRepository.allJobs.value
            val job=jobs.firstOrNull {it.id==record?.nexusJobId && it.status==GenerationStatus.COMPLETED}
            val output=job?.outputs?.filter {it.url==asset.uri}?.singleOrNull()
            val local=output?.let {OutputFiles.trustedLocalUri(it,OutputFiles.conflictingLocalUris(jobs.flatMap {j->j.outputs}))}
            if(local!=null) asset=asset.copy(uri=local)
        }
        val uri=Uri.parse(asset.uri)
        if(uri.scheme !in setOf("file","content")) throw ToolInputException("Importa o descarga el archivo antes de editarlo.")
        if(uri.scheme=="file") {
            val file=File(uri.path ?: throw ToolInputException("Archivo no válido.")).canonicalFile
            val privateRoot=app.filesDir.canonicalPath+File.separator
            val mediaRoots=listOf("agent_media","agent_exports").map {File(app.filesDir,it).canonicalPath+File.separator}
            if(file.path.startsWith(privateRoot) && mediaRoots.none(file.path::startsWith))
                throw ToolInputException("Solo se pueden editar medios importados o resultados de Nexus.")
            if(!file.isFile) throw ToolInputException("El archivo ya no está disponible.")
        }
        return asset
    }
    internal fun inspect(id:String):JsonObject {
        val source=source(id);val uri=Uri.parse(source.uri)
        val tracks=mutableListOf<JsonObject>()
        val extractor=MediaExtractor()
        try {
            extractor.setDataSource(app,uri,null)
            for(i in 0 until extractor.trackCount) {
                val f=extractor.getTrackFormat(i)
                tracks+=buildJsonObject {
                    put("mime",f.getString(MediaFormat.KEY_MIME).orEmpty())
                    if(f.containsKey(MediaFormat.KEY_DURATION)) put("duration_ms",f.getLong(MediaFormat.KEY_DURATION)/1000)
                    for(key in listOf(MediaFormat.KEY_WIDTH,MediaFormat.KEY_HEIGHT,MediaFormat.KEY_SAMPLE_RATE,MediaFormat.KEY_CHANNEL_COUNT))
                        if(f.containsKey(key)) put(key,f.getInteger(key))
                }
            }
        } catch(e:Exception) {throw ToolInputException("No se pudo leer este medio o su formato no es compatible.")}
        finally {extractor.release()}
        val duration=duration(uri)
        return buildJsonObject {put("asset_id",id);put("duration_ms",duration);put("tracks",JsonArray(tracks));put("local",true)}
    }
    private fun duration(uri:Uri):Long {
        val r=MediaMetadataRetriever()
        try {r.setDataSource(app,uri);return r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            ?.takeIf {it>0} ?: throw ToolInputException("No se pudo verificar la duración del medio.")}
        finally {r.release()}
    }
    fun validate(spec:MediaEditSpec) {
        val infos=spec.assetIds.map(::inspect)
        val wantsVideo=spec.action !in setOf("transcribe","analyze_audio")
        infos.forEach {info ->
            val tracks=info["tracks"]!!.jsonArray.map {it.jsonObject["mime"]!!.jsonPrimitive.content}
            if(wantsVideo && tracks.none {it.startsWith("video/")}) throw ToolInputException("Esta operación necesita vídeo.")
            if(spec.action in setOf("extract_audio","transcribe","analyze_audio") && tracks.none {it.startsWith("audio/")})
                throw ToolInputException("El archivo no contiene una pista de audio.")
        }
        val d=infos.first()["duration_ms"]!!.jsonPrimitive.long
        if(spec.startMs>=d || (spec.endMs ?: 0)>d) throw ToolInputException("El intervalo está fuera de la duración del archivo.")
        if(spec.framesMs.any {it>=d}) throw ToolInputException("Un fotograma está fuera de la duración del vídeo.")
        if(spec.action in setOf("transcribe","analyze_audio")) {
            val audio=infos.first()["tracks"]!!.jsonArray.first {it.jsonObject["mime"]!!.jsonPrimitive.content.startsWith("audio/")}.jsonObject
            if(audio[MediaFormat.KEY_CHANNEL_COUNT]?.jsonPrimitive?.int !in 1..2)
                throw ToolInputException("Whisper local admite audio mono o estéreo. Prepara otra pista para este archivo.")
            if((spec.endMs ?: d)-spec.startMs>900_000) throw ToolInputException("Transcribe como máximo 15 minutos por encargo; puedes seleccionar un intervalo.")
            if(spec.action=="transcribe") WhisperModelStore(app).requireModel()
        }
        spec.audioAssetId?.let {id ->
            if(inspect(id)["tracks"]!!.jsonArray.none {it.jsonObject["mime"]!!.jsonPrimitive.content.startsWith("audio/")})
                throw ToolInputException("La pista adicional no contiene audio.")
        }
        val totalMs=infos.sumOf {it["duration_ms"]!!.jsonPrimitive.long}
        val selectedMs=if(spec.action in setOf("trim","extract_audio","transcribe","analyze_audio"))
            (spec.endMs ?: totalMs)-spec.startMs else totalMs
        if(selectedMs>3_600_000 && spec.action !in setOf("split_video","extract_audio"))
            throw ToolInputException("Esta operación admite como máximo una hora. Usa split_video para segmentar archivos largos.")
        if(selectedMs>43_200_000) throw ToolInputException("El máximo automático es de 12 horas por archivo.")
        val seconds=selectedMs/1000
        val bytesPerSecond=if(spec.action=="extract_audio") 96_000L else 1_000_000L
        if(app.filesDir.usableSpace<maxOf(128L*1024*1024,seconds*bytesPerSecond))
            throw ToolInputException("No hay suficiente espacio libre para exportar sin tocar los originales.")
    }
    suspend fun execute(job:MediaEditJob,onProgress:(Int)->Unit):List<String> {
        validate(job.spec)
        val spec=job.spec
        if(spec.action=="split_video" || (spec.action=="extract_audio" &&
            (spec.endMs ?: inspect(spec.assetIds.first())["duration_ms"]!!.jsonPrimitive.long)-spec.startMs>3_600_000L))
            return processInChunks(job,onProgress)
        val first=source(spec.assetIds.first())
        val folder=File(app.filesDir,"agent_exports").apply {mkdirs()}
        if(spec.action=="frames") return frames(job,first,folder,onProgress)
        val stage=File(folder,"edit_${job.id}.part.mp4")
        val output=File(folder,"edit_${job.id}.${if(spec.action in setOf("extract_audio","transcribe","analyze_audio")) "m4a" else "mp4"}")
        // A stopped export is rebuilt from its private partial file, never from the original.
        stage.delete()
        val videoItems=spec.assetIds.map {id ->
            val src=source(id)
            val item=MediaItem.Builder().setUri(src.uri).apply {
                if(spec.startMs>0 || spec.endMs!=null) setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(spec.startMs).apply {spec.endMs?.let {setEndPositionMs(it)}}.build())
            }.build()
            EditedMediaItem.Builder(item).setRemoveAudio(spec.action in setOf("remove_audio","replace_audio"))
                .setRemoveVideo(spec.action in setOf("extract_audio","transcribe","analyze_audio")).apply {
                    if(spec.action in setOf("transcribe","analyze_audio")) {
                        val mono=ChannelMixingAudioProcessor().apply {
                            for(channels in 1..2) putChannelMixingMatrix(ChannelMixingMatrix.create(channels,1))
                        }
                        val resample=SonicAudioProcessor().apply {setOutputSampleRateHz(16000)}
                        setEffects(Effects(listOf(mono,resample),emptyList()))
                    }
                }.build()
        }
        val sequences=mutableListOf(EditedMediaItemSequence(videoItems))
        spec.audioAssetId?.let {id ->
            val d=duration(Uri.parse(first.uri))
            val audio=EditedMediaItem.Builder(MediaItem.Builder().setUri(source(id).uri)
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(d).build()).build())
                .setRemoveVideo(true).build()
            // The requested soundtrack covers the output duration; short tracks loop.
            sequences+=EditedMediaItemSequence(listOf(audio),true)
        }
        try {
            withTimeout(30*60*1000L) {export(Composition.Builder(sequences).build(),stage,onProgress)}
            if(!stage.isFile || stage.length()==0L) throw ToolInputException("La exportación no produjo un archivo válido.")
            val info=inspectFile(stage)
            if(!stage.renameTo(output)) throw ToolInputException("No se pudo guardar el resultado.")
            if(spec.action in setOf("transcribe","analyze_audio")) {
                try {return if(spec.action=="analyze_audio") AgentAudioAnalysis(app).analyze(job,output,first) else WhisperTranscription(app).transcribe(job,output,first,onProgress)}
                finally {output.delete()}
            }
            val type=if(spec.action=="extract_audio") AssetType.AUDIO else AssetType.VIDEO
            return listOf(index(job,output,first,type,if(type==AssetType.AUDIO) "audio/mp4" else "video/mp4",info).id)
        } finally {stage.delete()}
    }
    /** Segments long media locally; every chunk is a bounded, independently indexed export.
     * Originals are never modified. A failed chunk leaves already completed exports accessible.
     */
    private suspend fun processInChunks(job:MediaEditJob,onProgress:(Int)->Unit):List<String> {
        val original=job.spec
        val durationMs=inspect(original.assetIds.single())["duration_ms"]!!.jsonPrimitive.long
        val rangeStart=original.startMs
        val rangeEnd=original.endMs ?: durationMs
        val ranges=mediaChunkRanges(rangeStart,rangeEnd)
        val parts=ranges.size
        val outputs=mutableListOf<String>()
        for((i,range) in ranges.withIndex()) {
            currentCoroutineContext().ensureActive()
            val (start,end)=range
            val partSpec=original.copy(
                action=if(original.action=="split_video") "trim" else "extract_audio",
                startMs=start,endMs=end
            )
            val partJob=job.copy(id="${job.id}_part_${i+1}",spec=partSpec)
            outputs+=execute(partJob) {partProgress ->
                onProgress(((i*94)+(partProgress.coerceIn(0,100)*94/100))/parts)
            }
            onProgress((i+1)*94/parts)
        }
        if(original.action=="extract_audio") {
            onProgress(96)
            val chunks=outputs.map { id ->
                val uri=Uri.parse(source(id).uri)
                File(uri.path ?: throw ToolInputException("No se encontró un segmento de audio."))
            }
            val folder=File(app.filesDir,"agent_exports").apply {mkdirs()}
            val stage=File(folder,"edit_${job.id}.merged.part.m4a")
            val final=File(folder,"edit_${job.id}.m4a")
            stage.delete()
            try {
                withContext(Dispatchers.IO) { concatAudioAac(chunks,stage) }
                if(!stage.isFile || stage.length()==0L || inspectFile(stage)<=0L)
                    throw ToolInputException("No se pudo comprobar el audio unido.")
                if(!stage.renameTo(final)) throw ToolInputException("No se pudo publicar el audio unido.")
                val result=index(job,final,source(original.assetIds.single()),AssetType.AUDIO,"audio/mp4",inspectFile(final))
                onProgress(99)
                return listOf(result.id)
            } catch(e:Exception) {
                throw ToolInputException("No se pudo unir el audio automáticamente; los segmentos extraídos permanecen disponibles.")
            } finally { stage.delete() }
        }
        return outputs
    }

    /** Fast AAC remux: no second encode, source and intermediate exports remain intact. */
    private fun concatAudioAac(chunks:List<File>,target:File) {
        if(chunks.isEmpty()) throw ToolInputException("No hay audio para unir.")
        var muxer:MediaMuxer?=null
        var started=false
        var offsetUs=0L
        var previousPts=-1L
        var totalSamples=0L
        var expectedRate=-1
        var expectedChannels=-1
        try {
            for(chunk in chunks) {
                val extractor=MediaExtractor()
                try {
                    extractor.setDataSource(chunk.path)
                    val track=(0 until extractor.trackCount).firstOrNull {
                        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)=="audio/mp4a-latm"
                    } ?: throw ToolInputException("Un segmento no contiene audio AAC compatible.")
                    val format=extractor.getTrackFormat(track)
                    val rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if(muxer==null) {
                        muxer=MediaMuxer(target.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                        muxer.addTrack(format)
                        muxer.start()
                        started=true
                        expectedRate=rate
                        expectedChannels=channels
                    } else if(rate!=expectedRate || channels!=expectedChannels) {
                        throw ToolInputException("Los segmentos tienen frecuencias o canales diferentes.")
                    }
                    extractor.selectTrack(track)
                    val declaredSize=if(format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))
                        format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 262144
                    if(declaredSize<=0 || declaredSize>8*1024*1024)
                        throw ToolInputException("El tamaño de muestra AAC no es compatible.")
                    val buffer=ByteBuffer.allocateDirect(maxOf(262144,declaredSize))
                    val info=MediaCodec.BufferInfo()
                    var firstUs=-1L
                    var lastRelativeUs=-1L
                    var segmentSamples=0
                    val frameUs=maxOf(1L,1024L*1_000_000L/rate)
                    while(true) {
                        buffer.clear()
                        val size=extractor.readSampleData(buffer,0)
                        if(size<0) break
                        if(size>buffer.capacity()) throw ToolInputException("La muestra AAC excede el búfer.")
                        val sampleUs=extractor.sampleTime
                        if(sampleUs<0) break
                        if(firstUs<0L) firstUs=sampleUs
                        val relativeUs=(sampleUs-firstUs).coerceAtLeast(0L)
                        val pts=offsetUs+relativeUs
                        if(pts<=previousPts) throw ToolInputException("Marcas de tiempo AAC desordenadas.")
                        info.set(0,size,pts,extractor.sampleFlags)
                        buffer.position(0)
                        buffer.limit(size)
                        muxer!!.writeSampleData(0,buffer,info)
                        previousPts=pts
                        lastRelativeUs=relativeUs
                        segmentSamples++
                        totalSamples++
                        extractor.advance()
                    }
                    if(segmentSamples==0) throw ToolInputException("Uno de los segmentos está vacío.")
                    offsetUs+=lastRelativeUs+frameUs
                } finally { extractor.release() }
            }
            if(totalSamples==0L) throw ToolInputException("No se encontraron muestras AAC.")
        } finally {
            try { if(started) muxer?.stop() } finally { muxer?.release() }
        }
    }

    private fun inspectFile(file:File):Long = duration(Uri.fromFile(file))
    private suspend fun export(composition:Composition,file:File,onProgress:(Int)->Unit) = withContext(Dispatchers.Main) {
        val complete=CompletableDeferred<Unit>()
        val transformer=Transformer.Builder(app).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object:Transformer.Listener {
                override fun onCompleted(composition:Composition,result:ExportResult) {complete.complete(Unit)}
                override fun onError(composition:Composition,result:ExportResult,error:ExportException) {
                    complete.completeExceptionally(ToolInputException("Android no pudo exportar este formato. Los originales se conservan (código ${error.errorCode})."))
                }
            }).build()
        try {
            transformer.start(composition,file.path)
            val progress=launch {val holder=ProgressHolder();while(isActive) {
                if(transformer.getProgress(holder)==Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress.coerceIn(0,94))
                delay(1000)
            }}
            try {complete.await()} finally {progress.cancel()}
        } finally {transformer.cancel()}
    }
    private suspend fun frames(job:MediaEditJob,source:Asset,folder:File,onProgress:(Int)->Unit):List<String> {
        val r=MediaMetadataRetriever();val ids=mutableListOf<String>()
        try {
            r.setDataSource(app,Uri.parse(source.uri))
            for((i,time) in job.spec.framesMs.withIndex()) {
                currentCoroutineContext().ensureActive()
                val bitmap=if(Build.VERSION.SDK_INT>=27) r.getScaledFrameAtTime(time*1000,MediaMetadataRetriever.OPTION_CLOSEST,
                    job.spec.maxDimension,job.spec.maxDimension) else r.getFrameAtTime(time*1000,MediaMetadataRetriever.OPTION_CLOSEST)
                val frame=bitmap ?: throw ToolInputException("No se pudo extraer el fotograma de ${time} ms.")
                val output=File(folder,"frame_${job.id}_$time.jpg");val stage=File(folder,output.name+".part")
                try {
                    stage.outputStream().use {out ->check(frame.compress(Bitmap.CompressFormat.JPEG,95,out));out.fd.sync()}
                    check(stage.renameTo(output))
                    ids+=index(job,output,source,AssetType.IMAGE,"image/jpeg",null,frame.width,frame.height).id
                } finally {frame.recycle();stage.delete()}
                onProgress(((i+1)*100/job.spec.framesMs.size).coerceAtMost(99))
            }
            return ids
        } finally {r.release()}
    }
    internal suspend fun index(job:MediaEditJob,file:File,parent:Asset,type:AssetType,mime:String,duration:Long?,width:Int?=null,height:Int?=null):Asset {
        val uri=Uri.fromFile(file).toString()
        assets.assetByUri(uri)?.let {return it}
        return assets.addAsset(parent.copy(id="",uri=uri,type=type,displayName=file.name,mimeType=mime,width=width,height=height,
            durationSec=duration?.div(1000.0),source=AssetSource.IMPORTED,role=AssetRole.OUTPUT,parentAssetId=parent.id,
            generationId=null,libraryDeleted=false,projectId=job.spec.projectId ?: parent.projectId,createdAt=System.currentTimeMillis()))
    }
}
