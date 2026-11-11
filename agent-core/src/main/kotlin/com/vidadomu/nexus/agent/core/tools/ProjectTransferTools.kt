package com.vidadomu.nexus.agent.core.tools

import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.cut.command.CutSession
import com.vidadomu.nexus.cut.command.CutFailure
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.command.Op
import com.vidadomu.nexus.cut.command.Origin
import com.vidadomu.nexus.cut.command.Outcome
import com.vidadomu.nexus.cut.command.Request
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** A durable saga: index commits atomically; each montage commits through its own command bus. */
class ProjectTransferCoordinator(private val assets: AssetIndex, private val cut: CutWorkspace, private val clock: () -> Long = System::currentTimeMillis) {
    private companion object { val operations = Mutex() }

    suspend fun preview(requestId: String, source: String, target: String, retireSource: Boolean): ProjectTransferReceipt = operations.withLock {
        val sourceId = assets.transferProjectId(source)
        val targetId = assets.transferProjectId(target)
        assets.snapshot().projectTransfers.firstOrNull { it.requestId == requestId }?.let { previous ->
            require(previous.sourceId == sourceId && previous.targetId == targetId && previous.retireSource == retireSource) { "request_id ya pertenece a otra operación." }
            return@withLock previous
        }
        val montages = cut.list().filter { it.nexusProjectId == sourceId }.map { summary ->
            val p = cut.session(summary.id).state.value.project
            require(p.nexusProjectId == sourceId) { "Un montaje cambió de proyecto mientras se preparaba el traslado." }
            ProjectTransferMontage(p.id, p.revision, p.sceneId)
        }
        assets.previewProjectTransfer(requestId, sourceId, targetId, retireSource, montages, clock())
    }

    suspend fun execute(requestId: String, fingerprint: String): JsonObject = operations.withLock {
        var r = assets.projectTransfer(requestId)
        require(r.fingerprint == fingerprint) { "La huella no corresponde a esta operación." }
        require(r.state !in setOf("UNDONE", "UNDOING")) { "Esta operación se ha deshecho o se está deshaciendo." }
        if (r.state == "COMPLETED") return@withLock checkedReport(r)
        if (r.state == "PREVIEW") {
            val planned = r.montages.mapTo(HashSet()) { it.id }
            require(cut.list().filter { it.nexusProjectId == r.sourceId }.map { it.id }.toSet() == planned) { "Los montajes del origen cambiaron desde la vista previa. No se ha movido nada." }
            r.montages.forEach { plan ->
                val p = cut.session(plan.id).state.value.project
                require(p.nexusProjectId == r.sourceId && p.sceneId == plan.sceneId && p.revision == plan.revision) { "El montaje ${p.name} cambió desde la vista previa. No se ha movido nada." }
            }
            r = assets.mergeTransferIndex(requestId, fingerprint, clock())
        }
        for (plan in r.montages) {
            val session = cut.session(plan.id)
            val key = "project-transfer:${r.requestId}:${plan.id}"
            if (plan.appliedRevision == null) {
                // The saved key also recovers a process death between a montage commit and its receipt update.
                val outcome = session.execute(Request(
                    ops = listOf(Op("project.link_story", CutSession.argsOf("nexus_project_id" to r.targetId))),
                    origin = Origin.AGENT, label = "Trasladar montaje al proyecto", expectedRevision = plan.revision, idempotencyKey = key,
                ))
                if (outcome is Outcome.Failed) return@withLock report(assets.projectTransfer(requestId), listOf("Un montaje no se pudo trasladar: ${outcome.error.message}. El origen sigue disponible; reanuda la misma operación tras revisar el conflicto."))
                val done = outcome as Outcome.Done
                val p = session.state.value.project
                if (p.nexusProjectId != r.targetId || p.sceneId != plan.sceneId || p.revision != done.revision)
                    return@withLock report(assets.projectTransfer(requestId), listOf("Un montaje recibió cambios después del traslado. El origen no se ha archivado."))
                assets.updateTransfer(requestId) { current -> current.copy(montages = current.montages.map { if (it.id == plan.id) it.copy(appliedRevision = done.revision) else it }, updatedAt = clock()) }
            }
        }
        r = assets.projectTransfer(requestId)
        val blockers = verify(r)
        if (blockers.isNotEmpty()) return@withLock report(r, blockers)
        checkedReport(r, complete = true)
    }

    suspend fun status(requestId: String): JsonObject = operations.withLock {
        val r = assets.projectTransfer(requestId)
        if (r.state in setOf("INDEX_MERGED", "COMPLETED")) checkedReport(r) else report(r, emptyList())
    }

