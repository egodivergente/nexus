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
    val versionMatch = Regex("$token[- .]*(?:video[- ]*)?v?([oh]?\\d+(?:[._-]\\d+)?)(?=[^0-9]|$)").find(text)
    val version = versionMatch?.groupValues?.get(1)
    if (versionMatch != null && version != null) {
        val normalized = version.replace('_', '.').replace('-', '.').removeSuffix(".0")
        // A speed/size tier right after the version (Seedance 2.0 Fast, Mini…) is its own model, not a variant of the base one.
        // Limited to families where those tiers are separate products with their own price and limits.
        val tier = if(family == "Seedance") Regex("^[-_ ]?(fast|mini|lite)(?=[^a-z]|$)").find(text.substring(versionMatch.range.last + 1))?.groupValues?.get(1) else null
        return "$family ${normalized.uppercase()}" + (tier?.let { " " + it.replaceFirstChar { c -> c.uppercase() } } ?: "")
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

