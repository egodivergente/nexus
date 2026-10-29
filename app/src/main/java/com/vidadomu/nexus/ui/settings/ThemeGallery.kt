package com.vidadomu.nexus.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.vidadomu.nexus.ui.components.NexusSheet
import com.vidadomu.nexus.ui.theme.*
import kotlin.math.roundToInt

internal val AppearanceSelectionSaver=listSaver<AppearanceSelection,String>(
    save={listOf(it.theme.name,it.ambientMode.name,it.intensity.toString(),it.reduceMotion.toString())},
    restore={saved -> runCatching {sanitizedAppearance(AppearanceSelection(NexusAppearance.valueOf(saved[0]),AmbientMode.valueOf(saved[1]),
        saved[2].toFloat(),saved[3].toBooleanStrict()))}.getOrNull()})

/** A reversible session: selection and environment preview together, then persist in one explicit action. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PremiumAppearancePicker(appearance:AppearancePreferences,onDismiss:()->Unit,onMoreSettings:(()->Unit)?=null) {
    val initial=rememberSaveable(saver=AppearanceSelectionSaver) {appearance.selection()}
    var selection by rememberSaveable(stateSaver=AppearanceSelectionSaver) {mutableStateOf(initial)}
    val haptics=rememberNexusHaptics()
    LaunchedEffect(selection) {appearance.previewAppearance(selection)}
    DisposableEffect(appearance) {onDispose {appearance.previewAppearance(null)}}
    val cancel:()->Unit = {
        appearance.previewAppearance(null)
        haptics.perform(NexusHapticEvent.CANCEL_CONFIRMED,changed=selection!=initial)
        onDismiss()
    }
    NexusSheet("Apariencia",cancel,subtitle="Explora el ambiente. Se guarda al aplicar.",icon=Icons.Default.Palette,
        primaryAction={
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    TextButton(onClick=cancel,modifier=Modifier.weight(1f).heightIn(min=NexusTokens.minimumTouch)) {Text("Cancelar")}
                    Button(onClick={appearance.applyAppearance(selection);haptics.perform(NexusHapticEvent.CONFIRMATION);onDismiss()},
                        enabled=selection!=initial,modifier=Modifier.weight(1f).heightIn(min=NexusTokens.minimumTouch),shape=NexusTokens.action) {
                        Text("Aplicar")
                    }
                }
                onMoreSettings?.let {more -> TextButton(onClick={cancel();more()},modifier=Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch)) {
                    Text("Tamaño de letra y más ajustes")
                }}
            }
        }) {
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=NexusTokens.space12),
            verticalArrangement=Arrangement.spacedBy(NexusTokens.space16)) {
            item {
                ThemeGallery(selection) {theme ->
                    haptics.perform(NexusHapticEvent.SELECTION,changed=selection.theme!=theme)
                    selection=selection.copy(theme=theme)
                }
            }
            item {
                Column(Modifier.padding(horizontal=NexusTokens.screenMargin),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    Text(selection.theme.label,style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics {heading()})
                    Text(selection.theme.description,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(selection.theme !in NexusAppearance.premiumThemes) Text("Tu tema anterior se conserva hasta que elijas otro.",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(Modifier.padding(horizontal=NexusTokens.screenMargin),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    Text("Ambiente",style=MaterialTheme.typography.titleSmall)
                    FlowRow(Modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8),
                        verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                        AmbientMode.entries.forEach {mode -> FilterChip(selected=selection.ambientMode==mode,onClick={
                            haptics.perform(NexusHapticEvent.SELECTION,changed=selection.ambientMode!=mode)
                            selection=selection.copy(ambientMode=mode)
                        },label={Text(mode.label)})}
                    }
                    Text(when(selection.ambientMode) {
                        AmbientMode.STATIC -> "Luz y profundidad sin animación."
                        AmbientMode.SUBTLE -> "Movimiento suave, con el contenido en primer plano."
                        AmbientMode.IMMERSIVE -> "Un ambiente más envolvente, con mayor presencia."
                    },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Intensidad · ${(selection.intensity*100).roundToInt()} %",style=MaterialTheme.typography.labelLarge)
                    Slider(value=selection.intensity,onValueChange={selection=selection.copy(intensity=it)},valueRange=0f..1f,
                        modifier=Modifier.semantics {contentDescription="Intensidad del ambiente"})
                    Row(Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch)
                        .toggleable(selection.reduceMotion,role=Role.Switch,onValueChange={value ->
                            haptics.perform(NexusHapticEvent.TOGGLE_DECISIVE,changed=value!=selection.reduceMotion)
                            selection=selection.copy(reduceMotion=value)
                        }),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
                        Column(Modifier.weight(1f)) {
                            Text("Reducir movimiento",style=MaterialTheme.typography.bodyLarge)
                            Text("Mantiene el ambiente estático y respeta las preferencias del sistema.",
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(selection.reduceMotion,onCheckedChange=null)
                    }
                    TextButton(onClick={selection=AppearanceSelection(NexusAppearance.AURORA)}) {Text("Restaurar predeterminado")}
                }
            }
        }
    }
}

/** Five real palettes, with the selected card sharing the app's ambient clock instead of starting its own. */
@Composable
internal fun ThemeGallery(selection:AppearanceSelection,onSelect:(NexusAppearance)->Unit) {
    val selectedIndex=NexusAppearance.premiumThemes.indexOf(selection.theme)
    val galleryState=rememberLazyListState(initialFirstVisibleItemIndex=selectedIndex.coerceAtLeast(0))
    LaunchedEffect(selectedIndex) {if(selectedIndex>=0) galleryState.scrollToItem(selectedIndex)}
    LazyRow(Modifier.fillMaxWidth().selectableGroup(),contentPadding=PaddingValues(horizontal=NexusTokens.screenMargin),
        horizontalArrangement=Arrangement.spacedBy(NexusTokens.space12),state=galleryState) {
        items(NexusAppearance.premiumThemes,key={it.name}) {option ->
            val selected=selection.theme==option
            Surface(shape=NexusTokens.card,color=MaterialTheme.colorScheme.surfaceContainerLow,
                border=BorderStroke(if(selected) NexusTokens.divider*4 else NexusTokens.divider,
                    if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier=Modifier.width(NexusTokens.minimumTouch*4).selectable(selected,role=Role.RadioButton,onClick={onSelect(option)})
                    .semantics(mergeDescendants=true) {stateDescription=if(selected) "Vista previa seleccionada" else "Elegir tema"}) {
                Column(Modifier.padding(NexusTokens.space8),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    ThemeMiniature(option,selection,selected)
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                        Text(option.label,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                        if(selected) Icon(Icons.Default.Check,null,Modifier.size(NexusTokens.iconSmall))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeMiniature(appearance:NexusAppearance,selection:AppearanceSelection,selected:Boolean) {
    val colors=remember(appearance) {palette(appearance)}
    val typography=nexusThemeTypography(appearance)
    MaterialTheme(colorScheme=colors,typography=typography,shapes=NexusTokens.shapes) {
        Surface(shape=NexusTokens.field,color=colors.background,
            modifier=Modifier.fillMaxWidth().heightIn(min=NexusTokens.minimumTouch*4).clearAndSetSemantics {}) {
            Box(Modifier.clip(NexusTokens.field)) {
                NexusAmbientBackground(appearance,selection.ambientMode,selection.intensity,Modifier.matchParentSize(),preview=!selected)
                Column(Modifier.padding(NexusTokens.space12),verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("Nexus",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                        Icon(Icons.Default.AutoAwesome,null,Modifier.size(NexusTokens.iconSmall),tint=colors.primary)
                    }
                    Surface(shape=NexusTokens.thumbnail,color=colors.surfaceContainer.copy(alpha=.94f)) {
                        Column(Modifier.fillMaxWidth().padding(NexusTokens.space8),verticalArrangement=Arrangement.spacedBy(NexusTokens.space4)) {
                            Text("Una nueva idea",style=MaterialTheme.typography.labelLarge)
                            Text("Dale forma a lo que imaginas.",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                        }
                    }
                    Surface(shape=NexusTokens.thumbnail,color=colors.primary) {
                        Row(Modifier.fillMaxWidth().padding(NexusTokens.space8),verticalAlignment=Alignment.CenterVertically) {
                            Text("Crear",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=colors.onPrimary)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(NexusTokens.iconSmall),tint=colors.onPrimary)
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Icon(Icons.Default.ChatBubbleOutline,null,Modifier.size(NexusTokens.iconSmall),tint=colors.onSurfaceVariant)
                        Icon(Icons.Default.AutoAwesome,null,Modifier.size(NexusTokens.iconSmall),tint=colors.primary)
                        Icon(Icons.Default.PhotoLibrary,null,Modifier.size(NexusTokens.iconSmall),tint=colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
