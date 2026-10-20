package com.vidadomu.nexus.sync

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.ui.theme.NexusTheme
import java.util.TimeZone
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic fixtures and a callback spy only: no NexusDriveSync, Drive, account or sync run. */
class DriveConflictReviewUiTest {
    @get:Rule val compose=createComposeRule()
    private val calls=mutableListOf<Pair<String,String>>()
    private val assetId="0d8c2f0e-7b0a-4c9e-9a51-2b7c51d7a001"
    private val at=1792491300000L

    private fun guard() {check(BuildConfig.OFFLINE_QA)}
    private fun version(id:String,device:String,value:JsonObject?)=buildJsonObject {put("id",id);put("device",device);put("at",at);put("value",value?:JsonNull)}
    private fun asset(name:String,uri:String,width:Int)=buildJsonObject {put("id",assetId);put("uri",uri);put("type","IMAGE");put("displayName",name);put("width",width);put("height",768);put("createdAt",at)}
    private fun assetConflict(index:Int)=buildJsonObject {
        put("key","assets:$assetId-$index")
        put("versions",buildJsonArray {
            add(version("a-$index","SM-S938B",asset("foto-$index.png","nexus-sync:a$index.png",1024)))
            add(version("b-$index","Windows",asset("foto-$index.png","file:///local/foto-$index.png",2048)))
        })
    }
    private fun removalConflict()=buildJsonObject {
        put("key","assets:$assetId-gone")
        put("versions",buildJsonArray {
            add(version("keep","SM-S938B",asset("borrador.png","nexus-sync:k.png",1024)))
            add(version("drop","Pixel 8 Pro",null))
        })
    }
    private fun summary(vararg conflicts:JsonObject,missing:Int=0)=DriveConflictPresentation.summarize(
        buildJsonObject {put("state","conflicts");put("missingMedia",missing);put("conflicts",JsonArray(conflicts.toList()))},
        zone=TimeZone.getTimeZone("UTC"),
    )
    private fun show(summary:DriveConflictSummary,pending:String?=null) {
        compose.setContent {NexusTheme {DriveConflictReview(summary,pending,onResolve={key,version->calls+=key to version})}}
    }

    @Test fun tappingTheNameOpensDetailAndNeverResolves() {
        guard()
        show(summary(assetConflict(1)))
        compose.onNodeWithText("Archivo «foto-1.png»").performClick()
        compose.onNodeWithTag("drive-conflict-detail").assertIsDisplayed()
        compose.onNodeWithText("Versión 1 de 2").assertIsDisplayed()
        compose.onNodeWithText("Resolución: 2048 × 768 · distinto").assertExists()
        compose.onNodeWithText("Original sin referencia en Drive").assertExists()
        compose.runOnIdle {assertTrue(calls.isEmpty())}
    }

    @Test fun resolvingNeedsButtonAndConfirmationAndCallsExactlyOnce() {
        guard()
        show(summary(assetConflict(1),assetConflict(2)))
        compose.onNodeWithText("Archivo «foto-1.png»").performClick()
        compose.onAllNodesWithText("Conservar esta versión")[1].performClick()
        compose.onNodeWithText("¿Conservar la versión 2?").assertIsDisplayed()
        compose.runOnIdle {assertTrue(calls.isEmpty())}
        compose.onNodeWithText("Sí, conservar esta").performClick()
        compose.onNodeWithText("Sí, conservar esta").assertDoesNotExist()
        compose.runOnIdle {assertEquals(listOf("assets:$assetId-1" to "b-1"),calls)}
        compose.onNodeWithText("Archivo «foto-2.png»").performClick()
        compose.onAllNodesWithText("Conservar esta versión").assertCountEquals(2).onFirst().assertIsNotEnabled()
        compose.onAllNodesWithText("Conservar esta versión")[1].assertIsNotEnabled()
        compose.runOnIdle {assertEquals(1,calls.size)}
    }

    @Test fun cancellingConfirmationLeavesEverythingUntouched() {
        guard()
        show(summary(assetConflict(1)))
        compose.onNodeWithText("Archivo «foto-1.png»").performClick()
        compose.onAllNodesWithText("Conservar esta versión").onFirst().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithTag("drive-conflict-detail").assertDoesNotExist()
        compose.runOnIdle {assertTrue(calls.isEmpty())}
    }

    @Test fun pendingChoiceFromManagerBlocksNewChoices() {
        guard()
        show(summary(assetConflict(1),assetConflict(2)),pending="assets:$assetId-1")
        compose.onNodeWithText("Elección guardada para Archivo «foto-1.png»",substring=true).assertExists()
        compose.onAllNodesWithTag("drive-conflict-row")[0].assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Elección pendiente de sincronizar"))
        compose.onNodeWithText("Archivo «foto-2.png»").performClick()
        compose.onNodeWithText("Hay otra elección pendiente",substring=true).assertExists()
        compose.onAllNodesWithText("Conservar esta versión").onFirst().assertIsNotEnabled()
        compose.runOnIdle {assertTrue(calls.isEmpty())}
    }

    @Test fun largeListsShowSummaryGroupsAndPages() {
        guard()
        show(summary(*(1..12).map(::assetConflict).toTypedArray()))
        compose.onNodeWithText("12 elementos con versiones distintas").assertExists()
        compose.onNodeWithText("Archivos (12)").assertExists()
        compose.onAllNodesWithTag("drive-conflict-row").assertCountEquals(5)
        compose.onNodeWithText("Mostrar más archivos (quedan 7)").performClick()
        compose.onAllNodesWithTag("drive-conflict-row").assertCountEquals(10)
        compose.onNodeWithText("Mostrar más archivos (quedan 2)").performClick()
        compose.onAllNodesWithTag("drive-conflict-row").assertCountEquals(12)
        compose.onNodeWithText("Mostrar más",substring=true).assertDoesNotExist()
    }

    @Test fun missingOriginalsAndRemovalAreExplicitAndIdsHiddenByDefault() {
        guard()
        show(summary(removalConflict(),missing=2))
        compose.onNodeWithTag("drive-missing-originals").assertExists()
        compose.onNodeWithText("Nexus no pudo leer 2 originales para copiarlos a Drive.").assertExists()
        compose.onNodeWithText(assetId,substring=true).assertDoesNotExist()
        compose.onAllNodesWithTag("drive-conflict-row").onFirst()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Button))
            .performClick()
        compose.onNodeWithText("Esta versión retira el elemento").assertExists()
        compose.onNodeWithText("Confirmar retirada").assertIsEnabled().performClick()
        compose.onNodeWithText("¿Retirar este elemento?").assertIsDisplayed()
        compose.onNodeWithText("Sí, retirar").performClick()
        compose.runOnIdle {assertEquals(listOf("assets:$assetId-gone" to "drop"),calls)}
    }

    @Test fun technicalIdsOnlyAppearOnRequest() {
        guard()
        show(summary(assetConflict(1)))
        compose.onNodeWithText("Archivo «foto-1.png»").performClick()
        compose.onNodeWithText("a-1",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Mostrar identificadores técnicos").performScrollTo().performClick()
        compose.onNodeWithText("Versión 1: a-1").assertExists()
        compose.runOnIdle {assertTrue(calls.isEmpty())}
    }
}
