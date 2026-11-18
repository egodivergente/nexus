package com.vidadomu.nexus.assistant

/** What happens, when a turn ends, to the message the user left waiting («Enviar al terminar»). */
internal enum class QueuedOutcome {
    /** Nothing was waiting, or it is not this screen's business: the draft stays where it was written. */
    NONE,
    /** The answer ended on its own in the conversation on screen: the draft goes out as the next message. */
    SEND,
    /** It was waiting and it cannot go out: the draft is kept and the user is told. */
    KEPT,
}

/** The rules of the queue, apart from the view model so they can be read and tested on their own. */
internal object QueuedSend {
    /**
     * [queued]: something was left waiting. [completed]: the answer ended on its own. [stopped]: the user stopped it.
     * [onScreen]: the turn belongs to the conversation on screen. [movedProject]: the agent changed project while
     * it worked, so the screen is about to show another conversation.
     */
    fun afterTurn(queued: Boolean, completed: Boolean, stopped: Boolean, onScreen: Boolean, movedProject: Boolean): QueuedOutcome = when {
        !queued -> QueuedOutcome.NONE
        // Stopping is the user's own decision and a turn in another conversation has its draft in its own field:
        // neither needs a notice, and neither sends.
        stopped || !onScreen -> QueuedOutcome.NONE
        completed && !movedProject -> QueuedOutcome.SEND
        // An error, a timeout or a change of project: sending now would answer something the user has not seen.
        else -> QueuedOutcome.KEPT
    }

    fun keptMessage(movedProject: Boolean): String =
        if (movedProject) "El mensaje en espera no se ha enviado porque Nexus ha cambiado de proyecto. Sigue como borrador en su conversación."
        else "El mensaje en espera no se ha enviado. Sigue en el cuadro de texto."
}
