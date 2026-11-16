package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.AttachedMedia
import com.vidadomu.nexus.core.model.MediaSlotType
import com.vidadomu.nexus.core.model.Modality
import org.junit.Assert.*
import org.junit.Test

/** Vidal 14-11: ArtBox/Lienzo references are removable for good and reset 24 h after entering a draft. */
class DraftMediaAgeTest {
    private val hour = 60L * 60 * 1000
    private fun image(id: String, file: String = id) =
        AttachedMedia(id, "file:///fixture/$file.png", "image/png", MediaSlotType.REFERENCE_IMAGE)
    private fun work(model: String, vararg media: AttachedMedia) = ModelWorkDraft(model, "p", media = media.toList())
    private fun draft(media: List<AttachedMedia>, retained: List<AttachedMedia> = emptyList()) =
        CreationDraft("m", Modality.IMAGE, "p", emptyMap(), media, retainedMedia = retained)

    @Test fun ageBelongsToTheFileAndSurvivesTracking() {
        val first = DraftMediaAge.track(emptyMap(), setOf("a"), now = 1_000)
        val later = DraftMediaAge.track(first, setOf("a", "b"), now = 5 * hour)
        assertEquals(1_000L, later["a"]); assertEquals(5 * hour, later["b"])
    }

    @Test fun filesNoDraftReferencesAreForgotten() {
        val ages = DraftMediaAge.track(mapOf("a" to 1L, "b" to 2L), setOf("b"), now = 10)
        assertEquals(setOf("b"), ages.keys)
    }

    @Test fun referencesExpireAtTwentyFourHoursNotBefore() {
        val ages = mapOf("old" to 0L, "fresh" to 2 * hour)
        assertEquals(emptySet<String>(), DraftMediaAge.expired(ages, now = 24 * hour - 1))
        assertEquals(setOf("old"), DraftMediaAge.expired(ages, now = 24 * hour))
    }

    @Test fun aClockSetBackNeverPostponesExpiryForever() {
        val ages = DraftMediaAge.track(mapOf("a" to 50 * hour), setOf("a"), now = 10 * hour)
        assertEquals(10 * hour, ages["a"])
    }

    @Test fun removalForgetsCopiesOfTheSameFileUnderAnotherId() {
        val drafts = listOf(work("a", image("1", "cat"), image("2", "dog")), work("b", image("9", "cat")))
        val gone = DraftMediaAge.forgottenUris(listOf(image("1", "cat")), stillAttached = emptyList())
        val pruned = DraftMediaAge.pruneDrafts(drafts, setOf("1"), gone)
        assertEquals(listOf("2"), pruned[0].media.map { it.id })
        assertTrue(pruned[1].media.isEmpty())
        // The next model switch merges drafts: the removed photo must not come back.
        val plan = ModelWorkDrafts.plan(pruned[0], pruned[1]) { true }
        assertEquals(listOf("2"), plan.media.map { it.id })
    }

    @Test fun aFileStillUsedInAnotherFieldIsNotForgottenByUri() {
        val start = image("start", "frame"); val end = image("end", "frame")
        val gone = DraftMediaAge.forgottenUris(listOf(start), stillAttached = listOf(end))
        assertTrue(gone.isEmpty())
        val pruned = DraftMediaAge.prune(listOf(start, end), setOf("start"), gone)
        assertEquals(listOf("end"), pruned.map { it.id })
    }

    @Test fun startupDropsExpiredReferencesFromEveryStoredDraft() {
        val old = image("old"); val fresh = image("fresh")
        val saved = mapOf(old.uriString to 0L, fresh.uriString to 20 * hour)
        val result = DraftMediaAge.startup(draft(listOf(old, fresh), retained = listOf(old)),
            listOf(work("x", old), work("y", fresh, old)), saved, now = 30 * hour)
        assertEquals(setOf(old.uriString), result.expired)
        assertEquals(listOf("fresh"), result.draft!!.media.map { it.id })
        assertTrue(result.draft!!.retainedMedia.isEmpty())
        assertTrue(result.workDrafts[0].media.isEmpty())
        assertEquals(listOf("fresh"), result.workDrafts[1].media.map { it.id })
        assertEquals(setOf(fresh.uriString), result.ages.keys)
    }

    @Test fun oneTimePurgeClearsEverythingStoredEvenIfFresh() {
        val a = image("a"); val b = image("b")
        val saved = mapOf(a.uriString to 29 * hour, b.uriString to 29 * hour)
        val result = DraftMediaAge.startup(draft(listOf(a), retained = listOf(b)), listOf(work("x", a, b)), saved,
            now = 30 * hour, purgeAll = true)
        assertTrue(result.draft!!.media.isEmpty() && result.draft!!.retainedMedia.isEmpty())
        assertTrue(result.workDrafts[0].media.isEmpty())
        assertTrue(result.ages.isEmpty())
    }

    @Test fun firstRunDatesTheExistingPileWithTheLegacyClock() {
        val pile = image("pile"); val today = image("today")
        val legacy = mapOf(pile.uriString to 0L, today.uriString to 29 * hour)
        val result = DraftMediaAge.startup(draft(listOf(pile, today)), emptyList(), savedAges = null,
            now = 30 * hour) { legacy.getValue(it) }
        assertEquals(setOf(pile.uriString), result.expired)
        assertEquals(listOf("today"), result.draft!!.media.map { it.id })
        assertEquals(29 * hour, result.ages[today.uriString])
    }

    @Test fun afterTheFirstRunUnknownFilesStartTheirDayNow() {
        val item = image("new")
        val result = DraftMediaAge.startup(draft(listOf(item)), emptyList(), savedAges = emptyMap(),
            now = 30 * hour) { 0L }
        assertTrue(result.expired.isEmpty()); assertEquals(30 * hour, result.ages[item.uriString])
    }

    @Test fun legacyClockReadsTheModificationTimeOfLocalCopies() {
        val file = kotlin.io.path.createTempFile("draft", ".png").toFile().apply { deleteOnExit(); setLastModified(123_000) }
        assertEquals(123_000L, DraftMediaAge.legacyFirstSeen(file.toURI().toString(), now = 999_000))
        assertEquals(999_000L, DraftMediaAge.legacyFirstSeen("https://cdn.example/x.png", now = 999_000))
        assertEquals(999_000L, DraftMediaAge.legacyFirstSeen("file:///no/such/file.png", now = 999_000))
    }
}
