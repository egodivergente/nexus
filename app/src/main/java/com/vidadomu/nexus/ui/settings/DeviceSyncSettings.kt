package com.vidadomu.nexus.ui.settings

import com.vidadomu.nexus.ui.theme.NexusTokens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.agent.core.sync.SyncCrypto
import com.vidadomu.nexus.sync.DeviceSync
import com.vidadomu.nexus.ui.components.NexusSheet

@Composable
fun DeviceSyncSettings(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sync = remember { DeviceSync.get(context) }
    var options by remember { mutableStateOf(sync.options()) }
    var peer by remember { mutableStateOf(options.peer) }
    var port by remember { mutableStateOf(options.port.toString()) }
    var interval by remember { mutableStateOf(options.intervalSeconds.toString()) }
    var timeout by remember { mutableStateOf(options.timeoutSeconds.toString()) }
    var maxFile by remember { mutableStateOf(options.maxFileMb.toString()) }
    var agyEndpoint by remember { mutableStateOf(options.agyEndpoint) }
    var key by remember { mutableStateOf(sync.pairingKey()) }
    var error by remember { mutableStateOf<String?>(null) }
    val status by sync.status.collectAsState()
    NexusSheet("Sincronización entre móviles", onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = NexusTokens.space24), verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Text("Los dos móviles deben estar vinculados y conectados. Puedes usar sus direcciones Tailscale para continuar fuera de casa. Los cambios pendientes se recuperan al reconectar.")
            SyncToggle("Sincronización automática", options.enabled) { options = options.copy(enabled = it) }
            SyncToggle("Aceptar cambios enviados desde Nexus Windows", options.desktop) { options = options.copy(desktop = it) }
            OutlinedTextField(peer, { peer = it }, label = { Text("Dirección del otro móvil") }, placeholder = { Text("http://100.x.x.x:8795") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it.trim() }, label = { Text("Misma clave de vinculación en ambos") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                TextButton({ key = SyncCrypto.newKey() }) { Text("Crear clave") }
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                TextButton({ clipboard.setText(androidx.compose.ui.text.AnnotatedString(key)) }, enabled = key.isNotEmpty()) { Text("Copiar clave") }
            }
            OutlinedTextField(port, { port = it }, label = { Text("Puerto de este móvil") }, singleLine = true)
            OutlinedTextField(interval, { interval = it }, label = { Text("Intervalo de intercambio (segundos)") }, singleLine = true)
            OutlinedTextField(timeout, { timeout = it }, label = { Text("Espera de red (segundos; 0 sin límite)") }, singleLine = true)
            OutlinedTextField(maxFile, { maxFile = it }, label = { Text("Tamaño por archivo (MB; 0 sin límite)") }, singleLine = true)
            HorizontalDivider()
            SyncToggle("Chats e historial de conversaciones", options.chats) { options = options.copy(chats = it) }
            SyncToggle("Memoria, proyectos y personajes", options.memory) { options = options.copy(memory = it) }
            SyncToggle("Skills personales y sus archivos", options.skills) { options = options.copy(skills = it) }
            SyncToggle("Galería y generaciones completadas", options.gallery) { options = options.copy(gallery = it) }
            SyncToggle("Archivos y adjuntos locales", options.attachments) { options = options.copy(attachments = it) }
            SyncToggle("Claves API y claves de los puentes", options.credentials) { options = options.copy(credentials = it) }
            SyncToggle("Ajustes y perfiles de proveedores", options.settings) { options = options.copy(settings = it) }
            SyncToggle("Autenticación de AGY en Termux", options.agy) { options = options.copy(agy = it) }
            if (options.agy) OutlinedTextField(agyEndpoint, { agyEndpoint = it }, label = {Text("Adaptador AGY local")}, singleLine = true, modifier = Modifier.fillMaxWidth())
            SyncToggle("Propagar eliminaciones de memoria, chats, skills y claves", options.deletions) { options = options.copy(deletions = it) }
            SyncToggle("Reanudar al reiniciar el móvil", options.afterBoot) { options = options.copy(afterBoot = it) }
            Text("Los chats recibidos aparecen en el historial; tu conversación abierta se mantiene. Las ediciones incompatibles conservan una copia recibida. AGY requiere el adaptador local de Termux activo. La dirección del puente y el proyecto abierto se mantienen en cada móvil.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                error = runCatching {
                    val next = options.copy(peer = peer.trim(), port = port.toInt(), intervalSeconds = interval.toLong(), timeoutSeconds = timeout.toLong(), maxFileMb = maxFile.toLong(), agyEndpoint = agyEndpoint.trim())
                    sync.configure(next, key); options = next
                }.exceptionOrNull()?.message
            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar y aplicar") }
            OutlinedButton(onClick = { sync.syncSoon() }, enabled = sync.options().enabled, modifier = Modifier.fillMaxWidth()) { Text("Sincronizar ahora") }
        }
    }
}

@Composable
private fun SyncToggle(title: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f)); Switch(checked, change)
    }
}
