package com.vidadomu.nexus

import com.vidadomu.nexus.assistant.assistantMediaInput
import com.vidadomu.nexus.agent.core.spend.MediaRef
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.create.CreateUiState
import com.vidadomu.nexus.ui.create.creationReadiness
import org.junit.Assert.*
import org.junit.Test

class AssistantReferenceInputTest {
    private val edit = NexusModel("edit", "Edit", "fixture", organization = "fixture", modalities = listOf(Modality.IMAGE), capabilities = emptyList(), parameters = emptyList(),
        imagesArrayField = "images", maxImages = 4, nativeRequiredFields = listOf("prompt", "images"))
    private val reference = MediaRef("reference", "file:///reference.png", "image/png", fieldKey = "images")

    @Test fun nativeArrayReferenceSatisfiesTheSameReadinessAsTheCanvas() {
        val input = assistantMediaInput(edit, reference)
        assertNull(input.fieldKey)
        assertEquals(MediaSlotType.REFERENCE_IMAGE, input.slotType)
        val state = CreateUiState(selectedModel = edit, prompt = "Edit fixture", schemaReady = true, attachedMedia = listOf(input))
        assertTrue(creationReadiness(state).canSubmit)
        assertFalse(creationReadiness(state.copy(attachedMedia = emptyList())).canSubmit)
    }

    @Test fun firstFramePlacedInAnArrayUsesTheReferenceSlot() {
        assertEquals(MediaSlotType.REFERENCE_IMAGE, assistantMediaInput(edit, reference.copy(role = "first_frame")).slotType)
    }

    @Test fun capturedSeedreamEditingSchemaAcceptsAgentReferences() {
        val original = com.vidadomu.nexus.provider.atlas.AtlasModelCatalog.curatedModels.first()
        val schema = javaClass.getResource("/native/bytedance_seedream-v5.0-pro_edit.json")!!.readText()
        val model = com.vidadomu.nexus.provider.atlas.NativeModelSchema.decode(original, schema).model.copy(providerId = "fixture")
        val field = requireNotNull(model.imagesArrayField)
        val attached = assistantMediaInput(model, reference.copy(fieldKey = field))
        val state = CreateUiState(selectedModel = model, prompt = "Fixture edit", schemaReady = true,
            parameters = FormContract.restoredValues(model, emptyMap()), attachedMedia = listOf(attached))
        assertTrue(state.missingRequiredFields.toString(), state.missingRequiredFields.isEmpty())
        assertTrue(creationReadiness(state).message, creationReadiness(state).canSubmit)
        assertFalse(creationReadiness(state.copy(attachedMedia = emptyList())).canSubmit)
    }

    @Test fun singleFirstAndLastFramesKeepTheirSeparateSlots() {
        val model = edit.copy(imagesArrayField = null, firstFrameField = "image", lastFrameField = "end_image", nativeRequiredFields = listOf("image", "end_image"))
        val inputs = listOf(assistantMediaInput(model, reference.copy(role = "first_frame", fieldKey = "image")),
            assistantMediaInput(model, reference.copy(role = "last_frame", fieldKey = "end_image")))
        assertEquals(listOf(MediaSlotType.PRIMARY_IMAGE, MediaSlotType.LAST_FRAME), inputs.map { it.slotType })
        assertTrue(CreateUiState(selectedModel = model, attachedMedia = inputs).missingRequiredFields.isEmpty())
    }

    @Test fun videoArrayAndSingleVideoKeepTheCorrectSlotsAndMime() {
        val video = reference.copy(role = "video", mimeType = null, fieldKey = "videos")
        assertEquals(MediaSlotType.VIDEO_REFERENCE, assistantMediaInput(edit.copy(videosArrayField = "videos"), video).slotType)
        val single = assistantMediaInput(edit.copy(videoField = "video"), video.copy(fieldKey = "video"))
        assertEquals(MediaSlotType.VIDEO_INPUT, single.slotType)
        assertEquals("video/*", single.mimeType)
    }

    @Test fun contractInputRetainsItsExplicitField() {
        val contract = edit.copy(contractMedia = listOf(ContractMediaField("images", "Referencias", "image/*", required = true, maximum = 4, encoding = "array")))
        assertEquals("images", assistantMediaInput(contract, reference).fieldKey)
        assertTrue(CreateUiState(selectedModel = contract, prompt = "Fixture", attachedMedia = listOf(assistantMediaInput(contract, reference))).missingRequiredFields.isEmpty())
    }
}
