package com.vidadomu.nexus.ui.create

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.core.model.ModelParameter
import com.vidadomu.nexus.ui.create.capability.ModelUiCapabilities
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.tabularMoney

internal enum class DynamicParameterKind { DURATION, RESOLUTION, ASPECT_RATIO, FPS, AUDIO, SEED, NATIVE }
internal enum class DynamicInputKind { IMAGE, REFERENCES, VIDEO, AUDIO, NATIVE }
internal typealias ParameterRenderer = @Composable (ModelParameter, String, Boolean, (String)->Unit) -> Unit
internal typealias MediaRenderer = @Composable (CanvasInputField, List<AttachedMedia>, Boolean,
    (List<Pair<Uri,String>>)->Unit, (String)->Unit, (String,Int)->Unit) -> Unit

/** Renderer registration is scoped to a composition, so a screen/extension cannot mutate another screen's controls. */
internal data class DynamicControlRenderers(
    val parameterKeys: Map<String,ParameterRenderer> = emptyMap(),
    val parameterKinds: Map<DynamicParameterKind,ParameterRenderer> = emptyMap(),
    val inputKeys: Map<String,MediaRenderer> = emptyMap(),
    val inputKinds: Map<DynamicInputKind,MediaRenderer> = emptyMap()
)
internal val LocalDynamicControlRenderers=staticCompositionLocalOf {DynamicControlRenderers()}

internal object DynamicControlRegistry {
    /** Only native schema keys determine semantics; model brands and display names never participate. */
    fun parameterKind(parameter: ModelParameter): DynamicParameterKind = when(parameter.key.lowercase()) {
        "duration","duration_seconds","seconds","video_length" -> DynamicParameterKind.DURATION
        "resolution","target_resolution","output_resolution" -> DynamicParameterKind.RESOLUTION
        "aspect_ratio","aspectratio","ratio","size","image_size" -> DynamicParameterKind.ASPECT_RATIO
        "fps","frame_rate","output_fps" -> DynamicParameterKind.FPS
        "generate_audio","audio","sound","enable_audio","keep_original_sound" -> DynamicParameterKind.AUDIO
        "seed","random_seed" -> DynamicParameterKind.SEED
        else -> DynamicParameterKind.NATIVE
    }
    fun inputKind(field: CanvasInputField): DynamicInputKind = when {
        field.mime.startsWith("video/") -> DynamicInputKind.VIDEO
        field.mime.startsWith("audio/") -> DynamicInputKind.AUDIO
        field.mime.startsWith("image/") && field.maximum>1 -> DynamicInputKind.REFERENCES
        field.mime.startsWith("image/") -> DynamicInputKind.IMAGE
        else -> DynamicInputKind.NATIVE
    }
    @Composable fun Provide(renderers: DynamicControlRenderers, content: @Composable ()->Unit) {
        val inherited=LocalDynamicControlRenderers.current
        val merged=DynamicControlRenderers(inherited.parameterKeys+renderers.parameterKeys,
            inherited.parameterKinds+renderers.parameterKinds,inherited.inputKeys+renderers.inputKeys,
            inherited.inputKinds+renderers.inputKinds)
        CompositionLocalProvider(LocalDynamicControlRenderers provides merged,content=content)
    }
}

@Composable internal fun RegisteredParameterControl(parameter: ModelParameter,value: String,compact: Boolean=false,onChange:(String)->Unit) {
    val kind=DynamicControlRegistry.parameterKind(parameter)
    val registered=LocalDynamicControlRenderers.current
    val custom=registered.parameterKeys[parameter.key] ?: registered.parameterKinds[kind]
    if(custom!=null) custom(parameter,value,compact,onChange)
    else when(kind) {
        DynamicParameterKind.DURATION -> DurationSelector(parameter,value,compact,onChange)
        DynamicParameterKind.RESOLUTION -> ResolutionSelector(parameter,value,compact,onChange)
        DynamicParameterKind.ASPECT_RATIO -> AspectRatioSelector(parameter,value,compact,onChange)
        DynamicParameterKind.FPS -> FpsSelector(parameter,value,compact,onChange)
        DynamicParameterKind.AUDIO -> AudioToggle(parameter,value,compact,onChange)
        DynamicParameterKind.SEED -> SeedInput(parameter,value,compact,onChange)
        DynamicParameterKind.NATIVE -> NativeParameterControl(parameter,value,compact,onChange)
    }
}

