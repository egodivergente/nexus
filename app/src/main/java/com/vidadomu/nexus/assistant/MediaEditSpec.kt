package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.tools.ToolInputException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Closed editing vocabulary. No command lines, paths, deletion or provider submissions. */
@Serializable
internal data class MediaEditSpec(
    val action: String,
    val assetIds: List<String>,
    val startMs: Long = 0,
    val endMs: Long? = null,
    val audioAssetId: String? = null,
    val framesMs: List<Long> = emptyList(),
    val maxDimension: Int = 1920,
    val projectId: String? = null,
) {
    companion object {
        val ACTIONS = setOf("trim", "concat", "remove_audio", "replace_audio", "mix_audio", "extract_audio", "frames", "transcribe", "analyze_audio")
        fun parse(action: String, input: JsonObject, projectId: String?): MediaEditSpec {
            if(action !in ACTIONS) throw ToolInputException("Operación de edición no admitida.")
            val allowed=setOf("action","asset_id","asset_ids","start_ms","end_ms","audio_asset_id","timestamps_ms","max_dimension")
            if(input.keys.any {it !in allowed}) throw ToolInputException("La edición recibió opciones que no admite.")
            if("asset_id" in input && "asset_ids" in input) throw ToolInputException("Usa asset_id o asset_ids, no ambos.")
            if("asset_ids" in input && input["asset_ids"] !is JsonArray) throw ToolInputException("asset_ids debe ser una lista de identificadores.")
            val ids = (input["asset_ids"] as? JsonArray)?.map {
                (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
                    ?: throw ToolInputException("asset_ids debe contener identificadores de archivos.")
            } ?: listOf(input.requiredText("asset_id"))
            if(ids.size !in 1..12 || ids.any {it.isBlank() || it.length>120}) throw ToolInputException("Elige de 1 a 12 archivos indexados.")
            if(action != "concat" && ids.size != 1) throw ToolInputException("Esta operación necesita un solo archivo.")
            if(action == "concat" && ids.size < 2) throw ToolInputException("Para unir clips elige al menos dos, en orden.")
            fun ms(key:String, default:Long?=null):Long? {
                if(key !in input) return default
                val value=(input[key] as? JsonPrimitive)?.longOrNull ?: throw ToolInputException("$key debe ser un entero en milisegundos.")
                if(value !in 0..3_600_000L) throw ToolInputException("$key debe estar entre 0 y 3600000 ms.")
                return value
            }
            val start=ms("start_ms",0)!!; val end=ms("end_ms")
            if(end!=null && end<=start) throw ToolInputException("El final debe ser posterior al inicio.")
            if(action=="trim" && end==null) throw ToolInputException("Indica end_ms para recortar.")
            if(action !in setOf("trim","transcribe","analyze_audio") && (start!=0L || end!=null)) throw ToolInputException("El intervalo sólo se aplica al recorte o la transcripción.")
            val audio=input.text("audio_asset_id")
            if(action in setOf("replace_audio","mix_audio") && audio==null) throw ToolInputException("Indica la pista audio_asset_id.")
            if(action !in setOf("replace_audio","mix_audio") && audio!=null) throw ToolInputException("Esta operación no usa una pista adicional.")
            val frames=if(action=="frames") {
                val values=(input["timestamps_ms"] as? JsonArray)?.map {
                    (it as? JsonPrimitive)?.longOrNull?.takeIf {v -> v in 0..3_600_000L}
                        ?: throw ToolInputException("Los fotogramas requieren tiempos enteros en milisegundos.")
                } ?: throw ToolInputException("Indica timestamps_ms.")
                if(values.size !in 1..12) throw ToolInputException("Extrae de 1 a 12 fotogramas por encargo.")
                values.distinct()
            } else emptyList()
            if(action!="frames" && ("timestamps_ms" in input || "max_dimension" in input)) throw ToolInputException("Los tiempos de fotograma y tamaño sólo se aplican a extraer fotogramas.")
            return MediaEditSpec(action,ids,start,end,audio,frames,input.integer("max_dimension",1920,320..3840),projectId)
        }
    }
}

@Serializable
internal data class MediaEditJob(
    val id:String, val spec:MediaEditSpec, val state:String="QUEUED", val progress:Int=0,
    val outputAssetIds:List<String> = emptyList(), val error:String?=null,
    val createdAt:Long=System.currentTimeMillis(), val updatedAt:Long=createdAt,
)
