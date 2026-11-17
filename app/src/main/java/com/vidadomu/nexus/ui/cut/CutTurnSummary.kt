package com.vidadomu.nexus.ui.cut

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Resumen comprobado de lo que hizo el agente en un turno, a partir de los «antes/después» que da el
 * motor en cada paso confirmado: el «antes» del primero y el «después» del último. No interpreta lo que
 * el modelo dijo; solo lo que cambió de verdad en el montaje.
 */
internal object CutTurnSummary {
    fun describe(first: JsonObject?, last: JsonObject?): String? {
        if (first == null || last == null) return null
        fun pair(o: JsonObject, key: String, side: String): Long? = runCatching {
            o[key]?.jsonObject?.get(side)?.jsonPrimitive?.longOrNull
        }.getOrNull()
        val d0 = pair(first, "duration_us", "before")
        val d1 = pair(last, "duration_us", "after")
        val c0 = pair(first, "clips", "before")
        val c1 = pair(last, "clips", "after")
        val parts = ArrayList<String>(2)
        if (d0 != null && d1 != null && d0 != d1) parts += "Duración ${CutDragText.seconds(d0)} → ${CutDragText.seconds(d1)}"
        if (c0 != null && c1 != null && c0 != c1) parts += "$c0 → $c1 clips"
        return if (parts.isEmpty()) "Comprobado en el montaje" else parts.joinToString(" · ")
    }

    /** «Dividir clip», o «Dividir clip y 2 más». */
    fun headline(steps: List<String>): String = when (steps.size) {
        0 -> "Sin cambios en el montaje"
        1 -> steps.first()
        else -> "${steps.first()} y ${steps.size - 1} más"
    }
}
