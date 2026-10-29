package com.vidadomu.nexus.ui.create

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.core.model.ModelParameter
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.ui.theme.NexusTokens

/** Layout follows resolved contract fields; the renderer never infers a provider endpoint from a model name. */
@Composable
internal fun CapabilityMediaInputs(fields: List<CanvasInputField>, model: NexusModel?, media: List<AttachedMedia>,
    enabled: Boolean, onAdd: (CanvasInputField, List<Pair<Uri,String>>) -> Unit,
    onRemove: (String) -> Unit, onReorder: (String,Int) -> Unit) {
    val pair=declaredFramePair(fields,model)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val parallel=CanvasInputPlan.pairedFrames(pair,model,maxWidth.value,LocalDensity.current.fontScale)
        // This is presentation order only: native field keys, media order and the request stay unchanged.
        val groups=(fields.filterNot {parallel && it in pair}.map {listOf(it)}+
            if(parallel) listOf(pair) else emptyList()).sortedWith(
                compareBy<List<CanvasInputField>> {group -> group.none {it.required}}
                    .thenBy {group -> group.minOf {field -> when {
                        field.mime.startsWith("image/") && field.maximum==1 -> 0
                        field.mime.startsWith("image/") -> 1
                        field.mime.startsWith("video/") -> 2
                        field.mime.startsWith("audio/") -> 3
                        else -> 4
                    }}})
        Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            groups.forEach {group ->
                if(parallel && group==pair) StartEndFrameSelector(pair,media,enabled,onAdd,onRemove,onReorder)
                else group.forEach {field -> key(model?.providerId,model?.id,field.key) {
                    RegisteredMediaControl(field,media,enabled,{onAdd(field,it)},onRemove,onReorder)
                }}
            }
        }
    }
}

/** Extract only contract-declared scalar frames; a reference array is never mistaken for a frame. */
internal fun declaredFramePair(fields:List<CanvasInputField>,model:NexusModel?):List<CanvasInputField> {
    val images=fields.filter {it.maximum==1 && it.mime=="image/*"}
    val first=images.firstOrNull {it.key==(model?.firstFrameField ?: model?.inputImageField) || it.slot==com.vidadomu.nexus.core.model.MediaSlotType.PRIMARY_IMAGE}
    val last=images.firstOrNull {it.key==model?.lastFrameField || it.slot==com.vidadomu.nexus.core.model.MediaSlotType.LAST_FRAME}
    return if(first!=null && last!=null && first.key!=last.key) listOf(first,last)
        else images.takeIf {CanvasInputPlan.pairedFrames(it,model,Float.MAX_VALUE,1f)}.orEmpty()
}

@Composable
internal fun StartEndFrameSelector(fields:List<CanvasInputField>,media:List<AttachedMedia>,enabled:Boolean,
    onAdd:(CanvasInputField,List<Pair<Uri,String>>)->Unit,onRemove:(String)->Unit,onReorder:(String,Int)->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
        fields.forEach {field -> Box(Modifier.weight(1f)) {
            val overrides=LocalDynamicControlRenderers.current
            val custom=overrides.inputKeys[field.key] ?: overrides.inputKinds[DynamicControlRegistry.inputKind(field)]
            if(custom!=null) custom(field,media,enabled,{onAdd(field,it)},onRemove,onReorder)
            else CanvasAttachmentEditor(field,media,enabled,onAdd={onAdd(field,it)},onRemove=onRemove,
                compact=true,onReorder=onReorder,frameColumn=true)
        }}
    }
}

/** 2–4 primary controls stay in view; the rest retain their native values in a collapsible advanced section. */
@Composable
internal fun CapabilityParameterControls(model: NexusModel, primary: List<ModelParameter>, advanced: List<ModelParameter>,
    values: Map<String,String>, onChange: (String,String) -> Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns=if(maxWidth>=NexusTokens.minimumTouch*7 && LocalDensity.current.fontScale<=1.3f) 2 else 1
            Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                primary.chunked(columns).forEach {row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        row.forEach {param -> Box(Modifier.weight(1f)) {
                            key(model.providerId,model.id,param.key) {
                                RegisteredParameterControl(param,values[param.key] ?: param.defaultValue,compact=true) {onChange(param.key,it)}
                            }
                        }}
                        if(row.size<columns) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        AdvancedParameters(model,advanced,values,onChange)
    }
}

