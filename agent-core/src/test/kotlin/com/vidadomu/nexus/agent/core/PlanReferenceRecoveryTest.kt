package com.vidadomu.nexus.agent.core

import com.vidadomu.nexus.agent.core.config.AgentMode
import com.vidadomu.nexus.agent.core.spend.PlanState
import com.vidadomu.nexus.agent.core.tools.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class PlanReferenceRecoveryTest {
    private fun session() = AgentSession("reference-fix", AgentMode.AGENT).apply {
        attachments += Attachment("first", "file:///first.png", "image/png")
        attachments += Attachment("second", "file:///second.png", "image/png")
    }
    private fun request(extra: String = "") = Json.parseToJsonElement("""{"model_id":"${KLING_PRO.modelId}","provider_id":"$ATLAS","prompt":"Fixture motion","media":[{"role":"first_frame","attachment_id":"first"}]$extra}""").jsonObject
    private fun planId(result: ToolOutcome) = result.result.jsonObject["plan"]!!.jsonObject["plan_id"]!!.jsonPrimitive.content

    @Test fun repeatedPreparationReusesThePendingCardWithoutSubmitting() = runTest {
        val h = Harness(); val tool = PrepareGenerationTool(h.deps); val s = session()
        val first = tool.run(request(), s); val second = tool.run(request(), s)
        assertFalse(first.isError); assertFalse(second.isError)
        assertEquals(planId(first), planId(second)); assertEquals(1, h.guard.plans().size)
        assertTrue(second.result.jsonObject["reused"]!!.jsonPrimitive.boolean)
        assertTrue(h.backend.submitted.isEmpty())
    }

    @Test fun referencesCanBeReplacedOnTheSamePlanAndInvalidateConfirmation() = runTest {
        val h = Harness(); val s = session(); val id = planId(PrepareGenerationTool(h.deps).run(request(), s))
        val old = h.guard.plan(id)!!.fingerprint(); h.guard.requestConfirmation(id)
        val result = UpdatePlanTool(h.deps).run(Json.parseToJsonElement("""{"plan_id":"$id","media":[{"role":"first_frame","attachment_id":"second"}]}""").jsonObject, s)
        assertFalse(result.isError); assertEquals(id, planId(result)); assertEquals(1, h.guard.plans().size)
        assertEquals("file:///second.png", h.guard.plan(id)!!.media.single().uri)
        assertNotEquals(old, h.guard.plan(id)!!.fingerprint()); assertEquals(PlanState.PREPARED, h.guard.state(id))
        assertTrue(h.backend.submitted.isEmpty())
    }

    @Test fun invalidReferenceEditPreservesTheOriginalPlan() = runTest {
        val h = Harness(); val s = session(); val id = planId(PrepareGenerationTool(h.deps).run(request(), s))
        val before = h.guard.plan(id)
        val result = UpdatePlanTool(h.deps).run(Json.parseToJsonElement("""{"plan_id":"$id","media":[]}""").jsonObject, s)
        assertTrue(result.isError); assertEquals(before, h.guard.plan(id)); assertTrue(h.backend.submitted.isEmpty())
    }

    @Test fun explicitAdditionalCopyIsNotDeduplicated() = runTest {
        val h = Harness(); val s = session(); val tool = PrepareGenerationTool(h.deps)
        val first = planId(tool.run(request(), s)); val second = planId(tool.run(request(",\"new_copy\":true"), s))
        assertNotEquals(first, second); assertEquals(2, h.guard.plans().size)
    }

    @Test fun discardedAndUncertainPlansAreNotReused() = runTest {
        val h = Harness(); val s = session(); val tool = PrepareGenerationTool(h.deps)
        val first = planId(tool.run(request(), s)); h.guard.discard(first)
        val second = planId(tool.run(request(), s)); assertNotEquals(first, second)
        h.backend.failNext = com.vidadomu.nexus.agent.core.spend.SubmissionUncertain("fixture")
        assertFailsWith<com.vidadomu.nexus.agent.core.spend.SubmissionUncertain> { h.deps.lifecycle.confirmAndSubmit(second, h.guard.plan(second)!!.fingerprint()) }
        val third = planId(tool.run(request(), s)); assertNotEquals(second, third)
    }
}
