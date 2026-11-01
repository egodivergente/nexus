package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.Modality
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
    mergeProviderTwinGroups(recentModels(models).distinctBy(::modelBookmark)
        .groupBy { catalogFamily(it) + ":" + modelGroupTitle(it).lowercase(java.util.Locale.ROOT) }
        .map { (key, routes) -> CatalogModelGroup(key, modelGroupTitle(routes.first()), routes) })
        .sortedByDescending { group -> group.routes.any { modelBookmark(it) in favorites } }

private val PROVIDER_WORKFLOW_SUFFIX = Regex("([/_-](?:image|reference|text|start-end|first-last-frame)-to-video|[/_-](?:i2v|r2v|t2v))$")

/**
 * Provider-neutral names of a published route, letters and digits only and without the workflow suffix: Atlas
 * «minimax/h3/image-to-video» and Crun «minimax/h3-i2v» are both «minimaxh3». The last path segment also counts when it
 * carries a version («bytedance/seedance-2.0» and «seedance2-0» meet as «seedance20»). Only for grouping, never for requests.
 */
internal fun providerNeutralStems(model: NexusModel): Set<String> {
    val id = (model.providerModelId?.takeIf { it.isNotBlank() } ?: model.id).lowercase(java.util.Locale.ROOT)
        .removePrefix("crun/").removePrefix("runway/").replace(PROVIDER_WORKFLOW_SUFFIX, "")
    val full = id.filter(Char::isLetterOrDigit)
    val last = id.substringAfterLast('/').filter(Char::isLetterOrDigit).takeIf { it.length >= 3 && it.any(Char::isDigit) }
    return setOfNotNull(full.takeIf { it.length >= 3 }, last)
}

/** Routes of one variant published by different providers, in their original order; a provider never appears twice in one. */
internal fun providerTwinClusters(routes: List<NexusModel>): List<List<NexusModel>> {
    val clusters = mutableListOf<MutableList<NexusModel>>()
    val stems = mutableListOf<MutableSet<String>>()
    for (route in routes) {
        val own = providerNeutralStems(route)
        val index = clusters.indices.firstOrNull { i -> stems[i].any { it in own } && clusters[i].none { it.providerId == route.providerId } }
        if (index == null) { clusters += mutableListOf(route); stems += own.toMutableSet() }
        else { clusters[index] += route; stems[index] += own }
    }
    return clusters
}

/**
 * The same video model on another provider joins its card instead of becoming a second variant (Vidal 01-11: «MiniMax H3»
 * and «Video» were Atlas Cloud and Crun). Only within one family, only video, and only when no provider repeats, so two
 * products of the same provider never merge. The shorter (base) title and its key are kept.
 */
private fun mergeProviderTwinGroups(groups: List<CatalogModelGroup>): List<CatalogModelGroup> {
    val stemCache = HashMap<String, Set<String>>()
    fun stems(model: NexusModel) = stemCache.getOrPut(modelBookmark(model)) { providerNeutralStems(model) }
    fun video(group: CatalogModelGroup) = group.routes.all { Modality.VIDEO in it.modalities && Modality.CHAT !in it.modalities }
    fun twins(a: CatalogModelGroup, b: CatalogModelGroup): Boolean {
        if (a.key.substringBefore(':') != b.key.substringBefore(':') || !video(a) || !video(b)) return false
        if (a.routes.any { r -> b.routes.any { it.providerId == r.providerId } }) return false
        val own = a.routes.flatMapTo(HashSet()) { stems(it) }
        return b.routes.any { route -> stems(route).any { it in own } }
    }
    val out = groups.toMutableList()
    var i = 0
    while (i < out.size) {
        var j = i + 1
        while (j < out.size) {
            if (twins(out[i], out[j])) {
                val a = out[i]; val b = out[j]
                val (keep, other) = if (b.title.length < a.title.length) b to a else a to b
                out[i] = keep.copy(routes = keep.routes + other.routes)
                out.removeAt(j)
                j = i + 1
            } else j++
        }
        i++
    }
    return out
}

internal fun groupPurpose(group: CatalogModelGroup): String = workflowRoutes(group).keys.joinToString(" · ") {it.label}

