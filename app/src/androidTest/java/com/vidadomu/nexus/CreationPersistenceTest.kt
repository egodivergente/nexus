package com.vidadomu.nexus

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.AtlasModelCatalog
import com.vidadomu.nexus.ui.create.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreationPersistenceTest {
    @Test fun draftsAndNamedPresetsSurviveNewPreferencesInstance() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val context=object:ContextWrapper(base) {
            override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("qa_persistence_fixture",Context.MODE_PRIVATE)
        }
        val prefs=context.getSharedPreferences("",0)
        prefs.edit().clear().commit()
        try {
            val model=AtlasModelCatalog.curatedModels.first().copy(parameters=listOf(ModelParameter("count","Cantidad",ParameterType.INTEGER,"1",nativeType="integer")))
            val media=AttachedMedia(uriString="file:///local/fixture.mp4",mimeType="video/mp4",slotType=MediaSlotType.VIDEO_INPUT)
            val draft=CreationDraft(model.id,Modality.VIDEO,"Prompt local de prueba",mapOf("count" to "2"),listOf(media))
            CreationPreferences(context).apply {saveDraft(draft,model.providerId);savePreset(model,"Mi prueba",draft.parameters)}
            val restored=CreationPreferences(context)
            assertEquals(draft,restored.draft());assertEquals("2",restored.values(model)["count"])
            assertEquals(mapOf("count" to "2"),restored.presets(model).single().values)
            restored.setRememberParameters(false)
            assertEquals("1",restored.values(model)["count"])
            restored.removePreset(model,"Mi prueba");assertTrue(restored.presets(model).isEmpty())
        } finally {prefs.edit().clear().commit()}
    }
}
