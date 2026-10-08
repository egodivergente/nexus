package com.vidadomu.nexus.provider.atlas

import com.vidadomu.nexus.core.network.awaitResponse
import com.vidadomu.nexus.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class NativeConfiguration(val model: NexusModel, val unsupportedRequired: List<String>, val notices: List<String> = emptyList())

object NativeModelSchema {
    fun validToken(value: String?): Boolean = value != null && value.isNotBlank() &&
        value.trim().lowercase() !in setOf("null", "undefined", "-", "–", "—") &&
        value.any { it.isLetterOrDigit() }

    private val client = OkHttpClient.Builder().addInterceptor(com.vidadomu.nexus.core.network.LocalQaInterceptor()).callTimeout(20, TimeUnit.SECONDS).build()
    private val cached = com.vidadomu.nexus.core.util.BoundedCache<String, NativeConfiguration>(96)
    private val schemas = com.vidadomu.nexus.core.util.BoundedCache<String, JsonObject>(128)

    fun promptLimit(modelId: String): com.vidadomu.nexus.agent.core.registry.TextLimit? {
        val prompt = schemas[modelId]?.get("properties")?.jsonObject?.get("prompt")?.jsonObject ?: return null
        val minimum = prompt["minLength"]?.jsonPrimitive?.intOrNull
        val maximum = prompt["maxLength"]?.jsonPrimitive?.intOrNull
        if (minimum == null && maximum == null) return null
        return com.vidadomu.nexus.agent.core.registry.TextLimit("unicode_code_points", minimum, maximum, "Atlas Input schema")
    }

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
        cached[model.id+":"+model.schemaUrl]?.takeIf {schemas[model.id]!=null}?.let { return@withContext it }
        val url = requireNotNull(model.schemaUrl) { "Este modelo no publica un esquema de configuración." }
        val parsed = java.net.URI(url)
        require(parsed.scheme == "https" && parsed.host == "static.atlascloud.ai") { "El esquema no procede del dominio oficial de Atlas." }
        val payload = client.newCall(Request.Builder().url(url).build()).awaitResponse().use {
            check(it.isSuccessful) { "No se pudo cargar la configuración (HTTP " + it.code + ")." }
            requireNotNull(it.body).string()
        }
        decode(model, payload).also { cached[model.id+":"+model.schemaUrl] = it }
    }

    fun decode(model: NexusModel, payload: String): NativeConfiguration {
        val root = Json.parseToJsonElement(payload).jsonObject
        val input = root["components"]?.jsonObject?.get("schemas")?.jsonObject?.get("Input")?.jsonObject
            ?: error("El esquema no incluye una definición Input compatible.")
        val props = JsonObject((input["properties"]?.jsonObject ?: error("El esquema no contiene propiedades.")).filterKeys { validToken(it) && !it.startsWith("__") })
        schemas[model.id] = JsonObject(input + ("properties" to props))
        val required = (input["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty().filter { validToken(it) }
        fun field(vararg names: String): String? = names.firstOrNull { name ->
            props[name] is JsonObject && (props[name]?.jsonObject?.get("type") as? JsonPrimitive)?.contentOrNull == "string"
        }
        val first = field("image","image_url","input_image","input_image_url","reference_image_url","first_frame_image","start_image")
        val last = field("end_image","last_image","last_frame_image")
        val video = field("video","video_url","input_video","input_video_url","source_video","source_video_url")
        fun arrayField(vararg names: String): String? = names.firstOrNull { name ->
            (props[name] as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "array"
        }
        val refers = (props["refers"] as? JsonObject)?.takeIf {
            it["type"]?.jsonPrimitive?.contentOrNull == "array" &&
                (it["items"] as? JsonObject)?.get("properties")?.jsonObject?.containsKey("url") == true
        }
        val structuredImages = refers != null
        fun mediaArray(kind: String): String? = props.entries.firstOrNull { (key, raw) ->
            val schema = raw as? JsonObject ?: return@firstOrNull false
            if(schema["type"]?.jsonPrimitive?.contentOrNull != "array") return@firstOrNull false
            val normalized = key.lowercase()
            if(kind !in normalized || normalized.contains("output") || normalized.contains("mask")) return@firstOrNull false
            val items = schema["items"] as? JsonObject ?: return@firstOrNull true
            items["type"]?.jsonPrimitive?.contentOrNull == "string" ||
                (items["properties"] as? JsonObject)?.keys?.any { it in setOf("url", "uri") } == true
        }?.key
        val images = arrayField(
            "images", "reference_images", "reference_image_urls", "image_urls",
            "input_images", "input_image_urls", "ref_images"
        ) ?: if(structuredImages) "refers" else mediaArray("image")
        val videos = arrayField(
            "reference_videos", "reference_video_urls", "videos", "video_urls",
            "input_videos", "input_video_urls"
        ) ?: mediaArray("video")
        val extraMedia=props.mapNotNull { (key,raw) ->
            val schema=raw as? JsonObject ?: return@mapNotNull null
            val type=schema["type"]?.jsonPrimitive?.contentOrNull
            val isArray=type=="array" && (schema["items"] as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull=="string"
            if(!(type=="string" || isArray) || !("audio" in key || "mask" in key)) return@mapNotNull null
            if(schema["enum"]!=null || key in setOf("audio_format","audio_codec","audio_language")) return@mapNotNull null
            ContractMediaField(key,if("mask" in key) "Máscara de edición" else "Audio de entrada",
                if("mask" in key) "image/*" else "audio/*",key in required,
                if(isArray) (schema["maxItems"]?.jsonPrimitive?.intOrNull ?: 20).coerceAtLeast(1) else 1,
                if(isArray) "array" else "string")
        }
        val media = listOfNotNull(first,last,video,images,videos).toSet()+extraMedia.map { it.key }
        val unsupported = required.filter { it != "model" && props[it] !is JsonObject }.toMutableList()
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
            if(type !in setOf("string","integer","number","boolean")) {
                if(key in required) unsupported += key
                return@mapNotNull null
            }
            val options = (p["enum"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty().filter { validToken(it) }.distinct()
            if (p["enum"] is JsonArray && options.isEmpty()) {
                if(key in required) unsupported += key
                return@mapNotNull null
            }
            val publishedDefault = (p["default"] as? JsonPrimitive)?.contentOrNull?.takeIf { validToken(it) } ?: if(type == "boolean" && key in required) "false" else ""
            val typedDefault = publishedDefault.takeIf { value ->
                when(type) {
                    "boolean" -> value in setOf("true", "false")
                    "integer" -> value.toLongOrNull() != null
                    "number" -> value.toDoubleOrNull()?.isFinite() == true
                    else -> true
                }
            }.orEmpty()
            val defaultValue = if(options.isNotEmpty() && typedDefault.isNotEmpty() && typedDefault !in options) {
                notices += "El proveedor publica un valor por defecto incoherente para " + key + "; selecciona una opción."
                ""
            } else typedDefault
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
                    "character_orientation"->"Orientación del personaje"; "keep_original_sound"->"Conservar sonido original";
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
                isAdvanced = key !in required && key !in setOf("aspect_ratio","resolution","duration","size","quality"),
                isRequired = key in required, nativeType = type)
        }
        // Absence of maxItems means unspecified, NOT one image.
        fun limit(key:String?): Int = key?.let { (props[it]?.jsonObject?.get("maxItems") as? JsonPrimitive)?.intOrNull }?.coerceAtLeast(0) ?: Int.MAX_VALUE
        val effectiveRequired = required.distinct()
        val mode = when {
            (first != null || images != null) && (video != null || videos != null) -> ModelInputMode.MULTI_MODAL
            images != null -> ModelInputMode.MULTI_IMAGE
            video != null || videos != null -> ModelInputMode.VIDEO_INPUT
            first != null && last != null -> ModelInputMode.DUAL_FRAME
            first != null -> ModelInputMode.SINGLE_IMAGE
            else -> ModelInputMode.TEXT_ONLY
        }
        val nativeCapabilities=if(video!=null && first!=null && "character_orientation" in props)
            model.capabilities.filterNot {it in setOf(ModelCapability.TEXT_TO_VIDEO,ModelCapability.IMAGE_TO_VIDEO)}+ModelCapability.MOTION_CONTROL
            else model.capabilities
        return NativeConfiguration(model.copy(capabilities=nativeCapabilities,parameters = FormContract.normalize(parameters), contractMedia=extraMedia, inputMode = mode, nativeRequiredFields = effectiveRequired,
            firstFrameField = first, inputImageField = first, lastFrameField = last,
            imagesArrayField = images, structuredImageReferences = structuredImages, videoField = video, videosArrayField = videos,
            maxImages = if(structuredImages) {
                refers?.get("x-media-limits")?.jsonObject?.get("image")?.jsonObject?.get("maxItems")?.jsonPrimitive?.intOrNull ?: limit(images)
            } else if(images != null) limit(images) else if(last != null) 2 else if(first != null) 1 else 0,
            maxVideos = if(videos != null) limit(videos) else if(video != null) 1 else 0,
            isMediaRequired = media.any { it in effectiveRequired },
            supportsNativeAspectRatio = "aspect_ratio" in props,
            supportedAspectRatios = parameters.find { it.key == "aspect_ratio" }?.options.orEmpty(),
            supportedResolutions = parameters.find { it.key == "resolution" }?.options.orEmpty(),
            supportedDurations = parameters.find { it.key == "duration" }?.options.orEmpty(),
            supportsImageInput = first != null || images != null, supportsLastFrame = last != null,
            supportsFirstFrame = first != null, supportsReferenceImages = images != null,
            supportsVideoInput = video != null || videos != null), unsupported, notices)
    }

    fun referenceCountProblem(model: NexusModel, count: Int): String? {
        val key = model.imagesArrayField ?: return null
        val input = schemas[model.id] ?: return null
        val field = (input["properties"] as? JsonObject)?.get(key) as? JsonObject ?: return null
        val minimum = (field["minItems"] as? JsonPrimitive)?.intOrNull ?: 0
        val arrayCount = count - if(model.firstFrameField != null) 1 else 0
        return if(arrayCount < minimum) "Necesita al menos $minimum referencias; has añadido $count." else null
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
