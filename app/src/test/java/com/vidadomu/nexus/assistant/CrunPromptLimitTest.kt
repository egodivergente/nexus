package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.provider.crun.CrunCatalogSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrunPromptLimitTest {
    private fun model(extra: String) = CrunCatalogSnapshot.decode(Json.parseToJsonElement(
        """{"providerModelId":"qwen-image-edit","title":"Q","output":"IMAGE","required":["img_urls","prompt"],"properties":{"img_urls":{"type":"array","maxItems":3,"items":{"type":"string"}},"prompt":{"type":"string"$extra}}}"""))!!

    @Test fun `el limite de prompt del contrato de Crun se aplica al preparar el encargo`() {
        assertEquals(800, ProviderPromptLimits.forModel(model(""","maxLength":800"""))?.maximum)
    }

    @Test fun `sin maxLength en el contrato el limite sigue desconocido`() {
        assertNull(ProviderPromptLimits.forModel(model("")))
    }
}