@Composable
internal fun AdvancedParameters(model:NexusModel,parameters:List<ModelParameter>,values:Map<String,String>,onChange:(String,String)->Unit) {
    if(parameters.isEmpty()) return
    var expanded by rememberSaveable(model.providerId,model.id) {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
        Row(Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch)
            .semantics {stateDescription=if(expanded) "Desplegado" else "Plegado"}
            .clickable(role=Role.Button,onClickLabel=if(expanded) "Ocultar parámetros avanzados" else "Mostrar parámetros avanzados") {expanded=!expanded},
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            Icon(Icons.Default.Tune,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Avanzado · ${parameters.size}",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
            Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
        }
        if(expanded) parameters.forEach {param -> key(model.providerId,model.id,param.key) {
            RegisteredParameterControl(param,values[param.key] ?: param.defaultValue) {onChange(param.key,it)}
        }}
    }
}

/** Incompatible draft media remains inspectable and removable; these files are excluded from submission. */
@Composable
internal fun ConservedMediaSection(media: List<AttachedMedia>, enabled: Boolean, onRemove: (String)->Unit) {
    if(media.isEmpty()) return
    var expanded by rememberSaveable {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
        Row(Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch)
            .semantics {stateDescription=if(expanded) "Desplegado" else "Plegado"}
            .clickable(role=Role.Button) {expanded=!expanded},verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Default.Inventory2,null, tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(NexusTokens.space8))
            Text("Conservados · ${media.size}",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
            Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
        }
        if(expanded) {
            Text("Estos archivos se conservan en el borrador y no se enviarán con el modelo actual.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                items(media,key={it.id}) {item -> CanvasAttachmentTile(item,item.fileName ?: "Archivo conservado",enabled,
                    thumbnailSize=NexusTokens.minimumTouch*2) {onRemove(item.id)} }
            }
        }
    }
}

