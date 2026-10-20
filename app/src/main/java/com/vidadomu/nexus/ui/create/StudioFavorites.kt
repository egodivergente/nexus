package com.vidadomu.nexus.ui.create

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.ui.components.BrandLogo
import com.vidadomu.nexus.ui.components.NexusSheet
import com.vidadomu.nexus.ui.theme.NexusTokens
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class StudioModelKey(val providerId: String, val modelId: String)

internal fun NexusModel.studioKey() = StudioModelKey(providerId, id)

/** null means the initial curated defaults; an empty list deliberately means no favourites. */
internal class StudioFavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_studio_favorites_v1", Context.MODE_PRIVATE)
    fun read(modality: Modality): List<StudioModelKey>? {
        val saved = prefs.getString(modality.name, null) ?: return null
        return runCatching { Json.decodeFromString<List<StudioModelKey>>(saved).distinct() }.getOrNull()
    }
    fun save(modality: Modality, keys: List<StudioModelKey>) {
        prefs.edit().putString(modality.name, Json.encodeToString(keys.distinct())).apply()
    }
}

/** Resolve against the live catalogue without replacing unavailable choices with unrelated models. */
internal fun studioFavorites(catalog: List<NexusModel>, modality: Modality, saved: List<StudioModelKey>?): List<FlagshipMatch> {
    if (saved == null) return matchFlagships(catalog, modality, null)
    val available = catalog.filter { modality in it.modalities }.associateBy { it.studioKey() }
    return saved.distinct().mapNotNull { key -> available[key]?.let {
        FlagshipMatch(FlagshipSpec(it.displayName, emptyList()), it)
    } }
}

internal fun moveStudioFavorite(keys: List<StudioModelKey>, key: StudioModelKey, offset: Int): List<StudioModelKey> {
    val index = keys.indexOf(key)
    val target = index + offset
    if (index < 0 || target !in keys.indices) return keys
    return keys.toMutableList().apply { removeAt(index); add(target, key) }
}

@Composable
internal fun StudioFavoritesEditor(
    modality: Modality, catalog: List<NexusModel>, saved: List<StudioModelKey>?,
    onSave: (List<StudioModelKey>) -> Unit, onDismiss: () -> Unit,
) {
    val available = remember(catalog, modality) {
        catalog.filter { modality in it.modalities }.distinctBy { it.studioKey() }.sortedWith(NexusModel.DEFAULT_COMPARATOR)
    }
    var chosen by remember(modality) { mutableStateOf(saved ?: studioFavorites(catalog, modality, null).map { it.model.studioKey() }) }
    var query by remember(modality) { mutableStateOf("") }
    val byKey = remember(available) { available.associateBy { it.studioKey() } }
    val results = remember(available, chosen, query) {
        available.filter { it.studioKey() !in chosen && (query.isBlank() ||
            "${it.displayName} ${it.organization} ${it.id}".contains(query.trim(), ignoreCase = true)) }
    }
    val label = if (modality == Modality.VIDEO) "vídeo" else "imagen"
    NexusSheet("Destacados de $label", onDismiss, subtitle = "Elige y ordena tus accesos rápidos",
        primaryAction = {
            Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(onClick = { onSave(chosen) }, modifier = Modifier.weight(1f)) { Text("Guardar") }
            }
        }) {
        OutlinedTextField(query, { query = it }, label = { Text("Buscar en el catálogo") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = NexusTokens.screenMargin, vertical = NexusTokens.space8))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = NexusTokens.screenMargin, vertical = NexusTokens.space8),
            verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tus destacados · ${chosen.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { chosen = studioFavorites(catalog, modality, null).map { it.model.studioKey() } }) { Text("Restablecer") }
                }
                if (chosen.isEmpty()) Text("Sin destacados. Puedes añadirlos abajo y seguir usando Ver todos.", style = MaterialTheme.typography.bodySmall)
            }
            items(chosen, key = { "chosen:${it.providerId}:${it.modelId}" }) { key ->
                val model = byKey[key]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(model?.displayName ?: key.modelId, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(model?.organization ?: "Temporalmente fuera del catálogo", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { chosen = moveStudioFavorite(chosen, key, -1) }, enabled = chosen.indexOf(key) > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, "Subir ${model?.displayName ?: key.modelId}")
                    }
                    IconButton(onClick = { chosen = moveStudioFavorite(chosen, key, 1) }, enabled = chosen.indexOf(key) < chosen.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, "Bajar ${model?.displayName ?: key.modelId}")
                    }
                    IconButton(onClick = { chosen = chosen - key }) { Icon(Icons.Default.Close, "Quitar ${model?.displayName ?: key.modelId} de destacados") }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = NexusTokens.space8))
                Text("Añadir del catálogo", style = MaterialTheme.typography.titleSmall)
                if (results.isEmpty()) Text(if (query.isBlank()) "No quedan más modelos disponibles." else "No hay coincidencias.", style = MaterialTheme.typography.bodySmall)
            }
            items(results, key = { "catalog:${it.providerId}:${it.id}" }) { model ->
                Row(Modifier.fillMaxWidth().heightIn(min = NexusTokens.minimumTouch)
                    .clickable(role = Role.Button, onClickLabel = "Añadir a destacados") { chosen = (chosen + model.studioKey()).distinct() }
                    .padding(vertical = NexusTokens.space8), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    BrandLogo(organization = model.organization, family = model.family, modelId = model.id, size = 20.dp, containerSize = 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(model.organization, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.Add, null)
                }
            }
        }
    }
}
