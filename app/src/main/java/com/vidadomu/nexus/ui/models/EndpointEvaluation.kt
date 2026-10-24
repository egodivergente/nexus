package com.vidadomu.nexus.ui.models

import android.content.Context
import com.vidadomu.nexus.core.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*

@Serializable internal data class EvidenceField(val key: String, val type: String = "", val required: Boolean = false,
    @SerialName("enum") val options: List<JsonElement> = emptyList(), val minimum: Double? = null, val maximum: Double? = null,
    val minLength: Int? = null, val maxLength: Int? = null, val minItems: Int? = null, val maxItems: Int? = null)
@Serializable internal data class EndpointEvidence(val provider: String, val model: String, val source: String,
    val checkedAt: String, val status: String, val fields: List<EvidenceField> = emptyList(), val metadata: JsonObject = JsonObject(emptyMap()),
    val documentation: String = "", val sourceSha256: String = "")
@Serializable private data class EvidenceBundle(val records: List<EndpointEvidence>)
internal data class EvaluatedAxis(val title: String, val score: Double, val reason: String)
internal data class EndpointEvaluation(val facts: List<Pair<String,String>>, val axes: List<EvaluatedAxis>,
    val source: String, val checkedAt: String, val evidence: EndpointEvidence?) {
    val score: Double get() = if(axes.isEmpty()) 0.0 else axes.map {it.score}.average()
}

internal object EndpointEvaluations {
    @Volatile private var cache: Map<String,EndpointEvidence>? = null
    private val json=Json {ignoreUnknownKeys=true}
    fun all(context: Context): Map<String,EndpointEvidence> = cache ?: synchronized(this) {
        cache ?: json.decodeFromString<EvidenceBundle>(context.assets.open("model_endpoint_evidence.json").bufferedReader().use {it.readText()})
            .records.associateBy {"${it.provider}:${it.model}"}.also {cache=it}
    }
    fun evidence(context: Context, model: NexusModel) = all(context)["${model.providerId}:${model.providerModelId ?: model.id}"]

