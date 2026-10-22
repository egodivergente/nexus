package com.vidadomu.nexus.ui.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.ui.components.BrandLogo
import com.vidadomu.nexus.ui.theme.NexusHapticEvent
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.rememberNexusHaptics
import com.vidadomu.nexus.ui.theme.rememberSystemAnimatorsEnabled

/** A curated slot. Any [alternatives] set must be fully contained in the model tokens; [excluded] rules out siblings. */
internal data class FlagshipSpec(val label: String, val alternatives: List<Set<String>>, val excluded: Set<String> = emptySet())

internal data class FlagshipMatch(val spec: FlagshipSpec, val model: NexusModel)

private fun spec(label: String, vararg alternatives: String, excluded: Set<String> = emptySet()) =
    FlagshipSpec(label, alternatives.map { flagshipTokens(it) }, excluded)

internal val VIDEO_FLAGSHIPS = listOf(
    spec("MiniMax Hailuo", "hailuo"),
    spec("Seedance 2.5", "seedance 2.5"),
    spec("Seedance 2.0", "seedance 2.0", excluded = setOf("fast", "2.5")),
    spec("Seedance 2.0 Fast", "seedance 2.0 fast"),
    spec("Kling 3.0 Pro", "kling 3.0 pro", excluded = setOf("motion", "omni")),
    spec("Kling 3.0 Standard", "kling 3.0 standard", "kling 3.0 std", excluded = setOf("motion", "omni")),
    spec("Kling 3.0 Motion", "kling 3.0 motion"),
    spec("Kling 3.0 Omni", "kling 3.0 omni"),
    spec("Wan 3.0", "wan 3.0"),
    spec("Veo 3.1", "veo 3.1"),
)

internal val IMAGE_FLAGSHIPS = listOf(
    spec("Seedream 5.0 Pro", "seedream 5.0 pro"),
    spec("Seedream 5.0 Layout", "seedream 5.0 layout", "seedream 5.0 lite"),
    spec("Midjourney", "midjourney"),
    spec("FLUX 2 Pro", "flux 2 pro"),
    spec("GPT Image 2.5", "gpt image 2.5", "sunburst"),
    spec("Qwen Image 3.0", "qwen image 3.0"),
    spec("Nano Banana Pro", "nano banana pro", "nano banana ultra"),
)

/** "Kling-v3-0-Pro", "kling 3.0 pro" and "FLUX.2" normalise alike: versions are joined and a trailing ".0" dropped. */
internal fun flagshipTokens(raw: String): Set<String> {
    var text = raw.lowercase()
    text = Regex("([a-z])[.]?(?=\\d)").replace(text, "$1 ")
    text = Regex("(\\d+)[._,-](\\d+)").replace(text, "$1.$2")
    return text.split(Regex("[^a-z0-9.]+"))
        .map { it.trim('.') }
        .map { if (Regex("\\d+\\.0").matches(it)) it.substringBefore('.') else it }
        .filter { it.isNotEmpty() && it != "v" }
        .toSet()
}

private fun NexusModel.flagshipHaystack(): Set<String> =
    flagshipTokens(listOfNotNull(displayName, id, providerModelId, family).joinToString(" "))

