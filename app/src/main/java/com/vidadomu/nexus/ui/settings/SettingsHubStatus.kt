package com.vidadomu.nexus.ui.settings

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Una lectura más antigua se rotula como dato antiguo, no como actual. */
internal const val SETTINGS_STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000
private const val HUB_ERROR_MAX_CHARS = 60

/** Resultado de una petición real de esta sesión; tener la clave guardada nunca cuenta como comprobación. */
internal sealed interface ProviderCheck {
    data object NotChecked : ProviderCheck
    data class Succeeded(val atMillis: Long) : ProviderCheck
    data class Failed(val atMillis: Long, val message: String) : ProviderCheck
}

internal data class ProviderPill(val healthy: Boolean, val label: String)

internal fun providerPill(hasKey: Boolean, check: ProviderCheck): ProviderPill = when {
    !hasKey -> ProviderPill(false, "Sin clave")
    check is ProviderCheck.Failed -> ProviderPill(false, "Con error")
    check is ProviderCheck.Succeeded -> ProviderPill(true, "Conectado")
    else -> ProviderPill(false, "Sin comprobar")
}

/** El puente de Flow no expone una comprobación aquí: vincular no es estar en línea. */
internal fun flowPill(linked: Boolean): ProviderPill = ProviderPill(false, if (linked) "Vinculado" else "Sin vincular")

internal fun checkTime(atMillis: Long, nowMillis: Long, zone: ZoneId): String {
    val at = Instant.ofEpochMilli(atMillis).atZone(zone)
    val sameDay = at.toLocalDate() == Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val pattern = if (sameDay) "HH:mm" else "dd/MM HH:mm"
    return at.format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("es-ES")))
}

internal fun isStale(lastUpdatedMillis: Long, nowMillis: Long): Boolean =
    lastUpdatedMillis <= 0L || nowMillis - lastUpdatedMillis > SETTINGS_STALE_AFTER_MILLIS

/** Sin fecha conocida cuenta como antiguo: nada demuestra que sea reciente. */
internal fun readingAge(lastUpdatedMillis: Long, nowMillis: Long, zone: ZoneId): String = when {
    lastUpdatedMillis <= 0L -> "sin fecha de lectura · dato antiguo"
    isStale(lastUpdatedMillis, nowMillis) -> "leído ${checkTime(lastUpdatedMillis, nowMillis, zone)} · dato antiguo"
    else -> "leído ${checkTime(lastUpdatedMillis, nowMillis, zone)}"
}

private fun shortError(message: String): String {
    val clean = message.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        .ifBlank { "error sin detalle" }
    return if (clean.length <= HUB_ERROR_MAX_CHARS) clean else clean.take(HUB_ERROR_MAX_CHARS - 1).trimEnd() + "…"
}

internal fun atlasHubStatus(hasKey: Boolean, check: ProviderCheck, cachedReadingMillis: Long?, nowMillis: Long, zone: ZoneId): String? {
    if (!hasKey) return null
    return when (check) {
        is ProviderCheck.Failed -> "Atlas con error ${checkTime(check.atMillis, nowMillis, zone)}: ${shortError(check.message)}"
        is ProviderCheck.Succeeded -> "Atlas respondió ${checkTime(check.atMillis, nowMillis, zone)}"
        ProviderCheck.NotChecked -> cachedReadingMillis
            ?.let { "Atlas sin comprobar · saldo ${readingAge(it, nowMillis, zone)}" }
            ?: "Atlas sin comprobar"
    }
}

internal fun providersHubSummary(
    keysConfigured: Int,
    totalProviders: Int,
    atlasHasKey: Boolean,
    atlasCheck: ProviderCheck,
    atlasCachedReadingMillis: Long?,
    flowLinked: Boolean,
    nowMillis: Long,
    zone: ZoneId,
): String {
    val atlas = atlasHubStatus(atlasHasKey, atlasCheck, atlasCachedReadingMillis, nowMillis, zone)
    val othersWithKey = keysConfigured - if (atlasHasKey) 1 else 0
    val parts = buildList {
        if (atlasCheck is ProviderCheck.Failed && atlas != null) add(atlas)
        add("$keysConfigured de $totalProviders con clave")
        if (atlasCheck !is ProviderCheck.Failed && atlas != null) add(atlas)
        if (othersWithKey > 0) add("otros sin comprobar aquí")
        add(if (flowLinked) "Google Flow vinculado" else "Google Flow sin vincular")
    }
    return parts.joinToString(" · ")
}

internal fun versionLabel(versionName: String, codename: String): String = "Nexus $versionName «$codename»"

internal fun systemHubSummary(versionName: String, codename: String, mockMode: Boolean): String =
    versionLabel(versionName, codename) + if (mockMode) " · proveedor simulado activo" else ""