// Specialized extension points keep the same contract-valued editor and validation; they never substitute display labels in requests.
@Composable internal fun DurationSelector(p:ModelParameter,v:String,compact:Boolean=false,onChange:(String)->Unit) = NativeParameterControl(p,v,compact,onChange)
@Composable internal fun ResolutionSelector(p:ModelParameter,v:String,compact:Boolean=false,onChange:(String)->Unit) = NativeParameterControl(p,v,compact,onChange)
@Composable internal fun AspectRatioSelector(p:ModelParameter,v:String,compact:Boolean=false,onChange:(String)->Unit) = NativeParameterControl(p,v,compact,onChange)
@Composable internal fun FpsSelector(p:ModelParameter,v:String,compact:Boolean=false,onChange:(String)->Unit) = NativeParameterControl(p,v,compact,onChange)
@Composable internal fun AudioToggle(p:ModelParameter,v:String,compact:Boolean=false,onChange:(String)->Unit) = NativeParameterControl(p,v,compact,onChange)
@Composable internal fun SeedInput(p:ModelParameter,v:String,compact:Boolean=false,onChange:(String)->Unit) = NativeParameterControl(p,v,compact,onChange)

@Composable internal fun RegisteredMediaControl(field:CanvasInputField,media:List<AttachedMedia>,enabled:Boolean,
    onAdd:(List<Pair<Uri,String>>)->Unit,onRemove:(String)->Unit,onReorder:(String,Int)->Unit) {
    val kind=DynamicControlRegistry.inputKind(field)
    val registered=LocalDynamicControlRenderers.current
    val custom=registered.inputKeys[field.key] ?: registered.inputKinds[kind]
    if(custom!=null) custom(field,media,enabled,onAdd,onRemove,onReorder)
    else when(kind) {
        DynamicInputKind.IMAGE -> InputImageSlot(field,media,enabled,onAdd,onRemove,onReorder)
        DynamicInputKind.REFERENCES -> ReferenceMediaPicker(field,media,enabled,onAdd,onRemove,onReorder)
        DynamicInputKind.VIDEO -> VideoInputSelector(field,media,enabled,onAdd,onRemove,onReorder)
        DynamicInputKind.AUDIO -> AudioInputSelector(field,media,enabled,onAdd,onRemove,onReorder)
        DynamicInputKind.NATIVE -> CanvasAttachmentEditor(field,media,enabled,onAdd,onRemove,compact=true,onReorder=onReorder)
    }
}
/** Fixed-workspace summaries reuse the same key/kind extension points and picker state as the detailed editor. */
@Composable internal fun RegisteredSummaryMediaControl(field:CanvasInputField,media:List<AttachedMedia>,enabled:Boolean,
    onAdd:(List<Pair<Uri,String>>)->Unit,onRemove:(String)->Unit,onReorder:(String,Int)->Unit,
    previewHeight:androidx.compose.ui.unit.Dp?=null) {
    val registered=LocalDynamicControlRenderers.current
    val custom=registered.inputKeys[field.key] ?: registered.inputKinds[DynamicControlRegistry.inputKind(field)]
    if(custom!=null) custom(field,media,enabled,onAdd,onRemove,onReorder)
    else CanvasAttachmentEditor(field,media,enabled,onAdd,onRemove,compact=true,onReorder=onReorder,summaryMode=true,summaryPreviewHeight=previewHeight)
}

