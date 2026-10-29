package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.create.capability.ModelCapabilityRegistry
import org.junit.Assert.*
import org.junit.Test

class ArtBoxMediaImportTest {
    private fun model(id: String = "target") = NexusModel(id, id, "fixture", organization = "Fixture",
        modalities = listOf(Modality.IMAGE), capabilities = emptyList(), parameters = emptyList())
    private fun image(id: String) = AttachedMedia(id, "content://selection/$id", "image/png", MediaSlotType.REFERENCE_IMAGE)
    private fun state(model: NexusModel) = CreateUiState(selectedModel = model, selectedModality = Modality.IMAGE,
        prompt = "Original full prompt", negativePrompt = "Original negative", parameters = mapOf("quality" to "original"), schemaReady = true)
    private fun copied(item: AttachedMedia) = item.copy(uriString = "file:///private/${item.id}.png")
    private fun pool(state: CreateUiState) = state.attachedMedia + state.retainedMedia
    private fun prepared(state: CreateUiState, incoming: List<AttachedMedia>): CreateUiState = incoming.fold(stageArtBoxMedia(state, incoming)) {
        current, item -> completeArtBoxMedia(current, item, copied(item), newlyStaged = true)
    }

    @Test fun thirteenSelectedFilesAndThePreviousPoolSurviveASingleImageDestination() {
        val target = model().copy(firstFrameField = "image")
        val old = copied(image("old")).copy(slotType = MediaSlotType.PRIMARY_IMAGE)
        val parked = copied(image("parked")).copy(mimeType = "video/mp4", slotType = MediaSlotType.VIDEO_INPUT)
        val before = state(target).copy(attachedMedia = listOf(old), retainedMedia = listOf(parked))
        val incoming = (1..13).map { image("new$it") }
        val result = finishArtBoxMediaImport(prepared(before, incoming), ModelCapabilityRegistry.key(target, Modality.IMAGE),
            true, incoming.map { it.id }, incoming.map { it.id }.toSet())
        assertEquals(listOf("new1"), result.attachedMedia.map { it.id })
        assertEquals(14, result.retainedMedia.size)
        assertEquals((incoming + listOf(old, parked)).map { it.id }.toSet(), pool(result).map { it.id }.toSet())
        assertEquals(15, pool(result).map { it.id }.distinct().size)
        assertEquals(old, result.retainedMedia.single { it.id == "old" })
        assertEquals(parked, result.retainedMedia.single { it.id == "parked" })
        assertEquals(before.prompt, result.prompt)
        assertEquals(before.negativePrompt, result.negativePrompt)
        assertEquals(before.parameters, result.parameters)
    }

    @Test fun textOnlyDestinationConservesEveryCopyAndACompatibleModelCanReactivateThem() {
        val text = model("text").copy(contractEndpoint = "/text")
        val incoming = (1..13).map { image("$it") }
        val result = finishArtBoxMediaImport(prepared(state(text), incoming), ModelCapabilityRegistry.key(text, Modality.IMAGE),
            true, incoming.map { it.id }, incoming.map { it.id }.toSet())
        assertTrue(result.attachedMedia.isEmpty())
        assertEquals(incoming.map { it.id }.toSet(), result.retainedMedia.map { it.id }.toSet())
        assertTrue(result.retainedMedia.all { it.uriString.startsWith("file:///private/") })
        val refs = model("references").copy(imagesArrayField = "images", maxImages = 20)
        val returned = result.copy(selectedModel = refs).withModelMedia(refs)
        assertEquals(13, returned.attachedMedia.size)
        assertTrue(returned.retainedMedia.isEmpty())
        assertEquals(pool(result).map { it.uriString }.toSet(), pool(returned).map { it.uriString }.toSet())
    }

    @Test fun resolvedMimeAndCurrentAvailabilityDetermineActiveVersusConserved() {
        val target = model().copy(firstFrameField = "image")
        val wrong = image("actually-video")
        val missing = image("missing")
        var staged = stageArtBoxMedia(state(target), listOf(wrong, missing))
        staged = completeArtBoxMedia(staged, wrong, copied(wrong).copy(mimeType = "video/mp4"), true)
        staged = completeArtBoxMedia(staged, missing, copied(missing), true)
        val result = finishArtBoxMediaImport(staged, ModelCapabilityRegistry.key(target, Modality.IMAGE), true,
            listOf(wrong.id, missing.id), setOf(wrong.id, missing.id)) { it.id != missing.id }
        assertTrue(result.attachedMedia.isEmpty())
        assertEquals(setOf(wrong.id, missing.id), result.retainedMedia.map { it.id }.toSet())
        assertEquals("video/mp4", result.retainedMedia.single { it.id == wrong.id }.mimeType)
    }

    @Test fun changingProviderEndpointOrModalityCannotApplyTheOldHandoff() {
        val original = model().copy(contractEndpoint = "/one", firstFrameField = "image",
            contractMedia = listOf(ContractMediaField("image", "Image", "image/*")))
        val incoming = image("new")
        val expected = ModelCapabilityRegistry.key(original, Modality.IMAGE)
        for ((target, modality) in listOf(original.copy(providerId = "another") to Modality.IMAGE,
            original.copy(contractEndpoint = "/other") to Modality.IMAGE, original to Modality.VIDEO)) {
            val prior = copied(image("prior"))
            val current = prepared(state(target).copy(selectedModality = modality, attachedMedia = listOf(prior)), listOf(incoming))
            val result = finishArtBoxMediaImport(current, expected, true, listOf(incoming.id), setOf(incoming.id))
            assertEquals(listOf(prior), result.attachedMedia)
            assertEquals(listOf(copied(incoming)), result.retainedMedia)
        }
    }

