package com.vidadomu.nexus.ui.models

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

/** Icons the user can pick for any catalog entry; stored by name so the choice survives updates. */
internal val CatalogIconChoices: List<Pair<String, ImageVector>> = listOf(
    "estrella" to Icons.Default.Star, "rayo" to Icons.Default.Bolt, "video" to Icons.Default.Videocam,
    "imagen" to Icons.Default.Image, "musica" to Icons.Default.MusicNote, "voz" to Icons.Default.RecordVoiceOver,
    "pincel" to Icons.Default.Brush, "varita" to Icons.Default.AutoFixHigh, "tijeras" to Icons.Default.ContentCut,
    "cubo" to Icons.Default.ViewInAr, "fuego" to Icons.Default.LocalFireDepartment, "corazon" to Icons.Default.Favorite,
    "carpeta" to Icons.Default.Folder, "chip" to Icons.Default.Memory, "chispas" to Icons.Default.AutoAwesome)

/**
 * The user's own names, descriptions, icons and logo sizes for catalog entries. They only change what Nexus shows:
 * the provider's identifiers, prices and contracts are untouched, and «Restaurar» brings back the original look.
 */
internal class CatalogLabelStore private constructor(private val appContext: Context) {
    private val prefs = appContext.getSharedPreferences("catalog_labels_v1", Context.MODE_PRIVATE)
    private val names: SnapshotStateMap<String, String> = mutableStateMapOf()
    private val descriptions: SnapshotStateMap<String, String> = mutableStateMapOf()
    private val icons: SnapshotStateMap<String, String> = mutableStateMapOf()
    private val scales: SnapshotStateMap<String, Float> = mutableStateMapOf()
    /** Free placement of a part of a card (key = "<entry>|<part>"), in dp from its default position. */
    private val placements: SnapshotStateMap<String, Pair<Float, Float>> = mutableStateMapOf()

    init {
        prefs.all.forEach { (key, value) ->
            when {
                key.startsWith("n:") -> (value as? String)?.let { names[key.removePrefix("n:")] = it }
                key.startsWith("d:") -> (value as? String)?.let { descriptions[key.removePrefix("d:")] = it }
                key.startsWith("i:") -> (value as? String)?.let { icons[key.removePrefix("i:")] = it }
                key.startsWith("s:") -> (value as? Float)?.let { scales[key.removePrefix("s:")] = it }
                key.startsWith("p:") -> (value as? String)?.split(',')?.takeIf { it.size == 2 }?.let { v ->
                    val x = v[0].toFloatOrNull(); val y = v[1].toFloatOrNull()
                    if (x != null && y != null) placements[key.removePrefix("p:")] = x to y
                }
            }
        }
    }

    fun name(key: String, original: String): String = names[key]?.takeIf { it.isNotBlank() } ?: original
    fun description(key: String, original: String): String = descriptions[key]?.takeIf { it.isNotBlank() } ?: original
    /** `icon:<name>` or `image:<absolute path>`; null keeps the original artwork. */
    fun icon(key: String): String? = icons[key]
    fun scale(key: String): Float = (scales[key] ?: 1f).coerceIn(MIN_SCALE, MAX_SCALE)
    fun hasCustom(key: String): Boolean = key in names || key in descriptions || key in icons || key in scales || placements.keys.any { it.startsWith("$key|") }

    fun save(key: String, name: String, description: String, originalName: String, originalDescription: String, icon: String?, scale: Float) {
        val cleanName = name.trim()
        val cleanDescription = description.trim()
        val edit = prefs.edit()
        if (cleanName.isEmpty() || cleanName == originalName) { names.remove(key); edit.remove("n:$key") }
        else { names[key] = cleanName; edit.putString("n:$key", cleanName) }
        if (cleanDescription.isEmpty() || cleanDescription == originalDescription) { descriptions.remove(key); edit.remove("d:$key") }
        else { descriptions[key] = cleanDescription; edit.putString("d:$key", cleanDescription) }
        if (icon == null) { icons.remove(key); edit.remove("i:$key") } else { icons[key] = icon; edit.putString("i:$key", icon) }
        if (scale in .98f..1.02f) { scales.remove(key); edit.remove("s:$key") } else { scales[key] = scale; edit.putFloat("s:$key", scale) }
        edit.apply()
    }

    /** Live resize while a finger is still down: only memory; [commitScale] writes it to disk when the finger lifts. */
    fun setScaleLive(key: String, scale: Float) { scales[key] = scale.coerceIn(MIN_SCALE, MAX_SCALE) }

    fun commitScale(key: String) {
        val v = scales[key] ?: 1f
        if (v in .98f..1.02f) { scales.remove(key); prefs.edit().remove("s:$key").apply() } else prefs.edit().putFloat("s:$key", v).apply()
    }

    fun resetScale(key: String) { scales.remove(key); prefs.edit().remove("s:$key").apply() }

