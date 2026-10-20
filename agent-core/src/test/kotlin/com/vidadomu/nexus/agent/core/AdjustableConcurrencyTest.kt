package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.config.AdjustableConcurrencyGate
import com.vidadomu.nexus.agent.core.config.AgentLimits
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AdjustableConcurrencyTest {
    @Test fun `raising and lowering a limit preserves active work and FIFO order`() = runTest {
        val gate=AdjustableConcurrencyGate(1)
        val release=List(3) {CompletableDeferred<Unit>()}
        val entered=mutableListOf<Int>()
        val jobs=(0..2).map {i->launch {gate.withPermit {entered+=i;release[i].await()}}}
        runCurrent();assertEquals(listOf(0),entered)
        gate.setLimit(2);runCurrent();assertEquals(listOf(0,1),entered)
        gate.setLimit(1);release[0].complete(Unit);runCurrent();assertEquals(listOf(0,1),entered)
        release[1].complete(Unit);runCurrent();assertEquals(listOf(0,1,2),entered)
        release[2].complete(Unit);jobs.joinAll()
    }

    @Test fun `zero lifts the ceiling and cancelled waiters never leak permits`() = runTest {
        val gate=AdjustableConcurrencyGate(1)
        val release=CompletableDeferred<Unit>()
        val first=launch {gate.withPermit {release.await()}}
        runCurrent()
        val cancelled=launch {gate.withPermit {error("Cancelled task must not start")}}
        runCurrent();cancelled.cancelAndJoin()
        var entered=0
        val others=List(8) {launch {gate.withPermit {entered++;release.await()}}}
        runCurrent();assertEquals(0,entered)
        gate.setLimit(0);runCurrent();assertEquals(8,entered)
        release.complete(Unit);first.join();others.joinAll()
        gate.setLimit(1)
        assertFailsWith<IllegalStateException> {gate.withPermit {error("failure")}}
        assertEquals("free",gate.withPermit {"free"})
    }

    @Test fun `counts can exceed former ceilings and zero has no artificial maximum`() {
        val limits=AgentLimits(maxToolResults=500,maxRecommendations=60,maxGenerationsPerBatch=1000,
            maxConcurrentGenerations=100,maxConcurrentFlowGenerations=80,maxConcurrentSubagents=100,maxSubagentDepth=40,flowMaxResults=0)
        assertTrue(limits.problems().isEmpty())
        assertEquals(200,limits.queryLimit(200,20))
        assertEquals(50,limits.boundedCount(50,3,limits.maxRecommendations))
        assertEquals(Int.MAX_VALUE,limits.copy(maxToolResults=0).queryLimit(0,20))
        assertEquals(500,limits.queryLimit(999,20))
        assertTrue(AgentLimits(maxConcurrentGenerations=-1).problems().isNotEmpty())
    }
}