    suspend fun undo(requestId: String): JsonObject = operations.withLock {
        var r = assets.projectTransfer(requestId)
        if (r.state == "UNDONE") return@withLock report(r, emptyList())
        require(r.state in setOf("INDEX_MERGED", "COMPLETED", "UNDOING")) { "La vista previa todavía no ha aplicado cambios." }
        require(transferContent(assets.snapshot(), r.sourceId, r.targetId) == r.after) { "No se puede deshacer: hubo cambios posteriores en los proyectos. Se conservan sin sobrescribirlos." }
        ensureUndoReferencesSafe(r)
        val currentIds = cut.list().mapTo(HashSet()) { it.id }
        r.montages.forEach { plan ->
            require(plan.id in currentIds) { "No se puede deshacer: falta un montaje." }
            val p = cut.session(plan.id).state.value.project
            if (plan.undoneRevision != null) require(p.nexusProjectId == r.sourceId && p.sceneId == plan.sceneId && p.revision == plan.undoneRevision) { "Un montaje cambió después de su restauración parcial." }
            else if (plan.appliedRevision != null) {
                if (p.nexusProjectId == r.sourceId && p.sceneId == plan.sceneId) {
                    // Recover a committed inverse whose receipt was not fsynced before process death.
                    val replay = cut.session(plan.id).execute(undoRequest(r, plan))
                    require(replay is Outcome.Done && replay.replayed && replay.revision == p.revision) { "No se puede recuperar el deshacer: el montaje cambió fuera de esta operación." }
                    assets.updateTransfer(requestId) { current -> current.copy(montages = current.montages.map { if (it.id == plan.id) it.copy(undoneRevision = replay.revision) else it }) }
                } else require(p.nexusProjectId == r.targetId && p.sceneId == plan.sceneId && p.revision == plan.appliedRevision) { "No se puede deshacer: un montaje recibió una edición posterior." }
            }
            else require(p.nexusProjectId == r.sourceId && p.sceneId == plan.sceneId) { "Reanuda el traslado para recuperar el recibo de un montaje antes de deshacerlo." }
        }
        r = assets.updateTransfer(requestId) { it.copy(state = "UNDOING", updatedAt = clock()) }
        for (plan in r.montages.filter { it.appliedRevision != null && it.undoneRevision == null }) {
            val session = cut.session(plan.id)
            val outcome = session.execute(undoRequest(r, plan))
            require(outcome is Outcome.Done) { "No se pudo restaurar un montaje. El deshacer queda pendiente y se puede reanudar sin repetir cambios." }
            assets.updateTransfer(requestId) { current -> current.copy(montages = current.montages.map { if (it.id == plan.id) it.copy(undoneRevision = outcome.revision) else it }, updatedAt = clock()) }
        }
        r = assets.projectTransfer(requestId)
        r.montages.forEach { plan ->
            val p = cut.session(plan.id).state.value.project
            require(p.nexusProjectId == r.sourceId && p.sceneId == plan.sceneId &&
                (plan.undoneRevision == null || p.revision == plan.undoneRevision)) {
                "Un montaje cambió durante el deshacer. El recibo conserva los pasos realizados; no se ha restaurado el índice."
            }
        }
        ensureUndoReferencesSafe(r)
        r = assets.undoTransferIndex(requestId, clock())
        report(r, emptyList())
    }

    private fun undoRequest(r: ProjectTransferReceipt, plan: ProjectTransferMontage) = Request(
        ops = listOf(Op("project.link_story", CutSession.argsOf("nexus_project_id" to r.sourceId))),
        origin = Origin.AGENT, label = "Deshacer traslado del montaje", expectedRevision = plan.appliedRevision,
        idempotencyKey = "project-transfer-undo:${r.requestId}:${plan.id}",
    )

    /** Retirement and its compensating visibility repair must finish even if the caller cancels. */
    private suspend fun checkedReport(receipt: ProjectTransferReceipt, complete: Boolean = false): JsonObject = withContext(NonCancellable) {
        var current = receipt
        val blockers = try {
            if (complete) current = assets.completeTransfer(receipt.requestId, clock())
            verify(current)
        } catch (_: Exception) {
            // A failed read proves nothing. Keep the durable receipt and expose the source again.
            current = assets.projectTransfer(receipt.requestId)
            listOf("No se pudo comprobar el índice o los montajes. El traslado no se da por verificado; consulta de nuevo su recibo.")
        }
        if (blockers.isNotEmpty() && current.retireSource && current.state == "COMPLETED")
            current = assets.reopenIncompleteTransfer(current.requestId, clock())
        report(current, blockers)
    }

