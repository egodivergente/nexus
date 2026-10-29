package com.vidadomu.nexus.ui.models

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps one completed catalog snapshot across screen visits without retaining a history of catalogs. */
internal class CompletedSnapshotWarmup<T>(private val load: suspend (T) -> Unit) {
    private val mutex = Mutex()
    private var completed: List<T>? = null

    suspend fun warm(snapshot: List<T>) = mutex.withLock {
        currentCoroutineContext().ensureActive()
        if (completed == snapshot) return@withLock
        for (item in snapshot) {
            currentCoroutineContext().ensureActive()
            load(item)
        }
        // A canceled or failed pass may have filled some cache entries, but must remain retryable.
        currentCoroutineContext().ensureActive()
        completed = snapshot.toList()
    }
}
