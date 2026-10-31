package com.vidadomu.nexus.ui.settings

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class SettingsHubStatusTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val now = ZonedDateTime.of(2026, 10, 20, 14, 30, 0, 0, zone).toInstant().toEpochMilli()
    private val hour = 60L * 60 * 1000

    @Test fun storedKeyAloneIsNeverReportedAsConnected() {
        assertEquals(ProviderPill(false, "Sin comprobar"), providerPill(true, ProviderCheck.NotChecked))
        assertEquals(ProviderPill(false, "Sin clave"), providerPill(false, ProviderCheck.NotChecked))
        assertEquals(ProviderPill(true, "Conectado"), providerPill(true, ProviderCheck.Succeeded(now)))
    }

    @Test fun failedProviderShowsErrorInItsRowAndMissingKeyWins() {
        assertEquals(ProviderPill(false, "Con error"), providerPill(true, ProviderCheck.Failed(now, "401")))
        assertEquals(ProviderPill(false, "Sin clave"), providerPill(false, ProviderCheck.Failed(now, "401")))
    }

    @Test fun readingAgeMarksOldAndUndatedData() {
        assertEquals("leído 13:30", readingAge(now - hour, now, zone))
        assertEquals("leído 18/10 09:00 · dato antiguo",
            readingAge(ZonedDateTime.of(2026, 10, 18, 9, 0, 0, 0, zone).toInstant().toEpochMilli(), now, zone))
        assertEquals("sin fecha de lectura · dato antiguo", readingAge(0L, now, zone))
        assertFalse(isStale(now - SETTINGS_STALE_AFTER_MILLIS, now))
        assertTrue(isStale(now - SETTINGS_STALE_AFTER_MILLIS - 1, now))
    }

    @Test fun yesterdayInsideTheWindowKeepsItsDate() {
        val lateYesterday = ZonedDateTime.of(2026, 10, 19, 23, 50, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("leído 19/10 23:50", readingAge(lateYesterday, now, zone))
    }

    @Test fun hubSummaryLeadsWithTheRealErrorAndItsTime() {
        val summary = providersHubSummary(2, 3, true, ProviderCheck.Failed(now - hour, "La clave API no es válida."),
            now - 3 * 24 * hour, now, zone)
        assertEquals("Atlas con error 13:30: La clave API no es válida. · 2 de 3 con clave · otros sin comprobar aquí", summary)
    }

    @Test fun hubSummaryWithoutAResultSaysUncheckedAndFlagsOldCache() {
        val old = ZonedDateTime.of(2026, 10, 17, 8, 5, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("1 de 3 con clave · Atlas sin comprobar · saldo leído 17/10 08:05 · dato antiguo",
            providersHubSummary(1, 3, true, ProviderCheck.NotChecked, old, now, zone))
        assertEquals("1 de 3 con clave · Atlas sin comprobar",
            providersHubSummary(1, 3, true, ProviderCheck.NotChecked, null, now, zone))
    }

    @Test fun hubSummaryReportsOnlyARealSuccess() {
        assertEquals("1 de 3 con clave · Atlas respondió 14:25",
            providersHubSummary(1, 3, true, ProviderCheck.Succeeded(now - 5 * 60 * 1000), null, now, zone))
    }

    @Test fun hubSummaryWithoutKeysInventsNoStatus() {
        assertEquals("0 de 3 con clave",
            providersHubSummary(0, 3, false, ProviderCheck.NotChecked, null, now, zone))
        assertEquals("2 de 3 con clave · otros sin comprobar aquí",
            providersHubSummary(2, 3, false, ProviderCheck.NotChecked, null, now, zone))
    }

    @Test fun longOrMultilineErrorsStayReadableInTheHub() {
        val long = "x".repeat(200)
        val summary = atlasHubStatus(true, ProviderCheck.Failed(now, long), null, now, zone)!!
        assertTrue(summary.endsWith("…"))
        assertTrue(summary.length < 90)
        assertEquals("Atlas con error 14:30: Primera línea",
            atlasHubStatus(true, ProviderCheck.Failed(now, "\nPrimera línea\nstack"), null, now, zone))
        assertEquals("Atlas con error 14:30: error sin detalle",
            atlasHubStatus(true, ProviderCheck.Failed(now, "  "), null, now, zone))
    }

    @Test fun systemSummaryUsesTheGivenBuildVersion() {
        assertEquals("Nexus 1.9.64-mega1-agent «Júpiter»", systemHubSummary("1.9.64-mega1-agent", "Júpiter", false))
        assertEquals("Nexus 1.9.64 «Júpiter» · proveedor simulado activo", systemHubSummary("1.9.64", "Júpiter", true))
    }
}
