package com.vidadomu.nexus.agent.core.assets

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
data class ProjectTransferMontage(
    val id: String, val revision: Long, val sceneId: String? = null, val appliedRevision: Long? = null,
    val undoneRevision: Long? = null,
)

@Serializable
data class ProjectTransferReceipt(
    val requestId: String, val sourceId: String, val targetId: String, val retireSource: Boolean,
    val fingerprint: String, val before: JsonObject,
    val montages: List<ProjectTransferMontage> = emptyList(),
    val after: JsonObject? = null,
    /** PREVIEW, INDEX_MERGED, COMPLETED, UNDOING, UNDONE. */
    val state: String = "PREVIEW", val createdAt: Long, val updatedAt: Long = createdAt,
    val contextSessionId: String? = null, val contextMoved: Boolean = false,
)

/** These names resolve uniquely; a fuzzy first match is never a mutation destination. */
fun AssetIndex.transferProjectId(query: String): String {
    val projects = snapshot().projects
    projects.firstOrNull { it.id == query }?.let { return it.id }
    val exact = projects.filter { AssetIndex.norm(it.name) == AssetIndex.norm(query) }
    require(exact.size == 1) { if (exact.isEmpty()) "No existe ese proyecto." else "El nombre coincide con varios proyectos. Indica su id." }
    return exact.single().id
}

internal fun transferContent(d: IndexData, source: String, target: String): JsonObject {
    val owners = setOf(source, target)
    val links = d.story.links.filter { it.projectId in owners }
    val linkedAssets = links.mapTo(HashSet()) { it.assetId }
    val membership = ProjectAssetMembership(d)
    val scoped = IndexData(
        projects = d.projects.filter { it.id in owners },
        assets = d.assets.filter { it.projectId in owners || it.id in linkedAssets || membership.projectIds(it).any { owner -> owner in owners } },
        scenes = d.scenes.filter { it.projectId in owners }, shots = d.shots.filter { it.projectId in owners },
        characters = d.characters.filter { it.projectId in owners },
        generations = d.generations.filter { it.projectId in owners },
        story = d.story.copy(
            blocks = d.story.blocks.filter { it.projectId in owners }, links = links,
            elements = d.story.elements.filter { it.projectId in owners }, preferences = d.story.preferences.filter { it.projectId in owners },
            proposals = d.story.proposals.filter { it.projectId in owners }, tasks = d.story.tasks.filter { it.projectId in owners },
            autonomy = d.story.autonomy.filter { it.projectId in owners }, receipts = d.story.receipts.filter { it.projectId in owners },
            history = d.story.history.filter { it.projectId in owners }, legacyAdoptedProjectIds = d.story.legacyAdoptedProjectIds.filter { it in owners },
        ),
    )
    return AssetIndex.JSON.encodeToJsonElement(scoped).jsonObject
}

