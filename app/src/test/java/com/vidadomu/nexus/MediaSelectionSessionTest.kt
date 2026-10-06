package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.MediaSlotType.*
import com.vidadomu.nexus.ui.create.MediaSelectionSession
import org.junit.Assert.*
import org.junit.Test

class MediaSelectionSessionTest {
    @Test fun separateContractInputsDoNotCancelEachOther() {
        val session = MediaSelectionSession()
        val audio = session.begin(REFERENCE_IMAGE, fieldKey = "audio")
        val mask = session.begin(REFERENCE_IMAGE, fieldKey = "mask")
        assertTrue(session.accepts(audio)); assertTrue(session.accepts(mask))
        session.invalidate(REFERENCE_IMAGE, "mask")
        assertTrue(session.accepts(audio)); assertFalse(session.accepts(mask))
    }
    @Test fun switchingAwayAndBackRejectsOldCopy() {
        val session = MediaSelectionSession()
        val old = session.begin(PRIMARY_IMAGE)
        session.reset()
        session.reset()
        assertFalse(session.accepts(old))
    }
    @Test fun mostRecentPrimarySelectionWins() {
        val session = MediaSelectionSession()
        val old = session.begin(PRIMARY_IMAGE)
        val current = session.begin(PRIMARY_IMAGE)
        assertFalse(session.accepts(old))
        assertTrue(session.accepts(current))
    }
    @Test fun firstAndLastFrameCanCopyIndependently() {
        val session = MediaSelectionSession()
        val first = session.begin(PRIMARY_IMAGE)
        val last = session.begin(LAST_FRAME)
        assertTrue(session.accepts(first))
        assertTrue(session.accepts(last))
    }
    @Test fun referenceBatchesCanAppendConcurrently() {
        val session = MediaSelectionSession()
        val first = session.begin(REFERENCE_IMAGE, append = true)
        val second = session.begin(REFERENCE_IMAGE, append = true)
        assertTrue(session.accepts(first))
        assertTrue(session.accepts(second))
    }
    @Test fun clearingSlotPreventsPendingCopyFromReappearing() {
        val session = MediaSelectionSession()
        val pending = session.begin(VIDEO_INPUT)
        session.invalidate(VIDEO_INPUT)
        assertFalse(session.accepts(pending))
    }
    @Test fun clearingAllRejectsEveryPendingSlot() {
        val session = MediaSelectionSession()
        val tokens = listOf(session.begin(PRIMARY_IMAGE), session.begin(LAST_FRAME), session.begin(REFERENCE_IMAGE, true))
        session.reset()
        assertTrue(tokens.none(session::accepts))
    }
}
