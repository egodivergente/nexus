package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.ui.create.capability.*

/** Domain projection of the active request; retained resources stay owned by CreateUiState/the saved draft. */
internal fun CreateUiState.capabilityValidation(): CapabilityValidation? {
    val model=selectedModel ?: return null
    val capabilities=ModelCapabilityRegistry.resolve(model,selectedModality,parameters,schemaReady,attachedMedia)
    return ComposerState(prompt,attachedMedia).withValues(capabilities.key,parameters).evaluate(capabilities)
}

/** The workspace owns every reference; a request only sees the active partition. */
internal fun CreateUiState.withModelMedia(model: NexusModel,isAvailable:(AttachedMedia)->Boolean = {true}): CreateUiState {
    val pool=(attachedMedia+retainedMedia).distinctBy {it.id}
    val routed=compatibleModelMedia(model,pool.filter(isAvailable))
    val fields=ModelCapabilityRegistry.resolve(model,selectedModality,parameters,schemaReady,routed).inputFields
    val active=fields.flatMap {field -> routed.filter {field.contains(it) && field.acceptsMime(it.mimeType)}.take(field.maximum)}
        .distinctBy {it.id}
    val ids=active.map {it.id}.toSet()
    return copy(attachedMedia=active,retainedMedia=pool.filterNot {it.id in ids})
}
