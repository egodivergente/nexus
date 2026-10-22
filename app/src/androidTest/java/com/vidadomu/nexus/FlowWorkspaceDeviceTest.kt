package com.vidadomu.nexus

import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.ui.flow.*
import org.junit.Assert.*
import org.junit.Test

class FlowWorkspaceDeviceTest {
    @Test fun promptsAndPrivateReferencesSurviveReentryWithoutLeakingAcrossProjects() {
        check(BuildConfig.OFFLINE_QA)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val key="canvas-test-${java.util.UUID.randomUUID()}"
        val drafts=listOf(FlowStudioDraft(prompt="Draft fixture",count=3,modality=Modality.VIDEO))
        val refs=listOf(FlowStudioReference("file:///private-fixture.png","image/png","Fixture"))
        try {
            FlowWorkspaceStore(context,key).write(drafts,refs)
            val restored=FlowWorkspaceStore(context,key).read()
            assertEquals(drafts,restored.drafts)
            assertEquals(refs,restored.references)
            assertTrue(FlowWorkspaceStore(context,"$key-other").read().drafts.single().prompt.isEmpty())
            FlowWorkspaceStore(context,key).write(listOf(FlowStudioDraft()),emptyList())
            assertTrue(FlowWorkspaceStore(context,key).read().references.isEmpty())
        } finally { context.getSharedPreferences("flow_workspace_v1",0).edit().remove(key).apply() }
    }
}
