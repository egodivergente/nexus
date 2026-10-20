package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.llm.*
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BytePlusUsageTest {
    private fun file() = Files.createTempDirectory("byteplus-test").resolve("usage.json").toFile()
    private val request = LlmRequest("fixture-model", emptyList(), emptyList(), emptyList(), 10, turnId="turn")
    @Test fun settlementDeduplicatesAndPersistsAcrossRestart() {
        val file = file(); val ledger = BytePlusUsageLedger(file)
        val ticket = ledger.reserve("model", "turn", 1.0)
        ledger.settle(ticket, Usage(1000, 200), 1.0 to 2.0)
        ledger.settle(ticket, Usage(99999, 9999), 1.0 to 2.0)
        val restored = BytePlusUsageLedger(file)
        val total = restored.totals(restored.snapshot().entries)
        assertEquals(1, total.calls); assertEquals(1000L, total.input); assertEquals(200L, total.output)
        assertEquals(.0014, total.usd!!, .0000001)
    }
    @Test fun reservationsBlockBeforeBackendAndUnknownRatesFailClosed() = runTest {
        val ledger = BytePlusUsageLedger(file())
        ledger.settings(BytePlusUsageLimits(enabled=true, dayUsd=.000001))
        var called=false
        val provider = trackedBytePlus(object:LlmProvider {
            override val id="fixture"
            override suspend fun complete(request:LlmRequest):LlmResponse { called=true; error("must not call") }
        }, ledger, 1.0 to 2.0)
        assertFailsWith<LlmException> { provider.complete(request) }; assertFalse(called)
        assertFailsWith<LlmException> { ledger.reserve("model", "turn", null) }
    }
    @Test fun unknownUsageIsNotInventedAndUncertainReservationsStillCount() {
        val file=file(); val ledger=BytePlusUsageLedger(file)
        ledger.settings(BytePlusUsageLimits(enabled=true, dayUsd=1.0))
        val ticket=ledger.reserve("model", "t", .8)
        ledger.failed(ticket, false)
        assertFailsWith<LlmException> { BytePlusUsageLedger(file).reserve("model", "next", .3) }
        val total=ledger.totals(ledger.snapshot().entries)
        assertNull(total.input); assertNull(total.usd); assertEquals(1,total.uncertain)
    }
    @Test fun noRatesAndMissingUsageNeverBecomeZeroCost() {
        val ledger=BytePlusUsageLedger(file())
        val ticket=ledger.reserve("model", "t", null)
        ledger.settle(ticket, Usage(0,0), null)
        val total=ledger.totals(ledger.snapshot().entries)
        assertNull(total.input); assertNull(total.output); assertNull(total.usd)
    }
    @Test fun callCapAndRejectedCallsAndMonthBoundary() {
        var day="2026-10-31";val ledger=BytePlusUsageLedger(file()) {day}
        ledger.settings(BytePlusUsageLimits(enabled=true, monthUsd=1.0, dayCalls=1))
        val rejected=ledger.reserve("model","turn",.8); ledger.failed(rejected,true)
        val ticket=ledger.reserve("model","turn",.8)
        assertFailsWith<LlmException> {ledger.reserve("model","new",.1)}
        day="2026-11-01"
        ledger.reserve("model","new",.8)
        assertEquals(2,ledger.totals(ledger.snapshot().entries).calls)
        assertNotEquals(rejected,ticket)
    }
    @Test fun concurrentReservationsRespectDayCap() = runTest {
        val ledger=BytePlusUsageLedger(file());ledger.settings(BytePlusUsageLimits(enabled=true,dayCalls=2))
        val results=(1..12).map { async(Dispatchers.Default) {runCatching {ledger.reserve("m","t",null)}.isSuccess} }.awaitAll()
        assertEquals(2,results.count {it})
    }
    @Test fun corruptLedgerNeverResetsLimits() {
        for (corrupt in listOf("broken", "{}")) {
            val file=file();file.writeText(corrupt)
            assertFailsWith<LlmException> {BytePlusUsageLedger(file).reserve("m","t",null)}
            assertEquals(corrupt,file.readText())
        }
    }
    @Test fun streamingIsPreservedAndCallbackFollowsSettlement() = runTest {
        val ledger=BytePlusUsageLedger(file());var notified=0;var delta=""
        val backend=object:StreamingLlmProvider {
            override val id="fixture"
            override suspend fun complete(request:LlmRequest)=LlmResponse(LlmMessage(Role.ASSISTANT, emptyList()),StopReason.END_TURN,Usage(10,2))
            override suspend fun completeStreaming(request:LlmRequest,onTextDelta:(String)->Unit):LlmResponse {onTextDelta("ok");return complete(request)}
        }
        val provider=trackedBytePlus(backend,ledger,1.0 to 2.0) {notified++}
        assertTrue(provider is StreamingLlmProvider)
        (provider as StreamingLlmProvider).completeStreaming(request) {delta+=it}
        assertEquals("ok",delta);assertEquals(1,notified);assertEquals(1,ledger.snapshot().entries.size)
    }
}
