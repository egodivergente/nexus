package com.vidadomu.nexus.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class MediaSnifferTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

    private val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D)
    private val jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10)
    private val mp4 = bytes(0, 0, 0, 0x18) + ascii("ftypisom") + bytes(0, 0, 2, 0)
    private val webp = ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("WEBPVP8 ")

    @Test fun pngWithOctetStreamIsImage() {
        assertEquals("image/png", MediaSniffer.resolve(png, "application/octet-stream"))
        assertEquals("image/png", MediaSniffer.resolve(png, "*/*", null))
    }

    @Test fun garbageNamedPngIsNotImage() {
        val garbage = ascii("hola, no soy imagen")
        val resolved = MediaSniffer.resolve(garbage, "application/octet-stream")
        assertEquals(MediaSniffer.UNKNOWN, resolved)
        assertFalse(resolved.startsWith("image/"))
    }

    @Test fun jpegMp4AndWebpAreDetected() {
        assertEquals("image/jpeg", MediaSniffer.resolve(jpeg, ""))
        assertEquals("video/mp4", MediaSniffer.resolve(mp4, "application/octet-stream"))
        assertEquals("image/webp", MediaSniffer.resolve(webp, null))
    }

    @Test fun specificDeclaredMimeWinsOverBytes() {
        assertEquals("image/png", MediaSniffer.resolve(jpeg, "image/png"))
    }

    @Test fun declaredFamilyIsKeptWhenBytesAreUnknown() {
        assertEquals("image/*", MediaSniffer.resolve(ascii("????"), "application/octet-stream", "image/*"))
        assertEquals("image/jpeg", MediaSniffer.resolve(jpeg, "image/*"))
    }

    @Test fun otherSignatures() {
        assertEquals("image/gif", MediaSniffer.sniff(ascii("GIF89a")))
        assertEquals("audio/wav", MediaSniffer.sniff(ascii("RIFF") + bytes(0, 0, 0, 0) + ascii("WAVEfmt ")))
        assertEquals("video/webm", MediaSniffer.sniff(bytes(0x1A, 0x45, 0xDF, 0xA3)))
        assertEquals("audio/mpeg", MediaSniffer.sniff(ascii("ID3") + bytes(4, 0)))
        assertEquals("audio/mpeg", MediaSniffer.sniff(bytes(0xFF, 0xFB, 0x90)))
        assertEquals("application/pdf", MediaSniffer.sniff(ascii("%PDF-1.7")))
        assertEquals("video/quicktime", MediaSniffer.sniff(bytes(0, 0, 0, 0x14) + ascii("ftypqt  ")))
        assertEquals(null, MediaSniffer.sniff(ByteArray(0)))
    }

    @Test fun genericDetection() {
        assertTrue(MediaSniffer.isGeneric(null))
        assertTrue(MediaSniffer.isGeneric("*/*"))
        assertTrue(MediaSniffer.isGeneric("Application/Octet-Stream; charset=binary"))
        assertFalse(MediaSniffer.isGeneric("image/png"))
    }

    @Test fun readHeaderStopsAtLimitAndAtEnd() {
        assertEquals(MediaSniffer.HEADER_BYTES, MediaSniffer.readHeader(ByteArrayInputStream(ByteArray(64))).size)
        assertEquals(3, MediaSniffer.readHeader(ByteArrayInputStream(ByteArray(3))).size)
    }
}
