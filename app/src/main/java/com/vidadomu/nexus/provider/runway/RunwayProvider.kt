package com.vidadomu.nexus.provider.runway

import com.vidadomu.nexus.core.network.awaitResponse
import okhttp3.RequestBody.Companion.asRequestBody
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.core.network.NetworkErrorParser
import com.vidadomu.nexus.core.security.SecureStorage
import com.vidadomu.nexus.provider.api.NexusProvider
import com.vidadomu.nexus.provider.api.ProviderAccountStatus
import com.vidadomu.nexus.provider.api.ProviderBalanceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody

object RunwayModelCatalog {
    private fun enumParam(key:String,name:String,default:String,options:List<String>) =
        ModelParameter(key,name,ParameterType.ENUM,default,options=options,nativeType="string")
    private fun duration(min:Int,max:Int,default:Int=5) =
        ModelParameter("duration","Duración",ParameterType.DURATION,default.toString(),options=(min..max).map(Int::toString),nativeType="integer")
    private fun video(
        nativeId:String,name:String,family:String,version:Double,rank:Int=3,
        centsPerSecond:Int,mode:String="i2v",resolutions:List<String> = emptyList(),
        ratios:List<String> = listOf("1280:720","720:1280"),durations:IntRange=2..10,
        audio:Boolean=false
    ): NexusModel {
        val inputMode=when(mode){"t2v"->ModelInputMode.TEXT_ONLY;"v2v"->ModelInputMode.VIDEO_INPUT;"r2v"->ModelInputMode.MULTI_MODAL;else->ModelInputMode.SINGLE_IMAGE}
        val videoInputKey=if(nativeId=="aleph2" || nativeId.startsWith("gemini_omni_flash")) "videoUri" else "promptVideo"
        val routeName=when(mode){"t2v"->"Texto a vídeo";"v2v"->"Vídeo a vídeo";"r2v"->"Referencias a vídeo";else->"Imagen a vídeo"}
        val maxResolution=resolutions.lastOrNull()
        return NexusModel(
            id="runway/$nativeId/$mode",providerModelId=nativeId,displayName="$name · $routeName",
            providerId=RunwayProvider.PROVIDER_ID,organization=family,modalities=listOf(Modality.VIDEO),
            capabilities=buildList { add(ModelCapability.TEXT_TO_VIDEO);if(mode!="t2v") add(ModelCapability.IMAGE_TO_VIDEO);if(mode=="v2v") add(ModelCapability.VIDEO_TO_VIDEO);if(mode=="r2v") add(ModelCapability.REFERENCE_TO_VIDEO) },
            parameters=buildList {
                if(ratios.isNotEmpty()) add(enumParam("ratio","Formato",ratios.first(),ratios))
                if(resolutions.isNotEmpty()) add(enumParam("resolution","Calidad",maxResolution!!,resolutions))
                add(duration(durations.first,durations.last,durations.first.coerceAtLeast(5)))
                if(audio) add(ModelParameter("audio","Generar sonido",ParameterType.BOOLEAN,"true",nativeType="boolean"))
            },
            description="Ruta oficial de $name mediante Runway Dev.",
            pricingInfo=if(centsPerSecond>0) "${centsPerSecond} créditos/s en la calidad predeterminada máxima" else "Tarifa variable publicada por Runway",
            basePriceUsd=centsPerSecond.takeIf { it>0 }?.times(.01),priceUnit="second",priceUnitVerified=centsPerSecond>0,
            inputMode=inputMode,maxImages=if(mode in setOf("i2v","r2v")) if(mode=="r2v") 10 else 2 else 0,
            maxVideos=if(mode in setOf("v2v","r2v")) if(mode=="r2v") 5 else 1 else 0,
            firstFrameField=if(mode=="i2v") "promptImage" else null,
            imagesArrayField=if(mode=="r2v") "referenceImages" else null,
            videoField=if(mode=="v2v") videoInputKey else null,
            videosArrayField=if(mode=="r2v") "referenceVideos" else null,
            isMediaRequired=mode!="t2v",nativeRequiredFields=when(mode){"i2v"->listOf("promptImage");"v2v"->listOf(videoInputKey);"r2v"->listOf("referenceImages");else->listOf("prompt")},
            supportsImageInput=mode in setOf("i2v","r2v"),supportsFirstFrame=mode=="i2v",supportsLastFrame=mode=="i2v" && family=="Wan",
            supportsReferenceImages=mode=="r2v",supportsVideoInput=mode in setOf("v2v","r2v"),inputImageField=if(mode=="i2v") "promptImage" else null,
            supportsAudio=audio,supportedAspectRatios=ratios,supportedResolutions=resolutions,supportedDurations=durations.map(Int::toString),
            shortDescription="Calidad máxima disponible por defecto; puedes bajarla manualmente.",speedQualityTag="Alta calidad",isFlagship=rank>=4,
            versionNumeric=version,modelFamily=family,variantRank=rank,isPrimaryGeneration=mode in setOf("t2v","i2v"),providerPriority=98
        )
    }

