package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.ui.create.*
import org.junit.Assert.*
import org.junit.Test

class BatchReadinessTest {
    private val model = NexusModel("test", "Test", "atlas_cloud", organization = "Test", modalities = listOf(Modality.IMAGE), capabilities = emptyList(), parameters = emptyList())
    @Test fun unknownCostAndOneInvalidModelDoNotBlockIndependentReadyOrder() {
        val ready = ComparisonEntry(model, prepared = PreparedComparison(CreateUiState(),emptyList()), cost = null)
        val invalid = ComparisonEntry(model.copy(id="other"),error="Prompt no válido")
        val state = ComparisonState(entries=listOf(ready,invalid),phase=ComparisonPhase.REVIEW)
        assertTrue(state.canSend)
        assertNull(state.total)
        assertFalse(state.copy(entries=listOf(invalid)).canSend)
    }
}
