package com.vidadomu.nexus.sync

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.ui.theme.NexusTheme
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** OfflineQA only. One synthetic conflict and a callback spy; the sync manager is never created. */
class DriveConflictSelectionUiTest {
    @get:Rule val compose=createComposeRule()
    private val calls=mutableListOf<Pair<String,String>>()
    private val key="projects:fixture-project"

    private fun version(id:String,device:String,name:String)=buildJsonObject {
        put("id",id);put("device",device);put("at",1792491300000L)
        put("value",buildJsonObject {put("id","fixture-project");put("name",name);put("createdAt",1)})
    }
    private fun setUp() {
        check(BuildConfig.OFFLINE_QA)
        val status=buildJsonObject {
            put("state","conflicts")
            put("conflicts",buildJsonArray {add(buildJsonObject {
                put("key",key);put("versions",buildJsonArray {add(version("left","SM-S938B","Corto"));add(version("right","Windows","Corto final"))})
            })})
        }
        val summary=DriveConflictPresentation.summarize(status)
        compose.setContent {NexusTheme {DriveConflictReview(summary,null,onResolve={k,v->calls+=k to v},
            modifier=Modifier.verticalScroll(rememberScrollState()))}}
    }

    @Test fun selectingTheConflictOnlyOpensDetail() {
        setUp()
        compose.onNodeWithText("Proyecto «Corto»").performClick()
        compose.onNodeWithTag("drive-conflict-detail").assertIsDisplayed()
        compose.onNodeWithText("Nombre: Corto final · distinto").assertExists()
        compose.onAllNodesWithText("Conservar esta versión").assertCountEquals(2)
        compose.runOnIdle {assertTrue(calls.isEmpty())}
    }

    @Test fun choosingAVersionWaitsForExplicitConfirmation() {
        setUp()
        compose.onNodeWithText("Proyecto «Corto»").performClick()
        compose.onAllNodesWithText("Conservar esta versión").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("¿Conservar la versión 1?").assertIsDisplayed()
        compose.runOnIdle {assertTrue(calls.isEmpty())}
        compose.onNodeWithText("Sí, conservar esta").performClick()
        compose.onNodeWithTag("drive-conflict-detail").assertDoesNotExist()
        compose.runOnIdle {assertEquals(listOf(key to "left"),calls)}
    }

    @Test fun cancellingTheConfirmationNeverResolves() {
        setUp()
        compose.onNodeWithText("Proyecto «Corto»").performClick()
        compose.onAllNodesWithText("Conservar esta versión")[1].performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("¿Conservar la versión 2?").assertDoesNotExist()
        compose.onNodeWithTag("drive-conflict-detail").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithTag("drive-conflict-detail").assertDoesNotExist()
        compose.runOnIdle {assertTrue(calls.isEmpty())}
    }
}
