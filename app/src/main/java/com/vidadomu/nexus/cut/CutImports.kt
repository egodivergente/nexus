package com.vidadomu.nexus.cut

import com.vidadomu.nexus.cut.command.CutFailure
import com.vidadomu.nexus.cut.command.CutSession
import com.vidadomu.nexus.cut.command.CutWorkspace
import com.vidadomu.nexus.cut.command.Op
import com.vidadomu.nexus.cut.command.Origin
import com.vidadomu.nexus.cut.command.Outcome
import com.vidadomu.nexus.cut.command.Request
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Abrir desde otra pantalla solo incorpora archivos nuevos; añadir otra vez es una edición explícita. */
internal class CutImports {
    private val opening = Mutex()

    suspend fun openWith(workspace: CutWorkspace, assetIds: List<String>, montageFor: suspend () -> CutSession): CutSession = opening.withLock {
        // Incluye buscar/crear el montaje: dos pulsaciones no pueden crear sesiones distintas ni duplicar clips.
        val session = montageFor()
        val state = session.state.value
        val known = state.project.media.mapNotNullTo(HashSet()) { it.assetId }
        val additions = assetIds.filter { known.add(it) }
        if (additions.isNotEmpty()) {
            val out = session.execute(Request(
                ops = listOf(Op("media.import", CutSession.argsOf("asset_ids" to additions))),
                origin = Origin.USER,
                label = "Añadir desde la Biblioteca",
                expectedRevision = state.revision,
            ))
            if (out is Outcome.Failed) throw CutFailure(out.error.code, out.error.message)
        }
        workspace.setActive(session.projectId)
        session
    }
}
