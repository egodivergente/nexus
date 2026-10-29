package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.ui.theme.NexusTokens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.llm.BrainPresets
import com.vidadomu.nexus.assistant.BrainStore
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.provider.custom.BytePlusMediaPreset

internal const val BYTEPLUS_MEDIA_PROVIDER = "usr_byteplus-media"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BytePlusCanvasSheet(app: NexusApplication, models: List<NexusModel>, onDismiss: () -> Unit,
    onChoose: (Modality) -> Unit, onSettings: (String) -> Unit) {
    val hasKey = app.customMediaStore.hasKey(BytePlusMediaPreset.ID) ||
        app.secureStorage.hasProviderApiKey(BrainStore.keyId(BrainPresets.BYTEPLUS_ID))
    val direct = models.filter { it.providerId == BYTEPLUS_MEDIA_PROVIDER }
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        com.vidadomu.nexus.ui.theme.NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=NexusTokens.space24),
            verticalArrangement=Arrangement.spacedBy(NexusTokens.space12)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tu cuenta BytePlus",style=MaterialTheme.typography.titleLarge)
                    Text(if(hasKey) "Clave guardada · acceso pendiente de verificar" else "Conecta tu clave de ModelArk",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick=onDismiss) {Icon(Icons.Default.Close,"Cerrar BytePlus")}
            }
            Text("Elige aquí Seedream o Seedance para usar tu cuenta de BytePlus. El mismo modelo a través de Atlas o Crun se factura en ese proveedor.",
                style=MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                OutlinedButton(onClick={onChoose(Modality.IMAGE)},enabled=direct.any {Modality.IMAGE in it.modalities},modifier=Modifier.weight(1f)) {Text("Crear imagen")}
                OutlinedButton(onClick={onChoose(Modality.VIDEO)},enabled=direct.any {Modality.VIDEO in it.modalities},modifier=Modifier.weight(1f)) {Text("Crear vídeo")}
            }
            if(direct.isEmpty()) Text("Activa el proveedor y sus modelos en Configurar.",style=MaterialTheme.typography.bodySmall)
            Text("Los paquetes gratuitos dependen del modelo, la región y su fecha de caducidad. Un paquete de tokens de chat no implica crédito para imagen o vídeo. Comprueba qué modelos cubre en Consumo y cuotas.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Configura la clave, la región y el ID exacto del modelo o endpoint de tu consola ModelArk. El saldo de la barra superior pertenece a Atlas.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={onSettings("BYTEPLUS")},modifier=Modifier.fillMaxWidth()) {Text("Consumo y cuotas")}
            TextButton(onClick={onSettings("CUSTOM")},modifier=Modifier.fillMaxWidth()) {Text("Configurar BytePlus")}
        }
    }
}
