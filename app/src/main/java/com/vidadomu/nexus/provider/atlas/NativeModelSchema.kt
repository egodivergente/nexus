package com.vidadomu.nexus.provider.atlas

import com.vidadomu.nexus.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class NativeConfiguration(val model: NexusModel, val unsupportedRequired: List<String>, val notices: List<String> = emptyList())

object NativeModelSchema {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val cached = java.util.concurrent.ConcurrentHashMap<String, NativeConfiguration>()
    private val schemas = java.util.concurrent.ConcurrentHashMap<String, JsonObject>()

    suspend fun load(model: NexusModel): NativeConfiguration = withContext(Dispatchers.IO) {
        if(Modality.CHAT in model.modalities) return@withContext NativeConfiguration(model.copy(
            parameters = model.chatSamplingParameters.mapNotNull { key ->
                when(key) {
                    "temperature" -> ModelParameter(key,"Creatividad",ParameterType.FLOAT,nativeType="number",isAdvanced=true)
                    "top_p" -> ModelParameter(key,"Diversidad de respuesta",ParameterType.FLOAT,nativeType="number",isAdvanced=true)
                    "max_tokens" -> ModelParameter(key,"Máximo de tokens de respuesta",ParameterType.INTEGER,defaultValue="1024",minValue=1f,nativeType="integer")
                    else -> null
                }
            }, inputMode=ModelInputMode.TEXT_ONLY, isMediaRequired=false, maxImages=0, maxVideos=0,
            firstFrameField=null,lastFrameField=null,imagesArrayField=null,videoField=null,videosArrayField=null,
            inputImageField=null,supportsImageInput=false,supportsVideoInput=false,supportsReferenceImages=false,
            nativeRequiredFields=listOf("prompt")),emptyList())
        cached[model.id]?.let { return@withContext it }
        val url = requireNotNull(model.schemaUrl) { "Este modelo no publica un esquema de configuración." }
        val parsed = java.net.URI(url)
        require(parsed.scheme == "https" && parsed.host == "static.atlascloud.ai") { "El esquema no procede del dominio oficial de Atlas." }
        val payload = client.newCall(Request.Builder().url(url).build()).execute().use {
            check(it.isSuccessful) { "No se pudo cargar la configuración (HTTP " + it.code + ")." }
            requireNotNull(it.body).string()
        }
        decode(model, payload).also { cached[model.id] = it }
    }

