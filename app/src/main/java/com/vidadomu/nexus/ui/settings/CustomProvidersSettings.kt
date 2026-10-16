package com.vidadomu.nexus.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.llm.BrainAuthMode
import com.vidadomu.nexus.agent.core.llm.BrainProfile
import com.vidadomu.nexus.agent.core.llm.BrainProtocol
import com.vidadomu.nexus.agent.core.media.MediaMode
import com.vidadomu.nexus.agent.core.media.MediaProviderConfig
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.assistant.BrainEditorDialog
import com.vidadomu.nexus.assistant.BrainStore
import com.vidadomu.nexus.assistant.MediaProviderEditorDialog
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.TextSecondary

/** Una fila de lista: título, detalle discreto y, a la derecha, una acción o una marca. */
@Composable
private fun ProviderRow(title: String, detail: String, warn: Boolean, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = NexusTokens.space8), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = if (warn) MaterialTheme.colorScheme.error else TextSecondary, maxLines = 1)
        }
        trailing()
    }
}

/** Ajustes → Cerebros del agente: BytePlus, los proveedores añadidos y «Añadir proveedor personalizado». */
@Composable
internal fun BrainsSettingsSection() {
    val app = LocalContext.current.applicationContext as NexusApplication
    val settings = remember { AssistantRuntime.get(app).settings }
    var tick by remember { mutableStateOf(0) }
    val brains = remember(tick) { settings.brains.all() }
    val active = remember(tick) { settings.load().backend }
    var editing by remember { mutableStateOf<BrainProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
        brains.forEach { b ->
            val ready = settings.brains.hasKey(b.id) || b.auth.mode == BrainAuthMode.NONE
            val mine = active == BrainStore.backendFor(b.id)
            ProviderRow(b.name, listOf(b.modelId.ifBlank { "sin modelo" }, when (b.protocol) { BrainProtocol.OPENAI_RESPONSES -> "Responses"; BrainProtocol.CUSTOM_REST -> "REST"; else -> "Chat" },
                if (ready) "clave lista" else "falta la clave").joinToString(" · "), !ready, { editing = b }) {
                if (mine) Text("Activo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                else TextButton(onClick = {
                    // Cambiar de cerebro no toca claves ni la configuración de los demás proveedores.
                    error = runCatching { settings.save(settings.load().copy(backend = BrainStore.backendFor(b.id), modelId = b.modelId), null) }.exceptionOrNull()?.message
                    tick++
                }) { Text("Usar") }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { adding = true }, contentPadding = PaddingValues(0.dp)) { Text("+ Añadir proveedor personalizado") }
        Text("El agente usa el cerebro «Activo» con todas sus herramientas. Bedrock, Claude, OpenAI, Proxy y Antigravity se eligen en los ajustes del asistente y no se tocan.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
    if (adding || editing != null) BrainEditorDialog(editing, settings.brains) { _ -> adding = false; editing = null; tick++ }
}

/** Ajustes → Generadores personalizados: proveedores API de imagen/vídeo definidos por configuración. */
@Composable
internal fun MediaProvidersSettingsSection() {
    val app = LocalContext.current.applicationContext as NexusApplication
    val store = app.customMediaStore
    var tick by remember { mutableStateOf(0) }
    val providers = remember(tick) { store.all() }
    var editing by remember { mutableStateOf<MediaProviderConfig?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
        providers.forEach { p ->
            val ready = store.hasKey(p.id) || p.auth.mode == BrainAuthMode.NONE
            ProviderRow(p.name, "${p.models.size} modelo(s) · ${if (p.models.all { it.mode == MediaMode.SYNC }) "síncrono" else "asíncrono"} · ${if (ready) "clave lista" else "falta la clave"}", !ready, { editing = p }) {}
        }
        if (providers.isEmpty()) Text("Aún no has añadido ninguno.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        TextButton(onClick = { adding = true }, contentPadding = PaddingValues(0.dp)) { Text("+ Añadir proveedor API") }
        Text("Describe un servicio REST/JSON (rutas, petición, respuesta) y sus modelos aparecen en el Lienzo y para el agente. Atlas, Crun, Runway y Flow siguen como están.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
    if (adding || editing != null) MediaProviderEditorDialog(editing, store) { _ -> adding = false; editing = null; tick++ }
}
