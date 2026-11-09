import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.agent.core.tools.StoryAgentSelection
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Desktop transports the same transactions as Android, on its existing assistant/index.json. */
class DesktopStoryWorkspace(private val assets: AssetIndex, private val data: File) : AutoCloseable {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val selectionFile = File(data, "story-selection.json")
    @Volatile private var selected: StoryAgentSelection? = runCatching {
        selectionFile.takeIf { it.isFile }?.readText()?.let { json.decodeFromString<StoryAgentSelection>(it) }
    }.getOrNull()
    private val preparation = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        preparation.launch {
            assets.snapshot().story.tasks.filter { it.status == "running" && it.providerJobId == null }.map { it.projectId }.distinct().forEach { project ->
                assets.storyCommand(project, buildJsonObject { put("action", "recover_tasks"); put("operation_id", "desktop-resume:${java.util.UUID.randomUUID()}") }, "system:recovery")
            }
            assets.changes.map { index -> index.story.autonomy.filter { it.enabled }.mapNotNull { policy ->
                val queued = index.story.tasks.filter { it.projectId == policy.projectId && it.status == "queued" && (policy.sceneId == null || it.sceneId == policy.sceneId) }.map { it.id }.sorted()
                if (queued.isEmpty()) null else Triple(policy.projectId, policy.revision, queued)
            } }.distinctUntilChanged().collect { groups ->
                for ((project, revision, tasks) in groups) assets.storyCommand(project, buildJsonObject {
                    put("action", "run_preparation"); put("operation_id", "desktop-local:$project:$revision:${tasks.joinToString(",")}")
                }, "worker")
            }
        }
    }

    fun selection(): StoryAgentSelection? = selected
    override fun close() { preparation.cancel() }
    fun captureForTurn(projectId: String?): StoryAgentSelection? = selected?.takeIf { it.projectId == projectId }?.also(::validateSelection)
    private fun JsonObject.text(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun validateSelection(scope: StoryAgentSelection) {
        val d = assets.snapshot()
        require(d.projects.any { it.id == scope.projectId && !it.archived }) { "El proyecto seleccionado ya no está disponible. Vuelve a seleccionarlo." }
        val scene = scope.sceneId?.let { id -> d.scenes.firstOrNull { it.id == id && it.projectId == scope.projectId } ?: error("La escena seleccionada cambió o se eliminó. Vuelve a seleccionar el fragmento.") }
        val shot = scope.shotId?.let { id -> d.shots.firstOrNull { it.id == id && it.projectId == scope.projectId } ?: error("El plano seleccionado cambió o se eliminó. Vuelve a seleccionar el fragmento.") }
        val block = scope.blockId?.let { id -> d.story.blocks.firstOrNull { it.id == id && it.projectId == scope.projectId } ?: error("El bloque seleccionado cambió o se eliminó. Vuelve a seleccionar el fragmento.") }
        require(scene == null || shot == null || shot.sceneId == scene.id) { "El plano no pertenece a la escena seleccionada." }
        require(scene == null || block == null || block.sceneId == scene.id) { "El bloque no pertenece a la escena seleccionada." }
        require(shot == null || block?.shotId == null || block.shotId == shot.id) { "El bloque pertenece a otro plano." }
        require(scope.mode in setOf("proposal", "direct")) { "Modo de trabajo desconocido." }
        val ids = d.scenes.filter { it.projectId == scope.projectId }.map { it.id } + d.shots.filter { it.projectId == scope.projectId }.map { it.id } + d.story.blocks.filter { it.projectId == scope.projectId }.map { it.id } + d.characters.filter { it.projectId == scope.projectId }.map { it.id } + d.story.elements.filter { it.projectId == scope.projectId }.map { it.id }
        require(scope.preserveIds.all { it in ids }) { "Un elemento fijado ya no pertenece al proyecto seleccionado." }
    }
    private fun saveSelection(scope: StoryAgentSelection?) {
        val temp = File(data, "story-selection.json.tmp")
        data.mkdirs()
        java.io.FileOutputStream(temp).use { output -> output.write(json.encodeToString(scope).toByteArray(Charsets.UTF_8)); output.fd.sync() }
        try { Files.move(temp.toPath(), selectionFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp.toPath(), selectionFile.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        selected = scope
    }

    suspend fun command(op: String, args: JsonObject): JsonObject {
        if (op == "storySelection") {
            if ("projectId" in args || args["clear"]?.jsonPrimitive?.booleanOrNull == true) {
                val scope = args.text("projectId")?.let { project -> StoryAgentSelection(project, args.text("sceneId"), args.text("shotId"), args.text("blockId"), args.text("mode") ?: "proposal", (args["preserveIds"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }.toSet()).also(::validateSelection) }
                saveSelection(scope)
            }
            return buildJsonObject { put("selection", selected?.let { json.encodeToJsonElement(it) } ?: JsonNull); put("index", json.encodeToJsonElement(assets.snapshot())) }
        }
        val project = args.text("projectId") ?: error("Selecciona un proyecto audiovisual.")
        if (op == "storyView") {
            val adoption = assets.storyCommand(project, buildJsonObject { put("action", "adopt_legacy_links"); put("operation_id", "desktop-adopt:$project") }, "system:migration")
            require(adoption["ok"]?.jsonPrimitive?.booleanOrNull != false) { adoption["error"]?.jsonPrimitive?.content ?: "No se pudo abrir el proyecto." }
            return JsonObject(assets.storyView(project, args.text("sceneId"), args.text("shotId")) + mapOf("selection" to (selected?.takeIf { it.projectId == project }?.let { json.encodeToJsonElement(it) } ?: JsonNull)))
        }
        require(op == "storyCommand") { "Operación audiovisual desconocida." }
        val input = args["input"] as? JsonObject ?: error("Falta la operación estructurada del proyecto.")
        require(input.text("action") != "register_result") { "Los resultados se registran exclusivamente desde una tarea o archivo verificado por Nexus." }
        val result = assets.storyCommand(project, input, "user")
        return JsonObject(result + mapOf("view" to assets.storyView(project), "index" to json.encodeToJsonElement(assets.snapshot())))
    }
}
