package com.vidadomu.nexus

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.assistant.PendingShares
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Own the shared-intent Activity directly; a second singleTask launch invalidates an older ActivityScenario. */
class SharedIntentDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun fiveAndroidSharedImagesSurviveActivityRecreationBeforeDestination() {
        check(BuildConfig.OFFLINE_QA)
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication
        runBlocking {app.ready.await()}
        val uris=(0..4).map {index->
            val file=File(app.cacheDir,"intent-share-${System.nanoTime()}-$index.png")
            val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.rgb(index*45,180,250-index*40))}
            file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            FileProvider.getUriForFile(app,app.packageName+".fileprovider",file)
        }
        val intent=Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type="image/png";setClass(app,MainActivity::class.java)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM,ArrayList(uris))
            clipData=android.content.ClipData.newRawUri("QA images",uris.first()).apply {uris.drop(1).forEach {addItem(android.content.ClipData.Item(it))}}
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        ActivityScenario.launch<MainActivity>(intent).use {scenario->
            scenario.onActivity {it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
            compose.waitUntil(20000) {PendingShares(app).pending().any {it.files.size==5 && it.ready}}
            scenario.recreate()
            scenario.onActivity {it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
            compose.waitUntil(10000) {compose.onAllNodesWithText("Compartir en…").fetchSemanticsNodes().isNotEmpty()}
            val before=app.assistantViewModel().ui.value.pendingAttachments.size
            compose.onNodeWithContentDescription("Continuar en el proyecto actual").performClick()
            compose.waitUntil(10000) {app.assistantViewModel().ui.value.pendingAttachments.size==before+5 && PendingShares(app).pending().isEmpty()}
            assertTrue(PendingShares(app).pending().isEmpty())
            val attachments=app.assistantViewModel().ui.value.pendingAttachments
            assertEquals(attachments.size,attachments.map {it.id}.distinct().size)
            assertTrue(attachments.all {it.uri.startsWith("file:")})
        }
    }
}