    /** Transparent editorial rubric for exposed controls, not a fabricated quality benchmark. */
    fun evaluate(model: NexusModel, evidence: EndpointEvidence?): EndpointEvaluation {
        val fields=evidence?.fields?.takeIf {it.isNotEmpty()} ?: model.parameters.map {p->
            EvidenceField(p.key,p.nativeType.orEmpty(),p.isRequired,p.options.map(::JsonPrimitive),p.minValue?.toDouble(),p.maxValue?.toDouble())
        } + model.contractMedia.map {EvidenceField(it.key,"array",it.required,maxItems=it.maximum)}
        val facts=buildList {
            fields.filter {it.key !in setOf("prompt","negative_prompt","seed") && (it.options.isNotEmpty() || it.minimum!=null || it.maximum!=null || it.type=="boolean" || it.maxItems!=null)}.forEach {f->
                val value=when {
                    f.options.isNotEmpty() -> f.options.mapNotNull {it.jsonPrimitive.contentOrNull}.joinToString(" · ") {humanValue(it)}
                    f.maxItems!=null -> "${f.minItems ?: if(f.required) 1 else 0}–${f.maxItems} archivos"
                    f.minimum!=null || f.maximum!=null -> listOfNotNull(f.minimum?.let(::number),f.maximum?.let(::number)).joinToString("–")
                    else -> "Sí / No"
                }
                add(title(f.key) to value)
            }
            val media=fields.filter {it.key.contains(Regex("(?i)image|frame|video|audio|refers")) && it.type in setOf("string","array","object")}
            if(media.isNotEmpty()) add("Entradas" to media.joinToString(" · ") {title(it.key)+(if(it.required) " *" else "")})
            evidence?.metadata?.let {m->
                listOf("contextLength" to "Contexto (tokens)","maxCompletionTokens" to "Salida máxima (tokens)","avgTotalTPS" to "Tokens/s declarados por Atlas", "avgLatency" to "Latencia declarada por Atlas").forEach {(key,label)->
                    m[key]?.takeUnless {it==JsonNull}?.let {add(label to (it as? JsonPrimitive)?.content.orEmpty())}
                }
                (m["supported_features"] as? JsonArray)?.takeIf {it.isNotEmpty()}?.let {add("Funciones publicadas" to it.joinToString(" · ") {v->(v as? JsonPrimitive)?.content ?: v.toString()})}
            }
        }.distinct()
        val axes=buildList {
            val format=fields.filter {it.key.lowercase() in setOf("resolution","size","image_size","aspect_ratio","aspectratio","ratio","width","height")}
            if(format.isNotEmpty()) add(EvaluatedAxis("Encuadre y salida",minOf(10.0,4.0+format.size+format.sumOf {minOf(it.options.size,4)}*.5),format.joinToString(" · ") {title(it.key)}))
            val references=fields.filter {it.key.contains(Regex("(?i)image|frame|refers|reference|video_url|audio_url"))}
            if(references.isNotEmpty()) add(EvaluatedAxis("Control por medios",minOf(10.0,4.0+references.size+references.mapNotNull {it.maxItems}.maxOrNull().let {if(it==null) 0.0 else minOf(3.0,it*.4)}),references.joinToString(" · ") {title(it.key)}))
            val precise=fields.filter {it.key.contains(Regex("(?i)seed|guidance|cfg|steps|strength|mask|bbox|point|camera|motion|control|timestamp"))}
            if(precise.isNotEmpty()) add(EvaluatedAxis("Dirección y ajuste",minOf(10.0,4.0+precise.size*1.0),precise.joinToString(" · ") {title(it.key)}))
            val temporal=fields.filter {it.key.contains(Regex("(?i)duration|fps|frame_rate|extend|shot|multi_prompt"))}
            if(temporal.isNotEmpty()) add(EvaluatedAxis("Control temporal",minOf(10.0,5.0+temporal.size*1.5),temporal.joinToString(" · ") {title(it.key)}))
            if(Modality.CHAT in model.modalities || evidence?.metadata?.get("contextLength")?.jsonPrimitive?.doubleOrNull?.let {it>0}==true) {
                val context=evidence?.metadata?.get("contextLength")?.jsonPrimitive?.doubleOrNull
                context?.takeIf {it>0}?.let {add(EvaluatedAxis("Capacidad de contexto",when {it>=200000->10.0;it>=128000->9.0;it>=64000->8.0;it>=32000->7.0;else->5.0},"${number(it)} tokens de contexto publicados; incluye entrada y salida."))}
                if(model.chatSamplingParameters.isNotEmpty()) add(EvaluatedAxis("Control de respuesta",minOf(10.0,4.0+model.chatSamplingParameters.size),model.chatSamplingParameters.joinToString(" · ")))
            }
            if(isEmpty()) {
                val documented=fields.filter {it.required || it.options.isNotEmpty() || it.minimum!=null || it.maximum!=null || it.maxLength!=null || it.maxItems!=null}
                add(EvaluatedAxis("Contrato documentado",if(fields.isEmpty()) 0.0 else 10.0*documented.size/fields.size,
                    "${documented.size} de ${fields.size} campos con obligación, opciones o rango documentado. No mide calidad del modelo."))
            }
        }
        return EndpointEvaluation(facts,axes,evidence?.source ?: model.schemaUrl ?: if(model.providerId=="runway") "https://docs.dev.runwayml.com/api/" else "Configuración del proveedor en Nexus",evidence?.checkedAt.orEmpty(),evidence)
    }
    private fun number(value: Double) = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    private fun title(key: String): String = when(key.lowercase()) {
        "aspect_ratio","aspectratio","ratio" -> "Formato"
        "resolution","size","image_size" -> "Resolución"
        "duration","video_duration" -> "Duración (s)"
        "fps","frame_rate" -> "Fotogramas/s"
        "guidance_scale","cfg_scale","cfgscale" -> "Adherencia (CFG)"
        "num_inference_steps","steps" -> "Pasos de inferencia"
        "image","image_url","input_image" -> "Imagen inicial"
        "last_frame","end_image","last_frame_image","last_image" -> "Fotograma final"
        "images","image_urls","reference_images" -> "Referencias de imagen"
        "refers" -> "Referencias multimodales"
        "video","video_url","input_video" -> "Vídeo de entrada"
        "audio","generate_audio","enable_audio" -> "Control de audio"
        "seed" -> "Semilla"
        "width" -> "Ancho (px)"
        "height" -> "Alto (px)"
        "enable_web_search","web_search" -> "Búsqueda web"
        "enable_base64_output","base64_output","output_base64" -> "Salida en base64"
        "enable_safety_checker","safety_checker" -> "Filtro de seguridad"
        "enable_prompt_expansion","prompt_expansion","prompt_extend" -> "Ampliar instrucciones"
        "output_format" -> "Formato de archivo"
        "num_images","n" -> "Número de imágenes"
        "negative_prompt" -> "Prompt negativo"
        "watermark" -> "Marca de agua"
        else -> ParameterLabels.label(key)
    }

    /** Provider tokens read as plain Spanish; unknown values are left exactly as published. */
    private fun humanValue(raw: String): String = when(raw.trim().lowercase()) {
        "default" -> "Predeterminada"
        "auto" -> "Automática"
        "true" -> "Sí"
        "false" -> "No"
        else -> raw
    }
}
