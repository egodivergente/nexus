package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.ui.theme.NexusHapticEvent

/** What a press of «Generar» did, so the button is felt for it: sent, a cost question, refused, or nothing. */
enum class GenerationRequestOutcome(val hapticEvent: NexusHapticEvent?) {
    SENT(NexusHapticEvent.CONFIRMATION),
    ASKS_ACCEPTANCE(NexusHapticEvent.SELECTION),
    REFUSED(NexusHapticEvent.ERROR),
    IGNORED(null),
}
