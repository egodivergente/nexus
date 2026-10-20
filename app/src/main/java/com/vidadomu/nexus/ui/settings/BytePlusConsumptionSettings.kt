package com.vidadomu.nexus.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.assistant.BytePlusConsumption
import com.vidadomu.nexus.assistant.AssistantSettingsStore
import com.vidadomu.nexus.agent.core.llm.BytePlusUsageLimits
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable internal fun BytePlusConsumptionSettings() {
    val app = LocalContext.current.applicationContext as NexusApplication
    val store = remember { BytePlusConsumption.get(app) }
    val scope = rememberCoroutineScope()
    var period by rememberSaveable { mutableStateOf("day") }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val data = remember(revision) { runCatching { store.ledger.snapshot() } }
    val official = remember(period, revision) { store.cached(period) }
    val refresh: () -> Unit = { if (!busy) scope.launch {
        busy = true
        try { store.sync(period, force = true) } finally { busy = false; revision++ }
    }; Unit }
    LaunchedEffect(period) { if (store.periodic && store.stale(period)) refresh() }
    LaunchedEffect(Unit) { while (true) { delay(1500); revision++ } }
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space12)) {
        Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
            FilterChip(period == "day", { period = "day" }, label = { Text("Día") })
            FilterChip(period == "month", { period = "month" }, label = { Text("Mes") })
        }
        val today = LocalDate.now().toString()
        Text(if (period == "day") "Hoy · $today" else "Mes · ${today.take(7)}", style = MaterialTheme.typography.titleMedium)
        LocalUsageCard(store, data, period)
        OfficialUsageCard(store, official, period, busy, refresh)
        SyncSettingsCard(store)
        LocalLimitsCard(store) { revision++ }
    }
}

