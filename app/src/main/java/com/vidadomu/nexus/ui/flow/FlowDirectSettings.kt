package com.vidadomu.nexus.ui.flow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.core.model.GenerationRequest
import com.vidadomu.nexus.core.model.MediaInput
import com.vidadomu.nexus.core.model.MediaSlotType
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.provider.flow.FlowBridgeProvider
import com.vidadomu.nexus.provider.flow.FlowDirect
import com.vidadomu.nexus.provider.flow.FlowDirectCapabilities
import com.vidadomu.nexus.provider.flow.FlowDirectSelection
import com.vidadomu.nexus.ui.theme.NexusTokens

/** Choices for the explicit Flow route, one set per output kind; persisted so the phone reopens as it was left. */
@kotlinx.serialization.Serializable
data class FlowDirectSettings(
    val model: String,
    val mode: String,
    val aspect: String = "16:9",
    val durationSeconds: Int? = null,
    val resolution: String? = null,
) {
    /** Keeps the user's choice when still valid, otherwise the first valid value of the same capability. */
    fun normalized(capabilities: FlowDirectCapabilities, kind: String): FlowDirectSettings? {
        val models = capabilities.models(kind).ifEmpty { return null }
        val model = models.firstOrNull { it.model == this.model } ?: models.first()
        val mode = model.mode(this.mode) ?: model.modes.firstOrNull() ?: return null
        return FlowDirectSettings(model.model, mode.mode,
            aspect.takeIf { it in mode.aspects } ?: mode.aspects.firstOrNull { it == "16:9" } ?: mode.aspects.first(),
            durationSeconds?.takeIf { it in mode.durations } ?: mode.durations.maxOrNull(),
            resolution?.takeIf { it in mode.resolutions } ?: mode.resolutions.firstOrNull { it == "720p" } ?: mode.resolutions.firstOrNull())
    }

    fun selection(count: Int, start: String?, end: String?, references: List<String>) = FlowDirectSelection(
        model = model, mode = mode, aspect = aspect, durationSeconds = durationSeconds, resolution = resolution, count = count,
        initialFrameId = start.takeIf { mode == "i2v" }, endFrameId = end.takeIf { mode == "i2v" },
        referenceIds = if (mode == "r2v" || mode == "t2i") references else emptyList())

    companion object {
        fun default(kind: String) = if (kind == "video") FlowDirectSettings("veo_fast", "t2v", "16:9", 8, "720p")
            else FlowDirectSettings("nano-pro", "t2i", "16:9")
    }
}

/** What the user is about to accept: each prompt with its settings and the amount Flow itself reported. */
data class FlowDirectConfirmation(val requests: List<Item>, val references: List<MediaInput>, val start: MediaInput?, val end: MediaInput?) {
    data class Item(val draft: FlowStudioDraft, val settings: FlowDirectSettings, val quote: com.vidadomu.nexus.provider.flow.FlowDirectQuote)

    companion object {
        fun summary(confirmation: FlowDirectConfirmation): String = confirmation.requests.groupBy { it.settings to it.draft.count }.entries.joinToString("\n\n") { (key, items) ->
            val (settings, count) = key
            val quote = items.first().quote
            val what = buildString {
                append(items.size).append(if (items.size == 1) " prompt" else " prompts").append(" · ")
                append(FlowDirect.modelLabel(settings.model))
                if (settings.mode != "t2i") append(" · ").append(when (settings.mode) { "i2v" -> "inicio y fin"; "r2v" -> "referencias"; else -> "texto" })
                append(" · ").append(settings.aspect)
                settings.durationSeconds?.let { append(" · ").append(it).append(" s") }
                append(" · ×").append(FlowStudioPlan.clampCount(count))
            }
            val cost = when {
                quote.included -> "Incluido en tu cuenta Flow."
                quote.amount != null -> "${quote.amount} créditos de Flow por prompt."
                else -> "Flow no muestra el importe de esta configuración; se usará tu cuenta Flow."
            }
            "$what\n$cost"
        } + "\n\nSe envía una sola vez; si la conexión falla, se recupera sin repetir."
    }
}

