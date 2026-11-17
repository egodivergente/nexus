package com.vidadomu.nexus.ui.cut

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferencias de pantalla del editor, fuera del proyecto y de su historial: cambiar el reparto o el zoom
 * nunca es una edición. Ripple, imán y reparto son del usuario (valen para todos los montajes); zoom y
 * cabezal son de cada montaje, para volver a él donde se dejó.
 */
internal class CutViewPrefs(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE))

    var ripple: Boolean
        get() = prefs.getBoolean("ripple", true)
        set(v) { prefs.edit().putBoolean("ripple", v).apply() }
    var snapping: Boolean
        get() = prefs.getBoolean("snapping", true)
        set(v) { prefs.edit().putBoolean("snapping", v).apply() }
    /**
     * Parte del espacio de edición que ocupa la línea de tiempo (el resto es la imagen). null = automático:
     * la imagen recibe el alto que pide su proporción (ver [cutPanelSizes]).
     */
    var timelineFraction: Float?
        get() = if (prefs.contains("timeline_fraction")) clampFraction(prefs.getFloat("timeline_fraction", DEFAULT_TIMELINE_FRACTION)) else null
        set(v) { prefs.edit().apply { if (v == null) remove("timeline_fraction") else putFloat("timeline_fraction", clampFraction(v)) }.apply() }

    /** Zoom guardado del montaje, o null si aún no se ha visto (se elige uno según su duración). */
    fun zoom(projectId: String): Float? = if (prefs.contains("zoom.$projectId")) clampZoom(prefs.getFloat("zoom.$projectId", DEFAULT_PX_PER_SECOND)) else null
    fun playhead(projectId: String): Long = prefs.getLong("playhead.$projectId", 0L).coerceAtLeast(0L)

    /** [pxPerSecond] null: solo el cabezal (el zoom sigue como estaba, o sin elegir). */
    fun saveProjectView(projectId: String, pxPerSecond: Float?, playheadUs: Long) {
        prefs.edit().apply {
            if (pxPerSecond != null) putFloat("zoom.$projectId", clampZoom(pxPerSecond))
            putLong("playhead.$projectId", playheadUs.coerceAtLeast(0L))
        }.apply()
    }

    companion object {
        const val NAME = "nexus_cut_view"
        const val DEFAULT_TIMELINE_FRACTION = .42f
        const val DEFAULT_PX_PER_SECOND = 120f
        const val MIN_TIMELINE_FRACTION = .18f
        const val MAX_TIMELINE_FRACTION = .78f
        fun clampFraction(v: Float) = if (v.isFinite()) v.coerceIn(MIN_TIMELINE_FRACTION, MAX_TIMELINE_FRACTION) else DEFAULT_TIMELINE_FRACTION
        fun clampZoom(v: Float) = if (v.isFinite()) v.coerceIn(MIN_PX_PER_S, MAX_PX_PER_S) else DEFAULT_PX_PER_SECOND
    }
}
