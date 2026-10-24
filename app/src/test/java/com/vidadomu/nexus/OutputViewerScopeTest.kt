package com.vidadomu.nexus

import com.vidadomu.nexus.ui.output.OutputViewerScope
import org.junit.Assert.*
import org.junit.Test

class OutputViewerScopeTest {
    @Test fun viewerCapturesOnlyTheOriginCollectionAndDoesNotLeakItToTheNextRoute() {
        val collection=listOf("flow-1" to 0,"flow-1" to 1,"flow-2" to 0)
        OutputViewerScope.prepare("flow-1",collection+collection.first())
        assertEquals(collection,OutputViewerScope.take("flow-1"))
        assertTrue(OutputViewerScope.take("another-job").isEmpty())
        OutputViewerScope.prepare("flow-1",collection)
        assertTrue(OutputViewerScope.take("another-job").isEmpty())
        assertTrue(OutputViewerScope.take("flow-1").isEmpty())
    }
}
