package com.vidadomu.nexus.sync

import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus

/** Additive output merge; a local id collision or running job stays on its originating device. */
internal fun mergeCompletedPeerJob(local: GenerationJob?, remote: GenerationJob): GenerationJob? {
    if (remote.status != GenerationStatus.COMPLETED) return local
    if (local == null) return remote
    if (local.status != GenerationStatus.COMPLETED || local.id != remote.id || local.providerId != remote.providerId || local.modelId != remote.modelId) return local
    val incoming = remote.outputs.associateBy { it.id }
    val outputs = local.outputs.map { output ->
        val peer = incoming[output.id]?.localUri
        val uri = output.localUri?.takeIf { peerUriAvailable(it) } ?: peer?.takeIf { peerUriAvailable(it) } ?: output.localUri
        output.copy(localUri = uri)
    } +
        remote.outputs.filter { r -> local.outputs.none { it.id == r.id } }
    return local.copy(outputs = outputs)
}

internal fun peerUriAvailable(uri: String): Boolean = when {
    uri.startsWith("file:") -> runCatching { java.io.File(java.net.URI(uri)).isFile }.getOrDefault(false)
    uri.startsWith("http://") || uri.startsWith("https://") -> true
    else -> false
}
