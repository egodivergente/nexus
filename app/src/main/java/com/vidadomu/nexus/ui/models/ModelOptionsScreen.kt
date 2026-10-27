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
    onUseWithMedia:((NexusModel,List<Pair<android.net.Uri,String>>)->Unit)?=null) {
    val context=LocalContext.current
    val app=context.applicationContext as NexusApplication
    val choices=remember {context.getSharedPreferences("catalog_choices_v1",0)}
    val rules by com.vidadomu.nexus.settings.RuntimePreferences.get(context).state.collectAsState()
    val workflowOrder=rules.value(if(com.vidadomu.nexus.core.model.Modality.VIDEO in group.routes.first().modalities) "catalog.videoOrder" else "catalog.imageOrder").split(',').map {it.trim()}
    val workflows=remember(group,workflowOrder) {workflowRoutes(group,workflowOrder)}
    var taskKey by rememberSaveable(group.key) {mutableStateOf(restoredWorkflow(group,
        choices.getString("task:${group.key}",null),choices.getString("use:${group.key}",null),workflowOrder).name)}
    val workflow=workflows.keys.firstOrNull {it.name==taskKey} ?: workflows.keys.first()
    val routes=workflows.getValue(workflow)
    var savedRoute by rememberSaveable(group.key,workflow) {mutableStateOf(choices.getString("task-route:${group.key}:${workflow.name}",null)
        ?: choices.getString("route:${group.key}:${choices.getString("use:${group.key}",null)}",null))}
    val priceRevision by AtlasPublishedPrice.revision.collectAsState()
    val selected=routes.firstOrNull {modelBookmark(it)==savedRoute} ?: routes.first()
    val logicalRoutes=remember(routes,selected,workflow) {
        com.vidadomu.nexus.routing.logicalVariantRepresentatives(routes,selected,workflow in setOf(CatalogWorkflow.ANIMATE,CatalogWorkflow.FRAMES,CatalogWorkflow.EDIT_IMAGE,CatalogWorkflow.REFERENCES))
    }
    var infoOpen by rememberSaveable(group.key) {mutableStateOf(false)}
    var contractOpen by rememberSaveable(group.key) {mutableStateOf(false)}
    var variantsOpen by remember {mutableStateOf(false)}
    var details by rememberSaveable(group.key) {mutableStateOf(false)}
    val estimate=rememberPriceEstimate(selected)
    val guide=remember(selected) {modelGuide(selected)}
    val focusAdvice=remember(selected) {CatalogFocus.forModel(selected)}
    val release=remember(selected) {modelRelease(selected)}
    val colors=MaterialTheme.colorScheme
    val uriHandler=LocalUriHandler.current
    var verifiedModels by remember(group.key) {mutableStateOf(mapOf<String,NexusModel>())}
    val verifiedSelected=verifiedModels[modelBookmark(selected)] ?: selected.takeIf(::hasProviderContract)
    LaunchedEffect(selected) {
        loadVerifiedContract(selected)?.let {verifiedModels=verifiedModels+(modelBookmark(selected) to it)}
    }
    val publishedUnit=rememberPublishedPrice(selected)?.unit
    val facts=remember(selected,verifiedSelected,publishedUnit) {modelFactSheet(selected,verifiedSelected,publishedUnit)}
    val labels=rememberCatalogLabels()
    var editLabels by remember {mutableStateOf(false)}
    // Image inputs come from the model's own contract; the images chosen here travel with «Crear con este modelo».
    val imageField=remember(selected,verifiedSelected) {
        com.vidadomu.nexus.ui.create.CanvasInputPlan.fields(verifiedSelected ?: selected).firstOrNull {it.mime.startsWith("image/")}
    }
    // Images sent from the Library arrive already picked (up to what this model accepts).
    var pickedImages by remember(group.key,modelBookmark(selected)) {mutableStateOf(
        if(imageField==null) emptyList() else ArtBoxInbox.images.value.take((imageField.maximum).coerceIn(1,50)))}
    LaunchedEffect(imageField,modelBookmark(selected)) {
        if(imageField!=null && pickedImages.isEmpty()) pickedImages=ArtBoxInbox.images.value.take(imageField.maximum.coerceIn(1,50))
    }
    fun addImages(uris:List<android.net.Uri>) {
        val limit=(imageField?.maximum ?: 1).coerceIn(1,50)
        val extra=uris.map {it to (context.contentResolver.getType(it) ?: "image/jpeg")}
        pickedImages=(if(limit==1) extra.take(1) else (pickedImages+extra).distinctBy {it.first}.take(limit))
    }
    val galleryPicker=androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(maxItems=(imageField?.maximum ?: 1).coerceIn(2,50))) {uris -> addImages(uris)}
    val singleGalleryPicker=androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) {uri -> if(uri!=null) addImages(listOf(uri))}
    val libraryPicker=com.vidadomu.nexus.assistant.rememberProjectMediaPicker {uris -> addImages(uris)}

    Scaffold(modifier=modifier,containerColor=colors.background,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        com.vidadomu.nexus.ui.components.FloatingActionDock {
            Column(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.space16).padding(bottom=NexusTokens.space12),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                    Column(Modifier.weight(1f)) {
                        Text(estimate.amount,style=MaterialTheme.typography.titleMedium,
                            fontWeight=FontWeight.SemiBold,color=if(estimate.hasPrice) colors.onSurface else colors.onSurfaceVariant)
                        Text(if(estimate.hasPrice) "Confirma el total con tus ajustes en Lanzadera" else estimate.details.ifBlank {"Precio por confirmar: no equivale a coste cero"},style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    }
                    if(!estimate.hasPrice) estimate.retry?.let {retry -> IconButton(onClick=retry) {Icon(Icons.Default.Refresh,"Volver a consultar el precio")}}
                }
                Button(onClick={
                    ArtBoxInbox.images.value=emptyList()
                    if(pickedImages.isNotEmpty() && onUseWithMedia!=null) onUseWithMedia(selected,pickedImages) else onUse(selected)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=NexusTokens.field) {
                    Text("Crear con este modelo",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(20.dp))
                }
            }
        }
    }) {padding ->
        // El contenido pasa por debajo del botón flotante y se desvanece; no termina en un corte recto.
        Column(Modifier.fillMaxSize().padding(top=padding.calculateTopPadding()).verticalScroll(rememberScrollState())
            .padding(horizontal=NexusTokens.space16).padding(top=NexusTokens.space12,bottom=padding.calculateBottomPadding()+NexusTokens.space8),verticalArrangement=Arrangement.spacedBy(NexusTokens.space16)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                CatalogGlyph("group:"+group.key,56.dp,labels) {size -> BrandLogo(family=catalogFamily(selected),modelId=selected.id,size=size,bare=true)}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                    Text(labels.name("group:"+group.key,group.title),style=MaterialTheme.typography.titleLarge,color=colors.onSurface,maxLines=3,overflow=TextOverflow.Clip)
                    Text(listOfNotNull(creator(selected).takeIf {it.isNotBlank()},release?.label).joinToString(" · "),
                        style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                }
                IconButton(onClick={editLabels=true}) {Icon(Icons.Default.Edit,"Cambiar nombre, descripción, icono y tamaño",tint=colors.onSurfaceVariant)}
                IconToggleButton(checked=modelBookmark(selected) in favorites,onCheckedChange={toggleFavorite(selected)}) {
                    Icon(if(modelBookmark(selected) in favorites) Icons.Default.Star else Icons.Default.StarBorder,
                        if(modelBookmark(selected) in favorites) "Quitar de favoritos" else "Añadir a favoritos",tint=if(modelBookmark(selected) in favorites) colors.primary else colors.onSurfaceVariant)
                }
            }
            Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
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
                Text(labels.description("group:"+group.key,workflow.summary),style=MaterialTheme.typography.bodyLarge)
                guide.strength.takeUnless {it.startsWith("Uso:") || it.contains("varias imágenes") && workflow==CatalogWorkflow.CREATE_IMAGE}?.let {
                    Text(it,style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                }
                focusAdvice?.let { advice ->
                    Text("Bueno para: ${advice.good}",style=MaterialTheme.typography.bodyMedium,color=colors.onSurface)
                    Text("Evítalo si: ${advice.avoid}",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                }
            }
            Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                Text("Variante",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant,modifier=Modifier.semantics { heading() })
                Surface(color=colors.surface,shape=NexusTokens.field,modifier=Modifier.fillMaxWidth()
                    .clickable(enabled=logicalRoutes.size>1,role=Role.Button,onClickLabel="Cambiar variante") {variantsOpen=true}) {
                    Row(Modifier.padding(NexusTokens.space16),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                            Text(optionVariantLabel(selected,group.title),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=3,overflow=TextOverflow.Clip)
                            Text(if(routes.size>logicalRoutes.size) "Proveedor automático según tus ajustes" else providerName(selected.providerId),style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                        }
                        if(logicalRoutes.size>1) Icon(Icons.Default.UnfoldMore,"${logicalRoutes.size} opciones disponibles",tint=colors.onSurfaceVariant)
                    }
                }
            }
            if(imageField!=null) Surface(shape=NexusTokens.card,color=colors.surfaceContainerLow,border=androidx.compose.foundation.BorderStroke(.5.dp,colors.outlineVariant.copy(alpha=.7f)),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(NexusTokens.space16),verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(imageField.label,style=MaterialTheme.typography.titleSmall,modifier=Modifier.semantics { heading() })
                        Text((if(imageField.required) "Obligatoria" else "Opcional")+if(imageField.maximum>1) " · hasta ${imageField.maximum} imágenes" else " · una imagen",
                            style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    }
                    if(pickedImages.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        items(pickedImages,key={it.first.toString()}) {(uri,_)->
                            Box(Modifier.size(88.dp)) {
                                coil.compose.AsyncImage(uri,contentDescription="Imagen elegida",contentScale=androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier=Modifier.fillMaxSize().clip(NexusTokens.thumbnail))
                                IconButton(onClick={pickedImages=pickedImages.filterNot {it.first==uri}},Modifier.align(Alignment.TopEnd).size(NexusTokens.minimumTouch)) {
                                    Icon(Icons.Default.Close,"Quitar imagen",Modifier.size(18.dp).background(Color.Black.copy(alpha=.55f),androidx.compose.foundation.shape.CircleShape).padding(3.dp),tint=Color.White)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        OutlinedButton(onClick={
                            val request=androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)
                            if(imageField.maximum>1) galleryPicker.launch(request) else singleGalleryPicker.launch(request)
                        },modifier=Modifier.weight(1f).heightIn(min=NexusTokens.minimumTouch),shape=NexusTokens.field) {
                            Icon(Icons.Default.AddPhotoAlternate,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Galería",maxLines=1)
                        }
                        OutlinedButton(onClick={libraryPicker.launch(arrayOf("image/*"),imageField.maximum.coerceIn(1,50))},
                            modifier=Modifier.weight(1f).heightIn(min=NexusTokens.minimumTouch),shape=NexusTokens.field) {
                            Icon(Icons.Default.PhotoLibrary,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Biblioteca",maxLines=1)
                        }
                    }
                }
            }
            TextButton(onClick={infoOpen=true}) {Icon(Icons.Default.Info,null);Spacer(Modifier.width(8.dp));Text("Información y evaluación")}
            Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(role=Role.Button,onClickLabel=if(details) "Plegar detalles" else "Desplegar detalles") {details=!details}
                    .semantics { stateDescription=if(details) "Desplegado" else "Plegado" },verticalAlignment=Alignment.CenterVertically) {
                    Text("Detalles del modelo",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                    Icon(if(details) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null,tint=colors.onSurfaceVariant)
                }
                if(details) {
                    Text(guide.purpose,style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                    if(estimate.details.isNotBlank()) Text(estimate.details,style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    Text(guide.note,style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    Text(selected.id,style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    com.vidadomu.nexus.routing.RoutingDiagnostics.latest(context)?.let { diagnostic ->
                        Text("Diagnóstico del último enrutamiento",style=MaterialTheme.typography.labelLarge)
                        Text(diagnostic,style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    }
                    release?.let {info -> TextButton(onClick={uriHandler.openUri(info.source)}) {Text("Fuente del lanzamiento",color=colors.onSurfaceVariant)}}
                    guide.source?.let {source -> TextButton(onClick={uriHandler.openUri(source)}) {Text("Ficha y tarifas del proveedor",color=colors.onSurfaceVariant)}}
                }
                onHide?.let {action -> TextButton(onClick={action(selected)}) {Text("Quitar esta variante",color=colors.onSurfaceVariant)}}
            }
        }
    }
    if(infoOpen) ModalBottomSheet(onDismissRequest={infoOpen=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(com.vidadomu.nexus.ui.create.canvasModelIdentity(selected).title,style=MaterialTheme.typography.titleLarge)
            ModelEvaluation(verifiedSelected ?: selected,group.routes)
            val promptCapability=verifiedSelected?.let(com.vidadomu.nexus.settings.ModelCapabilities::prompt)
            TextButton(onClick={contractOpen=!contractOpen}) {Text(if(contractOpen) "Ocultar contrato y límites" else "Contrato, límites y coste")}
            if(contractOpen) Surface(shape=NexusTokens.card,color=colors.surfaceContainerLow,border=androidx.compose.foundation.BorderStroke(.5.dp,colors.outlineVariant.copy(alpha=.7f)),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(NexusTokens.space16),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                Text("Entradas, límites y coste",style=MaterialTheme.typography.titleSmall,modifier=Modifier.semantics { heading() })
                if(selected.promptAccepted) Row(Modifier.fillMaxWidth().semantics(mergeDescendants=true) {},horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                    Text("Prompt",Modifier.weight(.42f),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    Text(promptCapability?.let {"Hasta ${it.maximum} ${it.unitLabel}"} ?: "Máximo no verificado",Modifier.weight(.58f),style=MaterialTheme.typography.bodyMedium,
                        color=if(promptCapability==null) colors.onSurfaceVariant else colors.onSurface)
                }
                facts.forEach {fact ->
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants=true) {},horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                        Text(fact.label,Modifier.weight(.42f),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                        Text(fact.value ?: MISSING_FACT,Modifier.weight(.58f),style=MaterialTheme.typography.bodyMedium,
                            color=if(fact.value==null) colors.onSurfaceVariant else colors.onSurface)
                    }
                }
                promptCapability?.let {cap->
                    Text("Contrato del proveedor · "+java.text.DateFormat.getDateInstance().format(java.util.Date(cap.verifiedAt)),style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                    if(cap.source.startsWith("https://")) TextButton(onClick={uriHandler.openUri(cap.source)},contentPadding=PaddingValues(0.dp)) {Text("Consultar origen del máximo")}
                }
                if(verifiedSelected==null) Text("Entradas y límites sin verificar: el esquema del proveedor aún no se ha leído.",
                    style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            }
            }
        }
    }
    if(editLabels) CatalogLabelEditor("group:"+group.key,group.title,workflow.summary,labels,onDismiss={editLabels=false})
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
            Text("Mismo ejemplo para todas: ${plainNumber(EXAMPLE_SECONDS)} s de vídeo o 1 imagen con la tarifa base. Es una cuenta orientativa, no una cotización; tus ajustes cambian el total.",
                style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f,false),contentPadding=PaddingValues(horizontal=NexusTokens.space16,vertical=NexusTokens.space16)) {
            items(logicalRoutes.sortedWith(compareBy<NexusModel> {providerName(it.providerId)}.thenByDescending {it.variantRank}.thenBy {it.id}),key=::modelBookmark) {route ->
                val checked=modelBookmark(route)==modelBookmark(selected)
                Column {
                    Row(Modifier.fillMaxWidth().selectable(selected=checked,role=Role.RadioButton,onClick={
                        savedRoute=modelBookmark(route);choices.edit().putString("task-route:${group.key}:${workflow.name}",savedRoute).apply();variantsOpen=false
                    }).padding(vertical=NexusTokens.space16),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                            Text(optionVariantLabel(route,group.title),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium,maxLines=3,overflow=TextOverflow.Clip)
                            Text(providerName(route.providerId),style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                            val routeEvidence=evidence.first {modelBookmark(it.model)==modelBookmark(route)}
                            routeEvidence.tariff?.takeIf {it.unit!=null && it.usd.isFinite() && it.usd>=0}?.let {price ->
                                Text("Desde ${app.currencyManager.formatUnitPrice(price.usd)} / ${price.unit}",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                            }
                            val example=remember(routeEvidence) {exampleCost(route,routeEvidence.tariff,routeEvidence.verified)}
                            Text(example.usd?.let {"≈ ${app.currencyManager.formatCost(it)} · ${example.note}"} ?: example.note,
                                style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                            if(logicalRoutes.size>1) Text(differences[modelBookmark(route)] ?: UNDOCUMENTED_DIFFERENCE,style=MaterialTheme.typography.bodySmall,
                                color=if(differences[modelBookmark(route)]==UNDOCUMENTED_DIFFERENCE) colors.onSurfaceVariant else colors.onSurface)
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

/** Public schema already used by the Lienzo; failures leave the facts as «Sin dato». */
private suspend fun loadVerifiedContract(model:NexusModel):NexusModel? {
    if(hasProviderContract(model)) return model
    if(model.providerId!=AtlasCloudProvider.PROVIDER_ID || model.schemaUrl==null) return null
    return try {NativeModelSchema.load(model).model} catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(e:Exception) {null}
}