/** A bounded summary keeps normal creation on one screen; complete media management lives in its sheet. */
@Composable
internal fun CompactCapabilityMediaInputs(fields:List<CanvasInputField>,model:NexusModel?,media:List<AttachedMedia>,enabled:Boolean,
    onAdd:(CanvasInputField,List<Pair<Uri,String>>)->Unit,onRemove:(String)->Unit,onReorder:(String,Int)->Unit) {
    val pair=declaredFramePair(fields,model)
    val primary=fields.filter {it.required || media.any(it::contains) || it.mime.startsWith("image/")}
    val secondary=fields.filterNot {it in primary}
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density=LocalDensity.current
        val sizing=LocalLauncherContentSizing.current
        val overrides=LocalDynamicControlRenderers.current
        val parallel=CanvasInputPlan.pairedFrames(pair,model,maxWidth.value,density.fontScale)
        val groups=(if(parallel) listOf(pair) else emptyList())+
            primary.filterNot {parallel && it in pair}.map {listOf(it)}+
            secondary.chunked(if(density.fontScale>1.3f) 1 else 2)
        val previewBase=NexusTokens.minimumTouch+NexusTokens.space8
        // A preview can grow only as far as its real column width allows. Empty optional fields stay compact.
        val capacities=groups.map {group ->
            val selected=group.map {field -> media.filter(field::contains)}
            val builtIn=group.all {overrides.inputKeys[it.key]==null && overrides.inputKinds[DynamicControlRegistry.inputKind(it)]==null}
            if(!builtIn || selected.flatten().none {it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")}) 0.dp
            else {
                val width=(maxWidth-NexusTokens.space8*(group.size-1))/group.size-NexusTokens.space16
                selected.minOf {items ->
                    val maximum=when(items.size) {
                        2 -> (width-NexusTokens.space4)/2
                        3 -> (width-NexusTokens.space4*2)/3
                        else -> width
                    }
                    (maximum-previewBase).coerceAtLeast(0.dp)
                }
            }
        }
        val capacityPixels=capacities.map {with(density) {it.roundToPx()}}
        val totalCapacity=capacityPixels.sum()
        val capacity=with(density) {totalCapacity.toDp()}
        SideEffect {sizing.reportMediaCapacity(capacity)}
        var remaining=with(density) {sizing.mediaExtra.roundToPx()}.coerceAtMost(totalCapacity)
        var capacityLeft=totalCapacity
        val extras=capacityPixels.map {pixels ->
            val extra=if(capacityLeft<=0) 0 else minOf(remaining,(remaining.toLong()*pixels/capacityLeft).toInt())
            remaining-=extra;capacityLeft-=pixels
            with(density) {extra.toDp()}
        }
        Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
        groups.forEachIndexed {index,row ->
            val showPreview=capacities[index]>0.dp || row.any {field -> media.any {field.contains(it) && (it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/"))}}
            val previewHeight=if(showPreview) previewBase+extras[index] else null
            Row(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                row.forEach {field -> Box(Modifier.weight(1f)) {
                    key(model?.providerId,model?.id,field.key) {
                        RegisteredSummaryMediaControl(field,media,enabled,{onAdd(field,it)},onRemove,onReorder,previewHeight)
                    }
                }}
            }
        }
        }
    }
}

/** Common settings remain one short line of launchers; an editor opens only for the chosen setting. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompactPrimaryParameterControls(model:NexusModel,parameters:List<ModelParameter>,values:Map<String,String>,onChange:(String,String)->Unit) {
    var editing by remember(model.providerId,model.id) {mutableStateOf<String?>(null)}
    val focusManager=LocalFocusManager.current
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),
        verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
        parameters.forEach {parameter ->
            val title=ParameterPresentation.title(parameter)
            val value=values[parameter.key] ?: parameter.defaultValue
            val kind=DynamicControlRegistry.parameterKind(parameter)
            val icon=when(kind) {
                DynamicParameterKind.DURATION -> Icons.Default.Timer
                DynamicParameterKind.RESOLUTION -> Icons.Default.HighQuality
                DynamicParameterKind.ASPECT_RATIO -> Icons.Default.AspectRatio
                DynamicParameterKind.FPS -> Icons.Default.Speed
                DynamicParameterKind.AUDIO -> Icons.Default.VolumeUp
                DynamicParameterKind.SEED -> Icons.Default.Casino
                else -> Icons.Default.Tune
            }
            val label=when(kind) {
                DynamicParameterKind.DURATION -> value.takeIf {it.isNotBlank()} ?: "Duración"
                DynamicParameterKind.AUDIO -> if(parameter.key.lowercase()=="generate_audio") {
                    if(value=="true") "Con audio" else if(value=="false") "Sin audio" else "Audio auto"
                } else "$title · ${ParameterPresentation.label(parameter,value).primary}"
                else -> ParameterPresentation.label(parameter,value).primary
            }
            FilledTonalButton(onClick={focusManager.clearFocus();editing=parameter.key},shape=NexusTokens.pill,
                contentPadding=PaddingValues(horizontal=NexusTokens.space12,vertical=NexusTokens.space4),
                modifier=Modifier.heightIn(min=NexusTokens.minimumTouch).semantics {contentDescription="$title: $label. Cambiar"}) {
                Icon(icon,null,Modifier.size(NexusTokens.iconSmall))
                Spacer(Modifier.width(NexusTokens.space4))
                Text(label,style=MaterialTheme.typography.labelLarge)
            }
        }
    }
    parameters.firstOrNull {it.key==editing}?.let {parameter -> CanvasSheet(ParameterPresentation.title(parameter),{editing=null}) {
        RegisteredParameterControl(parameter,values[parameter.key] ?: parameter.defaultValue) {onChange(parameter.key,it)}
    }}
}