@Composable internal fun InputImageSlot(f:CanvasInputField,m:List<AttachedMedia>,enabled:Boolean,add:(List<Pair<Uri,String>>)->Unit,remove:(String)->Unit,reorder:(String,Int)->Unit) = CanvasAttachmentEditor(f,m,enabled,add,remove,compact=true,onReorder=reorder)
@Composable internal fun ReferenceMediaPicker(f:CanvasInputField,m:List<AttachedMedia>,enabled:Boolean,add:(List<Pair<Uri,String>>)->Unit,remove:(String)->Unit,reorder:(String,Int)->Unit) = CanvasAttachmentEditor(f,m,enabled,add,remove,compact=true,onReorder=reorder)
@Composable internal fun VideoInputSelector(f:CanvasInputField,m:List<AttachedMedia>,enabled:Boolean,add:(List<Pair<Uri,String>>)->Unit,remove:(String)->Unit,reorder:(String,Int)->Unit) = CanvasAttachmentEditor(f,m,enabled,add,remove,compact=true,onReorder=reorder)
@Composable internal fun AudioInputSelector(f:CanvasInputField,m:List<AttachedMedia>,enabled:Boolean,add:(List<Pair<Uri,String>>)->Unit,remove:(String)->Unit,reorder:(String,Int)->Unit) = CanvasAttachmentEditor(f,m,enabled,add,remove,compact=true,onReorder=reorder)

@Composable internal fun PromptEditor(value:String,onValueChange:(String)->Unit,placeholder:String,
    focusRequester:FocusRequester?,enabled:Boolean,capability:com.vidadomu.nexus.agent.core.registry.PromptCapability?,
    ruleScope:com.vidadomu.nexus.agent.core.config.RuleScope,onFocusChanged:(Boolean)->Unit = {},onExpand:(()->Unit)?=null) {
    StudioPromptField(value,onValueChange,placeholder,focusRequester=focusRequester,enabled=enabled,capability=capability,
        ruleScope=ruleScope,onEditorFocusChanged=onFocusChanged,onExpand=onExpand)
}

/** All dynamic controls in one flow. Values/callbacks remain owned by CreateViewModel. */
@Composable internal fun DynamicFormRenderer(capabilities:ModelUiCapabilities,values:Map<String,String>,media:List<AttachedMedia>,
    enabled:Boolean,countKey:String?,onAdd:(CanvasInputField,List<Pair<Uri,String>>)->Unit,onRemove:(String)->Unit,
    onReorder:(String,Int)->Unit,onChange:(String,String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
        CapabilityMediaInputs(capabilities.inputFields,capabilities.model,media,enabled,onAdd,onRemove,onReorder)
        CapabilityParameterControls(capabilities.model,capabilities.primaryParameters.filter {it.key!=countKey},
            capabilities.advancedParameters.filter {it.key!=countKey},values,onChange)
    }
}

/** Displays an already resolved estimate. It never calculates a tariff or starts a provider request. */
@Composable internal fun CostEstimator(amount:String,onDetails:()->Unit,modifier:Modifier=Modifier) {
    TextButton(onClick=onDetails,modifier=modifier.heightIn(min=NexusTokens.minimumTouch)
        .semantics {contentDescription="Coste estimado: $amount. Ver detalles"},shape=NexusTokens.thumbnail,
        contentPadding=PaddingValues(horizontal=NexusTokens.space8,vertical=NexusTokens.space4)) {
        Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
            Text("Coste estimado",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount,style=MaterialTheme.typography.labelLarge.tabularMoney())
        }
    }
}

@Composable internal fun GenerateActionBar(cost:String,enabled:Boolean,busy:Boolean,busyLabel:String,onClick:()->Unit,
    actionLabel:String,disabledLabel:String?,onCostDetails:()->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
        if(!enabled && !busy && !disabledLabel.isNullOrBlank()) Text(disabledLabel,
            modifier=Modifier.fillMaxWidth().padding(horizontal=NexusTokens.space8),
            style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            CostEstimator(cost,onCostDetails,Modifier.weight(.38f))
            StudioGenerateButton(cost,enabled,busy,busyLabel,onClick,Modifier.weight(.62f),actionLabel,disabledLabel=null,showCost=false)
        }
    }
}
