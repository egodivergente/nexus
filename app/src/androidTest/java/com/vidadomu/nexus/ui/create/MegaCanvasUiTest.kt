package com.vidadomu.nexus.ui.create

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.components.StudioToolChip
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Offline fixtures. Never launch production activity or a generation. */
class MegaCanvasUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun guard()=check(BuildConfig.OFFLINE_QA)
    private fun screenshot(name:String) {
        val bmp=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val dir=File(context.getExternalFilesDir(null),"mega-ux").apply {mkdirs()}
        File(dir,"$name.png").outputStream().use {bmp.compress(Bitmap.CompressFormat.PNG,100,it)}
        bmp.recycle()
    }
    private fun model(id:String,modality:Modality)=NexusModel(id,id,"fixture",organization="Estudio",
        modalities=listOf(modality),capabilities=emptyList(),parameters=emptyList())
    @Test fun pickerStartsContextualAndClearRecoversWholeCatalog() {
        guard();val edit=model("Imagen compatible",Modality.IMAGE).copy(firstFrameField="image")
        val text=model("Solo texto",Modality.IMAGE); val video=model("Vídeo de prueba",Modality.VIDEO)
        val ref=AttachedMedia("reference","file:///fixture.png","image/png",MediaSlotType.PRIMARY_IMAGE)
        var chosen:String?=null
        compose.setContent {NexusTheme {
            ModelPickerSheet(listOf(edit,text,video),edit,Modality.IMAGE,listOf(ref),
                (context.applicationContext as NexusApplication).currencyManager,{chosen=it.id},{})
        }}
        compose.onNodeWithText("1 modelo · 1 familia").assertIsDisplayed()
        compose.onNodeWithText("Buscar modelo, familia o tarea").performTextInput("zzzz")
        compose.onNodeWithText("Ver todo el catálogo").performScrollTo().performClick()
        compose.onNodeWithText("3 modelos · 1 familia").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Vídeo de prueba").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(video.id,chosen)}
        screenshot("selector-contextual")
    }
    @Test fun toolbarActionsRemainReachableAtTwoHundredPercent() {
        guard();var tapped by mutableStateOf(false)
        compose.setContent {NexusTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                Column(Modifier.widthIn(max=411.dp)) {StudioToolRow {
                    StudioToolChip("Inspirar") {}
                    StudioToolChip("Parámetros") {}
                    StudioToolChip("Coste") {}
                    StudioToolChip("Más") {tapped=true}
                };Text(if(tapped) "Abierto" else "Cerrado")}
            }
        }}
        listOf("Inspirar","Parámetros","Coste","Más").forEach {compose.onNodeWithText(it).assertIsDisplayed()}
        compose.onNodeWithText("Más").performClick()
        compose.onNodeWithText("Abierto").assertIsDisplayed()
        screenshot("herramientas-200")
    }
}
