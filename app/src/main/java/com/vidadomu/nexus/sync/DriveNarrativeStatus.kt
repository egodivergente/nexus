package com.vidadomu.nexus.sync

import kotlinx.serialization.json.*

/** A completed legacy exchange does not prove the living screenplay was shared. */
data class DriveNarrativeStatus(val title: String, val detail: String? = null, val attention: Boolean = false)

fun driveNarrativeStatus(status: JsonObject, enabled: Boolean = false): DriveNarrativeStatus {
    if (!enabled) return DriveNarrativeStatus("Guion y storyboard pausados", "El contenido local se conserva.")
    val value = status["narrativeSync"] as? JsonObject
        ?: return DriveNarrativeStatus("Guion pendiente de sincronizar", "Este intercambio todavía no confirma el guion y sus vínculos de material.")
    fun text(key: String) = (value[key] as? JsonPrimitive)?.contentOrNull
    fun count(key: String) = (value[key] as? JsonPrimitive)?.intOrNull?.coerceAtLeast(0) ?: 0
    if ((value["supported"] as? JsonPrimitive)?.booleanOrNull != true)
        return DriveNarrativeStatus("Guion sin conexión compatible", "Esta conexión todavía no permite compartir la estructura del guion.", true)
    if ((value["enabled"] as? JsonPrimitive)?.booleanOrNull == false)
        return DriveNarrativeStatus("Guion pendiente de sincronizar", "Se incluirá en el próximo intercambio.")
    val conflicts = count("conflicts")
    val dependencies = count("pendingDependencies")
    if (conflicts > 0 || text("state") == "conflicts") return DriveNarrativeStatus(
        "Guion con versiones para revisar",
        if (conflicts == 1) "1 elemento conserva versiones distintas." else if (conflicts > 1) "$conflicts elementos conservan versiones distintas." else "Compara las versiones antes de elegir.", true)
    if (dependencies > 0) return DriveNarrativeStatus(
        "Guion con vínculos pendientes",
        if (dependencies == 1) "1 asociación necesita recuperar su contexto o material." else "$dependencies asociaciones necesitan recuperar su contexto o material.", true)
    return when (text("state")) {
        "synced" -> DriveNarrativeStatus("Guion compartido en Drive", "El otro dispositivo necesita una versión compatible y completar su sincronización.")
        "syncing" -> DriveNarrativeStatus("Compartiendo guion y storyboard…")
        "disabled" -> DriveNarrativeStatus("Guion pendiente de sincronizar", "Se incluirá en el próximo intercambio.")
        "unsupported", "waiting_peer" -> DriveNarrativeStatus("Guion pendiente de otro dispositivo compatible", text("reason"), true)
        "error", "retry" -> DriveNarrativeStatus("Guion pendiente de reintento", text("reason") ?: "Tus cambios locales se conservan.", true)
        "partial" -> DriveNarrativeStatus("Guion recibido parcialmente", text("reason") ?: "Hay información pendiente de recuperar.", true)
        else -> DriveNarrativeStatus("Guion pendiente de sincronizar", text("reason"))
    }
}
