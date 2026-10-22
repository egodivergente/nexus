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
    var compatibleOnly by rememberSaveable { mutableStateOf(media.isNotEmpty()) }
    val filtered = remember(availableModels, modality, media, compatibleOnly, org, query) {
        filterCanvasModels(availableModels, modality, media, compatibleOnly, org, query)
    }
    val families = remember(filtered) { filtered.groupBy(::catalogFamily).toSortedMap() }
    val orgs = remember(availableModels, modality) {
        availableModels.filter { modality == null || modality in it.modalities }.map { it.organization }.distinct().sorted()
    }
    ModalBottomSheet(onDismissRequest=onDismiss, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        com.vidadomu.nexus.ui.theme.NexusSheetSystemBars()
        LazyColumn(Modifier.fillMaxWidth(), contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=24.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Elige tu modelo",style=MaterialTheme.typography.titleLarge)
                        Text("${filtered.size} ${if(filtered.size==1) "modelo" else "modelos"} · ${families.size} ${if(families.size==1) "familia" else "familias"}",style=MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick=onDismiss) {Icon(Icons.Default.Close,"Cerrar selector de modelos")}
                }
            }
            item {
                OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
                    label={Text("Buscar modelo, familia o tarea")},leadingIcon={Icon(Icons.Default.Search,null)},
                    trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}) {Icon(Icons.Default.Close,"Limpiar búsqueda de modelos")}})
            }
            item {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(listOf(Modality.IMAGE to "Imagen",Modality.VIDEO to "Vídeo",Modality.AUDIO to "Audio",Modality.CHAT to "Chat")) { (value,label) ->
                        FilterChip(modality==value,{modality=value;org=null},label={Text(label)})
                    }
                    item {FilterChip(modality==null,{modality=null;org=null;compatibleOnly=false},label={Text("Todo el catálogo")})}
                }
            }
            if(media.isNotEmpty()) item {
                Column {
                    FilterChip(compatibleOnly,{compatibleOnly=!compatibleOnly},label={Text("Admite mis ${media.size} referencias")})
                    Text("Según las entradas declaradas. Si falta un modelo, prueba Todo el catálogo. Revisarás la configuración final antes de generar.",style=MaterialTheme.typography.bodySmall)
                }
            }
            if(orgs.size>1) item {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    item {FilterChip(org==null,{org=null},label={Text("Todas las marcas")})}
                    items(orgs,key={it}) {brand -> FilterChip(org==brand,{org=if(org==brand) null else brand},label={Text(brand)})}
                }
            }
            item {
                Text("Tarifas guardadas, no un coste final. El precio se consulta al elegir y configurar el modelo.",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(filtered.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical=16.dp)) {
                    Text("No hay modelos con estos filtros",style=MaterialTheme.typography.titleMedium)
                    TextButton(onClick={query="";org=null;modality=null;compatibleOnly=false}) {Text("Ver todo el catálogo")}
                }
            }
            families.forEach { (family,models) ->
                item(key="family:$family") {
                    Text("$family · ${models.size}",Modifier.padding(top=12.dp).semantics {heading()},
                        style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                }
                items(models,key={"model:${it.providerId}:${it.id}"}) {model ->
                    val isSelected=model.id==selectedModel?.id && model.providerId==selectedModel?.providerId
                    val tariff=remember(model) {AtlasPublishedPrice.cached(model) ?: PublishedTariff.quote(model)}
                    val price=tariff?.takeIf {it.unit!=null && it.usd.isFinite() && it.usd>=0}?.let {"${if(it.baseTariff) "Desde " else ""}${currencyManager.formatUnitPrice(it.unitUsd ?: it.usd)} / ${it.unit}"} ?: "Por confirmar"
                    Surface(color=if(isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        shape=NexusTokens.field,modifier=Modifier.fillMaxWidth()
                            .semantics(mergeDescendants=true) {selected=isSelected}
                            .clickable(role=Role.RadioButton) {onSelectModel(model)}) {
                        Row(Modifier.padding(12.dp).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            BrandLogo(model.organization,model.family,model.id,size=24.dp,containerSize=40.dp)
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
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
