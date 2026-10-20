package com.vidadomu.nexus.ui.models

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.core.model.ModelCapability
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.provider.atlas.AtlasCloudProvider
import com.vidadomu.nexus.provider.atlas.AtlasPublishedPrice
import com.vidadomu.nexus.ui.theme.NexusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.concurrent.thread

/** In-memory fixture only: the offlineQa build blocks the price fetch, so the tariff arrives solely via the seeded cache. */
class PublishedPriceReactivityTest {
    @get:Rule val compose = createComposeRule()

    private fun snapshot(id: String) = """[{"id":"$id","usd":0.05,"unit":"second","checkedAt":1792400000000}]"""
    private fun count(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().size

    @Test fun cachedTariffArrivingLaterReplacesMissingCostOnlyForItsModel() {
        val id = "qa-reactivity/motion-${System.nanoTime()}"
        val model = NexusModel(id, "Reactividad QA", AtlasCloudProvider.PROVIDER_ID, organization = "Estudio QA",
            modalities = listOf(Modality.VIDEO), capabilities = listOf(ModelCapability.IMAGE_TO_VIDEO), parameters = emptyList())
        val group = CatalogModelGroup("qa-reactivity-${System.nanoTime()}", "Reactividad QA", listOf(model))
        val actions = mutableListOf<String>()
        compose.setContent {
            NexusTheme {
                ModelOptionsScreen(group, emptySet(),
                    toggleFavorite = { actions += "favorite" }, onUse = { actions += "use" }, modifier = Modifier.fillMaxSize())
            }
        }
        val missingCost = hasText("Coste por") and hasText(MISSING_FACT)
        val perSecond = hasText("Coste por") and hasText("segundo")
        compose.waitForIdle()
        assertEquals(1, count(missingCost))

        thread { AtlasPublishedPrice.seedSnapshot(snapshot("$id-other")) }.join()
        compose.waitForIdle()
        assertEquals(1, count(missingCost))
        assertEquals(0, count(perSecond))

        thread { AtlasPublishedPrice.seedSnapshot(snapshot(id)) }.join()
        compose.waitUntil(5_000) { count(perSecond) == 1 }
        assertEquals(0, count(missingCost))
        compose.waitUntil(5_000) { count(hasText("Desde ", substring = true)) > 0 }
        assertTrue("Acción real sobre el modelo: $actions", actions.isEmpty())
    }
}
