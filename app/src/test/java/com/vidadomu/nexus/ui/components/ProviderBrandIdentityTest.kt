package com.vidadomu.nexus.ui.components

import com.vidadomu.nexus.R
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ProviderBrandIdentityTest {
    private fun resId(org: String? = null, family: String? = null, id: String? = null) =
        (ProviderBrandRegistry.resolve(org, family, id) as? BrandAsset.Resource)?.resId

    private fun entries(resource: String): List<JsonObject> {
        val root = Json.parseToJsonElement(javaClass.getResource(resource)!!.readText())
        val array = (root as? JsonArray) ?: root.jsonObject["data"]!!.jsonArray
        return array.map { it.jsonObject }
    }
    private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull

    @Test fun `DeepSeek del catalogo real con familia OpenAI-Compatible muestra su logo`() {
        val entry = entries("/native/catalog-public.json").first { it.text("model") == "deepseek-ai/deepseek-v4.1-flash" }
        val id = entry.text("model")!!
        val family = entry.text("familyDisplayName")!!
        val org = entry.text("organization")
        assertTrue("el dato real contiene la subcadena que antes ganaba", "openai" in family.lowercase())
        // Expresión anterior: id y familia concatenados en una sola búsqueda por subcadena.
        assertEquals(R.drawable.alexia_openai, AlexiaBrandAssets.resolve("${id.lowercase()} ${family.lowercase()}")?.resId)
        assertEquals(R.drawable.alexia_deepseek, resId(org, family, id))
    }

    @Test fun `ids mixtos openai y deepseek resuelven por el nombre del modelo`() {
        assertEquals(R.drawable.alexia_deepseek, resId("OpenAI", "OpenAI-compatible", "openai/deepseek-v3"))
        assertEquals(R.drawable.alexia_deepseek, resId(null, null, "openai/deepseek-ai/deepseek-r1"))
        assertEquals(R.drawable.alexia_deepseek, resId("OpenAI", null, "deepseek-ai/deepseek-v3"))
        assertEquals(R.drawable.alexia_deepseek, resId(null, "DeepSeek", null))
        assertEquals(R.drawable.alexia_openai, resId(null, null, "openai/gpt-image-2"))
        assertEquals(R.drawable.alexia_openai, resId("DeepSeek", null, "deepseek/gpt-oss-120b"))
    }

    @Test fun `marcas existentes se conservan`() {
        assertEquals(R.drawable.alexia_kling, resId("Kling", "Kling Video API", "kwaivgi/kling-v3.0-pro/image-to-video"))
        assertEquals(R.drawable.alexia_nanobanana, resId("Google", null, "google/nano-banana-2/edit"))
        assertEquals(R.drawable.alexia_seedream, resId("ByteDance", null, "bytedance/seedream-v5.0-pro/text-to-image"))
        assertEquals(R.drawable.alexia_veo, resId("Google", null, "runway/veo3.1/i2v"))
        assertEquals(R.drawable.alexia_kling, resId(null, null, "crun/kling-v3-std"))
        assertEquals(R.drawable.alexia_sora, resId("OpenAI", null, "openai/sora-2/text-to-video"))
        assertEquals(R.drawable.alexia_atlascloud, resId("Atlas Cloud", null, null))
        assertTrue(ProviderBrandRegistry.resolve(null, "Desconocida", "acme/foo") is BrandAsset.Monogram)
    }

    @Test fun `cada cambio frente al orden anterior lo explica un segmento del propio id`() {
        val all = entries("/native/catalog-public.json") + entries("/atlas-catalog-2026-09-22.json")
        var changed = 0
        all.forEach { entry ->
            val id = entry.text("model") ?: entry.text("id") ?: return@forEach
            val family = entry.text("familyDisplayName") ?: entry.text("familyName")
            val org = entry.text("organization")
            val before = (AlexiaBrandAssets.resolve("${id.trim().lowercase()} ${(family ?: "").trim().lowercase()}")
                ?: AlexiaBrandAssets.resolve((org ?: "").trim().lowercase()))?.resId
            val after = resId(org, family, id)
            if (before != null && before != after) {
                changed++
                val segments = id.lowercase().split('/').mapNotNull { AlexiaBrandAssets.resolve(it)?.resId }
                assertTrue("$id cambió sin evidencia en su id", after in segments)
            }
        }
        assertTrue("el fixture debe incluir los casos DeepSeek", changed > 0)
    }
}
