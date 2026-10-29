package com.vidadomu.nexus.ui.models

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.ui.create.CanvasInputField

/** The family owns the complete selection; a route's limits only change its visible partition. */
internal data class CatalogMediaReference(val uriString:String,val mimeType:String)
internal data class CatalogMediaPartition(val active:List<CatalogMediaReference>,val retained:List<CatalogMediaReference>)

internal fun mergeCatalogMediaReferences(current:List<CatalogMediaReference>,incoming:List<CatalogMediaReference>) =
    (current+incoming).distinctBy {it.uriString}

/** A deliberate replacement uses the new file first while preserving the earlier inputs in the same pool. */
internal fun addCatalogMediaReferences(current:List<CatalogMediaReference>,incoming:List<CatalogMediaReference>,singleInput:Boolean) =
    if(singleInput) mergeCatalogMediaReferences(incoming,current) else mergeCatalogMediaReferences(current,incoming)

internal fun removeCatalogMediaReference(selection:List<CatalogMediaReference>,uriString:String) =
    selection.filterNot {it.uriString==uriString}

internal fun catalogMediaPartition(selection:List<CatalogMediaReference>,field:CanvasInputField?):CatalogMediaPartition {
    val active=if(field==null) emptyList() else selection.filter {field.acceptsMime(it.mimeType)}.take(field.maximum.coerceAtLeast(0))
    val activeUris=active.map {it.uriString}.toSet()
    return CatalogMediaPartition(active,selection.filterNot {it.uriString in activeUris})
}

internal val CatalogMediaSelectionSaver=listSaver<List<CatalogMediaReference>,String>(
    save={selection -> selection.flatMap {listOf(it.uriString,it.mimeType)}},
    restore={saved -> saved.chunked(2).filter {it.size==2}.map {CatalogMediaReference(it[0],it[1])}})

/** Keeps every original group identity while opening its real brief directly from the family card. */
@Composable
internal fun FamilyModelOptionsScreen(family:String,groups:List<CatalogModelGroup>,selectedGroup:CatalogModelGroup?,
    onSelectGroup:(CatalogModelGroup)->Unit,favorites:Set<String>,toggleFavorite:(NexusModel)->Unit,
    onUse:(NexusModel)->Unit,modifier:Modifier=Modifier,
    onUseWithMedia:((NexusModel,List<Pair<android.net.Uri,String>>)->Unit)?=null,
    mediaSelection:MutableState<List<CatalogMediaReference>>) {
    val context=LocalContext.current
    val choices=remember(context) {context.getSharedPreferences("catalog_choices_v1",0)}
    // The browser persists its chosen tab; a route may declare several modalities, so its first entry is not this scope.
    val modality=runCatching {Modality.valueOf(context.getSharedPreferences("catalog_grid_v2",0)
        .getString("browser_modality",Modality.VIDEO.name) ?: Modality.VIDEO.name)}.getOrDefault(Modality.VIDEO)
    val preferenceKey="family-variant:v1:${modality.name}:$family"
    var savedGroupKey by remember(preferenceKey) {mutableStateOf(choices.getString(preferenceKey,null))}
    val group=groups.firstOrNull {it.key==selectedGroup?.key}
        ?: groups.firstOrNull {it.key==savedGroupKey} ?: groups.firstOrNull() ?: return
    LaunchedEffect(group.key) {if(selectedGroup==null) onSelectGroup(group)}
    var currentModel by remember(family) {mutableStateOf<NexusModel?>(null)}
    var currentWorkflow by remember(family) {mutableStateOf<CatalogWorkflow?>(null)}
    var continuation by remember(family) {mutableStateOf<NexusModel?>(null)}
    var continuingWorkflow by remember(family) {mutableStateOf<CatalogWorkflow?>(null)}
    ModelOptionsScreen(group,favorites,toggleFavorite,onUse,modifier=modifier,onUseWithMedia=onUseWithMedia,familyTitle=family,
        mediaSelection=mediaSelection,preferredModel=continuation,preferredWorkflow=continuingWorkflow,
        onSelectionChanged={model,workflow -> currentModel=model;currentWorkflow=workflow},
        familySelector={CatalogVariantSelector(groups,group,{next ->
            savedGroupKey=next.key
            choices.edit().putString(preferenceKey,next.key).apply()
            continuation=catalogVariantContinuation(next,currentModel)
            continuingWorkflow=currentWorkflow
            onSelectGroup(next)
        })})
}
