package com.vidadomu.nexus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.noticeMessage
import com.vidadomu.nexus.ui.theme.*

/** Verde de estado «conectado»; también es el color de los proveedores. */
internal val ProviderConnected = Color(0xFF34D399)
internal val ProviderTint = Color(0xFF34D399)

/** Baldosa de color con icono: identifica una categoría o un proveedor de un vistazo. */
@Composable
internal fun CategoryTile(icon: ImageVector, tint: Color, size: Dp = 44.dp) {
    Box(Modifier.size(size).background(tint.copy(alpha = .16f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * .45f))
    }
}

/** Pastilla de estado: punto de color y texto corto. */
@Composable
internal fun StatusPill(connected: Boolean, label: String) {
    Surface(shape = NexusTokens.filter,
        color = if (connected) ProviderConnected.copy(alpha = .14f) else MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = NexusTokens.space12, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (connected) ProviderConnected else TextSecondary, CircleShape))
            Spacer(Modifier.width(NexusTokens.space8))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (connected) ProviderConnected else TextSecondary)
        }
    }
}

/** Tarjeta única de proveedor: cabecera con estado y, debajo, el contenido de cada uno (clave, saldo, prueba). */
@Composable
internal fun ProviderCard(name: String, subtitle: String, icon: ImageVector, tint: Color, connected: Boolean,
    idleLabel: String = "Sin clave", content: @Composable ColumnScope.() -> Unit) {
    // Plegada por defecto: todas las tarjetas de proveedor miden lo mismo; se despliegan con un toque.
    var open by androidx.compose.runtime.saveable.rememberSaveable(name) { androidx.compose.runtime.mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), shape = NexusTokens.card, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp) {
        Column(Modifier.padding(NexusTokens.space16), verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
            Row(Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Plegar $name" else "Desplegar $name") { open = !open },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
                CategoryTile(icon, tint)
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusPill(connected, if (connected) "Conectado" else idleLabel)
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = TextSecondary)
            }
            if (open) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                content()
            }
        }
    }
}

/** Clave guardada (enmascarada) con Añadir/Cambiar y, si la hay, Borrar. */
@Composable
internal fun ProviderKeyRow(maskedKey: String?, onChange: () -> Unit, onClear: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = TextSecondary)
        Spacer(Modifier.width(NexusTokens.space8))
        Text(maskedKey ?: "Sin clave guardada", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (maskedKey != null) FontFamily.Monospace else null,
            color = if (maskedKey != null) MaterialTheme.colorScheme.onSurface else TextSecondary)
        TextButton(onClick = onChange) { Text(if (maskedKey == null) "Añadir" else "Cambiar") }
        if (maskedKey != null && onClear != null) TextButton(onClick = onClear) {
            Text("Borrar", color = MaterialTheme.colorScheme.error)
        }
    }
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