    private data class Spec(
        val id:String,val name:String,val family:String,val version:Double,val rank:Int,val credits:Int,
        val modes:Set<String>,val resolutions:List<String>,val durations:IntRange,val audio:Boolean=false,
        val ratios:List<String> = listOf("1280:720","720:1280")
    )

    private fun image(nativeId:String,name:String,family:String,credits:Double,mode:String,maxRefs:Int=3,version:Double=1.0,rank:Int=3):NexusModel = NexusModel(
        id="runway/$nativeId/$mode",providerModelId=nativeId,displayName="$name · ${if(mode=="t2i") "Texto a imagen" else "Editar con referencias"}",
        providerId=RunwayProvider.PROVIDER_ID,organization=family,modalities=listOf(Modality.IMAGE),
        capabilities=if(mode=="t2i") listOf(ModelCapability.TEXT_TO_IMAGE) else listOf(ModelCapability.IMAGE_TO_IMAGE,ModelCapability.IMAGE_EDITING),
        parameters=listOf(
            enumParam("ratio","Formato","1920:1080",listOf("1920:1080","1080:1920","1024:1024","1360:768","768:1360")),
            enumParam("resolution","Calidad","2K",listOf("1K","2K")),
            ModelParameter("outputCount","Resultados",ParameterType.INTEGER,"1",minValue=1f,maxValue=4f,nativeType="integer")
        ),description="Ruta oficial de $name mediante Runway Dev.",
        pricingInfo=if(credits>0) "${credits.toBigDecimal().stripTrailingZeros().toPlainString()} créditos por imagen en calidad alta" else "Tarifa publicada por Runway según calidad",
        basePriceUsd=credits.takeIf { it>0 }?.times(.01),priceUnit="image",priceUnitVerified=credits>0,
        inputMode=if(mode=="t2i") ModelInputMode.TEXT_ONLY else ModelInputMode.MULTI_IMAGE,maxImages=if(mode=="t2i") 0 else maxRefs,
        imagesArrayField=if(mode=="i2i") "referenceImages" else null,isMediaRequired=mode=="i2i",
        nativeRequiredFields=if(mode=="i2i") listOf("prompt","referenceImages") else listOf("prompt"),
        supportsImageInput=mode=="i2i",supportsReferenceImages=mode=="i2i",supportedAspectRatios=listOf("1920:1080","1080:1920","1024:1024","1360:768","768:1360"),
        supportedResolutions=listOf("1K","2K"),shortDescription="Generación directa con la API oficial de Runway.",speedQualityTag="Alta calidad",
        versionNumeric=version,modelFamily=family,variantRank=rank,isPrimaryGeneration=mode=="t2i",providerPriority=98
    )