    fun decode(model: NexusModel, payload: String): NativeConfiguration {
        val root = Json.parseToJsonElement(payload).jsonObject
        val input = root["components"]?.jsonObject?.get("schemas")?.jsonObject?.get("Input")?.jsonObject
            ?: error("El esquema no incluye una definición Input compatible.")
        val props = input["properties"]?.jsonObject ?: error("El esquema no contiene propiedades.")
        schemas[model.id] = input
        val required = (input["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        fun field(vararg names: String): String? = names.firstOrNull { name ->
            props[name] is JsonObject && (props[name]?.jsonObject?.get("type") as? JsonPrimitive)?.contentOrNull == "string"
        }
        val first = field("image","image_url","input_image","first_frame_image","start_image")
        val last = field("end_image","last_image","last_frame_image")
        val video = field("video","video_url","input_video")
        fun arrayField(vararg names: String): String? = names.firstOrNull { name ->
            (props[name] as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "array"
        }
        val images = arrayField("images","reference_images","image_urls")
        val videos = arrayField("reference_videos","videos")
        val media = listOfNotNull(first,last,video,images,videos).toSet()
        val unsupported = mutableListOf<String>()
        val notices = mutableListOf<String>()
        val parameters = props.mapNotNull { (key, raw) ->
            if(key in media || key in setOf("model","prompt")) return@mapNotNull null
            val p = raw as? JsonObject ?: return@mapNotNull null
            val type = (p["type"] as? JsonPrimitive)?.contentOrNull
            // Structured editors and additional media inputs need a dedicated UI.
            if(type in setOf("array","object") || key in setOf("audio","audio_url","mask","mask_image","mask_url") && type == "string") {
                if(key in required) unsupported += key
                else notices += "Entrada opcional no disponible: " + key
                return@mapNotNull null
            }
            val options = (p["enum"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            val publishedDefault = (p["default"] as? JsonPrimitive)?.contentOrNull ?: ""
            val defaultValue = if(options.isNotEmpty() && publishedDefault.isNotEmpty() && publishedDefault !in options) {
                notices += "El proveedor publica un valor por defecto incoherente para " + key + "; selecciona una opción."
                ""
            } else publishedDefault
            val kind = when {
                options.isNotEmpty() -> when(key) {
                    "aspect_ratio" -> ParameterType.ASPECT_RATIO
                    "resolution","size" -> ParameterType.RESOLUTION
                    "duration" -> ParameterType.DURATION
                    else -> ParameterType.ENUM
                }
                type == "boolean" -> ParameterType.BOOLEAN
                key == "seed" -> ParameterType.SEED
                type == "integer" -> ParameterType.INTEGER
                type == "number" -> ParameterType.FLOAT
                type == "string" -> ParameterType.TEXT
                else -> { if(key in required) unsupported += key; return@mapNotNull null }
            }
            ModelParameter(key = key,
                displayName = when(key) {
                    "aspect_ratio"->"Formato"; "resolution"->"Resolución"; "duration"->"Duración"
                    "size"->"Tamaño"; "quality"->"Calidad"; "seed"->"Semilla"; "negative_prompt"->"Prompt negativo"
                    "ratio"->"Formato"; "audio"->"Generar sonido"; "generate_audio"->"Generar sonido"
                    "n","num_images"->"Número de imágenes"; "output_format"->"Formato de archivo"
                    "background"->"Fondo"; "moderation"->"Moderación"; "guidance_scale","cfg_scale"->"Fidelidad a las instrucciones"
                    "num_inference_steps","steps"->"Pasos"; "enable_safety_checker"->"Comprobación de seguridad"
                    "prompt_enhancement","enable_prompt_expansion"->"Ampliar instrucciones"; "watermark"->"Marca de agua"
                    else->key.replace('_',' ')
                }, type = kind, defaultValue = defaultValue,
                options = options, minValue = (p["minimum"] as? JsonPrimitive)?.floatOrNull,
                maxValue = (p["maximum"] as? JsonPrimitive)?.floatOrNull,
                isAdvanced = key !in setOf("aspect_ratio","resolution","duration","size","quality"),
                isRequired = key in required, nativeType = type)
        }
        fun limit(key:String?): Int = key?.let { (props[it]?.jsonObject?.get("maxItems") as? JsonPrimitive)?.intOrNull } ?: 1
        val mode = when {
            images != null && (video != null || videos != null) -> ModelInputMode.MULTI_MODAL
            video != null -> ModelInputMode.VIDEO_INPUT
            images != null -> ModelInputMode.MULTI_IMAGE
            first != null && last != null -> ModelInputMode.DUAL_FRAME
            first != null -> ModelInputMode.SINGLE_IMAGE
            else -> ModelInputMode.TEXT_ONLY
        }
        return NativeConfiguration(model.copy(parameters = parameters, inputMode = mode, nativeRequiredFields = required,
            firstFrameField = first, inputImageField = first, lastFrameField = last,
            imagesArrayField = images, videoField = video, videosArrayField = videos,
            maxImages = if(images != null) limit(images) else if(last != null) 2 else if(first != null) 1 else 0,
            maxVideos = if(videos != null) limit(videos) else if(video != null) 1 else 0,
            isMediaRequired = media.any { it in required },
            supportsNativeAspectRatio = "aspect_ratio" in props,
            supportedAspectRatios = parameters.find { it.key == "aspect_ratio" }?.options.orEmpty(),
            supportedResolutions = parameters.find { it.key == "resolution" }?.options.orEmpty(),
            supportedDurations = parameters.find { it.key == "duration" }?.options.orEmpty(),
            supportsImageInput = first != null || images != null, supportsLastFrame = last != null,
            supportsFirstFrame = first != null, supportsReferenceImages = images != null,
            supportsVideoInput = video != null || videos != null), unsupported, notices)
    }

    /** Validate published length and conditional enum constraints before any paid request. */
    fun validate(model: NexusModel, values: Map<String, String>): String? {
        if(Modality.CHAT in model.modalities) return if(values["prompt"].isNullOrBlank()) "Escribe tu consulta." else null
        val input = schemas[model.id] ?: return "Vuelve a seleccionar el modelo para cargar su configuración."
        fun checkProperties(schema: JsonObject): String? {
            val props = schema["properties"] as? JsonObject ?: return null
            for((key, raw) in props) {
                val value = values[key] ?: continue
                val p = raw as? JsonObject ?: continue
                val enum = (p["enum"] as? JsonArray)?.map { it.jsonPrimitive.content }
                if(enum != null && value !in enum) return "Opción no permitida para " + key
                val max = (p["maxLength"] as? JsonPrimitive)?.intOrNull
                val min = (p["minLength"] as? JsonPrimitive)?.intOrNull
                if(max != null && value.length > max || min != null && value.length < min) return "Revisa la longitud de " + key
            }
            return null
        }
        checkProperties(input)?.let { return it }
        for(raw in (input["allOf"] as? JsonArray).orEmpty()) {
            val rule = raw as? JsonObject ?: continue
            val condition = rule["if"] as? JsonObject ?: continue
            val props = condition["properties"] as? JsonObject ?: continue
            val matches = props.all { (key, raw) ->
                val p = raw as? JsonObject
                values[key] != null && values[key] == (p?.get("const") as? JsonPrimitive)?.contentOrNull
            }
            if(matches) (rule["then"] as? JsonObject)?.let { checkProperties(it)?.let { error -> return error } }
        }
        return null
    }
}
