package com.vidadomu.nexus

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.ui.navigation.NotificationNavigation
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import com.vidadomu.nexus.settings.RuntimePreferences
import org.junit.Rule
import org.junit.Test

/** Uses the actual MainActivity graph, not a separate test-only graph. No provider requests. */
class Tanda1NavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NexusApplication

    private var previousRules: String? = null

    @Before fun disableProjectChooserOnlyInQa() {
        check(BuildConfig.OFFLINE_QA)
        val rules = RuntimePreferences.get(app)
        previousRules = rules.export()
        rules.set("startup.enabled", "false")
    }

    @After fun restoreQaPreferences() {
        previousRules?.let { RuntimePreferences.get(app).import(it) }
    }

    @Test fun notificationIdentityIsDistinctAndPayloadIsConsumedOnce() {
        val first = NotificationNavigation.intent(app, "Aa")
        val second = NotificationNavigation.intent(app, "BB")
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertFalse(first.filterEquals(second))
        assertTrue(first.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(first.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertEquals("Aa", NotificationNavigation.takeJobId(first))
        assertFalse(first.hasExtra(NotificationNavigation.EXTRA_JOB_ID))
        assertNull(NotificationNavigation.takeJobId(first))
        assertNull(NotificationNavigation.takeJobId(Intent().putExtra(NotificationNavigation.EXTRA_JOB_ID, " ")))
        val info = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, info.launchMode)
    }

    @Test fun notificationDoesNotReplayAfterLeavingResultAndRecreating() {
        check(BuildConfig.OFFLINE_QA)
        ActivityScenario.launch<MainActivity>(NotificationNavigation.intent(app, "tanda1-missing-job")).use { scenario ->
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Generación no encontrada").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Atrás").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Generación no encontrada").fetchSemanticsNodes().isEmpty() }
            scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithText("Generación no encontrada").assertDoesNotExist()
            scenario.onActivity { assertFalse(it.intent.hasExtra(NotificationNavigation.EXTRA_JOB_ID)) }
        }
    }

    @Test fun warmNotificationUsesSameActivityAndStillOpensNewResult() {
        check(BuildConfig.OFFLINE_QA)
        ActivityScenario.launch<MainActivity>(NotificationNavigation.intent(app, "tanda1-first")).use { scenario ->
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Generación no encontrada").fetchSemanticsNodes().isNotEmpty() }
            lateinit var original: MainActivity
            scenario.onActivity {
                original = it
                it.startActivity(NotificationNavigation.intent(it, "tanda1-second"))
            }
            compose.waitForIdle()
            scenario.onActivity {
                assertSame(original, it)
                assertEquals("tanda1-second", it.intent.data?.lastPathSegment)
                assertFalse(it.intent.hasExtra(NotificationNavigation.EXTRA_JOB_ID))
            }
        }
    }
}
