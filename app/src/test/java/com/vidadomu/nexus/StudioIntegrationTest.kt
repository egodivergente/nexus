package com.vidadomu.nexus

import com.vidadomu.nexus.assistant.quickOrderControls
import com.vidadomu.nexus.agent.core.registry.*
import com.vidadomu.nexus.agent.core.spend.GenerationPlan
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.components.liveRenderSnapshot
import org.junit.Assert.*
import org.junit.Test

class StudioIntegrationTest {
    @Test fun widgetChoicesAreExactAndDoNotSelectSubstrings() {
        val answer=com.vidadomu.nexus.assistant.WidgetCtx("Elijo: Vídeo Pro",null)
        assertTrue(answer.chose("Vídeo Pro"))
        assertFalse(answer.chose("Vídeo"))
        val multi=com.vidadomu.nexus.assistant.WidgetCtx("A, B\nC",null)
        assertTrue(multi.chose("A, B"))
        assertTrue(multi.chose("C"))
        assertFalse(multi.chose("B"))
    }
    private val ref = ModelRef("provider", "model")
    private fun plan() = GenerationPlan("p", ref, "model", "VIDEO", prompt="scene", params=emptyMap(),
        effectiveParams=mapOf("aspectRatio" to "4:3", "duration" to "5", "quality" to "high", "seed" to "1"), createdAt=0)
    @Test fun quickOptionsComeFromTheActualModelIncludingCamelCaseFields() {
        val schema=ModelSchema(ref, listOf(ParamSpec("aspectRatio", "Formato", "ASPECT_RATIO", options=listOf("4:3", "3:4")),
            ParamSpec("duration", "Duración", "DURATION", options=listOf("5", "10")),
            ParamSpec("quality", "Calidad", "ENUM", options=listOf("high"), advanced=true)), MediaSpec())
        val controls=quickOrderControls(plan(), schema)
        assertEquals(listOf("aspectRatio", "duration"), controls.map { it.key })
        assertEquals(listOf("4:3", "3:4"), controls.first().options)
        assertFalse(controls.first().options.contains("16:9"))
    }
    @Test fun failedOrStaleSchemaDoesNotInventControls() {
        assertTrue(quickOrderControls(plan(), null).isEmpty())
        assertTrue(quickOrderControls(plan(), ModelSchema(ModelRef("other","model"), emptyList(), MediaSpec())).isEmpty())
    }
    private fun job(id:String,status:GenerationStatus,started:Long=0,completed:Long?=null)=GenerationJob(
        id=id,providerId="google_flow",modelId="model",modality=Modality.IMAGE,prompt="test",status=status,
        startedAt=started,completedAt=completed,outputs=listOf(GenerationOutput("out-$id",id,Modality.IMAGE,"https://example.invalid/$id.png")))
    @Test fun completionAfterIdleIsSelectedByCompletionTimeRatherThanJobStart() {
        val snapshot=liveRenderSnapshot(listOf(job("new-start",GenerationStatus.COMPLETED,100,80_000),
            job("recent-finish",GenerationStatus.COMPLETED,1,99_999)),100_000,emptySet())
        assertEquals("recent-finish", snapshot.completed?.id)
    }
    @Test fun expiryDismissalAndPausedJobsNeverAppearAsRunning() {
        val done=job("done",GenerationStatus.COMPLETED,completed=10_000)
        val paused=job("paused",GenerationStatus.GENERATING).copy(parameters=mapOf("__tracking_paused" to "true"))
        assertNull(liveRenderSnapshot(listOf(done),30_001,emptySet()).completed)
        assertNull(liveRenderSnapshot(listOf(done),15_000,setOf("done")).completed)
        assertNull(liveRenderSnapshot(listOf(done),9_999,emptySet()).completed)
        assertTrue(liveRenderSnapshot(listOf(paused),15_000,emptySet()).active.isEmpty())
    }
    @Test fun activeAndCompletedJobsRemainAvailableTogether() {
        val snapshot=liveRenderSnapshot(listOf(job("run",GenerationStatus.UPLOADING,started=9_000),
            job("done",GenerationStatus.COMPLETED,completed=10_000)),15_000,emptySet())
        assertEquals("run", snapshot.active.single().id)
        assertEquals("done", snapshot.completed?.id)
    }
}
