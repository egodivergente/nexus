package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.orders.Order
import com.vidadomu.nexus.agent.core.orders.OrderBookState
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.agent.core.spend.PlanState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAutosaveTest {
    private val ref = ModelRef("atlas_cloud", "kling/video")
    private val plan = GenerationPlan(id = "p1", ref = ref, modelName = "Kling", modality = "VIDEO", prompt = "faro",
        params = emptyMap(), effectiveParams = emptyMap(), createdAt = 1)
    private val order = Order(id = "o1", planId = "p1", status = OrderStatus.AWAITING_CONFIRMATION, modelName = "Kling",
        modelRef = ref.toString(), modality = "VIDEO", createdAt = 0, updatedAt = 0)
    private val user = ChatItem.User("u1", "Haz el plano del faro", emptyList(), 1)

    private fun chat(items: List<ChatItem>, plan: PlanUi? = null, order: Order? = null) =
        SavedAssistantChat(items, listOfNotNull(plan).associateBy { it.plan.id }, orders = order?.let { OrderBookState(listOf(it), seq = 1) },
            projectId = "general", conversationId = "c1")

    /** Today: every UI state whose snapshot differs from the last one written goes to disk at once. */
    private fun before(events: List<Pair<Long, SavedAssistantChat>>): List<SavedAssistantChat> {
        val writes = mutableListOf<SavedAssistantChat>()
        events.forEach { (_, s) -> if (s != writes.lastOrNull()) writes += s }
        return writes
    }

    /** Same as [AssistantChatStore.autosave] under collectLatest: a newer state replaces the one still waiting. */
    private fun after(events: List<Pair<Long, SavedAssistantChat>>): List<SavedAssistantChat> {
        val policy = ChatAutosave(); val writes = mutableListOf<SavedAssistantChat>()
        fun write(s: SavedAssistantChat, at: Long) { if (s != policy.saved) { writes += s; policy.written(s, at) } }
        var pending: Pair<Long, SavedAssistantChat>? = null
        events.forEach { (t, s) ->
            pending?.let { (due, p) -> if (due <= t) write(p, due) }
            pending = null
            val wait = policy.waitMs(s, t)
            if (wait == 0L) write(s, t) else pending = t + wait to s
        }
        pending?.let { (due, p) -> write(p, due) }
        return writes
    }

    @Test fun aStreamedTurnWritesOnlyTheMessagesBeforeAndAfter() {
        val asked = chat(listOf(user))
        // Fragments, steps and «Pensando…» live outside the saved snapshot: they leave it equal to the last one.
        val events = listOf(0L to asked) + (1..50).map { it * 100L to asked } +
            listOf(5_100L to chat(listOf(user, ChatItem.Assistant("a1", "Hecho.", emptyList(), "END_TURN"))))
        assertEquals(2, before(events).size)
        assertEquals(before(events), after(events))
    }

    @Test fun progressTicksWaitButEveryStateChangeIsWrittenAndTheLastValueReachesDisk() {
        val card = listOf(user, ChatItem.PlanCard("k1", "p1", "o1"))
        val ready = PlanUi(plan, true, PlanState.AWAITING_CONFIRMATION)
        val sent = ready.copy(state = PlanState.SUBMITTED, jobId = "job1", jobStatus = "PENDING")
        val running = order.copy(status = OrderStatus.PROCESSING, jobId = "job1")
        val events = mutableListOf(0L to chat(card, ready, order), 200L to chat(card, ready.copy(busy = true, submitting = true), order),
            400L to chat(card, sent, running))
        var planUi = sent; var book = running
        // 30 s of a job, a tick every 500 ms: the card's percentage and the order's, each its own UI state.
        for (tick in 1..60) {
            val p = tick / 61f
            planUi = planUi.copy(jobStatus = "PROCESSING", jobProgress = p)
            events += 400L + tick * 500 to chat(card, planUi, book)
            book = book.copy(progress = p, updatedAt = tick.toLong())
            events += 400L + tick * 500 + 1 to chat(card, planUi, book)
        }
        val done = chat(card, planUi.copy(jobStatus = "COMPLETED", jobProgress = 1f), book.copy(status = OrderStatus.COMPLETED, progress = 1f))
        events += 31_000L to done

        val old = before(events); val new = after(events)
        assertEquals(done, old.last()); assertEquals(done, new.last())
        // Nothing but a percentage may be skipped: each state (sending, sent with its job, finished) is written.
        events.zipWithNext().filter { (a, b) -> !ChatAutosave.progressOnly(a.second, b.second) }.forEach { (_, b) -> assertTrue(b.second in new) }
        assertEquals(124, old.size)
        assertTrue("${new.size} escrituras", new.size <= 4 + 30_000 / ChatAutosave.PROGRESS_INTERVAL_MS.toInt() + 2)
    }

    @Test fun aMessageNeverWaitsBehindAProgressTick() {
        val policy = ChatAutosave()
        val running = chat(listOf(user, ChatItem.PlanCard("k1", "p1", "o1")), PlanUi(plan, false, PlanState.SUBMITTED, jobId = "job1", jobProgress = 0.1f))
        policy.written(running, 1_000)
        val tick = running.copy(plans = running.plans.mapValues { it.value.copy(jobProgress = 0.2f) })
        assertEquals(2_500L, policy.waitMs(tick, 1_500))
        assertEquals(0L, policy.waitMs(tick.copy(items = tick.items + ChatItem.User("u2", "¿Va bien?", emptyList(), 2)), 1_500))
        assertEquals(0L, policy.waitMs(tick.copy(plans = tick.plans.mapValues { it.value.copy(error = "Falló") }), 1_500))
        assertEquals(0L, policy.waitMs(tick, 4_000))
        assertEquals(0L, ChatAutosave().waitMs(tick, 0))
    }

    @Test fun progressOnlyIgnoresJustThePercentages() {
        val base = chat(listOf(user), PlanUi(plan, false, PlanState.SUBMITTED, jobId = "j"), order.copy(status = OrderStatus.PROCESSING))
        val moved = chat(listOf(user), PlanUi(plan, false, PlanState.SUBMITTED, jobId = "j", jobProgress = 0.5f),
            order.copy(status = OrderStatus.PROCESSING, progress = 0.5f, updatedAt = 9))
        assertTrue(ChatAutosave.progressOnly(base, moved))
        assertFalse(ChatAutosave.progressOnly(base, chat(listOf(user), PlanUi(plan, false, PlanState.SUBMITTED, jobId = "j"), order.copy(status = OrderStatus.COMPLETED))))
        assertFalse(ChatAutosave.progressOnly(base, base.copy(orders = base.orders!!.copy(seq = 2))))
    }

    @Test fun theConversationIndexIsRewrittenOnlyWhenItsListChangesOrTooManyEntriesWait() {
        assertFalse(ConversationIndexPolicy.persist(firstInProcess = true, structural = false, unsaved = 0))
        assertTrue(ConversationIndexPolicy.persist(firstInProcess = false, structural = true, unsaved = 0))
        assertTrue(ConversationIndexPolicy.persist(firstInProcess = true, structural = false, unsaved = 1))
        assertFalse(ConversationIndexPolicy.persist(firstInProcess = false, structural = false, unsaved = ConversationIndexPolicy.MAX_UNSAVED - 1))
        assertTrue(ConversationIndexPolicy.persist(firstInProcess = false, structural = false, unsaved = ConversationIndexPolicy.MAX_UNSAVED))
    }
}
