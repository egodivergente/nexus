@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vidadomu.nexus.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vidadomu.nexus.agent.core.config.LimitOverrides
import com.vidadomu.nexus.agent.core.llm.BrainAuth
import com.vidadomu.nexus.agent.core.llm.BrainAuthMode
import com.vidadomu.nexus.agent.core.llm.BrainCapabilities
import com.vidadomu.nexus.agent.core.llm.BrainDiscovery
import com.vidadomu.nexus.agent.core.llm.BrainPresets
import com.vidadomu.nexus.agent.core.llm.BrainProfile
import com.vidadomu.nexus.agent.core.llm.BrainProtocol
import com.vidadomu.nexus.agent.core.llm.LlmException
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.StudioCredentialField
import com.vidadomu.nexus.ui.components.StudioFilterChip
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.ScreenBackground
import com.vidadomu.nexus.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Título pequeño de sección: el editor se organiza con aire y rótulos discretos, sin cajas ni adornos. */
@Composable
internal fun EditorSection(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
        content()
    }
}

/** Una sección que se despliega con un toque; lo avanzado y los límites no estorban hasta que se piden. */
@Composable
internal fun FoldSection(title: String, summary: String = "", content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
        TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(NexusTokens.space8))
            Text(if (open) "−" else summary.ifBlank { "+" }, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
        if (open) content()
    }
}

@Composable
internal fun <T> ChipChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
        options.forEach { (v, label) -> StudioFilterChip(selected == v, { onSelect(v) }, label = { Text(label) }) }
    }
}

private fun num(text: String): Int? = text.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
private fun dec(text: String): Double? = text.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

