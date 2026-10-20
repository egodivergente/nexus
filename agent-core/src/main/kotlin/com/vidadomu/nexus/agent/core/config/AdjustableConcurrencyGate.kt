package com.vidadomu.nexus.agent.core.config

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

/** FIFO permits. A reduced limit never cancels accepted work; zero removes the local ceiling. */
class AdjustableConcurrencyGate(initialLimit: Int) {
    private val mutex = Mutex()
    private var limit = initialLimit.also { require(it >= 0) }
    private var active = 0L
    private val waiting = ArrayDeque<CompletableDeferred<Unit>>()

    suspend fun setLimit(value: Int) {
        require(value >= 0)
        mutex.withLock { limit = value; drain() }
    }

    private fun drain() {
        while (waiting.isNotEmpty() && (limit == 0 || active < limit)) {
            val waiter = waiting.removeFirst()
            active++
            if (!waiter.complete(Unit)) active--
        }
    }

    suspend fun <T> withPermit(block: suspend () -> T): T {
        val waiter = CompletableDeferred<Unit>()
        mutex.withLock { waiting.addLast(waiter); drain() }
        try {
            waiter.await()
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (!waiting.remove(waiter)) active--
                    drain()
                }
            }
            throw error
        }
        try {
            return block()
        } finally {
            withContext(NonCancellable) { mutex.withLock { active--; drain() } }
        }
    }
}
