package com.vidadomu.nexus

import com.vidadomu.nexus.ui.library.tileDateTime
import com.vidadomu.nexus.ui.library.tileQuality
import com.vidadomu.nexus.ui.library.tileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class LibraryTileDetailsTest {
    @Test fun videoQualityUsesTheShortSideSoVerticalVideosReadTheSame() {
        assertEquals("4K", tileQuality(image = false, 3840, 2160))
        assertEquals("1080p", tileQuality(image = false, 1920, 1080))
        assertEquals("1080p", tileQuality(image = false, 1080, 1920))
        assertEquals("1440p", tileQuality(image = false, 2560, 1440))
        assertEquals("720p", tileQuality(image = false, 1280, 720))
    }

    @Test fun imageQualityShowsPixelsAndUnknownSizesShowNothing() {
        assertEquals("2048×2048", tileQuality(image = true, 2048, 2048))
        assertNull(tileQuality(image = false, null, 1080))
        assertNull(tileQuality(image = true, 0, 0))
    }

    @Test fun sizeReadsInKilobytesMegabytesOrGigabytesWithSpanishDecimals() {
        assertEquals("1 KB", tileSize(10))
        assertEquals("350 KB", tileSize(350L * 1024))
        assertEquals("12,4 MB", tileSize((12.4 * 1024 * 1024).toLong()))
        assertEquals("1,5 GB", tileSize(3L * 512 * 1024 * 1024))
    }

    @Test fun dateOmitsTheYearOnlyWhenItIsTheCurrentOne() {
        fun at(year: Int) = Calendar.getInstance().apply { set(year, Calendar.OCTOBER, 16, 7, 14, 0) }.timeInMillis
        val now = at(2026)
        assertEquals("16 oct · 07:14", tileDateTime(at(2026), now))
        assertEquals("16 oct 2025 · 07:14", tileDateTime(at(2025), now))
    }
}
