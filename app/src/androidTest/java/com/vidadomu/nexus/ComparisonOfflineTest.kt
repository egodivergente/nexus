package com.vidadomu.nexus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.ui.create.*
import com.vidadomu.nexus.core.model.AttachedMedia
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComparisonOfflineTest {
    @Test fun localPreviewRetainsThreeIndependentReferences() = runBlocking {
        check(BuildConfig.OFFLINE_QA)
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
        withContext(Dispatchers.Main) {
            val store = androidx.lifecycle.ViewModelStore()
            val vm = CreateViewModel(app)
            store.put("references",vm)
            try {
                val model=LocalQaFixture.model(app,"references")
                val media=LocalQaFixture.media(app,model,3)
                vm.loadLocalPreview(model,media)
                assertEquals(3,vm.uiState.value.attachedMedia.map { it.uriString }.distinct().size)
            } finally { store.clear() }
        }
    }

    @Test fun threeReferencesUseEditingContractsAndBlockIncompatibleModelBeforeReview() = runBlocking {
        check(BuildConfig.OFFLINE_QA)
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
        app.database.loading.first { !it }
        val before = app.generationRepository.allJobs.value
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val controller = ComparisonController(app, scope)
        try {
            withContext(Dispatchers.Main) {
                controller.open(CreateUiState(prompt = "QA · escultura de cerámica azul", schemaReady = true, attachedMedia = (1..3).map { AttachedMedia(uriString="file:///local/ref$it.png",mimeType="image/png",slotType=com.vidadomu.nexus.core.model.MediaSlotType.REFERENCE_IMAGE) }))
            }
            withTimeout(10_000) { controller.state.first { it.checking.isEmpty() && it.checked.size == 4 } }
            withContext(Dispatchers.Main) {
                assertTrue(controller.state.value.catalog.none { it.id.endsWith("text-to-image") })
                controller.state.value.catalog.forEach { if(ComparisonPlan.key(it) !in controller.state.value.selected) controller.toggle(it) }
                assertEquals(3, controller.state.value.selected.size)
                assertTrue(controller.state.value.selectionProblems.values.any { "1 referencias" in it })
                controller.state.value.catalog.filter { ComparisonPlan.key(it) in controller.state.value.selected }
                    .forEachIndexed { index, model -> controller.setQuantity(model, index+1) }
                assertTrue(controller.state.value.canReview)
                controller.prepare()
            }
            val prepared = withTimeout(10_000) { controller.state.first { it.phase == ComparisonPhase.REVIEW } }
            assertEquals(3, prepared.entries.size)
            assertTrue(prepared.entries.all { it.error == null && it.cost != null && it.prepared != null })
            assertTrue(prepared.canSend)
            assertEquals(6, prepared.requestCount)
            assertEquals(.3, prepared.total!![ComparisonCurrency.USD]!!, .00001)
            assertEquals(6, ComparisonPlan.requests(prepared.entries.map { it.quantity }).size)
            withContext(Dispatchers.Main) { controller.setQuantity(prepared.entries.first().model, 20) }
            assertEquals(6, controller.state.value.requestCount)
            withContext(Dispatchers.Main) { controller.send(); controller.send() }
            assertTrue(controller.state.value.message.orEmpty().contains("0 generaciones"))
            assertFalse(controller.sending)
            var uploadOrSubmitStarted = false
            try {
                GenerationDispatcher(app).submit(prepared.entries.first().prepared!!.state) { _,_ -> uploadOrSubmitStarted = true }
                fail("Offline QA must reject dispatch")
            } catch(_: IllegalStateException) { }
            assertFalse(uploadOrSubmitStarted)
            assertEquals(before, app.generationRepository.allJobs.value)
        } finally { scope.cancel() }
    }
}
