package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.orders.FailureKind
import com.vidadomu.nexus.agent.core.orders.Order
import com.vidadomu.nexus.agent.core.orders.OrderBook
import com.vidadomu.nexus.agent.core.orders.OrderFailure
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import com.vidadomu.nexus.agent.core.orders.SuggestedFix
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.MediaRef
import com.vidadomu.nexus.agent.core.spend.PlanState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderCardLogicTest {
    private val ref = ModelRef("atlas_cloud", "tencent/video/upscaler")
    private fun plan(id: String, video: String = "file:///clip.mp4", template: String = "live-action-enhance-4k") = GenerationPlan(
        id = id, ref = ref, modelName = "Tencent Video Upscaler", modality = "VIDEO", prompt = "",
        params = mapOf("template" to template), effectiveParams = mapOf("template" to template, "resolution" to "4k"),
        media = listOf(MediaRef("video", video, "video/mp4", null, "video_url")), createdAt = 1,
    )
    private fun order(status: OrderStatus, failure: OrderFailure? = null, jobId: String? = null) = Order(
        id = "ord_1", planId = "p1", status = status, modelName = "Tencent Video Upscaler", modelRef = ref.toString(), modality = "VIDEO",
        jobId = jobId, failures = listOfNotNull(failure), createdAt = 0, updatedAt = 0,
    )
    private fun fail(kind: FailureKind, message: String, fix: SuggestedFix? = null) = OrderFailure(kind, message, 0, "p1", 1, fix, "Atlas exige resolution.")

    @Test fun headerNeverInventsProgressOrShowsFlowMilestones() {
        val processing = order(OrderStatus.PROCESSING)
        for (value in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, -0.1f, 1.1f)) {
            assertEquals("Procesando", presentOrder(processing.copy(progress = value), null, false, false, false).status)
        }
        assertEquals("Procesando", presentOrder(processing.copy(modelRef = "google_flow:veo", progress = .5f), null, false, false, false).status)
        assertEquals("Procesando · 50 %", presentOrder(processing.copy(progress = .5f), null, false, false, false).status)
    }

    @Test fun everyStateShowsAtMostOneActionAndHonestLabels() {
        val awaiting = presentOrder(order(OrderStatus.AWAITING_CONFIRMATION), "1,00 €", busy = false, assistantBusy = false, dirty = false)
        assertEquals(CardAction.GENERATE, awaiting.action)
        assertEquals("Generar · 1,00 €", awaiting.actionLabel)
        assertTrue(awaiting.enabled)
        assertFalse(presentOrder(order(OrderStatus.AWAITING_CONFIRMATION), null, busy = false, assistantBusy = true, dirty = false).enabled)
        assertFalse(presentOrder(order(OrderStatus.AWAITING_CONFIRMATION), null, busy = true, assistantBusy = false, dirty = false).enabled)

        val sending = presentOrder(order(OrderStatus.SUBMITTING), null, busy = true, assistantBusy = false, dirty = false)
        assertEquals(CardAction.SENDING, sending.action); assertFalse("bloquea pulsaciones repetidas", sending.enabled)
        assertNull(presentOrder(order(OrderStatus.PROCESSING), null, false, false, false).actionLabel)
        assertEquals(CardAction.VIEW_RESULT, presentOrder(order(OrderStatus.COMPLETED), null, false, false, false).action)

        val uncertain = presentOrder(order(OrderStatus.UNCERTAIN, jobId = "job_1"), null, false, false, false)
        assertEquals(CardAction.CHECK_HISTORY, uncertain.action)
        assertEquals("Abrir Historial", uncertain.actionLabel)
        assertTrue(uncertain.headline!!.contains("no lo repetirá"))
        assertTrue("el aviso que menciona Historial lleva su acceso aunque no haya job", presentOrder(order(OrderStatus.UNCERTAIN), null, false, false, false).action == CardAction.CHECK_HISTORY)
    }

    @Test fun failureShowsTheLiteralErrorAndTheConcreteFix() {
        val withFix = presentOrder(order(OrderStatus.FAILED, fail(FailureKind.PROVIDER_REJECTED, "resolution is required (720p, 1080p, 1440p or 4k)",
            SuggestedFix("Fijar Resolución = 4k", mapOf("resolution" to "4k")))), null, false, false, false)
        assertEquals(CardAction.APPLY_FIX, withFix.action)
        assertEquals("Error", withFix.status)
        assertTrue(withFix.detail!!.startsWith("resolution is required"))
        assertTrue("dice si se cobró", withFix.headline!!.contains("No se ha cobrado"))
        assertEquals(CardAction.RETRY, presentOrder(order(OrderStatus.FAILED, fail(FailureKind.PROVIDER_REJECTED, "HTTP 503 unavailable")), null, false, false, false).action)
        val jobFailed = presentOrder(order(OrderStatus.FAILED, fail(FailureKind.JOB_FAILED, "content policy")), null, false, false, false)
        assertEquals(CardAction.ASK_AGENT, jobFailed.action)
        assertTrue(jobFailed.headline!!.contains("decide si cobra"))
        assertTrue(withFix.rendered.startsWith("Error · botón «Aplicar corrección»"))
    }

    @Test fun restorePreservesDifferentInstructionsAndParametersForSameMedia() {
        val plans = listOf(plan("a"), plan("b").copy(prompt = "Different instructions"),
            plan("c", template = "anime-enhance-4k"))
        val saved = SavedAssistantChat(plans.map { ChatItem.PlanCard("card-${it.id}", it.id) },
            plans.associate { it.id to PlanUi(it, true, PlanState.PREPARED) })
        val restored = ChatRestore.clean(saved)
        assertEquals(3, restored.items.size)
        assertTrue(restored.discardedPlanIds.isEmpty())
    }

    @Test fun restoreRemovesDiscardedAndOrphanCardsWithoutTouchingMessages() {
        val saved = SavedAssistantChat(listOf(ChatItem.PlanCard("gone", "missing"),
            ChatItem.PlanCard("discarded", "a"), ChatItem.User("user", "Keep\nhttps://example.org", emptyList())),
            mapOf("a" to PlanUi(plan("a"), false, PlanState.DISCARDED)))
        val restored = ChatRestore.clean(saved)
        assertEquals(listOf(saved.items.last()), restored.items)
        assertEquals(restored.items, ChatRestore.clean(SavedAssistantChat(restored.items, restored.plans, orders = restored.orders)).items)
    }

    @Test fun savedChatWithTriplicatedCardsShowsOneCardPerOrder() {
        val p1 = PlanUi(plan("p1"), true, PlanState.PREPARED)
        val p2 = PlanUi(plan("p2"), true, PlanState.AWAITING_CONFIRMATION)
        val done = PlanUi(plan("p3", video = "file:///otro.mp4"), false, PlanState.SUBMITTED, jobId = "job_9", jobStatus = "COMPLETED")
        val saved = SavedAssistantChat(
            items = listOf(
                ChatItem.User("u1", "Haz upscale de este vídeo a 4K con Tencent", emptyList()),
                ChatItem.PlanCard("c1", "p1"), ChatItem.PlanCard("c2", "p1"), ChatItem.PlanCard("c3", "p1"),
                ChatItem.Assistant("a1", "Listo", emptyList(), "END_TURN"),
                ChatItem.PlanCard("c4", "p2"),
                ChatItem.PlanCard("c5", "p3"), ChatItem.PlanCard("c6", "p3"),
            ),
            plans = mapOf("p1" to p1, "p2" to p2, "p3" to done),
        )
        val r = ChatRestore.clean(saved, now = 5)
        val cards = r.items.filterIsInstance<ChatItem.PlanCard>()
        assertEquals("p1 y p2 eran el mismo encargo (mismo modelo y vídeo); p3 otro", listOf("p2", "p3"), cards.map { it.planId })
        assertEquals(setOf("p1"), r.discardedPlanIds)
        assertTrue(cards.all { it.orderId != null })
        assertEquals(2, r.orders.orders.size)
        assertEquals(OrderStatus.AWAITING_CONFIRMATION, r.orders.orders.first { it.planId == "p2" }.status)
        assertEquals(OrderStatus.COMPLETED, r.orders.orders.first { it.planId == "p3" }.status)
        assertEquals(setOf("p2", "p3"), r.plans.keys)
        // Una segunda limpieza es estable: no crea encargos nuevos.
        val again = ChatRestore.clean(SavedAssistantChat(r.items, r.plans, orders = r.orders), now = 6)
        assertEquals(r.orders.orders.map { it.id }.toSet(), again.orders.orders.map { it.id }.toSet())
        assertEquals(cards, again.items.filterIsInstance<ChatItem.PlanCard>())
    }

    /** Datos reales del S25 (2026-10-16): tres envíos de Tencent fallidos del mismo vídeo + avisos «Enviado…». */
    @Test fun repeatedFailedSubmissionsOfTheSameVideoBecomeOneOrderWithAttempts() {
        val error = "generate task failed, tencent-upscaler: resolution is required (720p, 1080p, 1440p or 4k)"
        fun failed(id: String) = PlanUi(plan(id).copy(prompt = "Upscale to 4K"), false, PlanState.SUBMITTED, jobId = "job_$id", jobStatus = "FAILED", error = error)
        val completed = PlanUi(plan("img", video = "file:///foto.png").copy(ref = ModelRef("atlas_cloud", "tencent/image/upscaler")), false, PlanState.SUBMITTED, jobId = "job_img", jobStatus = "COMPLETED")
        val saved = SavedAssistantChat(
            items = listOf(
                ChatItem.PlanCard("c0", "img"),
                ChatItem.PlanCard("c1", "a"), ChatItem.Notice("n1", "Enviado a Tencent Video Upscaler. Lo verás en Historial; aquí se actualiza el estado."),
                ChatItem.PlanCard("c2", "b"), ChatItem.Notice("n2", "Enviado a Tencent Video Upscaler. Lo verás en Historial; aquí se actualiza el estado."),
                ChatItem.PlanCard("c3", "c"), ChatItem.Notice("n3", "Error real", error = true),
            ),
            plans = mapOf("img" to completed, "a" to failed("a"), "b" to failed("b"), "c" to failed("c")),
        )
        val r = ChatRestore.clean(saved, now = 9)
        assertEquals(listOf("img", "c"), r.items.filterIsInstance<ChatItem.PlanCard>().map { it.planId })
        assertTrue("los envíos fallidos conservan su job: no se anulan", r.discardedPlanIds.isEmpty())
        assertFalse(r.items.any { it is ChatItem.Notice && it.text.startsWith("Enviado a") })
        assertTrue("los errores reales se conservan", r.items.any { it is ChatItem.Notice && it.error })
        val tencent = r.orders.orders.single { it.planId == "c" }
        assertEquals(OrderStatus.FAILED, tencent.status)
        assertEquals(3, tencent.attempt)
        assertEquals(listOf(1, 2, 3), tencent.failures.map { it.attempt })
        assertEquals(listOf("a", "b"), tencent.previousPlanIds)
        assertTrue(tencent.lastFailure!!.message.contains("resolution is required"))
        // El chat ya migrado una vez (dos encargos guardados) también se agrupa al reabrirlo.
        val half = ChatRestore.clean(saved.copy(items = saved.items.filterNot { it is ChatItem.PlanCard && it.planId == "c" }), now = 9)
        val reopened = ChatRestore.clean(SavedAssistantChat(half.items + ChatItem.PlanCard("c3", "c"), saved.plans, orders = half.orders), now = 10)
        assertEquals(listOf("img", "c"), reopened.items.filterIsInstance<ChatItem.PlanCard>().map { it.planId })
        assertEquals(3, reopened.orders.orders.single { it.planId == "c" }.failures.size)
    }

    @Test fun legacyFailedCardBecomesAFailedOrderWithItsError() {
        val failed = PlanUi(plan("p1"), true, PlanState.PREPARED, error = "No se envió: resolution is required (720p, 1080p, 1440p or 4k)")
        val r = ChatRestore.clean(SavedAssistantChat(listOf(ChatItem.PlanCard("c1", "p1")), mapOf("p1" to failed)), now = 1)
        val o = r.orders.orders.single()
        assertEquals(OrderStatus.FAILED, o.status)
        assertTrue(o.lastFailure!!.message.contains("resolution is required"))
    }

    @Test fun ordersSurviveSerializationAndOldChatsStillLoad() {
        val json = Json { ignoreUnknownKeys = true }
        val old = """{"items":[{"type":"com.vidadomu.nexus.assistant.ChatItem.PlanCard","id":"c1","planId":"p1"}],"plans":{}}"""
        val decoded = json.decodeFromString<SavedAssistantChat>(old)
        assertNull((decoded.items.single() as ChatItem.PlanCard).orderId)
        assertNull(decoded.orders)
        val book = OrderBook { 1 }
        val o = book.upsertPlan(plan("p1"))
        val chat = SavedAssistantChat(listOf(ChatItem.PlanCard("c1", "p1", o.id)), mapOf("p1" to PlanUi(plan("p1"), true, PlanState.PREPARED)), orders = book.snapshot())
        val back = json.decodeFromString<SavedAssistantChat>(json.encodeToString(chat))
        assertEquals(o.id, back.orders!!.orders.single().id)
        assertEquals(o.id, (back.items.single() as ChatItem.PlanCard).orderId)
    }
}
