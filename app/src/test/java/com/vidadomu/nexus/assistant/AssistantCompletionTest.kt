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
        val value = TextFieldValue("Usa @seed para Mateo", TextRange(9))
        val completion = requireNotNull(completeModels(value,models))
        val inserted = insertModel(value,completion,completion.models.single())
        assertEquals("Usa Seedream 5.0 Pro para Mateo",inserted.text)
        assertEquals(TextRange(20), inserted.selection)
    }
    @Test fun normalizesQuerySpacingWithoutDamagingOriginalText() {
        val text = "Usa @seed   5.0 pro"
        val value = TextFieldValue(text, TextRange(text.length))
        val completion = requireNotNull(completeModels(value,models))
        assertEquals("Usa Seedream 5.0 Pro ",insertModel(value,completion,completion.models.single()).text)
    }
    @Test fun selectedTextAndUnknownModelDoNotCreateSuggestions() {
        assertNull(completeModels(TextFieldValue("@seed",TextRange(0,5)),models))
        assertNull(completeModels(TextFieldValue("@modelo inexistente"),models))
        assertNull(completeModels(TextFieldValue("seed",TextRange(4)),models))
        assertNull(completeModels(TextFieldValue("email@seed",TextRange(10)),models))
    }
    @Test fun matchesFamilyVersionAndVariantFromCatalog() {
        assertEquals("Seedream 5.0 Pro",requireNotNull(completeModels(TextFieldValue("@seed 5.0 pro",TextRange(13)),models)).models.single().name)
    }

    @Test fun shortFamilyQueryShowsCreativeFamiliesBeforeTextVariants() {
        val textVariants = (1..7).map { ModelSummary(ModelRef("atlas_cloud","doubao-seed-$it"),"Doubao Seed $it",family="Doubao",modalities=setOf("TEXT")) }
        val videos = (1..7).map { ModelSummary(ModelRef("atlas_cloud","seedance-$it"),"Seedance $it",family="Seedance",modalities=setOf("VIDEO")) }
        val image = ModelSummary(ModelRef("atlas_cloud","seedream-5-pro"),"Seedream 5.0 Pro",family="Seedream",modalities=setOf("IMAGE"))
        val choices = requireNotNull(completeModels(TextFieldValue("@seed",TextRange(5)),textVariants+videos+image)).models
        assertTrue(choices.take(2).any { it.ref == image.ref })
        assertTrue(choices.take(2).any { it.family == "Seedance" })
        assertEquals(6,choices.size)
    }
}
