@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vidadomu.nexus.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vidadomu.nexus.agent.core.llm.BrainAuth
import com.vidadomu.nexus.agent.core.llm.BrainAuthMode
import com.vidadomu.nexus.agent.core.llm.BrainProfile
import com.vidadomu.nexus.agent.core.media.MediaCapability
import com.vidadomu.nexus.agent.core.media.MediaMode
import com.vidadomu.nexus.agent.core.media.MediaModelConfig
import com.vidadomu.nexus.agent.core.media.MediaParamsText
import com.vidadomu.nexus.agent.core.media.MediaProviderConfig
import com.vidadomu.nexus.agent.core.media.MediaTemplates
import com.vidadomu.nexus.agent.core.media.MediaUploadMode
import com.vidadomu.nexus.provider.custom.CustomMediaStore
import com.vidadomu.nexus.ui.components.InlineNotice
import com.vidadomu.nexus.ui.components.StudioCredentialField
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.ScreenBackground
import com.vidadomu.nexus.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private fun int(text: String, default: Int): Int = text.trim().toIntOrNull() ?: default

/**
 * Alta y edición de un proveedor multimedia por configuración (texto/imagen a imagen o vídeo, escalado).
 * Describe el REST/JSON del servicio; no promete compatibilidad con protocolos que necesiten un adaptador propio.
 */
