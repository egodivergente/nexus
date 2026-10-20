package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.sync.mergeCompletedPeerJob
import org.junit.Assert.*
import org.junit.Test

class PeerJobsTest {
    private val output = GenerationOutput("one", "job", Modality.IMAGE, "https://example.invalid/one.png")
    private val job = GenerationJob("job", providerId="test", modelId="test", modality=Modality.IMAGE,
        prompt="local", status=GenerationStatus.COMPLETED, outputs=listOf(output))
    @Test fun importsOnlyCompletedJobsAndNeverOverridesRunningWork() {
        assertNull(mergeCompletedPeerJob(null, job.copy(status=GenerationStatus.GENERATING)))
        assertEquals(job, mergeCompletedPeerJob(null, job))
        val running = job.copy(status=GenerationStatus.GENERATING)
        assertEquals(running, mergeCompletedPeerJob(running, job))
    }
    @Test fun fillsMissingLocalFilesAndAddsOutputsWithoutChangingOriginalPrompt() {
        val file=java.io.File.createTempFile("nexus-peer-verified",".png")
        try {
        val uri=file.toURI().toString()
        val incoming = job.copy(prompt="remote", outputs=listOf(output.copy(localUri=uri), output.copy(id="two")))
        val merged = mergeCompletedPeerJob(job, incoming)!!
        assertEquals("local", merged.prompt)
        assertEquals(uri, merged.outputs[0].localUri)
        assertEquals(2, merged.outputs.size)
        assertEquals(merged, mergeCompletedPeerJob(merged, incoming))
        } finally {file.delete()}
    }
    @Test fun keepsExistingLocalUriAndRejectsProviderIdCollision() {
        val local=job.copy(outputs=listOf(output.copy(localUri="file:///original")))
        val remote=job.copy(outputs=listOf(output.copy(localUri="file:///other")))
        assertEquals(local, mergeCompletedPeerJob(local,remote))
        assertEquals(local, mergeCompletedPeerJob(local,remote.copy(providerId="different")))
    }
    @Test fun repairsMissingOldPhoneUriWithVerifiedPeerCopy() {
        val file=java.io.File.createTempFile("nexus-peer-repair",".png")
        try {
            val local=job.copy(outputs=listOf(output.copy(localUri="file:///old-phone/missing")))
            val remote=job.copy(outputs=listOf(output.copy(localUri=file.toURI().toString())))
            assertEquals(file.toURI().toString(),mergeCompletedPeerJob(local,remote)!!.outputs.single().localUri)
        } finally {file.delete()}
    }
}
