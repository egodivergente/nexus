package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.*
import kotlinx.serialization.json.*
import java.io.File

internal class AgentAudioAnalysis(private val app:NexusApplication) {
    suspend fun analyze(job:MediaEditJob,file:File,parent:Asset):List<String> {
        val samples=WhisperTranscription(app).decode(file)
        val measured=AudioMeasurements.measure(samples)
        val report=buildJsonObject {
            put("method","Audio decodificado desde copia mono AAC a 16 kHz; FFT Hann 2048, hasta 256 ventanas distribuidas por el intervalo.")
            put("source_asset_id",parent.id);put("start_ms",job.spec.startMs);put("duration_seconds",samples.size/16000.0)
            put("rms_dbfs",measured.rmsDbfs);put("peak_dbfs",measured.peakDbfs)
            put("near_full_scale_samples_percent",measured.nearFullScalePercent);put("samples_below_minus_60_dbfs_percent",measured.quietPercent)
            put("bands",JsonArray(AudioMeasurements.bands.mapIndexed {i,range -> buildJsonObject {put("from_hz",range.first);put("to_hz",range.second);put("relative_energy_percent",measured.bandsPercent[i])}}))
            put("limits","Mediciones de una copia mono remuestreada y comprimida. No mide LUFS, fase estéreo, frecuencias superiores a 8 kHz ni prueba clipping del original. No identifica voces o instrumentos.")
        }
        val output=File(file.parentFile,"audio_analysis_${job.id}.txt")
        output.writeText(Json {prettyPrint=true}.encodeToString(JsonObject.serializer(),report))
        return listOf(AgentVideoOperations(app).index(job,output,parent,AssetType.OTHER,"text/plain",null).id)
    }
}
