package com.vidadomu.nexus.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.TextSecondary

// Cabecera de sección de los ajustes del asistente: icono y título en el color de acento.
@Composable
internal fun AgentSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        Row(Modifier.padding(start = NexusTokens.space4, top = NexusTokens.space8), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp)
        }
        Surface(Modifier.fillMaxWidth(), shape = NexusTokens.card, color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(horizontal = NexusTokens.space16, vertical = NexusTokens.space12),
                verticalArrangement = Arrangement.spacedBy(NexusTokens.space12), content = content)
        }
    }
}

// Fila plegable: título a la izquierda, resumen a la derecha y flecha; sustituye a los botones de texto sueltos.
@Composable
internal fun FoldHeader(title: String, summary: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = NexusTokens.minimumTouch).clickable(role = Role.Button, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1)
        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = TextSecondary)
    }
}
