package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.llm.*
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.test.*

class LlmBudgetTest {
    private fun file() = Files.createTempDirectory("nexus-budget").resolve("usage.json").toFile()
    private val req = LlmRequest("us.anthropic.claude-sonnet-4-6",emptyList(),listOf(LlmMessage(Role.USER,listOf(ContentBlock.Text("hola")))),emptyList(),10,turnId="turn")
    @Test fun reservationsSurviveRestartAndMidnightDoesNotResetTurn() {
        val f=file();var day="2026-10-11"
        val ledger=LlmBudgetLedger(f) {day}
        ledger.reserve("t",0.8,100,LlmLimits())
        assertFailsWith<LlmException> {LlmBudgetLedger(f) {day}.reserve("t",0.3,100,LlmLimits())}
        day="2026-10-12"
        assertFailsWith<LlmException> {ledger.reserve("t",0.3,100,LlmLimits())}
        ledger.reserve("new",0.3,100,LlmLimits())
    }
    @Test fun concurrentReservationsCannotOverspend() = runTest {
        val ledger=LlmBudgetLedger(file())
        val admitted=(1..10).map {async {runCatching {ledger.reserve("turn",0.3,100,LlmLimits())}.isSuccess}}.awaitAll().count {it}
        assertEquals(3,admitted)
    }
    @Test fun lostResponseRetainsReservationAndBlocksNextPaidCall() = runTest {
        var calls=0
        val backend=object:LlmProvider {
            override val id="fake"
            override suspend fun countInputTokens(request:LlmRequest)=100
            override suspend fun complete(request:LlmRequest):LlmResponse {calls++;throw LlmException(LlmException.Kind.NETWORK,"lost")}
        }
        val ledger=LlmBudgetLedger(file())
        val provider=BudgetedLlmProvider(backend,ledger) {LlmLimits(perTurnCalls=1)}
        assertFailsWith<LlmException> {provider.complete(req)}
        assertTrue(ledger.spentToday()>0)
        assertEquals(LlmException.Kind.BUDGET,assertFailsWith<LlmException> {provider.complete(req)}.kind)
        assertEquals(1,calls)
    }
    @Test fun settlementUsesUsageAndCorruptLedgerDoesNotResetBudget() = runTest {
        val f=file();val ledger=LlmBudgetLedger(f)
        val ticket=ledger.reserve("t",0.8,100,LlmLimits())
        ledger.settle(ticket,0.2,20)
        assertEquals(0.2,ledger.spentToday())
        f.writeText("corrupt")
        assertFailsWith<LlmException> {ledger.reserve("new",0.1,10,LlmLimits())}
    }
}
