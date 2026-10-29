package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.ui.create.capability.CapabilityKey
import com.vidadomu.nexus.ui.create.capability.ModelCapabilityRegistry

/** Keep the complete selection visible/owned while private copies are prepared, including text-only targets. */
internal fun stageArtBoxMedia(state: CreateUiState, selection: List<AttachedMedia>): CreateUiState {
    val existing = (state.attachedMedia + state.retainedMedia).map { it.id }.toSet()
    return state.copy(retainedMedia = (state.retainedMedia + selection.filterNot { it.id in existing }).distinctBy { it.id })
}

/** Only a still-owned placeholder may be completed; removing it explicitly must never resurrect it. */
internal fun completeArtBoxMedia(state: CreateUiState, original: AttachedMedia, prepared: AttachedMedia?, newlyStaged: Boolean): CreateUiState {
    if ((state.attachedMedia + state.retainedMedia).none { it.id == original.id }) return state
    if (prepared == null && !newlyStaged) return state // A failed import cannot remove an existing Canvas reference.
    if (newlyStaged) return state.copy(
        attachedMedia = state.attachedMedia.filterNot { it.id == original.id },
        retainedMedia = (state.retainedMedia.filterNot { it.id == original.id } + listOfNotNull(prepared)).distinctBy { it.id })
    return state.copy(
        attachedMedia = state.attachedMedia.map { if (it.id == original.id) prepared!! else it },
        retainedMedia = state.retainedMedia.map { if (it.id == original.id) prepared!! else it })
}

/** A stale handoff remains conserved, never active on a different form (including A → B → A). */
internal fun finishArtBoxMediaImport(state: CreateUiState, expected: CapabilityKey, sessionStillCurrent: Boolean,
    importedIds: List<String>, newlyStagedIds: Set<String>, isAvailable: (AttachedMedia) -> Boolean = { true }): CreateUiState {
    val pool = (state.attachedMedia + state.retainedMedia).distinctBy { it.id }
    val model = state.selectedModel
    val actual = model?.let { ModelCapabilityRegistry.key(it, state.selectedModality) }
    val sameTarget = actual != null && expected.providerId == actual.providerId && expected.modelId == actual.modelId &&
        expected.modality == actual.modality && (expected.endpoint == null || expected.endpoint == actual.endpoint)
    if (!sessionStillCurrent || !sameTarget || !state.schemaReady) return state.copy(
        attachedMedia = state.attachedMedia.filterNot { it.id in newlyStagedIds },
        retainedMedia = (state.retainedMedia + pool.filter { it.id in newlyStagedIds }).distinctBy { it.id })

    val byId = pool.associateBy { it.id }
    val selected = importedIds.distinct().mapNotNull(byId::get)
    val activeModel = requireNotNull(model)
    // ArtBox offered its first image field. Overflow must not acquire an end-frame/mask/other role implicitly.
    val field = ModelCapabilityRegistry.resolve(activeModel, state.selectedModality, state.parameters, true, state.attachedMedia)
        .inputFields.firstOrNull { it.mime.startsWith("image/") || it.mime == "*/*" }
        ?: return state.copy(attachedMedia = state.attachedMedia.filterNot { it.id in newlyStagedIds },
            retainedMedia = (state.retainedMedia + pool.filter { it.id in newlyStagedIds }).distinctBy { it.id })
    val eligible = selected.filter { isAvailable(it) && field.acceptsMime(it.mimeType) }
    val otherActive = state.attachedMedia.filterNot { it.id in importedIds }
    val replacing = field.maximum == 1 && eligible.isNotEmpty()
    val previousActive = if (replacing) otherActive.filterNot(field::contains) else otherActive
    val remaining = (field.maximum - previousActive.count(field::contains)).coerceAtLeast(0)
    val assigned = eligible.take(remaining).map { it.copy(fieldKey = field.contract?.key, slotType = field.slot) }
    // Revalidate only the intended active candidates. Keep every overflow/previously retained item outside this pass.
    val projected = state.copy(attachedMedia = previousActive + assigned, retainedMedia = emptyList())
        .withModelMedia(activeModel, isAvailable)
    val activeIds = projected.attachedMedia.map { it.id }.toSet()
    return projected.copy(retainedMedia = pool.filterNot { it.id in activeIds })
}
