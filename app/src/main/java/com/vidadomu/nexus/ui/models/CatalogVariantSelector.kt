package com.vidadomu.nexus.ui.models

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.theme.NexusHapticEvent
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.rememberNexusHaptics

/** Real product variants only. The caller keeps responsibility for choosing a native route or a workflow. */
@Composable
internal fun CatalogVariantSelector(groups:List<CatalogModelGroup>,selectedGroup:CatalogModelGroup?,
    onSelect:(CatalogModelGroup)->Unit,modifier:Modifier=Modifier,compact:Boolean=false) {
    var expanded by remember {mutableStateOf(false)}
    val feedback=rememberNexusHaptics()
    val labels=rememberCatalogLabels()
    val title=selectedGroup?.let {labels.name("group:${it.key}",catalogVariantTitle(it))} ?: "Elegir variante"
    val canChoose=groups.size>1 || (selectedGroup==null && groups.isNotEmpty())
    val safeInsets=WindowInsets.safeDrawing.asPaddingValues()
    val available=(LocalConfiguration.current.screenHeightDp.dp-safeInsets.calculateTopPadding()-safeInsets.calculateBottomPadding()
        -NexusTokens.space32).coerceAtLeast(NexusTokens.minimumTouch)
    Box(modifier) {
        FilledTonalButton(onClick={expanded=true},enabled=canChoose,shape=NexusTokens.field,
            contentPadding=PaddingValues(horizontal=NexusTokens.space12,vertical=NexusTokens.space4),
            modifier=Modifier.heightIn(min=NexusTokens.minimumTouch).semantics {
                contentDescription="Variante: $title"
                stateDescription=if(expanded) "Desplegado" else "Plegado"
            }) {
            Text(if(compact) title else "Variante · $title",Modifier.weight(1f,fill=false),style=MaterialTheme.typography.labelLarge,
                maxLines=2,overflow=TextOverflow.Ellipsis)
            Spacer(Modifier.width(NexusTokens.space4))
            if(canChoose) Icon(Icons.Default.KeyboardArrowDown,null,Modifier.size(NexusTokens.iconSmall))
        }
        // Material positions the popup within the window; its scrollable menu remains bounded by safe height.
        DropdownMenu(expanded=expanded && groups.isNotEmpty(),onDismissRequest={expanded=false},
            modifier=Modifier.heightIn(max=(available*.6f).coerceAtLeast(NexusTokens.minimumTouch))) {
            groups.forEach {group -> key(group.key) {
                val selected=group.key==selectedGroup?.key
                DropdownMenuItem(text={Text(labels.name("group:${group.key}",catalogVariantTitle(group)),style=MaterialTheme.typography.bodyLarge)},
                    trailingIcon={if(selected) Icon(Icons.Default.Check,null,Modifier.size(NexusTokens.iconSmall))},
                    onClick={
                        expanded=false
                        if(!selected) {feedback.perform(NexusHapticEvent.SELECTION);onSelect(group)}
                    },modifier=Modifier.semantics {stateDescription=if(selected) "Seleccionada" else "Sin seleccionar"})
            }}
        }
    }
}
