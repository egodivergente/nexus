package com.vidadomu.nexus.agent.core.assets

import java.io.File
import java.text.Normalizer
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Project(
    val id: String,
    val name: String,
    val description: String? = null,
    val createdAt: Long,
    val archived: Boolean = false,
    /** Biblia del proyecto que el asistente lee en cada mensaje mientras el proyecto está activo. */
    val memory: ProjectMemory = ProjectMemory(),
)

@Serializable
data class ProjectNote(val at: Long, val text: String)

/** Memoria editorial de un proyecto: sinopsis, tono, estilo, reglas fijas y notas de decisiones. */
@Serializable
data class ProjectMemory(
    val synopsis: String? = null,
    /** Género, tono, público, duración prevista… */
    val tone: String? = null,
    /** Estética: paleta, luz, óptica, textura, referencias visuales, ratio preferido. */
    val visualStyle: String? = null,
    /** Reglas que deben respetarse siempre (p. ej. «Mateo siempre con la chaqueta verde»). */
    val rules: List<String> = emptyList(),
    /** Modelos o familias preferidos para este proyecto (orientativo). */
    val preferredModels: List<String> = emptyList(),
    /** Decisiones y aprendizajes fechados (las más antiguas se recortan). */
    val notes: List<ProjectNote> = emptyList(),
    val updatedAt: Long? = null,
) {
    fun isEmpty() = synopsis.isNullOrBlank() && tone.isNullOrBlank() && visualStyle.isNullOrBlank() && rules.isEmpty() && preferredModels.isEmpty() && notes.isEmpty()
}

/** Intervalo en que un proyecto estuvo activo: lo generado entonces (también a mano) se etiqueta con él. */
@Serializable
data class ProjectSession(val projectId: String, val from: Long, val to: Long? = null)

@Serializable
data class Scene(val id: String, val projectId: String, val code: String, val title: String? = null)

/** status: PLANNED → KEYFRAME → VIDEO → FINAL (derivado de sus assets, ver [AssetIndex.shotStatus]). */
@Serializable
data class Shot(val id: String, val projectId: String, val sceneId: String? = null, val code: String, val description: String? = null)

@Serializable
data class Character(val id: String, val projectId: String?, val name: String, val aliases: List<String> = emptyList(), val description: String? = null, val referenceAssetIds: List<String> = emptyList())

@Serializable
enum class AssetType { IMAGE, VIDEO, AUDIO, OTHER }

@Serializable
enum class AssetRole { KEYFRAME, REFERENCE, OUTPUT, FINAL, SOURCE, OTHER }

@Serializable
enum class AssetSource { GENERATED, IMPORTED, SHARED }

@Serializable
data class Asset(
    val id: String,
    val uri: String,
    val type: AssetType,
    val role: AssetRole = AssetRole.OTHER,
    val source: AssetSource = AssetSource.IMPORTED,
    /** Nombre lógico; renombrar NO toca el fichero físico. */
    val displayName: String,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationSec: Double? = null,
    val projectId: String? = null,
    val sceneId: String? = null,
    val shotId: String? = null,
    val characterIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val generationId: String? = null,
    val parentAssetId: String? = null,
    /** Removed from library; history and existing references retain the file and asset identity. */
    val libraryDeleted: Boolean = false,
    val version: Int = 1,
    val createdAt: Long,
)

/** Lineage completo de una generación. */
@Serializable
data class GenerationRecord(
    val id: String,
    val nexusJobId: String? = null,
    val providerJobId: String? = null,
    val providerId: String,
    val modelId: String,
    val modelVersion: String? = null,
    val modality: String,
    val prompt: String,
    val negativePrompt: String? = null,
    val params: Map<String, String> = emptyMap(),
    val seed: String? = null,
    val referenceAssetIds: List<String> = emptyList(),
    val referenceUris: List<String> = emptyList(),
    val estimatedCostUsd: Double? = null,
    val actualCostUsd: Double? = null,
    val status: String,
    val parentGenerationId: String? = null,
    val agentPlanId: String? = null,
    val projectId: String? = null,
    val shotId: String? = null,
    val outputAssetIds: List<String> = emptyList(),
    val createdAt: Long,
    val completedAt: Long? = null,
)

