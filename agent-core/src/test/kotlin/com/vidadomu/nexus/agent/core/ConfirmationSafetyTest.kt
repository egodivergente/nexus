package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.agent.GenerationLifecycle
import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.GuardedExecutor
import com.vidadomu.nexus.agent.core.spend.PlanState
import com.vidadomu.nexus.agent.core.spend.SpendGuard
import com.vidadomu.nexus.agent.core.spend.SpendViolation
import com.vidadomu.nexus.agent.core.spend.SubmissionUncertain
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Hallazgos de la revisión independiente (02-10): cada uno fijado con una prueba. */
class ConfirmationSafetyTest {
    private val guard = SpendGuard()
    private val backend = FakeBackend()
    private val exec = GuardedExecutor(backend, guard)

    private fun plan(prompt: String = "dolly lento", duration: String = "8") = GenerationPlan(
        "p1", KLING_PRO, "Kling", "VIDEO", prompt = prompt, params = mapOf("duration" to duration),
        effectiveParams = mapOf("duration" to duration), createdAt = 0,
    )

    private fun lifecycle(index: AssetIndex = AssetIndex(null)) = GenerationLifecycle(guard, exec, index)

    @Test fun `si el plan cambia con el dialogo abierto no se paga`() = runTest {
        val shown = guard.register(plan()).fingerprint()
        guard.replace(plan(duration = "15"))          // p. ej. update_plan del LLM mientras confirmas
        assertFailsWith<SpendViolation> { lifecycle().confirmAndSubmit("p1", shown) }
        assertTrue(backend.submitted.isEmpty())
        // Con la huella de lo que ahora se ve, sí.
        lifecycle().confirmAndSubmit("p1", guard.plan("p1")!!.fingerprint())
        assertEquals("15", backend.submitted.single().params["duration"])
    }

    @Test fun `envio incierto bloquea el plan para no pagar dos veces`() = runTest {
        val fp = guard.register(plan()).fingerprint()
        backend.failNext = SubmissionUncertain("timeout leyendo la respuesta", "job_x")
        assertFailsWith<SubmissionUncertain> { lifecycle().confirmAndSubmit("p1", fp) }
        assertEquals(PlanState.UNCERTAIN, guard.state("p1"))
        assertFailsWith<SpendViolation> { lifecycle().confirmAndSubmit("p1", fp) }
        assertFailsWith<SpendViolation> { guard.replace(plan(prompt = "otro")) }
        guard.discard("p1")
        assertEquals(PlanState.UNCERTAIN, guard.state("p1"))
        assertTrue(backend.submitted.isEmpty())
    }

    @Test fun `fallo claro antes de enviar permite volver a pulsar`() = runTest {
        val fp = guard.register(plan()).fingerprint()
        backend.failNext = IllegalStateException("subida de medios falló")
        assertFailsWith<IllegalStateException> { lifecycle().confirmAndSubmit("p1", fp) }
        assertEquals(PlanState.PREPARED, guard.state("p1"))
        lifecycle().confirmAndSubmit("p1", fp)
        assertEquals(1, backend.submitted.size)
    }

    @Test fun `un plan descartado o enviado no se puede resucitar`() = runTest {
        guard.register(plan())
        guard.discard("p1")
        assertFailsWith<SpendViolation> { guard.replace(plan(prompt = "revivir")) }
        assertEquals(PlanState.DISCARDED, guard.state("p1"))

        val g2 = SpendGuard(); val e2 = GuardedExecutor(backend, g2)
        val fp = g2.register(plan()).fingerprint()
        GenerationLifecycle(g2, e2, AssetIndex(null)).confirmAndSubmit("p1", fp)
        assertFailsWith<SpendViolation> { g2.replace(plan(prompt = "otra vez")) }
        assertEquals(PlanState.SUBMITTED, g2.state("p1"))
    }

    @Test fun `si falla el indice tras enviar no se dice que no se envio`() = runTest {
        val dir = Files.createTempDirectory("nexus-idx").toFile()
        val blocker = File(dir, "no-es-carpeta").apply { writeText("x") }
        val broken = AssetIndex(File(blocker, "index.json"))   // el padre es un archivo: escribir falla
        val fp = guard.register(plan()).fingerprint()
        val r = lifecycle(broken).confirmAndSubmit("p1", fp)
        assertEquals(1, backend.submitted.size)
        assertEquals(PlanState.SUBMITTED, guard.state("p1"))
        assertTrue(r.note.orEmpty().contains("lineage"), r.note)
        dir.deleteRecursively()
    }

    @Test fun `ticket caducado o con huella vieja no se quema en falso`() = runTest {
        var now = 0L
        val g = SpendGuard(clock = { now }, ticketTtlMs = 1_000)
        val e = GuardedExecutor(backend, g)
        g.register(plan())
        val t = g.confirmByUser("p1")
        now = 5_000
        assertFailsWith<SpendViolation> { e.submit("p1", t) }
        assertEquals(PlanState.PREPARED, g.state("p1"))   // vuelve a estar listo para otra pulsación
        e.submit("p1", g.confirmByUser("p1"))
        assertEquals(1, backend.submitted.size)
    }
}
