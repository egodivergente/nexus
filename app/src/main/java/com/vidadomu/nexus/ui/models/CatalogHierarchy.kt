package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.NexusModel

/** Navigation groups, not interchangeable API routes. Every leaf retains its original ID. */
internal data class CatalogModelGroup(val key: String, val title: String, val routes: List<NexusModel>)

internal fun modelGroupTitle(model: NexusModel): String {
    val family = catalogFamily(model)
    val text = (model.providerModelId ?: model.id).lowercase()
    val token = when (family) {
        "Qwen Image" -> "qwen[- ]image"
        "Grok Imagine" -> "grok[- ]imagine"
        "GPT Image" -> "gpt[- ]image"
        "Nano Banana" -> "nano[- ]banana"
        else -> Regex.escape(family.lowercase()).replace(" ", "[- ]")
    }
    val version = Regex("$token[- .]*(?:video[- ]*)?v?([oh]?\\d+(?:[._-]\\d+)?)(?=[^0-9]|$)").find(text)?.groupValues?.get(1)
    if (version != null) {
        val normalized = version.replace('_', '.').replace('-', '.').removeSuffix(".0")
        return "$family ${normalized.uppercase()}"
    }
    // Strip only explicit workflow names. Unknown product identities remain separate.
    return model.displayName
        .replace(Regex("(?i)\\b(text[- ]to[- ](?:image|video)|image[- ]to[- ](?:image|video)|reference[- ]to[- ](?:image|video)|video[- ]edit|motion[- ]control|start[- ]end[- ]frame|t2v|i2v)\\b"), " ")
        .replace(Regex("(?i)\\b(atlas cloud|crun|edit|pro|std|standard|turbo|fast|mini|4k|1080p|720p|480p)\\b"), " ")
        .replace(Regex("[-_ ]+"), " ").trim().ifBlank { family }
}

internal fun catalogModelGroups(models: List<NexusModel>, favorites: Set<String> = emptySet()): List<CatalogModelGroup> =
    recentModels(models).distinctBy(::modelBookmark)
        .groupBy { catalogFamily(it) + ":" + modelGroupTitle(it).lowercase() }
        .map { (key, routes) -> CatalogModelGroup(key, modelGroupTitle(routes.first()), routes) }
        .sortedByDescending { group -> group.routes.any { modelBookmark(it) in favorites } }

internal fun groupPurpose(group: CatalogModelGroup): String = workflowRoutes(group).keys.joinToString(" · ") {it.label}

