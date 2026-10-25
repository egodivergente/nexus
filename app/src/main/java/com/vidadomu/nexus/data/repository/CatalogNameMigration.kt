package com.vidadomu.nexus.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * The ArtBox tool categories «Mejorar imagen» / «Mejorar vídeo» are now «Upscale imagen» / «Upscale vídeo»
 * (owner's decision, revision video 2 at 01:09, 25/10/2026). Their names are part of saved keys
 * (hidden models, labels, logos, order, choices). This one-time pass renames those keys and values so nothing the
 * user hid, renamed, reordered or re-logoed comes back or gets lost. Idempotent; never deletes unrelated data.
 */
internal object CatalogNameMigration {
    val RENAMES = mapOf("Mejorar imagen" to "Upscale imagen", "Mejorar vídeo" to "Upscale vídeo")
    private val STORES = listOf("catalog_customization_v1", "catalog_labels_v1", "catalog_grid_v2", "catalog_choices_v1", "family_logos")
    private const val FLAG = "migration_upscale_names_v1"

    /** Rewrites a single string. Only whole category names are replaced, never parts of model names. */
    fun rename(value: String): String = RENAMES.entries.fold(value) { text, (old, new) ->
        text.replace(Regex("(?<![\\p{L}])" + Regex.escape(old) + "(?![\\p{L}])"), new)
    }

    fun run(context: Context) {
        val marker = context.getSharedPreferences(STORES.first(), 0)
        if (marker.getBoolean(FLAG, false)) return
        STORES.forEach { name -> migrate(context.getSharedPreferences(name, 0)) }
        marker.edit().putBoolean(FLAG, true).commit()
    }

    internal fun migrate(prefs: SharedPreferences) {
        val edit = prefs.edit()
        var changed = false
        prefs.all.forEach { (key, value) ->
            val newKey = rename(key)
            val newValue: Any? = when (value) {
                is String -> rename(value)
                is Set<*> -> value.filterIsInstance<String>().map(::rename).toSet()
                else -> value
            }
            if (newKey == key && newValue == value) return@forEach
            if (newKey != key) edit.remove(key)
            when (newValue) {
                is String -> edit.putString(newKey, newValue)
                is Set<*> -> edit.putStringSet(newKey, newValue.filterIsInstance<String>().toSet())
                is Boolean -> edit.putBoolean(newKey, newValue)
                is Int -> edit.putInt(newKey, newValue)
                is Long -> edit.putLong(newKey, newValue)
                is Float -> edit.putFloat(newKey, newValue)
            }
            changed = true
        }
        if (changed) edit.commit()
    }
}
