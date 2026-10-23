package com.vidadomu.nexus

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.assistant.*
import com.vidadomu.nexus.settings.RuntimePreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real Activity + private synthetic fixtures. Never submits generation requests. */
class MasterWorkspaceDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
    private fun enter() {
        check(BuildConfig.OFFLINE_QA)
        compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        runBlocking {app.ready.await()}
        compose.waitUntil(20000) {compose.onAllNodesWithText("Abrir último proyecto").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Abrir último proyecto").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Agente").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun fiveDestinationsAndLongFlowPromptRemainAccessible() {
        enter()
        listOf("Agente","ArtBox","Lanzadera","Flow","Biblioteca").forEach {compose.onNodeWithContentDescription(it).assertExists()}
        compose.onNodeWithContentDescription("ArtBox").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Herramientas").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Herramientas").assertIsDisplayed()
        compose.onNodeWithContentDescription("Flow").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Prompt de creación").fetchSemanticsNodes().isNotEmpty()}
        val prompt="Texto íntegro 😀 ".repeat(450)
        compose.onNodeWithContentDescription("Prompt de creación").performTextReplacement(prompt)
        compose.onNodeWithContentDescription("Prompt de creación").assertTextContains(prompt)
        compose.onNodeWithText("Máximo no verificado",substring=true).assertExists()
    }
    @Test fun conversationsKeepProjectIsolationAndDrafts() {
        enter()
        val vm=app.assistantViewModel()
        val runtime=vm.runtime
        val suffix=System.currentTimeMillis().toString()
        val a=runBlocking {runtime.assets.createProject("QA A $suffix")}
        val b=runBlocking {runtime.assets.createProject("QA B $suffix")}
        compose.runOnUiThread {vm.selectProject(a.id)}
        compose.waitUntil(10000) {vm.ui.value.activeProjectId==a.id && !vm.ui.value.thinking}
        val a1=vm.ui.value.conversationId
        app.getSharedPreferences("assistant_draft_v1",0).edit().putString("conversation:$a1","Borrador A1").commit()
        compose.runOnUiThread {vm.newConversation()}
        compose.waitUntil(10000) {vm.ui.value.conversationId!=a1 && !vm.ui.value.thinking}
        val a2=vm.ui.value.conversationId
        compose.runOnUiThread {vm.selectProject(b.id)}
        compose.waitUntil(10000) {vm.ui.value.activeProjectId==b.id && !vm.ui.value.thinking}
        val b1=vm.ui.value.conversationId
        assertNotEquals(a1,b1);assertNotEquals(a2,b1)
        compose.runOnUiThread {vm.selectProject(a.id)}
        compose.waitUntil(10000) {vm.ui.value.activeProjectId==a.id && !vm.ui.value.thinking}
        assertEquals(a2,vm.ui.value.conversationId)
        compose.runOnUiThread {vm.openConversation(a1)}
        compose.waitUntil(10000) {vm.ui.value.conversationId==a1 && !vm.ui.value.thinking}
        assertEquals("Borrador A1",app.getSharedPreferences("assistant_draft_v1",0).getString("conversation:$a1",null))
        assertTrue(vm.conversations().all {it.projectId==a.id})
    }
    @Test fun multipleSharedImagesAreCopiedBeforeChoosingDestinationAndRuntimeRulesApply() {
        enter()
        val uris=(0..4).map {index->
            val file=File(app.cacheDir,"master-share-${System.nanoTime()}-$index.png")
            val bitmap=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.rgb(index*40,100,220-index*30))}
            file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();Uri.fromFile(file)
        }
        val shares=PendingShares(app)
        val batch=runBlocking {shares.receive(Intent(Intent.ACTION_SEND_MULTIPLE).apply {type="image/*";putParcelableArrayListExtra(Intent.EXTRA_STREAM,ArrayList(uris))})}
        assertEquals(5,batch.files.size);assertTrue(batch.ready)
        assertEquals(5,shares.pending().first {it.id==batch.id}.files.size)
        assertTrue(batch.files.all {it.attachment!!.uri.startsWith("file:") && File(Uri.parse(it.attachment.uri).path!!).length()>0})
        val rules=RuntimePreferences.get(app)
        rules.set("startup.idleMinutes","5");assertEquals(5,rules.int("startup.idleMinutes"))
        val exported=rules.export();rules.set("startup.idleMinutes","240");rules.import(exported)
        assertEquals(5,rules.int("startup.idleMinutes"))
        rules.set("startup.idleMinutes",null)
        shares.consumed(batch.id)
    }

    @Test fun returningToAConversationRestoresItsScrollPosition() {
        enter()
        val vm=app.assistantViewModel()
        val id="qa_scroll_${System.nanoTime()}"
        val project=vm.ui.value.activeProjectId!!
        val items=(0..79).map {ChatItem.User("$id-$it","Fila $it · Mensaje local largo para comprobar que el historial conserva la posición al cambiar de conversación.",emptyList())}
        val saved=SavedAssistantChat(items,emptyMap(),projectId=project,conversationId=id)
        File(app.filesDir,"assistant/conversations/$id.json").apply {parentFile!!.mkdirs();writeText(Json.encodeToString(saved))}
        val prefs=app.getSharedPreferences("assistant_draft_v1",0)
        prefs.edit().putInt("scroll:$id",20).putInt("offset:$id",15).commit()
        compose.runOnUiThread {vm.openConversation(id)}
        compose.waitUntil(10000) {vm.ui.value.conversationId==id && !vm.ui.value.thinking}
        compose.waitForIdle()
        assertEquals(20,prefs.getInt("scroll:$id",-1))
        compose.runOnUiThread {vm.newConversation()}
        compose.waitUntil(10000) {vm.ui.value.conversationId!=id && !vm.ui.value.thinking}
        compose.runOnUiThread {vm.openConversation(id)}
        compose.waitUntil(10000) {vm.ui.value.conversationId==id && !vm.ui.value.thinking}
        compose.waitForIdle()
        assertEquals(20,prefs.getInt("scroll:$id",-1))
    }

    @Test fun chatTabsStayHiddenAndClosingKeepsSavedConversationAndDraft() {
        enter()
        compose.onNodeWithContentDescription("Agente").performClick()
        val vm=app.assistantViewModel()
        RuntimePreferences.get(app).set("chat.tabsVisible","false")
        val id="qa_close_${System.nanoTime()}"
        val saved=SavedAssistantChat(listOf(ChatItem.User("$id-message","Chat local para cerrar",emptyList())),emptyMap(),projectId=vm.ui.value.activeProjectId,conversationId=id)
        File(app.filesDir,"assistant/conversations/$id.json").writeText(Json.encodeToString(saved))
        val prefs=app.getSharedPreferences("assistant_draft_v1",0)
        prefs.edit().putString("conversation:$id","Conservar borrador").commit()
        compose.runOnUiThread {vm.openConversation(id)}
        compose.waitUntil(10000) {vm.ui.value.conversationId==id && !vm.ui.value.thinking}
        compose.onNodeWithContentDescription("Cerrar conversación Chat local para cerrar").assertDoesNotExist()
        compose.onNodeWithContentDescription("Conversaciones y proyectos").performClick()
        compose.onNodeWithContentDescription("Cerrar conversación Chat local para cerrar").performClick()
        compose.waitUntil(10000) {vm.ui.value.conversationId!=id && !vm.ui.value.thinking}
        assertTrue(id in ConversationTabs(app).closed())
        assertEquals("Chat local para cerrar",(AssistantChatStore(app).conversation(id)!!.items.single() as ChatItem.User).text)
        assertEquals("Conservar borrador",prefs.getString("conversation:$id",null))
    }

    @Test fun libraryCanSelectMoreThanOneProject() {
        enter()
        val prefs=app.getSharedPreferences("library_actions_v1",0)
        prefs.edit().remove("project_filter").commit()
        compose.onNodeWithContentDescription("Biblioteca").performClick()
        compose.onNodeWithContentDescription("Filtrar por proyectos").performClick()
        val projects=app.assistantViewModel().runtime.assets.projects().take(2)
        assertEquals(2,projects.size)
        projects.forEach {compose.onNodeWithText(it.name).performClick()}
        assertEquals(projects.map {it.id}.toSet(),prefs.getStringSet("project_filter",emptySet()))
    }

    @Test fun voiceNoteCanRecordPreviewAndAttachWithoutSending() {
        enter()
        compose.onNodeWithContentDescription("Agente").performClick()
        val vm=app.assistantViewModel()
        compose.runOnUiThread {vm.newConversation()}
        compose.waitUntil(10000) {!vm.ui.value.thinking && vm.ui.value.items.isEmpty()}
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName,android.Manifest.permission.RECORD_AUDIO)
        compose.onNodeWithContentDescription("Grabar audio o dictar").performClick()
        compose.onNodeWithText("Grabar",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Grabando…").assertExists()
        android.os.SystemClock.sleep(1500)
        compose.onNodeWithText("Detener").performClick()
        compose.onNodeWithText("Escuchar").performClick()
        compose.onNodeWithText("Adjuntar",useUnmergedTree=true).performClick()
        compose.waitUntil(15000) {!vm.ui.value.attaching && vm.ui.value.pendingAttachments.any {it.mimeType.startsWith("audio/")}}
        val audio=vm.ui.value.pendingAttachments.first {it.mimeType.startsWith("audio/")}
        assertTrue(File(Uri.parse(audio.uri).path!!).length()>1000)
        assertTrue((audio.durationSec ?: 0.0)>0)
    }

}