    /** New destination work may depend on migrated story identities without changing AssetIndex. */
    private suspend fun ensureUndoReferencesSafe(r: ProjectTransferReceipt) {
        val before = AssetIndex.JSON.decodeFromJsonElement<IndexData>(r.before)
        val scenes = before.scenes.filter { it.projectId == r.sourceId }.mapTo(HashSet()) { it.id }
        val shots = before.shots.filter { it.projectId == r.sourceId }.mapTo(HashSet()) { it.id }
        if (scenes.isEmpty() && shots.isEmpty()) return
        val planned = r.montages.mapTo(HashSet()) { it.id }
        for (summary in cut.list().filter { it.id !in planned }) {
            val p = cut.session(summary.id).state.value.project
            if (p.nexusProjectId == r.sourceId) continue
            val dependent = p.sceneId in scenes || p.timeline.allTracks.any { track -> track.clips.any { clip ->
                clip.story?.sceneId in scenes || clip.story?.shotId in shots
            } }
            require(!dependent) { "No se puede deshacer: otro montaje usa una escena o plano trasladado. Conserva ese montaje y revisa sus vínculos antes de restaurar el origen." }
        }
    }

    private suspend fun verify(r: ProjectTransferReceipt): List<String> {
        val errors = mutableListOf<String>()
        if (r.after != null && transferContent(assets.snapshot(), r.sourceId, r.targetId) != r.after) errors += "El índice recibió cambios posteriores: no se da por verificada la operación."
        val summaries = cut.list()
        if (summaries.any { it.nexusProjectId == r.sourceId }) errors += "Todavía hay montajes vinculados al origen."
        for (plan in r.montages) {
            if (summaries.none { it.id == plan.id }) { errors += "Falta un montaje del manifiesto."; continue }
            val p = cut.session(plan.id).state.value.project
            if (plan.appliedRevision == null || p.nexusProjectId != r.targetId || p.sceneId != plan.sceneId || p.revision != plan.appliedRevision)
                errors += "El vínculo o la revisión de un montaje no coincide con su recibo."
        }
        return errors
    }

    private fun report(r: ProjectTransferReceipt, blockers: List<String>): JsonObject {
        val before = AssetIndex.JSON.decodeFromJsonElement<IndexData>(r.before)
        val membership = ProjectAssetMembership(before)
        return buildJsonObject {
            put("ok", blockers.isEmpty())
            put("request_id", r.requestId); put("source_project_id", r.sourceId); put("target_project_id", r.targetId)
            put("fingerprint", r.fingerprint); put("state", r.state); put("verified", r.state == "COMPLETED" && blockers.isEmpty())
            put("assets", before.assets.count { it.projectId == r.sourceId }); put("scenes", before.scenes.count { it.projectId == r.sourceId })
            put("owned_assets", before.assets.count { it.projectId == r.sourceId })
            put("material_associations", before.assets.count { membership.inProject(it, r.sourceId) })
            put("shared_or_legacy_material", before.assets.count { it.projectId != r.sourceId && membership.inProject(it, r.sourceId) })
            put("shots", before.shots.count { it.projectId == r.sourceId }); put("characters", before.characters.count { it.projectId == r.sourceId })
            put("generations", before.generations.count { it.projectId == r.sourceId }); put("montages", r.montages.size)
            put("montages_verified", r.montages.count { it.appliedRevision != null }); put("files_moved", false); put("identities_preserved", true)
            put("source_archived_recoverable", assets.snapshot().projects.firstOrNull { it.id == r.sourceId }?.archived == true)
            put("source_memory_and_history_preserved", true); put("sqlite_job_history_rewritten", false); put("chat_history_rewritten", false)
            put("proposals_requiring_new_review", before.story.proposals.count { it.projectId == r.sourceId && it.status in setOf("pending", "partial") })
            put("blockers", JsonArray(blockers.distinct().map(::JsonPrimitive)))
            put("message", when {
                blockers.isNotEmpty() -> "Traslado sin verificar por completo. El recibo conserva los pasos realizados; no se afirma que el origen se haya eliminado."
                r.state == "PREVIEW" -> "Vista previa guardada; no se ha trasladado contenido. Ejecuta solo la orden concreta solicitada por el usuario."
                r.state == "UNDONE" -> "Traslado deshecho; los originales y las identidades se conservan."
                r.state == "COMPLETED" && r.retireSource -> "Traslado verificado. El proyecto de origen está archivado y es recuperable; su memoria e historial se conservan. No se ha borrado definitivamente."
                r.state == "COMPLETED" -> "Traslado verificado. El proyecto de origen sigue disponible."
                else -> "Operación pendiente; reanuda con el mismo request_id."
            })
        }
    }
}

