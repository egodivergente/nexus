@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.vidadomu.nexus.assistant

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Presentation only: never shares a store with credentials, budgets or conversation state. */
internal enum class AssistantCardSize(val label: String, val imageDp: Int, val videoDp: Int) {
    COMPACT("Compactas", 56, 88),
    BALANCED("Equilibradas", 96, 132),
    LARGE("Amplias", 220, 260);

    companion object {
        fun fromStored(value: String?): AssistantCardSize = entries.firstOrNull { it.name == value } ?: BALANCED
    }
}

internal data class AssistantPresentation(
    val cardSize: AssistantCardSize = AssistantCardSize.BALANCED,
    val animateGeneration: Boolean = true,
)

internal class AssistantPresentationStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("assistant_presentation_v1", Context.MODE_PRIVATE))
    fun load() = AssistantPresentation(AssistantCardSize.fromStored(prefs.getString("card_size", null)), prefs.getBoolean("animate_generation", true))
    fun setCardSize(size: AssistantCardSize) { prefs.edit().putString("card_size", size.name).apply() }
    fun setAnimateGeneration(value: Boolean) { prefs.edit().putBoolean("animate_generation", value).apply() }
    fun reset() { prefs.edit().remove("card_size").remove("animate_generation").apply() }
    fun observe(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

@Composable
internal fun rememberAssistantPresentationStore(): AssistantPresentationStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { AssistantPresentationStore(context) }
}

@Composable
internal fun AssistantPresentationStore.state(): State<AssistantPresentation> {
    val state = remember(this) { mutableStateOf(load()) }
    DisposableEffect(this) {
        val remove = observe { state.value = load() }
        state.value = load()
        onDispose(remove)
    }
    return state
}

@Composable
internal fun AssistantPresentationSettings() {
    val store = rememberAssistantPresentationStore()
    val presentation by store.state()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tu espacio de creación", style = MaterialTheme.typography.titleMedium)
        Text("Tamaño de las tarjetas del agente", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistantCardSize.entries.forEach { size ->
                FilterChip(selected = presentation.cardSize == size, onClick = { store.setCardSize(size) }, label = { Text(size.label) })
            }
        }
        Text("Los logos se conservan en todos los tamaños. Toca una imagen del agente para ampliarla.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Animación al generar", style = MaterialTheme.typography.bodyMedium)
                Text("Actividad visual; porcentaje solo si lo comunica el proveedor.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = presentation.animateGeneration, onCheckedChange = store::setAnimateGeneration,
                modifier = Modifier.semantics { contentDescription = "Animación al generar" })
        }
        Text("Se guarda al instante, solo en este dispositivo.", style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = store::reset) { Text("Restablecer presentación") }
        HorizontalDivider()
    }
}