    @Test fun switchingAwayAndBackRejectsTheOldSessionEvenWithAnUnresolvedEndpoint() {
        val target = model().copy(firstFrameField = "image")
        val expected = ModelCapabilityRegistry.key(target, Modality.IMAGE)
        val session = MediaSelectionSession()
        val token = session.begin(MediaSlotType.REFERENCE_IMAGE, append = true)
        session.reset() // A → B
        session.reset() // B → A
        val incoming = image("new")
        val result = finishArtBoxMediaImport(prepared(state(target), listOf(incoming)), expected,
            session.accepts(token), listOf(incoming.id), setOf(incoming.id))
        assertTrue(result.attachedMedia.isEmpty())
        assertEquals(listOf(copied(incoming)), result.retainedMedia)
    }

    @Test fun failedCopyDoesNotRemovePriorWorkAndKeepsTheOtherSuccessfulCopies() {
        val target = model().copy(firstFrameField = "image")
        val existing = copied(image("existing"))
        val good = image("good")
        val bad = image("bad")
        var current = stageArtBoxMedia(state(target).copy(attachedMedia = listOf(existing)), listOf(existing, good, bad))
        current = completeArtBoxMedia(current, existing, null, false)
        current = completeArtBoxMedia(current, good, copied(good), true)
        current = completeArtBoxMedia(current, bad, null, true)
        val result = finishArtBoxMediaImport(current, ModelCapabilityRegistry.key(target, Modality.IMAGE), true,
            listOf(good.id), setOf(good.id, bad.id))
        assertEquals(listOf(copied(good)), result.attachedMedia.map { it.copy(slotType = MediaSlotType.REFERENCE_IMAGE) })
        assertEquals(listOf(existing), result.retainedMedia)
        assertFalse(pool(result).any { it.id == bad.id })
    }

    @Test fun explicitlyRemovedStagedReferenceCannotReturnAfterTheCopyCompletes() {
        val target = model().copy(firstFrameField = "image")
        val incoming = image("removed")
        val staged = stageArtBoxMedia(state(target), listOf(incoming))
        val removed = staged.copy(retainedMedia = emptyList())
        val completed = completeArtBoxMedia(removed, incoming, copied(incoming), true)
        val result = finishArtBoxMediaImport(completed, ModelCapabilityRegistry.key(target, Modality.IMAGE), true,
            listOf(incoming.id), setOf(incoming.id))
        assertTrue(pool(result).isEmpty())
    }

    @Test fun missingSchemaConservesTheSelectionUntilTheContractBecomesReady() {
        val target = model().copy(firstFrameField = "image")
        val incoming = image("new")
        val result = finishArtBoxMediaImport(prepared(state(target).copy(schemaReady = false), listOf(incoming)),
            ModelCapabilityRegistry.key(target, Modality.IMAGE), true, listOf(incoming.id), setOf(incoming.id))
        assertTrue(result.attachedMedia.isEmpty())
        assertEquals(listOf(copied(incoming)), result.retainedMedia)
        assertEquals(listOf("new"), result.copy(schemaReady = true).withModelMedia(target).attachedMedia.map { it.id })
    }

    @Test fun firstImageImportDoesNotFillFinalFramesMasksOrReferenceArraysWithItsOverflow() {
        val target = model().copy(firstFrameField = "first", lastFrameField = "last", imagesArrayField = "references", maxImages = 8,
            contractMedia = listOf(ContractMediaField("mask_image", "Mask", "application/octet-stream")))
        val incoming = (1..4).map { image("$it") }
        val result = finishArtBoxMediaImport(prepared(state(target), incoming), ModelCapabilityRegistry.key(target, Modality.IMAGE),
            true, incoming.map { it.id }, incoming.map { it.id }.toSet())
        assertEquals(listOf("1"), result.attachedMedia.map { it.id })
        assertEquals(MediaSlotType.PRIMARY_IMAGE, result.attachedMedia.single().slotType)
        assertEquals(setOf("2", "3", "4"), result.retainedMedia.map { it.id }.toSet())
        assertFalse(result.attachedMedia.any { it.slotType == MediaSlotType.LAST_FRAME || it.slotType == MediaSlotType.REFERENCE_IMAGE })
    }

    @Test fun arrayImportAppendsWithinItsRemainingCapacityAndDoesNotReactivatePreviousRetainedMedia() {
        val target = model().copy(imagesArrayField = "images", maxImages = 2)
        val old = copied(image("old"))
        val alreadyParked = copied(image("already-parked"))
        val incoming = listOf(image("new1"), image("new2"))
        val before = state(target).copy(attachedMedia = listOf(old), retainedMedia = listOf(alreadyParked))
        val result = finishArtBoxMediaImport(prepared(before, incoming), ModelCapabilityRegistry.key(target, Modality.IMAGE),
            true, incoming.map { it.id }, incoming.map { it.id }.toSet())
        assertEquals(listOf("old", "new1"), result.attachedMedia.map { it.id })
        assertEquals(setOf("already-parked", "new2"), result.retainedMedia.map { it.id }.toSet())
        assertEquals(4, pool(result).map { it.id }.distinct().size)
    }
}
