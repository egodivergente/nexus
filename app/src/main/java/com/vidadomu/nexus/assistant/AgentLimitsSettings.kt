package com.vidadomu.nexus.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.config.AgentLimits
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.TextSecondary

/**
 * Límites del agente en un solo sitio. Ninguna de estas cifras está fijada en el prompt ni en el código:
 * lo que pongas aquí es lo que el agente recibe como límite vigente. En todos, 0 = sin límite.
 */
@Composable
internal fun AgentLimitsSettings() {
    val app = LocalContext.current.applicationContext as NexusApplication
    val store = remember { AssistantRuntime.get(app).settings }
    val current = remember { store.limits() }
    var expanded by remember { mutableStateOf(false) }
    var rounds by remember { mutableStateOf(current.maxToolRounds.toString()) }
    var timeout by remember { mutableStateOf(current.turnTimeoutSeconds.toString()) }
    var flow by remember { mutableStateOf(current.flowMaxResults.toString()) }
    var images by remember { mutableStateOf(current.maxImagesPerTurn.toString()) }
    var videos by remember { mutableStateOf(current.maxVideosPerTurn.toString()) }
    var documents by remember { mutableStateOf(current.maxDocumentsPerTurn.toString()) }
    var messages by remember { mutableStateOf(current.maxMessages.toString()) }
    var toolChars by remember { mutableStateOf(current.maxToolResultChars.toString()) }
    var uiChars by remember { mutableStateOf(current.maxUiContextChars.toString()) }
    var tracking by remember { mutableStateOf(current.jobTrackingMinutes.toString()) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
        Text("Límites del agente", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(NexusTokens.space8))
        Text(if (expanded) "−" else "+", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        Text("Son tuyos: el agente los recibe como límite vigente y mandan sobre cualquier cifra de sus instrucciones. 0 = sin límite. Cada proveedor puede tener los suyos.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        val number = KeyboardOptions(keyboardType = KeyboardType.Number)
        @Composable fun field(value: String, onChange: (String) -> Unit, label: String) =
            StudioTextField(value, { onChange(it.filter(Char::isDigit)) }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, keyboardOptions = number)
        field(rounds, { rounds = it }, "Pasos seguidos por mensaje")
        field(timeout, { timeout = it }, "Tiempo máximo por mensaje (s)")
        field(flow, { flow = it }, "Resultados por encargo en Flow")
        field(images, { images = it }, "Imágenes por mensaje que ve el agente")
        field(videos, { videos = it }, "Vídeos por mensaje que ve el agente")
        field(documents, { documents = it }, "Documentos por mensaje")
        field(messages, { messages = it }, "Mensajes de historial que se reenvían")
        field(toolChars, { toolChars = it }, "Caracteres por resultado de herramienta")
        field(uiChars, { uiChars = it }, "Caracteres del estado de la interfaz")
        field(tracking, { tracking = it }, "Minutos de seguimiento de un trabajo en curso")
        FilledTonalButton(onClick = {
            fun n(s: String, d: Int) = s.toIntOrNull() ?: d
            val limits = AgentLimits(
                maxToolRounds = n(rounds, current.maxToolRounds), turnTimeoutSeconds = n(timeout, current.turnTimeoutSeconds),
                maxTokens = current.maxTokens, flowMaxResults = n(flow, current.flowMaxResults).coerceAtLeast(1),
                maxImagesPerTurn = n(images, current.maxImagesPerTurn), maxVideosPerTurn = n(videos, current.maxVideosPerTurn),
                maxDocumentsPerTurn = n(documents, current.maxDocumentsPerTurn), maxMessages = n(messages, current.maxMessages),
                maxToolResultChars = n(toolChars, current.maxToolResultChars), maxUiContextChars = n(uiChars, current.maxUiContextChars),
                jobTrackingMinutes = n(tracking, current.jobTrackingMinutes),
            )
            message = runCatching { store.saveLimits(limits) }
                .fold({ "Límites guardados. Se aplican al siguiente mensaje." to false }, { (it.message ?: "No se pudieron guardar.") to true })
        }) { Text("Guardar límites") }
        message?.let { (text, isError) -> InlineNotice(text, isError = isError) }
    }
}
