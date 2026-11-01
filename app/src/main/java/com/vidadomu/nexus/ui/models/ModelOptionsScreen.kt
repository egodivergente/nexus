package com.vidadomu.nexus.ui.models

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.provider.atlas.AtlasCloudProvider
import com.vidadomu.nexus.provider.atlas.AtlasPublishedPrice
import com.vidadomu.nexus.provider.atlas.NativeModelSchema
import com.vidadomu.nexus.ui.theme.*
import com.vidadomu.nexus.ui.components.BrandLogo
import com.vidadomu.nexus.ui.components.rememberPriceEstimate
import com.vidadomu.nexus.ui.components.rememberPublishedPrice

/** A quiet model brief with one action, and secondary routes in a separate sheet. */
@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
internal fun ModelOptionsScreen(group:CatalogModelGroup,favorites:Set<String>,toggleFavorite:(NexusModel)->Unit,
    onUse:(NexusModel)->Unit,onHide:((NexusModel)->Unit)?=null,modifier:Modifier=Modifier,
    onUseWithMedia:((NexusModel,List<Pair<android.net.Uri,String>>)->Unit)?=null,
    mediaSelection:MutableState<List<CatalogMediaReference>>?=null,preferredModel:NexusModel?=null,
    preferredWorkflow:CatalogWorkflow?=null,onSelectionChanged:(NexusModel,CatalogWorkflow)->Unit={_,_->},
    familySelector:@Composable ()->Unit={},familyTitle:String?=null) {
    val context=LocalContext.current
    val app=context.applicationContext as NexusApplication
    val choices=remember {context.getSharedPreferences("catalog_choices_v1",0)}
    val rules by com.vidadomu.nexus.settings.RuntimePreferences.get(context).state.collectAsState()
    val workflowOrder=rules.value(if(com.vidadomu.nexus.core.model.Modality.VIDEO in group.routes.first().modalities) "catalog.videoOrder" else "catalog.imageOrder").split(',').map {it.trim()}
    val workflows=remember(group,workflowOrder) {workflowRoutes(group,workflowOrder)}
    val preferred=preferredModel?.takeIf {candidate -> group.routes.any {modelBookmark(it)==modelBookmark(candidate)}}
    var taskKey by rememberSaveable(group.key) {mutableStateOf(
        preferredWorkflow?.takeIf {it in workflows.keys}?.name ?: restoredWorkflow(group,
            choices.getString("task:${group.key}",null),choices.getString("use:${group.key}",null),workflowOrder).name)}
    val workflow=workflows.keys.firstOrNull {it.name==taskKey} ?: workflows.keys.first()
    val routes=workflows.getValue(workflow)
    var savedRoute by rememberSaveable(group.key,workflow) {mutableStateOf(preferred?.takeIf {candidate -> routes.any {modelBookmark(it)==modelBookmark(candidate)}}?.let(::modelBookmark)
        ?: choices.getString("task-route:${group.key}:${workflow.name}",null)
        ?: choices.getString("route:${group.key}:${choices.getString("use:${group.key}",null)}",null))}
    val priceRevision by AtlasPublishedPrice.revision.collectAsState()
    val selected=routes.firstOrNull {modelBookmark(it)==savedRoute} ?: routes.first()
    LaunchedEffect(group.key,modelBookmark(selected),workflow) {onSelectionChanged(selected,workflow)}
    val logicalRoutes=remember(routes,selected,workflow) {
        com.vidadomu.nexus.routing.logicalVariantRepresentatives(routes,selected,workflow in setOf(CatalogWorkflow.ANIMATE,CatalogWorkflow.FRAMES,CatalogWorkflow.EDIT_IMAGE,CatalogWorkflow.REFERENCES))
    }
    // Vidal 01-11 (vídeo, 05:11): el mismo modelo publicado por Atlas Cloud y por Crun es UNA variante con un selector de
    // proveedor, no dos pantallas. Cada ruta conserva su id exacto; solo se agrupan para elegir.
    val variantClusters=remember(logicalRoutes) {providerTwinClusters(logicalRoutes)}
    val selectedCluster=variantClusters.firstOrNull {cluster -> cluster.any {modelBookmark(it)==modelBookmark(selected)}} ?: listOf(selected)
    val variantChoices=remember(variantClusters,selected) {variantClusters.map {cluster -> cluster.firstOrNull {modelBookmark(it)==modelBookmark(selected)} ?: cluster.first()}}
    fun chooseRoute(route:NexusModel) {
        savedRoute=modelBookmark(route)
        choices.edit().putString("task-route:${group.key}:${workflow.name}",savedRoute).apply()
    }
    var variantsOpen by remember {mutableStateOf(false)}
    val estimate=rememberPriceEstimate(selected)
    val release=remember(selected) {modelRelease(selected)}
    val colors=MaterialTheme.colorScheme
    var verifiedModels by remember(group.key) {mutableStateOf(mapOf<String,NexusModel>())}
    val verifiedSelected=verifiedModels[modelBookmark(selected)] ?: selected.takeIf(::hasProviderContract)
    LaunchedEffect(selected) {
        loadVerifiedContract(selected)?.let {verifiedModels=verifiedModels+(modelBookmark(selected) to it)}
    }
    val labels=rememberCatalogLabels()
    var editLabels by remember {mutableStateOf(false)}
    // Image inputs come from the model's own contract; the images chosen here travel with «Crear con este modelo».
    val imageField=remember(selected,verifiedSelected) {
        com.vidadomu.nexus.ui.create.CanvasInputPlan.fields(verifiedSelected ?: selected).firstOrNull {it.mime.startsWith("image/")}
    }
    val localMedia=rememberSaveable(stateSaver=CatalogMediaSelectionSaver) {mutableStateOf(
        ArtBoxInbox.images.value.map {CatalogMediaReference(it.first.toString(),it.second)})}
    val selection=mediaSelection ?: localMedia
    var pickedImages by selection
    val inbox=ArtBoxInbox.images.value
    LaunchedEffect(inbox) {
        if(mediaSelection==null) pickedImages=mergeCatalogMediaReferences(pickedImages,
            inbox.map {CatalogMediaReference(it.first.toString(),it.second)})
    }
    val mediaPartition=remember(pickedImages,imageField) {catalogMediaPartition(pickedImages,imageField)}
    fun addImages(uris:List<android.net.Uri>) {
        val extra=uris.map {CatalogMediaReference(it.toString(),context.contentResolver.getType(it) ?: "image/jpeg")}
        pickedImages=addCatalogMediaReferences(pickedImages,extra,imageField?.maximum==1)
    }
    // Vidal 01-11 (vídeo, 05:55): un solo botón. La hoja de la Biblioteca ya incluye «Importar» desde el móvil.
    val libraryPicker=com.vidadomu.nexus.assistant.rememberProjectMediaPicker {uris -> addImages(uris)}

    Scaffold(modifier=modifier,containerColor=colors.background,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        com.vidadomu.nexus.ui.components.FloatingActionDock {
            Column(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.space16).padding(bottom=NexusTokens.space12),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                    Column(Modifier.weight(1f)) {
                        Text(estimate.amount,style=MaterialTheme.typography.titleMedium,
                            fontWeight=FontWeight.SemiBold,color=if(estimate.hasPrice) colors.onSurface else colors.onSurfaceVariant)
                        Text(if(estimate.hasPrice) "El total depende de tus ajustes" else estimate.details.ifBlank {"Precio por confirmar: no equivale a coste cero"},style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant,
                            maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    if(!estimate.hasPrice) estimate.retry?.let {retry -> IconButton(onClick=retry) {Icon(Icons.Default.Refresh,"Volver a consultar el precio")}}
                }
                Button(onClick={
                    val resources=pickedImages.map {android.net.Uri.parse(it.uriString) to it.mimeType}
                    if(resources.isNotEmpty() && onUseWithMedia!=null) {
                        onUseWithMedia(selected,resources)
                        ArtBoxInbox.images.value=emptyList()
                    } else {
                        if(resources.isNotEmpty()) ArtBoxInbox.images.value=resources
                        onUse(selected)
                        if(resources.isEmpty()) ArtBoxInbox.images.value=emptyList()
                    }},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=NexusTokens.field) {
                    Text("Crear con este modelo",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(NexusTokens.iconMedium))
                }
            }
        }
    }) {padding ->
        // Vidal 01-11 (vídeo, 03:15 y 06:36): todo cabe en una pantalla, sin desplazarse. Fuera descripciones genéricas,
        // «Información y evaluación», evaluaciones propias y «Detalles del modelo». El scroll queda solo como red para pantallas pequeñas.
        Column(Modifier.fillMaxSize().padding(top=padding.calculateTopPadding()).verticalScroll(rememberScrollState())
            .padding(horizontal=NexusTokens.space16).padding(top=NexusTokens.space8,bottom=padding.calculateBottomPadding()+NexusTokens.space8),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            familySelector()
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                CatalogGlyph(familyTitle?.let {"family:$it"} ?: "group:"+group.key,48.dp,labels) {size -> BrandLogo(family=catalogFamily(selected),modelId=selected.id,size=size,bare=true)}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(familyTitle?.let {labels.name("family:$it",it)} ?: labels.name("group:"+group.key,group.title),
                        style=MaterialTheme.typography.titleLarge,color=colors.onSurface,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(listOfNotNull(creator(selected).takeIf {it.isNotBlank()},release?.label).joinToString(" · "),
                        style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                IconButton(onClick={editLabels=true}) {Icon(Icons.Default.Edit,"Cambiar nombre, icono y tamaño",tint=colors.onSurfaceVariant)}
                IconToggleButton(checked=modelBookmark(selected) in favorites,onCheckedChange={toggleFavorite(selected)}) {
                    Icon(if(modelBookmark(selected) in favorites) Icons.Default.Star else Icons.Default.StarBorder,
                        if(modelBookmark(selected) in favorites) "Quitar de favoritos" else "Añadir a favoritos",tint=if(modelBookmark(selected) in favorites) colors.primary else colors.onSurfaceVariant)
                }
            }
            if(workflows.size>1) FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                workflows.keys.forEach {task ->
                    val checked=task==workflow
                    // Slim pills: the active one is outlined in the accent colour instead of becoming a large block.
                    Surface(shape=NexusTokens.filter,color=if(checked) colors.primary.copy(alpha=.14f) else Color.Transparent,
                        border=androidx.compose.foundation.BorderStroke(1.dp,if(checked) colors.primary else colors.outlineVariant),
                        modifier=Modifier.selectable(selected=checked,role=Role.Tab,onClick={taskKey=task.name;choices.edit().putString("task:${group.key}",task.name).apply()})) {
                        Text(task.label,Modifier.heightIn(min=40.dp).padding(horizontal=NexusTokens.space16,vertical=9.dp),style=MaterialTheme.typography.labelLarge,
                            fontWeight=if(checked) FontWeight.SemiBold else FontWeight.Normal,color=if(checked) colors.primary else colors.onSurfaceVariant)
                    }
                }
            }
            // La variante solo aparece cuando hay una elección real; el proveedor, como un selector pequeño en la misma pantalla.
            if(variantChoices.size>1) Surface(color=colors.surface,shape=NexusTokens.field,modifier=Modifier.fillMaxWidth()
                .clickable(role=Role.Button,onClickLabel="Cambiar variante") {variantsOpen=true}) {
                Row(Modifier.padding(horizontal=NexusTokens.space16,vertical=NexusTokens.space12),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                    Text(optionVariantLabel(selected,group.title),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold,
                        maxLines=2,overflow=TextOverflow.Ellipsis)
                    Icon(Icons.Default.UnfoldMore,"${variantChoices.size} variantes",tint=colors.onSurfaceVariant)
                }
            }
            ProviderChoice(selectedCluster,selected,automatic=routes.size>logicalRoutes.size,onChoose={chooseRoute(it)})
            if(imageField!=null || pickedImages.isNotEmpty()) Surface(shape=NexusTokens.card,color=colors.surfaceContainerLow,border=androidx.compose.foundation.BorderStroke(.5.dp,colors.outlineVariant.copy(alpha=.7f)),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(NexusTokens.space12),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            Text(imageField?.label ?: "Archivos conservados",style=MaterialTheme.typography.titleSmall,modifier=Modifier.semantics { heading() })
                            Text(imageField?.let {(if(it.required) "Obligatoria" else "Opcional")+if(it.maximum>1) " · hasta ${it.maximum} imágenes" else " · una imagen"}
                                ?: "Esta variante no admite estas imágenes; se conservan para otra.",
                                style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                        }
                        if(imageField!=null) FilledTonalButton(onClick={libraryPicker.launch(arrayOf("image/*"),imageField.maximum.coerceIn(1,50))},
                            shape=NexusTokens.field,contentPadding=PaddingValues(horizontal=NexusTokens.space12),
                            modifier=Modifier.heightIn(min=NexusTokens.minimumTouch)) {
                            Icon(Icons.Default.Add,null,Modifier.size(NexusTokens.iconSmall));Spacer(Modifier.width(4.dp))
                            Text(if(pickedImages.isEmpty()) "Añadir" else "Más",maxLines=1)
                        }
                    }
                    if(pickedImages.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        items(pickedImages,key={it.uriString}) {resource ->
                            Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                            Box(Modifier.size(72.dp)) {
                                coil.compose.AsyncImage(android.net.Uri.parse(resource.uriString),contentDescription="Imagen elegida",contentScale=androidx.compose.ui.layout.ContentScale.Fit,
                                    modifier=Modifier.fillMaxSize().clip(NexusTokens.thumbnail).background(colors.surfaceContainerHigh))
                                IconButton(onClick={pickedImages=removeCatalogMediaReference(pickedImages,resource.uriString)},Modifier.align(Alignment.TopEnd).size(NexusTokens.minimumTouch)) {
                                    Icon(Icons.Default.Close,"Quitar imagen",Modifier.size(NexusTokens.iconSmall).background(Color.Black.copy(alpha=.55f),androidx.compose.foundation.shape.CircleShape).padding(3.dp),tint=Color.White)
                                }
                            }
                            if(resource in mediaPartition.retained) Text("Conservado",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            onHide?.let {action -> TextButton(onClick={action(selected)}) {Text("Quitar esta variante",color=colors.onSurfaceVariant)}}
        }
    }
    // The description line was removed from this page, so its editor only keeps name, icon and size here.
    if(editLabels) CatalogLabelEditor("group:"+group.key,group.title,workflow.summary,labels,onDismiss={editLabels=false},withDescription=false)
    if(variantsOpen) {
    LaunchedEffect(routes) {
        routes.forEach {route -> if(modelBookmark(route) !in verifiedModels) loadVerifiedContract(route)?.let {verifiedModels=verifiedModels+(modelBookmark(route) to it)}}
    }
    val evidence=remember(routes,verifiedModels,priceRevision) {routes.map {route -> VariantEvidence(route,verifiedModels[modelBookmark(route)] ?: route.takeIf(::hasProviderContract),AtlasPublishedPrice.cached(route))}}
    val differences=remember(evidence) {variantDifferences(evidence)}
    ModalBottomSheet(onDismissRequest={variantsOpen=false},containerColor=colors.surface,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.space16),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
            Text("Elige una variante",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.semantics { heading() })
            Text("${group.title} · ${workflow.label.lowercase()}",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f,false),contentPadding=PaddingValues(horizontal=NexusTokens.space16,vertical=NexusTokens.space16)) {
            items(variantChoices,key=::modelBookmark) {route ->
                val checked=modelBookmark(route)==modelBookmark(selected)
                val cluster=variantClusters.firstOrNull {c -> c.any {modelBookmark(it)==modelBookmark(route)}} ?: listOf(route)
                Column {
                    Row(Modifier.fillMaxWidth().selectable(selected=checked,role=Role.RadioButton,onClick={
                        chooseRoute(route);variantsOpen=false
                    }).padding(vertical=NexusTokens.space12),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                            Text(optionVariantLabel(route,group.title),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(cluster.map {providerName(it.providerId)}.distinct().joinToString(" · "),style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                            val routeEvidence=evidence.firstOrNull {modelBookmark(it.model)==modelBookmark(route)}
                            routeEvidence?.tariff?.takeIf {it.unit!=null && it.usd.isFinite() && it.usd>=0}?.let {price ->
                                Text("Desde ${app.currencyManager.formatUnitPrice(price.usd)} / ${price.unit}",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                            }
                            differences[modelBookmark(route)]?.takeIf {it!=UNDOCUMENTED_DIFFERENCE}?.let {difference ->
                                Text(difference,style=MaterialTheme.typography.bodySmall,color=colors.onSurface,maxLines=2,overflow=TextOverflow.Ellipsis)
                            }
                        }
                        RadioButton(selected=checked,onClick=null)
                    }
                    HorizontalDivider(color=colors.outlineVariant.copy(alpha=.4f))
                }
            }
        }
    }
}
}

/** One small selector when the same variant is published by several providers; otherwise a single quiet line. */
@Composable
private fun ProviderChoice(cluster:List<NexusModel>,selected:NexusModel,automatic:Boolean,onChoose:(NexusModel)->Unit) {
    val colors=MaterialTheme.colorScheme
    val providers=cluster.distinctBy {it.providerId}
    if(providers.size<=1) {
        Text(if(automatic) "Proveedor automático según tus ajustes" else "Proveedor · ${providerName(selected.providerId)}",
            style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
        return
    }
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
        Text("Proveedor",style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)
        providers.forEach {route ->
            val checked=route.providerId==selected.providerId
            Surface(shape=NexusTokens.filter,color=if(checked) colors.primary.copy(alpha=.14f) else Color.Transparent,
                border=androidx.compose.foundation.BorderStroke(1.dp,if(checked) colors.primary else colors.outlineVariant),
                modifier=Modifier.selectable(selected=checked,role=Role.RadioButton,onClick={if(!checked) onChoose(route)})) {
                Text(providerName(route.providerId),Modifier.heightIn(min=40.dp).padding(horizontal=NexusTokens.space12,vertical=9.dp),
                    style=MaterialTheme.typography.labelLarge,fontWeight=if(checked) FontWeight.SemiBold else FontWeight.Normal,
                    color=if(checked) colors.primary else colors.onSurfaceVariant)
            }
        }
    }
}

/** Public schema already used by the Lienzo; failures leave the facts as «Sin dato». */
private suspend fun loadVerifiedContract(model:NexusModel):NexusModel? {
    if(hasProviderContract(model)) return model
    if(model.providerId!=AtlasCloudProvider.PROVIDER_ID || model.schemaUrl==null) return null
    return try {NativeModelSchema.load(model).model} catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(e:Exception) {null}
}
