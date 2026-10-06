package com.vidadomu.nexus.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.NexusApplication

internal const val FLOW_RECOVERY_KEY = "nexus_flow_recovery"

@Composable
internal fun FlowRecoverySettings() {
    val context = LocalContext.current
    val storage = (context.applicationContext as NexusApplication).secureStorage
    var pair by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun openRecovery() {
        val key = storage.getProviderApiKey(FLOW_RECOVERY_KEY)
        if (key.isNullOrBlank()) {
            pair = true
            return
        }
        val uri = Uri.parse("http://100.112.57.2:8899/").buildUpon()
            .encodedFragment("token=" + Uri.encode(key)).build()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            error = null
        } catch (_: ActivityNotFoundException) {
            error = "No hay un navegador disponible para abrir el acceso privado."
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Google Flow", style = MaterialTheme.typography.titleLarge)
        Text("Acceso a la cuenta del ASUS", style = MaterialTheme.typography.bodyMedium)
        Text("Requiere Tailscale. La generaci\u00f3n desde esta APK todav\u00eda no est\u00e1 conectada.",
            style = MaterialTheme.typography.bodySmall)
        Button(onClick = ::openRecovery) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Conectar Flow")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (pair) AlertDialog(
        onDismissRequest = { pair = false; token = "" },
        title = { Text("Vincular ASUS") },
        text = {
            OutlinedTextField(value = token, onValueChange = { token = it.trim() },
                label = { Text("Clave privada del ASUS") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(enabled = token.matches(Regex("[A-Za-z0-9_-]{43}")), onClick = {
                try {
                    storage.saveProviderApiKey(FLOW_RECOVERY_KEY, token)
                    token = ""; pair = false; openRecovery()
                } catch (_: Exception) {
                    token = ""; pair = false
                    error = "No se pudo guardar la clave de forma segura."
                }
            }) { Text("Vincular") }
        },
        dismissButton = { TextButton(onClick = { pair = false; token = "" }) { Text("Cancelar") } }
    )
}
