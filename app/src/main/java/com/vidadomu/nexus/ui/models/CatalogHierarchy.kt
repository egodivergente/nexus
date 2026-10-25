package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.NexusModel

/** Navigation groups, not interchangeable API routes. Every leaf retains its original ID. */
internal data class CatalogModelGroup(val key: String, val title: String, val routes: List<NexusModel>)

internal fun catalogAtRoot(family: String?, query: String, favoritesOnly: Boolean): Boolean =
    family == null && query.isBlank() && !favoritesOnly

private val TIER_TOKENS = Regex("(?<![a-z])(fast|mini|lite|flash|turbo|pro|std|standard|master|max|prime|ultra)(?![a-z])")
private val GPT_PRODUCTS = listOf("sunburst", "flare", "developer").associateWith { Regex("(?<![a-z])$it(?![a-z])") }
private val WORKFLOW_WORDS = Regex("(?i)\\b(text[- ]to[- ](?:image|video)|image[- ]to[- ](?:image|video)|reference[- ]to[- ](?:image|video)|video[- ]edit|motion[- ]control|start[- ]end[- ]frame|t2v|i2v)\\b")
private val TIER_WORDS = Regex("(?i)\\b(atlas cloud|crun|edit|pro|std|standard|turbo|fast|mini|4k|1080p|720p|480p)\\b")
private val SEPARATORS = Regex("[-_ ]+")
private val versionPatterns = java.util.concurrent.ConcurrentHashMap<String, Regex>()
/** Titles are pure functions of a few strings; the catalogue asks for them on every recomposition and visibility check. */
private val groupTitleCache = com.vidadomu.nexus.core.util.BoundedCache<String, String>(4096)

private fun versionPattern(family: String): Regex = versionPatterns.getOrPut(family) {
    val token = when (family) {
        "Qwen Image" -> "qwen[- ]image"
        "Grok Imagine" -> "grok[- ]imagine"
        "GPT Image" -> "gpt[- ]image"
        "Nano Banana" -> "nano[- ]banana"
        else -> Regex.escape(family.lowercase(java.util.Locale.ROOT)).replace(" ", "[- ]")
    }
    Regex("$token[- .]*(?:video[- ]*)?v?([oh]?\\d+(?:[._-]\\d+)?)(?=[^0-9]|$)")
}

internal fun modelGroupTitle(model: NexusModel): String {
    val family = catalogFamily(model)
    val text = (model.providerModelId?.takeIf { it.isNotBlank() } ?: model.id).lowercase(java.util.Locale.ROOT)
    return groupTitleCache.getOrPut(family + "\u0000" + text + "\u0000" + model.displayName) { computeGroupTitle(family, text, model.displayName) }
}

private fun computeGroupTitle(family: String, text: String, displayName: String): String {
    val versionMatch = versionPattern(family).find(text)
    val version = versionMatch?.groupValues?.get(1)
    if (versionMatch != null && version != null) {
        val normalized = version.replace('_', '.').replace('-', '.').removeSuffix(".0")
        // A speed/size tier right after the version (Seedance 2.0 Fast, Mini…) is its own model, not a variant of the base one.
        // Limited to families where those tiers are separate products with their own price and limits.
        // The tier can sit right after the version (kling-v3.0-pro) or after the workflow (seedance-v1.5-pro/image-to-video-fast).
        val tiers = TIER_TOKENS.findAll(text.substring(versionMatch.range.last + 1)).map { it.groupValues[1] }.distinct().toList()
        val product=if(family=="GPT Image") GPT_PRODUCTS.filter { (_, pattern) -> pattern.containsMatchIn(text) }.keys
            .joinToString("") {" "+it.replaceFirstChar(Char::uppercase)} else ""
        return "$family ${normalized.uppercase(java.util.Locale.ROOT)}" + product + tiers.joinToString("") { " " + (if(it == "std" || it == "standard") "Std" else it.replaceFirstChar { c -> c.uppercase(java.util.Locale.ROOT) }) }
    }
    // Strip only explicit workflow names. Unknown product identities remain separate.
    return displayName
        .replace(WORKFLOW_WORDS, " ")
        .replace(TIER_WORDS, " ")
        .replace(SEPARATORS, " ").trim().ifBlank { family }
}

internal fun catalogModelGroups(models: List<NexusModel>, favorites: Set<String> = emptySet()): List<CatalogModelGroup> =
    recentModels(models).distinctBy(::modelBookmark)
        .groupBy { catalogFamily(it) + ":" + modelGroupTitle(it).lowercase(java.util.Locale.ROOT) }
        .map { (key, routes) -> CatalogModelGroup(key, modelGroupTitle(routes.first()), routes) }
        .sortedByDescending { group -> group.routes.any { modelBookmark(it) in favorites } }

internal fun groupPurpose(group: CatalogModelGroup): String = workflowRoutes(group).keys.joinToString(" · ") {it.label}

