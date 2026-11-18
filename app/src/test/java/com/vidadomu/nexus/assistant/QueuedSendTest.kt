package com.vidadomu.nexus.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Enviar al terminar»: when the waiting message goes out, when it is kept, and when nothing has to be said. */
class QueuedSendTest {
    private fun after(queued: Boolean = true, completed: Boolean = true, stopped: Boolean = false, onScreen: Boolean = true, moved: Boolean = false) =
        QueuedSend.afterTurn(queued, completed, stopped, onScreen, moved)

    @Test fun anAnswerThatEndsOnItsOwnSendsWhatWasWaiting() {
        assertEquals(QueuedOutcome.SEND, after())
    }

    @Test fun nothingWaitingMeansNothingToDoHoweverTheTurnEnds() {
        for (completed in listOf(true, false)) for (stopped in listOf(true, false)) for (onScreen in listOf(true, false)) for (moved in listOf(true, false))
            assertEquals(QueuedOutcome.NONE, after(queued = false, completed = completed, stopped = stopped, onScreen = onScreen, moved = moved))
    }

    @Test fun stoppingTheAnswerNeverSendsAndNeedsNoNotice() {
        // Stop cancels the turn: it did not complete. Even a contradictory «completed» must not send after a stop.
        assertEquals(QueuedOutcome.NONE, after(completed = false, stopped = true))
        assertEquals(QueuedOutcome.NONE, after(completed = true, stopped = true))
    }

    @Test fun anErrorOrATimeoutKeepsTheDraftAndSaysSo() {
        assertEquals(QueuedOutcome.KEPT, after(completed = false))
    }

    @Test fun aTurnOfAnotherConversationNeverSendsHere() {
        assertEquals(QueuedOutcome.NONE, after(onScreen = false))
        assertEquals(QueuedOutcome.NONE, after(onScreen = false, completed = false))
    }

    @Test fun ifTheAgentChangedProjectTheDraftIsKeptWithItsConversation() {
        assertEquals(QueuedOutcome.KEPT, after(moved = true))
        assertEquals(QueuedOutcome.KEPT, after(moved = true, completed = false))
    }

    @Test fun sendingIsTheOnlyOutcomeOfExactlyOneCase() {
        var sends = 0
        for (queued in listOf(true, false)) for (completed in listOf(true, false)) for (stopped in listOf(true, false))
            for (onScreen in listOf(true, false)) for (moved in listOf(true, false))
                if (after(queued, completed, stopped, onScreen, moved) == QueuedOutcome.SEND) sends++
        assertEquals(1, sends)
    }

    @Test fun theNoticeSaysWhereTheDraftIs() {
        assertTrue(QueuedSend.keptMessage(movedProject = false).contains("cuadro de texto"))
        assertTrue(QueuedSend.keptMessage(movedProject = true).contains("cambiado de proyecto"))
        assertNotEquals(QueuedSend.keptMessage(true), QueuedSend.keptMessage(false))
        for (moved in listOf(true, false)) assertTrue(QueuedSend.keptMessage(moved).startsWith("El mensaje en espera no se ha enviado"))
    }
}
