package com.vidadomu.nexus.ui.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.core.currency.CurrencyManager
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.AtlasPublishedPrice
import com.vidadomu.nexus.provider.atlas.PublishedTariff
import com.vidadomu.nexus.ui.components.BrandLogo
import com.vidadomu.nexus.ui.models.catalogFamily
import com.vidadomu.nexus.ui.models.modelUse
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.NexusHapticEvent
import com.vidadomu.nexus.ui.theme.rememberNexusHaptics

/** One scrolling surface, including filters: still reachable with IME or large text.
 * Material's sheet consumes the system insets; the footer is just breathing room.
 * Browsing never starts hundreds of schema/price requests. Quote the selected model in the canvas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelPickerSheet(availableModels: List<NexusModel>, selectedModel: NexusModel?,
    initialModality: Modality, media: List<AttachedMedia>, currencyManager: CurrencyManager,
    onSelectModel: (NexusModel) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var org by rememberSaveable { mutableStateOf<String?>(null) }
    var modality by rememberSaveable { mutableStateOf<Modality?>(initialModality) }
    // Switching is safe even when a target uses fewer inputs: the composer keeps the rest.
    var compatibleOnly by rememberSaveable { mutableStateOf(false) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val feedback=rememberNexusHaptics()
    val context=LocalContext.current
    val favoritesStore=remember(context) {StudioFavoritesStore(context)}
    var favoritesRevision by remember {mutableIntStateOf(0)}
    var editingFavorites by remember {mutableStateOf(false)}
    val favoriteKeys=remember(availableModels,modality,favoritesRevision) {
        (modality?.let(::listOf) ?: listOf(Modality.IMAGE,Modality.VIDEO,Modality.AUDIO,Modality.CHAT)).flatMap {type ->
            studioFavorites(availableModels,type,favoritesStore.read(type)).map {it.model.studioKey()}
        }.toSet()
    }
    if(editingFavorites) {
        val type=modality ?: initialModality
        StudioFavoritesEditor(type,availableModels,favoritesStore.read(type),onSave={keys ->
            favoritesStore.save(type,keys);favoritesRevision++;editingFavorites=false
        },onDismiss={editingFavorites=false})
        return
    }
    val selectionMedia=remember(selectedModel,media) {selectedModel?.let {canonicalModelMedia(it,media)} ?: media}
    val filtered = remember(availableModels, modality, selectionMedia, compatibleOnly, org, query, favoritesOnly, favoriteKeys) {
        filterCanvasModels(availableModels, modality, selectionMedia, compatibleOnly, org, query)
            .filter {!favoritesOnly || it.studioKey() in favoriteKeys}
    }
    val families = remember(filtered) { filtered.groupBy(::catalogFamily).toSortedMap() }
    val orgs = remember(availableModels, modality) {
        availableModels.filter { modality == null || modality in it.modalities }.map { it.organization }.distinct().sorted()
    }
    ModalBottomSheet(onDismissRequest=onDismiss, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        com.vidadomu.nexus.ui.theme.NexusSheetSystemBars()
        LazyColumn(Modifier.fillMaxWidth(), contentPadding=PaddingValues(start=NexusTokens.space16,end=NexusTokens.space16,bottom=NexusTokens.space24),
            verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cambiar modelo",style=MaterialTheme.typography.titleLarge)
                        Text("Conservas prompt, archivos y ajustes compatibles",style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick={editingFavorites=true}) {Icon(Icons.Default.StarOutline,"Editar modelos favoritos")}
                    IconButton(onClick=onDismiss) {Icon(Icons.Default.Close,"Cerrar selector de modelos")}
                }
            }
            item {
                OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
                    label={Text("Buscar modelo, familia o tarea")},leadingIcon={Icon(Icons.Default.Search,null)},
                    trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}) {Icon(Icons.Default.Close,"Limpiar búsqueda de modelos")}})
            }
            item {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    item {FilterChip(favoritesOnly,{favoritesOnly=!favoritesOnly},label={Text("Favoritos")},leadingIcon={Icon(Icons.Default.Star,null,Modifier.size(NexusTokens.iconSmall))})}
                    if(media.isNotEmpty()) item {FilterChip(compatibleOnly,{compatibleOnly=!compatibleOnly},label={Text("Todas mis referencias")})}
                    item {FilterChip(showFilters,{showFilters=!showFilters},label={Text("Filtros")},leadingIcon={Icon(Icons.Default.Tune,null,Modifier.size(NexusTokens.iconSmall))})}
                }
            }
            if(showFilters) item {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    items(listOf(Modality.IMAGE to "Imagen",Modality.VIDEO to "Vídeo",Modality.AUDIO to "Audio",Modality.CHAT to "Chat")) { (value,label) ->
                        FilterChip(modality==value,{modality=value;org=null},label={Text(label)})
                    }
                    item {FilterChip(modality==null,{modality=null;org=null;compatibleOnly=false},label={Text("Todo el catálogo")})}
                }
            }
            if(showFilters && orgs.size>1) item {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    item {FilterChip(org==null,{org=null},label={Text("Todas las marcas")})}
                    items(orgs,key={it}) {brand -> FilterChip(org==brand,{org=if(org==brand) null else brand},label={Text(brand)})}
                }
            }
            item {
                Text("${filtered.size} modelos"+if(media.isNotEmpty()) " · Los archivos incompatibles quedan guardados" else "",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(filtered.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical=NexusTokens.space16)) {
                    Text("No hay modelos con estos filtros",style=MaterialTheme.typography.titleMedium)
                    TextButton(onClick={query="";org=null;modality=null;compatibleOnly=false;favoritesOnly=false}) {Text("Ver todo el catálogo")}
                }
            }
            families.forEach { (family,models) ->
                item(key="family:$family") {
                    Text("$family · ${models.size}",Modifier.padding(top=NexusTokens.space12).semantics {heading()},
                        style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                }
                items(models,key={"model:${it.providerId}:${it.id}"}) {model ->
                    val isSelected=model.id==selectedModel?.id && model.providerId==selectedModel?.providerId
                    val tariff=remember(model) {AtlasPublishedPrice.cached(model) ?: PublishedTariff.quote(model)}
                    val price=tariff?.takeIf {it.unit!=null && it.usd.isFinite() && it.usd>=0}?.let {"${if(it.baseTariff) "Desde " else ""}${currencyManager.formatUnitPrice(it.unitUsd ?: it.usd)} / ${it.unit}"} ?: "Por confirmar"
                    Surface(color=if(isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        shape=NexusTokens.field,modifier=Modifier.fillMaxWidth()
                            .semantics(mergeDescendants=true) {selected=isSelected}
                            .clickable(role=Role.RadioButton) {
                                feedback.perform(NexusHapticEvent.SELECTION,changed=!isSelected)
                                onSelectModel(model)
                            }) {
                        Row(Modifier.padding(NexusTokens.space12).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                            BrandLogo(model.organization,model.family,model.id,size=24.dp,containerSize=40.dp)
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                                Text(canvasModelIdentity(model).title,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.SemiBold,
                                    maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text("${canvasModelIdentity(model).detail} · $price",style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(isSelected) Icon(Icons.Default.Check,null,tint=MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
