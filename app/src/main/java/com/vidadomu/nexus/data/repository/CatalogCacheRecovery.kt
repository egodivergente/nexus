package com.vidadomu.nexus.data.repository

import com.vidadomu.nexus.core.model.NexusModel
import kotlinx.serialization.json.Json

internal fun recoverCatalog(content: String?, bundled: () -> List<NexusModel>): List<NexusModel> {
    val cached=content?.takeIf {it.isNotBlank()}?.let {
        runCatching {Json {ignoreUnknownKeys=true}.decodeFromString<List<NexusModel>>(it)}.getOrNull()
    }
    return cached?.takeIf {it.isNotEmpty()} ?: bundled()
}
