package com.vidadomu.nexus

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.assistant.*
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Real Android WebView and stylus events; isolated offline QA package only. */
class ChatRichContentDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun ready() {
        check(BuildConfig.OFFLINE_QA)
        compose.runOnUiThread {compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
    }
    private fun web(view:View):WebView? = if(view is WebView) view else if(view is ViewGroup)
        (0 until view.childCount).firstNotNullOfOrNull {web(view.getChildAt(it))} else null
    private fun javascript(script:String):String {
        val value=AtomicReference<String?>(null)
        compose.runOnUiThread {web(compose.activity.window.decorView)!!.evaluateJavascript(script) {value.set(it)}}
        compose.waitUntil(5000) {value.get()!=null}
        return value.get()!!
    }
    @Test fun mermaidRendersAnActualSvgWithoutExternalNetwork() {
        compose.setContent {NexusTheme {Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ChatMarkdownView("```mermaid\nflowchart LR\nA[Idea] --> B[Resultado]\n```")
        }}}
        compose.onNodeWithText("Diagrama").assertIsDisplayed()
        var rendered=false
        repeat(12) {
            if(!rendered) {rendered=javascript("Boolean(document.querySelector('#diagram svg'))")=="true";if(!rendered) Thread.sleep(500)}
        }
        assertTrue("Mermaid did not produce SVG: "+javascript("document.getElementById('visual-error').textContent"),rendered)
        compose.onNodeWithContentDescription("Guardar contenido").assertIsDisplayed()
    }
    @Test fun htmlWidgetsExecuteJavascriptAndKeepTheirSourceAvailable() {
        val source="""<button id="demo" onclick="this.textContent='Hecho';parent.postMessage('Hecho','*')">Probar</button><script>addEventListener('message',()=>document.getElementById('demo').click())</script>"""
        compose.setContent {NexusTheme {Column(Modifier.fillMaxSize().safeDrawingPadding()) {ChatMarkdownView("```html\n$source\n```")}}}
        compose.onNodeWithText("Interactivo").assertIsDisplayed()
        var executed=false
        repeat(12) {if(!executed) {
            javascript("window.richResult=null;window.addEventListener('message',e=>window.richResult=e.data);document.getElementById('visual')?.contentWindow.postMessage('tap','*')")
            Thread.sleep(300)
            executed=javascript("window.richResult")=="\"Hecho\""
        }}
        assertTrue("The isolated HTML widget did not run its button handler",executed)
        compose.onNodeWithContentDescription("Ver código").performClick()
        compose.onNodeWithText(source).assertIsDisplayed()
    }
    @Test fun localMarkdownLinkOffersOpeningAndSavingTheRealFile() {
        val file=File(context.cacheDir,"rich-link-fixture.txt").apply {writeText("Contenido descargable")}
        compose.setContent {NexusTheme {ChatMarkdownView("[Documento de prueba](${android.net.Uri.fromFile(file)})")}}
        compose.onNodeWithText("Documento de prueba").performTouchInput {click()}
        compose.onNodeWithText("Guardar archivo").assertIsDisplayed()
        compose.onNodeWithText("Abrir").assertIsDisplayed()
        compose.onNodeWithText("Copiar enlace").assertIsDisplayed()
    }
    private fun stylus(x:Float,y:Float) {
        val now=SystemClock.uptimeMillis()
        val properties=arrayOf(MotionEvent.PointerProperties().apply {id=0;toolType=MotionEvent.TOOL_TYPE_STYLUS})
        listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP).forEachIndexed {index,action ->
            val coords=arrayOf(MotionEvent.PointerCoords().apply {this.x=x+index*25;this.y=y+index*12;pressure=if(action==MotionEvent.ACTION_UP) 0f else .6f;size=1f})
            MotionEvent.obtain(now,now+index*40,action,1,properties,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0).also {
                InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(it,true);it.recycle()
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
    @Test fun penDoesNotDrawUntilLongPressAndAnnotationsAreSelected() {
        val id="stylus-${java.util.UUID.randomUUID()}"
        val image=File(context.cacheDir,"$id.png")
        Bitmap.createBitmap(200,160,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.LTGRAY);image.outputStream().use {compress(Bitmap.CompressFormat.PNG,100,it)};recycle()}
        val store=ChatBoardStore(context)
        store.save(ChatBoard.arrange(id,listOf(Triple(android.net.Uri.fromFile(image).toString(),"Imagen de prueba",1.25f))))
        compose.setContent {NexusTheme {Column(Modifier.fillMaxSize().safeDrawingPadding()) {ChatBoardCard(id)}}}
        val node=compose.onNodeWithContentDescription("Tablero de imágenes; mantener pulsado para ver opciones")
        compose.waitUntil(5000) {compose.onAllNodesWithContentDescription("Tablero de imágenes; mantener pulsado para ver opciones").fetchSemanticsNodes().isNotEmpty()}
        val bounds=node.fetchSemanticsNode().boundsInRoot
        stylus(bounds.center.x,bounds.center.y)
        assertTrue(store.load(id)!!.images.all {it.strokes.isEmpty()})
        assertTrue(store.load(id)!!.strokes.isEmpty())
        node.performTouchInput {longClick()}
        compose.onNodeWithText("Anotaciones").performClick()
        stylus(bounds.center.x,bounds.center.y)
        compose.waitUntil(5000) {store.load(id)!!.images.any {it.strokes.isNotEmpty()} || store.load(id)!!.strokes.isNotEmpty()}
        compose.onNodeWithContentDescription("Terminar anotaciones").performClick()
    }
}