/** Only slots with a real catalog model are returned; each model fills at most one slot and the active one wins ties. */
internal fun matchFlagships(catalog: List<NexusModel>, modality: Modality, selected: NexusModel?): List<FlagshipMatch> {
    val specs = when (modality) {
        Modality.VIDEO -> VIDEO_FLAGSHIPS
        Modality.IMAGE -> IMAGE_FLAGSHIPS
        else -> return emptyList()
    }
    val candidates = catalog.filter { modality in it.modalities }
        .sortedWith(compareByDescending<NexusModel> { it.id == selected?.id && it.providerId == selected?.providerId }
            .then(NexusModel.DEFAULT_COMPARATOR))
        .map { it to it.flagshipHaystack() }
    val used = mutableSetOf<Pair<String, String>>()
    return specs.mapNotNull { spec ->
        candidates.firstOrNull { (model, tokens) ->
            (model.providerId to model.id) !in used && tokens.none { it in spec.excluded } &&
                spec.alternatives.any { tokens.containsAll(it) }
        }?.let { (model, _) ->
            used += model.providerId to model.id
            FlagshipMatch(spec, model)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlagshipStudioHeader(
    modality: Modality,
    catalog: List<NexusModel>,
    selectedModel: NexusModel?,
    onModality: (Modality) -> Unit,
    onChoose: (NexusModel) -> Unit,
    onAllModels: () -> Unit,
    onOpenFlow: () -> Unit,
) {
    val animate = rememberSystemAnimatorsEnabled()
    val haptics = rememberNexusHaptics()
    val context = LocalContext.current
    val favoritesStore = remember(context) { StudioFavoritesStore(context) }
    var favoritesRevision by remember { mutableStateOf(0) }
    var editingFavorites by remember { mutableStateOf(false) }
    val savedFavorites = remember(modality, favoritesRevision) { favoritesStore.read(modality) }
    val matches = remember(catalog, modality, savedFavorites) { studioFavorites(catalog, modality, savedFavorites) }
    if (editingFavorites) StudioFavoritesEditor(modality, catalog, savedFavorites,
        onSave = { keys ->
            favoritesStore.save(modality, keys)
            favoritesRevision++
            editingFavorites = false
            haptics.perform(NexusHapticEvent.SELECTION)
        }, onDismiss = { editingFavorites = false })
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showIdentity by remember {mutableStateOf(false)}
    val identity=selectedModel?.let(::canvasModelIdentity)
    if(showIdentity && selectedModel!=null && identity!=null) AlertDialog(onDismissRequest={showIdentity=false},
        title={Text(identity.title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(identity.detail)
            Text(selectedModel.displayName)
            androidx.compose.foundation.text.selection.SelectionContainer {Text(identity.exactId,style=MaterialTheme.typography.bodySmall)}
        }},confirmButton={TextButton(onClick={showIdentity=false}) {Text("Cerrar")}})
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CanvasWorkspaceSwitch(false, {}, onOpenFlow)
        StudioModalitySegments(modality, animate, onModality)
        Surface(onClick=onAllModels, shape=NexusTokens.card, color=MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                BrandLogo(organization=selectedModel?.organization, modelId=selectedModel?.id, size=28.dp)
                Column(Modifier.weight(1f)) {
                    Text(identity?.detail ?: "Elegir modelo",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(identity?.title ?: "Elegir modelo", style=MaterialTheme.typography.titleSmall)
                }
                if(selectedModel!=null) IconButton(onClick={showIdentity=true}) {Icon(Icons.Default.Info,"Identidad exacta del modelo",Modifier.size(18.dp))}
                Icon(Icons.Default.SwapHoriz,"Cambiar modelo",Modifier.size(20.dp))
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Ocultar favoritos" else "Modelos favoritos") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick={editingFavorites=true}) { Icon(Icons.Default.Tune,"Personalizar modelos destacados",Modifier.size(20.dp)) }
        }
        if(expanded) LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            items(matches,key={it.model.providerId+"/"+it.model.id}) { match ->
                FlagshipCard(match, match.model.id==selectedModel?.id && match.model.providerId==selectedModel?.providerId, animate) {
                    onChoose(match.model)
                }
            }
            item(key="all-models") { AllModelsCard(onAllModels) }
        }
    }
}

@Composable
private fun StudioModalitySegments(
    modality: Modality,
    animate: Boolean,
    onModality: (Modality) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    segmentHeight: androidx.compose.ui.unit.Dp = 40.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberNexusHaptics()
    Surface(modifier, shape = NexusTokens.action, color = scheme.surfaceContainerHigh) {
        Row(Modifier.padding(NexusTokens.space4)) {
            listOf(Modality.IMAGE to "Imagen", Modality.VIDEO to "Vídeo").forEach { (target, label) ->
                val selected = modality == target
                val container by animateColorAsState(if (selected) scheme.primary else Color.Transparent,
                    if (animate) tween(NexusTokens.contentDuration) else snap(), label = "Segment $label")
                val content by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                    if (animate) tween(NexusTokens.contentDuration) else snap(), label = "Segment text $label")
                Surface(Modifier.weight(1f).heightIn(min = segmentHeight)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.Tab) {
                        if (!selected) {
                            haptics.perform(NexusHapticEvent.SELECTION)
                            onModality(target)
                        }
                    },
                    shape = NexusTokens.action, color = container) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = segmentHeight).padding(vertical = NexusTokens.space4)) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = content,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun FlagshipCard(match: FlagshipMatch, active: Boolean, animate: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val border by animateColorAsState(if (active) scheme.primary else scheme.outlineVariant,
        if (animate) tween(NexusTokens.contentDuration) else snap(), label = "Flagship border")
    val container by animateColorAsState(if (active) scheme.primary.copy(alpha = .10f) else scheme.surface,
        if (animate) tween(NexusTokens.contentDuration) else snap(), label = "Flagship container")
    Surface(Modifier.width(128.dp).height(112.dp)
        .semantics(mergeDescendants = true) {
            contentDescription = "Modelo ${match.model.displayName}" + if (active) ", activo" else ""
            selected = active
        }
        .clickable(role = Role.Button, onClick = onClick),
        shape = NexusTokens.card, color = container, border = BorderStroke(if (active) 1.5.dp else .5.dp, border)) {
        Column(Modifier.padding(NexusTokens.space8), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                BrandLogo(organization = match.model.organization, family = match.model.family, modelId = match.model.id,
                    size = 22.dp, containerSize = 32.dp)
                Spacer(Modifier.weight(1f))
                if (active) Icon(Icons.Default.Check, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(canvasModelIdentity(match.model).title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(match.model.organization, style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AllModelsCard(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Label differs from the overflow menu's "Todos los modelos" (catalog screen): this one opens the in-place picker.
    Surface(Modifier.width(104.dp).height(96.dp)
        .semantics(mergeDescendants = true) { contentDescription = "Ver todos los modelos" }
        .clickable(role = Role.Button, onClick = onClick),
        shape = NexusTokens.card, color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(NexusTokens.space8), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Add, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(NexusTokens.space8))
            Text("Ver todos", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Icon(Icons.Default.ChevronRight, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun BatchCountChips(selected: Int, enabledFor: (Int) -> Boolean, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberNexusHaptics()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        Text("Cantidad", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        BATCH_COUNTS.forEach { count ->
            val isSelected = count == selected
            val enabled = enabledFor(count)
            Surface(Modifier.heightIn(min = 36.dp).width(48.dp)
                .semantics { contentDescription = "Generar $count"; this.selected = isSelected }
                .clickable(enabled = enabled, role = Role.RadioButton) {
                    haptics.perform(NexusHapticEvent.SELECTION, changed = !isSelected)
                    onSelect(count)
                },
                shape = NexusTokens.filter,
                color = if (isSelected) scheme.primary else scheme.surfaceContainerHigh) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("x$count", style = MaterialTheme.typography.labelLarge,
                        color = when { isSelected -> scheme.onPrimary; enabled -> scheme.onSurface
                            else -> scheme.onSurfaceVariant.copy(alpha = .38f) })
                }
            }
        }
    }
}

internal val BATCH_COUNTS = listOf(1, 2, 4, 8)

/** Provider-native count field ("num_images" or "n"), when the model exposes one. */
internal fun nativeCountParameter(model: NexusModel?) =
    model?.parameters?.firstOrNull { it.key.lowercase() in setOf("num_images","num_videos","num_outputs","output_count","outputcount","number_of_images","batch_size","n","count") }

internal fun nativeCountAllowed(param: com.vidadomu.nexus.core.model.ModelParameter, count: Int): Boolean {
    if (param.options.isNotEmpty()) return param.options.any { it.trim() == count.toString() }
    param.minValue?.let { if (count < it) return false }
    param.maxValue?.let { if (count > it) return false }
    return true
}
