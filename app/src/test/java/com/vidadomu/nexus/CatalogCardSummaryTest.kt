package com.vidadomu.nexus

import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.provider.atlas.AtlasModelMapper
import com.vidadomu.nexus.ui.models.*
import org.junit.Assert.*
import org.junit.Test

class CatalogCardSummaryTest {
    private val catalog = AtlasModelMapper.parseModelsResponse(javaClass.getResource("/native/catalog-public.json")!!.readText())

    @Test fun summariesNeverCutWordsAndFitTwoLines() {
        assertEquals("Crea una imagen.", fitCardSummary("Crea una imagen. Después añade detalles muy largos que no caben en dos líneas de tarjeta.", 40))
        assertEquals("Texto en imagen · Máscara", fitCardSummary("Texto en imagen · Máscara · Composición a partir de varias referencias", 30))
        assertNull(fitCardSummary("Unapalabraextremadamentelargaquenocabeenningunlado", 10))
    }

    @Test fun workflowListDropsWholeItems() {
        val all = CatalogWorkflow.entries.toList()
        val text = workflowSummary(all, 40)!!
        assertTrue(text.length <= 40)
        assertTrue(text.endsWith("más"))
        assertEquals(CatalogWorkflow.ANIMATE.summary, workflowSummary(listOf(CatalogWorkflow.ANIMATE)))
    }

    @Test fun everyFixtureFamilyGetsACompleteFunctionWithinTheLimit() {
        listOf(Modality.VIDEO, Modality.IMAGE).forEach { modality ->
            catalogSections(catalog.filter { modality in it.modalities }, emptySet()).forEach { section ->
                val prefix = pluralModelos(catalogModelGroups(section.models).size) + " · "
                val summary = catalogCardSummary(section.models, prefix)
                assertTrue("${section.family}: $summary", summary.length <= CATALOG_CARD_SUMMARY_MAX_CHARS)
                assertFalse(summary.endsWith("…"))
                assertTrue("${section.family} sin función", summary.length > prefix.length)
            }
        }
    }

    @Test fun activeFiltersAreNamedAndCounted() {
        val labels = activeCatalogFilterLabels("Atlas Cloud", CreationUse.VIDEO_IMAGE, true, Modality.VIDEO, imageReferences = true)
        assertEquals(listOf("Proveedor: Atlas Cloud", "Uso: Animar imagen", "Solo favoritos"), labels)
        assertEquals("Filtros (3)", filterButtonLabel(labels.size))
        assertEquals("Filtros", filterButtonLabel(0))
        assertEquals("Filtros y orden, 1 filtro activo", filterButtonDescription(1))
    }

    @Test fun imageReferencesCountOnlyInImageCatalog() {
        assertEquals(listOf("Con referencias"), activeCatalogFilterLabels(null, null, false, Modality.IMAGE, imageReferences = true))
        assertEquals(emptyList<String>(), activeCatalogFilterLabels(null, null, false, Modality.IMAGE, imageReferences = false))
        assertEquals(emptyList<String>(), activeCatalogFilterLabels(null, null, false, Modality.VIDEO, imageReferences = true))
        assertEquals(listOf("Solo favoritos"), activeCatalogFilterLabels(null, null, true, Modality.VIDEO, imageReferences = false))
    }

    @Test fun accessibleMoveMatchesDragPersistence() {
        val visible = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), moveCatalogItemBy(emptyList(), visible, "a", 1))
        assertEquals(listOf("a", "c", "b"), moveCatalogItemBy(emptyList(), visible, "c", -1))
        assertEquals(emptyList<String>(), moveCatalogItemBy(emptyList(), visible, "a", -1))
        assertEquals(listOf("x", "b", "a", "c"), moveCatalogItemBy(listOf("x"), visible, "b", -1))
    }
}
