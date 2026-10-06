package com.vidadomu.nexus.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.vidadomu.nexus.R
import com.vidadomu.nexus.ui.theme.*

sealed class BrandAsset {
    data class Resource(
        @DrawableRes val resId: Int,
        val isBitmap: Boolean = false,
        val tint: Color = Color.Unspecified,
        val backgroundColor: Color = Color(0xFF161D2B)
    ) : BrandAsset()

    data class Monogram(
        val initials: String,
        val backgroundColor: Color = Color(0xFF161D2B),
        val textColor: Color = TextPrimary
    ) : BrandAsset()
}

object ProviderBrandRegistry {

    fun resolve(
        organization: String? = null,
        family: String? = null,
        modelId: String? = null
    ): BrandAsset {
        val org = (organization ?: "").trim().lowercase()
        val fam = (family ?: "").trim().lowercase()
        val id = (modelId ?: "").trim().lowercase()
        val combined = "$org $fam $id"

        // 1. Google / Gemini / Imagen / Veo / Nano Banana
        if (combined.contains("google") || combined.contains("gemini") || combined.contains("imagen") ||
            combined.contains("veo") || combined.contains("nano") || combined.contains("banana")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_google,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF181F30)
            )
        }

        // 2. Kling AI / Kwai / KwaiVGI
        if (combined.contains("kling") || combined.contains("kwai") || combined.contains("kwaivgi") || combined.contains("kwaipilot")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_kling,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF28190B)
            )
        }

        // 3. Alibaba Wan / WanX / Wan-3.0 / wan-prime
        if (combined.contains("wanx") || combined.contains("wan-") || combined.contains("/wan") ||
            combined.contains("alibaba-wan") || combined.contains("wan 3") || combined.contains("wan3")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_wan,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF221435)
            )
        }
        if (combined.contains("qwen") || combined.contains("alibaba")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_qwen,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF241038)
            )
        }

        // 4. ByteDance / Doubao / Seedance / Seedream
        if (combined.contains("bytedance") || combined.contains("seedance") || combined.contains("seedream") || combined.contains("doubao")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_bytedance,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF112033)
            )
        }

        // 5. MiniMax / Hailuo / H3
        if (combined.contains("minimax") || combined.contains("hailuo")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_minimax,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF221428)
            )
        }

        // 6. OpenAI / ChatGPT / Sora / GPT Image / DALL-E
        if (combined.contains("openai") || combined.contains("gpt-") || combined.contains("dall-e") || combined.contains("sora") || combined.contains("chatgpt")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_openai,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF0E2822)
            )
        }

        // 7. Anthropic / Claude
        if (combined.contains("anthropic") || combined.contains("claude")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_claude,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF281C0E)
            )
        }

        // 8. Black Forest Labs / Flux
        if (combined.contains("black forest") || combined.contains("bfl") || combined.contains("flux")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_flux,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF281018)
            )
        }

        // 9. Midjourney
        if (combined.contains("midjourney")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_vendor_midjourney,
                isBitmap = false,
                tint = Color.White,
                backgroundColor = Color(0xFF141926)
            )
        }

        // 10. DeepSeek
        if (combined.contains("deepseek")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_deepseek,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF08273D)
            )
        }

        // 11. xAI / Grok
        if (combined.contains("xai") || combined.contains("grok")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_vendor_xai,
                isBitmap = false,
                tint = Color.White,
                backgroundColor = Color(0xFF1A1A1D)
            )
        }

        // 12. Microsoft / Azure
        if (combined.contains("microsoft") || combined.contains("azure") || combined.contains("phi-")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_microsoft,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF141D2B)
            )
        }

        // 13. Vidu / Shengshu
        if (combined.contains("vidu") || combined.contains("shengshu")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_vidu,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF0B2138)
            )
        }

        // 14. PixVerse
        if (combined.contains("pixverse")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_pixverse,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF281024)
            )
        }

        // 15. Zhipu / GLM / CogVideo / CogView
        if (combined.contains("zhipu") || combined.contains("glm") || combined.contains("cogvideo") || combined.contains("cogview")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_vendor_zhipu,
                isBitmap = false,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF0D2530)
            )
        }

        // 16. Tencent / Hunyuan
        if (combined.contains("tencent") || combined.contains("hunyuan")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_vendor_tencent,
                isBitmap = false,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF0E2238)
            )
        }

        // 17. Suno
        if (combined.contains("suno")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_suno,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF2E0F1E)
            )
        }

        // 18. Nvidia / Cosmos / Edify
        if (combined.contains("nvidia") || combined.contains("cosmos") || combined.contains("edify")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_nvidia,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF132B18)
            )
        }

        // 19. Ideogram
        if (combined.contains("ideogram")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_ideogram,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF1C1C24)
            )
        }

        // 20. ElevenLabs
        if (combined.contains("elevenlabs") || combined.contains("eleven")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_elevenlabs,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF1E212B)
            )
        }

        // 21. Runway
        if (combined.contains("runway") || combined.contains("gen-2") || combined.contains("gen-3") || combined.contains("gen-4")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_runway,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF1E2430)
            )
        }

        // 22. Stability AI / Stable Diffusion
        if (combined.contains("stability") || combined.contains("stable diffusion") || combined.contains("sdxl") || combined.contains("sd3")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_stability,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF231238)
            )
        }

        // 23. Recraft
        if (combined.contains("recraft")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_vendor_recraft,
                isBitmap = false,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF331215)
            )
        }

        // 24. Luma
        if (combined.contains("luma") || combined.contains("dream machine")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_luma,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF0A2933)
            )
        }

        // 25. Pika
        if (combined.contains("pika")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_logo_pika,
                isBitmap = true,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF2D1E0B)
            )
        }

        // 26. Udio
        if (combined.contains("udio")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_vendor_udio,
                isBitmap = false,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF2E1022)
            )
        }

        // 27. 3D / Tripo / Trellis
        if (combined.contains("tripo") || combined.contains("trellis") || combined.contains("3d")) {
            return BrandAsset.Resource(
                resId = R.drawable.ic_vendor_3d,
                isBitmap = false,
                tint = Color.Unspecified,
                backgroundColor = Color(0xFF1C2233)
            )
        }

        // 28. Neutral Monogram Fallback
        val cleanName = when {
            !organization.isNullOrBlank() && !organization.equals("other", ignoreCase = true) && !organization.equals("others", ignoreCase = true) -> organization.trim()
            !family.isNullOrBlank() -> family.trim()
            !modelId.isNullOrBlank() -> modelId.trim().substringBefore('/').substringBefore('-')
            else -> "AI"
        }

        val initials = cleanName.split(" ", "-", "_")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { cleanName.take(2).uppercase() }

        return BrandAsset.Monogram(
            initials = initials,
            backgroundColor = Color(0xFF182030),
            textColor = TextSecondary
        )
    }
}
