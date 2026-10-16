package com.vidadomu.nexus.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.tabularMoney

@Composable
internal fun AgentBudgetSettings() {
    val app=LocalContext.current.applicationContext as NexusApplication
    val runtime=AssistantRuntime.get(app)
    val currencyState by app.currencyManager.state.collectAsState()
    val limits=remember {runtime.settings.configuredLlmLimits()}
    var enabled by remember {mutableStateOf(runtime.settings.budgetEnabled())}
    var expanded by remember {mutableStateOf(false)}
    var turn by remember {mutableStateOf(limits.perTurnUsd.let { if (it >= Double.MAX_VALUE / 2) "0" else it.toString().replace('.',',') })}
    var daily by remember {mutableStateOf(limits.dailyUsd.let { if (it >= Double.MAX_VALUE / 2) "0" else it.toString().replace('.',',') })}
    var message by remember {mutableStateOf("")}
    val spent=remember {runCatching {runtime.llmBudget.spentToday()}.getOrNull()}
    val spendingLabel=remember(spent,currencyState) {spent?.let {app.currencyManager.formatCost(it)} ?: "registro no disponible"}
    TextButton(onClick={expanded=!expanded}) {Text("Consumo del agente · $spendingLabel hoy",style=MaterialTheme.typography.bodyMedium.tabularMoney())}
    if(expanded) Column(verticalArrangement=Arrangement.spacedBy(NexusTokens.space8)) {
        spent?.let {Text(app.currencyManager.formatUsdDetails(it),style=MaterialTheme.typography.labelMedium.tabularMoney())}
        Text("AWS directo: presupuesto local estimado con margen del 20%. Incluye reservas de envios sin respuesta; no es la factura global de AWS.",style=MaterialTheme.typography.bodySmall)
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Text("Límite de gasto voluntario",Modifier.weight(1f));Switch(enabled,{enabled=it;runtime.settings.setBudgetEnabled(it)}) }
        Text(if(enabled) "Se aplican tus importes locales." else "Sin límite monetario local. El consumo depende de tu cuenta y sus créditos.",style=MaterialTheme.typography.bodyMedium)
        if(enabled) Column {
        StudioTextField(turn,{turn=it},label={Text("Máximo por mensaje · USD (0 = sin límite)")},singleLine=true,modifier=Modifier.fillMaxWidth())
        StudioTextField(daily,{daily=it},label={Text("Máximo diario · USD (UTC, 0 = sin límite)")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("Sin cuota fija local de tokens por mensaje ni por día. Se aplican los importes configurados arriba.",style=MaterialTheme.typography.bodySmall)
        FilledTonalButton(onClick={
            runCatching {runtime.settings.saveLlmLimits(turn.replace(',','.').toDouble(),daily.replace(',','.').toDouble())}
                .onSuccess {message="Limites guardados. Se aplican a la siguiente llamada."}
                .onFailure {message="Indica importes numéricos (0 = sin límite)."}
        }) {Text("Guardar limites")}
        }
        if(message.isNotEmpty()) InlineNotice(message,isError=!message.startsWith("Limites guardados"))
    }
}