internal fun transferHash(content: JsonObject, montages: List<ProjectTransferMontage>, retire: Boolean): String {
    val value = content.toString() + "\n" + montages.joinToString("\n") { "${it.id}:${it.revision}:${it.sceneId}" } + "\n$retire"
    return MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

fun AssetIndex.projectTransfer(requestId: String): ProjectTransferReceipt = snapshot().projectTransfers.firstOrNull { it.requestId == requestId }
    ?: throw IllegalArgumentException("No existe esa operación de traslado. Prepara su vista previa.")

suspend fun AssetIndex.previewProjectTransfer(requestId: String, sourceId: String, targetId: String, retireSource: Boolean,
    montages: List<ProjectTransferMontage>, at: Long): ProjectTransferReceipt = edit { d ->
    require(requestId.isNotBlank() && requestId.length <= 200) { "Indica request_id estable, de hasta 200 caracteres." }
    d.projectTransfers.firstOrNull { it.requestId == requestId }?.let { previous ->
        require(previous.sourceId == sourceId && previous.targetId == targetId && previous.retireSource == retireSource) { "request_id ya pertenece a otra operación." }
        return@edit d to previous
    }
    require(sourceId != targetId) { "Origen y destino deben ser distintos." }
    require(d.projects.firstOrNull { it.id == sourceId }?.archived == false) { "El proyecto de origen no existe o está archivado." }
    require(d.projects.firstOrNull { it.id == targetId }?.archived == false) { "El proyecto de destino no existe o está archivado." }
    // Legacy organize tools resolve scene/shot codes within a project. Do not introduce an
    // ambiguous code that a later first-match resolver could silently associate incorrectly.
    val sceneCodes = d.scenes.filter { it.projectId == targetId }.mapTo(HashSet()) { AssetIndex.norm(it.code) }
    val shotCodes = d.shots.filter { it.projectId == targetId }.mapTo(HashSet()) { AssetIndex.norm(it.code) }
    require(d.scenes.none { it.projectId == sourceId && AssetIndex.norm(it.code) in sceneCodes } &&
        d.shots.none { it.projectId == sourceId && AssetIndex.norm(it.code) in shotCodes }) {
        "Origen y destino tienen códigos de escena o plano coincidentes. Renómbralos antes del traslado para evitar asociaciones ambiguas; no se ha movido nada."
    }
    require(d.projectTransfers.none { it.state in setOf("INDEX_MERGED", "UNDOING") &&
        (sourceId in setOf(it.sourceId, it.targetId) || targetId in setOf(it.sourceId, it.targetId)) }) { "Hay otro traslado sin terminar sobre estos proyectos." }
    val running = d.story.tasks.any { it.projectId == sourceId && it.status.lowercase() in setOf("running", "generating", "submitting", "processing") } ||
        d.generations.any { it.projectId == sourceId && it.status.uppercase() !in setOf("COMPLETED", "FAILED", "CANCELLED", "CANCELED", "DONE") }
    require(!running) { "El origen tiene trabajos en curso. Espera a que terminen antes de trasladarlo; no se ha cancelado ni reenviado nada." }
    val before = transferContent(d, sourceId, targetId)
    val ordered = montages.sortedBy { it.id }
    val receipt = ProjectTransferReceipt(requestId, sourceId, targetId, retireSource, transferHash(before, ordered, retireSource), before, ordered, createdAt = at)
    d.copy(projectTransfers = d.projectTransfers + receipt) to receipt
}

internal suspend fun AssetIndex.updateTransfer(requestId: String, change: (ProjectTransferReceipt) -> ProjectTransferReceipt): ProjectTransferReceipt = edit { d ->
    val old = d.projectTransfers.firstOrNull { it.requestId == requestId } ?: error("Operación desconocida.")
    val next = change(old)
    d.copy(projectTransfers = d.projectTransfers.map { if (it.requestId == requestId) next else it }) to next
}

/** One AssetIndex transaction: originals, IDs, immutable story/generation history and file URIs remain intact. */
internal suspend fun AssetIndex.mergeTransferIndex(requestId: String, fingerprint: String, at: Long): ProjectTransferReceipt = edit { d ->
    val r = d.projectTransfers.firstOrNull { it.requestId == requestId } ?: error("Prepara la vista previa del traslado.")
    require(r.fingerprint == fingerprint) { "La huella no corresponde a esta vista previa." }
    if (r.state != "PREVIEW") return@edit d to r
    require(transferContent(d, r.sourceId, r.targetId) == r.before) { "El contenido cambió desde la vista previa. Prepara otra operación; no se ha movido nada." }
    val source = r.sourceId; val target = r.targetId
    val sourceScenes = d.scenes.filter { it.projectId == source }.sortedWith(compareBy({ it.order }, { it.id }))
    val sceneOrder = sourceScenes.mapIndexed { index, scene -> scene.id to ((d.scenes.filter { it.projectId == target }.maxOfOrNull { it.order } ?: -1) + 1 + index) }.toMap()
    val migrated = d.copy(
        projects = d.projects.map { if (it.id in setOf(source, target)) it.copy(revision = it.revision + 1) else it },
        assets = d.assets.map { if (it.projectId == source) it.copy(projectId = target) else it },
        scenes = d.scenes.map { if (it.projectId == source) it.copy(projectId = target, order = sceneOrder.getValue(it.id), revision = it.revision + 1) else it },
        shots = d.shots.map { if (it.projectId == source) it.copy(projectId = target, revision = it.revision + 1) else it },
        characters = d.characters.map { if (it.projectId == source) it.copy(projectId = target, revision = it.revision + 1) else it },
        generations = d.generations.map { if (it.projectId == source) it.copy(projectId = target) else it },
        story = d.story.copy(
            blocks = d.story.blocks.map { if (it.projectId == source) it.copy(projectId = target, revision = it.revision + 1) else it },
            links = d.story.links.map { if (it.projectId == source) it.copy(projectId = target, targetId = if (it.scope == "project" && it.targetId == source) target else it.targetId, revision = it.revision + 1) else it },
            elements = d.story.elements.map { if (it.projectId == source) it.copy(projectId = target, revision = it.revision + 1) else it },
            preferences = d.story.preferences.map { if (it.projectId == source) it.copy(projectId = target, targetId = if (it.scope == "project" && it.targetId == source) target else it.targetId, revision = it.revision + 1) else it },
            // Existing approval was scoped to the source, including creation commands without entity revisions.
            // Preserve its commands and require a new proposal; applied/rejected proposals remain provenance.
            proposals = d.story.proposals.map { if (it.projectId == source && it.status in setOf("pending", "partial"))
                it.copy(projectId = target, status = "invalidated_transfer", revision = it.revision + 1) else it },
            tasks = d.story.tasks.map { if (it.projectId == source && it.status !in setOf("complete", "completed", "cancelled", "failed")) it.copy(projectId = target,
                status = if (it.status in setOf("queued", "prepared", "pending_resume")) "blocked" else it.status,
                detail = listOfNotNull(it.detail, "Proyecto trasladado: revisa el alcance antes de reanudar.").joinToString("\n"), revision = it.revision + 1) else it },
            // Moving content is not consent to widen another project's autonomous execution policy.
            autonomy = d.story.autonomy.map { if (it.projectId == source && it.enabled) it.copy(enabled = false, revision = it.revision + 1) else it },
            legacyAdoptedProjectIds = (d.story.legacyAdoptedProjectIds + listOfNotNull(target.takeIf { source in d.story.legacyAdoptedProjectIds })).distinct(),
        ),
    )
    val next = r.copy(after = transferContent(migrated, source, target), state = "INDEX_MERGED", updatedAt = at)
    migrated.copy(projectTransfers = migrated.projectTransfers.map { if (it.requestId == requestId) next else it }) to next
}

internal suspend fun AssetIndex.completeTransfer(requestId: String, at: Long): ProjectTransferReceipt = edit { d ->
    val r = d.projectTransfers.first { it.requestId == requestId }
    if (r.state == "COMPLETED") return@edit d to r
    require(r.state == "INDEX_MERGED" && transferContent(d, r.sourceId, r.targetId) == r.after) { "El contenido cambió después del traslado. El origen se conserva sin archivar." }
    require(r.montages.all { it.appliedRevision != null }) { "Faltan montajes por verificar. El origen sigue disponible." }
    val completed = if (r.retireSource) d.copy(projects = d.projects.map { if (it.id == r.sourceId) it.copy(archived = true, revision = it.revision + 1) else it }) else d
    val next = r.copy(after = transferContent(completed, r.sourceId, r.targetId), state = "COMPLETED", updatedAt = at)
    completed.copy(projectTransfers = completed.projectTransfers.map { if (it.requestId == requestId) next else it }) to next
}

/** A late cross-store conflict must not leave a known-incomplete source hidden. Never absorb new edits into the undo baseline. */
internal suspend fun AssetIndex.reopenIncompleteTransfer(requestId: String, at: Long): ProjectTransferReceipt = edit { d ->
    val r = d.projectTransfers.first { it.requestId == requestId }
    val current = d.projects.first { it.id == r.sourceId }
    val restored = current.copy(archived = false, revision = current.revision + if (current.archived) 1 else 0)
    val expected = AssetIndex.JSON.decodeFromJsonElement<IndexData>(requireNotNull(r.after))
    val after = expected.copy(projects = expected.projects.map { if (it.id == r.sourceId) it.copy(archived = false, revision = restored.revision) else it })
    val next = r.copy(state = "INDEX_MERGED", after = AssetIndex.JSON.encodeToJsonElement(after).jsonObject, updatedAt = at)
    d.copy(projects = d.projects.map { if (it.id == r.sourceId) restored else it }, projectTransfers = d.projectTransfers.map { if (it.requestId == requestId) next else it }) to next
}

/** Restores only the scoped records, after comparing their complete current value. Other projects are untouched. */
internal suspend fun AssetIndex.undoTransferIndex(requestId: String, at: Long): ProjectTransferReceipt = edit { d ->
    val r = d.projectTransfers.first { it.requestId == requestId }
    if (r.state == "UNDONE") return@edit d to r
    require(r.state in setOf("INDEX_MERGED", "COMPLETED", "UNDOING") && transferContent(d, r.sourceId, r.targetId) == r.after) { "No se puede deshacer: el proyecto recibió cambios posteriores. No se han sobrescrito." }
    val before = AssetIndex.JSON.decodeFromJsonElement<IndexData>(r.before)
    val after = AssetIndex.JSON.decodeFromJsonElement<IndexData>(requireNotNull(r.after))
    fun <T> restore(all: List<T>, old: List<T>, applied: List<T>, id: (T) -> String): List<T> {
        val touched = (old + applied).mapTo(HashSet(), id)
        return all.filterNot { id(it) in touched } + old
    }
    val owners = setOf(r.sourceId, r.targetId)
    val reverted = d.copy(
        projects = restore(d.projects, before.projects.map { old -> old.copy(revision = d.projects.first { it.id == old.id }.revision + 1) }, after.projects) { it.id },
        assets = restore(d.assets, before.assets, after.assets) { it.id },
        scenes = restore(d.scenes, before.scenes.map { old -> old.copy(revision = d.scenes.first { it.id == old.id }.revision + 1) }, after.scenes) { it.id },
        shots = restore(d.shots, before.shots.map { old -> old.copy(revision = d.shots.first { it.id == old.id }.revision + 1) }, after.shots) { it.id },
        characters = restore(d.characters, before.characters.map { old -> old.copy(revision = d.characters.first { it.id == old.id }.revision + 1) }, after.characters) { it.id },
        generations = restore(d.generations, before.generations, after.generations) { it.id },
        story = d.story.copy(
            blocks = restore(d.story.blocks, before.story.blocks.map { old -> old.copy(revision = d.story.blocks.first { it.id == old.id }.revision + 1) }, after.story.blocks) { it.id },
            links = restore(d.story.links, before.story.links.map { old -> old.copy(revision = d.story.links.first { it.id == old.id }.revision + 1) }, after.story.links) { it.id },
            elements = restore(d.story.elements, before.story.elements.map { old -> old.copy(revision = d.story.elements.first { it.id == old.id }.revision + 1) }, after.story.elements) { it.id },
            preferences = restore(d.story.preferences, before.story.preferences.map { old -> old.copy(revision = d.story.preferences.first { it.id == old.id }.revision + 1) }, after.story.preferences) { it.id },
            proposals = restore(d.story.proposals, before.story.proposals, after.story.proposals) { it.id },
            tasks = restore(d.story.tasks, before.story.tasks, after.story.tasks) { it.id },
            autonomy = restore(d.story.autonomy, before.story.autonomy, after.story.autonomy) { it.projectId },
            legacyAdoptedProjectIds = d.story.legacyAdoptedProjectIds.filterNot { it in owners } + before.story.legacyAdoptedProjectIds,
        ),
    )
    val next = r.copy(state = "UNDONE", updatedAt = at)
    reverted.copy(projectTransfers = reverted.projectTransfers.map { if (it.requestId == requestId) next else it }) to next
}