    private val specs=listOf(
        Spec("wan3","Wan 3.0","Wan",3.0,3,20,setOf("t2v","i2v"),listOf("480p","720p","1080p"),2..30,true,listOf("16:9","9:16","4:3","3:4","1:1")),
        Spec("wan3_prime","Wan 3.0 Prime","Wan",3.0,4,0,setOf("t2v","i2v"),listOf("480p","720p","1080p"),2..30,true,listOf("16:9","9:16","4:3","3:4","1:1")),
        Spec("seedance2_5","Seedance 2.5","Seedance",2.5,4,68,setOf("t2v","i2v","v2v"),listOf("480p","720p","1080p"),4..30,true),
        Spec("grok_imagine_1_5","Grok Imagine 1.5","Grok",1.5,4,29,setOf("t2v","i2v"),listOf("480p","720p","1080p"),1..15,true),
        Spec("seedance2","Seedance 2.0","Seedance",2.0,3,150,setOf("t2v","i2v","v2v"),listOf("480p","720p","1080p","4K"),4..15,true),
        Spec("seedance2_fast","Seedance 2.0 Fast","Seedance",2.0,2,29,setOf("t2v","i2v","v2v"),listOf("480p","720p"),4..15,true),
        Spec("seedance2_mini","Seedance 2.0 Mini","Seedance",2.0,1,16,setOf("t2v","i2v","v2v"),listOf("480p","720p"),4..15,true),
        Spec("h3_max","MiniMax H3 Max","MiniMax",3.0,4,8,setOf("t2v","i2v"),listOf("480p","768p"),5..15,false,listOf("adaptive","21:9","16:9","4:3","1:1","3:4","9:16")),
        Spec("hailuo3","MiniMax Hailuo 3","MiniMax",3.0,3,15,setOf("t2v","i2v","v2v"),listOf("768p","2k"),4..15,true),
        Spec("aleph2","Runway Aleph 2.0","Runway",2.0,4,28,setOf("v2v"),emptyList(),2..30),
        Spec("gen4.5","Runway Gen-4.5","Runway",4.5,4,12,setOf("t2v","i2v"),emptyList(),2..10),
        Spec("gen4_turbo","Runway Gen-4 Turbo","Runway",4.0,2,5,setOf("i2v"),emptyList(),2..10),
        Spec("act_two","Runway Act-Two","Runway",2.0,3,5,setOf("i2v","v2v"),emptyList(),3..30),
        Spec("veo3.1","Google Veo 3.1","Google",3.1,4,40,setOf("t2v","i2v"),listOf("720p","1080p"),4..8,true),
        Spec("veo3.1_fast","Google Veo 3.1 Fast","Google",3.1,2,15,setOf("t2v","i2v"),listOf("720p","1080p"),4..8,true),
        Spec("happyhorse_1_0","HappyHorse 1.0","HappyHorse",1.0,3,30,setOf("t2v","i2v"),listOf("720P","1080P"),4..15,true),
        Spec("gemini_omni_flash","Gemini Omni Flash","Google",1.0,2,10,setOf("t2v","i2v","v2v"),listOf("720p"),4..10,true)
        ,Spec("gemini_omni_flash_1.1","Gemini Omni Flash 1.1","Google",1.1,3,0,setOf("t2v","i2v","v2v"),listOf("720p"),4..10,true)
    )

    private data class ImageSpec(val id:String,val name:String,val family:String,val credits:Double,val maxRefs:Int=3,val version:Double=1.0,val rank:Int=3,val modes:Set<String> = setOf("t2i","i2i"))
    private val imageSpecs=listOf(
        ImageSpec("muse_image","Muse Image","Meta",1.0,10,1.0,3),
        ImageSpec("grok_imagine_image_2","Grok Imagine Image 2","Grok",8.0,3,2.0,3),
        ImageSpec("seedream5_pro","Seedream 5 Pro","Seedream",9.0,10,5.0,4),
        ImageSpec("seedream5_lite","Seedream 5 Lite","Seedream",4.0,10,5.0,1),
        ImageSpec("gen4_image","Runway Gen-4 Image","Runway",8.0,3,4.0,4),
        ImageSpec("gen4_image_turbo","Runway Gen-4 Image Turbo","Runway",2.0,3,4.0,2,setOf("i2i")),
        ImageSpec("gemini_image3_pro","Nano Banana Pro","Google",20.0,10,3.0,4),
        ImageSpec("gemini_image3.1_flash","Nano Banana 2","Google",0.0,10,3.1,2),
        ImageSpec("gpt_image_2","GPT Image 2","OpenAI",20.0,3,2.0,3),
        ImageSpec("gemini_2.5_flash","Nano Banana","Google",5.0,10,2.5,2)
    )

