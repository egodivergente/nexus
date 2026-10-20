package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.tools.Attachment
import org.junit.Test
import org.junit.Assert.*

class MediaAndReferenceTest {
    @Test fun proportionsAndPixelRotation() {
        assertEquals(9f/16f,mediaAspectRatio(1080,1920)!!,.0001f)
        assertEquals(16f/9f,mediaAspectRatio(1920,1080)!!,.0001f)
        assertEquals(1f,mediaAspectRatio(1024,1024)!!,.0001f)
        assertEquals(9f/16f,mediaAspectRatio(1920,1080,rotation=90)!!,.0001f)
        assertEquals(16f/9f,mediaAspectRatio(720,576,64f/45f)!!,.0001f)
        assertNull(mediaAspectRatio(null,100)); assertNull(mediaAspectRatio(0,100))
        assertNull(mediaAspectRatio(100,-1)); assertNull(mediaAspectRatio(100,100,Float.NaN))
    }
    @Test fun addingReferencesPreservesOrderAndDoesNotDuplicateIdOrUri() {
        val a=Attachment("a","file:///a","image/jpeg")
        val b=Attachment("b","file:///b","video/mp4")
        assertEquals(listOf(a,b),mergeReferenceAttachments(listOf(a),b))
        assertEquals(listOf(a),mergeReferenceAttachments(listOf(a),a.copy(name="Again")))
        assertEquals(listOf(a),mergeReferenceAttachments(listOf(a),a.copy(id="duplicate")))
    }
}
