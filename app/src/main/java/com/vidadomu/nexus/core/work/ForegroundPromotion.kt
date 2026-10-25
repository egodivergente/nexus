package com.vidadomu.nexus.core.work

import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import kotlinx.coroutines.CancellationException

/**
 * Asks Android to run this worker as a foreground service, without making the work depend on it.
 * Android 15+ may refuse (dataSync quota exhausted, app in background); the job then continues as normal background work
 * instead of failing. Cancellation is never swallowed.
 */
suspend fun CoroutineWorker.promoteToForeground(info: ForegroundInfo): Boolean = try {
    setForeground(info); true
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.w("NexusWork", "Foreground promotion refused: ${e.javaClass.simpleName}")
    false
}
