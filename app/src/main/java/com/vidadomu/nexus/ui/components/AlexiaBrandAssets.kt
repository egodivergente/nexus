package com.vidadomu.nexus.ui.components

import com.vidadomu.nexus.R
import androidx.compose.ui.graphics.Color

/** Original artwork from the user's ALEXIA branding library. */
internal object AlexiaBrandAssets {
    fun resolve(text: String): BrandAsset.Resource? {
        val id = when {
            "nano-banana" in text || "nanobanana" in text -> R.drawable.alexia_nanobanana
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
            else -> return null
        }
        return BrandAsset.Resource(id, isBitmap = true, backgroundColor = Color(0xFF141B29))
    }
}
