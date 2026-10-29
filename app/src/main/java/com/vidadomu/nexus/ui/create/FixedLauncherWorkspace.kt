package com.vidadomu.nexus.ui.create

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.theme.NexusTokens
import kotlin.math.roundToInt

/** Only editable text and real media previews consume spare space in the fixed workspace. */
internal data class LauncherContentSizing(val promptExtra:Dp=0.dp,val mediaExtra:Dp=0.dp,
    val reportMediaCapacity:(Dp)->Unit = {})
internal val LocalLauncherContentSizing=staticCompositionLocalOf {LauncherContentSizing()}
private enum class WorkspacePart { HEADER, MODEL, PROMPT, INPUTS, PARAMETERS, STATUS, ADVANCED }

/** A fixed composition for everyday creation. Scrolling is a fallback for IME, large text or insufficient height. */
@Composable
internal fun FixedLauncherWorkspace(modifier:Modifier,scroll:ScrollState,imeVisible:Boolean,compactEditor:Boolean,
    promptVisible:Boolean=true,
    header:@Composable ()->Unit,model:@Composable ()->Unit,
    prompt:@Composable ()->Unit,inputs:@Composable ()->Unit,parameters:@Composable ()->Unit,
    status:@Composable ()->Unit,advanced:@Composable ()->Unit) {
    val modelContent=rememberWorkspaceContent(model)
    val promptContent=rememberWorkspaceContent(prompt)
    val inputContent=rememberWorkspaceContent(inputs)
    val parameterContent=rememberWorkspaceContent(parameters)
    BoxWithConstraints(modifier) {
        val density=LocalDensity.current
        val wide=maxWidth>=600.dp
        var baseHeights by remember {mutableStateOf(emptyMap<WorkspacePart,Int>())}
        var mediaCapacity by remember {mutableStateOf(0.dp)}
        val gap=with(density) {NexusTokens.space8.roundToPx()}
        val available=with(density) {maxHeight.roundToPx()}
        fun height(part:WorkspacePart)=baseHeights[part] ?: 0
        val leftBase=height(WorkspacePart.MODEL)+height(WorkspacePart.PROMPT)+gap
        val rightBase=height(WorkspacePart.INPUTS)+height(WorkspacePart.PARAMETERS)+gap
        val chrome=height(WorkspacePart.HEADER)+height(WorkspacePart.STATUS)+height(WorkspacePart.ADVANCED)+gap*3
        val base=if(wide) chrome+maxOf(leftBase,rightBase) else baseHeights.values.sum()+gap*6
        val measured=baseHeights.size==WorkspacePart.entries.size
        val fallback=imeVisible || density.fontScale>1.25f || (measured && base>available)
        val canExpand=measured && !fallback && !compactEditor
        val free=if(canExpand) (available-base).coerceAtLeast(0) else 0
        val capacity=with(density) {mediaCapacity.roundToPx()}
        val mediaExtra=if(wide && canExpand) minOf(capacity,(available-chrome-rightBase).coerceAtLeast(0))
            else minOf(capacity,if(promptVisible) (free*.45f).roundToInt() else free)
        val promptExtra=if(!promptVisible || !canExpand) 0 else if(wide)
            (available-chrome-leftBase).coerceAtLeast(0) else free-mediaExtra
        val sizing=LauncherContentSizing(with(density) {promptExtra.toDp()},with(density) {mediaExtra.toDp()}) {
            if(mediaCapacity!=it) mediaCapacity=it
        }
        // Subtract only the exact viewport extension: expanded measurements never become the next minimum.
        fun measuredPart(part:WorkspacePart,extra:Int=0)=Modifier.fillMaxWidth().layout {measurable,constraints ->
            val placeable=measurable.measure(constraints)
            val natural=(placeable.height-extra).coerceAtLeast(0)
            if(baseHeights[part]!=natural) baseHeights=baseHeights+(part to natural)
            layout(placeable.width,placeable.height) {placeable.placeRelative(0,0)}
        }
        CompositionLocalProvider(LocalLauncherContentSizing provides sizing) {
        Column(Modifier.fillMaxWidth()
            .then(if(fallback) Modifier.fillMaxHeight().verticalScroll(scroll) else Modifier.wrapContentHeight(unbounded=true)),
            verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            if(!compactEditor) Box(measuredPart(WorkspacePart.HEADER)) {header()}
            // IME changes visibility/width, never the focused editor's Row/Column ancestry or call site.
            if(wide) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space16)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        if(!compactEditor) Box(measuredPart(WorkspacePart.MODEL)) {modelContent()}
                        Box(measuredPart(WorkspacePart.PROMPT,promptExtra)) {promptContent()}
                    }
                    if(!compactEditor) Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        Box(measuredPart(WorkspacePart.INPUTS,mediaExtra)) {inputContent()}
                        Box(measuredPart(WorkspacePart.PARAMETERS)) {parameterContent()}
                    }
                }
            } else {
                if(!compactEditor) Box(measuredPart(WorkspacePart.MODEL)) {modelContent()}
                Box(measuredPart(WorkspacePart.PROMPT,promptExtra)) {promptContent()}
                if(!compactEditor) {
                    Box(measuredPart(WorkspacePart.INPUTS,mediaExtra)) {inputContent()}
                    Box(measuredPart(WorkspacePart.PARAMETERS)) {parameterContent()}
                }
            }
            Column(measuredPart(WorkspacePart.STATUS),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {status()}
            if(!compactEditor) Box(measuredPart(WorkspacePart.ADVANCED)) {advanced()}
        }
        }
    }
}

/** Width-class changes retain up-to-date content; IME transitions keep the prompt's ancestry fixed above. */
@Composable
private fun rememberWorkspaceContent(content:@Composable ()->Unit):@Composable ()->Unit {
    val latest by rememberUpdatedState(content)
    return remember {movableContentOf {latest()}}
}
