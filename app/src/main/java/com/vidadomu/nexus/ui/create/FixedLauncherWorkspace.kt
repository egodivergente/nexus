package com.vidadomu.nexus.ui.create

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.theme.NexusTokens

/** A fixed composition for everyday creation. Scrolling is a fallback for IME, large text or insufficient height. */
@Composable
internal fun FixedLauncherWorkspace(modifier:Modifier,scroll:ScrollState,imeVisible:Boolean,compactEditor:Boolean,
    header:@Composable ()->Unit,model:@Composable ()->Unit,
    prompt:@Composable ()->Unit,inputs:@Composable ()->Unit,parameters:@Composable ()->Unit,
    status:@Composable ()->Unit,advanced:@Composable ()->Unit) {
    val modelContent=rememberWorkspaceContent(model)
    val promptContent=rememberWorkspaceContent(prompt)
    val inputContent=rememberWorkspaceContent(inputs)
    val parameterContent=rememberWorkspaceContent(parameters)
    BoxWithConstraints(modifier) {
        val fontScale=LocalDensity.current.fontScale
        val wide=maxWidth>=600.dp
        var naturalContentHeight by remember {mutableIntStateOf(0)}
        val availableHeight=with(LocalDensity.current) {maxHeight.roundToPx()}
        val fallback=imeVisible || fontScale>1.25f || naturalContentHeight>availableHeight
        Column(Modifier.fillMaxWidth()
            .then(if(fallback) Modifier.fillMaxHeight().verticalScroll(scroll) else Modifier.wrapContentHeight(unbounded=true))
            .onSizeChanged {naturalContentHeight=it.height},
            verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
            if(!compactEditor) header()
            // IME changes visibility/width, never the focused editor's Row/Column ancestry or call site.
            if(wide) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space16)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        if(!compactEditor) modelContent()
                        promptContent()
                    }
                    if(!compactEditor) Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        inputContent();parameterContent()
                    }
                }
            } else {
                if(!compactEditor) modelContent()
                promptContent()
                if(!compactEditor) {inputContent();parameterContent()}
            }
            status()
            if(!compactEditor) {
                advanced()
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
