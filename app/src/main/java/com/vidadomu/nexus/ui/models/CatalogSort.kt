package com.vidadomu.nexus.ui.models
import com.vidadomu.nexus.core.model.GenerationJob
internal enum class CatalogSort(val label:String) { PERSONAL("Mi orden"),NAME("Nombre"),PRICE("Precio base"),RECENT("Usados recientemente"),RELEASE("Más nuevos · lanzamiento") }
internal fun latestVerifiedRelease(group:CatalogModelGroup):String? = group.routes.mapNotNull {modelRelease(it)?.month}.maxOrNull()
internal fun filterDatedGroups(groups:List<CatalogModelGroup>,onlyDated:Boolean) = if(onlyDated) groups.filter {latestVerifiedRelease(it)!=null} else groups
internal fun sortGroups(groups:List<CatalogModelGroup>,sort:CatalogSort,jobs:List<GenerationJob>):List<CatalogModelGroup> = when(sort) {
    CatalogSort.PERSONAL -> groups
    CatalogSort.NAME -> groups.sortedBy {it.title.lowercase()}
    CatalogSort.PRICE -> groups.sortedBy {g->g.routes.mapNotNull(::referencePrice).minOrNull() ?: Double.MAX_VALUE}
    CatalogSort.RECENT -> { val recency=jobs.groupBy {it.providerId+":"+it.modelId}.mapValues {it.value.maxOf {j->j.startedAt}}
        groups.sortedByDescending {g->g.routes.maxOfOrNull {recency[modelBookmark(it)] ?: 0L} ?: 0L} }
    CatalogSort.RELEASE -> groups.sortedWith(compareByDescending<CatalogModelGroup> {latestVerifiedRelease(it).orEmpty()}.thenBy {it.title.lowercase()})
}
