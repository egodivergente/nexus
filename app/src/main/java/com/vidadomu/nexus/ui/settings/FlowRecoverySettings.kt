package com.vidadomu.nexus.ui.settings

import com.vidadomu.nexus.ui.theme.*
import com.vidadomu.nexus.ui.components.InlineNotice
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
internal fun FlowRecoverySettings(onCreateInFlow: () -> Unit) {
    val context = LocalContext.current
    val storage = (context.applicationContext as NexusApplication).secureStorage
    var pair by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLinked by remember { mutableStateOf(!storage.getProviderApiKey(FLOW_RECOVERY_KEY).isNullOrBlank()) }

    fun openRecovery() {
        val key = storage.getProviderApiKey(FLOW_RECOVERY_KEY)
        if (key.isNullOrBlank()) {
            pair = true
            return
        }
        val uri = Uri.parse("http://asusvidal.tail6ac6b4.ts.net:8899/").buildUpon()
            .encodedFragment("token=" + Uri.encode(key)).build()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            error = null
        } catch (_: ActivityNotFoundException) {
            error = "No hay un navegador disponible para abrir el acceso privado."
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        Text("Google Flow", style = MaterialTheme.typography.titleLarge)
        Text("Proyecto activo del puente (AXELPC o ASUS)", style = MaterialTheme.typography.bodyMedium)
        Text("La creación usa los ajustes y el coste que muestre Google Flow.",
            style = MaterialTheme.typography.bodySmall)
        FilledTonalButton(onClick = {
            if (isLinked) onCreateInFlow() else pair = true
        }) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(NexusTokens.space8))
            Text(if (isLinked) "Crear en Flow" else "Vincular Flow")
        }
        if (isLinked) {
            OutlinedButton(onClick = ::openRecovery) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(NexusTokens.space8))
                Text("Abrir Flow")
            }
        }
        error?.let {InlineNotice(it)}
    }
    if (pair) AlertDialog(
        onDismissRequest = { pair = false; token = "" },
        title = { Text("Vincular puente de Flow") },
        text = {
            OutlinedTextField(value = token, onValueChange = { token = it.trim() },
                label = { Text("Clave privada del puente") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), shape=NexusTokens.field)
        },
        confirmButton = {
            TextButton(enabled = token.matches(Regex("[A-Za-z0-9_-]{43}")), onClick = {
                try {
                    storage.saveProviderApiKey(FLOW_RECOVERY_KEY, token)
                    isLinked = true
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