    private val upscalers=listOf(
        NexusModel(
            id="runway/magnific_precision_upscaler_v2",providerModelId="magnific_precision_upscaler_v2",displayName="Magnific Precision Upscaler v2",
            providerId=RunwayProvider.PROVIDER_ID,organization="Magnific",modalities=listOf(Modality.IMAGE),capabilities=listOf(ModelCapability.UPSCALE),
            parameters=listOf(
                enumParam("scaleFactor","Escala","4",listOf("2","4","8","16")),
                ModelParameter("sharpen","Nitidez",ParameterType.INTEGER,"50",minValue=0f,maxValue=100f,nativeType="integer"),
                ModelParameter("smartGrain","Grano inteligente",ParameterType.INTEGER,"0",minValue=0f,maxValue=100f,nativeType="integer"),
                ModelParameter("ultraDetail","Detalle ultra",ParameterType.INTEGER,"50",minValue=0f,maxValue=100f,nativeType="integer"),
                enumParam("flavor","Perfil","photo",listOf("sublime","photo","photo_denoiser"))
            ),description="Upscaler de precisión de Magnific mediante Runway.",pricingInfo="25 créditos por imagen; 150 si supera 4096 px",
            basePriceUsd=.25,priceUnit="image",priceUnitVerified=true,inputMode=ModelInputMode.SINGLE_IMAGE,maxImages=1,firstFrameField="imageUri",
            isMediaRequired=true,nativeRequiredFields=listOf("imageUri"),supportsImageInput=true,supportsFirstFrame=true,inputImageField="imageUri",
            shortDescription="Escalado de alta fidelidad con detalle y nitidez regulables.",speedQualityTag="Alta calidad",versionNumeric=2.0,modelFamily="Magnific Upscale",variantRank=4,providerPriority=98
        ),
        NexusModel(
            id="runway/magnific_video_upscaler_creative",providerModelId="magnific_video_upscaler_creative",displayName="Magnific Video Upscaler Creative",
            providerId=RunwayProvider.PROVIDER_ID,organization="Magnific",modalities=listOf(Modality.VIDEO),capabilities=listOf(ModelCapability.UPSCALE,ModelCapability.VIDEO_TO_VIDEO),
            parameters=listOf(
                enumParam("resolution","Calidad","4k",listOf("720p","1k","2k","4k")),
                ModelParameter("creativity","Creatividad",ParameterType.INTEGER,"25",minValue=0f,maxValue=100f,nativeType="integer"),
                ModelParameter("sharpen","Nitidez",ParameterType.INTEGER,"50",minValue=0f,maxValue=100f,nativeType="integer"),
                ModelParameter("smartGrain","Grano inteligente",ParameterType.INTEGER,"0",minValue=0f,maxValue=100f,nativeType="integer"),
                enumParam("flavor","Perfil","natural",listOf("vivid","natural")),
                ModelParameter("fpsBoost","Mejorar fotogramas por segundo",ParameterType.BOOLEAN,"false",nativeType="boolean")
            ),description="Upscaler de vídeo Magnific Creative mediante Runway.",pricingInfo="Tarifa por fotograma: 0,012 USD a 4K",
            priceUnitVerified=false,inputMode=ModelInputMode.VIDEO_INPUT,maxVideos=1,videoField="videoUri",isMediaRequired=true,nativeRequiredFields=listOf("videoUri"),
            supportsVideoInput=true,supportedResolutions=listOf("720p","1k","2k","4k"),shortDescription="Escalado de vídeo hasta 4K.",speedQualityTag="Alta calidad",
            versionNumeric=1.0,modelFamily="Magnific Upscale",variantRank=4,isPrimaryGeneration=false,providerPriority=98
        )
    )

    val models:List<NexusModel> = specs.flatMap { spec -> spec.modes.map { mode ->
        video(spec.id,spec.name,spec.family,spec.version,spec.rank,spec.credits,mode,spec.resolutions,spec.ratios,spec.durations,spec.audio)
    }} + imageSpecs.flatMap { spec -> spec.modes.map { mode -> image(spec.id,spec.name,spec.family,spec.credits,mode,spec.maxRefs,spec.version,spec.rank) } } + upscalers

    fun find(appId:String):NexusModel? = RunwayContracts.models.firstOrNull { it.id==appId } ?: models.firstOrNull { it.id==appId }
        ?: if(appId=="runway/gen4.5") models.firstOrNull { it.id=="runway/gen4.5/i2v" } else null
}

