package com.vidadomu.nexus.ui.components

import com.vidadomu.nexus.R
import androidx.compose.ui.graphics.Color

/** Original artwork from the user's ALEXIA branding library. */
internal object AlexiaBrandAssets {
    fun resolve(text: String): BrandAsset.Resource? {
        val id = when {
            "nano-banana" in text || "nanobanana" in text -> R.drawable.alexia_nanobanana
            // Marcas concretas antes que su casa matriz (Seedream/Seedance antes que ByteDance, Veo/Gemini/Imagen/Flow antes que Google).
            "antigravity" in text -> R.drawable.alexia_antigravity
            "dola" in text -> R.drawable.alexia_dola
            "seedream" in text -> R.drawable.alexia_seedream
            "seedance" in text -> R.drawable.alexia_seedance
            "byteplus" in text -> R.drawable.alexia_bytedance
            "veo" in text -> R.drawable.alexia_veo
            "gemini" in text -> R.drawable.alexia_gemini
            "imagen" in text -> R.drawable.alexia_imagen
            "google flow" in text || "google_flow" in text || Regex("\\bflow\\b").containsMatchIn(text) -> R.drawable.alexia_flow
            "hi3d" in text -> R.drawable.alexia_hi3d
            "hidream" in text -> R.drawable.alexia_hidream
            "kwaipilot" in text -> R.drawable.alexia_kwaipilot
            "ltx" in text -> R.drawable.alexia_ltx
            "recraft" in text -> R.drawable.alexia_recraft
            Regex("\\bsync\\b").containsMatchIn(text) -> R.drawable.alexia_sync
            Regex("\\bdots\\b").containsMatchIn(text) -> R.drawable.alexia_dots
            "cohere" in text -> R.drawable.alexia_cohere
            "tencent" in text -> R.drawable.alexia_tencent
            Regex("\\budio\\b").containsMatchIn(text) -> R.drawable.alexia_udio
            "veed" in text -> R.drawable.alexia_veed
            "youchuan" in text -> R.drawable.alexia_youchuan
            "hailuo" in text -> R.drawable.alexia_hailuo
            "sora" in text -> R.drawable.alexia_sora
            "grok" in text -> R.drawable.alexia_grok
            "seedream" in text || "seedance" in text || "bytedance" in text -> R.drawable.alexia_bytedance
            "qwen" in text -> R.drawable.alexia_qwen
            Regex("\\bwan\\b").containsMatchIn(text) -> R.drawable.alexia_wan
            "kling" in text || "kwaivgi" in text -> R.drawable.alexia_kling
            "flux" in text || "black forest" in text -> R.drawable.alexia_flux
            "minimax" in text -> R.drawable.alexia_minimax
            "midjourney" in text -> R.drawable.alexia_midjourney
            "vidu" in text -> R.drawable.alexia_vidu
            "pixverse" in text -> R.drawable.alexia_pixverse
            "hunyuan" in text -> R.drawable.alexia_hunyuan
            "happyhorse" in text -> R.drawable.alexia_happyhorse
            "z-image" in text || "zimage" in text -> R.drawable.alexia_zimage
            "tripo" in text -> R.drawable.alexia_tripo
            "ideogram" in text -> R.drawable.alexia_ideogram
            "krea" in text -> R.drawable.alexia_krea
            "luma" in text -> R.drawable.alexia_luma
            "pika" in text -> R.drawable.alexia_pika
            "runway" in text -> R.drawable.alexia_runway
            "gpt" in text || "openai" in text -> R.drawable.alexia_openai
            "veo" in text || "gemini" in text || "google" in text || "imagen" in text -> R.drawable.alexia_google
            "claude" in text || "anthropic" in text -> R.drawable.alexia_claude
            "deepseek" in text -> R.drawable.alexia_deepseek
            "kimi" in text -> R.drawable.alexia_kimi
            "moonshot" in text -> R.drawable.alexia_moonshot
            "glm" in text -> R.drawable.alexia_glm
            "zai" in text || "z.ai" in text -> R.drawable.alexia_zai
            "xai" in text -> R.drawable.alexia_xai
            "suno" in text -> R.drawable.alexia_suno
            "elevenlabs" in text -> R.drawable.alexia_elevenlabs
            "nvidia" in text -> R.drawable.alexia_nvidia
            "stability" in text -> R.drawable.alexia_stability
            "microsoft" in text || "mai-image" in text -> R.drawable.alexia_microsoft
            "doubao" in text -> R.drawable.alexia_doubao
            "ernie" in text -> R.drawable.alexia_ernie
            "longcat" in text -> R.drawable.alexia_longcat
            // Proveedores y marcas añadidas después (Logos_Oficiales_Branding): solo cuando el modelo no tiene logo propio.
            "crun" in text -> R.drawable.alexia_crun
            "bedrock" in text || "amazon" in text -> R.drawable.alexia_bedrock
            "agnes" in text -> R.drawable.alexia_agnes
            "mistral" in text -> R.drawable.alexia_mistral
            "perplexity" in text || "sonar" in text -> R.drawable.alexia_perplexity
            "pruna" in text -> R.drawable.alexia_pruna
            "meshy" in text -> R.drawable.alexia_meshy
            Regex("\\bmeta\\b").containsMatchIn(text) || "llama" in text -> R.drawable.alexia_meta
            "atlascloud" in text || "atlas cloud" in text || "atlas_cloud" in text -> R.drawable.alexia_atlascloud
            else -> return null
        }
        // Logos oscuros o azules: fondo claro para que se lean.
        val light = id in setOf(R.drawable.alexia_pruna, R.drawable.alexia_meta, R.drawable.alexia_perplexity, R.drawable.alexia_crun)
        return BrandAsset.Resource(id, isBitmap = true, backgroundColor = if (light) Color(0xFFF1EFE9) else Color(0xFF141B29))
    }
}
