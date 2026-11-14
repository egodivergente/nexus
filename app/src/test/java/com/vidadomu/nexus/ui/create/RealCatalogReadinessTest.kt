package com.vidadomu.nexus.ui.create

import com.vidadomu.nexus.core.model.FormContract
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.provider.crun.CrunModelCatalog
import com.vidadomu.nexus.provider.crun.CrunTextCatalog
import com.vidadomu.nexus.provider.runway.RunwayContracts
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nada del catálogo real debe quedar imposible de enviar por una entrada «no representada»: eso manda al
 * usuario a los parámetros a arreglar algo que no existe. Con fixtures no se veía; con los modelos de
 * verdad, 24 de las 64 rutas de Runway estaban bloqueadas porque declaran el prompt como «prompt»
 * mientras el motor solo contaba el nombre del contrato («promptText»).
 */
class RealCatalogReadinessTest {
    private fun unrepresented(models: List<NexusModel>): List<String> = models.mapNotNull { model ->
        val state = CreateUiState(selectedModel = model, prompt = "Un plano de prueba", schemaReady = true,
            selectedModality = model.modalities.first(), parameters = FormContract.restoredValues(model, emptyMap()))
        val message = creationReadiness(state).message
        if (message?.contains("aún no representada") == true) "${model.id}: $message" else null
    }

    @Test fun ningunaRutaDeRunwayQuedaBloqueadaPorUnaEntradaNoRepresentada() {
        val models = RunwayContracts.models
        assertTrue("el catálogo de Runway no debería estar vacío", models.size > 20)
        val blocked = unrepresented(models)
        assertTrue(blocked.joinToString("\n"), blocked.isEmpty())
    }

    @Test fun ningunaRutaDeCrunQuedaBloqueadaPorUnaEntradaNoRepresentada() {
        val blocked = unrepresented(CrunModelCatalog.models)
        assertTrue(blocked.joinToString("\n"), blocked.isEmpty())
    }

    /** Suno en modo simple se envía con solo la descripción: el título es cosa del modo «custom». */
    @Test fun sunoEnModoSimpleSeEnviaConSoloLaDescripcion() {
        val models = CrunTextCatalog.suno
        assertTrue("el catálogo de Suno no debería estar vacío", models.isNotEmpty())
        for (model in models) {
            val state = CreateUiState(selectedModel = model, prompt = "Una canción tranquila de guitarra",
                schemaReady = true, selectedModality = model.modalities.first(),
                parameters = FormContract.restoredValues(model, emptyMap()))
            val readiness = creationReadiness(state)
            assertTrue("${model.id}: ${readiness.message}", readiness.canSubmit)
        }
    }
}
