package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.orders.DuplicateSubmission
import com.vidadomu.nexus.agent.core.orders.FailureKind
import com.vidadomu.nexus.agent.core.orders.IllegalOrderTransition
import com.vidadomu.nexus.agent.core.orders.OrderBook
import com.vidadomu.nexus.agent.core.orders.OrderEventKind
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import com.vidadomu.nexus.agent.core.orders.SuggestedFix
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.MediaRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderBookTest {
    private var now = 1_000L
    private fun plan(id: String, prompt: String = "", media: List<String> = listOf("file:///v.mp4"), params: Map<String, String> = emptyMap()) = GenerationPlan(
        id = id, ref = ModelRef(ATLAS, "tencent/video/upscaler"), modelName = "Tencent Video Upscaler", modality = "VIDEO",
        prompt = prompt, params = params, effectiveParams = params, media = media.map { MediaRef("video", it, "video/mp4", null, "video_url") }, createdAt = now,
    )

    @Test fun `estados borrador, pendiente, enviando, procesando y completado`() {
        val book = OrderBook { now++ }
        val draft = book.upsertPlan(plan("p1"), issues = listOf("template: falta"))
        assertEquals(OrderStatus.DRAFT, draft.status)
        assertFailsWith<IllegalOrderTransition> { book.beginSubmit(draft.id, plan("p1").fingerprint()) }
        val ready = book.upsertPlan(plan("p1", params = mapOf("template" to "live-action-enhance-4k")), draft.id)
        assertEquals(draft.id, ready.id)
        assertEquals(OrderStatus.AWAITING_CONFIRMATION, ready.status)
        book.beginSubmit(ready.id, ready.fingerprint)
        assertEquals(OrderStatus.SUBMITTING, book.get(ready.id)!!.status)
        book.accepted(ready.id, "job_1")
        assertEquals(OrderStatus.PROCESSING, book.get(ready.id)!!.status)
        book.jobUpdate(ready.id, "PROCESSING", 0.5f)
        book.jobUpdate(ready.id, "COMPLETED", outputs = listOf("file:///out.mp4"))
        val done = book.get(ready.id)!!
        assertEquals(OrderStatus.COMPLETED, done.status)
        assertEquals(1, book.all().size, "nunca se crea un segundo encargo")
        assertFailsWith<IllegalOrderTransition> { book.upsertPlan(plan("p2"), done.id) }
    }

    @Test fun `una segunda pulsacion no envia dos veces`() {
        val book = OrderBook { now++ }
        val o = book.upsertPlan(plan("p1"))
        book.beginSubmit(o.id, o.fingerprint)
        assertFailsWith<DuplicateSubmission> { book.beginSubmit(o.id, o.fingerprint) }
        assertTrue(book.events().any { it.kind == OrderEventKind.DUPLICATE_BLOCKED })
    }

    @Test fun `un rechazo se corrige en el mismo encargo y cuenta como nuevo intento`() {
        val book = OrderBook { now++ }
        val o = book.upsertPlan(plan("p1"))
        book.beginSubmit(o.id, o.fingerprint)
        book.rejected(o.id, FailureKind.PROVIDER_REJECTED, "resolution is required (720p, 1080p, 1440p or 4k)",
            SuggestedFix("Fijar resolución = 4k", mapOf("resolution" to "4k")), "El proveedor exige resolución.")
        val failed = book.get(o.id)!!
        assertEquals(OrderStatus.FAILED, failed.status)
        assertEquals("resolution is required (720p, 1080p, 1440p or 4k)", failed.lastFailure!!.message)
        val fixed = book.upsertPlan(plan("p1", params = mapOf("template" to "live-action-enhance-4k")), o.id)
        assertEquals(o.id, fixed.id)
        assertEquals(2, fixed.attempt)
        assertEquals(OrderStatus.AWAITING_CONFIRMATION, fixed.status)
        assertEquals(1, fixed.failures.size, "el historial de errores se conserva")
        // El nuevo intento tiene clave propia: se puede confirmar una vez.
        book.beginSubmit(o.id, fixed.fingerprint)
        assertFailsWith<DuplicateSubmission> { book.beginSubmit(o.id, fixed.fingerprint) }
    }

    @Test fun `un envio incierto nunca se reabre ni se reenvia`() {
        val book = OrderBook { now++ }
        val o = book.upsertPlan(plan("p1"))
        book.beginSubmit(o.id, o.fingerprint)
        book.uncertain(o.id, "timeout", "job_9")
        assertFailsWith<IllegalOrderTransition> { book.beginSubmit(o.id, o.fingerprint) }
        assertFailsWith<IllegalOrderTransition> { book.upsertPlan(plan("p2"), o.id) }
        assertFailsWith<IllegalOrderTransition> { book.discard(o.id) }
        // Solo el estado verificado del trabajo lo resuelve.
        book.jobUpdate(o.id, "COMPLETED", outputs = listOf("file:///x.mp4"))
        assertEquals(OrderStatus.COMPLETED, book.get(o.id)!!.status)
    }

    @Test fun `al restaurar, un envio a medias pasa a incierto`() {
        val book = OrderBook { now++ }
        val o = book.upsertPlan(plan("p1"))
        book.beginSubmit(o.id, o.fingerprint)
        val restored = OrderBook { now++ }.apply { restore(book.snapshot()) }
        assertEquals(OrderStatus.UNCERTAIN, restored.get(o.id)!!.status)
    }

    @Test fun `confirmacion caducada vuelve a pendiente y libera la clave`() {
        val book = OrderBook { now++ }
        val o = book.upsertPlan(plan("p1"))
        book.beginSubmit(o.id, o.fingerprint)
        book.backToReview(o.id, "La confirmación ha caducado")
        assertEquals(OrderStatus.AWAITING_CONFIRMATION, book.get(o.id)!!.status)
        book.beginSubmit(o.id, o.fingerprint)
        assertEquals(OrderStatus.SUBMITTING, book.get(o.id)!!.status)
    }

    @Test fun `mismo modelo y mismo video reutilizan el encargo abierto`() {
        val book = OrderBook { now++ }
        val first = book.upsertPlan(plan("p1"))
        assertEquals(first.id, book.findReusable(plan("p2", params = mapOf("template" to "anime-enhance-4k")))?.id)
        assertNull(book.findReusable(plan("p3", media = listOf("file:///otro.mp4"))))
    }

    @Test fun `el agente recibe cada evento una sola vez y no ve los repintados`() {
        val book = OrderBook { now++ }
        val o = book.upsertPlan(plan("p1"))
        book.rendered(o.id, "Pendiente · botón Generar")
        val first = book.drainForAgent()
        assertEquals(listOf(OrderEventKind.CREATED), first.map { it.kind })
        assertTrue(book.drainForAgent().isEmpty())
        assertEquals("Pendiente · botón Generar", book.get(o.id)!!.rendered)
        book.beginSubmit(o.id, o.fingerprint)
        assertNotNull(book.drainForAgent().singleOrNull { it.kind == OrderEventKind.USER_CONFIRMED })
    }
}