object RunwayPayloadCodec {
    fun encode(request: GenerationRequest): JsonObject = encode(
        RunwayModelCatalog.find(request.modelId)
            ?: throw IllegalArgumentException("Modelo Runway no reconocido: ${request.modelId}"),request)

    fun encode(model:NexusModel,request: GenerationRequest): JsonObject = if(model.contractEndpoint!=null) ContractPayload.encode(model,request) else buildJsonObject {
        put("model", model.providerModelId ?: model.id.substringAfter("runway/").substringBefore('/'))
        val images=request.mediaInputs.filter { it.type==Modality.IMAGE }.mapNotNull { it.remoteUrl }
        val videos=request.mediaInputs.filter { it.type==Modality.VIDEO }.mapNotNull { it.remoteUrl }
        if(ModelCapability.UPSCALE in model.capabilities) {
            images.firstOrNull()?.let { put("imageUri",it) }
            videos.firstOrNull()?.let { put("videoUri",it) }
            model.parameters.forEach { parameter -> request.parameters[parameter.key]?.takeIf(String::isNotBlank)?.let { value ->
                put(parameter.key,when(parameter.nativeType) {
                    "integer" -> JsonPrimitive(value.toIntOrNull() ?: 0)
                    "number" -> JsonPrimitive(value.toDoubleOrNull() ?: 0.0)
                    "boolean" -> JsonPrimitive(value.toBoolean())
                    else -> JsonPrimitive(value)
                })
            }}
            return@buildJsonObject
        }
        put("promptText", request.prompt.trim())
        request.parameters["ratio"]?.takeIf(String::isNotBlank)?.let { put("ratio",it) }
        request.parameters["resolution"]?.takeIf(String::isNotBlank)?.let { put("resolution",it) }
        if(model.modalities.contains(Modality.VIDEO)) put("duration", request.parameters["duration"]?.toIntOrNull() ?: 5)
        request.parameters["outputCount"]?.toIntOrNull()?.let { put("outputCount",it) }
        request.parameters["audio"]?.toBooleanStrictOrNull()?.let { put("audio",it) }
        when {
            model.modalities.contains(Modality.IMAGE) && images.isNotEmpty() -> put("referenceImages",JsonArray(images.mapIndexed { index,url -> buildJsonObject { put("uri",url);put("tag","ref${index+1}") } }))
            model.inputMode==ModelInputMode.MULTI_MODAL -> {
                if(images.isNotEmpty()) put("referenceImages",JsonArray(images.map { url -> buildJsonObject { put("uri",url) } }))
                if(videos.isNotEmpty()) put("referenceVideos",JsonArray(videos.map { url -> buildJsonObject { put("uri",url) } }))
            }
            model.inputMode==ModelInputMode.VIDEO_INPUT -> videos.firstOrNull()?.let { put(model.videoField ?: "promptVideo",it) }
            else -> if(images.size>1) put("promptImage",JsonArray(listOf(
                buildJsonObject { put("uri",images[0]);put("position","first") },
                buildJsonObject { put("uri",images[1]);put("position","last") }
            ))) else images.firstOrNull()?.let { put("promptImage",it) }
        }
    }
}

