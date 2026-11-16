package com.vidadomu.nexus.ui.create

import android.content.Context
import com.vidadomu.nexus.core.model.AttachedMedia
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Vidal 14-11: «siempre me encuentro subidos en el modelo decenas de fotos; por mucho que las borro no se
 * quitan; debería resetearse a las 24 h». Two rules keep the drafts of ArtBox and the Canvas clean:
 *
 * - Removing a file forgets it in every model draft, also the copies of the same file that entered under
 *   another id. Before, a model switch merged the drafts back together and the file returned.
 * - Every reference leaves the drafts 24 h after it first entered one. The age belongs to the file (its URI),
 *   so switching models does not renew it.
 *
 * Only references are dropped: the private copies are reclaimed by [PrivateMediaStorage] once nothing
 * protects them, and Library files are never touched.
 */
internal object DraftMediaAge {
    const val TTL_MILLIS = 24L * 60 * 60 * 1000

    /** First-seen time per referenced URI. URIs no draft references any more are forgotten. */
    fun track(ages: Map<String, Long>, referenced: Set<String>, now: Long,
        firstSeen: (String) -> Long = { now }): Map<String, Long> =
        referenced.associateWith { uri -> (ages[uri] ?: firstSeen(uri)).coerceAtMost(now) }

    fun expired(ages: Map<String, Long>, now: Long, ttl: Long = TTL_MILLIS): Set<String> =
        ages.filterValues { now - it >= ttl }.keys

    /** URIs that leave the drafts on an explicit removal: the removed files minus those the form still uses. */
    fun forgottenUris(removed: Collection<AttachedMedia>, stillAttached: Collection<AttachedMedia>): Set<String> =
        removed.map { it.uriString }.toSet() - stillAttached.map { it.uriString }.toSet()

    fun prune(media: List<AttachedMedia>, ids: Set<String> = emptySet(), uris: Set<String>): List<AttachedMedia> =
        if (ids.isEmpty() && uris.isEmpty()) media else media.filterNot { it.id in ids || it.uriString in uris }

    fun pruneDrafts(drafts: List<ModelWorkDraft>, ids: Set<String> = emptySet(), uris: Set<String>): List<ModelWorkDraft> =
        drafts.map { it.copy(media = prune(it.media, ids, uris)) }

    fun prune(draft: CreationDraft?, uris: Set<String>): CreationDraft? = draft?.copy(
        media = prune(draft.media, uris = uris), retainedMedia = prune(draft.retainedMedia, uris = uris))

    fun uris(draft: CreationDraft?, drafts: List<ModelWorkDraft>): Set<String> =
        (draft?.media.orEmpty() + draft?.retainedMedia.orEmpty() + drafts.flatMap { it.media }).map { it.uriString }.toSet()

    /**
     * Before this rule existed there was no record of when a file entered a draft. The first run dates each
     * local file by its modification time (a private copy is written when it is attached), so the pile that
     * already built up goes at once and anything attached today keeps the rest of its day.
     */
    fun legacyFirstSeen(uri: String, now: Long): Long {
        if (!uri.startsWith("file:")) return now
        val path = runCatching { java.net.URI(uri).path }.getOrNull() ?: return now
        val modified = java.io.File(path).lastModified()
        return if (modified > 0) modified else now
    }

    data class Startup(val draft: CreationDraft?, val workDrafts: List<ModelWorkDraft>, val ages: Map<String, Long>,
        val expired: Set<String>)

    /** Drops expired references from the persisted drafts before the form is built from them. */
    fun startup(draft: CreationDraft?, workDrafts: List<ModelWorkDraft>, savedAges: Map<String, Long>?, now: Long,
        purgeAll: Boolean = false, legacy: (String) -> Long = { legacyFirstSeen(it, now) }): Startup {
        val ages = track(savedAges.orEmpty(), uris(draft, workDrafts), now, if (savedAges == null) legacy else { _ -> now })
        // purgeAll: una sola vez, vacía de golpe todo lo acumulado antes de esta regla (solo referencias, no archivos).
        val expired = if (purgeAll) ages.keys else expired(ages, now)
        if (expired.isEmpty()) return Startup(draft, workDrafts, ages, expired)
        return Startup(prune(draft, expired), pruneDrafts(workDrafts, uris = expired), ages - expired, expired)
    }
}

/** First-seen times of draft references, beside the drafts themselves. Never deletes files. */
class DraftMediaAgeStore(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_draft_media_age_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** Null until the first save: that run dates the references already stored (see [DraftMediaAge.legacyFirstSeen]). */
    fun load(): Map<String, Long>? = prefs.getString(KEY, null)?.let { raw ->
        runCatching { json.decodeFromString<Map<String, Long>>(raw) }.getOrDefault(emptyMap())
    }

    /** Limpieza única de lo acumulado antes de la regla de 24 h; devuelve true la primera vez. */
    fun consumePurge(): Boolean {
        if (prefs.getBoolean(PURGED, false)) return false
        prefs.edit().putBoolean(PURGED, true).apply()
        return true
    }

    fun save(ages: Map<String, Long>) { prefs.edit().putString(KEY, json.encodeToString(ages)).apply() }

    private companion object { const val KEY = "ages"; const val PURGED = "purged_v2" }
}
