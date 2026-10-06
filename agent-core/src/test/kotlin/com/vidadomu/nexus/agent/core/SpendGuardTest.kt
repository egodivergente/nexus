package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.GuardedExecutor
import com.vidadomu.nexus.agent.core.spend.PlanState
import com.vidadomu.nexus.agent.core.spend.SpendGuard
import com.vidadomu.nexus.agent.core.spend.SpendViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SpendGuardTest {
    private var now = 1_000L
    private val guard = SpendGuard(clock = { now }, ticketTtlMs = 60_000)
    private val backend = FakeBackend()
    private val exec = GuardedExecutor(backend, guard)

    private fun plan(id: String = "p1", prompt: String = "dolly lento") = GenerationPlan(
        id, KLING_PRO, "Kling", "VIDEO", prompt = prompt, params = mapOf("duration" to "8"), effectiveParams = mapOf("duration" to "8"), createdAt = now,
    )

    @Test fun `pedir confirmacion no ejecuta`() {
        guard.register(plan())
        assertEquals(PlanState.AWAITING_CONFIRMATION, guard.requestConfirmation("p1"))
        assertTrue(backend.submitted.isEmpty())
    }

    @Test fun `un ticket una generacion`() = runTest {
        guard.register(plan())
        val t = guard.confirmByUser("p1")
        exec.submit("p1", t)
        assertEquals(1, backend.submitted.size)
        assertFailsWith<SpendViolation> { exec.submit("p1", t) }
        assertFailsWith<SpendViolation> { guard.confirmByUser("p1") }
        assertEquals(1, backend.submitted.size)
    }

    @Test fun `cambiar el plan invalida la confirmacion`() = runTest {
        guard.register(plan())
        val t = guard.confirmByUser("p1")
        guard.replace(plan(prompt = "dolly rápido"))
        assertFailsWith<SpendViolation> { exec.submit("p1", t) }
        assertTrue(backend.submitted.isEmpty())
    }

    @Test fun `ticket caduca`() = runTest {
        guard.register(plan())
        val t = guard.confirmByUser("p1")
        now += 61_000
        assertFailsWith<SpendViolation> { exec.submit("p1", t) }
        assertTrue(backend.submitted.isEmpty())
    }

    @Test fun `ticket de otro plan no sirve`() = runTest {
        guard.register(plan("a")); guard.register(plan("b"))
        val ta = guard.confirmByUser("a")
        assertFailsWith<SpendViolation> { exec.submit("b", ta) }
        assertTrue(backend.submitted.isEmpty())
    }

    @Test fun `ticket de otra sesion no sirve`() = runTest {
        val other = SpendGuard(clock = { now })
        other.register(plan()); guard.register(plan())
        val foreign = other.confirmByUser("p1")
        assertFailsWith<SpendViolation> { exec.submit("p1", foreign) }
    }

    @Test fun `huella cambia con medios y parametros`() {
        val a = plan()
        assertTrue(a.fingerprint() != a.copy(params = mapOf("duration" to "10")).fingerprint())
        assertTrue(a.fingerprint() != a.copy(ref = ModelRef("crun", a.ref.modelId)).fingerprint())
        assertEquals(a.fingerprint(), a.copy(rationale = "otra explicación").fingerprint())
    }
}
