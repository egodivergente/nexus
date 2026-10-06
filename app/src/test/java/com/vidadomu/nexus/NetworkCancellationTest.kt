package com.vidadomu.nexus

import com.vidadomu.nexus.core.network.awaitResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test

class NetworkCancellationTest {
    private class PendingCall:Call {
        var wasCancelled=false
        var starts=0
        override fun request()=Request.Builder().url("https://example.invalid/local-fixture").build()
        override fun execute():Response=error("Synchronous transport must not run")
        override fun enqueue(callback:Callback) {starts++}
        override fun cancel() {wasCancelled=true}
        override fun isExecuted()=starts>0
        override fun isCanceled()=wasCancelled
        override fun timeout()=Timeout.NONE
        override fun clone():Call=PendingCall()
    }
    @Test fun cancelledScreenCancelsPendingCallExactlyOnceWithoutRetry() = runTest {
        val call=PendingCall()
        val job=launch(start=CoroutineStart.UNDISPATCHED) {call.awaitResponse()}
        job.cancelAndJoin()
        assertTrue(call.wasCancelled);assertEquals(1,call.starts)
    }
}
