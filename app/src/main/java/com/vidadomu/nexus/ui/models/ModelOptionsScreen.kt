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

/** A quiet model brief with one action, and secondary routes in a separate sheet. */
@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
internal fun ModelOptionsScreen(group:CatalogModelGroup,favorites:Set<String>,toggleFavorite:(NexusModel)->Unit,
    onUse:(NexusModel)->Unit,onHide:((NexusModel)->Unit)?=null,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val app=context.applicationContext as NexusApplication
    val choices=remember {context.getSharedPreferences("catalog_choices_v1",0)}
    val workflows=remember(group) {workflowRoutes(group)}
    var taskKey by rememberSaveable(group.key) {mutableStateOf(restoredWorkflow(group,
        choices.getString("task:${group.key}",null),choices.getString("use:${group.key}",null)).name)}
    val workflow=workflows.keys.firstOrNull {it.name==taskKey} ?: workflows.keys.first()
    val routes=workflows.getValue(workflow)
    var savedRoute by rememberSaveable(group.key,workflow) {mutableStateOf(choices.getString("task-route:${group.key}:${workflow.name}",null)
        ?: choices.getString("route:${group.key}:${choices.getString("use:${group.key}",null)}",null))}
    val selected=routes.firstOrNull {modelBookmark(it)==savedRoute} ?: routes.sortedWith(
        compareByDescending<NexusModel> {AtlasPublishedPrice.cached(it)!=null || it.priceUnitVerified}
            .thenByDescending {it.providerPriority}.thenByDescending {it.variantRank}.thenBy {it.id}
    ).first()
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
    val publishedUnit=AtlasPublishedPrice.cached(selected)?.unit
    val facts=remember(selected,verifiedSelected,publishedUnit) {modelFactSheet(selected,verifiedSelected,publishedUnit)}

    Scaffold(modifier=modifier,containerColor=colors.background,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        Surface(color=colors.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.space16).padding(top=NexusTokens.space12,bottom=NexusTokens.space12),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                HorizontalDivider(color=colors.outlineVariant.copy(alpha=.5f))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                    Column(Modifier.weight(1f)) {
                        Text(estimate.amount,style=MaterialTheme.typography.titleMedium,
                            fontWeight=FontWeight.SemiBold,color=if(estimate.hasPrice) colors.onSurface else colors.onSurfaceVariant)
                        Text(if(estimate.hasPrice) "Confirma el total con tus ajustes en el lienzo" else estimate.details.ifBlank {"Precio por confirmar: no equivale a coste cero"},style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                    }
                    if(!estimate.hasPrice) estimate.retry?.let {retry -> IconButton(onClick=retry) {Icon(Icons.Default.Refresh,"Volver a consultar el precio")}}
                }
                Button(onClick={onUse(selected)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=NexusTokens.field) {
                    Text("Usar ${group.title}",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(20.dp))
                }
            }
        }
    }) {padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal=NexusTokens.space16).padding(top=NexusTokens.space12,bottom=NexusTokens.space16),verticalArrangement=Arrangement.spacedBy(NexusTokens.space24)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                BrandLogo(family=catalogFamily(selected),modelId=selected.id,size=NexusTokens.space32,containerSize=NexusTokens.space32)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                    Text(group.title,style=MaterialTheme.typography.titleMedium,color=colors.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis)
                    release?.let {Text(it.label,style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                }
                IconToggleButton(checked=modelBookmark(selected) in favorites,onCheckedChange={toggleFavorite(selected)}) {
                    Icon(if(modelBookmark(selected) in favorites) Icons.Default.Star else Icons.Default.StarBorder,
                        if(modelBookmark(selected) in favorites) "Quitar de favoritos" else "Añadir a favoritos",tint=if(modelBookmark(selected) in favorites) colors.primary else colors.onSurfaceVariant)
                }
            }
            Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                if(workflows.size>1) FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    workflows.keys.forEach {task ->
                        val checked=task==workflow
                        Surface(shape=NexusTokens.field,color=if(checked) colors.primary.copy(alpha=.12f) else colors.surface,
                            modifier=Modifier.selectable(selected=checked,role=Role.Tab,onClick={taskKey=task.name;choices.edit().putString("task:${group.key}",task.name).apply()})) {
                            Text(task.label,Modifier.heightIn(min=48.dp).padding(horizontal=NexusTokens.space16,vertical=NexusTokens.space12),style=MaterialTheme.typography.labelLarge,
                                fontWeight=if(checked) FontWeight.SemiBold else FontWeight.Normal,color=if(checked) colors.primary else colors.onSurfaceVariant)
                        }
                    }
                }
                Text(workflow.summary,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
                guide.strength.takeUnless {it.startsWith("Uso:") || it.contains("varias imágenes") && workflow==CatalogWorkflow.CREATE_IMAGE}?.let {
                    Text(it,style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                }
                focusAdvice?.let { advice ->
                    Text("Bueno para: ${advice.good}",style=MaterialTheme.typography.bodyMedium,color=colors.onSurface)
                    Text("Evítalo si: ${advice.avoid}",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                }
            }
            Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                Text("Qué admite y cómo se cobra",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant,modifier=Modifier.semantics { heading() })
                facts.forEach {fact ->
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants=true) {},horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                        Text(fact.label,Modifier.widthIn(min=88.dp),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                        Text(fact.value ?: MISSING_FACT,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,
                            color=if(fact.value==null) colors.onSurfaceVariant else colors.onSurface)
                    }
                }
                if(verifiedSelected==null) Text("Entradas y límites sin verificar: el esquema del proveedor aún no se ha leído.",
                    style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            }
            Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                Text("Variante y proveedor",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant,modifier=Modifier.semantics { heading() })
                Surface(color=colors.surface,shape=NexusTokens.field,modifier=Modifier.fillMaxWidth()
                    .clickable(enabled=routes.size>1,role=Role.Button,onClickLabel="Cambiar variante y proveedor") {variantsOpen=true}) {
                    Row(Modifier.padding(NexusTokens.space16),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                            Text(optionVariantLabel(selected,group.title),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(providerName(selected.providerId),style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                        }
                        if(routes.size>1) Icon(Icons.Default.UnfoldMore,"${routes.size} opciones disponibles",tint=colors.onSurfaceVariant)
                    }
                }
            }
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
                    release?.let {info -> TextButton(onClick={uriHandler.openUri(info.source)}) {Text("Fuente del lanzamiento",color=colors.onSurfaceVariant)}}
                    guide.source?.let {source -> TextButton(onClick={uriHandler.openUri(source)}) {Text("Ficha y tarifas del proveedor",color=colors.onSurfaceVariant)}}
                }
                onHide?.let {action -> TextButton(onClick={action(selected)}) {Text("Quitar esta variante",color=colors.onSurfaceVariant)}}
            }
        }
    }
    if(variantsOpen) {
    LaunchedEffect(routes) {
        routes.forEach {route -> if(modelBookmark(route) !in verifiedModels) loadVerifiedContract(route)?.let {verifiedModels=verifiedModels+(modelBookmark(route) to it)}}
    }
    val evidence=routes.map {route -> VariantEvidence(route,verifiedModels[modelBookmark(route)] ?: route.takeIf(::hasProviderContract),AtlasPublishedPrice.cached(route))}
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
            items(routes.sortedWith(compareBy<NexusModel> {providerName(it.providerId)}.thenByDescending {it.variantRank}.thenBy {it.id}),key=::modelBookmark) {route ->
                val checked=modelBookmark(route)==modelBookmark(selected)
                Column {
                    Row(Modifier.fillMaxWidth().selectable(selected=checked,role=Role.RadioButton,onClick={
                        savedRoute=modelBookmark(route);choices.edit().putString("task-route:${group.key}:${workflow.name}",savedRoute).apply();variantsOpen=false
                    }).padding(vertical=NexusTokens.space16),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                            Text(optionVariantLabel(route,group.title),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(providerName(route.providerId),style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
                            val routeEvidence=evidence.first {modelBookmark(it.model)==modelBookmark(route)}
                            routeEvidence.tariff?.takeIf {it.unit!=null && it.usd.isFinite() && it.usd>=0}?.let {price ->
                                Text("Desde ${app.currencyManager.formatUnitPrice(price.usd)} / ${price.unit}",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                            }
                            val example=remember(routeEvidence) {exampleCost(route,routeEvidence.tariff,routeEvidence.verified)}
                            Text(example.usd?.let {"≈ ${app.currencyManager.formatCost(it)} · ${example.note}"} ?: example.note,
                                style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                            if(routes.size>1) Text(differences[modelBookmark(route)] ?: UNDOCUMENTED_DIFFERENCE,style=MaterialTheme.typography.bodySmall,
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