@Composable
internal fun MediaProviderEditorDialog(initial: MediaProviderConfig?, store: CustomMediaStore, onClose: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val isNew = initial == null
    val seed = initial ?: MediaTemplates.all.first().config.copy(id = "nuevo", name = "", baseUrl = "")
    var name by remember { mutableStateOf(if (isNew) "" else seed.name) }
    var baseUrl by remember { mutableStateOf(seed.baseUrl) }
    var authMode by remember { mutableStateOf(seed.auth.mode) }
    var authName by remember { mutableStateOf(seed.auth.name) }
    var authPrefix by remember { mutableStateOf(seed.auth.prefix) }
    var apiKey by remember { mutableStateOf("") }
    var clearKey by remember { mutableStateOf(false) }
    var healthPath by remember { mutableStateOf(seed.healthPath) }
    var headers by remember { mutableStateOf(seed.extraHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }) }
    var models by remember { mutableStateOf(seed.models) }
    var uploadMode by remember { mutableStateOf(seed.uploadMode) }
    var uploadPath by remember { mutableStateOf(seed.uploadPath) }
    var uploadField by remember { mutableStateOf(seed.uploadFieldName) }
    var uploadUrlPath by remember { mutableStateOf(seed.uploadUrlPath) }
    var poll by remember { mutableStateOf(seed.pollIntervalSeconds.toString()) }
    var wait by remember { mutableStateOf(seed.maxWaitMinutes.toString()) }
    var httpSeconds by remember { mutableStateOf(seed.httpTimeoutSeconds.toString()) }
    var uploadMb by remember { mutableStateOf(seed.maxUploadMb.toString()) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<MediaModelConfig?>(null) }
    var addingModel by remember { mutableStateOf(false) }
    val maskedKey = remember(initial?.id) { initial?.let { store.maskedKey(it.id) }.orEmpty() }

    fun assemble(): Pair<MediaProviderConfig?, String?> {
        val headerMap = headers.lines().map { it.trim() }.filter { it.isNotEmpty() }.associate { line ->
            val k = line.substringBefore(':', "").trim()
            if (k.isEmpty()) return null to "Cabecera mal escrita: «$line» (usa Nombre: valor)."
            k to line.substringAfter(':', "").trim()
        }
        val cfg = MediaProviderConfig(
            id = initial?.id ?: store.uniqueId(BrainProfile.slug(name)), name = name.trim(), baseUrl = baseUrl.trim().trimEnd('/'),
            auth = BrainAuth(authMode, authName.trim(), authPrefix), extraHeaders = headerMap, models = models, healthPath = healthPath.trim(),
            uploadMode = uploadMode, uploadPath = uploadPath.trim(), uploadFieldName = uploadField.trim().ifBlank { "file" }, uploadUrlPath = uploadUrlPath.trim().ifBlank { "url" },
            pollIntervalSeconds = int(poll, 5), maxWaitMinutes = int(wait, 30), httpTimeoutSeconds = int(httpSeconds, 60), maxUploadMb = int(uploadMb, 20),
        )
        return cfg to cfg.problems().firstOrNull()
    }

    FullscreenDialog({ if (!busy) onClose(null) }) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space4), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onClose(null) }) { Icon(Icons.Default.Close, "Cerrar sin guardar") }
                    Text(if (isNew) "Añadir proveedor API" else name.ifBlank { initial?.name.orEmpty() }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    if (!isNew) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más acciones") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Duplicar") }, onClick = { menu = false; onClose(store.duplicate(initial!!.id)?.id) })
                            DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = NexusTokens.screenMargin), verticalArrangement = Arrangement.spacedBy(NexusTokens.space16)) {
                    if (isNew) EditorSection("Plantilla", "Punto de partida orientativo. Revisa rutas y campos con la documentación del servicio.") {
                        ChipChoice(MediaTemplates.all.map { it.label to it.label }, "") { label ->
                            val t = MediaTemplates.all.first { it.label == label }
                            name = t.config.name; baseUrl = if (t.config.baseUrl == "https://") "" else t.config.baseUrl
                            authMode = t.config.auth.mode; authName = t.config.auth.name; authPrefix = t.config.auth.prefix
                            headers = t.config.extraHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }; models = t.config.models
                            message = t.note to false
                        }
                    }
                    EditorSection("Proveedor") {
                        StudioTextField(name, { name = it }, shape = NexusTokens.field, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        StudioTextField(baseUrl, { baseUrl = it.trim() }, shape = NexusTokens.field, label = { Text("URL base (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    }
                    EditorSection("Clave", "Se guarda cifrada en este teléfono. Nunca se muestra, se registra ni se sube.") {
                        ChipChoice(listOf(BrainAuthMode.BEARER to "Bearer", BrainAuthMode.HEADER to "Cabecera", BrainAuthMode.QUERY to "Parámetro", BrainAuthMode.NONE to "Sin clave"), authMode) { authMode = it }
                        if (authMode == BrainAuthMode.HEADER || authMode == BrainAuthMode.QUERY)
                            StudioTextField(authName, { authName = it.trim() }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (authMode == BrainAuthMode.HEADER) "Nombre de la cabecera" else "Nombre del parámetro") })
                        if (authMode == BrainAuthMode.HEADER)
                            StudioTextField(authPrefix, { authPrefix = it }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Texto antes de la clave (p. ej. «Key »)") })
                        if (authMode != BrainAuthMode.NONE) {
                            StudioCredentialField(apiKey, { apiKey = it; clearKey = false }, shape = NexusTokens.field, label = { Text(if (maskedKey.isNotBlank()) "Clave de API ($maskedKey)" else "Clave de API") })
                            if (maskedKey.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(clearKey, { clearKey = it }); Text("Borrar la clave guardada", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    EditorSection("Modelos", "Cada modelo describe su petición, su respuesta y sus parámetros. Aparecen en el Lienzo y los ve el agente.") {
                        models.forEach { m ->
                            Row(Modifier.fillMaxWidth().clickable { editingModel = m }.padding(vertical = NexusTokens.space8), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.name.ifBlank { m.id }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    Text("${m.capability.label} · ${if (m.mode == MediaMode.SYNC) "síncrono" else "asíncrono"} · ${m.params.size} parámetros", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                }
                            }
                        }
                        TextButton(onClick = { addingModel = true }, contentPadding = PaddingValues(0.dp)) { Text("+ Añadir modelo") }
                    }
                    FoldSection("Avanzado") {
                        StudioTextField(healthPath, { healthPath = it.trim() }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Ruta para probar la conexión (GET, opcional)") })
                        StudioTextField(headers, { headers = it }, shape = NexusTokens.field, minLines = 2, modifier = Modifier.fillMaxWidth(), label = { Text("Cabeceras extra (Nombre: valor, una por línea)") })
                        Text("Archivos adjuntos", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        ChipChoice(listOf(MediaUploadMode.DATA_URI to "Incrustar en la petición", MediaUploadMode.ENDPOINT to "Subir a un endpoint"), uploadMode) { uploadMode = it }
                        if (uploadMode == MediaUploadMode.ENDPOINT) {
                            StudioTextField(uploadPath, { uploadPath = it.trim() }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Ruta de subida (POST multipart)") })
                            Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                                StudioTextField(uploadField, { uploadField = it.trim() }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.weight(1f), label = { Text("Campo del archivo") })
                                StudioTextField(uploadUrlPath, { uploadUrlPath = it.trim() }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.weight(1f), label = { Text("Ruta de la URL") })
                            }
                        }
                    }
                    FoldSection("Límites de este proveedor") {
                        Text("0 = sin límite. Aquí mandas tú.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        val number = KeyboardOptions(keyboardType = KeyboardType.Number)
                        StudioTextField(poll, { poll = it.filter(Char::isDigit) }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Segundos entre consultas") }, keyboardOptions = number)
                        StudioTextField(wait, { wait = it.filter(Char::isDigit) }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Espera máxima por trabajo (min)") }, keyboardOptions = number)
                        StudioTextField(httpSeconds, { httpSeconds = it.filter(Char::isDigit) }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Tiempo de espera de cada petición (s)") }, keyboardOptions = number)
                        StudioTextField(uploadMb, { uploadMb = it.filter(Char::isDigit) }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Tamaño máximo de adjunto (MB)") }, keyboardOptions = number)
                    }
                    message?.let { (text, isError) -> InlineNotice(text, isError = isError) }
                    Spacer(Modifier.height(NexusTokens.space8))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.screenMargin, vertical = NexusTokens.space8), horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                    OutlinedButton(enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                        val (cfg, problem) = assemble()
                        if (cfg == null || problem != null) { message = (problem ?: "Revisa los datos.") to true; return@OutlinedButton }
                        busy = true; message = "Probando…" to false
                        scope.launch {
                            try {
                                val key = apiKey.trim().ifBlank { store.apiKey(cfg.id) }
                                val r = store.test(cfg, key)
                                message = r.fold({ "Conexión correcta con ${cfg.name}." to false }, { (it.message ?: "No se pudo conectar.") to true })
                            } finally { busy = false }
                        }
                    }) { Text(if (busy) "Probando…" else "Probar conexión") }
                    Button(enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                        val (cfg, problem) = assemble()
                        if (cfg == null || problem != null) { message = (problem ?: "Revisa los datos.") to true; return@Button }
                        try { store.save(cfg, when { clearKey -> ""; apiKey.isNotBlank() -> apiKey; else -> null }); onClose(cfg.id) }
                        catch (e: IllegalArgumentException) { message = e.message.orEmpty() to true }
                        catch (e: IllegalStateException) { message = e.message.orEmpty() to true }
                    }) { Text("Guardar") }
                }
            }
        }
    }
    if (addingModel || editingModel != null) MediaModelEditorDialog(editingModel, models.map { it.id }.toSet() - setOfNotNull(editingModel?.id),
        onDelete = editingModel?.let { old -> { models = models.filter { it.id != old.id }; editingModel = null; addingModel = false } },
        onDismiss = { editingModel = null; addingModel = false },
        onSave = { saved ->
            val old = editingModel
            models = if (old == null) models + saved else models.map { if (it.id == old.id) saved else it }
            editingModel = null; addingModel = false
        })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("¿Eliminar «${initial?.name.orEmpty()}»?") }, text = { Text("Se borra el proveedor, sus modelos y su clave de este teléfono. Lo ya generado se conserva.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; initial?.let { store.delete(it.id) }; onClose(null) }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}

/** Un modelo: ruta, petición JSON, respuesta (síncrona o con consulta), estados, parámetros y precio. */
@Composable
private fun MediaModelEditorDialog(initial: MediaModelConfig?, takenIds: Set<String>, onDelete: (() -> Unit)?, onDismiss: () -> Unit, onSave: (MediaModelConfig) -> Unit) {
    val seed = initial ?: MediaTemplates.all.first().config.models.first().copy(id = "", name = "")
    var name by remember { mutableStateOf(seed.name) }
    var id by remember { mutableStateOf(seed.id) }
    var providerModelId by remember { mutableStateOf(seed.providerModelId) }
    var capability by remember { mutableStateOf(seed.capability) }
    var createPath by remember { mutableStateOf(seed.createPath) }
    var method by remember { mutableStateOf(seed.method) }
    var template by remember { mutableStateOf(seed.requestTemplate) }
    var mode by remember { mutableStateOf(seed.mode) }
    var taskIdPath by remember { mutableStateOf(seed.taskIdPath) }
    var pollPath by remember { mutableStateOf(seed.pollPath) }
    var statusPath by remember { mutableStateOf(seed.statusPath) }
    var successValues by remember { mutableStateOf(seed.successValues.joinToString(", ")) }
    var failureValues by remember { mutableStateOf(seed.failureValues.joinToString(", ")) }
    var progressPath by remember { mutableStateOf(seed.progressPath) }
    var errorPath by remember { mutableStateOf(seed.errorPath) }
    var resultPaths by remember { mutableStateOf(seed.resultPaths.joinToString("\n")) }
    var paramsText by remember { mutableStateOf(MediaParamsText.format(seed.params)) }
    var maxImages by remember { mutableStateOf(seed.maxImages.toString()) }
    var price by remember { mutableStateOf(seed.priceUsd?.toString().orEmpty()) }
    var priceUnit by remember { mutableStateOf(seed.priceUnit) }
    var error by remember { mutableStateOf<String?>(null) }

    FullscreenDialog(onDismiss) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = NexusTokens.space4), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                    Text(if (initial == null) "Nuevo modelo" else name.ifBlank { "Modelo" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    onDelete?.let { TextButton(onClick = it) { Text("Quitar", color = MaterialTheme.colorScheme.error) } }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = NexusTokens.screenMargin), verticalArrangement = Arrangement.spacedBy(NexusTokens.space16)) {
                    EditorSection("Modelo") {
                        StudioTextField(name, { name = it }, shape = NexusTokens.field, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        StudioTextField(id, { id = it.trim() }, shape = NexusTokens.field, label = { Text("ID del modelo en el proveedor") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("Es el valor de {{model}} en la petición.") })
                        ChipChoice(MediaCapability.values().map { it to it.label }, capability) { capability = it }
                    }
                    EditorSection("Petición", "JSON con marcadores: {{prompt}} {{model}} {{image_url}} {{image_urls}} {{video_url}} y uno por cada parámetro. Un marcador sin valor omite su clave.") {
                        Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8), verticalAlignment = Alignment.CenterVertically) {
                            ChipChoice(listOf("POST" to "POST", "PUT" to "PUT", "GET" to "GET"), method.uppercase()) { method = it }
                        }
                        StudioTextField(createPath, { createPath = it.trim() }, shape = NexusTokens.field, label = { Text("Ruta de creación (/v1/…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        StudioTextField(template, { template = it }, shape = NexusTokens.field, minLines = 4, modifier = Modifier.fillMaxWidth(), label = { Text("Cuerpo JSON") },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    }
                    EditorSection("Respuesta") {
                        ChipChoice(listOf(MediaMode.SYNC to "Síncrono (trae la URL)", MediaMode.ASYNC to "Asíncrono (id + consulta)"), mode) { mode = it }
                        if (mode == MediaMode.ASYNC) {
                            StudioTextField(taskIdPath, { taskIdPath = it.trim() }, shape = NexusTokens.field, label = { Text("Ruta del id de la tarea (data.id)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            StudioTextField(pollPath, { pollPath = it.trim() }, shape = NexusTokens.field, label = { Text("Ruta de consulta (/v1/tasks/{{task_id}})") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            StudioTextField(statusPath, { statusPath = it.trim() }, shape = NexusTokens.field, label = { Text("Ruta del estado (status)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            StudioTextField(successValues, { successValues = it }, shape = NexusTokens.field, label = { Text("Estados de éxito (separados por comas)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            StudioTextField(failureValues, { failureValues = it }, shape = NexusTokens.field, label = { Text("Estados de fallo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            StudioTextField(progressPath, { progressPath = it.trim() }, shape = NexusTokens.field, label = { Text("Ruta del progreso (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        StudioTextField(errorPath, { errorPath = it.trim() }, shape = NexusTokens.field, label = { Text("Ruta del mensaje de error (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        StudioTextField(resultPaths, { resultPaths = it }, shape = NexusTokens.field, minLines = 2, modifier = Modifier.fillMaxWidth(), label = { Text("Rutas de la URL del resultado (una por línea; * recorre listas)") })
                    }
                    EditorSection("Parámetros", "Una línea cada uno: clave | Etiqueta | tipo | defecto | opción1, opción2 | avanzado. Clave* = obligatorio.") {
                        StudioTextField(paramsText, { paramsText = it }, shape = NexusTokens.field, minLines = 3, modifier = Modifier.fillMaxWidth(), label = { Text("Parámetros editables") },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                        if (capability.needsImage) StudioTextField(maxImages, { maxImages = it.filter(Char::isDigit) }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Imágenes de referencia máximas") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    FoldSection("Precio (opcional)") {
                        Row(horizontalArrangement = Arrangement.spacedBy(NexusTokens.space8)) {
                            StudioTextField(price, { price = it }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.weight(1f), label = { Text("USD por unidad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            StudioTextField(priceUnit, { priceUnit = it }, shape = NexusTokens.field, singleLine = true, modifier = Modifier.weight(1f), label = { Text("Unidad (imagen, s…)") })
                        }
                        Text("Lo indicas tú: Nexus lo muestra como estimación y no lo verifica con el proveedor.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    error?.let { InlineNotice(it) }
                    Spacer(Modifier.height(NexusTokens.space8))
                }
                Button(modifier = Modifier.fillMaxWidth().padding(horizontal = NexusTokens.screenMargin, vertical = NexusTokens.space8), onClick = {
                    val params = MediaParamsText.parse(paramsText).getOrElse { error = it.message; return@Button }
                    val p = price.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: run { error = "El precio no es un número."; return@Button } }
                    val model = MediaModelConfig(
                        id = id.trim(), name = name.trim(), capability = capability, providerModelId = "", createPath = createPath.trim(), method = method.uppercase(),
                        requestTemplate = template.trim(), params = params, mode = mode, taskIdPath = taskIdPath, pollPath = pollPath, statusPath = statusPath,
                        successValues = MediaParamsText.list(successValues), failureValues = MediaParamsText.list(failureValues), progressPath = progressPath, errorPath = errorPath,
                        resultPaths = MediaParamsText.list(resultPaths), maxImages = int(maxImages, 1), priceUsd = p, priceUnit = priceUnit.trim(),
                    )
                    if (model.id in takenIds) { error = "Ya hay un modelo con ese ID."; return@Button }
                    // Se valida dentro de un proveedor de relleno: los mensajes son los mismos que al guardar.
                    val problem = MediaProviderConfig("validar", "validar", "https://validar.invalid", models = listOf(model)).problems().firstOrNull()
                    if (problem != null) { error = problem.substringAfter(": ", problem); return@Button }
                    onSave(model)
                }) { Text("Guardar modelo") }
            }
        }
    }
}