@Composable private fun LocalUsageCard(store: BytePlusConsumption, data: Result<com.vidadomu.nexus.agent.core.llm.BytePlusUsageData>, period: String) {
    val today = LocalDate.now().toString()
    val entries = data.getOrNull()?.entries?.filter { if (period == "day") it.date == today else it.date.take(7) == today.take(7) }.orEmpty()
    val totals = store.ledger.totals(entries)
        SettingsCard {
            Text("Consumo de Nexus", style = MaterialTheme.typography.titleMedium)
            Text("Solo este dispositivo · ${data.getOrNull()?.zone ?: "zona local"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (data.isFailure) InlineNotice("Registro local no disponible. No se sustituye por ceros.", isError = true)
            else if (entries.isEmpty()) Text("Sin registros locales de BytePlus en este periodo. El seguimiento comienza con esta actualización; no reconstruye llamadas anteriores.", style = MaterialTheme.typography.bodyMedium)
            else {
                Text("Entrada: ${totals.input ?: "no informada"} tokens · Salida: ${totals.output ?: "no informada"} tokens")
                Text("Llamadas: ${totals.calls} · Coste estimado: ${bpMoney(totals.usd)}")
                if (totals.uncertain > 0) InlineNotice("${totals.uncertain} llamadas pendientes o de resultado incierto. Sus reservas cuentan para el límite, no son cargos definitivos.")
                entries.filter { it.state != "rejected" }.groupBy { it.model }.forEach { (model, rows) ->
                    val sum = store.ledger.totals(rows)
                    Text(model, style = MaterialTheme.typography.labelLarge)
                    Text("${sum.calls} llamadas · entrada ${sum.input ?: "—"} · salida ${sum.output ?: "—"} · ${bpMoney(sum.usd)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("El coste usa las tarifas del cerebro o, si faltan, una tarifa oficial sincronizada hace menos de 24 horas. Sin una tarifa verificable, figura como no disponible.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("Estimación de entrada y salida, sin descontar paquetes gratuitos. No es la factura de BytePlus y no se suma a ella.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            val saved = data.getOrNull()?.limits
            val dayTotals = store.ledger.totals(data.getOrNull()?.entries.orEmpty().filter { it.date == today })
            if (saved?.alertUsd?.let { threshold -> dayTotals.usd?.let { it >= threshold } } == true) InlineNotice("Alerta: el coste estimado ha alcanzado tu umbral.")
            if (saved?.alertTokens?.let { threshold -> dayTotals.input?.let { input -> dayTotals.output?.let { input + it >= threshold } } } == true) InlineNotice("Alerta: se ha alcanzado tu umbral de tokens.")
        }
}

@Composable private fun OfficialUsageCard(store: BytePlusConsumption, official: JsonObject?, period: String, busy: Boolean, refresh: () -> Unit) {
    var allQuotas by remember { mutableStateOf(false) }
        SettingsCard {
            Text("Cuenta BytePlus · datos oficiales", style = MaterialTheme.typography.titleMedium)
            Text("Puede incluir otras apps que compartan cuenta o clave. Nunca se atribuye todo este uso a Nexus.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("Última sincronización: ${bpStamp(listOf("stats", "billing", "quota", "balance").mapNotNull { (official?.get(it) as? JsonObject)?.get("updated_at")?.jsonPrimitive?.longOrNull }.maxOrNull())}", style = MaterialTheme.typography.bodySmall)
            Text("Último intento: ${bpStamp(store.lastAttempt(period).takeIf { it > 0 })}", style = MaterialTheme.typography.bodySmall)
            if (store.stale(period)) InlineNotice("Datos desactualizados o todavía no disponibles. Se conservan las últimas lecturas.")
            store.lastError(period)?.let { InlineNotice(it, isError = true) }
            FilledTonalButton(onClick = refresh, enabled = !busy) { Text(if (busy) "Actualizando…" else "Actualizar ahora") }
            OfficialSection("Consumo oficial", official?.get("stats") as? JsonObject) { section ->
                Text("Retraso habitual de 5–30 min · ámbito: ${section.word("scope") ?: "cuenta BytePlus"}", style = MaterialTheme.typography.bodySmall)
                if (section["empty"]?.jsonPrimitive?.booleanOrNull == true) Text("Consulta correcta: sin registros disponibles en esta fuente.")
                section["rows"]?.jsonArray?.forEach { element ->
                    val row = element.jsonObject
                    Text("${row.word("model") ?: "Sin modelo"}: ${row.word("calls") ?: "—"} llamadas · entrada ${row.word("input") ?: "—"} · salida ${row.word("output") ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            OfficialSection("Gasto facturado", official?.get("billing") as? JsonObject) { section ->
                Text("${section.word("amount") ?: "—"} ${section.word("currency") ?: ""} · Impuestos: ${section.word("tax") ?: "no informados"}")
                Text("Total con impuestos: ${section.word("total") ?: "no informado"} · Liquidación T+1. No se suma al consumo estimado.", style = MaterialTheme.typography.bodySmall)
            }
            OfficialSection("Saldo monetario", official?.get("balance") as? JsonObject, instant = true) { section -> Text("${section.word("amount") ?: "No disponible"} ${section.word("currency") ?: ""}") }
            OfficialSection("Paquetes gratuitos restantes", official?.get("quota") as? JsonObject, instant = true) { section ->
                if (section["empty"]?.jsonPrimitive?.booleanOrNull == true) Text("Consulta correcta: sin registros disponibles en esta fuente.")
                val rows = (section["rows"] as? JsonArray).orEmpty().sortedBy { ((it as? JsonObject)?.get("remaining") as? JsonPrimitive)?.doubleOrNull ?: Double.MAX_VALUE }
                rows.take(if (allQuotas) rows.size else 3).forEach { element ->
                    val row = element.jsonObject
                    Text("${row.word("model") ?: "Paquete"}: ${row.word("remaining") ?: "—"} ${row.word("unit") ?: "unidad no informada"}")
                }
                if (rows.size > 3) TextButton(onClick = { allQuotas = !allQuotas }) { Text(if (allQuotas) "Mostrar menos" else "Ver los ${rows.size} paquetes") }
                Text("Son cuotas de uso, no saldo monetario.", style = MaterialTheme.typography.bodySmall)
            }

            OfficialSection("Tarifas de estimación", official?.get("pricing") as? JsonObject, instant = true) { section ->
                section["rows"]?.jsonArray?.forEach { element ->
                    val row = element.jsonObject
                    Text("${row.word("model")} · entrada ${row.word("input_usd_per_million")} USD/M · salida ${row.word("output_usd_per_million")} USD/M", style = MaterialTheme.typography.bodySmall)
                }
            }

        }
}

@Composable private fun SyncSettingsCard(store: BytePlusConsumption) {
    var periodic by remember { mutableStateOf(store.periodic) }
    var minutes by remember { mutableIntStateOf(store.interval) }
        SettingsCard {
            Text("Sincronización", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Actualización periódica", Modifier.weight(1f)); Switch(periodic, { periodic = it; store.configurePeriodic(it, minutes) })
            }
            Text("Android programa las consultas cuando dispone de red; no garantiza una hora exacta.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { minutes = when(minutes) { 15 -> 30; 30 -> 60; else -> 15 }; store.configurePeriodic(periodic, minutes) }) { Text("Cada $minutes minutos") }
        }
}

@Composable private fun LocalLimitsCard(store: BytePlusConsumption, onSaved: () -> Unit) {
    val app = LocalContext.current.applicationContext as NexusApplication
    var message by remember { mutableStateOf<String?>(null) }
    val initial = remember { runCatching { store.ledger.snapshot().limits }.getOrDefault(BytePlusUsageLimits()) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var turn by remember { mutableStateOf(initial.turnUsd?.toString().orEmpty()) }
    var daily by remember { mutableStateOf(initial.dayUsd?.toString().orEmpty()) }
    var monthly by remember { mutableStateOf(initial.monthUsd?.toString().orEmpty()) }
    var calls by remember { mutableStateOf(initial.dayCalls?.toString().orEmpty()) }
    var alert by remember { mutableStateOf(initial.alertUsd?.toString().orEmpty()) }
    var alertTokens by remember { mutableStateOf(initial.alertTokens?.toString().orEmpty()) }
        SettingsCard {
            Text("Alertas y límites locales", style = MaterialTheme.typography.titleMedium)
            Text("Opcionales. Solo bloquean nuevas llamadas BytePlus desde Nexus en este dispositivo. No limitan otras apps ni el gasto total de la cuenta. No cancelan trabajos ya enviados.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Activar límites BytePlus", Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
            @Composable fun field(label: String, value: String, change: (String) -> Unit) {
                StudioTextField(value, change, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            field("USD por turno · vacío = sin límite", turn) { turn = it }
            field("USD por día · vacío = sin límite", daily) { daily = it }
            field("USD por mes · vacío = sin límite", monthly) { monthly = it }
            field("Llamadas por día · vacío = sin límite", calls) { calls = it }
            field("Alerta diaria · USD estimados", alert) { alert = it }
            field("Alerta diaria · tokens", alertTokens) { alertTokens = it }
            FilledTonalButton(onClick = {
                runCatching {
                    fun amount(text: String): Double? = if (text.isBlank()) null else text.replace(',', '.').toDouble().also { require(it.isFinite() && it > 0) }
                    fun integer(text: String): Int? = if (text.isBlank()) null else text.toInt().also { require(it > 0) }
                    val tokenAlert = if (alertTokens.isBlank()) null else alertTokens.toLong().also { require(it > 0) }
                    store.ledger.settings(BytePlusUsageLimits(enabled, amount(turn), amount(daily), amount(monthly), integer(calls), amount(alert), tokenAlert))
                }.onSuccess { message = "Configuración guardada. Se aplica a la siguiente llamada BytePlus."; onSaved() }
                    .onFailure { message = "Introduce importes y cantidades positivos, o deja el campo vacío." }
            }) { Text("Guardar alertas y límites") }
            message?.let { InlineNotice(it) }
            val global = remember { AssistantSettingsStore(app) }
            Text("Presupuesto general del agente: ${if (global.budgetEnabled()) "activado" else "desactivado"}. Los límites propios del cerebro pueden prevalecer. Este apartado añade un control BytePlus independiente.", style = MaterialTheme.typography.bodySmall)
        }
}

private fun bpMoney(value: Double?) = value?.let { String.format(Locale.ROOT, "%.6f USD", it) } ?: "No disponible"
private fun bpStamp(value: Long?) = value?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "Nunca"
private fun JsonObject.word(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
@Composable private fun OfficialSection(title: String, data: JsonObject?, instant: Boolean = false, content: @Composable (JsonObject) -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(title, style = MaterialTheme.typography.titleSmall)
    if (data == null) Text("No disponible · sin lectura oficial", style = MaterialTheme.typography.bodyMedium)
    else {
        if (data.word("status") in listOf("ok", "stale")) content(data)
        if (data.word("status") != "ok") InlineNotice(data.word("error") ?: "Fuente no disponible.")
        data.word("updated_at")?.toLongOrNull()?.let { Text("Lectura: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)), style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
        if (!instant) data.word("start")?.let { Text("Periodo de la lectura: $it → ${data.word("end") ?: it}", style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
        data.word("source")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
    }
}
