package com.vidadomu.nexus.ui.settings

import androidx.compose.runtime.saveable.SaverScope
import com.vidadomu.nexus.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class ThemeGalleryStateTest {
    private val scope=object:SaverScope {override fun canBeSaved(value:Any)=true}

    @Test fun recreationKeepsTheExactLegacySessionInsteadOfApplyingTheNewDefault() {
        val previous=AppearanceSelection(NexusAppearance.QUARTZ,AmbientMode.STATIC,.23f,true)
        val saved=with(AppearanceSelectionSaver) {scope.save(previous)}!!
        assertEquals(previous,AppearanceSelectionSaver.restore(saved))
        val preview=previous.copy(theme=NexusAppearance.EMERALD,ambientMode=AmbientMode.IMMERSIVE,intensity=.78f)
        val previewSaved=with(AppearanceSelectionSaver) {scope.save(preview)}!!
        assertEquals(preview,AppearanceSelectionSaver.restore(previewSaved))
        assertNotEquals(previous,preview)
        assertEquals(NexusAppearance.QUARTZ,previous.theme)
    }

    @Test fun corruptOrFutureSavedSessionsDoNotThrowIntoThePicker() {
        assertNull(AppearanceSelectionSaver.restore(listOf("FUTURE_THEME","SUBTLE",".5","false")))
        assertNull(AppearanceSelectionSaver.restore(listOf("AURORA")))
        assertNull(AppearanceSelectionSaver.restore(listOf("AURORA","SUBTLE",".5","invalid")))
    }

    @Test fun restoredAmbientIntensityIsFiniteAndBoundedForTheSliderAndRenderer() {
        fun restore(value:String)=AppearanceSelectionSaver.restore(listOf("AURORA","SUBTLE",value,"false"))!!
        assertEquals(.55f,restore("NaN").intensity,0f)
        assertEquals(1f,restore("4").intensity,0f)
        assertEquals(0f,restore("-1").intensity,0f)
    }
}
