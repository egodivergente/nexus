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
            assertEquals(12, schema.media.maxImages)
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
    @Test fun tooManyReferencesAreRejectedRatherThanTruncated() {
        assertThrows(IllegalArgumentException::class.java) { FlowReferences.ids(List(13) { input() }) }
        assertThrows(IllegalArgumentException::class.java) {
            FlowReferences.savedIds(mapOf(FlowReferences.PARAMETER to List(13) { id }.joinToString(",")))
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
    }
}
