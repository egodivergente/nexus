package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.Timeline
import com.vidadomu.corte.model.TrackRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentOpsTest {
    private val fx = Fx().apply { media("long", 100) }
    private fun ops(json: String) = Json.parseToJsonElement(json).jsonArray

    private fun linked() = Timeline(
        videoTracks = listOf(vtrack("V1", clip("v", 0, 20, link = "L"))),
        audioTracks = listOf(atrack("A1", TrackRole.DIALOGUE, aclip("a", 0, 20, link = "L"))),
    )

    @Test fun `quitar tramos corta y cierra los huecos en video y audio vinculados de una sola vez`() {
        val base = linked()
        val r = AgentOps.apply(base, fx.ctx, ops("""[{"op":"delete_ranges","ranges":[[${s(2)},${s(4)}],[${s(10)},${s(11)}]]}]""")).ok()
        assertValid(r.timeline, fx)
        assertEquals(s(17), r.timeline.durationUs)
        assertEquals(s(17), r.timeline.track("A1")!!.endUs)
        val h = History().apply { record(r) }
        val (undone, _) = h.undo(r.timeline)!!
        assertEquals(base, undone)
    }

    @Test fun `varias operaciones quedan en un solo paso del historial`() {
        val base = linked()
        val r = AgentOps.apply(base, fx.ctx, ops("""[{"op":"split","at_us":${s(5)}},{"op":"add_marker","at_us":${s(5)},"label":"corte"}]"""), "Agente: dividir").ok()
        assertEquals("Agente: dividir", r.label)
        assertEquals(1, r.timeline.markers.size)
        assertEquals(2, r.timeline.track("V1")!!.clips.size)
        val h = History().apply { record(r) }
        assertEquals(base, h.undo(r.timeline)!!.first)
    }

    @Test fun `el foco va al primer punto editado y no al final por el ripple`() {
        val r = AgentOps.apply(linked(), fx.ctx, ops("""[{"op":"delete_ranges","ranges":[[${s(3)},${s(4)}],[${s(12)},${s(13)}]]}]""")).ok()
        assertEquals(s(3), AgentFocus.timeOf(r.changes, r.timeline))
        val m = AgentOps.apply(linked(), fx.ctx, ops("""[{"op":"add_marker","at_us":${s(180)},"label":"x"}]""")).ok()
        assertEquals(s(180), AgentFocus.timeOf(m.changes, m.timeline))
    }

    @Test fun `si una operacion falla no cambia nada y se dice cual y por que`() {
        val r = AgentOps.apply(linked(), fx.ctx, ops("""[{"op":"split","at_us":${s(5)}},{"op":"delete","clip_ids":["no-existe"]},{"op":"volar"}]""")).rejected()
        assertTrue(r.reason, r.reason.startsWith("Operación 2"))
        val u = AgentOps.apply(linked(), fx.ctx, ops("""[{"op":"volar"}]""")).rejected()
        assertTrue(u.reason, "operación desconocida" in u.reason)
        val p = AgentOps.apply(linked(), fx.ctx, ops("""[{"op":"trim","edge":"tail","delta_us":-1}]""")).rejected()
        assertTrue(p.reason, "clip_id" in p.reason)
    }
}
