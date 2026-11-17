package com.vidadomu.nexus.ui.cut

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.cut.engine.PreviewLevel
import com.vidadomu.nexus.cut.export.ExportMath
import com.vidadomu.nexus.cut.model.Clip
import com.vidadomu.nexus.cut.model.ExportResolution
import com.vidadomu.nexus.cut.model.Project
import com.vidadomu.nexus.cut.model.VideoCodec
import com.vidadomu.nexus.ui.theme.rememberNexusHaptics
import com.vidadomu.nexus.ui.theme.NexusHapticEvent

internal val CUT_ASPECTS = listOf("16:9", "9:16", "1:1", "4:5", "4:3", "21:9")
/** Los que se ofrecen a mano; si el montaje va a 23.98 o 29.97 (los pone el material o el agente) también sale el suyo. */
internal val CUT_FRAME_RATES = listOf("24", "25", "30", "50", "60")

/** Hoja inferior de Cut con el mismo tratamiento que las de Nexus (forma, superficie, velo y barras del sistema ocultas). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CutSheet(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = com.vidadomu.nexus.ui.theme.NexusThemeSpecs.treatment(com.vidadomu.nexus.ui.theme.LocalNexusAppearance.current).sheet,
        containerColor = com.vidadomu.nexus.ui.theme.LocalNexusVisuals.current.sheetSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.56f),
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        com.vidadomu.nexus.ui.theme.NexusSheetSystemBars()
        content()
    }
}

/**
 * Ajustes del montaje, fuera de la pantalla de edición: formato, comportamiento de la edición y calidad de
 * la previsualización. Nada de esto se toca a menudo; todo sigue a dos toques (nombre o icono de ajustes).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CutSettingsSheet(
    project: Project, ripple: Boolean, snapping: Boolean, previewLevel: PreviewLevel,
    onRipple: (Boolean) -> Unit, onSnapping: (Boolean) -> Unit, onPreviewLevel: (PreviewLevel) -> Unit,
    onAspect: (String) -> Unit, onFrameRate: (String) -> Unit, onRename: () -> Unit, onDismiss: () -> Unit,
) {
    CutSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    val s = project.settings
                    Text("${s.width} × ${s.height} · ${formatFps(s.frameRate.fps)} fps", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onRename) { Text("Renombrar") }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("Formato", style = MaterialTheme.typography.labelLarge)
            val current = cutAspectLabel(project.settings.width, project.settings.height)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in CUT_ASPECTS) CutChip(selected = a == current, onClick = { if (a != current) onAspect(a) }, label = { Text(a) })
            }
            Spacer(Modifier.height(4.dp))
            Text("Fotogramas por segundo", style = MaterialTheme.typography.labelLarge)
            val fps = formatFps(project.settings.frameRate.fps)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (f in (CUT_FRAME_RATES + fps).distinct().sortedBy { it.toDouble() }) CutChip(selected = f == fps, onClick = { if (f != fps) onFrameRate(f) }, label = { Text(f) })
            }
            Text("La rejilla del montaje: cabezal, cortes y recortes caen en estos fotogramas. Un montaje nuevo toma los de su primer vídeo.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("Edición", style = MaterialTheme.typography.labelLarge)
            SettingSwitch("Ripple", "Al recortar, borrar o insertar, lo que va detrás se desplaza y no quedan huecos.", ripple, onRipple)
            SettingSwitch("Imán", "Los bordes y el cabezal se pegan a los cortes, marcadores y al cabezal.", snapping, onSnapping)
            Spacer(Modifier.height(4.dp))
            Text("Calidad de la previsualización", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in PreviewLevel.entries) CutChip(selected = l == previewLevel, onClick = { onPreviewLevel(l) }, label = { Text(l.label) })
            }
            Text("Solo cambia lo que se ve al editar; la exportación usa siempre la calidad completa.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatFps(fps: Double): String =
    if (kotlin.math.abs(fps - Math.round(fps)) < .01) Math.round(fps).toString() else String.format(java.util.Locale.ROOT, "%.2f", fps)

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null)
    }
}

/**
 * Exportar: de entrada, una línea con lo que saldrá (tamaño, códec, peso aproximado) y un botón. Qué
 * exportar, resolución y códec quedan en «Opciones» para quien los necesite.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CutExportSheet(
    project: Project, durationUs: Long, clipCount: Int, selectedCount: Int,
    onDismiss: () -> Unit, onExport: (ExportResolution, VideoCodec, Int) -> Unit,
) {
    var res by remember { mutableStateOf(project.export.resolution) }
    var cod by remember { mutableStateOf(project.export.codec) }
    var mode by remember { mutableIntStateOf(0) }
    var advanced by remember { mutableStateOf(false) }
    val haptics = rememberNexusHaptics()
    val plan = remember(res, cod, durationUs) {
        runCatching { ExportMath.plan(project.settings, project.export.copy(resolution = res, codec = cod), durationUs) }.getOrNull()
    }
    CutSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Exportar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val what = when (mode) { 1 -> "Cada clip por separado · $clipCount"; 2 -> "Selección · $selectedCount"; else -> "Montaje completo" }
            val size = plan?.let { "${it.width} × ${it.height}" } ?: res.label
            val weight = plan?.let { " · ≈ ${ExportMath.formatBytes(it.estimatedBytes)}" }.orEmpty()
            Text("$what · ${CutDragText.Clock.short(durationUs)}", style = MaterialTheme.typography.bodyMedium)
            Text("$size · ${cod.label}$weight · se guarda en la Biblioteca", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Button(onClick = { onExport(res, cod, mode) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (mode == 0) "Exportar montaje" else "Exportar ${if (mode == 1) clipCount else selectedCount} clips")
            }
            TextButton(onClick = { haptics.perform(NexusHapticEvent.SELECTION); advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
                Text(if (advanced) "Menos opciones" else "Opciones")
                Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(advanced) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Qué exportar", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CutChip(mode == 0, { mode = 0 }, { Text("Montaje completo") })
                        CutChip(mode == 1, { mode = 1 }, { Text("Cada clip · $clipCount") }, enabled = clipCount > 0)
                        CutChip(mode == 2, { mode = 2 }, { Text("Selección · $selectedCount") }, enabled = selectedCount > 0)
                    }
                    if (mode != 0) Text("Un MP4 por segmento, con su audio y capas. El montaje se conserva intacto.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Resolución", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in ExportResolution.entries) CutChip(r == res, { res = r }, { Text(r.label) })
                    }
                    Text("Códec", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CutChip(cod == VideoCodec.H264, { cod = VideoCodec.H264 }, { Text("H.264 · compatible") })
                        CutChip(cod == VideoCodec.H265, { cod = VideoCodec.H265 }, { Text("H.265 · más ligero") })
                    }
                }
            }
        }
    }
}

@Composable
internal fun CutTextDialog(title: String, initial: String, multiline: Boolean = false, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = !multiline, minLines = if (multiline) 2 else 1, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CutChoiceDialog(title: String, options: List<String>, current: String?, label: (String) -> String, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    val choiceHaptics = rememberNexusHaptics()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (o in options) CutChip(selected = o == current, onClick = { choiceHaptics.perform(NexusHapticEvent.SELECTION); onChoose(o) }, label = { Text(label(o)) }) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
internal fun CutVolumeDialog(clips: List<Clip>, onDismiss: () -> Unit, onMute: (Boolean) -> Unit, onConfirm: (Double) -> Unit) {
    var db by remember { mutableFloatStateOf(clips.first().audio.gainDb.toFloat().coerceIn(-30f, 12f)) }
    val muted = clips.all { it.audio.muted }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Volumen") },
        text = { Column {
            Text(if (db == 0f) "Original" else String.format(java.util.Locale.forLanguageTag("es-ES"), "%+.1f dB", db), style = MaterialTheme.typography.titleMedium)
            Slider(db, { db = (Math.round(it * 2) / 2f) }, valueRange = -30f..12f)
            TextButton(onClick = { onMute(!muted) }) { Text(if (muted) "Quitar silencio" else "Silenciar") }
        } },
        confirmButton = { TextButton(onClick = { onConfirm(db.toDouble()) }) { Text("Aplicar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

/**
 * Chip de opción de las hojas del editor: la elegida va rellena con el acento (con el gris de Material apenas se
 * distinguía de las demás sobre el fondo oscuro, visto en el Pixel 8 Pro).
 */
@Composable
private fun CutChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected, onClick = onClick, label = label, enabled = enabled,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = scheme.primary, selectedLabelColor = scheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled, selected = selected, selectedBorderColor = scheme.primary,
        ),
    )
}
