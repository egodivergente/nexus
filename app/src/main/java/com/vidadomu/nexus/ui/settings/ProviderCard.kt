package com.vidadomu.nexus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.noticeMessage
import com.vidadomu.nexus.ui.theme.*

/** Verde de estado «conectado»; también es el color de los proveedores. */
internal val ProviderConnected:Color @Composable get()=LocalNexusVisuals.current.success
internal val ProviderTint:Color @Composable get()=MaterialTheme.colorScheme.primary

/** Baldosa de color con icono: identifica una categoría o un proveedor de un vistazo. */
@Composable
internal fun CategoryTile(icon: ImageVector, tint: Color, size: Dp = 44.dp) {
    Box(Modifier.size(size).background(tint.copy(alpha = .16f), NexusTokens.card), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * .45f))
    }
}

/**
 * Baldosa de una marca: su logo real cuando Nexus lo tiene (los mismos del catálogo) y, si no, las
 * iniciales de la marca. Nunca el icono genérico, que hacía que todos los proveedores pareciesen el mismo.
 */
@Composable
internal fun BrandTile(brand: String, tint: Color, size: Dp = 44.dp) {
    val asset = remember(brand) { com.vidadomu.nexus.ui.components.ProviderBrandRegistry.resolve(organization = brand, modelId = brand) }
    Box(Modifier.size(size).background(tint.copy(alpha = .16f), NexusTokens.card), contentAlignment = Alignment.Center) {
        if (asset is com.vidadomu.nexus.ui.components.BrandAsset.Resource) {
            com.vidadomu.nexus.ui.components.DefaultBrandLogo(organization = brand, modelId = brand, size = size * .62f)
        } else {
            // Sin logo propio: sus iniciales con el color del tema, que distinguen mejor que un icono repetido.
            Text(initialsOf(brand), style = MaterialTheme.typography.titleSmall, color = tint)
        }
    }
}

internal fun initialsOf(brand: String): String = brand.split(' ', '-', '_', '.')
    .filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("")
    .ifEmpty { brand.take(2).uppercase() }

/** Pastilla de estado: punto de color y texto corto. */
@Composable
internal fun StatusPill(connected: Boolean, label: String) {
    Surface(shape = NexusTokens.filter,
        color = if (connected) ProviderConnected.copy(alpha = .14f) else MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space4), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (connected) ProviderConnected else TextSecondary, CircleShape))
            Spacer(Modifier.width(NexusTokens.space8))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (connected) ProviderConnected else TextSecondary)
        }
    }
}

/** Tarjeta única de proveedor: cabecera con estado y, debajo, el contenido de cada uno (clave, saldo, prueba). */
@Composable
internal fun ProviderCard(name: String, subtitle: String, icon: ImageVector, tint: Color, connected: Boolean,
    idleLabel: String = "Sin clave", brand: String? = null, content: @Composable ColumnScope.() -> Unit) {
    // Plegada por defecto: todas las tarjetas de proveedor miden lo mismo; se despliegan con un toque.
    var open by androidx.compose.runtime.saveable.rememberSaveable(name) { androidx.compose.runtime.mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), shape = NexusTokens.card, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp) {
        Column(Modifier.padding(NexusTokens.space16), verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
            Row(Modifier.fillMaxWidth().heightIn(min = NexusTokens.minimumTouch)
                .semantics { stateDescription = if (open) "Desplegado" else "Plegado" }
                .clickable(role = Role.Button, onClickLabel = if (open) "Plegar $name" else "Desplegar $name") { open = !open },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                if (brand != null) BrandTile(brand, tint) else CategoryTile(icon, tint)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    StatusPill(connected, if (connected) "Conectado" else idleLabel)
                }
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = TextSecondary)
            }
            if (open) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                content()
            }
        }
    }
}

/** Clave guardada (enmascarada) con Añadir/Cambiar y, si la hay, Borrar tras confirmarlo. */
@Composable
internal fun ProviderKeyRow(maskedKey: String?, onChange: () -> Unit, onClear: (() -> Unit)?, providerName: String? = null) {
    // Solo se recuerda si el diálogo está abierto: la clave no se copia a memoria para un posible «deshacer».
    // Con clave por proveedor y clave enmascarada, una fila reutilizada para otro destino descarta la confirmación pendiente.
    var confirmClear by androidx.compose.runtime.saveable.rememberSaveable(providerName, maskedKey) { androidx.compose.runtime.mutableStateOf(false) }
    if (confirmClear && maskedKey != null && onClear != null) {
        ClearProviderKeyDialog(providerName, onConfirm = { confirmClear = false; onClear() }, onDismiss = { confirmClear = false })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = TextSecondary)
        Spacer(Modifier.width(NexusTokens.space8))
        Text(maskedKey ?: "Sin clave guardada", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (maskedKey != null) FontFamily.Monospace else null,
            color = if (maskedKey != null) MaterialTheme.colorScheme.onSurface else TextSecondary)
        TextButton(onClick = onChange) { Text(if (maskedKey == null) "Añadir" else "Cambiar") }
        if (maskedKey != null && onClear != null) TextButton(onClick = { confirmClear = true }) {
            Text("Borrar", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ClearProviderKeyDialog(providerName: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val provider = providerName?.takeIf { it.isNotBlank() }
    AlertDialog(onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null) },
        title = { Text(if (provider != null) "¿Borrar la clave de $provider?" else "¿Borrar la clave API?") },
        text = { Text("Se eliminará de este dispositivo y no se puede recuperar desde Nexus. " +
            "Para volver a usar ${provider ?: "este proveedor"} tendrás que pegarla de nuevo.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Borrar clave", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

/** Saldo del proveedor, con su detalle y un botón para actualizarlo. */
@Composable
internal fun ProviderBalance(value: String, detail: String?, busy: Boolean, onRefresh: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = NexusTokens.field, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(horizontal = NexusTokens.space12, vertical = NexusTokens.space8), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Saldo", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Text(value, style = MaterialTheme.typography.titleLarge.tabularMoney())
                detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = TextSecondary) }
            }
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Actualizar saldo") }
        }
    }
}

/** Probar conexión: el botón solo se activa con una clave guardada; el resultado aparece debajo. */
@Composable
internal fun ProviderTest(enabled: Boolean, testing: Boolean, result: Pair<Boolean, String>?, onTest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        FilledTonalButton(onClick = onTest, enabled = enabled && !testing) {
            if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.NetworkCheck, null, Modifier.size(16.dp))
            Spacer(Modifier.width(NexusTokens.space8))
            Text(if (testing) "Probando…" else "Probar conexión")
        }
        if (!enabled) Text("Añade una clave para probar la conexión.", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        result?.let { (ok, msg) ->
            InlineNotice(if (ok) "Conexión correcta." else noticeMessage(msg, "No se pudo conectar con el proveedor."),
                technicalDetails = if (ok) null else msg, isError = !ok)
        }
    }
}
