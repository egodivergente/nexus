package com.vidadomu.nexus.assistant

import androidx.annotation.Keep
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Keep
internal object WhisperNative {
    @Volatile private var loaded = false
    init {System.loadLibrary("nexus_whisper"); loaded = true; WhisperCacheLifecycle.trim = { trimCache() }}
    private val cacheTimer = java.util.concurrent.ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "nexus-whisper-cache").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }.apply { removeOnCancelPolicy = true }
    private var pendingRelease: java.util.concurrent.ScheduledFuture<*>? = null
    private val cacheGate = Any()
    private external fun releaseCache()
    fun trimCache(): java.util.concurrent.Future<*>? = if (loaded) cacheTimer.submit { releaseCache() } else null
    private fun releaseAfterIdle() = synchronized(cacheGate) {
        pendingRelease?.cancel(false)
        pendingRelease = cacheTimer.schedule({ releaseCache() }, 60L, java.util.concurrent.TimeUnit.SECONDS)
    }
    external fun createControl():Long
    external fun cancel(control:Long)
    external fun freeControl(control:Long)
    external fun transcribe(modelPath:String,samples:FloatArray,control:Long):ByteArray

    suspend fun run(modelPath:String,samples:FloatArray):ByteArray = suspendCancellableCoroutine {continuation ->
        synchronized(cacheGate) { pendingRelease?.cancel(false) }
        val gate=Any()
        var control=createControl()
        continuation.invokeOnCancellation {synchronized(gate) {if(control!=0L) cancel(control)}}
        Thread({
            try {
                if(continuation.isActive) {
                    val result=transcribe(modelPath,samples,control)
                    if(continuation.isActive) continuation.resume(result)
                }
            } catch(e:Exception) {if(continuation.isActive) continuation.resumeWithException(e)}
            finally {synchronized(gate) {freeControl(control);control=0};releaseAfterIdle()}
        },"nexus-whisper").apply {priority=Thread.MIN_PRIORITY;start()}
    }
}

internal object WhisperCacheLifecycle {
    @Volatile var trim: (() -> Unit)? = null
}
