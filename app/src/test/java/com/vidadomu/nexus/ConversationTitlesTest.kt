package com.vidadomu.nexus

import com.vidadomu.nexus.assistant.ConversationTitles
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationTitlesTest {
    @Test fun greetingsAndRequestsAreDropped() {
        assertEquals("Hacerme un vídeo de un faro en la niebla", ConversationTitles.smart("hola, puedes hacerme un vídeo de un faro en la niebla. Que dure 8 s"))
        assertEquals("Hagas un cartel para el festival", ConversationTitles.smart("Oye Nexus, necesito que me hagas un cartel para el festival"))
    }

    @Test fun longTitlesCutOnAWordAndMarkupIsRemoved() {
        val title = ConversationTitles.smart("**Corrige** el encargo GPT Image 2.5 Sunburst (ord_1a408518134a): falló con «Metadatos locales no admitidos.»")
        assertEquals(true, title.length <= 49 && title.endsWith("…") && !title.contains("*"))
    }

    @Test fun syncedConversationsKeepTheirMarkAndNothingIsEmpty() {
        assertEquals("↔ Ideas para el corto", ConversationTitles.smart("↔ ideas para el corto"))
        assertEquals("Nueva conversación", ConversationTitles.smart(""))
    }
}
