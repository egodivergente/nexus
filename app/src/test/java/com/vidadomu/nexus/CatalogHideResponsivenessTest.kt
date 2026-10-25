package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.data.repository.*
import com.vidadomu.nexus.provider.atlas.AtlasModelMapper
import com.vidadomu.nexus.ui.models.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression for the stuck «Quitar Gemini 2.5 Flash» / «Quitar Nano Banana 2 Lite» dialog (Pixel 8 Pro, 25/10/2026):
 * hiding re-ran the regex classifiers for every model × every hidden key on the main thread.
 */
class CatalogHideResponsivenessTest {
    private val models = AtlasModelMapper.parseModelsResponse(javaClass.getResource("/native/catalog-public.json")!!.readText())

    /** The previous definition, kept here as the reference semantics. */
    private fun referenceVisible(model: NexusModel, hidden: Set<String>): Boolean =
        familyVisibilityKey(catalogFamily(model)) !in hidden && groupVisibilityKey(model) !in hidden && routeVisibilityKey(model) !in hidden &&
            toolCategory(model)?.let { toolVisibilityKey(it) !in hidden && familyVisibilityKey(it) !in hidden } != false

    private fun everyKey(): Set<String> = models.flatMap { catalogVisibilityKeys(it).keys }.toSet()

    @Test fun visibilityMatchesThePreviousRuleForEveryKey() {
        for (key in everyKey()) for (model in models)
            assertEquals("$key / ${model.id}", referenceVisible(model, setOf(key)), isCatalogModelVisible(model, setOf(key)))
    }

    @Test fun capturedRoutesMatchThePreviousRule() {
        val hidden = everyKey().shuffled(java.util.Random(7)).take(25).toSet()
        val captured = captureHiddenRoutes(models + models, hidden, emptyMap())
        for (key in hidden) {
            val expected = models.filterNot { referenceVisible(it, setOf(key)) }.map(::routeVisibilityKey).toSet()
            assertEquals(key, expected, captured.getValue(key))
        }
        assertEquals(models.filter { referenceVisible(it, hidden) }, visibleCatalog(models, hidden, captured))
    }

    @Test fun previouslyCapturedRoutesSurviveARefresh() {
        val key = groupVisibilityKey(models.first())
        val captured = captureHiddenRoutes(emptyList(), setOf(key), mapOf(key to setOf("route:gone:model")))
        assertTrue("route:gone:model" in captured.getValue(key))
    }

    @Test fun hidingStaysFastWithALargeCatalogueAndManyHiddenModels() {
        // ~10× the public catalogue and 60 hidden entries: far beyond today's real use on the phone.
        val large = (0 until 10).flatMap { copy -> models.map { it.copy(id = it.id + "#$copy") } }
        val hidden = large.flatMap { catalogVisibilityKeys(it).keys }.distinct().shuffled(java.util.Random(3)).take(60).toSet()
        captureHiddenRoutes(large, hidden, emptyMap()) // warm caches like a running app
        val started = System.nanoTime()
        repeat(5) {
            val captured = captureHiddenRoutes(large + large, hidden, emptyMap())
            visibleCatalog(large, hidden, captured)
        }
        val perHideMs = (System.nanoTime() - started) / 5 / 1_000_000
        assertTrue("Ocultar un modelo tarda $perHideMs ms con ${large.size} modelos", perHideMs < 400)
    }

    @Test fun groupTitlesAreStableAcrossCalls() {
        for (model in models) assertEquals(modelGroupTitle(model), modelGroupTitle(model.copy()))
    }
}
