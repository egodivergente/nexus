package com.vidadomu.nexus

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.assistant.*
import com.vidadomu.nexus.agent.core.agent.AgentOrchestrator
import com.vidadomu.nexus.agent.core.config.AgentConfig
import com.vidadomu.nexus.agent.core.llm.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android lifecycle/FGS, isolated package and gated local LLM. No provider calls. */
@RunWith(AndroidJUnit4::class)
class AssistantBackgroundTurnTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as NexusApplication
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use {it.readText()}
    @Suppress("DEPRECATION") private fun serviceRunning() =
        app.getSystemService(ActivityManager::class.java).getRunningServices(100)
            .any {it.service.className == AssistantTurnService::class.java.name && it.foreground}
    private suspend fun waitUntil(condition: () -> Boolean) = withTimeout(15_000) {
        while (!condition()) delay(100)
    }
    private fun assertIsolated() {
        check(BuildConfig.OFFLINE_QA && (app.packageName.endsWith(".backgroundqa") || app.packageName.endsWith(".mediaqa")))
    }
    private fun wakeHeld() = shell("dumpsys power").lineSequence()
        .any {it.contains("PARTIAL_WAKE_LOCK") && it.contains("Nexus:AssistantTurn")}

    @Test fun realSendCompletesWithActivityDestroyedAndScreenOff(): Unit = runBlocking {
        assertIsolated()
        var scenario: ActivityScenario<MainActivity>? = ActivityScenario.launch(MainActivity::class.java)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val answer = "Respuesta local terminada en segundo plano ${java.util.UUID.randomUUID()}"
        lateinit var vm: AssistantViewModel
        var vmReady = false
        var phase = "start provider"
        val screenWasOn = app.getSystemService(PowerManager::class.java).isInteractive
        try {
            scenario!!.onActivity {
                vm = app.assistantViewModel()
                vmReady = true
                val fake = object : LlmProvider {
                    override val id = "background-fixture"
                    override suspend fun complete(request: LlmRequest): LlmResponse {
                        calls.incrementAndGet(); started.complete(Unit)
                        release.await()
                        repeat(5) {delay(200)}
                        return LlmResponse(LlmMessage(Role.ASSISTANT, listOf(ContentBlock.Text(answer))), StopReason.END_TURN, Usage(0,0))
                    }
                }
                val orchestrator = AgentOrchestrator(fake, AgentConfig(modelId="fixture",region="test",endpoint="http://unused"),
                    emptyList(), vm.runtime.skills, vm.runtime.assets)
                // Test seam only: leave production settings/keys and provider selection untouched.
                AssistantViewModel::class.java.getDeclaredField("orchestrator").apply {isAccessible=true}.set(vm,orchestrator)
                assertTrue(vm.send("Petición local de prueba de segundo plano"))
            }
            withTimeout(15_000) {started.await()}
            phase = "inspect foreground service"
            assertTrue(serviceRunning()); assertTrue(wakeHeld())
            scenario!!.close(); scenario = null
            phase = "background response"
            shell("input keyevent KEYCODE_SLEEP")
            assertTrue(vm.ui.value.thinking)
            release.complete(Unit)
            waitUntil {!vm.ui.value.thinking}
            phase = "persist response"
            assertTrue(vm.ui.value.items.filterIsInstance<ChatItem.Assistant>().any {it.text==answer})
            // AtomicFile.openRead() may discard an in-progress .new file. Poll the committed
            // base without recovery side effects, then exercise the normal reader once saved.
            val committedChat = java.io.File(app.filesDir,"assistant/chat-v1.json")
            waitUntil {runCatching {committedChat.readText().contains(answer)}.getOrDefault(false)}
            assertTrue(AssistantChatStore(app).load()?.items?.filterIsInstance<ChatItem.Assistant>()?.any {it.text==answer} == true)
            assertEquals(1,calls.get())
            phase = "release service"
            waitUntil {!serviceRunning() && !wakeHeld()}
            if (screenWasOn) shell("input keyevent KEYCODE_WAKEUP")
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario!!.onActivity {assertSame(vm,app.assistantViewModel())}
        } catch (error: TimeoutCancellationException) {
            val notices = if(vmReady) vm.ui.value.items.filterIsInstance<ChatItem.Notice>().joinToString {it.text} else "no view model"
            throw AssertionError("phase=$phase provider_calls=${calls.get()} thinking=${if(vmReady) vm.ui.value.thinking else false} notices=$notices",error)
        } finally {
            release.complete(Unit)
            if (vmReady) instrumentation.runOnMainSync {vm.stop()}
            scenario?.close()
            if (screenWasOn) shell("input keyevent KEYCODE_WAKEUP")
        }
    }

    @Test fun cancellingTheCallerCancelsTheOriginalWorkAndReleasesWakeLock(): Unit = runBlocking {
        assertIsolated()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main)
        val entered = CompletableDeferred<Unit>()
        val ended = CompletableDeferred<Unit>()
        try {
            val job = scope.launch {
                AssistantTurnService.runTurn(app) {
                    try {entered.complete(Unit); awaitCancellation()} finally {ended.complete(Unit)}
                }
            }
            withTimeout(15_000) {entered.await()}
            assertTrue(wakeHeld())
            job.cancelAndJoin()
            withTimeout(15_000) {ended.await()}
            waitUntil {!serviceRunning() && !wakeHeld()}
        } finally {scope.cancel(); scenario.close()}
    }

    @Test fun consecutiveTurnsDoNotStopTheNewRequestOrReplayTheOldOne(): Unit = runBlocking {
        assertIsolated()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val calls = AtomicInteger()
        try {
            repeat(3) {index ->
                val value = withTimeout(15_000) {AssistantTurnService.runTurn(app) {calls.incrementAndGet(); delay(100); index}}
                assertEquals(index,value)
            }
            assertEquals(3,calls.get())
            waitUntil {!serviceRunning() && !wakeHeld()}
        } finally {scenario.close()}
    }

    @Test fun serviceDestructionReturnsAnErrorWithoutReplayingTheRequest(): Unit = runBlocking {
        assertIsolated()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main)
        val entered = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        try {
            val result = scope.async {
                runCatching {AssistantTurnService.runTurn(app) {calls.incrementAndGet(); entered.complete(Unit); awaitCancellation()}}
            }
            withTimeout(15_000) {entered.await()}
            app.stopService(android.content.Intent(app,AssistantTurnService::class.java))
            assertTrue(withTimeout(15_000) {result.await()}.isFailure)
            assertEquals(1,calls.get())
            waitUntil {!serviceRunning() && !wakeHeld()}
        } finally {scope.cancel(); scenario.close()}
    }
}
