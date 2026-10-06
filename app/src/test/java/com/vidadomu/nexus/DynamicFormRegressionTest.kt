package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.*
import com.vidadomu.nexus.ui.create.CreateUiState
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DynamicFormRegressionTest {
    private val base = AtlasModelCatalog.curatedModels.first().copy(id = "kwaivgi/kling-v3.0-std/motion-control", modalities = listOf(Modality.VIDEO))
    private fun motion() = NativeModelSchema.decode(base, javaClass.getResource("/native/kling-motion.json")!!.readText()).model
    private fun schema(properties: String, required: String = "[]") = NativeModelSchema.decode(base,
        """{"components":{"schemas":{"Input":{"properties":$properties,"required":$required}}}}""")
    private fun attached(slot: MediaSlotType) = AttachedMedia(uriString="file:///local/fixture", mimeType=if(slot == MediaSlotType.VIDEO_INPUT) "video/mp4" else "image/png", slotType=slot)
    @Test fun motionRequiresBothMediaAndOrientationButNotPrompt() {
        val model=motion()
        assertEquals(ModelInputMode.MULTI_MODAL, model.inputMode)
        assertEquals(setOf("model","image","video","character_orientation"),model.nativeRequiredFields.toSet())
        assertEquals(listOf("character_orientation","keep_original_sound"),model.parameters.map { it.key })
        assertFalse(model.parameters.first { it.key=="character_orientation" }.isAdvanced)
        val state=CreateUiState(selectedModel=model, parameters=mapOf("keep_original_sound" to "false"))
        assertEquals(setOf("Imagen de entrada","Vídeo de entrada","Orientación del personaje"),state.missingRequiredFields.toSet())
        val complete=state.copy(parameters=state.parameters+("character_orientation" to "video"), attachedMedia=listOf(attached(MediaSlotType.PRIMARY_IMAGE),attached(MediaSlotType.VIDEO_INPUT)))
        assertTrue(complete.missingRequiredFields.isEmpty())
        assertEquals(listOf("Imagen de entrada"),complete.copy(attachedMedia=listOf(attached(MediaSlotType.VIDEO_INPUT))).missingRequiredFields)
    }
    @Test fun actualProductionSerializerPreservesVideoPromptOrientationAndBooleanTypes() {
        val model=motion()
        for(sound in listOf("true","false")) {
            val request=NativeGenerationRequest.build(model,Modality.VIDEO,"Move \"gently\"\nnow",mapOf("character_orientation" to "video","keep_original_sound" to sound,"stale_field" to "ignored"),
                listOf(MediaInput(Modality.IMAGE,"file:///image","image/png",remoteUrl="https://example.invalid/image.png",slotType=MediaSlotType.PRIMARY_IMAGE),
                    MediaInput(Modality.VIDEO,"file:///video","video/mp4",remoteUrl="https://example.invalid/input.mp4",slotType=MediaSlotType.VIDEO_INPUT)),"offline")
            val payload=AtlasGenerationPayload.build(request)
            assertEquals(setOf("model","prompt","image","video","character_orientation","keep_original_sound"),payload.keys)
            assertEquals(base.id,payload["model"]!!.jsonPrimitive.content)
            assertEquals(request.prompt,payload["prompt"]!!.jsonPrimitive.content)
            assertEquals("https://example.invalid/input.mp4",payload["video"]!!.jsonPrimitive.content)
            assertEquals("video",payload["character_orientation"]!!.jsonPrimitive.content)
            assertEquals(sound.toBoolean(),payload["keep_original_sound"]!!.jsonPrimitive.boolean)
            assertFalse(payload["keep_original_sound"]!!.jsonPrimitive.isString)
        }
    }
    @Test fun malformedFieldsAndOptionsAreNotRendered() {
        val config=schema("""{"":{"type":"string"},"–":{"type":"string"},"null":{"type":"string"},"undefined":{"type":"string"},"bad":null,"empty":{"enum":[null,"","—"],"type":"string"},"choice":{"type":"string","enum":[null,"","–","undefined","ok","ok"],"default":"null"},"count":{"type":"integer","default":0},"flag":{"type":"boolean","default":false}}""")
        assertEquals(setOf("choice","count","flag"),config.model.parameters.map { it.key }.toSet())
        assertEquals(listOf("ok"),config.model.parameters.first { it.key=="choice" }.options)
        assertEquals("",config.model.parameters.first { it.key=="choice" }.defaultValue)
        assertEquals("0",config.model.parameters.first { it.key=="count" }.defaultValue)
        assertEquals("false",config.model.parameters.first { it.key=="flag" }.defaultValue)
    }
    @Test fun referenceVideosUseArrayAndKeepImages() {
        val model=schema("""{"images":{"type":"array","items":{"type":"string"}},"reference_videos":{"type":"array","items":{"type":"string"},"maxItems":2}}""","""["reference_videos"]""").model
        assertEquals(ModelInputMode.MULTI_MODAL,model.inputMode)
        val request=NativeGenerationRequest.build(model,Modality.VIDEO,"",emptyMap(),listOf(
            MediaInput(Modality.VIDEO,"a","video/mp4",remoteUrl="https://example.invalid/a.mp4",slotType=MediaSlotType.VIDEO_REFERENCE),
            MediaInput(Modality.VIDEO,"b","video/mp4",remoteUrl="https://example.invalid/b.mp4",slotType=MediaSlotType.VIDEO_REFERENCE)),"offline")
        val payload=AtlasGenerationPayload.build(request)
        assertEquals(2,payload["reference_videos"]!!.jsonArray.size)
        assertFalse(payload.containsKey("video"))
    }
    @Test fun optionalPromptAndOptionalImageDoNotBlock() {
        val model=schema("""{"image":{"type":"string"},"prompt":{"type":"string"}}""").model
        assertTrue(CreateUiState(selectedModel=model).missingRequiredFields.isEmpty())
    }
    @Test fun requiredFalseAndZeroArePresent() {
        val model=schema("""{"count":{"type":"integer"},"flag":{"type":"boolean"}}""","""["count","flag"]""").model
        assertTrue(CreateUiState(selectedModel=model,parameters=mapOf("count" to "0","flag" to "false")).missingRequiredFields.isEmpty())
    }
}