class RunwayProvider(
    private val client: OkHttpClient,
    private val secureStorage: SecureStorage
) : NexusProvider {
    override val id = PROVIDER_ID
    override val displayName = "Runway"
    override val description = "API directa oficial de Runway."
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getModels(): List<NexusModel> = RunwayContracts.models

    override suspend fun testConnection(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(ProviderError("Configura la clave API de Runway."))
        execute(Request.Builder().url("$BASE_URL/v1/organization").headers(apiKey).get().build()) { true }
    }

    override suspend fun fetchAccountStatus(apiKey: String): Result<ProviderAccountStatus> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(ProviderError("Configura la clave API de Runway."))
        execute(Request.Builder().url("$BASE_URL/v1/organization").headers(apiKey).get().build()) { body ->
            ProviderBalanceParser.decode(body, listOf("creditBalance", "credit_balance", "credits", "balance"))
        }
    }

    override suspend fun submitGeneration(request: GenerationRequest): Result<GenerationJob> = withContext(Dispatchers.IO) {
        val key = secureStorage.getProviderApiKey(PROVIDER_ID)
        if (key.isBlank()) return@withContext Result.failure(ProviderError("Configura la clave API de Runway en Ajustes."))
        val model=RunwayModelCatalog.find(request.modelId)
            ?: return@withContext Result.failure(ProviderError("El modelo ${request.modelId} no pertenece al catálogo de Runway."))
        val payload = RunwayPayloadCodec.encode(model,request).toString()
        val endpoint=model.contractEndpoint ?: when {
            ModelCapability.UPSCALE in model.capabilities && model.modalities.contains(Modality.IMAGE) -> "image_upscale"
            ModelCapability.UPSCALE in model.capabilities -> "video_upscale"
            model.modalities.contains(Modality.IMAGE) -> "text_to_image"
            model.inputMode==ModelInputMode.VIDEO_INPUT -> "video_to_video"
            model.id.endsWith("/t2v") -> "text_to_video"
            else -> "image_to_video"
        }
        val httpRequest = Request.Builder().url("$BASE_URL/v1/$endpoint").headers(key)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE)).build()
        execute(httpRequest) { body ->
            val taskId = json.parseToJsonElement(body).jsonObject["id"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("Runway no devolvió un identificador de tarea.")
            GenerationJob(
                id = request.id,
                providerJobId = taskId,
                providerId = PROVIDER_ID,
                modelId = request.modelId,
                modality = request.modality,
                prompt = request.prompt,
                parameters = request.parameters,
                inputMediaUri = request.mediaInputs.firstOrNull()?.uriString,
                status = GenerationStatus.QUEUED,
                estimatedCostUsd = RunwayBilling.estimatedUsd(json.parseToJsonElement(body).jsonObject) ?: RunwayPricing.quote(model,request.parameters,request.mediaInputs.count { it.type==Modality.IMAGE },promptLength=request.prompt.length).usd,
                technicalDetails = "Runway task creada correctamente."
            )
        }
    }

    override suspend fun pollJobStatus(job: GenerationJob): Result<GenerationJob> = withContext(Dispatchers.IO) {
        val taskId = job.providerJobId ?: return@withContext Result.failure(ProviderError("Falta el identificador de Runway."))
        val key = secureStorage.getProviderApiKey(PROVIDER_ID)
        val httpRequest = Request.Builder().url("$BASE_URL/v1/tasks/$taskId").headers(key).get().build()
        execute(httpRequest) { body ->
            val root = json.parseToJsonElement(body).jsonObject
            val statusText = root["status"]?.jsonPrimitive?.contentOrNull?.uppercase().orEmpty()
            val status = when (statusText) {
                "SUCCEEDED", "COMPLETED" -> GenerationStatus.COMPLETED
                "FAILED" -> GenerationStatus.FAILED
                "CANCELLED" -> GenerationStatus.CANCELLED
                "PENDING", "THROTTLED" -> GenerationStatus.QUEUED
                else -> GenerationStatus.GENERATING
            }
            val urls = (root["output"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
            job.copy(
                actualCostUsd = RunwayBilling.confirmedUsd(root) ?: job.actualCostUsd,
                estimatedCostUsd = RunwayBilling.estimatedUsd(root) ?: job.estimatedCostUsd,
                status = status,
                progress = if (status == GenerationStatus.COMPLETED) 1f else job.progress,
                completedAt = if (status in setOf(GenerationStatus.COMPLETED, GenerationStatus.FAILED)) System.currentTimeMillis() else null,
                error = root["failure"]?.jsonPrimitive?.contentOrNull,
                outputs = if (urls.isEmpty()) job.outputs else urls.mapIndexed { index, value ->
                    GenerationOutput("${job.id}_out_$index", job.id, job.modality, value)
                },
                technicalDetails = "Runway status: $statusText"
            )
        }
    }

    override val supportsRemoteCancellation: Boolean = true

    override suspend fun cancelJob(jobId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val key = secureStorage.getProviderApiKey(PROVIDER_ID)
        if (key.isBlank()) return@withContext Result.failure(ProviderError("Configura la clave API de Runway en Ajustes."))
        execute(Request.Builder().url("$BASE_URL/v1/tasks/$jobId").headers(key).delete().build()) { true }
    }

    override suspend fun uploadMedia(fileBytes: ByteArray, fileName: String, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        val key=secureStorage.getProviderApiKey(PROVIDER_ID)
        if(key.isBlank()) return@withContext Result.failure(ProviderError("Configura la clave API de Runway en Ajustes."))
        if(fileBytes.size<512) return@withContext Result.failure(ProviderError("Runway exige archivos de al menos 512 bytes."))
        if(fileBytes.size>200*1024*1024) return@withContext Result.failure(ProviderError("Runway admite archivos de hasta 200 MB."))
        val safeName=fileName.replace(Regex("[^A-Za-z0-9._-]"),"_").take(120).ifBlank { "nexus-upload" }
        val ticketPayload=buildJsonObject { put("filename",safeName);put("type","ephemeral") }.toString()
        execute(Request.Builder().url("$BASE_URL/v1/uploads").headers(key)
            .post(ticketPayload.toRequestBody(JSON_MEDIA_TYPE)).build()) { body ->
            val ticket=json.parseToJsonElement(body).jsonObject
            val uploadUrl=ticket["uploadUrl"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("Runway no devolvió uploadUrl.")
            val runwayUri=ticket["runwayUri"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("Runway no devolvió runwayUri.")
            val multipart=MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                ticket["fields"]?.jsonObject?.forEach { (name,value) ->
                    addFormDataPart(name,value.jsonPrimitive.content)
                }
                addFormDataPart("file",safeName,fileBytes.toRequestBody(mimeType.toMediaType()))
            }.build()
            client.newCall(Request.Builder().url(uploadUrl).post(multipart).build()).awaitResponse().use { response ->
                if(!response.isSuccessful) throw ProviderError("Runway rechazó la subida (${response.code}).")
            }
            runwayUri
        }
    }

    override suspend fun uploadFile(file: java.io.File, fileName: String, mimeType: String, onProgress:(Long,Long)->Unit): Result<String> = withContext(Dispatchers.IO) {
        val key=secureStorage.getProviderApiKey(PROVIDER_ID)
        if(key.isBlank()) return@withContext Result.failure(ProviderError("Configura la clave API de Runway en Ajustes."))
        if(file.length()<512) return@withContext Result.failure(ProviderError("Runway exige archivos de al menos 512 bytes."))
        if(file.length()>200*1024*1024) return@withContext Result.failure(ProviderError("Runway admite archivos de hasta 200 MB."))
        val safeName=fileName.replace(Regex("[^A-Za-z0-9._-]"),"_").take(120).ifBlank { "nexus-upload" }
        val ticketPayload=buildJsonObject { put("filename",safeName);put("type","ephemeral") }.toString()
        execute(Request.Builder().url("$BASE_URL/v1/uploads").headers(key)
            .post(ticketPayload.toRequestBody(JSON_MEDIA_TYPE)).build()) { body ->
            val ticket=json.parseToJsonElement(body).jsonObject
            val uploadUrl=ticket["uploadUrl"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("Runway no devolvió uploadUrl.")
            val runwayUri=ticket["runwayUri"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("Runway no devolvió runwayUri.")
            val multipart=MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                ticket["fields"]?.jsonObject?.forEach { (name,value) ->
                    addFormDataPart(name,value.jsonPrimitive.content)
                }
                addFormDataPart("file",safeName,com.vidadomu.nexus.core.network.ProgressRequestBody(file.asRequestBody(mimeType.toMediaType()),onProgress))
            }.build()
            client.newCall(Request.Builder().url(uploadUrl).post(multipart).build()).awaitResponse().use { response ->
                if(!response.isSuccessful) throw ProviderError("Runway rechazó la subida (${response.code}).")
            }
            runwayUri
        }
    }

    private fun Request.Builder.headers(key: String): Request.Builder =
        header("Authorization", "Bearer ${key.trim()}")
            .header("X-Runway-Version", API_VERSION)
            .header("Accept", "application/json")

    private suspend fun <T> execute(request: Request, parser: suspend (String) -> T): Result<T> = try {
        client.newCall(request).awaitResponse().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) Result.failure(NetworkErrorParser.parseHttpError(response.code, body))
            else Result.success(parser(body))
        }
    } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
        Result.failure(NetworkErrorParser.parse(e))
    }

    companion object {
        const val PROVIDER_ID = "runway"
        private const val BASE_URL = "https://api.dev.runwayml.com"
        private const val API_VERSION = "2024-11-06"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