@Serializable
data class IndexData(
    val schemaVersion: Int = 1,
    val projects: List<Project> = emptyList(),
    val scenes: List<Scene> = emptyList(),
    val shots: List<Shot> = emptyList(),
    val characters: List<Character> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val generations: List<GenerationRecord> = emptyList(),
    val projectSessions: List<ProjectSession> = emptyList(),
)

@Serializable
data class AssetQuery(
    val text: String? = null,
    val projectId: String? = null,
    val sceneCode: String? = null,
    val shotCode: String? = null,
    val character: String? = null,
    val type: AssetType? = null,
    val role: AssetRole? = null,
    val modelId: String? = null,
    val limit: Int = 50,
)

/**
 * Índice de proyectos/escenas/planos/personajes/assets/generaciones. Persistido en un JSON propio con
 * escritura atómica: no modifica nexus.db ni mueve archivos, así que es reversible por completo.
 */
class AssetIndex(private val file: File?, private val clock: () -> Long = System::currentTimeMillis) {

    private val mutex = Mutex()
    private var data: IndexData = file?.takeIf { it.isFile }?.let { JSON.decodeFromString<IndexData>(it.readText()) } ?: IndexData()

    private val mutableChanges = MutableStateFlow(data)
    val changes = mutableChanges.asStateFlow()

    fun snapshot(): IndexData = data

    /** Sync must not overwrite edits made while network transfers were in progress. */
    suspend fun applySyncedSnapshot(expected:IndexData,incoming:IndexData) = edit { current ->
        check(current==expected) { "El proyecto cambió durante la sincronización. Se reintentará." }
        require(incoming.schemaVersion==1)
        val projectIds=incoming.projects.map {it.id}.toSet()
        require(projectIds.size==incoming.projects.size)
        require(incoming.assets.map {it.id}.distinct().size==incoming.assets.size)
        require(incoming.assets.all {it.projectId==null || it.projectId in projectIds}) {"Falta el proyecto de un medio sincronizado."}
        incoming.copy(projectSessions=current.projectSessions) to Unit
    }

    /** One atomic insertion; callers remap imported identities and materialize files first. */
    suspend fun importProject(bundle: IndexData): Project = edit { current ->
        require(bundle.schemaVersion == 1 && bundle.projects.size == 1 && bundle.projectSessions.isEmpty()) { "Paquete de proyecto incompatible" }
        val project = bundle.projects.single()
        fun ids(d: IndexData) = d.projects.map { it.id } + d.scenes.map { it.id } + d.shots.map { it.id } +
            d.characters.map { it.id } + d.assets.map { it.id } + d.generations.map { it.id }
        val incoming = ids(bundle)
        val existingIds=ids(current).toSet()
        require(incoming.all { it.isNotBlank() } && incoming.distinct().size == incoming.size) { "Identidades repetidas en el paquete" }
        require(incoming.none { it in existingIds }) { "El proyecto ya existe o sus identidades colisionan" }
        require(bundle.assets.all { it.projectId == project.id } && bundle.scenes.all { it.projectId == project.id } &&
            bundle.shots.all { it.projectId == project.id } && bundle.characters.all { it.projectId == project.id } &&
            bundle.generations.all { it.projectId == project.id }) { "El paquete contiene otro proyecto" }
        val sceneIds=bundle.scenes.map { it.id }.toSet()
        val shotIds=bundle.shots.map { it.id }.toSet()
        val characterIds=bundle.characters.map { it.id }.toSet()
        val assetIds=bundle.assets.map { it.id }.toSet()
        val generationIds=bundle.generations.map { it.id }.toSet()
        fun String?.belongsTo(ids: Set<String>) = this == null || this in ids
        require(bundle.assets.all { it.sceneId.belongsTo(sceneIds) && it.shotId.belongsTo(shotIds) &&
            it.generationId.belongsTo(generationIds) && it.parentAssetId.belongsTo(assetIds) && it.characterIds.all { id -> id in characterIds } } &&
            bundle.shots.all { it.sceneId.belongsTo(sceneIds) } && bundle.characters.all { it.referenceAssetIds.all { id -> id in assetIds } } &&
            bundle.generations.all { it.parentGenerationId.belongsTo(generationIds) && it.shotId.belongsTo(shotIds) &&
                (it.referenceAssetIds+it.outputAssetIds).all { id -> id in assetIds } }) { "Faltan relaciones del proyecto o tienen un tipo incorrecto" }
        current.copy(projects=current.projects+bundle.projects,scenes=current.scenes+bundle.scenes,
            shots=current.shots+bundle.shots,characters=current.characters+bundle.characters,
            assets=current.assets+bundle.assets,generations=current.generations+bundle.generations) to project
    }

