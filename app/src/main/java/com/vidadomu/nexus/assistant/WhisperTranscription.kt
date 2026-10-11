package com.vidadomu.nexus.assistant

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.agent.core.tools.ToolInputException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

internal class WhisperTranscription(private val app:NexusApplication) {
    suspend fun transcribe(job:MediaEditJob,audio:File,parent:Asset,onProgress:(Int)->Unit):List<String> = withContext(Dispatchers.IO) {
        val model=WhisperModelStore(app).requireModel()
        val samples=decode(audio)
        onProgress(95)
        val raw=try {withTimeout(30*60*1000L) {WhisperNative.run(model.path,samples)}.decodeToString()}
            catch(e:LinkageError) {throw ToolInputException("Whisper local no pudo cargar su motor nativo en este dispositivo.")}
        coroutineContext.ensureActive()
        val segments=Json.parseToJsonElement(raw).jsonObject["segments"]!!.jsonArray
        val text=segments.joinToString("\n") {it.jsonObject["text"]!!.jsonPrimitive.content.trim()}
        val srt=segments.mapIndexed {i,seg ->
            val item=seg.jsonObject
            "${i+1}\n${timestamp(item["start_ms"]!!.jsonPrimitive.long)} --> ${timestamp(item["end_ms"]!!.jsonPrimitive.long)}\n${item["text"]!!.jsonPrimitive.content.trim()}\n"
        }.joinToString("\n")
        val outputs=listOf("txt" to text,"srt" to srt)
        outputs.map {(ext,body) ->
            val file=File(audio.parentFile,"transcript_${job.id}.$ext");val stage=File(file.parentFile,file.name+".part")
            try {
                stage.outputStream().use {it.write(body.toByteArray());it.fd.sync()};check(stage.renameTo(file))
                AgentVideoOperations(app).index(job,file,parent,AssetType.OTHER,if(ext=="srt") "application/x-subrip" else "text/plain",null).id
            } finally {stage.delete()}
        }
    }
    private suspend fun decode(file:File):FloatArray {
        val extractor=MediaExtractor();var decoder:MediaCodec?=null
        try {
            extractor.setDataSource(file.path)
            val track=(0 until extractor.trackCount).firstOrNull {extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true}
                ?: throw ToolInputException("No se encontró audio para Whisper.")
            extractor.selectTrack(track)
            val format=extractor.getTrackFormat(track)
            if(format.getInteger(MediaFormat.KEY_SAMPLE_RATE)!=16000 || format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)!=1)
                throw ToolInputException("El audio no pudo prepararse a 16 kHz mono para Whisper.")
            val codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!);decoder=codec
            codec.configure(format,null,null,0);codec.start()
            val output=java.io.ByteArrayOutputStream()
            var inputEnded=false;var outputEnded=false;var floatPcm=false
            val info=MediaCodec.BufferInfo();val deadline=System.nanoTime()+120_000_000_000L
            while(!outputEnded) {
                coroutineContext.ensureActive()
                if(System.nanoTime()>deadline) throw ToolInputException("La preparación del audio agotó su tiempo.")
                if(!inputEnded) {
                    val index=codec.dequeueInputBuffer(10000)
                    if(index>=0) {
                        val buffer=codec.getInputBuffer(index)!!;val size=extractor.readSampleData(buffer,0)
                        if(size<0) {codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true}
                        else {codec.queueInputBuffer(index,0,size,extractor.sampleTime,0);extractor.advance()}
                    }
                }
                val index=codec.dequeueOutputBuffer(info,10000)
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val decoded=codec.outputFormat
                    if(decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE)!=16000 || decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)!=1)
                        throw ToolInputException("El decodificador cambió el formato de audio esperado.")
                    val encoding=if(decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)) decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) else 2
                    if(encoding !in setOf(2,4)) throw ToolInputException("Formato PCM no compatible.")
                    floatPcm=encoding==4
                } else if(index>=0) {
                    val buffer=codec.getOutputBuffer(index)!!;buffer.position(info.offset);buffer.limit(info.offset+info.size)
                    val bytes=ByteArray(info.size);buffer.get(bytes);output.write(bytes)
                    if(output.size()>16000*900*(if(floatPcm) 4 else 2)+8192) throw ToolInputException("El audio supera el límite de 15 minutos.")
                    outputEnded=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM !=0
                    codec.releaseOutputBuffer(index,false)
                }
            }
            val bytes=java.nio.ByteBuffer.wrap(output.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
            val count=bytes.remaining()/(if(floatPcm) 4 else 2)
            if(count==0) throw ToolInputException("El audio no contiene muestras.")
            return FloatArray(count.coerceAtMost(16000*900)) {if(floatPcm) bytes.float else bytes.short/32768f}
        } finally {decoder?.let {runCatching {it.stop()};it.release()};extractor.release()}
    }
    companion object {
        internal fun timestamp(ms:Long):String {
            val safe=ms.coerceAtLeast(0)
            return "%02d:%02d:%02d,%03d".format(java.util.Locale.ROOT,safe/3600000,safe/60000%60,safe/1000%60,safe%1000)
        }
    }
}