/**
 * Alta y edición de un cerebro (LLM): BytePlus, proveedores compatibles con OpenAI, Responses API o REST de solo texto.
 * [onClose] recibe el id guardado/duplicado o null si no cambió nada. Las claves nunca se muestran: solo su máscara.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrainEditorDialog(initial: BrainProfile?, store: BrainStore, onClose: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val isNew = initial == null
    val seed = initial ?: BrainPresets.templates.first().profile.copy(id = "nuevo", name = "")
    var protocol by remember { mutableStateOf(seed.protocol) }
    var name by remember { mutableStateOf(if (isNew) "" else seed.name) }
    var baseUrl by remember { mutableStateOf(seed.baseUrl) }
    var endpointPath by remember { mutableStateOf(seed.endpointPath) }
    var authMode by remember { mutableStateOf(seed.auth.mode) }
    var authName by remember { mutableStateOf(seed.auth.name) }
    var authPrefix by remember { mutableStateOf(seed.auth.prefix) }
    var apiKey by remember { mutableStateOf("") }
    var clearKey by remember { mutableStateOf(false) }
    var modelId by remember { mutableStateOf(seed.modelId) }
    var models by remember { mutableStateOf(seed.models) }
    var caps by remember { mutableStateOf(seed.capabilities) }
    var effort by remember { mutableStateOf(seed.reasoningEffort) }
    var thinking by remember { mutableStateOf(seed.thinking) }
    var temperature by remember { mutableStateOf(seed.temperature?.toString().orEmpty()) }
    var extraBody by remember { mutableStateOf(seed.extraBody) }
    var headers by remember { mutableStateOf(seed.extraHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }) }
    var inRate by remember { mutableStateOf(seed.inputUsdPerM?.toString().orEmpty()) }
    var outRate by remember { mutableStateOf(seed.outputUsdPerM?.toString().orEmpty()) }
    var store0 by remember { mutableStateOf(seed.storeOnServer) }
    var restTemplate by remember { mutableStateOf(seed.restRequestTemplate) }
    var restPath by remember { mutableStateOf(seed.restTextPath) }
    var lRounds by remember { mutableStateOf(seed.limits.maxToolRounds?.toString().orEmpty()) }
    var lTimeout by remember { mutableStateOf(seed.limits.turnTimeoutSeconds?.toString().orEmpty()) }
    var lTokens by remember { mutableStateOf(seed.limits.maxTokens?.toString().orEmpty()) }
    var lTurnUsd by remember { mutableStateOf(seed.limits.turnUsd?.toString().orEmpty()) }
    var lDailyUsd by remember { mutableStateOf(seed.limits.dailyUsd?.toString().orEmpty()) }
    var lCalls by remember { mutableStateOf(seed.limits.maxCallsPerTurn?.toString().orEmpty()) }
    var lHttp by remember { mutableStateOf(seed.limits.httpTimeoutSeconds?.toString().orEmpty()) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // texto, esError
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val maskedKey = remember(initial?.id) { initial?.let { store.maskedKey(it.id) }.orEmpty() }

    /** Construye el perfil con lo escrito; devuelve el primer problema en forma de mensaje. */
    fun assemble(): Pair<BrainProfile?, String?> {
        fun bad(m: String) = null to m
        val t = dec(temperature); if (temperature.isNotBlank() && t == null) return bad("La temperatura no es un número.")
        val i = dec(inRate); val o = dec(outRate)
        if ((inRate.isNotBlank() && i == null) || (outRate.isNotBlank() && o == null)) return bad("Una tarifa no es un número.")
        val headerMap = headers.lines().map { it.trim() }.filter { it.isNotEmpty() }.associate { line ->
            val k = line.substringBefore(':', "").trim(); val v = line.substringAfter(':', "").trim()
            if (k.isEmpty()) return bad("Cabecera mal escrita: «$line» (usa Nombre: valor).")
            k to v
        }
        fun n(s: String, what: String): Pair<Int?, String?> = if (s.isBlank()) null to null else num(s)?.let { it to null } ?: (null to "«$what» no es un número entero.")
        val parsed = listOf(n(lRounds, "pasos"), n(lTimeout, "tiempo por mensaje"), n(lTokens, "tokens"), n(lCalls, "llamadas"), n(lHttp, "tiempo HTTP"))
        parsed.firstNotNullOfOrNull { it.second }?.let { return bad(it) }
        val tu = dec(lTurnUsd); val du = dec(lDailyUsd)
        if ((lTurnUsd.isNotBlank() && tu == null) || (lDailyUsd.isNotBlank() && du == null)) return bad("Un importe de gasto no es un número.")
        val id = initial?.id ?: store.uniqueId(BrainProfile.slug(name))
        val p = BrainProfile(
            id = id, name = name.trim(), protocol = protocol, baseUrl = baseUrl.trim().trimEnd('/'), endpointPath = endpointPath.trim(),
            auth = BrainAuth(authMode, authName.trim(), authPrefix), modelId = modelId.trim(), models = models, capabilities = caps,
            reasoningEffort = effort, thinking = thinking, temperature = t, extraBody = extraBody.trim(), extraHeaders = headerMap,
            inputUsdPerM = i, outputUsdPerM = o, storeOnServer = store0, restRequestTemplate = restTemplate.trim(), restTextPath = restPath.trim(),
            limits = LimitOverrides(parsed[0].first, parsed[1].first, parsed[2].first, tu, du, parsed[3].first, parsed[4].first),
            builtin = initial?.builtin == true,
        )
        return p to p.problems().firstOrNull()
    }

    fun keyForCalls(id: String) = apiKey.trim().ifBlank { store.apiKey(id) }

    FullscreenDialog({ if (!busy) onClose(null) }) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space4), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onClose(null) }) { Icon(Icons.Default.Close, "Cerrar sin guardar") }
                    Text(if (isNew) "Añadir proveedor" else name.ifBlank { initial?.name.orEmpty() }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    if (!isNew) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más acciones") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Duplicar") }, onClick = { menu = false; onClose(store.duplicate(initial!!.id)?.id) })
                            if (initial?.builtin != true) DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = NexusTokens.screenMargin),
                    verticalArrangement = Arrangement.spacedBy(NexusTokens.space16)) {
                    if (isNew) EditorSection("Plantilla", "Rellena los datos habituales; puedes cambiar todo.") {
                        ChipChoice(BrainPresets.templates.map { it.label to it.label }, "") { label ->
                            val tpl = BrainPresets.templates.first { it.label == label }.profile
                            protocol = tpl.protocol; name = tpl.name; baseUrl = tpl.baseUrl; endpointPath = tpl.endpointPath
                            authMode = tpl.auth.mode; authName = tpl.auth.name; authPrefix = tpl.auth.prefix; caps = tpl.capabilities
                            restTemplate = tpl.restRequestTemplate; restPath = tpl.restTextPath; message = null
                        }
                    }
                    EditorSection("Proveedor") {
                        StudioTextField(name, { name = it }, shape = NexusTokens.field, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        StudioTextField(baseUrl, { baseUrl = it.trim() }, shape = NexusTokens.field, label = { Text("URL base (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                        ChipChoice(listOf(BrainProtocol.OPENAI_CHAT to "OpenAI compatible", BrainProtocol.OPENAI_RESPONSES to "Responses API", BrainProtocol.CUSTOM_REST to "REST (solo texto)"), protocol) {
                            protocol = it; if (it == BrainProtocol.CUSTOM_REST) caps = caps.copy(tools = false, streaming = false)
                        }
                        StudioTextField(endpointPath, { endpointPath = it.trim() }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Endpoint (opcional)") }, placeholder = { Text(when (protocol) { BrainProtocol.OPENAI_CHAT -> "/chat/completions"; BrainProtocol.OPENAI_RESPONSES -> "/responses"; else -> "/ruta" }) })
                    }
                    EditorSection("Clave", "Se guarda cifrada en este teléfono. Nunca se muestra, se registra ni se sube.") {
                        ChipChoice(listOf(BrainAuthMode.BEARER to "Bearer", BrainAuthMode.HEADER to "Cabecera", BrainAuthMode.QUERY to "Parámetro", BrainAuthMode.NONE to "Sin clave"), authMode) { authMode = it }
                        if (authMode == BrainAuthMode.HEADER || authMode == BrainAuthMode.QUERY)
                            StudioTextField(authName, { authName = it.trim() }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (authMode == BrainAuthMode.HEADER) "Nombre de la cabecera (x-api-key)" else "Nombre del parámetro (key)") })
                        if (authMode == BrainAuthMode.HEADER)
                            StudioTextField(authPrefix, { authPrefix = it }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                label = { Text("Texto antes de la clave (opcional, p. ej. «Key »)") })
                        if (authMode != BrainAuthMode.NONE) {
                            StudioCredentialField(apiKey, { apiKey = it; clearKey = false }, shape = NexusTokens.field,
                                label = { Text(if (maskedKey.isNotBlank()) "Clave de API ($maskedKey)" else "Clave de API") })
                            if (maskedKey.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(clearKey, { clearKey = it }); Text("Borrar la clave guardada", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    EditorSection("Modelo") {
                        StudioTextField(modelId, { modelId = it.trim() }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            label = { Text("ID del modelo") }, supportingText = { Text(if (models.isEmpty()) "Escríbelo o pulsa «Descubrir»." else "${models.size} modelos conocidos") })
                        val suggestions = models.filter { modelId.isBlank() || it.contains(modelId, true) }.filter { it != modelId }.take(8)
                        if (suggestions.isNotEmpty()) ChipChoice(suggestions.map { it to it }, "") { modelId = it }
                        if (protocol != BrainProtocol.CUSTOM_REST) TextButton(enabled = !busy && baseUrl.isNotBlank(), onClick = {
                            val (p, problem) = assemble()
                            if (p == null) { message = (problem ?: "Revisa los datos.") to true; return@TextButton }
                            busy = true; message = null
                            scope.launch {
                                try {
                                    val found = BrainDiscovery.listModels(p, { keyForCalls(p.id) })
                                    models = found
                                    message = (if (found.isEmpty()) "El proveedor no devolvió modelos; escribe el ID a mano." else "${found.size} modelos encontrados.") to found.isEmpty()
                                } catch (e: LlmException) { message = e.message.orEmpty() to true } finally { busy = false }
                            }
                        }) { Text("Descubrir modelos") }
                    }
                    EditorSection("Capacidades", "Lo que este modelo sabe hacer. «Herramientas» es imprescindible para que el agente ejecute acciones.") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
                            StudioFilterChip(caps.tools, { caps = caps.copy(tools = !caps.tools) }, label = { Text("Herramientas") })
                            StudioFilterChip(caps.vision, { caps = caps.copy(vision = !caps.vision) }, label = { Text("Visión") })
                            StudioFilterChip(caps.reasoning, { caps = caps.copy(reasoning = !caps.reasoning) }, label = { Text("Razonamiento") })
                            StudioFilterChip(caps.streaming, { caps = caps.copy(streaming = !caps.streaming) }, label = { Text("Streaming") })
                            StudioFilterChip(caps.jsonSchema, { caps = caps.copy(jsonSchema = !caps.jsonSchema) }, label = { Text("JSON estructurado") })
                        }
                        if (!caps.tools) Text("Sin herramientas el agente solo podrá conversar: no preparará ni enviará encargos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (caps.reasoning && protocol != BrainProtocol.CUSTOM_REST) EditorSection("Razonamiento", "Vacío = el valor por defecto del modelo.") {
                        ChipChoice(listOf<Pair<String?, String>>(null to "Esfuerzo: auto", "minimal" to "mínimo", "low" to "bajo", "medium" to "medio", "high" to "alto"), effort) { effort = it }
                        ChipChoice(listOf<Pair<String?, String>>(null to "Pensar: auto", "enabled" to "activado", "disabled" to "desactivado"), thinking) { thinking = it }
                        if (thinking == "disabled" && effort != null) Text("El esfuerzo se ignora con el pensamiento desactivado.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    if (protocol == BrainProtocol.CUSTOM_REST) EditorSection("Petición REST", "Marcadores: {{model}} {{system}} {{prompt}} {{messages}}. Sin herramientas ni imágenes.") {
                        StudioTextField(restTemplate, { restTemplate = it }, shape = NexusTokens.field, minLines = 3, modifier = Modifier.fillMaxWidth(), label = { Text("Cuerpo JSON") })
                        StudioTextField(restPath, { restPath = it.trim() }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Ruta del texto en la respuesta") })
                    }
                    FoldSection("Avanzado") {
                        StudioTextField(temperature, { temperature = it }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Temperatura (vacío = la del modelo)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        StudioTextField(extraBody, { extraBody = it }, shape = NexusTokens.field, minLines = 2, modifier = Modifier.fillMaxWidth(), label = { Text("Parámetros extra (objeto JSON)") },
                            supportingText = { Text("Se funden en la petición; no pisan modelo, mensajes ni herramientas.") })
                        StudioTextField(headers, { headers = it }, shape = NexusTokens.field, minLines = 2, modifier = Modifier.fillMaxWidth(), label = { Text("Cabeceras extra (Nombre: valor, una por línea)") })
                        if (protocol == BrainProtocol.OPENAI_RESPONSES) ChipChoice(listOf<Pair<Boolean?, String>>(null to "Guardar en servidor: por defecto", false to "no guardar", true to "guardar"), store0) { store0 = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                            StudioTextField(inRate, { inRate = it }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.weight(1f), label = { Text("USD/M entrada") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            StudioTextField(outRate, { outRate = it }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.weight(1f), label = { Text("USD/M salida") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                    }
                    FoldSection("Límites de este proveedor") {
                        Text("Vacío = el valor global (Ajustes → Límites). 0 = sin límite. Aquí mandas tú, no el prompt.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        val number = KeyboardOptions(keyboardType = KeyboardType.Number)
                        StudioTextField(lRounds, { lRounds = it.filter(Char::isDigit) }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Pasos seguidos por mensaje") }, keyboardOptions = number)
                        StudioTextField(lTimeout, { lTimeout = it.filter(Char::isDigit) }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Tiempo máximo por mensaje (s)") }, keyboardOptions = number)
                        StudioTextField(lTokens, { lTokens = it.filter(Char::isDigit) }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Máx. tokens de salida") }, keyboardOptions = number)
                        StudioTextField(lCalls, { lCalls = it.filter(Char::isDigit) }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Llamadas al LLM por mensaje") }, keyboardOptions = number)
                        StudioTextField(lHttp, { lHttp = it.filter(Char::isDigit) }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Tiempo de espera de cada petición (s)") }, keyboardOptions = number)
                        Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                            StudioTextField(lTurnUsd, { lTurnUsd = it }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.weight(1f), label = { Text("Gasto/mensaje USD") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            StudioTextField(lDailyUsd, { lDailyUsd = it }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.weight(1f), label = { Text("Gasto/día USD") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                    }
                    message?.let { (text, isError) -> InlineNotice(text, isError = isError) }
                    Spacer(Modifier.height(NexusTokens.space8))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.screenMargin, vertical = NexusTokens.space8), horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    OutlinedButton(enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                        val (p, problem) = assemble()
                        if (p == null || problem != null) { message = (problem ?: "Revisa los datos.") to true; return@OutlinedButton }
                        busy = true; message = "Probando…" to false
                        scope.launch {
                            try {
                                val r = BrainDiscovery.test(p, { keyForCalls(p.id) })
                                if (r.models.isNotEmpty()) models = r.models
                                message = r.message to !r.ok
                            } finally { busy = false }
                        }
                    }) { Text(if (busy) "Probando…" else "Probar conexión") }
                    Button(enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                        val (p, problem) = assemble()
                        if (p == null || problem != null) { message = (problem ?: "Revisa los datos.") to true; return@Button }
                        try {
                            store.save(p, when { clearKey -> ""; apiKey.isNotBlank() -> apiKey; else -> null })
                            onClose(p.id)
                        } catch (e: IllegalArgumentException) { message = e.message.orEmpty() to true }
                        catch (e: IllegalStateException) { message = e.message.orEmpty() to true }
                    }) { Text("Guardar") }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("¿Eliminar «${initial?.name.orEmpty()}»?") },
        text = { Text("Se borra el proveedor y su clave de este teléfono. Los demás no se tocan.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; initial?.let { store.delete(it.id) }; onClose(null) }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}
