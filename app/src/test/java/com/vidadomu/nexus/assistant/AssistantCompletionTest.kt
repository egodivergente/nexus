package com.vidadomu.nexus.assistant

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.vidadomu.nexus.agent.core.registry.ModelRef
import com.vidadomu.nexus.agent.core.registry.ModelSummary
import org.junit.Assert.*
import org.junit.Test

class AssistantCompletionTest {
    private val models = listOf(ModelSummary(ModelRef("atlas_cloud","bytedance/seedream-v5.0-pro"),"Seedream 5.0 Pro", modalities = setOf("IMAGE")),
        ModelSummary(ModelRef("runway","gen4"),"Runway Gen-4", modalities = setOf("VIDEO")))

    @Test fun insertsExactCatalogNameAtCursorWithoutLosingRemainingPrompt() {
        val value = TextFieldValue("Usa seed para Mateo", TextRange(8))
        val completion = requireNotNull(completeModels(value,models))
        val inserted = insertModel(value,completion,completion.models.single())
        assertEquals("Usa Seedream 5.0 Pro para Mateo",inserted.text)
        assertEquals(TextRange(20), inserted.selection)
    }
    @Test fun normalizesQuerySpacingWithoutDamagingOriginalText() {
        val value = TextFieldValue("Usa seed   5.0 pro", TextRange(18))
        val completion = requireNotNull(completeModels(value,models))
        assertEquals("Usa Seedream 5.0 Pro ",insertModel(value,completion,completion.models.single()).text)
    }
    @Test fun selectedTextAndUnknownModelDoNotCreateSuggestions() {
        assertNull(completeModels(TextFieldValue("seed",TextRange(0,4)),models))
        assertNull(completeModels(TextFieldValue("modelo inexistente"),models))
    }
    @Test fun matchesFamilyVersionAndVariantFromCatalog() {
        assertEquals("Seedream 5.0 Pro",requireNotNull(completeModels(TextFieldValue("seed 5.0 pro",TextRange(12)),models)).models.single().name)
    }
}
