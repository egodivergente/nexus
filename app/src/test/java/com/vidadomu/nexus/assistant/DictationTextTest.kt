package com.vidadomu.nexus.assistant

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class DictationTextTest {
    @Test fun dictationIntoAnEmptyFieldIsJustTheSpokenText() {
        val result = dictatedText(TextFieldValue(""), "  haz un plano nocturno de Madrid ")
        assertEquals("haz un plano nocturno de Madrid", result.text)
        assertEquals(TextRange(result.text.length), result.selection)
    }

    @Test fun dictationIsAppendedWithOneSpaceAndTheCursorGoesToTheEnd() {
        assertEquals("quiero un vídeo de 10 s", dictatedText(TextFieldValue("quiero un vídeo"), "de 10 s").text)
        assertEquals("hola mundo", dictatedText(TextFieldValue("hola "), "mundo").text)
    }
}
