package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.NexusModel

/** A compact label only: versions, tiers and every original group/route identity remain unchanged. */
internal fun catalogVariantTitle(group:CatalogModelGroup):String {
    val family=group.routes.firstOrNull()?.let(::catalogFamily) ?: return group.title
    val title=group.title
    val prefix=title.startsWith(family) && (title.length==family.length || title[family.length].isWhitespace())
    return if(prefix) title.removePrefix(family).trim().ifBlank {title} else title
}

/** Resolve the actual route, including its provider; neither a similar name nor a family is an identity. */
internal fun catalogFamilyVariantForModel(groups:List<CatalogModelGroup>,model:NexusModel?):CatalogModelGroup? {
    val bookmark=model?.let(::modelBookmark) ?: return null
    return groups.singleOrNull {group -> group.routes.any {modelBookmark(it)==bookmark}}
}

/** Continue a variant only when its native provider, modalities and complete workflow set identify one route. */
internal fun catalogVariantContinuation(group:CatalogModelGroup,current:NexusModel?):NexusModel? {
    if(current==null) return group.routes.singleOrNull()
    val modalities=current.modalities.toSet()
    val workflows=catalogWorkflows(current).toSet()
    return group.routes.filter {route -> route.providerId==current.providerId &&
        route.modalities.toSet()==modalities && catalogWorkflows(route).toSet()==workflows}.singleOrNull()
}
