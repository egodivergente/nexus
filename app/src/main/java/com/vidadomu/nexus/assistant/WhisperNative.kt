package com.vidadomu.nexus.assistant

import androidx.annotation.Keep
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Keep
internal object WhisperNative {
    init {System.loadLibrary("nexus_whisper")}
    external fun createControl():Long
    external fun cancel(control:Long)
    external fun freeControl(control:Long)
    external fun transcribe(modelPath:String,samples:FloatArray,control:Long):ByteArray

    suspend fun run(modelPath:String,samples:FloatArray):ByteArray = suspendCancellableCoroutine {continuation ->
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
            finally {synchronized(gate) {freeControl(control);control=0}}
        },"nexus-whisper").apply {priority=Thread.MIN_PRIORITY;start()}
    }
}
