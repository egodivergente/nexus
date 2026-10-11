package com.vidadomu.nexus

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.assistant.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProjectMediaPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun importedPhotoSurvivesOriginalRemovalAndReusingDoesNotCopyAgain() = runBlocking {
        check(BuildConfig.OFFLINE_QA)
        val app = ApplicationProvider.getApplicationContext<NexusApplication>()
        val runtime = AssistantRuntime.get(app)
        val project = runtime.assets.createProject("Photo picker QA ${UUID.randomUUID()}")
        val original = File(app.cacheDir, "photo-library-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val saved = AgentMediaStore.persist(app, Uri.fromFile(original), "image/png", project.id)
        assertNotEquals(Uri.fromFile(original).toString(), saved.uri)
        assertTrue(original.delete())
        assertTrue(File(Uri.parse(saved.uri).path!!).isFile)
        val again = AgentMediaStore.persist(app, Uri.parse(saved.uri), null, project.id)
        assertEquals(saved.uri, again.uri)
        assertEquals(saved.assetId, again.assetId)
        assertEquals(1, runtime.assets.snapshot().assets.count { it.projectId == project.id })
        // Reopen the actual persisted index, rather than checking only the in-memory snapshot.
        val indexFile = File(app.filesDir, "assistant/index.json")
        val persisted = if (indexFile.isFile) AssetIndex(indexFile) else error("Update test path to the runtime index")
        assertEquals(saved.uri, persisted.asset(saved.assetId!!)!!.uri)
    }

    @Test fun savedPhotosCanBeSelectedAndDeviceImportRemainsAvailable() {
        check(BuildConfig.OFFLINE_QA)
        val photo = Asset("one", "file:///fixture.png", AssetType.IMAGE, displayName = "Retrato guardado", mimeType = "image/png", projectId = "p", createdAt = 1)
        var used = emptyList<Asset>()
        var imported = false
        compose.setContent { MaterialTheme {
            ProjectMediaPickerSheet(listOf(photo), "p", "Proyecto QA", listOf("image/*"), 1, false, {}, { imported = true }, { used = it })
        } }
        compose.onNodeWithText("Usar seleccionadas · 0").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Retrato guardado").performClick()
        compose.onNodeWithText("Usar seleccionadas · 1").performClick()
        compose.runOnIdle { assertEquals(listOf(photo), used) }
        compose.onNodeWithText("Importar desde el dispositivo").performClick()
        compose.runOnIdle { assertTrue(imported) }
    }

    @Test fun managementExportsAllDespiteSearchAndConfirmsDeletionOfSelection() {
        check(BuildConfig.OFFLINE_QA)
        val photos = (1..3).map { Asset("$it", "file:///$it.png", AssetType.IMAGE, displayName = "Imagen $it",
            mimeType = "image/png", projectId = "p", createdAt = it.toLong()) }
        var exported = emptyList<Asset>()
        var deleted = emptyList<Asset>()
        compose.setContent { MaterialTheme {
            ProjectMediaPickerSheet(photos, "p", "Proyecto QA", listOf("image/*"), 1, false, {}, {}, {},
                managementOnly = true, onDelete = { deleted = it }, onExport = { exported = it })
        } }
        compose.onNodeWithText("Seleccionar todas").performClick()
        compose.onNodeWithText("ZIP de seleccionadas · 3").performClick()
        compose.runOnIdle { assertEquals(3, exported.size) }
        compose.onNodeWithText("Desmarcar todas").performClick()
        compose.onNodeWithContentDescription("Imagen 1").performClick()
        compose.onNodeWithText("ZIP de seleccionadas · 1").performClick()
        compose.runOnIdle { assertEquals(listOf(photos[0]), exported) }
        compose.onNodeWithText("Buscar archivos").performTextInput("Imagen 1")
        compose.onNodeWithText("ZIP de todas las imágenes · 3").performClick()
        compose.runOnIdle { assertEquals(3, exported.size) }
        compose.onNodeWithText("Borrar · 1").performClick()
        compose.runOnIdle { assertTrue(deleted.isEmpty()) }
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertTrue(deleted.isEmpty()) }
        compose.onNodeWithText("Borrar · 1").performClick()
        compose.onNodeWithText("Borrar", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(listOf(photos[0]), deleted) }
    }
}
