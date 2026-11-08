@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.vidadomu.nexus.assistant.story

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.assets.storyCommand
import com.vidadomu.nexus.agent.core.assets.storyView
import com.vidadomu.nexus.assistant.AgentMediaStore
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.library.Thumb
import com.vidadomu.nexus.ui.library.rememberThumb
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

private fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.number(key: String): Long = (get(key) as? JsonPrimitive)?.longOrNull ?: 0
private fun JsonObject.rows(key: String): List<JsonObject> = (get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
private fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: buildJsonObject {}
private fun id() = UUID.randomUUID().toString()
private fun command(action: String, block: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
    put("action", action); put("operation_id", id()); block()
}

/** All three views read the same AssetIndex. Drafts are local editing buffers, never a second script. */
@Composable
fun StoryWorkspaceScreen(
    projectId: String,
    onClose: () -> Unit,
    onAgent: () -> Unit,
    onGenerate: (String) -> Unit,
    onFlow: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val runtime = remember(app) { AssistantRuntime.get(app) }
    val index by runtime.assets.changes.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable(projectId) { mutableIntStateOf(0) }
    var sceneId by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var shotId by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var edit by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var linkTarget by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var linkAssetId by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    var search by rememberSaveable(projectId) { mutableStateOf("") }
    var inboxOnly by rememberSaveable(projectId) { mutableStateOf(false) }
    var materialType by rememberSaveable(projectId) { mutableStateOf("all") }
    var materialContext by rememberSaveable(projectId) { mutableStateOf("project") }
    var chosenOnly by rememberSaveable(projectId) { mutableStateOf(false) }
    var materialDetail by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var proposalDetail by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var taskPanel by rememberSaveable(projectId) { mutableStateOf(false) }
    var projectEditor by rememberSaveable(projectId) { mutableStateOf(false) }
    var elementPanel by rememberSaveable(projectId) { mutableStateOf(false) }
    var importScript by rememberSaveable(projectId) { mutableStateOf(false) }
    var agentMode by rememberSaveable(projectId) { mutableStateOf("proposal") }
    var agentScope by rememberSaveable(projectId) { mutableStateOf("fragment") }
    var preservedIds by rememberSaveable(projectId) { mutableStateOf("") }
    var agentSettings by remember { mutableStateOf(false) }
    val view = remember(index, projectId, sceneId, shotId) {
        runCatching { runtime.assets.storyView(projectId,
            sceneId?.takeIf { selected -> index.scenes.any { it.id == selected && it.projectId == projectId } },
            shotId?.takeIf { selected -> index.shots.any { it.id == selected && it.projectId == projectId } }) }
            .getOrElse { failure -> buildJsonObject { put("error", failure.message ?: "El proyecto ya no está disponible.") } }
    }
    val scenes = view.rows("scenes").sortedBy { it.number("order") }
    val shots = view.rows("shots").sortedBy { it.number("order") }
    val blocks = view.rows("blocks").sortedBy { it.number("order") }
    val links = view.rows("links")
    val project = index.projects.firstOrNull { it.id == projectId }
    val currentScene = scenes.firstOrNull { it.text("id") == sceneId }
    val currentShot = shots.firstOrNull { it.text("id") == shotId }
    val media = index.assets.filter { !it.libraryDeleted }
    fun select(scene: String? = sceneId, shot: String? = shotId, block: String? = null) {
        sceneId = scene; shotId = shot
        StorySelectionStore.select(StorySelection(projectId, scene, shot, block, agentMode, preservedIds.split('|').filter { it.isNotBlank() }.toSet()))
    }
    fun requestAgent(scene: String? = sceneId, shot: String? = shotId, block: String? = null) {
        select(scene, shot, block)
        StorySelectionStore.select(StorySelection(projectId, if (agentScope == "project") null else scene,
            if (agentScope == "fragment") shot else null, if (agentScope == "fragment") block else null,
            agentMode, preservedIds.split('|').filter { it.isNotBlank() }.toSet()))
        onAgent()
    }
    LaunchedEffect(scenes.map { it.text("id") }) {
        if (sceneId == null || scenes.none { it.text("id") == sceneId }) select(scenes.firstOrNull()?.text("id"), null)
    }
    LaunchedEffect(shots.map { it.text("id") }) { if (shotId != null && shots.none { it.text("id") == shotId }) select(shot = null) }
    fun run(input: JsonObject, done: (JsonObject) -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val result = runtime.assets.storyCommand(projectId, input, "user")
                if (result["ok"]?.jsonPrimitive?.booleanOrNull == false) snackbar.showSnackbar(result.text("error").ifBlank { result.text("message").ifBlank { "No se pudo aplicar el cambio." } })
                else done(result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { snackbar.showSnackbar(error.message ?: "No se pudo guardar. Tu borrador se conserva.") }
            finally { busy = false }
        }
    }
    val importMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            var count = 0
            try {
                for (uri in uris.distinct()) {
                    AgentMediaStore.persist(app, uri, context.contentResolver.getType(uri), projectId)
                    count++
                }
                snackbar.showSnackbar("$count archivos guardados en Por colocar")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { snackbar.showSnackbar("Importados $count. ${error.message.orEmpty()}") }
            finally { busy = false }
        }
    }
    BackHandler { if (shotId != null) select(shot = null) else onClose() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.imePadding().safeDrawingPadding(),
                topBar = {
                    Column {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (shotId != null) select(shot = null) else onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                            Column(Modifier.weight(1f).clickable { projectEditor = true }) {
                                Text(project?.name ?: "Proyecto", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(currentShot?.text("title")?.ifBlank { currentShot.text("code") } ?: "Guion vivo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { run(command("undo")) }, enabled = !busy) { Icon(Icons.Default.Undo, "Deshacer último cambio") }
                            Box {
                                IconButton(onClick = { tools = true }) { Icon(Icons.Default.MoreVert, "Opciones del proyecto") }
                                DropdownMenu(tools, { tools = false }) {
                                    DropdownMenuItem(text = { Text("Historia y criterios") }, onClick = { tools = false; projectEditor = true })
                                    DropdownMenuItem(text = { Text("Personajes y continuidad") }, onClick = { tools = false; elementPanel = true })
                                    DropdownMenuItem(text = { Text("Trabajo y propuestas") }, onClick = { tools = false; taskPanel = true })
                                    DropdownMenuItem(text = { Text("Importar material") }, onClick = { tools = false; importMedia.launch(arrayOf("image/*", "video/*", "audio/*", "text/plain")) })
                                    DropdownMenuItem(text = { Text("Importar o pegar guion") }, onClick = { tools = false; importScript = true })
                                    DropdownMenuItem(text = { Text("Alcance del agente") }, onClick = { tools = false; agentSettings = true })
                                    DropdownMenuItem(text = { Text("Trabajar con el agente") }, onClick = { tools = false; requestAgent() })
                                }
                            }
                        }
                        TabRow(selectedTabIndex = tab) {
                            listOf("Guion", "Storyboard", "Material").forEachIndexed { i, title -> Tab(tab == i, { tab = i }, text = { Text(title) }) }
                        }
                        if (scenes.isNotEmpty()) LazyRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            items(scenes, key = { it.text("id") }) { scene ->
                                FilterChip(scene.text("id") == sceneId, { select(scene.text("id"), null) }, label = { Text(scene.text("title").ifBlank { scene.text("code") }, maxLines = 1) })
                            }
                            item { IconButton(onClick = { edit = "scene:new" }, enabled = !busy) { Icon(Icons.Default.Add, "Nueva escena") } }
                        }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                val content = Modifier.fillMaxSize().padding(padding)
                if (view.text("error").isNotBlank()) Column(content.padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text(view.text("error")); TextButton(onClick = onClose) { Text("Volver a proyectos") }
                } else if (scenes.isEmpty() && tab != 2) {
                    Column(content.padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Text("Empieza donde tengas una idea", style = MaterialTheme.typography.headlineSmall)
                        Text("Una escena, una frase o una imagen. Puedes seguir cambiando la historia mientras trabajas.", modifier = Modifier.padding(vertical = 12.dp))
                        Button(onClick = { edit = "scene:new" }, enabled = !busy) { Icon(Icons.Default.Add, null); Text("Crear primera escena") }
                        TextButton(onClick = { importMedia.launch(arrayOf("image/*", "video/*", "audio/*", "text/plain")) }) { Text("Importar material") }
                    }
                } else when (tab) {
                    0 -> LazyColumn(content, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        currentScene?.let { scene -> item(key = "scene-header") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(scene.text("title").ifBlank { scene.text("code") }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = { run(command("move_scene") { put("scene_id", scene.text("id")); put("order", scene.number("order") - 1); put("expected_revision", scene.number("revision")) }) }) { Icon(Icons.Default.ChevronLeft, "Mover escena antes") }
                                IconButton(onClick = { run(command("move_scene") { put("scene_id", scene.text("id")); put("order", scene.number("order") + 1); put("expected_revision", scene.number("revision")) }) }) { Icon(Icons.Default.ChevronRight, "Mover escena después") }
                                IconButton(onClick = { edit = "scene:${scene.text("id")}" }) { Icon(Icons.Default.Edit, "Editar escena") }
                            }
                            scene.text("description").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            Row {
                                TextButton(onClick = { requestAgent(shot = null) }) { Text(if (agentMode == "proposal") "Pedir propuesta" else "Trabajar con agente") }
                                TextButton(onClick = { linkTarget = "scene:${scene.text("id")}" }) { Text("Referencias") }
                                IconButton(onClick = { agentSettings = true }) { Icon(Icons.Default.Tune, "Modo y alcance del agente") }
                            }
                            Text("${if (agentMode == "proposal") "Propuestas" else "Cambios directos"} · ${when (agentScope) { "project" -> "proyecto"; "scene" -> "escena"; else -> "selección" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } }
                        item(key = "insert-first") { InsertBar { kind -> edit = "$kind:new:0" } }
                        items(blocks.filter { it.text("scene_id") == sceneId }, key = { it.text("id") }) { block ->
                            val shot = shots.firstOrNull { it.text("id") == block.text("shot_id") }
                            if (shot != null) ShotTile(shot, media, links, { select(shot = shot.text("id")); edit = "shot:${shot.text("id")}" }, { select(shot = shot.text("id")); tab = 1 }, { delta -> run(command("move_shot") { put("shot_id", shot.text("id")); put("scene_id", sceneId); put("order", shot.number("order") + delta); put("expected_revision", shot.number("revision")) }) })
                            else Surface(tonalElevation = if (block.text("kind") in listOf("note", "idea")) 2.dp else 0.dp, shape = NexusTokens.thumbnail) {
                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(blockKindLabel(block.text("kind")) + if (block.text("narrative_state") == "story") " · Incorporado" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { edit = "block:${block.text("id")}" }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Edit, "Editar bloque", Modifier.size(18.dp)) }
                                    }
                                    if (block.text("speaker").isNotBlank()) Text(block.text("speaker"), style = MaterialTheme.typography.labelLarge)
                                    Text(block.text("text").ifBlank { "Referencia visual" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { edit = "block:${block.text("id")}" })
                                    LinkedThumbnails(links.filter { it.text("target_id") == block.text("id") }, media)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { run(command("move_block") { put("block_id", block.text("id")); put("scene_id", sceneId); put("order", block.number("order") - 1); put("expected_revision", block.number("revision")) }) }) { Icon(Icons.Default.KeyboardArrowUp, "Subir bloque") }
                                        IconButton(onClick = { run(command("move_block") { put("block_id", block.text("id")); put("scene_id", sceneId); put("order", block.number("order") + 1); put("expected_revision", block.number("revision")) }) }) { Icon(Icons.Default.KeyboardArrowDown, "Bajar bloque") }
                                        TextButton(onClick = { linkTarget = "block:${block.text("id")}" }) { Text("Material") }
                                        TextButton(onClick = { requestAgent(block = block.text("id"), shot = null) }) { Text("Agente") }
                                    }
                                }
                            }
                            InsertBar { kind -> edit = "$kind:new:${block.number("order") + 1}" }
                        }
                        // Legacy shots remain navigable even if they predate script blocks.
                        items(shots.filter { it.text("scene_id") == sceneId && blocks.none { b -> b.text("shot_id") == it.text("id") } }, key = { "legacy-${it.text("id")}" }) { shot ->
                            ShotTile(shot, media, links, { select(shot = shot.text("id")); edit = "shot:${shot.text("id")}" }, { select(shot = shot.text("id")); tab = 1 }, { delta -> run(command("move_shot") { put("shot_id", shot.text("id")); put("scene_id", sceneId); put("order", shot.number("order") + delta); put("expected_revision", shot.number("revision")) }) })
                        }
                    }
                    1 -> {
                        val shown = shots.filter { sceneId == null || it.text("scene_id") == sceneId }
                        Column(content) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${shown.size} planos", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                TextButton(onClick = { edit = "shot:new" }) { Icon(Icons.Default.Add, null); Text("Plano") }
                            }
                            LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.weight(1f), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(shown, key = { it.text("id") }) { shot -> ShotTile(shot, media, links, { select(shot = shot.text("id")); edit = "shot:${shot.text("id")}" }, { select(shot = shot.text("id")); tab = 0 }, { delta -> run(command("move_shot") { put("shot_id", shot.text("id")); put("scene_id", sceneId); put("order", shot.number("order") + delta); put("expected_revision", shot.number("revision")) }) }) }
                            }
                        }
                    }
                    2 -> {
                        val projectLinks = links.map { it.text("asset_id") }.toSet()
                        val contextTargets = when (materialContext) {
                            "project", "all" -> emptySet()
                            "scene" -> shots.filter { it.text("scene_id") == sceneId }.map { it.text("id") }.toSet() + blocks.filter { it.text("scene_id") == sceneId }.map { it.text("id") }.toSet() + setOfNotNull(sceneId)
                            "shot" -> setOfNotNull(shotId)
                            else -> setOf(materialContext)
                        }
                        val filtered = media.filter { asset ->
                            (materialContext == "all" || asset.projectId == projectId || asset.id in projectLinks) &&
                                (contextTargets.isEmpty() && materialContext in listOf("project", "all") || links.any { l -> l.text("asset_id") == asset.id && l.text("target_id") in contextTargets }) &&
                                (!inboxOnly || asset.projectId == projectId && asset.id !in projectLinks) &&
                                (!chosenOnly || links.any { l -> l.text("asset_id") == asset.id && l["selected"]?.jsonPrimitive?.booleanOrNull == true }) &&
                                (materialType == "all" || asset.type.name.equals(materialType, true)) &&
                                (search.isBlank() || asset.displayName.contains(search, true) || asset.tags.any { it.contains(search, true) } ||
                                    links.any { l -> l.text("asset_id") == asset.id && l.text("role").contains(search, true) })
                        }
                        Column(content) {
                            StudioTextField(search, { search = it }, placeholder = { Text("Buscar material, etiquetas o uso") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
                            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item { FilterChip(inboxOnly, { inboxOnly = !inboxOnly }, label = { Text("Por colocar") }) }
                                item { FilterChip(chosenOnly, { chosenOnly = !chosenOnly }, label = { Text("Elegido") }) }
                                items(listOf("all" to "Todo", "IMAGE" to "Imágenes", "VIDEO" to "Vídeos", "AUDIO" to "Audio")) { (type, label) -> FilterChip(materialType == type, { materialType = type }, label = { Text(label) }) }
                                item { TextButton(onClick = { importMedia.launch(arrayOf("image/*", "video/*", "audio/*", "text/plain")) }) { Text("Importar") } }
                            }
                            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(listOf("project" to "Proyecto", "scene" to "Escena", "shot" to "Plano", "all" to "Toda la Biblioteca")) { (value, label) -> FilterChip(materialContext == value, { materialContext = value }, label = { Text(label) }, enabled = value != "shot" || shotId != null) }
                                items(view.rows("characters") + view.rows("elements").filter { it.text("kind") == "character" }, key = { "character-${it.text("id")}" }) { character -> FilterChip(materialContext == character.text("id"), { materialContext = character.text("id") }, label = { Text(character.text("name")) }) }
                            }
                            LazyVerticalGrid(GridCells.Adaptive(120.dp), Modifier.weight(1f), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(filtered, key = { it.id }) { asset -> MaterialTile(asset, links.count { it.text("asset_id") == asset.id }, { materialDetail = asset.id }) }
                            }
                        }
                    }
                }
            }
        }
    }
    edit?.let { key ->
        val kind = key.substringBefore(':')
        val entityId = key.substringAfter(':').substringBefore(':')
        val target = when (kind) { "scene" -> scenes; "shot" -> shots; else -> blocks }.firstOrNull { it.text("id") == entityId }
        StoryEditorDialog(projectId, key, kind, target, sceneId, scenes, media, view.rows("effective_references"),
            busy = busy, onClose = { edit = null }, onSave = { fields ->
                val action = if (target == null) "create_${if (kind in listOf("action", "dialogue", "note", "reference", "material")) "block" else kind}" else "update_$kind"
                val mutation = command(action) {
                    fields.forEach { (k, v) -> put(k, v) }
                    if (target != null) { put("${kind}_id", entityId); put("expected_revision", fields["expected_revision"] ?: JsonPrimitive(target.number("revision"))) }
                    else {
                        sceneId?.let { put("scene_id", it) }
                        if (kind !in listOf("scene", "shot")) put("kind", kind)
                        key.substringAfterLast(':').toLongOrNull()?.let { put(if (kind == "shot") "block_order" else "order", it) }
                    }
                }
                val prefs = context.getSharedPreferences("story_editor_drafts_v1", Context.MODE_PRIVATE)
                val operationKey = "$projectId:$key:operation"
                val payload = JsonObject(mutation.filterKeys { it != "operation_id" }).toString()
                val operation = if (prefs.getString("$operationKey:payload", null) == payload) prefs.getString(operationKey, null) ?: id() else id()
                prefs.edit().putString(operationKey, operation).putString("$operationKey:payload", payload).commit()
                run(JsonObject(mutation + ("operation_id" to JsonPrimitive(operation)))) { result ->
                    prefs.edit().remove("$projectId:$key").remove(operationKey).remove("$operationKey:payload").apply()
                    edit = null
                    result.text("shot_id").takeIf { it.isNotBlank() }?.let { select(shot = it) }
                }
            }, onDelete = if (target == null) null else { {
                run(command("delete_$kind") { put("${kind}_id", entityId); put("expected_revision", target.number("revision")) }) { edit = null; if (kind == "shot") select(shot = null) }
            } }, onDuplicate = if (kind == "shot" && target != null) { {
                run(command("duplicate_shot") { put("shot_id", entityId); put("expected_revision", target.number("revision")) }) { edit = null }
            } } else null,
            onAgent = { edit = null; requestAgent(shot = if (kind == "shot") entityId else null, block = if (kind == "block") entityId else null) },
            onReferences = if (target == null) null else { { linkTarget = "$kind:$entityId" } },
            onGenerate = if (kind == "shot" && target != null) { { select(shot = entityId); edit = null; onGenerate(entityId) } } else null,
            onFlow = if (kind == "shot" && target != null) { { select(shot = entityId); edit = null; onFlow() } } else null,
        )
    }
    linkTarget?.let { destination ->
        MaterialLinkDialog(media, destination, view.rows("elements").filter { it.text("kind") == "character" }, linkAssetId, busy, { linkTarget = null; linkAssetId = null }) { assetId, role, selected, mode, characterId ->
            run(command("link_material") {
                put("asset_id", assetId); put("scope", destination.substringBefore(':')); put("target_id", destination.substringAfter(':'))
                put("role", role); put("selected", selected); put("behavior", mode)
                characterId?.let { put("character_id", it) }
            }) { linkTarget = null; linkAssetId = null }
        }
    }
    materialDetail?.let { assetId -> media.firstOrNull { it.id == assetId }?.let { asset ->
        val uses = links.filter { it.text("asset_id") == asset.id }
        AlertDialog(onDismissRequest = { materialDetail = null }, title = { Text(asset.displayName) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssetPreview(asset, Modifier.fillMaxWidth().height(180.dp))
                Text(if (uses.isEmpty()) "Por colocar · sin asociaciones" else "${uses.size} usos · un único archivo", style = MaterialTheme.typography.labelLarge)
                uses.forEach { link ->
                    val targetName = (shots + scenes + blocks).firstOrNull { it.text("id") == link.text("target_id") }?.let { it.text("title").ifBlank { it.text("text").take(60) }.ifBlank { it.text("code") } } ?: link.text("scope")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$targetName · ${roleLabel(link.text("role"))}${if (link["selected"]?.jsonPrimitive?.booleanOrNull == true) " · Elegido" else ""}${if (link["stale"]?.jsonPrimitive?.booleanOrNull == true) " · Revisar" else ""}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { run(command("unlink_material") { put("link_id", link.text("id")); put("expected_revision", link.number("revision")) }) }) { Icon(Icons.Default.LinkOff, "Quitar asociación") }
                    }
                }
                Text("Origen: ${asset.source.name.lowercase()}${asset.generationId?.let { " · generación $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    runCatching {
                        val original = Uri.parse(asset.uri)
                        val uri = if (original.scheme == "file") androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", java.io.File(original.path!!)) else original
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, asset.mimeType ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }
                        .onFailure { scope.launch { snackbar.showSnackbar("Usa la Biblioteca para abrir o exportar este archivo.") } }
                }) { Text("Abrir original") }
            }
        }, confirmButton = { TextButton(onClick = { linkAssetId = asset.id; materialDetail = null; linkTarget = if (shotId != null) "shot:$shotId" else if (sceneId != null) "scene:$sceneId" else "project:$projectId" }) { Text("Vincular a…") } }, dismissButton = { TextButton(onClick = { materialDetail = null }) { Text("Cerrar") } })
    } }
    if (projectEditor) {
        val p = view.obj("project")
        StoryFieldsDialog("Historia y criterios", projectId, "project", p, listOf("title" to "Título", "description" to "Descripción", "synopsis" to "Sinopsis", "treatment" to "Tratamiento", "tone" to "Tono e intención", "visual_style" to "Criterios visuales", "format" to "Formato"), busy, { projectEditor = false }) { fields ->
            run(command("update_project") { fields.forEach { (k, v) -> put(k, v) }; put("expected_revision", p.number("revision")) }) { projectEditor = false }
        }
    }
    if (taskPanel) StoryWorkDialog(view, busy, { taskPanel = false }, { run(it) }, { proposalDetail = it })
    proposalDetail?.let { proposalId -> view.rows("proposals").firstOrNull { it.text("id") == proposalId }?.let { proposal ->
        val applied = (proposal["applied_ids"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty().toSet()
        val changes = proposal.rows("changes").filter { it.text("id") !in applied }
        var selectedChanges by rememberSaveable(proposalId) { mutableStateOf(changes.joinToString("|") { it.text("id") }) }
        AlertDialog(onDismissRequest = { proposalDetail = null }, title = { Text(proposal.text("title").ifBlank { "Propuesta del agente" }) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("La propuesta conserva su revisión base. Al aplicarla se comprueban los cambios recientes.", style = MaterialTheme.typography.bodySmall)
                changes.forEachIndexed { index, entry ->
                    val change = entry.obj("command")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val chosen = selectedChanges.split('|').filter(String::isNotBlank).toSet()
                        Checkbox(entry.text("id") in chosen, { checked -> selectedChanges = (if (checked) chosen + entry.text("id") else chosen - entry.text("id")).joinToString("|") })
                        Text("${index + 1}. ${change.text("action").replace('_', ' ')}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                    }
                    val targetId = change.text("shot_id").ifBlank { change.text("block_id") }.ifBlank { change.text("scene_id") }
                    (shots + scenes + blocks).firstOrNull { it.text("id") == targetId }?.let { current -> Text("Actual · ${current.text("description").ifBlank { current.text("text") }.ifBlank { current.text("title") }}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp)) }
                    Text(change.filterKeys { it !in setOf("action", "operation_id", "expected_revision") }.entries.joinToString("\n") { (k, v) -> "${k.replace('_', ' ')}: ${(v as? JsonPrimitive)?.contentOrNull ?: v.toString()}" }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }, confirmButton = { TextButton(enabled = !busy && selectedChanges.isNotBlank(), onClick = { run(command("apply_proposal") { put("proposal_id", proposalId); put("expected_revision", proposal.number("revision")); put("change_ids", JsonArray(selectedChanges.split('|').filter { it.isNotBlank() && it !in applied }.map(::JsonPrimitive))) }) { proposalDetail = null } }) { Text("Aplicar selección") } }, dismissButton = { TextButton(enabled = !busy, onClick = { run(command("reject_proposal") { put("proposal_id", proposalId); put("expected_revision", proposal.number("revision")) }) { proposalDetail = null } }) { Text("Descartar") } })
    } }
    if (elementPanel) StoryContinuityDialog(projectId, view, sceneId, busy, { elementPanel = false }, { input, done -> run(input) { done() } }, { target -> linkTarget = target })
    if (importScript) ImportScriptDialog(projectId, busy, { importScript = false }) { title, text ->
        run(command("import_script") { put("title", title); put("text", text) }) { importScript = false; tab = 0 }
    }
    if (agentSettings) AlertDialog(onDismissRequest = { agentSettings = false }, title = { Text("Trabajo del agente") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Permiso narrativo. El gasto mantiene su autorización separada.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(agentMode == "proposal", { agentMode = "proposal" }, label = { Text("Proponer") })
                FilterChip(agentMode == "direct", { agentMode = "direct" }, label = { Text("Cambios directos") })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("fragment" to "Selección", "scene" to "Escena", "project" to "Proyecto").forEach { (value, label) -> FilterChip(agentScope == value, { agentScope = value }, label = { Text(label) }) } }
            Text("Conservar estos planos", style = MaterialTheme.typography.titleSmall)
            shots.filter { agentScope == "project" || it.text("scene_id") == sceneId }.forEach { shot ->
                val ids = preservedIds.split('|').filter { it.isNotBlank() }.toSet()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(shot.text("id") in ids, { checked -> preservedIds = (if (checked) ids + shot.text("id") else ids - shot.text("id")).joinToString("|") })
                    Text(shot.text("title").ifBlank { shot.text("code") }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { select(); agentSettings = false }) { Text("Aplicar alcance") } })
}

@Composable
private fun InsertBar(onAdd: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.CenterStart) {
        TextButton(onClick = { menu = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text("Insertar", style = MaterialTheme.typography.labelSmall) }
        DropdownMenu(menu, { menu = false }) {
            listOf("action" to "Acción", "dialogue" to "Diálogo", "note" to "Idea", "shot" to "Plano", "reference" to "Referencia").forEach { (kind, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; onAdd(kind) })
            }
        }
    }
}

@Composable
private fun ShotTile(shot: JsonObject, media: List<Asset>, links: List<JsonObject>, onOpen: () -> Unit, onScript: () -> Unit, onMove: (Int) -> Unit) {
    val candidates = links.filter { it.text("target_id") == shot.text("id") }
    val cover = (candidates.firstOrNull { it["selected"]?.jsonPrimitive?.booleanOrNull == true } ?: candidates.firstOrNull())?.text("asset_id")?.let { key -> media.firstOrNull { it.id == key } }
    Surface(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = NexusTokens.thumbnail, tonalElevation = 1.dp) {
        Column {
            if (cover != null) AssetPreview(cover, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            else Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AddPhotoAlternate, null); Text("Sin material", style = MaterialTheme.typography.labelSmall) }
            }
            Column(Modifier.padding(8.dp)) {
                Text(shot.text("title").ifBlank { shot.text("code") }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(shot.text("description"), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("${if (shot.text("narrative_state") == "idea") "Idea" else "En la historia"} · ${productionLabel(shot.text("production_state"))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onScript, contentPadding = PaddingValues(0.dp), modifier = Modifier.weight(1f).height(36.dp)) { Text("Ir al guion", style = MaterialTheme.typography.labelSmall) }
                    IconButton(onClick = { onMove(-1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Subir plano", Modifier.size(20.dp)) }
                    IconButton(onClick = { onMove(1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Bajar plano", Modifier.size(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LinkedThumbnails(links: List<JsonObject>, media: List<Asset>) {
    if (links.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(links, key = { it.text("id") }) { link -> media.firstOrNull { it.id == link.text("asset_id") }?.let { AssetPreview(it, Modifier.size(68.dp).clip(NexusTokens.thumbnail)) } }
    }
}

@Composable
private fun AssetPreview(asset: Asset, modifier: Modifier) {
    val context = LocalContext.current
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (asset.type in listOf(AssetType.IMAGE, AssetType.VIDEO)) {
            if (Uri.parse(asset.uri).scheme in listOf("file", "content")) when (val thumb = rememberThumb(asset.uri, asset.type == AssetType.VIDEO)) {
                is Thumb.Ready -> Image(thumb.bitmap.asImageBitmap(), asset.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Thumb.Failed -> Icon(Icons.Default.BrokenImage, "Original no disponible")
                Thumb.Loading -> Unit
            } else AsyncImage(remember(asset.uri) { ImageRequest.Builder(context).data(asset.uri).apply { if (asset.type == AssetType.VIDEO) decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) } }.build() }, asset.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (asset.type == AssetType.VIDEO) Icon(Icons.Default.PlayCircle, "Vídeo", Modifier.align(Alignment.BottomEnd).padding(4.dp).size(24.dp))
        } else Icon(if (asset.type == AssetType.AUDIO) Icons.Default.GraphicEq else Icons.Default.Description, asset.type.name, Modifier.size(32.dp))
    }
}

@Composable
private fun MaterialTile(asset: Asset, uses: Int, onClick: () -> Unit) {
    Column(Modifier.clip(NexusTokens.thumbnail).clickable(onClick = onClick)) {
        AssetPreview(asset, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(asset.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp))
        if (uses > 0) Text("$uses usos", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

private fun blockKindLabel(kind: String) = when (kind) { "action" -> "Acción"; "dialogue" -> "Diálogo"; "note", "idea" -> "Idea"; "reference" -> "Referencia"; "shot" -> "Plano"; else -> "Material" }
private fun productionLabel(state: String) = when (state) { "prepared" -> "Preparado"; "generated" -> "Generado"; "chosen" -> "Elegido"; else -> "Pendiente" }
private fun taskStatusLabel(state: String) = when (state) { "queued" -> "En cola"; "prepared" -> "Preparado"; "running" -> "En ejecución"; "pending_resume" -> "Pendiente de reanudación"; "waiting_approval" -> "Espera autorización"; "blocked" -> "Necesita información"; "complete" -> "Terminado"; "cancelled" -> "Cancelado"; "error" -> "Error"; else -> state }
private fun roleLabel(role: String) = when (role) { "identity" -> "Identidad"; "costume" -> "Vestuario"; "location" -> "Escenario"; "composition" -> "Encuadre"; "lighting" -> "Luz"; "sketch" -> "Boceto"; "generated" -> "Imagen generada"; "candidate" -> "Toma candidata"; "chosen" -> "Toma elegida"; "discarded" -> "Descartado"; "audio" -> "Audio"; else -> "Referencia" }

/** A saved draft keeps its base revision. Receiving an agent edit never changes that base silently. */
@Composable
private fun rememberStoryDraft(projectId: String, key: String, entity: JsonObject): Triple<Map<String, String>, Long, (Map<String, String>, Long) -> Unit> {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("story_editor_drafts_v1", Context.MODE_PRIVATE) }
    val prefKey = "$projectId:$key"
    val saved = remember(prefKey) { runCatching { Json.parseToJsonElement(prefs.getString(prefKey, null).orEmpty()).jsonObject }.getOrNull() }
    var values by remember(prefKey) { mutableStateOf(saved?.obj("fields")?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() } ?: entity.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() }) }
    var revision by remember(prefKey) { mutableLongStateOf(saved?.number("revision") ?: entity.number("revision")) }
    val update: (Map<String, String>, Long) -> Unit = { next, base ->
        values = next; revision = base
        prefs.edit().putString(prefKey, buildJsonObject { put("revision", base); put("fields", buildJsonObject { next.forEach { (k, v) -> put(k, v) } }) }.toString()).apply()
    }
    return Triple(values, revision, update)
}

@Composable
private fun StoryEditorDialog(
    projectId: String, key: String, kind: String, entity: JsonObject?, sceneId: String?, scenes: List<JsonObject>, media: List<Asset>, effectiveReferences: List<JsonObject>,
    busy: Boolean, onClose: () -> Unit, onSave: (JsonObject) -> Unit, onDelete: (() -> Unit)?, onDuplicate: (() -> Unit)?,
    onAgent: () -> Unit, onReferences: (() -> Unit)?, onGenerate: (() -> Unit)?, onFlow: (() -> Unit)?,
) {
    val current = entity ?: buildJsonObject {}
    val (values, revision, update) = rememberStoryDraft(projectId, key, current)
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    var compare by rememberSaveable(key) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moveMenu by remember { mutableStateOf(false) }
    val stale = entity != null && revision != entity.number("revision")
    val isShot = kind == "shot"
    val isScene = kind == "scene"
    val fields = when {
        isScene -> listOf("title" to "Título de la escena", "description" to "Situación e intención", "group" to "Acto, secuencia o capítulo · opcional")
        isShot -> listOf("title" to "Título del plano", "description" to "Qué ocurre", "intent" to "Qué debe contar")
        kind == "dialogue" || current.text("kind") == "dialogue" -> listOf("speaker" to "Personaje", "text" to "Diálogo")
        else -> listOf("text" to if (kind == "note" || current.text("kind") == "note") "Idea · escribe libremente" else "Texto")
    }
    fun change(field: String, value: String) = update(values + (field to value), revision)
    fun save() {
        val allowed = (fields.map { it.first } + if (isShot) listOf("framing", "camera", "performance", "dialogue", "sound", "duration_sec", "narrative_state", "production_state", "scene_id", "character_ids") else listOf("narrative_state", "scene_id")).toSet()
        onSave(buildJsonObject {
            values.filterKeys { it in allowed }.forEach { (k, v) ->
                when (k) {
                    "duration_sec" -> if (v.isNotBlank()) v.toDoubleOrNull()?.let { put(k, it) }
                    "character_ids" -> put(k, JsonArray(v.split(',').map(String::trim).filter(String::isNotBlank).map(::JsonPrimitive)))
                    else -> put(k, v)
                }
            }
            if (entity != null) put("expected_revision", revision)
        })
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Conservar borrador y volver") }
                    Text(if (isShot) "Plano" else if (isScene) "Escena" else blockKindLabel(current.text("kind").ifBlank { kind }), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button(onClick = ::save, enabled = !busy && !stale, contentPadding = PaddingValues(horizontal = 16.dp)) { Text(if (busy) "Guardando…" else "Guardar") }
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tu borrador se conserva al salir.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (stale) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = NexusTokens.thumbnail) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Este contenido cambió mientras editabas. Tu texto sigue aquí.", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { compare = !compare }) { Text("Comparar con la versión actual") }
                            if (compare) {
                                fields.forEach { (field, label) -> Text("$label · actual\n${current.text(field)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
                                TextButton(onClick = { update(values, current.number("revision")); compare = false }) { Text("Conservar mi edición tras comparar") }
                            }
                        }
                    }
                    fields.forEach { (field, label) ->
                        StudioTextField(values[field].orEmpty(), { change(field, it) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = if (field in listOf("text", "description", "intent")) 3 else 1)
                    }
                    if (isShot || current.text("kind") in listOf("note", "idea")) {
                        Text("Lugar en la historia", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(values["narrative_state"] == "idea", { change("narrative_state", "idea") }, label = { Text("Idea") })
                            FilterChip(values["narrative_state"] != "idea", { change("narrative_state", "story") }, label = { Text("Incorporado") })
                        }
                    }
                    if (isShot) {
                        Text("Producción", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("pending", "prepared", "generated", "chosen").forEach { state -> FilterChip(values["production_state"].orEmpty().ifBlank { "pending" } == state, { change("production_state", state) }, label = { Text(productionLabel(state)) }) }
                        }
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Menos indicaciones" else "Cámara, actuación, sonido y duración") }
                        if (expanded) listOf("framing" to "Encuadre", "camera" to "Cámara y movimiento", "performance" to "Actuación", "dialogue" to "Diálogo", "sound" to "Sonido", "duration_sec" to "Duración orientativa · segundos").forEach { (field, label) ->
                            StudioTextField(values[field].orEmpty(), { change(field, it) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (!isScene && entity != null) Box {
                        val chosenScene = values["scene_id"] ?: sceneId
                        TextButton(onClick = { moveMenu = true }) { Text("Escena: ${scenes.firstOrNull { it.text("id") == chosenScene }?.text("title").orEmpty()}") }
                        DropdownMenu(moveMenu, { moveMenu = false }) { scenes.forEach { scene -> DropdownMenuItem(text = { Text(scene.text("title")) }, onClick = { change("scene_id", scene.text("id")); moveMenu = false }) } }
                    }
                    if (entity != null) {
                        HorizontalDivider()
                        if (isShot && effectiveReferences.isNotEmpty()) {
                            Text("Referencias efectivas", style = MaterialTheme.typography.labelLarge)
                            LinkedThumbnails(effectiveReferences, media)
                            Text(effectiveReferences.joinToString(" · ") { "${roleLabel(it.text("role"))} (${it.text("scope")}${if (it.text("behavior") == "replace") ", sustituye" else ""})" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            onReferences?.let { TextButton(onClick = it) { Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp)); Text("Referencias y material") } }
                            TextButton(onClick = onAgent) { Text("Agente") }
                            onGenerate?.let { TextButton(onClick = it) { Text("ArtBox") } }
                            onFlow?.let { TextButton(onClick = it) { Text("Flow") } }
                            onDuplicate?.let { TextButton(onClick = it, enabled = !busy) { Text("Crear variante") } }
                        }
                        onDelete?.let { TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("Eliminar ${if (isScene) "escena" else if (isShot) "plano" else "bloque"}", color = MaterialTheme.colorScheme.error) } }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Eliminar ${if (isShot) "plano" else if (isScene) "escena" else "bloque"}") }, text = { Text("El material se conserva en la biblioteca. Puedes deshacer este cambio.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete?.invoke() }) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}

@Composable
private fun StoryFieldsDialog(title: String, projectId: String, key: String, entity: JsonObject, fields: List<Pair<String, String>>, busy: Boolean, onClose: () -> Unit, onSave: (JsonObject) -> Unit) {
    val (values, revision, update) = rememberStoryDraft(projectId, key, entity)
    val stale = revision != entity.number("revision")
    AlertDialog(onDismissRequest = onClose, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (stale) Text("El proyecto cambió. Conservamos tu borrador; revisa la versión actual antes de guardar.", color = MaterialTheme.colorScheme.error)
            fields.forEach { (field, label) -> StudioTextField(values[field].orEmpty(), { update(values + (field to it), revision) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth()) }
            if (stale) TextButton(onClick = { update(values, entity.number("revision")) }) { Text("He revisado la revisión ${entity.number("revision")}") }
        }
    }, confirmButton = { TextButton(enabled = !busy && !stale, onClick = { onSave(buildJsonObject { fields.forEach { (field, _) -> put(field, values[field].orEmpty()) }; put("expected_revision", revision) }) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cerrar · conservar borrador") } })
}

@Composable
private fun MaterialLinkDialog(media: List<Asset>, target: String, characters: List<JsonObject>, initialAssetId: String?, busy: Boolean, onClose: () -> Unit, onLink: (String, String, Boolean, String, String?) -> Unit) {
    var query by rememberSaveable(target) { mutableStateOf("") }
    var chosen by rememberSaveable(target, initialAssetId) { mutableStateOf(initialAssetId) }
    var role by rememberSaveable(target) { mutableStateOf("reference") }
    var selected by rememberSaveable(target) { mutableStateOf(false) }
    var replace by rememberSaveable(target) { mutableStateOf(false) }
    var characterId by rememberSaveable(target) { mutableStateOf(if (target.startsWith("character:")) target.substringAfter(':') else null) }
    val roles = listOf("reference", "identity", "costume", "location", "composition", "lighting", "sketch", "generated", "candidate", "audio", "chosen", "discarded")
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.92f), shape = NexusTokens.card) {
            Column(Modifier.padding(12.dp)) {
                Text("Vincular material", style = MaterialTheme.typography.titleLarge)
                Text("Se reutiliza el archivo original. Cada vínculo tiene su propio uso.", style = MaterialTheme.typography.bodySmall)
                StudioTextField(query, { query = it }, placeholder = { Text("Buscar en la biblioteca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(roles) { candidate -> FilterChip(role == candidate, { role = candidate; if (candidate == "chosen") selected = true }, label = { Text(roleLabel(candidate)) }) } }
                if (role in listOf("identity", "costume")) {
                    Text("Personaje al que corresponde", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(characters, key = { it.text("id") }) { character -> FilterChip(characterId == character.text("id"), { characterId = character.text("id") }, label = { Text(character.text("name")) }) } }
                    if (characters.isEmpty()) Text("Añade el personaje en Personajes y continuidad antes de vincular su identidad.", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(selected, { selected = it }); Text("Elegido para este contexto", style = MaterialTheme.typography.bodySmall) }
                if (role !in listOf("chosen", "candidate", "generated", "discarded", "audio")) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(replace, { replace = it }); Text("Sustituir referencia general del mismo tipo", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)) }
                LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(media.filter { query.isBlank() || it.displayName.contains(query, true) || it.tags.any { tag -> tag.contains(query, true) } }, key = { it.id }) { asset ->
                        Surface(Modifier.fillMaxWidth(), border = if (chosen == asset.id) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null, shape = NexusTokens.thumbnail) { MaterialTile(asset, 0) { chosen = asset.id } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("Cancelar") }
                    Button(enabled = chosen != null && !busy && (role !in listOf("identity", "costume") || characterId != null), onClick = { chosen?.let { onLink(it, role, selected, if (replace) "replace" else "complement", characterId.takeIf { role in listOf("identity", "costume") }) } }) { Text("Vincular") }
                }
            }
        }
    }
}

@Composable
private fun StoryWorkDialog(view: JsonObject, busy: Boolean, onClose: () -> Unit, onCommand: (JsonObject) -> Unit, onProposal: (String) -> Unit) {
    val autonomy = view.obj("autonomy")
    val enabled = autonomy["enabled"]?.jsonPrimitive?.booleanOrNull == true
    var mode by rememberSaveable { mutableStateOf(autonomy.text("mode").ifBlank { "proposals" }) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Trabajo del proyecto") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Preparación local. Las llamadas al agente y las generaciones mantienen su autorización de gasto.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(enabled, { onCommand(command("set_autonomy") { put("enabled", it); put("mode", mode); put("expected_revision", autonomy.number("revision")) }) }, enabled = !busy); Spacer(Modifier.width(8.dp)); Text(if (enabled) "Preparación habilitada" else "Trabajo pausado") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("preparation" to "Preparar", "proposals" to "Proponer", "direct" to "Cambios directos").forEach { (value, label) -> FilterChip(mode == value, { mode = value; onCommand(command("set_autonomy") { put("enabled", enabled); put("mode", value); put("expected_revision", autonomy.number("revision")) }) }, label = { Text(label) }) } }
            Text(autonomy.text("execution"), style = MaterialTheme.typography.bodySmall)
            if (enabled) TextButton(enabled = !busy, onClick = { onCommand(command("run_preparation")) }) { Text("Procesar preparación pendiente") }
            Text("Propuestas", style = MaterialTheme.typography.titleSmall)
            val proposals = view.rows("proposals").filter { it.text("status") in listOf("pending", "proposed", "conflict") }
            if (proposals.isEmpty()) Text("Sin propuestas pendientes", style = MaterialTheme.typography.bodySmall)
            proposals.forEach { proposal -> TextButton(onClick = { onProposal(proposal.text("id")) }) { Text(proposal.text("title").ifBlank { "Propuesta" }) } }
            Text("Cola", style = MaterialTheme.typography.titleSmall)
            val tasks = view.rows("tasks")
            if (tasks.isEmpty()) Text("No hay tareas pendientes", style = MaterialTheme.typography.bodySmall)
            tasks.forEach { task ->
                Column {
                    Text(task.text("title").ifBlank { task.text("kind") }, style = MaterialTheme.typography.bodyMedium)
                    Text(taskStatusLabel(task.text("status")) + task.text("detail").takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    if (task.text("status") !in listOf("complete", "cancelled")) TextButton(enabled = !busy, onClick = { onCommand(command("update_task") { put("task_id", task.text("id")); put("expected_revision", task.number("revision")); put("status", "cancelled") }) }) { Text("Cancelar") }
                }
            }
            TextButton(enabled = !busy, onClick = { onCommand(command("enqueue_task") { put("kind", "preparation"); put("title", "Revisar planos sin material") }) }) { Text("Preparar siguiente trabajo") }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Listo") } })
}

@Composable
private fun StoryContinuityDialog(projectId: String, view: JsonObject, activeSceneId: String?, busy: Boolean, onClose: () -> Unit, onCommand: (JsonObject, () -> Unit) -> Unit, onReferences: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("character") }
    var preference by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingRevision by rememberSaveable { mutableLongStateOf(0) }
    var sceneOnly by rememberSaveable { mutableStateOf(false) }
    var variants by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Personajes y continuidad") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TabRow(tab) { listOf("Elementos", "Indicaciones").forEachIndexed { i, title -> Tab(tab == i, { tab = i }, text = { Text(title) }) } }
            if (tab == 0) {
                view.rows("elements").forEach { element ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${element.text("name")} · ${element.text("kind")}\n${element.text("description")}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).clickable {
                            editingId = element.text("id"); editingRevision = element.number("revision"); name = element.text("name"); description = element.text("description"); kind = element.text("kind"); notes = element.text("notes"); variants = (element["variants"] as? JsonArray)?.joinToString("\n") { it.jsonPrimitive.content }.orEmpty(); sceneOnly = element.text("scene_id").isNotBlank()
                        })
                        IconButton(onClick = { onReferences("${if (element.text("kind") == "character") "character" else "element"}:${element.text("id")}") }) { Icon(Icons.Default.AddPhotoAlternate, "Referencias de ${element.text("name")}") }
                    }
                }
                StudioTextField(name, { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(listOf("character" to "Personaje", "location" to "Escenario", "object" to "Objeto", "costume" to "Vestuario")) { (value, label) -> FilterChip(kind == value, { if (editingId == null) kind = value }, label = { Text(label) }, enabled = editingId == null) } }
                StudioTextField(description, { description = it }, label = { Text("Descripción y continuidad") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                if (kind == "character") {
                    StudioTextField(variants, { variants = it }, label = { Text("Variantes de apariencia · una por línea") }, modifier = Modifier.fillMaxWidth())
                    StudioTextField(notes, { notes = it }, label = { Text("Notas de representación") }, modifier = Modifier.fillMaxWidth())
                } else if (activeSceneId != null) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(sceneOnly, { sceneOnly = it }); Text("Específico de la escena actual", style = MaterialTheme.typography.bodySmall) }
                TextButton(enabled = name.isNotBlank() && !busy, onClick = {
                    onCommand(command("upsert_element") { put("kind", kind); put("name", name); put("description", description); editingId?.let { put("element_id", it); put("expected_revision", editingRevision) }; if (kind == "character") { put("variants", JsonArray(variants.lines().map(String::trim).filter(String::isNotBlank).map(::JsonPrimitive))); put("notes", notes) } else if (sceneOnly) activeSceneId?.let { put("scene_id", it) } }) {
                        editingId = null; name = ""; description = ""; variants = ""; notes = ""
                    }
                }) { Text(if (editingId == null) "Añadir" else "Guardar elemento") }
            } else {
                view.rows("preferences").forEach { rule ->
                    Text("${rule.text("scope")} · ${rule.text("text")}", style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !busy, onClick = { onCommand(command("set_preference") { put("preference_id", rule.text("id")); put("expected_revision", rule.number("revision")); put("scope", rule.text("scope")); put("target_id", rule.text("target_id")); put("text", rule.text("text")); put("enabled", false) }, {}) }) { Text("Desactivar") }
                }
                StudioTextField(preference, { preference = it }, label = { Text("Indicación explícita para el proyecto") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                TextButton(enabled = preference.isNotBlank() && !busy, onClick = { onCommand(command("set_preference") { put("scope", "project"); put("target_id", projectId); put("text", preference); put("enabled", true) }) { preference = "" } }) { Text("Guardar indicación visible") }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Listo") } })
}

@Composable
private fun ImportScriptDialog(projectId: String, busy: Boolean, onClose: () -> Unit, onImport: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("story_editor_drafts_v1", Context.MODE_PRIVATE) }
    var title by rememberSaveable(projectId) { mutableStateOf(prefs.getString("$projectId:import_title", "Guion importado").orEmpty()) }
    var text by rememberSaveable(projectId) { mutableStateOf(prefs.getString("$projectId:import_text", "").orEmpty()) }
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("No se pudo leer el guion.") }
                prefs.edit().putString("$projectId:import_text", text).apply()
                importError = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { importError = failure.message }
            finally { importing = false }
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Importar guion", style = MaterialTheme.typography.titleLarge)
                Text("Pega el texto o abre un TXT o Markdown. Se conservará como bloques editables, sin exigir un formato especial.", style = MaterialTheme.typography.bodySmall)
                StudioTextField(title, { title = it; prefs.edit().putString("$projectId:import_title", it).apply() }, label = { Text("Título de la escena de entrada") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                StudioTextField(text, { text = it; prefs.edit().putString("$projectId:import_text", it).apply() }, placeholder = { Text("Escribe o pega el guion…") }, modifier = Modifier.fillMaxWidth().weight(1f))
                importError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { file.launch(arrayOf("text/plain", "text/markdown")) }, enabled = !busy && !importing) { Text("Abrir archivo") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Volver") }
                    Button(enabled = text.isNotBlank() && !busy && !importing, onClick = { onImport(title.ifBlank { "Guion importado" }, text) }) { Text("Importar") }
                }
            }
        }
    }
}