object ProjectTransferTools {
    fun all(d: ToolDeps): List<AgentTool> = d.cut?.let { workspace ->
        val coordinator = ProjectTransferCoordinator(d.assets, workspace, d.clock)
        listOf(
            TransferTool("preview_project_transfer", ToolCategory.READ, "Prepara y guarda una vista previa de trasladar TODO el contenido actual de un proyecto a otro: archivos, guion, asociaciones y montajes. No mueve archivos físicos ni cambia contenido. Resuelve nombres exactos; rechaza ambigüedades. Conserva request_id para ejecutar o reintentar.", schema {
                string("request_id", "Identificador estable de esta operación.", required = true)
                string("source_project", "Origen: id o nombre exacto.", required = true); string("target_project", "Destino: id o nombre exacto.", required = true)
                boolean("retire_source", "Archivar el origen recuperable SOLO tras verificar todo, si el usuario pidió quitar el proyecto original; por defecto false.")
            }) { input, _ ->
                val r = coordinator.preview(input.req("request_id"), input.req("source_project"), input.req("target_project"), input.bool("retire_source") ?: false)
                coordinator.status(r.requestId)
            },
            TransferTool("transfer_project", ToolCategory.ORGANIZE, "Ejecuta o reanuda la vista previa pedida por el usuario. El índice se migra en una transacción; los montajes pasan por CutSession. Verifica todos antes de archivar el origen recuperable. Conserva memoria, historial, IDs y originales. Un conflicto conserva el origen y devuelve los pendientes, nunca éxito supuesto.", schema {
                string("request_id", "Operación de la vista previa.", required = true); string("expected_fingerprint", "Huella exacta devuelta por la vista previa.", required = true)
            }) { input, session ->
                val result = coordinator.execute(input.req("request_id"), input.req("expected_fingerprint"))
                val r = d.assets.projectTransfer(input.req("request_id"))
                if (result["verified"]?.jsonPrimitive?.boolean == true && r.state == "COMPLETED" && r.retireSource && session.activeProjectId == r.sourceId) {
                    session.activeProjectId = r.targetId
                    d.assets.updateTransfer(r.requestId) { it.copy(contextSessionId = session.id, contextMoved = true) }
                }
                result
            },
            TransferTool("get_project_transfer", ToolCategory.READ, "Lee el recibo persistente y verifica su estado actual. Si el retiro quedó incompleto o no se puede verificar, reabre el origen para conservarlo visible. No repite el traslado ni genera contenido.", schema {
                string("request_id", "Operación que consultar.", required = true)
            }) { input, _ -> coordinator.status(input.req("request_id")) },
            TransferTool("undo_project_transfer", ToolCategory.ORGANIZE, "Deshace el traslado solo si sus registros y montajes no han recibido cambios posteriores; si no puede, informa sin sobrescribirlos. Es recuperable, local y no modifica archivos físicos.", schema {
                string("request_id", "Operación que deshacer.", required = true)
            }) { input, session ->
                val previous = d.assets.projectTransfer(input.req("request_id"))
                val result = coordinator.undo(previous.requestId)
                if (previous.contextMoved && previous.contextSessionId == session.id && session.activeProjectId == previous.targetId) session.activeProjectId = previous.sourceId
                result
            },
        )
    } ?: emptyList()
}

private class TransferTool(name: String, category: ToolCategory, description: String, inputSchema: JsonObject,
    private val action: suspend (JsonObject, AgentSession) -> JsonObject) : BaseTool(name, category, description, inputSchema) {
    override suspend fun execute(input: JsonObject, session: AgentSession): ToolOutcome = try {
        val result = action(input, session)
        ToolOutcome(result, isError = result["blockers"]?.jsonArray?.isNotEmpty() == true)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: CutFailure) { fail("No se pudo abrir un montaje (${error.code}); consulta el recibo y conserva el origen.") { put("ok", false) } }
    catch (error: java.io.IOException) { fail("No se pudo guardar o leer el traslado. Conserva request_id y consulta el recibo antes de reintentarlo.") { put("ok", false) } }
    catch (error: IllegalArgumentException) { fail(error.message ?: "El traslado no se pudo validar.") { put("ok", false) } }
    catch (error: IllegalStateException) { fail(error.message ?: "El traslado no se pudo completar; conserva su request_id para consultar el recibo.") { put("ok", false) } }
}
