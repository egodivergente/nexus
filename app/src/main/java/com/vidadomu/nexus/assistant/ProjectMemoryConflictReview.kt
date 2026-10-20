package com.vidadomu.nexus.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.ui.theme.NexusTokens

/** Reading or closing this comparison never writes, drops a draft or accepts a newer unseen revision. */
@Composable
internal fun ProjectMemoryConflictReview(current: Project, draft: ProjectMemoryDraft,
    onAcceptSnapshot: () -> Unit, onDismiss: () -> Unit) {
    // Shows what accepting would really overwrite after the three-way merge, not the raw draft difference.
    val plan = draft.reviewPlan(current)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = NexusTokens.sheet, modifier = Modifier.fillMaxWidth(.94f).fillMaxHeight(.9f)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Revisar cambios de memoria", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("La memoria de «${current.name}» cambió mientras editabas. Compara el contenido actual con tu borrador. Nada se guarda al abrir o cerrar esta revisión.")
                    if (plan.keptCurrent.isNotEmpty()) Text("No tocaste ${plan.keptCurrent.joinToString(", ") { it.label() }}: " +
                        "se conserva el contenido actual de ${if (plan.keptCurrent.size == 1) "ese campo" else "esos campos"}.")
                    if (!plan.fieldMergeAvailable) Text("Este borrador es anterior a la comparación por campos: al guardarlo se escriben todos sus campos tal como aparecen aquí.")
                    if (plan.overwritten.isEmpty()) Text("Tu borrador no sustituye ningún campo de la versión actual.")
                    else Text("Al guardar, tu borrador sustituirá estos campos:", style = MaterialTheme.typography.titleSmall)
                    plan.overwritten.forEach { field ->
                        val label = field.label()
                        val now = field.text(current)
                        val mine = field.text(plan.merged)
                        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text("Actual", style = MaterialTheme.typography.labelLarge)
                        Text(now.ifBlank { "Vacío" }, style = MaterialTheme.typography.bodyMedium)
                        Text("Tu borrador", style = MaterialTheme.typography.labelLarge)
                        Text(mine.ifBlank { "Vacío" }, style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider()
                    }
                    Text("Si mantienes tu borrador, podrás guardarlo sobre esta versión con «Guardar memoria».${if (plan.fieldMergeAvailable) " Los campos que no tocaste conservan el contenido actual." else ""} Las notas nuevas del agente se conservan; solo se quitan las que marcaste. Si llega otro cambio, se pedirá revisarlo de nuevo.", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onAcceptSnapshot, modifier = Modifier.fillMaxWidth().heightIn(min = NexusTokens.minimumTouch)) {
                    Text("Mantener mi borrador sobre esta versión")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = NexusTokens.minimumTouch)) {
                    Text("Volver sin aceptar cambios")
                }
            }
        }
    }
}

private fun ProjectMemoryPart.label(): String = when (this) {
    ProjectMemoryPart.DESCRIPTION -> "Descripción corta"
    ProjectMemoryPart.SYNOPSIS -> "Sinopsis"
    ProjectMemoryPart.TONE -> "Tono / género"
    ProjectMemoryPart.STYLE -> "Estilo visual"
    ProjectMemoryPart.RULES -> "Reglas fijas"
    ProjectMemoryPart.MODELS -> "Modelos preferidos"
}
