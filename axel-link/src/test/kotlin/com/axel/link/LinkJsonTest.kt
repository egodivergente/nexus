package com.axel.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkJsonTest {
    @Test fun readsNestedValuesWithTheirTypes() {
        val value = LinkJson.obj("""{"a": 1, "b": -2.5e1, "c": [true, false, null, "x"], "d": {"e": "f"}, "g": 12345678901234}""")
        assertEquals(1L, value["a"])
        assertEquals(-25.0, value["b"])
        assertEquals(listOf(true, false, null, "x"), value["c"])
        assertEquals("f", value.child("d").text("e"))
        assertEquals(12345678901234L, value["g"])
    }

    @Test fun keepsEscapesAndUnicodeThroughARoundTrip() {
        val original = mapOf("texto" to "línea 1\nruta C:\\Users\\\"Vidal\" \t \u0001 ñ 日本 \uD83D\uDE00", "n" to 3, "lista" to listOf(1.5, "a"))
        val text = LinkJson.write(original)
        assertTrue(text.contains("\\u0001"))
        val back = LinkJson.obj(text)
        assertEquals(original["texto"], back["texto"])
        assertEquals(3L, back["n"])
        assertEquals(listOf(1.5, "a"), back["lista"])
        assertEquals("é", LinkJson.parse("\"\\u00e9\""))
    }

    @Test fun rejectsWhatIsNotJson() {
        for (bad in listOf("", "{", "{\"a\":}", "[1,]", "<html>", "{\"a\":1} x", "\"sin cerrar", "nul")) {
            assertThrows(bad, IllegalArgumentException::class.java) { LinkJson.parse(bad) }
        }
        assertNull(LinkJson.parse("null"))
        assertThrows(IllegalArgumentException::class.java) { LinkJson.obj("[1]") }
    }
}
