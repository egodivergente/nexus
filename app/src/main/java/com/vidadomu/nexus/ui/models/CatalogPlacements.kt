package com.vidadomu.nexus.ui.models

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf

/**
 * The user's own sorting of the catalogue: which section a model (or a whole family) lives in. A target is
 * `tool:<category>` (shown in Herramientas under that category) or `none` (never a tool: it stays in Vídeos/Imágenes).
 * No target means the catalogue's own classification. Only where Nexus shows things changes, never what the model does.
 */
object CatalogPlacements {
    private var prefs: SharedPreferences? = null
    private val targets = HashMap<String, String>()
    /** Read by the catalogue screens so they recompute when the user moves something. */
    val revision = mutableIntStateOf(0)

    val toolCategories = listOf("Edición de vídeo", "Movimiento y pose", "Extender vídeo", "Sincronización labial", "Upscale vídeo",
        "Edición de imagen", "Upscale imagen", "Audio, música y voz")

    @Synchronized fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("catalog_placements_v1", Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (k, v) -> (v as? String)?.let { targets[k] = it } }
    }

    @Synchronized fun target(key: String): String? = if (targets.isEmpty()) null else targets[key]

    @Synchronized fun set(key: String, target: String?) {
        if (target == null) { targets.remove(key); prefs?.edit()?.remove(key)?.apply() }
        else { targets[key] = target; prefs?.edit()?.putString(key, target)?.apply() }
        revision.intValue++
    }

    @Synchronized fun isEmpty() = targets.isEmpty()
}
