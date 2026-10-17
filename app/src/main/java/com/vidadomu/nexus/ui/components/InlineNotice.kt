package com.vidadomu.nexus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.theme.NexusTokens

/** Maps transport failures to a useful message, leaving diagnostics available on demand. */
internal fun noticeMessage(raw: String?, fallback: String = "No se ha podido completar la acción."): String {
    val text = raw.orEmpty().lowercase()
    return when {
        "counttokens" in text ->
            "No se pudo contar el documento. Revisa los permisos de Bedrock en Ajustes del agente."
        listOf("limite diario", "límite diario", "tope diario").any(text::contains) ->
            "Se ha alcanzado el límite diario del agente. Revisa el consumo en Ajustes del agente."
        listOf("limite de consumo", "límite de consumo").any(text::contains) ->
            "Este mensaje ha alcanzado su límite de consumo. El trabajo se conserva; revisa los límites del agente."
        com.vidadomu.nexus.core.network.NetworkErrorParser.TLS_HINTS.any(text::contains) ->
            com.vidadomu.nexus.core.network.NetworkErrorParser.TLS_MESSAGE
        listOf("unable to resolve host", "unknownhost", "network", "connection", "sin conexión").any(text::contains) ->
            "No hay conexión. Comprueba la red y vuelve a intentarlo."
        listOf("timeout", "timed out", "tiempo de espera").any(text::contains) ->
            "La consulta tarda demasiado. Comprueba el estado antes de reintentar."
        listOf("401", "403", "unauthorized", "api key", "clave api").any(text::contains) ->
            "No se pudo acceder al proveedor. Revisa la conexión en Ajustes."
        listOf("insufficient", "saldo insuficiente", "credits", "balance").any(text::contains) ->
            "El proveedor no confirma saldo suficiente. Revisa el saldo y el coste."
        else -> fallback
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun InlineNotice(message: String, modifier: Modifier = Modifier, technicalDetails: String? = null,
    actionLabel: String? = null, onAction: (() -> Unit)? = null, isError: Boolean = true) {
    var expanded by remember(technicalDetails) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(modifier=modifier.fillMaxWidth().semantics {liveRegion=LiveRegionMode.Polite}, shape=NexusTokens.field,
        color=colors.surfaceContainerHigh,
        border=BorderStroke(1.dp, colors.outlineVariant),
        contentColor=colors.onSurface) {
        Column(Modifier.padding(NexusTokens.space12)) {
            Row(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                Icon(if(isError) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
                    contentDescription=if(isError) "Aviso" else "Información",
                    tint=if(isError) colors.error else colors.onSurfaceVariant, modifier=Modifier.size(20.dp))
                Text(message, style=MaterialTheme.typography.bodyMedium, modifier=Modifier.weight(1f))
            }
            if(onAction != null || !technicalDetails.isNullOrBlank()) {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
                    if(onAction != null && actionLabel != null) TextButton(onClick=onAction) {
                        Text(actionLabel, color=colors.primary)
                    }
                    if(!technicalDetails.isNullOrBlank()) TextButton(onClick={ expanded=!expanded },
                        modifier=Modifier.semantics {stateDescription=if(expanded) "Detalles expandidos" else "Detalles contraídos"}) {
                        Text(if(expanded) "Ocultar detalles" else "Detalles", color=colors.onSurfaceVariant)
                    }
                }
            }
            if(expanded) Text(technicalDetails.orEmpty(), style=MaterialTheme.typography.labelMedium)
        }
    }
}
