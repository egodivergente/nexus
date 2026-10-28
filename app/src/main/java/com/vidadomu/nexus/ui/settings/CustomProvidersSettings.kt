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
import com.vidadomu.nexus.assistant.AssistantSettings
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

/** Un motor integrado de la app (Bedrock, Claude API…): se activa desde aquí y se configura en los ajustes del asistente. */
private data class BuiltinEngine(val backend: String, val name: String, val summary: String)

private val builtinEngines = listOf(
    BuiltinEngine(AssistantSettings.BACKEND_DIRECT, "Amazon Bedrock", "Claude con tus claves de AWS"),
    BuiltinEngine(AssistantSettings.BACKEND_ANTHROPIC, "Claude API", "API de Anthropic con tu clave"),
    BuiltinEngine(AssistantSettings.BACKEND_OPENAI, "Compatible OpenAI", "OpenAI, OpenRouter, Gemini, DeepSeek, local…"),
    BuiltinEngine(AssistantSettings.BACKEND_PROXY, "Proxy", "APK → proxy → Bedrock"),
    BuiltinEngine(AssistantSettings.BACKEND_ANTIGRAVITY, "Antigravity (AGY)", "Puente local de Antigravity CLI"),
)

/**
 * Ajustes → Motores del agente: Bedrock, Claude, OpenAI, Proxy, Antigravity, BytePlus ModelArk y los proveedores
 * añadidos, en una sola lista. Solo uno está activo; cambiar de motor no borra claves ni configuración.
 */
@Composable
internal fun BrainsSettingsSection(onOpenAssistantSettings: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as NexusApplication
    val settings = remember { AssistantRuntime.get(app).settings }
    var tick by remember { mutableStateOf(0) }
    val brains = remember(tick) { settings.brains.all() }
    val current = remember(tick) { settings.load() }
    val active = current.backend
    var editing by remember { mutableStateOf<BrainProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun activate(backend: String, modelId: String) {
        // Cambiar de motor no toca claves ni la configuración de los demás.
        error = runCatching { settings.save(settings.load().copy(backend = backend, modelId = modelId), null) }.exceptionOrNull()?.message
        tick++
    }
    fun builtinReady(e: BuiltinEngine): Boolean = when (e.backend) {
        AssistantSettings.BACKEND_ANTHROPIC -> settings.maskedApiKey(e.backend).isNotBlank()
        AssistantSettings.BACKEND_OPENAI -> current.llmBaseUrl.isNotBlank() && (settings.maskedApiKey(e.backend).isNotBlank() || AssistantSettings.LOCAL_URL.matches(current.llmBaseUrl.trim()))
        AssistantSettings.BACKEND_PROXY -> current.endpoint.isNotBlank() && current.maskedToken.isNotBlank()
        AssistantSettings.BACKEND_ANTIGRAVITY -> current.antigravityEndpoint.isNotBlank()
        else -> current.awsAccessKeyId.isNotBlank() && current.maskedAwsSecret.isNotBlank() && current.region.isNotBlank()
    }
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
        builtinEngines.forEach { e ->
            val ready = builtinReady(e)
            val model = settings.savedModelFor(e.backend)
            ProviderRow(e.name, listOf(model.ifBlank { e.summary }, if (ready) "listo" else "sin configurar").joinToString(" · "), !ready, onOpenAssistantSettings) {
                if (active == e.backend) Text("Activo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                else TextButton(onClick = { activate(e.backend, model) }, enabled = ready) { Text("Usar") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        brains.forEach { b ->
            val ready = settings.brains.hasKey(b.id) || b.auth.mode == BrainAuthMode.NONE
            val mine = active == BrainStore.backendFor(b.id)
            ProviderRow(b.name, listOf(b.modelId.ifBlank { "sin modelo" }, when (b.protocol) { BrainProtocol.OPENAI_RESPONSES -> "Responses"; BrainProtocol.CUSTOM_REST -> "REST"; else -> "Chat" },
                if (ready) "clave lista" else "falta la clave").joinToString(" · "), !ready, { editing = b }) {
                if (mine) Text("Activo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                else TextButton(onClick = { activate(BrainStore.backendFor(b.id), b.modelId) }) { Text("Usar") }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { adding = true }, contentPadding = PaddingValues(0.dp)) { Text("+ Añadir motor personalizado") }
        Text("El agente usa el motor «Activo» con todas sus herramientas. Pulsa una fila para configurarla.",
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
