package com.vidadomu.nexus.cut

import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.assets.GenerationRecord
import com.vidadomu.nexus.agent.core.assets.IndexData
import com.vidadomu.nexus.agent.core.assets.Project
import com.vidadomu.nexus.cut.command.CutSession
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.model.MediaKind
import com.vidadomu.nexus.cut.storage.ProjectStore
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CutHubCover(val uri: String, val type: AssetType)

/** A projection of existing identities, including projects that do not have a montage yet. */
data class CutHubProject(
    val project: Project,
    val montages: List<ProjectStore.Summary>,
    val material: List<Asset>,
    val generations: List<GenerationRecord>,
    val sceneCount: Int,
    val shotCount: Int,
    val coverUri: String?,
    val coverType: AssetType?,
    val activityAt: Long,
) {
    val id: String get() = project.id
    val name: String get() = project.name
    val archived: Boolean get() = project.archived
    val montageCount: Int get() = montages.size
    val materialCount: Int get() = material.size
    val generationCount: Int get() = generations.size
}

data class CutProjectHubSnapshot(
    /** Archived projects remain here so their content can be opened and the project restored. */
    val projects: List<CutHubProject> = emptyList(),
    /** No owner, or an owner that no longer exists. The original binding is never rewritten. */
    val looseMontages: List<ProjectStore.Summary> = emptyList(),
    val montageCovers: Map<String, CutHubCover> = emptyMap(),
)

/** Reads the Library and Cut workspace together; project metadata never moves their media. */
class CutProjectHub(private val assets: AssetIndex, private val workspace: CutWorkspace) {
    suspend fun snapshot(): CutProjectHubSnapshot {
        val index = assets.snapshot()
        val montages = workspace.list()
        val covers = withContext(Dispatchers.IO) {
            buildMap {
                for (montage in montages) {
                    val project = try {
                        workspace.opened(montage.id)?.state?.value?.project ?: workspace.store.load(montage.id).project
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { continue }
                    val cover = project.timeline.videoTracks.flatMap { it.clips }
                        .sortedWith(compareBy({ it.timelineStartUs }, { it.id }))
                        .firstNotNullOfOrNull { clip ->
                            project.media(clip.mediaId)?.takeIf { it.kind != MediaKind.AUDIO && isLocalCutUri(it.uri) }?.let {
                                CutHubCover(it.uri, if (it.kind == MediaKind.IMAGE) AssetType.IMAGE else AssetType.VIDEO)
                            }
                        }
                    if (cover != null) put(montage.id, cover)
                }
            }
        }
        return withContext(Dispatchers.Default) { projectCutHub(index, montages, covers) }
    }

    suspend fun createProject(name: String): Project {
        val project = assets.createProject(validName(name))
        require(!project.archived) { "Ya existe un proyecto archivado con ese nombre. Restáuralo para continuar." }
        return project
    }

    suspend fun renameProject(projectId: String, name: String): Project =
        assets.updateProjectMetadata(projectId, name = validName(name))

    suspend fun setArchived(projectId: String, archived: Boolean): Project =
        assets.updateProjectMetadata(projectId, archived = archived)

    /** Every call intentionally creates another montage; opening an existing one is a separate action. */
    suspend fun createMontage(projectId: String?, name: String): CutSession {
        val clean = validName(name)
        if (projectId != null) {
            val project = assets.snapshot().projects.firstOrNull { it.id == projectId }
                ?: throw IllegalArgumentException("El proyecto ya no está disponible.")
            require(!project.archived) { "Restaura el proyecto antes de crear un montaje." }
        }
        return workspace.create(clean, nexusProjectId = projectId)
    }

    private fun validName(name: String): String = name.trim().also {
        require(it.isNotBlank()) { "Escribe un nombre para continuar." }
    }
}

/** Pure projection: associations may be shared across projects; no asset acquires a new owner. */
internal fun projectCutHub(
    index: IndexData,
    montages: List<ProjectStore.Summary>,
    montageCovers: Map<String, CutHubCover> = emptyMap(),
): CutProjectHubSnapshot {
    val montageOrder = compareByDescending<ProjectStore.Summary> { it.modifiedAtMs }.thenBy { it.id }
    val knownProjects = index.projects.mapTo(HashSet()) { it.id }
    val membership = com.vidadomu.nexus.agent.core.assets.ProjectAssetMembership(index)
    val assetsById = index.assets.associateBy { it.id }
    val assetsByGeneration = index.assets.filter { it.generationId != null }.groupBy { it.generationId }
    val generationOutputs = index.generations.associate { generation ->
        generation.id to (generation.outputAssetIds.mapNotNull(assetsById::get) + assetsByGeneration[generation.id].orEmpty()).distinctBy { it.id }
    }
    val projects = index.projects.map { project ->
        val scenes = index.scenes.filter { it.projectId == project.id }
        val shots = index.shots.filter { it.projectId == project.id }
        val material = index.assets.filter { membership.inProject(it, project.id) }
            .distinctBy { it.id }.sortedWith(compareByDescending<Asset> { it.createdAt }.thenBy { it.id })
        val materialIds = material.mapTo(HashSet()) { it.id }
        val generations = index.generations.filter { generation ->
            // Once outputs are indexed, their current assignments win over a generation's old owner.
            // A move to another project or to the general Library must not silently undo itself here.
            val outputs = generationOutputs[generation.id].orEmpty()
            if (outputs.isEmpty()) generation.projectId == project.id else outputs.any { it.id in materialIds }
        }.distinctBy { it.id }.sortedWith(compareByDescending<GenerationRecord> { it.completedAt ?: it.createdAt }.thenBy { it.id })
        val ownMontages = montages.filter { it.nexusProjectId == project.id }.sortedWith(montageOrder)
        val visualAsset = material.firstOrNull { it.type in setOf(AssetType.IMAGE, AssetType.VIDEO) && isLocalCutUri(it.uri) }
        val montageCover = ownMontages.firstNotNullOfOrNull { montage -> montageCovers[montage.id]?.let { montage.modifiedAtMs to it } }
        val cover = if (montageCover != null && (visualAsset == null || montageCover.first >= visualAsset.createdAt)) montageCover.second
            else visualAsset?.let { CutHubCover(it.uri, it.type) }
        val activity = sequenceOf(
            sequenceOf(project.createdAt, project.memory.updatedAt ?: 0L),
            ownMontages.asSequence().map { it.modifiedAtMs },
            material.asSequence().map { it.createdAt },
            generations.asSequence().map { it.completedAt ?: it.createdAt },
            index.story.history.asSequence().filter { it.projectId == project.id }.map { it.at },
            index.story.tasks.asSequence().filter { it.projectId == project.id }.map { it.updatedAt },
            index.projectSessions.asSequence().filter { it.projectId == project.id }.map { it.to ?: it.from },
        ).flatten().maxOrNull() ?: project.createdAt
        CutHubProject(project, ownMontages, material, generations, scenes.size, shots.size, cover?.uri, cover?.type, activity)
    }.sortedWith(compareByDescending<CutHubProject> { it.activityAt }.thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
    return CutProjectHubSnapshot(
        projects = projects,
        looseMontages = montages.filter { it.nexusProjectId !in knownProjects }.sortedWith(montageOrder),
        montageCovers = montageCovers,
    )
}
