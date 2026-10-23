package com.vidadomu.nexus.provider.flow

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.assistant.NexusModelRegistry
import com.vidadomu.nexus.agent.core.registry.ModelRef
import org.junit.Test
import org.junit.Assert.*

class FlowReferencesTest {
    private val id = "ref_" + "a".repeat(64)
    private fun input(remote: String? = id) = MediaInput(Modality.IMAGE, "file:///reference.png",
        "image/png", remoteUrl = remote, slotType = MediaSlotType.REFERENCE_IMAGE)

    @Test fun referenceModelAndAgentSchemaPermitOptionalIngredients() {
        for (modality in listOf(Modality.IMAGE, Modality.VIDEO)) {
            val m = FlowReferences.model(modality)
            assertTrue(m.supportsImageInput)
            assertTrue(m.supportsReferenceImages)
            assertFalse(m.isMediaRequired)
            val schema = NexusModelRegistry.toSchema(ModelRef(m.providerId, m.id), m, emptyList(), "test")
            assertEquals("reference_ids", schema.media.imagesArrayField)
            assertEquals(Int.MAX_VALUE, schema.media.maxImages)
            assertFalse(schema.media.mediaRequired)
        }
    }
    @Test fun uploadedReferencesSurviveQueueSerializationInOrder() {
        val next = "ref_" + "b".repeat(64)
        val ids = FlowReferences.ids(listOf(input(), input(next)))
        assertEquals(listOf(id, next), FlowReferences.savedIds(mapOf(FlowReferences.PARAMETER to ids.joinToString(","))))
        assertEquals(emptyList<String>(), FlowReferences.savedIds(emptyMap()))
    }
    @Test fun unuploadedMediaExternalUrlsAndUnsupportedRolesAreRejected() {
        for (item in listOf(input(null), input("https://other.test/image.png"),
            input().copy(slotType = MediaSlotType.LAST_FRAME), input().copy(type = Modality.VIDEO),
            input().copy(mimeType = "image/gif"))) {
            assertThrows(IllegalArgumentException::class.java) { FlowReferences.ids(listOf(item)) }
        }
    }
    @Test fun unknownGoogleReferenceMaximumDoesNotInventATwelveImageCeiling() {
        val ids = (0..12).map { "ref_" + it.toString(16).padStart(64, '0') }
        assertEquals(ids,FlowReferences.ids(ids.map { input(it) }))
        assertEquals(ids,FlowReferences.savedIds(mapOf(FlowReferences.PARAMETER to ids.joinToString(","))))
    }

    @Test fun sameImagePickedThroughDifferentUrisIsSentOnceInFirstOccurrenceOrder() {
        val next = "ref_" + "b".repeat(64)
        val repeated = input().copy(uriString = "content://picker/another-copy", fileName = "copy.png")
        val references = FlowReferences.inputs(listOf(input(), input(next), repeated), "Referencias")
        assertEquals(listOf(id, next), references.references)
        assertEquals(references.references, FlowReferences.savedIds(mapOf(
            FlowReferences.PARAMETER to listOf(id, next, id).joinToString(","))))
    }

    @Test fun duplicateCopiesDoNotConsumeAdditionalIngredientSlots() {
        assertEquals(listOf(id), FlowReferences.ids(List(13) { input() }))
        assertThrows(IllegalArgumentException::class.java) {
            FlowReferences.inputs(listOf(input(), input().copy(mimeType = "image/gif")))
        }
    }

    @Test fun videoContractExposesStartAndOptionalEndFrames() {
        val model = FlowReferences.model(Modality.VIDEO)
        assertEquals(listOf("initial_frame", "end_frame", "reference_ids"), model.contractMedia.map { it.key })
        assertFalse(model.contractMedia[0].required)
        assertFalse(model.contractMedia[1].required)
    }

    @Test fun startAndEndFramesKeepTheirRolesAndCannotBeReversedOrDuplicated() {
        val start = input().copy(fieldKey = "initial_frame", slotType = MediaSlotType.PRIMARY_IMAGE)
        val end = input("ref_" + "b".repeat(64)).copy(fieldKey = "end_frame", slotType = MediaSlotType.LAST_FRAME)
        assertEquals(FlowReferences.Inputs(emptyList(), id, end.remoteUrl), FlowReferences.inputs(listOf(start, end)))
        assertThrows(IllegalArgumentException::class.java) { FlowReferences.inputs(listOf(end)) }
        assertThrows(IllegalArgumentException::class.java) { FlowReferences.inputs(listOf(start, start)) }
        assertThrows(IllegalArgumentException::class.java) {
            FlowReferences.inputs(listOf(start, end.copy(remoteUrl = id)))
        }
    }

    @Test fun interchangeMovesBothFilesAndTheirRolesTogether() {
        val start = com.vidadomu.nexus.core.model.AttachedMedia(uriString = "file:///a.png", mimeType = "image/png",
            slotType = MediaSlotType.PRIMARY_IMAGE, fieldKey = "initial_frame")
        val end = com.vidadomu.nexus.core.model.AttachedMedia(uriString = "file:///b.png", mimeType = "image/png",
            slotType = MediaSlotType.LAST_FRAME, fieldKey = "end_frame")
        val (newStart, newEnd) = FlowReferences.swapFrames(start, end)
        assertEquals("file:///b.png", newStart.uriString)
        assertEquals("initial_frame", newStart.fieldKey)
        assertEquals("file:///a.png", newEnd.uriString)
        assertEquals("end_frame", newEnd.fieldKey)
    }

    @Test fun videoModesRequireMatchingImageInputs() {
        val start = input().copy(fieldKey = "initial_frame", slotType = MediaSlotType.PRIMARY_IMAGE)
        assertEquals(id, FlowReferences.inputs(listOf(start), "Fotogramas").initialFrame)
        assertThrows(IllegalArgumentException::class.java) { FlowReferences.inputs(emptyList(), "Fotogramas") }
        assertThrows(IllegalArgumentException::class.java) { FlowReferences.inputs(listOf(start), "Texto") }
        assertThrows(IllegalArgumentException::class.java) {
            FlowReferences.inputs(listOf(start, input("ref_" + "b".repeat(64))), "Fotogramas")
        }
        assertThrows(IllegalArgumentException::class.java) { FlowReferences.inputs(emptyList(), "Referencias") }
    }
}