    private suspend fun <T> edit(block: (IndexData) -> Pair<IndexData, T>): T = mutex.withLock {
        val (next, result) = block(data)
        persist(next)
        data = next
        mutableChanges.value = next
        result
    }

    private fun persist(snapshot: IndexData) {
        val f = file ?: return
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        try {
            java.io.FileOutputStream(tmp).use { output ->
                output.write(JSON.encodeToString(IndexData.serializer(), snapshot).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            try {
                java.nio.file.Files.move(tmp.toPath(), f.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { tmp.delete() }
    }

    // ---------- proyectos y estructura ----------

    fun projects(includeArchived: Boolean = false) = data.projects.filter { includeArchived || !it.archived }

    fun findProject(nameOrId: String): Project? =
        data.projects.firstOrNull { it.id == nameOrId } ?: data.projects.firstOrNull { norm(it.name) == norm(nameOrId) }
            ?: data.projects.firstOrNull { norm(it.name).contains(norm(nameOrId)) }

    suspend fun createProject(name: String, description: String? = null): Project = edit { d ->
        d.projects.firstOrNull { norm(it.name) == norm(name) }?.let { return@edit d to it }
        val p = Project(newId("prj"), name.trim(), description, clock())
        d.copy(projects = d.projects + p) to p
    }

    suspend fun updateProjectMemory(projectId: String, change: (ProjectMemory) -> ProjectMemory): Project = edit { d ->
        val cur = d.projects.firstOrNull { it.id == projectId } ?: throw IllegalArgumentException("Proyecto desconocido: $projectId")
        val m = change(cur.memory)
        val next = cur.copy(memory = m.copy(
            synopsis = m.synopsis?.trim()?.take(MAX_FIELD)?.ifBlank { null },
            tone = m.tone?.trim()?.take(MAX_FIELD)?.ifBlank { null },
            visualStyle = m.visualStyle?.trim()?.take(MAX_FIELD)?.ifBlank { null },
            rules = m.rules.map { it.trim().take(300) }.filter { it.isNotEmpty() }.distinctBy { norm(it) }.take(MAX_RULES),
            preferredModels = m.preferredModels.map { it.trim().take(120) }.filter { it.isNotEmpty() }.distinct().take(12),
            notes = m.notes.filter { it.text.isNotBlank() }.takeLast(MAX_NOTES),
            updatedAt = clock(),
        ))
        d.copy(projects = d.projects.map { if (it.id == projectId) next else it }) to next
    }

    suspend fun updateProject(projectId: String, description: String?): Project = edit { d ->
        val cur = d.projects.firstOrNull { it.id == projectId } ?: throw IllegalArgumentException("Proyecto desconocido: $projectId")
        val next = cur.copy(description = description?.trim()?.take(400)?.ifBlank { null })
        d.copy(projects = d.projects.map { if (it.id == projectId) next else it }) to next
    }

    /** Cierra el intervalo activo y abre otro para [projectId] (null = sin proyecto). Idempotente. */
    suspend fun startProjectSession(projectId: String?) = edit { d ->
        val open = d.projectSessions.lastOrNull()?.takeIf { it.to == null }
        if (open?.projectId == projectId) return@edit d to Unit
        val now = clock()
        val closed = d.projectSessions.map { if (it === open) it.copy(to = now) else it }
        val next = if (projectId == null) closed else closed + ProjectSession(projectId, now)
        d.copy(projectSessions = next.takeLast(500)) to Unit
    }

    /** Proyecto que estaba activo en [time], según los intervalos registrados. */
    fun projectAt(time: Long): String? = data.projectSessions.lastOrNull { time >= it.from && (it.to == null || time < it.to) }?.projectId

    suspend fun updateCharacter(id: String, change: (Character) -> Character): Character = edit { d ->
        val cur = d.characters.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Personaje desconocido: $id")
        val next = change(cur).copy(id = cur.id)
        d.copy(characters = d.characters.map { if (it.id == id) next else it }) to next
    }

    /**
     * Texto compacto con la memoria del proyecto, sus personajes y su estado, para el prompt de sistema.
     * Se recorta a [maxChars]; las notas más recientes tienen prioridad sobre las antiguas.
     */
    fun projectContext(projectId: String, maxChars: Int = 5_000): String? {
        val p = findProject(projectId) ?: return null
        val m = p.memory
        val chars = data.characters.filter { it.projectId == p.id }
        val assets = data.assets.filter { it.projectId == p.id }
        val shots = data.shots.filter { it.projectId == p.id }
        val head = buildString {
            appendLine("Proyecto activo: «${p.name}»" + (p.description?.let { " — $it" } ?: ""))
            m.synopsis?.let { appendLine("Sinopsis: $it") }
            m.tone?.let { appendLine("Tono/género: $it") }
            m.visualStyle?.let { appendLine("Estilo visual: $it") }
            if (m.rules.isNotEmpty()) { appendLine("Reglas fijas:"); m.rules.forEach { appendLine("- $it") } }
            if (m.preferredModels.isNotEmpty()) appendLine("Modelos preferidos: ${m.preferredModels.joinToString()}")
            if (chars.isNotEmpty()) {
                appendLine("Personajes:")
                chars.forEach { c ->
                    append("- ${c.name}")
                    if (c.aliases.isNotEmpty()) append(" (${c.aliases.joinToString()})")
                    c.description?.let { append(": $it") }
                    if (c.referenceAssetIds.isNotEmpty()) append(" [${c.referenceAssetIds.size} referencias]")
                    appendLine()
                }
            }
            val byType = assets.groupingBy { it.type.name.lowercase() }.eachCount()
            appendLine("Material: ${assets.size} assets" + (if (byType.isNotEmpty()) " (" + byType.entries.joinToString { "${it.value} ${it.key}" } + ")" else "") + ", ${shots.size} planos.")
            if (m.isEmpty()) appendLine("(Memoria vacía: cuando el usuario cuente de qué va el proyecto, guárdalo con update_project_memory.)")
        }.trim()
        if (head.length >= maxChars) return head.take(maxChars)
        val notes = mutableListOf<String>()
        var budget = maxChars - head.length - 30
        for (n in m.notes.asReversed()) {
            val line = "- ${n.text}"
            if (line.length + 1 > budget) break
            notes.add(0, line); budget -= line.length + 1
        }
        return if (notes.isEmpty()) head else head + "\nNotas y decisiones (recientes al final):\n" + notes.joinToString("\n")
    }

    suspend fun ensureScene(projectId: String, code: String, title: String? = null): Scene = edit { d ->
        d.scenes.firstOrNull { it.projectId == projectId && norm(it.code) == norm(code) }?.let { return@edit d to it }
        val s = Scene(newId("scn"), projectId, code.trim(), title)
        d.copy(scenes = d.scenes + s) to s
    }

    suspend fun ensureShot(projectId: String, code: String, sceneId: String? = null, description: String? = null): Shot = edit { d ->
        d.shots.firstOrNull { it.projectId == projectId && norm(it.code) == norm(code) }?.let { return@edit d to it }
        val s = Shot(newId("sht"), projectId, sceneId, code.trim(), description)
        d.copy(shots = d.shots + s) to s
    }

    suspend fun ensureCharacter(name: String, projectId: String? = null, description: String? = null): Character = edit { d ->
        d.characters.firstOrNull { c -> (projectId == null || c.projectId == projectId) && (norm(c.name) == norm(name) || c.aliases.any { norm(it) == norm(name) }) }
            ?.let { return@edit d to it }
        val c = Character(newId("chr"), projectId, name.trim(), description = description)
        d.copy(characters = d.characters + c) to c
    }

    fun findCharacter(name: String, projectId: String? = null): Character? = data.characters.firstOrNull { c ->
        (projectId == null || c.projectId == projectId || c.projectId == null) &&
            (norm(c.name) == norm(name) || c.aliases.any { norm(it) == norm(name) })
    }

    // ---------- assets ----------

    fun asset(id: String): Asset? = data.assets.firstOrNull { it.id == id }
    fun assetByUri(uri: String): Asset? = data.assets.firstOrNull { it.uri == uri }

    suspend fun addAsset(asset: Asset): Asset = edit { d ->
        d.assets.firstOrNull { it.uri == asset.uri }?.let { return@edit d to it }
        val version = if (asset.shotId != null) {
            (d.assets.filter { it.shotId == asset.shotId && it.type == asset.type }.maxOfOrNull { it.version } ?: 0) + 1
        } else asset.version
        val a = asset.copy(id = asset.id.ifBlank { newId("ast") }, version = version)
        d.copy(assets = d.assets + a) to a
    }

    /** One atomic, project-scoped library removal. Never invalidates historical file references. */
    suspend fun deleteLibraryAssets(ids: Set<String>): Int = edit { d ->
        val targets=d.assets.filter {it.id in ids && !it.libraryDeleted}.map {it.id}.toSet()
        d.copy(assets=d.assets.map {if(it.id in targets) it.copy(libraryDeleted=true) else it}) to targets.size
    }

    suspend fun deleteFromLibrary(projectId: String, ids: Set<String>): Int = edit { d ->
        val targets = d.assets.filter { it.id in ids && it.projectId == projectId && !it.libraryDeleted }.map { it.id }.toSet()
        d.copy(assets = d.assets.map { if (it.id in targets) it.copy(libraryDeleted = true) else it }) to targets.size
    }

    suspend fun updateAsset(id: String, change: (Asset) -> Asset): Asset = edit { d ->
        val cur = d.assets.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Asset desconocido: $id")
        val next = change(cur).copy(id = cur.id, uri = cur.uri, createdAt = cur.createdAt)
        d.copy(assets = d.assets.map { if (it.id == id) next else it }) to next
    }

    /** Native file operations use this only after preserving the previous URI on disk. */
    suspend fun relocateAsset(id: String, expectedUri: String, uri: String, displayName: String): Asset = edit { d ->
        val current = d.assets.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Asset desconocido: $id")
        require(current.uri == expectedUri) { "El archivo cambió durante la operación." }
        require(uri.isNotBlank() && displayName.isNotBlank())
        require(d.assets.none { it.id != id && it.uri == uri }) { "La nueva ruta ya está indexada." }
        val next = current.copy(uri = uri, displayName = displayName)
        d.copy(assets = d.assets.map { if (it.id == id) next else it }) to next
    }

    fun search(q: AssetQuery): List<Asset> {
        val project = q.projectId?.let { findProject(it) }
        val chars = q.character?.let { name -> data.characters.filter { c -> norm(c.name).contains(norm(name)) || c.aliases.any { norm(it).contains(norm(name)) } }.map { it.id }.toSet() }
        val shotIds = q.shotCode?.let { code -> data.shots.filter { (project == null || it.projectId == project.id) && norm(it.code) == norm(code) }.map { it.id }.toSet() }
        val sceneIds = q.sceneCode?.let { code -> data.scenes.filter { (project == null || it.projectId == project.id) && (norm(it.code) == norm(code) || norm(it.title ?: "").contains(norm(code))) }.map { it.id }.toSet() }
        val gens = data.generations.associateBy { it.id }
        val words = q.text?.let { norm(it).split(' ').filter { w -> w.length > 1 } }.orEmpty()
        return data.assets.asSequence()
            .filter { !it.libraryDeleted }
            .filter { project == null || it.projectId == project.id }
            .filter { q.type == null || it.type == q.type }
            .filter { q.role == null || it.role == q.role }
            .filter { chars == null || it.characterIds.any { c -> c in chars } }
            .filter { shotIds == null || it.shotId in shotIds }
            .filter { sceneIds == null || it.sceneId in sceneIds || data.shots.firstOrNull { s -> s.id == it.shotId }?.sceneId in sceneIds }
            .filter { q.modelId == null || gens[it.generationId]?.modelId?.contains(q.modelId, true) == true }
            .filter { a ->
                if (words.isEmpty()) true else {
                    val hay = norm(listOfNotNull(a.displayName, gens[a.generationId]?.prompt,
                        gens[a.generationId]?.providerId, gens[a.generationId]?.modelId, a.tags.joinToString(" "),
                        a.characterIds.mapNotNull { id -> data.characters.firstOrNull { it.id == id }?.name }.joinToString(" "),
                        data.scenes.firstOrNull { it.id == a.sceneId }?.title).joinToString(" "))
                    words.all { hay.contains(it) }
                }
            }
            .sortedByDescending { it.createdAt }
            .take(q.limit)
            .toList()
    }

    /** «¿Qué planos tienen keyframe pero no vídeo?» */
    fun shotsWith(projectId: String?, has: AssetType, missing: AssetType): List<Shot> {
        val project = projectId?.let { findProject(it) }
        return data.shots.filter { project == null || it.projectId == project.id }.filter { s ->
            val types = data.assets.filter { it.shotId == s.id }.map { it.type }.toSet()
            has in types && missing !in types
        }.sortedWith(compareBy({ it.code.toIntOrNull() ?: Int.MAX_VALUE }, { it.code }))
    }

    fun shotStatus(shotId: String): String {
        val a = data.assets.filter { it.shotId == shotId }
        return when {
            a.any { it.role == AssetRole.FINAL } -> "FINAL"
            a.any { it.type == AssetType.VIDEO } -> "VIDEO"
            a.any { it.type == AssetType.IMAGE } -> "KEYFRAME"
            else -> "PLANNED"
        }
    }

    fun shotVersions(projectId: String?, shotCode: String): List<Asset> {
        val project = projectId?.let { findProject(it) }
        val ids = data.shots.filter { (project == null || it.projectId == project.id) && norm(it.code) == norm(shotCode) }.map { it.id }.toSet()
        return data.assets.filter { it.shotId in ids }.sortedWith(compareBy({ it.type }, { it.version }))
    }

    // ---------- generaciones / lineage ----------

    fun generation(id: String): GenerationRecord? = data.generations.firstOrNull { it.id == id || it.nexusJobId == id }
    fun generationOfAsset(assetId: String): GenerationRecord? = asset(assetId)?.generationId?.let { generation(it) }

    suspend fun upsertGeneration(rec: GenerationRecord): GenerationRecord = edit { d ->
        val existing = d.generations.firstOrNull { it.id == rec.id || (rec.nexusJobId != null && it.nexusJobId == rec.nexusJobId) }
        if (existing == null) d.copy(generations = d.generations + rec) to rec
        else {
            val merged = rec.copy(id = existing.id, agentPlanId = rec.agentPlanId ?: existing.agentPlanId,
                projectId = rec.projectId ?: existing.projectId, shotId = rec.shotId ?: existing.shotId,
                parentGenerationId = rec.parentGenerationId ?: existing.parentGenerationId,
                outputAssetIds = (existing.outputAssetIds + rec.outputAssetIds).distinct())
            d.copy(generations = d.generations.map { if (it.id == existing.id) merged else it }) to merged
        }
    }

    fun history(projectId: String? = null, limit: Int = 30): List<GenerationRecord> {
        val project = projectId?.let { findProject(it) }
        return data.generations.filter { project == null || it.projectId == project.id }.sortedByDescending { it.createdAt }.take(limit)
    }

    companion object {
        const val MAX_FIELD = 2_000
        const val MAX_RULES = 40
        const val MAX_NOTES = 80
        val JSON = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = false }
        fun newId(prefix: String) = prefix + "_" + UUID.randomUUID().toString().replace("-", "").take(12)
        /** Minúsculas y sin tildes: «Julián» encuentra «julian». */
        fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    }
}
