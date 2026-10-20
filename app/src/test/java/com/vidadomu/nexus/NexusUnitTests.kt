package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.core.network.NetworkErrorParser
import com.vidadomu.nexus.core.security.SecureStorage
import com.vidadomu.nexus.provider.atlas.AtlasModelCatalog
import com.vidadomu.nexus.provider.atlas.AtlasUploadResponseParser
import com.vidadomu.nexus.provider.crun.CrunModelCatalog
import com.vidadomu.nexus.provider.crun.CrunPayloadCodec
import com.vidadomu.nexus.provider.crun.CrunCatalogSnapshot
import com.vidadomu.nexus.provider.runway.RunwayPayloadCodec
import com.vidadomu.nexus.provider.runway.RunwayModelCatalog
import com.vidadomu.nexus.ui.models.canonicalRouteKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NexusUnitTests {
    @Test fun bundledCrunSnapshotIsCompleteAndDecodable() {
        val asset=listOf(
            File("src/main/assets/crun_models_snapshot.json"),
            File("app/src/main/assets/crun_models_snapshot.json")
        ).firstOrNull(File::isFile) ?: error("No se encontró el snapshot Crun")
        val decoded=Json.parseToJsonElement(asset.readText()).jsonArray.mapNotNull(CrunCatalogSnapshot::decode)
        assertEquals(180,decoded.size)
        assertEquals(3,decoded.count { Modality.AUDIO in it.modalities })
        assertTrue(decoded.any { it.providerModelId=="wan/3-0-prime-i2v" })
        assertTrue(decoded.any { it.providerModelId=="wan/3-0-prime-t2v" })
        assertTrue(decoded.any { it.providerModelId=="wan/3-0-prime-r2v" })
        assertEquals(.1071,decoded.first { it.providerModelId=="wan/3-0-prime-i2v" }.basePriceUsd!!,0.00001)
        assertEquals(.0714,decoded.first { it.providerModelId=="minimax/h3-i2v" }.basePriceUsd!!,0.00001)
        assertNotNull(decoded.first { it.providerModelId=="minimax/hailuo-2-3" }.basePriceUsd)
        assertTrue(decoded.none { it.parameters.any { parameter -> parameter.key.isBlank() || parameter.displayName.isBlank() || parameter.displayName=="–" } })
    }
    @Test fun actualWanPrimeI2vCatalogEntriesShareOneRoute() {
        val asset=listOf(File("src/main/assets/crun_models_snapshot.json"),File("app/src/main/assets/crun_models_snapshot.json")).first(File::isFile)
        val crun=Json.parseToJsonElement(asset.readText()).jsonArray.mapNotNull(CrunCatalogSnapshot::decode)
            .first { it.providerModelId=="wan/3-0-prime-i2v" }
        val atlas=AtlasModelCatalog.curatedModels.first { it.id=="alibaba/wan-3.0-prime/image-to-video" }
        val runway=RunwayModelCatalog.models.first { it.id=="runway/wan3_prime/i2v" }
        assertEquals(canonicalRouteKey(atlas),canonicalRouteKey(crun))
        assertEquals(canonicalRouteKey(atlas),canonicalRouteKey(runway))
    }
    @Test fun runwayCatalogMatchesCurrentOfficialMediaInventory() {
        val models=RunwayModelCatalog.models
        assertEquals(62,models.size)
        assertTrue(models.any { it.id=="runway/wan3/i2v" })
        assertTrue(models.any { it.id=="runway/wan3_prime/i2v" })
        assertTrue(models.any { it.id=="runway/seedance2_5/v2v" })
        assertTrue(models.any { it.id=="runway/h3_max/t2v" })
        assertTrue(models.any { it.id=="runway/gemini_image3.1_flash/i2i" })
        assertTrue(models.any { it.id=="runway/magnific_video_upscaler_creative" })
        models.filter { it.supportedResolutions.isNotEmpty() }.forEach { model ->
            assertEquals("${model.displayName} debe iniciar en su calidad máxima",
                model.supportedResolutions.last(),model.parameters.first { it.key=="resolution" }.defaultValue)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testCrunPriorityCatalogHasExecutableQualityRoutes() {
        val models = CrunModelCatalog.models
        assertTrue(models.any { it.id == "crun/kling-v3-std" && it.parameters.any { p -> p.key == "mode" && p.defaultValue == "std" } })
        assertTrue(models.any { it.id == "crun/kling-v3-pro" && it.parameters.any { p -> p.key == "mode" && p.defaultValue == "pro" } })
        assertTrue(models.any { it.id == "crun/seedance-2.5" })
        assertTrue(models.any { it.id == "crun/wan-3.0" })
        assertTrue(models.any { it.id == "crun/minimax-h3" })
        assertTrue(models.none { it.displayName.isBlank() || it.displayName.trim() == "–" || it.parameters.any { p -> p.key.isBlank() || p.displayName.isBlank() || p.displayName.trim() == "–" } })
    }

    @Test
    fun testCrunTaskAndUploadResponsesAreParsedWithoutNetwork() {
        assertEquals("task_123", CrunPayloadCodec.parseCreatedTaskId("""{"code":200,"data":{"task_id":"task_123"}}"""))
        val upload = CrunPayloadCodec.parseUploadTicket(
            """{"code":200,"data":{"file_url":"https://temp.example/file.mp4","presigned_url":"https://upload.example/signed"}}"""
        )
        assertEquals("https://temp.example/file.mp4", upload.fileUrl)
        assertEquals("https://upload.example/signed", upload.presignedUrl)
    }

    @Test
    fun testRunwayGen45DryPayloadPreservesImagePromptAndDuration() {
        val request = GenerationRequest(
            id = "runway_preview", providerId = "runway", modelId = "runway/gen4.5",
            modality = Modality.VIDEO, prompt = "Travelling lento",
            parameters = mapOf("ratio" to "1280:720", "duration" to "7"),
            mediaInputs = listOf(MediaInput(Modality.IMAGE, "content://frame", "image/png", remoteUrl = "data:image/png;base64,AAAA"))
        )
        val payload = RunwayPayloadCodec.encode(request)
        assertEquals("gen4.5", payload["model"]!!.jsonPrimitive.content)
        assertEquals("Travelling lento", payload["promptText"]!!.jsonPrimitive.content)
        assertEquals(7, payload["duration"]!!.jsonPrimitive.int)
        assertEquals("data:image/png;base64,AAAA", payload["promptImage"]!!.jsonPrimitive.content)
    }

    @Test
    fun testUploadMediaUrlExtractionAcrossAtlasSchemas() {
        val cases = linkedMapOf(
            """{"url":"https://cdn.atlas.test/a.png"}""" to "https://cdn.atlas.test/a.png",
            """{"data":{"file_url":"https://cdn.atlas.test/b.mp4"}}""" to "https://cdn.atlas.test/b.mp4",
            """{"data":[{"download_url":"https://cdn.atlas.test/c.wav"}]}""" to "https://cdn.atlas.test/c.wav",
            """{"result":{"assets":[{"media_url":"https://cdn.atlas.test/d.webp"}]}}""" to "https://cdn.atlas.test/d.webp",
            """"https://cdn.atlas.test/e.jpg"""" to "https://cdn.atlas.test/e.jpg"
        )

        cases.forEach { (payload, expected) ->
            assertEquals(expected, AtlasUploadResponseParser.extractUrl(json.parseToJsonElement(payload)))
        }
        assertNull(
            AtlasUploadResponseParser.extractUrl(
                json.parseToJsonElement("""{"status":"success","data":{"id":"upload_1"}}""")
            )
        )
    }

    @Test
    fun testSecureStorageKeyMasking() {
        assertEquals("••••", SecureStorage.maskApiKey("abcd"))
        assertEquals("••••••••••••efgh", SecureStorage.maskApiKey("atlas_key_1234567890efgh"))
        assertEquals("Not configured", SecureStorage.maskApiKey(""))
        assertEquals("Not configured", SecureStorage.maskApiKey("   "))
    }

    @Test
    fun testSensitiveLogRedaction() {
        val rawLog = "POST /api/v1/model/generateImage with Authorization: Bearer atlas_secret_999888 and api_key=my_super_secret_key"
        val sanitized = SecureStorage.sanitizeForLogging(rawLog)

        assertFalse("Log must not contain raw bearer token", sanitized.contains("atlas_secret_999888"))
        assertFalse("Log must not contain raw api key", sanitized.contains("my_super_secret_key"))
        assertTrue("Log should contain [REDACTED]", sanitized.contains("[REDACTED]"))
    }

    @Test
    fun testAtlasModelCatalogStructure() {
        val models = AtlasModelCatalog.curatedModels
        assertTrue("Catalog should contain curated models", models.isNotEmpty())

        val seedream = models.find { it.id == "bytedance/seedream-v4" }
        assertNotNull("Seedream model must exist", seedream)
        assertTrue("Seedream must support IMAGE modality", seedream!!.modalities.contains(Modality.IMAGE))
        assertTrue("Seedream must have text-to-image capability", seedream.capabilities.contains(ModelCapability.TEXT_TO_IMAGE))

        val veo = models.find { it.id == "google/veo3.1-lite/text-to-video" }
        assertNotNull("Veo video model must exist", veo)
        assertTrue("Veo must support VIDEO modality", veo!!.modalities.contains(Modality.VIDEO))

        // Check parameters have valid default values
        for (param in veo.parameters) {
            assertNotNull("Parameter key must not be null", param.key)
            assertNotNull("Parameter defaultValue must not be null", param.defaultValue)
        }
    }

    @Test
    fun testGenerationRequestSerialization() {
        val request = GenerationRequest(
            id = "test_job_1",
            providerId = "atlas_cloud",
            modelId = "bytedance/seedream-v4",
            modality = Modality.IMAGE,
            prompt = "Futuristic cybernetic city at night",
            parameters = mapOf("aspect_ratio" to "16:9", "seed" to "42")
        )

        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<GenerationRequest>(serialized)

        assertEquals(request.id, deserialized.id)
        assertEquals(request.prompt, deserialized.prompt)
        assertEquals("16:9", deserialized.parameters["aspect_ratio"])
    }

    @Test
    fun testNetworkErrorParsing() {
        // 401 Unauthorized
        val err401 = NetworkErrorParser.parseHttpError(401, "{\"error\": \"Invalid API token\"}")
        assertTrue(err401.humanReadableMessage.contains("ha rechazado la clave API"))

        // 429 Rate Limit
        val err429 = NetworkErrorParser.parseHttpError(429, "Rate limit exceeded")
        assertTrue(err429.humanReadableMessage.contains("límite de solicitudes"))

        // Timeout
        val timeoutErr = NetworkErrorParser.parse(SocketTimeoutException("Read timed out"))
        assertTrue(timeoutErr.humanReadableMessage.contains("ha tardado demasiado"))

        // No connection
        val noNetErr = NetworkErrorParser.parse(UnknownHostException("api.atlascloud.ai"))
        assertTrue(noNetErr.humanReadableMessage.contains("No se puede contactar"))
    }

    @Test
    fun testAtlasPredictionResponseParsing() {
        val mockJson = """
            {
                "code": 200,
                "data": {
                    "id": "pred_xyz987",
                    "status": "completed",
                    "outputs": [
                        "https://storage.atlascloud.ai/outputs/img_123.jpg"
                    ]
                }
            }
        """.trimIndent()

        val jsonElement = json.parseToJsonElement(mockJson).jsonObject
        val dataObj = jsonElement["data"]!!.jsonObject
        val id = dataObj["id"]!!.jsonPrimitive.content
        val statusStr = dataObj["status"]!!.jsonPrimitive.content
        val outputs = dataObj["outputs"]!!.jsonArray.map { it.jsonPrimitive.content }

        assertEquals("pred_xyz987", id)
        assertEquals("completed", statusStr)
        assertEquals(1, outputs.size)
        assertEquals("https://storage.atlascloud.ai/outputs/img_123.jpg", outputs[0])
    }

    @Test
    fun testAtlasModelMapperRealMediaParsing() {
        val mockApiResponse = """
            {
                "code": 200,
                "data": [
                    {
                        "uuid": "ms-img-001",
                        "model": "openai/gpt-image-2.5-sunburst/text-to-image",
                        "type": "Image",
                        "displayName": "GPT Image 2.5 Sunburst Text-to-Image",
                        "profile": "Advanced photorealism image generator",
                        "organization": "OPENAI",
                        "categories": ["TEXT-TO-IMAGE"],
                        "tags": ["HOT", "NEW"],
                        "price": {
                            "actual": {
                                "base_price": "0.003",
                                "unit": "image"
                            },
                            "discount": "80"
                        }
                    },
                    {
                        "uuid": "ms-vid-002",
                        "model": "bytedance/seedance-v2/image-to-video",
                        "type": "Video",
                        "displayName": "Seedance 2.0 Image-to-Video",
                        "profile": "Cinematic motion generation from photo",
                        "organization": "BYTEDANCE",
                        "categories": ["IMAGE-TO-VIDEO"],
                        "tags": ["PRO"],
                        "price": {
                            "actual": {
                                "base_price": "0.05",
                                "unit": "second"
                            }
                        }
                    },
                    {
                        "uuid": "ms-aud-003",
                        "model": "suno/chirp-v5",
                        "type": "Audio",
                        "displayName": "Suno Chirp v5",
                        "profile": "Full song music generator",
                        "organization": "SUNO",
                        "categories": ["TEXT-TO-AUDIO"],
                        "tags": ["MUSIC"]
                    }
                ]
            }
        """.trimIndent()

        val models = com.vidadomu.nexus.provider.atlas.AtlasModelMapper.parseModelsResponse(mockApiResponse)
        assertEquals(3, models.size)

        // Test Image Model
        val imgModel = models[0]
        assertEquals("openai/gpt-image-2.5-sunburst/text-to-image", imgModel.id)
        assertEquals("GPT Image 2.5 Sunburst", imgModel.displayName)
        assertEquals("OpenAI", imgModel.organization)
        assertEquals(listOf(Modality.IMAGE), imgModel.modalities)
        assertTrue(imgModel.capabilities.contains(ModelCapability.TEXT_TO_IMAGE))
        assertEquals("NUEVO", imgModel.badge)
        val withoutNewTag = mockApiResponse.replace("\"HOT\", \"NEW\"", "\"HOT\"")
        val withoutNew = com.vidadomu.nexus.provider.atlas.AtlasModelMapper.parseModelsResponse(withoutNewTag).first()
        assertEquals("-80%", withoutNew.badge)
        assertNotEquals("HOT", withoutNew.badge)
        assertEquals(0.003, imgModel.basePriceUsd!!, 0.0001)
        assertEquals("img", imgModel.priceUnit)
        assertFalse(imgModel.supportsImageInput)

        // Test Video Model
        val vidModel = models[1]
        assertEquals("bytedance/seedance-v2/image-to-video", vidModel.id)
        assertEquals("Seedance 2.0", vidModel.displayName)
        assertEquals("ByteDance", vidModel.organization)
        assertEquals(listOf(Modality.VIDEO), vidModel.modalities)
        assertTrue(vidModel.capabilities.contains(ModelCapability.IMAGE_TO_VIDEO))
        assertEquals("PRO", vidModel.badge)
        assertTrue(vidModel.supportsImageInput)
        assertEquals("seg", vidModel.priceUnit)

        // Test Audio Model
        val audModel = models[2]
        assertEquals("suno/chirp-v5", audModel.id)
        assertEquals("Suno", audModel.organization)
        assertEquals(listOf(Modality.AUDIO), audModel.modalities)
        assertTrue(audModel.capabilities.contains(ModelCapability.TEXT_TO_AUDIO))
    }

    @Test
    fun testOrganizationNormalization() {
        val mapper = com.vidadomu.nexus.provider.atlas.AtlasModelMapper
        assertEquals("OpenAI", mapper.normalizeOrganizationName("OPENAI"))
        assertEquals("ByteDance", mapper.normalizeOrganizationName("BYTEDANCE"))
        assertEquals("ByteDance", mapper.normalizeOrganizationName("BYTEPLUS"))
        assertEquals("Kling (Kuaishou)", mapper.normalizeOrganizationName("KUAISHOU"))
        assertEquals("Kling (Kuaishou)", mapper.normalizeOrganizationName("KWAIVGI"))
        assertEquals("Black Forest Labs", mapper.normalizeOrganizationName("BLACK-FOREST-LABS"))
        assertEquals("MiniMax", mapper.normalizeOrganizationName("MINIMAX"))
        assertEquals("Google", mapper.normalizeOrganizationName("GOOGLE"))
        assertEquals("DeepSeek", mapper.normalizeOrganizationName("DEEPSEEK"))
        assertEquals("Suno", mapper.normalizeOrganizationName("SUNO"))
        assertEquals("Vidu", mapper.normalizeOrganizationName("VIDU"))
    }

    @Test
    fun testCatalogInstantSearchAndFilter() {
        val mockApiResponse = """
            {
                "code": 200,
                "data": [
                    { "model": "google/veo-3/text-to-video", "type": "Video", "displayName": "Veo 3", "organization": "GOOGLE", "categories": ["TEXT-TO-VIDEO"] },
                    { "model": "kwaivgi/kling-v3/image-to-video", "type": "Video", "displayName": "Kling 3", "organization": "KWAIVGI", "categories": ["IMAGE-TO-VIDEO"] },
                    { "model": "black-forest-labs/flux-schnell", "type": "Image", "displayName": "Flux Schnell", "organization": "BLACK-FOREST-LABS", "categories": ["TEXT-TO-IMAGE"] }
                ]
            }
        """.trimIndent()

        val models = com.vidadomu.nexus.provider.atlas.AtlasModelMapper.parseModelsResponse(mockApiResponse)

        // Search "kling"
        val searchKling = models.filter { it.displayName.contains("kling", ignoreCase = true) || it.id.contains("kling", ignoreCase = true) }
        assertEquals(1, searchKling.size)
        assertEquals("kwaivgi/kling-v3/image-to-video", searchKling[0].id)

        // Search "video"
        val videoModels = models.filter { it.modalities.contains(Modality.VIDEO) }
        assertEquals(2, videoModels.size)

        // Filter Image Input
        val imageInputModels = models.filter { it.supportsImageInput }
        assertEquals(1, imageInputModels.size)
        assertEquals("kwaivgi/kling-v3/image-to-video", imageInputModels[0].id)

        // Group by provider
        val grouped = models.groupBy { it.organization }
        assertEquals(3, grouped.size)
        assertTrue(grouped.containsKey("Google"))
        assertTrue(grouped.containsKey("Kling (Kuaishou)"))
        assertTrue(grouped.containsKey("Black Forest Labs"))
    }

    @Test
    fun testModelInputCapabilitiesAndFieldDerivation() {
        val mockApiResponse = """
            {
                "code": 200,
                "data": [
                    {
                        "model": "minimax/h3-max/image-to-video",
                        "type": "Video",
                        "displayName": "MiniMax H3 Max Image-to-Video",
                        "organization": "MINIMAX",
                        "categories": ["IMAGE-TO-VIDEO"],
                        "tags": ["IMAGE-TO-VIDEO", "NEW"]
                    },
                    {
                        "model": "alibaba/wan-3.0-prime/image-to-video",
                        "type": "Video",
                        "displayName": "Wan-3.0-Prime Image-to-video",
                        "organization": "QWEN",
                        "categories": ["IMAGE-TO-VIDEO"],
                        "tags": ["IMAGE-TO-VIDEO", "HOT"]
                    },
                    {
                        "model": "kwaivgi/kling-v2/image-to-video",
                        "type": "Video",
                        "displayName": "Kling v2 Image-to-Video",
                        "organization": "KWAIVGI",
                        "categories": ["IMAGE-TO-VIDEO"]
                    },
                    {
                        "model": "google/gemini-omni-1.1-flash/reference-to-video",
                        "type": "Video",
                        "displayName": "Gemini Omni Reference-to-Video",
                        "organization": "GOOGLE",
                        "categories": ["VIDEO-TO-VIDEO"],
                        "tags": ["REFERENCE-TO-VIDEO"]
                    },
                    {
                        "model": "google/gemini-omni-1.1-flash/video-extend",
                        "type": "Video",
                        "displayName": "Gemini Omni Video Extend",
                        "organization": "GOOGLE",
                        "categories": ["VIDEO-TO-VIDEO"],
                        "tags": ["VIDEO-EXTEND"]
                    },
                    {
                        "model": "openai/gpt-image-2.5-sunburst/text-to-image",
                        "type": "Image",
                        "displayName": "GPT Image 2.5",
                        "organization": "OPENAI",
                        "categories": ["TEXT-TO-IMAGE"]
                    }
                ]
            }
        """.trimIndent()

        val models = com.vidadomu.nexus.provider.atlas.AtlasModelMapper.parseModelsResponse(mockApiResponse)
        assertEquals(6, models.size)

        // 1. MiniMax H3 Max: Dual Frame with "image" + "end_image" according to official OpenAPI schema
        val minimax = models.find { it.id == "minimax/h3-max/image-to-video" }!!
        assertEquals(ModelInputMode.DUAL_FRAME, minimax.inputMode)
        assertTrue(minimax.supportsImageInput)
        assertTrue(minimax.supportsFirstFrame)
        assertTrue(minimax.supportsLastFrame)
        assertEquals(2, minimax.maxImages)
        assertEquals("image", minimax.firstFrameField)
        assertEquals("end_image", minimax.lastFrameField)
        assertFalse("I2V models should have adaptive ratio from image", minimax.supportsNativeAspectRatio)

        // 2. Wan 3.0: Dual Frame with "image" + "last_image"
        val wan = models.find { it.id == "alibaba/wan-3.0-prime/image-to-video" }!!
        assertEquals(ModelInputMode.DUAL_FRAME, wan.inputMode)
        assertTrue(wan.supportsImageInput)
        assertTrue(wan.supportsFirstFrame)
        assertTrue(wan.supportsLastFrame)
        assertEquals(2, wan.maxImages)
        assertEquals("image", wan.firstFrameField)
        assertEquals("last_image", wan.lastFrameField)
        assertFalse(wan.supportsNativeAspectRatio)

        // 3. Kling: Single Image with "image"
        val kling = models.find { it.id == "kwaivgi/kling-v2/image-to-video" }!!
        assertEquals(ModelInputMode.SINGLE_IMAGE, kling.inputMode)
        assertTrue(kling.supportsImageInput)
        assertEquals(1, kling.maxImages)
        assertEquals("image", kling.firstFrameField)
        assertNull(kling.lastFrameField)

        // 4. Reference to Video: Multi-modal with reference_images & reference_videos
        val refVideo = models.find { it.id == "google/gemini-omni-1.1-flash/reference-to-video" }!!
        assertEquals(ModelInputMode.MULTI_MODAL, refVideo.inputMode)
        assertTrue(refVideo.supportsImageInput)
        assertTrue(refVideo.supportsReferenceImages)
        assertEquals("reference_images", refVideo.imagesArrayField)
        assertEquals("reference_videos", refVideo.videosArrayField)
        assertEquals(10, refVideo.maxImages)
        assertEquals(3, refVideo.maxVideos)

        // 5. Video extend: Video input with video
        val videoExtend = models.find { it.id == "google/gemini-omni-1.1-flash/video-extend" }!!
        assertEquals(ModelInputMode.VIDEO_INPUT, videoExtend.inputMode)
        assertTrue(videoExtend.supportsVideoInput)
        assertEquals("video", videoExtend.videoField)
        assertEquals(1, videoExtend.maxVideos)

        // 6. Text to Image: Strictly TEXT_ONLY, 0 media attachments
        val t2i = models.find { it.id == "openai/gpt-image-2.5-sunburst/text-to-image" }!!
        assertEquals(ModelInputMode.TEXT_ONLY, t2i.inputMode)
        assertEquals(0, t2i.maxImages)
        assertEquals(0, t2i.maxVideos)
        assertFalse(t2i.supportsImageInput)
        assertNull(t2i.inputImageField)
        assertTrue(t2i.supportsNativeAspectRatio)
    }

    @Test
    fun testMultiImageEditModelsCapabilities() {
        val mockApiResponse = """
            {
                "code": 200,
                "data": [
                    {
                        "model": "openai/gpt-image-2.5-sunburst/edit",
                        "type": "Image",
                        "displayName": "GPT Image 2.5 Sunburst Edit",
                        "organization": "OPENAI",
                        "categories": ["IMAGE-TOOLS"]
                    },
                    {
                        "model": "bytedance/seedream-v4.7/edit",
                        "type": "Image",
                        "displayName": "Seedream 4.7 Edit",
                        "organization": "BYTEDANCE",
                        "categories": ["IMAGE-TOOLS"]
                    }
                ]
            }
        """.trimIndent()

        val models = com.vidadomu.nexus.provider.atlas.AtlasModelMapper.parseModelsResponse(mockApiResponse)
        assertEquals(2, models.size)

        val gptEdit = models.find { it.id == "openai/gpt-image-2.5-sunburst/edit" }!!
        assertEquals(ModelInputMode.MULTI_IMAGE, gptEdit.inputMode)
        assertEquals(16, gptEdit.maxImages)
        assertEquals("images", gptEdit.imagesArrayField)
        assertTrue(gptEdit.isMediaRequired)

        val seedreamEdit = models.find { it.id == "bytedance/seedream-v4.7/edit" }!!
        assertEquals(ModelInputMode.MULTI_IMAGE, seedreamEdit.inputMode)
        assertEquals(10, seedreamEdit.maxImages)
        assertEquals("images", seedreamEdit.imagesArrayField)
        assertTrue(seedreamEdit.isMediaRequired)
    }

    @Test
    fun testDiscountAndOfferFiltering() {
        val mockApiResponse = """
            {
                "code": 200,
                "data": [
                    {
                        "model": "model-discounted-80",
                        "type": "Image",
                        "displayName": "Discounted 80%",
                        "organization": "TEST",
                        "price": { "discount": "80", "actual": { "base_price": "0.01" } }
                    },
                    {
                        "model": "model-free-100",
                        "type": "Image",
                        "displayName": "Free 100%",
                        "organization": "TEST",
                        "price": { "discount": "100", "actual": { "base_price": "0.0" } }
                    },
                    {
                        "model": "model-full-price",
                        "type": "Image",
                        "displayName": "Full Price",
                        "organization": "TEST",
                        "price": { "discount": "0", "actual": { "base_price": "0.05" } }
                    },
                    {
                        "model": "model-no-discount",
                        "type": "Image",
                        "displayName": "No Discount",
                        "organization": "TEST",
                        "price": { "actual": { "base_price": "0.05" } }
                    }
                ]
            }
        """.trimIndent()

        val models = com.vidadomu.nexus.provider.atlas.AtlasModelMapper.parseModelsResponse(mockApiResponse)
        val discounted = models.filter {
            !it.discount.isNullOrBlank() && it.discount != "0" && it.discount != "100"
        }

        assertEquals(1, discounted.size)
        assertEquals("model-discounted-80", discounted[0].id)
    }
}