    fun placement(key: String, part: String): Pair<Float, Float> = placements["$key|$part"] ?: (0f to 0f)
    fun setPlacementLive(key: String, part: String, x: Float, y: Float) { placements["$key|$part"] = x to y }
    fun commitPlacement(key: String, part: String) {
        val v = placements["$key|$part"] ?: return
        if (kotlin.math.abs(v.first) < 1f && kotlin.math.abs(v.second) < 1f) { placements.remove("$key|$part"); prefs.edit().remove("p:$key|$part").apply() }
        else prefs.edit().putString("p:$key|$part", "${v.first},${v.second}").apply()
    }

    fun reset(key: String) {
        placements.keys.filter { it.startsWith("$key|") }.forEach { placements.remove(it); prefs.edit().remove("p:$it").apply() }
        names.remove(key); descriptions.remove(key); icons.remove(key); scales.remove(key)
        prefs.edit().remove("n:$key").remove("d:$key").remove("i:$key").remove("s:$key").apply()
    }

    /** Copies the chosen picture into private storage so the logo keeps working if the original is deleted. */
    fun importImage(key: String, uri: Uri): String? = runCatching {
        val directory = File(appContext.filesDir, "catalog_icons").apply { mkdirs() }
        val target = File(directory, "${key.hashCode().toUInt()}_${System.currentTimeMillis()}.img")
        appContext.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
        "image:" + target.absolutePath
    }.getOrNull()

    companion object {
        const val MIN_SCALE = .5f
        const val MAX_SCALE = 2.2f
        @Volatile private var instance: CatalogLabelStore? = null
        fun get(context: Context): CatalogLabelStore = instance ?: synchronized(this) {
            instance ?: CatalogLabelStore(context.applicationContext).also { instance = it }
        }
    }
}

@Composable internal fun rememberCatalogLabels(): CatalogLabelStore = CatalogLabelStore.get(LocalContext.current)

/** The entry's own artwork when the user chose one, otherwise [fallback] drawn at the user's size. */
@Composable
internal fun CatalogGlyph(key: String, baseSize: Dp, labels: CatalogLabelStore, fallback: @Composable (Dp) -> Unit) {
    val size = baseSize * labels.scale(key)
    val spec = labels.icon(key)
    when {
        spec != null && spec.startsWith("image:") && File(spec.removePrefix("image:")).isFile ->
            AsyncImage(File(spec.removePrefix("image:")), null, Modifier.size(size).clip(androidx.compose.foundation.shape.RoundedCornerShape(size * .2f)), contentScale = ContentScale.Fit)
        spec != null && spec.startsWith("icon:") -> {
            val vector = CatalogIconChoices.firstOrNull { it.first == spec.removePrefix("icon:") }?.second
            if (vector != null) Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Icon(vector, null, Modifier.size(size * .58f), tint = MaterialTheme.colorScheme.primary)
            } else fallback(size)
        }
        else -> fallback(size)
    }
}

/** Rename, re-describe, re-icon and resize an entry. Empty or unchanged fields keep the original. */
@Composable
internal fun CatalogLabelEditor(key: String, originalName: String, originalDescription: String, labels: CatalogLabelStore, onDismiss: () -> Unit, withDescription: Boolean = true) {
    var name by remember(key) { mutableStateOf(labels.name(key, originalName)) }
    var description by remember(key) { mutableStateOf(labels.description(key, originalDescription)) }
    var icon by remember(key) { mutableStateOf(labels.icon(key)) }
    var scale by remember(key) { mutableFloatStateOf(labels.scale(key)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) labels.importImage(key, uri)?.let { icon = it }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Personalizar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (withDescription) OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("Icono", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(icon == null, { icon = null }, label = { Text("Original") })
                    }
                    items(CatalogIconChoices, key = { it.first }) { (iconName, vector) ->
                        val selected = icon == "icon:$iconName"
                        Box(Modifier.size(48.dp).clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .2f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { icon = "icon:$iconName" }.semantics { contentDescription = "Icono $iconName" },
                            contentAlignment = Alignment.Center) { Icon(vector, null, tint = MaterialTheme.colorScheme.onSurface) }
                    }
                }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (icon?.startsWith("image:") == true) "Cambiar mi imagen" else "Usar una imagen mía")
                }
                Text("Tamaño del logo · ${(scale * 100).toInt()} %", style = MaterialTheme.typography.labelLarge)
                Slider(scale, { scale = it }, valueRange = CatalogLabelStore.MIN_SCALE..CatalogLabelStore.MAX_SCALE)
                Text("Solo cambia lo que ves en Nexus. El modelo, su precio y su contrato siguen siendo los del proveedor.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (labels.hasCustom(key)) TextButton(onClick = { labels.reset(key); onDismiss() }, contentPadding = PaddingValues(0.dp)) {
                    Text("Restaurar el original")
                }
            }
        },
        confirmButton = { TextButton(onClick = { labels.save(key, name, description, originalName, originalDescription, icon, scale); onDismiss() }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
