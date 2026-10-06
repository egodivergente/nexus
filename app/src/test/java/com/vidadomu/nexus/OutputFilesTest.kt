package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.*
import org.junit.Assert.*
import org.junit.Test

class OutputFilesTest {
    private fun output(id: String, url: String = "https://example.invalid/$id.png", local: String? = "file:///old.png") =
        GenerationOutput(id, "job", Modality.IMAGE, url, local)

    @Test fun independentResultsHaveStableIndependentNames() {
        val outputs = (1..4).map { output("result-$it") }
        assertEquals(4, outputs.map(OutputFiles::token).distinct().size)
        assertEquals(OutputFiles.token(outputs[0]), OutputFiles.token(outputs[0].copy(localUri = null)))
        assertNotEquals(OutputFiles.token(outputs[0]), OutputFiles.token(outputs[0].copy(jobId = "other")))
    }

    @Test fun oldCollisionIsDetectedWithoutTrustingAnyAmbiguousCopy() {
        val outputs = listOf(output("one"), output("two"), output("three", local = "file:///third.png"))
        val conflicts = OutputFiles.conflictingLocalUris(outputs)
        assertEquals(setOf("file:///old.png"), conflicts)
        assertNull(OutputFiles.trustedLocalUri(outputs[0], conflicts))
        assertNull(OutputFiles.trustedLocalUri(outputs[1], conflicts))
        assertEquals("file:///third.png", OutputFiles.trustedLocalUri(outputs[2], conflicts))
        assertEquals("file:///old.png", outputs[0].localUri) // Detection never mutates saved data.
    }

    @Test fun sameRemoteFileAndMissingLocalCopiesAreNotFalseCollisions() {
        val first = output("one")
        assertTrue(OutputFiles.conflictingLocalUris(listOf(first, first.copy(id = "duplicate"))).isEmpty())
        assertTrue(OutputFiles.conflictingLocalUris(listOf(output("one", local = null), output("two", local = null))).isEmpty())
    }
}
