@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.vidadomu.nexus.ui.components

import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.Locale

internal fun logoFamilyId(family: String): String = family.trim().lowercase(Locale.ROOT)
internal fun preferredLogo(familyLogo: String?, individualLogo: String?): String? =
    individualLogo?.takeIf { it.isNotBlank() } ?: familyLogo?.takeIf { it.isNotBlank() }

@Composable
fun BrandLogo(organization: String? = null, family: String? = null, modelId: String? = null,
    modifier: Modifier = Modifier, size: Dp = 20.dp, containerSize: Dp? = null, shape: Shape? = null,
    /** Catalog mode, forwarded to [DefaultBrandLogo]: official artwork on a transparent canvas. */
    bare: Boolean = false) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("family_logos", 0) }
    val familyKey = family?.takeIf { it.isNotBlank() }?.let { "family:${logoFamilyId(it)}" }
    val modelKey = modelId?.takeIf { it.isNotBlank() }?.let { "model:$it" }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val uri = remember(revision, familyKey, modelKey) {
        preferredLogo(familyKey?.let { prefs.getString(it, null) }, modelKey?.let { prefs.getString(it, null) })
    }
    var menu by remember { mutableStateOf(false) }
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        val key = target
        if (selected != null && key != null) runCatching {
            context.contentResolver.takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString(key, selected.toString()).apply()
        }.onFailure { Toast.makeText(context, "No se pudo conservar el permiso del logo", Toast.LENGTH_LONG).show() }
        target = null
    }
    var failed by remember(uri) { mutableStateOf(false) }
    Box(modifier.then(if (!bare && (familyKey != null || modelKey != null)) Modifier.combinedClickable(
        onClick = {}, onLongClickLabel = "Cambiar logo", onLongClick = { menu = true }) else Modifier)) {
        if (uri != null && !failed) AsyncImage(uri, family ?: modelId, Modifier.size(containerSize ?: size),
            contentScale = ContentScale.Fit, onError = { failed = true })
        else DefaultBrandLogo(organization, family, modelId, size = size, containerSize = containerSize, shape = shape, bare = bare)
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            familyKey?.let { key ->
                DropdownMenuItem(text = { Text("Logo de toda la familia") }, onClick = { menu = false; target = key; picker.launch(arrayOf("image/*")) })
                DropdownMenuItem(text = { Text("Restaurar logo de familia") }, onClick = { menu = false; prefs.edit().remove(key).apply() })
            }
            modelKey?.let { key ->
                DropdownMenuItem(text = { Text("Logo solo de esta variante") }, onClick = { menu = false; target = key; picker.launch(arrayOf("image/*")) })
                DropdownMenuItem(text = { Text("Usar logo de familia") }, onClick = { menu = false; prefs.edit().remove(key).apply() })
            }
        }
    }
}