object FlowDirectPlan {
    /** One request per prompt; frames travel with their own field so the bridge never guesses their role. */
    fun request(draft: FlowStudioDraft, settings: FlowDirectSettings, references: List<MediaInput>, start: MediaInput?, end: MediaInput?,
        quotedCost: Int?, id: String = java.util.UUID.randomUUID().toString()): GenerationRequest {
        val inputs = when (settings.mode) {
            "i2v" -> listOfNotNull(start?.copy(fieldKey = "initial_frame", slotType = MediaSlotType.PRIMARY_IMAGE),
                end?.copy(fieldKey = "end_frame", slotType = MediaSlotType.LAST_FRAME))
            "r2v", "t2i" -> references
            else -> emptyList()
        }
        val parameters = buildMap {
            put(FlowBridgeProvider.EXPECTED_OUTPUT_KEY, if (draft.modality == Modality.VIDEO) "video" else "image")
            put(FlowDirect.ROUTE, FlowDirect.DIRECT)
            put(FlowDirect.MODEL, settings.model)
            put(FlowDirect.MODE, settings.mode)
            put(FlowDirect.ASPECT, settings.aspect)
            settings.durationSeconds?.let { put(FlowDirect.DURATION, it.toString()) }
            settings.resolution?.let { put(FlowDirect.RESOLUTION, it) }
            put(FlowDirect.COUNT, FlowStudioPlan.clampCount(draft.count).toString())
            put(FlowDirect.COST, quotedCost?.toString() ?: "null")
        }
        return GenerationRequest(id = id, providerId = FlowBridgeProvider.PROVIDER_ID, modelId = "flow/${settings.model}",
            modality = draft.modality, prompt = draft.prompt, parameters = parameters, mediaInputs = inputs)
    }
}

/**
 * The explicit settings in one quiet card: chips that scroll sideways, nothing hidden behind dialogs.
 * Options come only from [capabilities]; 2K/4K are not here because they are a download of a finished result.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowDirectSettingsCard(
    capabilities: FlowDirectCapabilities,
    kind: String,
    settings: FlowDirectSettings,
    onChange: (FlowDirectSettings) -> Unit,
    start: FlowStudioReference?,
    end: FlowStudioReference?,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onClearStart: () -> Unit,
    onClearEnd: () -> Unit,
    enabled: Boolean,
) {
    val models = capabilities.models(kind)
    val model = models.firstOrNull { it.model == settings.model } ?: return
    val mode = model.mode(settings.mode) ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = NexusTokens.card,
        border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))) {
        Column(Modifier.fillMaxWidth().padding(vertical = NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipRow("Modelo", models.map { it.model }, settings.model, FlowDirect::modelLabel, enabled) { picked ->
                onChange(settings.copy(model = picked).normalized(capabilities, kind) ?: settings)
            }
            if (kind == "video") ChipRow("Modo", model.modes.map { it.mode }, settings.mode, { m -> when (m) { "i2v" -> "Inicio y fin"; else -> FlowDirect.modeLabel(m) } }, enabled) { picked ->
                onChange(settings.copy(mode = picked).normalized(capabilities, kind) ?: settings)
            }
            ChipRow("Formato", mode.aspects, settings.aspect, { a -> when (a) { "16:9" -> "16:9 horizontal"; "9:16" -> "9:16 vertical"; else -> a } }, enabled) {
                onChange(settings.copy(aspect = it))
            }
            if (mode.durations.size > 1) ChipRow("Duración", mode.durations, settings.durationSeconds ?: mode.durations.max(), { "$it s" }, enabled) {
                onChange(settings.copy(durationSeconds = it))
            } else if (mode.durations.size == 1) Text("Duración: ${mode.durations.single()} s (única que admite ${model.label} en este modo)",
                Modifier.padding(horizontal = NexusTokens.space12), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mode.resolutions.size > 1) ChipRow("Generación", mode.resolutions, settings.resolution ?: mode.resolutions.first(), { it }, enabled) {
                onChange(settings.copy(resolution = it))
            }
            if (settings.mode == "i2v") Row(Modifier.padding(horizontal = NexusTokens.space12), horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                FrameSlot("Inicio", start, enabled, onPickStart, onClearStart)
                if (mode.endFrame) FrameSlot("Fin (opcional)", end, enabled && start != null, onPickEnd, onClearEnd)
            }
        }
    }
}

@Composable
private fun <T> ChipRow(title: String, options: List<T>, selected: T, label: (T) -> String, enabled: Boolean, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = NexusTokens.space12),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 64.dp))
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, enabled = enabled,
                label = { Text(label(option), maxLines = 1) })
        }
    }
}

@Composable
private fun FrameSlot(title: String, frame: FlowStudioReference?, enabled: Boolean, onPick: () -> Unit, onClear: () -> Unit) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(width = 96.dp, height = 54.dp).clip(NexusTokens.field)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(.5.dp, MaterialTheme.colorScheme.outlineVariant, NexusTokens.field)
            .clickable(enabled = enabled, onClickLabel = if (frame == null) "Elegir $title" else "Sustituir $title", onClick = onPick)
            .semantics { contentDescription = "Fotograma $title" }, contentAlignment = Alignment.Center) {
            if (frame == null) Icon(Icons.Default.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                coil.compose.AsyncImage(model = coil.request.ImageRequest.Builder(context).data(frame.uri).build(), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                IconButton(onClick = onClear, enabled = enabled, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                    Icon(Icons.Default.Close, "Quitar $title", Modifier.size(16.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = .5f), NexusTokens.pill),
                        tint = MaterialTheme.colorScheme.surface)
                }
            }
        }
        Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
