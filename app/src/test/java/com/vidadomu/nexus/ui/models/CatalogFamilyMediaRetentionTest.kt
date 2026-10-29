package com.vidadomu.nexus.ui.models

import androidx.compose.runtime.saveable.SaverScope
import com.vidadomu.nexus.core.model.MediaSlotType
import com.vidadomu.nexus.ui.create.CanvasInputField
import org.junit.Assert.*
import org.junit.Test

class CatalogFamilyMediaRetentionTest {
    private fun image(id:String,mime:String="image/png")=CatalogMediaReference("content://fixture/$id",mime)
    private fun field(maximum:Int,mime:String="image/*")=
        CanvasInputField("images","Referencias",mime,false,maximum,MediaSlotType.REFERENCE_IMAGE)

    @Test fun switchingToASmallerVariantThenTextOnlyAndBackRetainsTheCompleteSelection() {
        val selection=(1..4).map {image("$it")}
        val original=selection.toList()
        val smaller=catalogMediaPartition(selection,field(1))
        assertEquals(listOf(selection.first()),smaller.active)
        assertEquals(selection.drop(1),smaller.retained)
        val textOnly=catalogMediaPartition(selection,null)
        assertTrue(textOnly.active.isEmpty())
        assertEquals(original,textOnly.retained)
        val back=catalogMediaPartition(selection,field(4))
        assertEquals(original,back.active)
        assertTrue(back.retained.isEmpty())
        assertEquals(original,selection)
    }

    @Test fun replacingASingleImageUsesTheNewOneAndRetainsEveryEarlierReference() {
        val original=listOf(image("first"),image("second"))
        val merged=addCatalogMediaReferences(original,listOf(image("third"),image("fourth")),singleInput=true)
        assertEquals(listOf("third","fourth","first","second"),merged.map {it.uriString.substringAfterLast('/')})
        val limited=catalogMediaPartition(merged,field(1))
        assertEquals(listOf(image("third")),limited.active)
        assertEquals(3,limited.retained.size)
        assertEquals(merged,catalogMediaPartition(merged,field(4)).active)
        assertEquals(2,original.size)
    }

    @Test fun mimeMismatchAndZeroCapacityAreRetainedWithoutChangingUriOrMime() {
        val selection=listOf(image("one"),image("two","image/jpeg"),image("video","video/mp4"))
        val png=catalogMediaPartition(selection,field(4,"image/png"))
        assertEquals(listOf(selection.first()),png.active)
        assertEquals(selection.drop(1),png.retained)
        val zero=catalogMediaPartition(selection,field(0))
        assertTrue(zero.active.isEmpty())
        assertEquals(selection,zero.retained)
    }

    @Test fun mergingTheSameInboxTwiceIsIdempotentAndKeepsTheOriginalOrder() {
        val inbox=listOf(image("one"),image("two"))
        val selection=mergeCatalogMediaReferences(inbox,listOf(image("three"),image("one")))
        assertEquals(selection,mergeCatalogMediaReferences(selection,inbox))
        assertEquals(listOf(image("one"),image("two"),image("three")),selection)
    }

    @Test fun aNewInboxImageHasPriorityAtASingleInputWithoutLosingThePreviousImage() {
        val previous=listOf(image("a"))
        val inbox=listOf(image("b"))
        val selection=applyCatalogInboxSelection(previous,inbox,alreadyHandled=emptyList())
        assertEquals(listOf(image("b"),image("a")),selection)
        val limited=catalogMediaPartition(selection,field(1))
        assertEquals(inbox,limited.active)
        assertEquals(previous,limited.retained)
        assertEquals(listOf(image("a")),previous)
    }

    @Test fun recreationDoesNotReplayAnInboxOverALaterGalleryChoiceOrRestoreAnExplicitlyRemovedFile() {
        val inbox=listOf(image("b"))
        val imported=applyCatalogInboxSelection(listOf(image("a")),inbox,emptyList())
        val edited=removeCatalogMediaReference(addCatalogMediaReferences(imported,listOf(image("c")),singleInput=true),image("b").uriString)
        val scope=object:SaverScope {override fun canBeSaved(value:Any)=true}
        val savedSelection=with(CatalogMediaSelectionSaver) {scope.save(edited)}!!
        val savedHandled=with(CatalogMediaSelectionSaver) {scope.save(inbox)}!!
        val restored=CatalogMediaSelectionSaver.restore(savedSelection)!!
        val handled=CatalogMediaSelectionSaver.restore(savedHandled)!!
        val reapplied=applyCatalogInboxSelection(restored,inbox.toList(),handled)
        assertSame(restored,reapplied)
        assertEquals(listOf(image("c"),image("a")),reapplied)
        assertEquals(listOf(image("c")),catalogMediaPartition(reapplied,field(1)).active)
        assertFalse(reapplied.any {it.uriString==image("b").uriString})
        assertSame(reapplied,applyCatalogInboxSelection(reapplied,inbox,handled))
    }

    @Test fun clearingInboxAfterUseKeepsThePoolAndAllowsANewExplicitSelectionToTakePriority() {
        val pool=listOf(image("c"),image("b"),image("a"))
        val handled=listOf(image("b"))
        val cleared=applyCatalogInboxSelection(pool,emptyList(),handled)
        assertEquals(pool,cleared)
        val explicit=applyCatalogInboxSelection(cleared,listOf(image("b")),alreadyHandled=emptyList())
        assertEquals(listOf(image("b"),image("c"),image("a")),explicit)
        assertEquals(pool.map {it.uriString}.toSet(),explicit.map {it.uriString}.toSet())
    }

    @Test fun explicitRemovalDeletesOnlyTheChosenReferenceAndPromotesTheNextActiveOne() {
        val original=listOf(image("one"),image("two"),image("three"))
        val selection=removeCatalogMediaReference(original,original.first().uriString)
        assertEquals(original.drop(1),selection)
        val limited=catalogMediaPartition(selection,field(1))
        assertEquals(listOf(original[1]),limited.active)
        assertEquals(listOf(original[2]),limited.retained)
        assertEquals(3,original.size)
    }

    @Test fun savedFamilySelectionRestoresAllResourcesIncludingThoseUnsupportedByTheCurrentVariant() {
        val selection=(1..75).map {image("$it",if(it%2==0) "image/jpeg" else "image/png")}
        val scope=object:SaverScope {override fun canBeSaved(value:Any)=true}
        val saved=with(CatalogMediaSelectionSaver) {scope.save(selection)}!!
        val restored=CatalogMediaSelectionSaver.restore(saved)!!
        assertEquals(selection,restored)
        assertEquals(74,catalogMediaPartition(restored,field(1)).retained.size)
        assertEquals(selection,catalogMediaPartition(restored,null).retained)
    }
}
