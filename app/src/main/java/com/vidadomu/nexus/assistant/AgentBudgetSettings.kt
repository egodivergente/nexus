package com.vidadomu.nexus.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.ui.components.StudioTextField

@Composable
internal fun AgentBudgetSettings() {
    val runtime=AssistantRuntime.get(LocalContext.current.applicationContext as NexusApplication)
    val limits=remember {runtime.settings.llmLimits()}
    var expanded by remember {mutableStateOf(false)}
    var turn by remember {mutableStateOf(limits.perTurnUsd.toString())}
    var daily by remember {mutableStateOf(limits.dailyUsd.toString())}
    var message by remember {mutableStateOf("")}
    val spent=remember {runCatching {runtime.llmBudget.spentToday()}.getOrNull()}
    TextButton(onClick={expanded=!expanded}) {Text("Consumo del agente · " + (spent?.let {"%.2f USD hoy".format(java.util.Locale.US,it)} ?: "registro no disponible"))}
    if(expanded) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("AWS directo: presupuesto local estimado con margen del 20%. Incluye reservas de envios sin respuesta; no es la factura global de AWS.",style=MaterialTheme.typography.bodySmall)
        StudioTextField(turn,{turn=it},label={Text("Maximo por mensaje · USD")},singleLine=true,modifier=Modifier.fillMaxWidth())
        StudioTextField(daily,{daily=it},label={Text("Maximo diario · USD (UTC)")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("Hasta 8 llamadas por mensaje. 150.000 tokens por mensaje y 500.000 al dia. Los documentos se leen una vez y se reutiliza el resumen.",style=MaterialTheme.typography.bodySmall)
        FilledTonalButton(onClick={
            runCatching {runtime.settings.saveLlmLimits(turn.replace(',','.').toDouble(),daily.replace(',','.').toDouble())}
                .onSuccess {message="Limites guardados. Se aplican a la siguiente llamada."}
                .onFailure {message="Indica importes positivos; el limite diario debe cubrir el de un mensaje."}
        }) {Text("Guardar limites")}
        if(message.isNotEmpty()) Text(message,style=MaterialTheme.typography.bodySmall)
    }
}
