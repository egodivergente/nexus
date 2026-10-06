package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.NexusModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Public launch month of the named base model, not the provider listing or a later feature. */
internal data class ModelRelease(val month: String, val source: String) {
    val label: String get() = "Salida · " + YearMonth.parse(month)
        .format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("es"))).replace(".", "")
}
private data class ReleaseRule(val pattern: Regex, val release: ModelRelease)
private fun release(pattern: String, month: String, source: String) =
    ReleaseRule(Regex("(?:^|/)" + pattern + "(?=/|$)", RegexOption.IGNORE_CASE), ModelRelease(month, source))

// Reviewed against first-party announcements on 2026-09-26. Deliberately no prefix/family fallback.
private val releases = listOf(
    release("kling-video-o1", "2025-12", "https://ir.kuaishou.com/node/11101/pdf"),
    release("kling-v?3(?:[.]0)?-(?:std|pro)", "2026-02", "https://en.prnasia.com/releases/global/kling-ai-launches-3-0-model-ushering-in-an-era-where-everyone-can-be-a-director-521494.shtml"),
    release("kling-video-o3-(?:std|pro)", "2026-02", "https://en.prnasia.com/releases/global/kling-ai-launches-3-0-model-ushering-in-an-era-where-everyone-can-be-a-director-521494.shtml"),
    release("kling-v2[.]6-pro", "2025-12", "https://ir.kuaishou.com/news-releases/news-release-details/kling-ai-launches-video-26-model-simultaneous-audio-visual"),
    release("kling-v2[.]5-turbo-pro", "2025-09", "https://ir.kuaishou.com/news-releases/news-release-details/kling-ai-launches-25-turbo-video-model-industry-leading"),
    release("kling-v2[.]1-(?:i2v|t2v)-(?:standard|pro|master)", "2025-05", "https://ir.kuaishou.com/system/files-encrypted/nasdaq_kms/assets/2025/09/04/5-40-53/2025%20Interim%20Report%20E.pdf"),
    release("kling-v2[.]0-(?:i2v|t2v)-master", "2025-04", "https://www1.hkexnews.hk/listedco/listconews/sehk/2025/0527/2025052700279.pdf"),
    release("wan-2[.]6", "2025-12", "https://www.alibabacloud.com/en/press-room/alibaba-unveils-wan2-6-series-enabling-everyone?_p_lc=1"),
    release("seedance-2[.]0", "2026-02", "https://seed.bytedance.com/en/blog/seedance-2-0-official-launch"),
    release("seedance-2[.]5", "2026-07", "https://seed.bytedance.com/en/blog/one-take-creation-flexible-referencing-introducing-seedance-2-5"),
    release("seedance-v1[.]5-pro", "2025-12", "https://arxiv.org/abs/2512.13507"),
    release("hailuo-02", "2025-06", "https://www.minimax.io/news/minimax-hailuo-02"),
    release("hailuo-2[.]3", "2025-10", "https://www.minimax.io/news/minimax-hailuo-23"),
    release("h3", "2026-07", "https://www.minimax.io/blog/minimax-h3"),
    release("veo3[.]1(?:-fast)?", "2025-10", "https://developers.googleblog.com/introducing-veo-3-1-and-new-creative-capabilities-in-the-gemini-api/"),
    release("nano-banana", "2025-08", "https://blog.google/products-and-platforms/products/gemini/updated-image-editing-model/"),
    release("nano-banana-pro", "2025-11", "https://blog.google/innovation-and-ai/products/nano-banana-pro/"),
    release("nano-banana-2", "2026-02", "https://blog.google/innovation-and-ai/technology/ai/nano-banana-2/"),
    release("seedream-v4(?:[.]0)?", "2025-09", "https://seed.bytedance.com/en/blog/seedream-4-0-officially-released-beyond-drawing-into-imagination"),
    release("flux-2-(?:pro|flex)", "2025-11", "https://bfl.ai/blog/flux-2"),
    release("qwen-image", "2025-08", "https://qwenlm.github.io/blog/qwen-image/")
)

internal fun modelRelease(model: NexusModel): ModelRelease? {
    val id = model.providerModelId ?: model.id
    // Explicit provider aliases; motion-control, turbo and lite remain separate releases.
    val canonical = when (id) {
        "bytedance/seedream-4" -> "bytedance/seedream-v4"
        "kling/v3" -> "kling-v3-pro"
        "kling/v3-omni-i2v", "kling/v3-omni-r2v", "kling/v3-omni-t2v" -> "kling-video-o3-pro"
        "kling/v2-6" -> "kling-v2.6-pro"
        "kling/v2-5-turbo-pro" -> "kling-v2.5-turbo-pro"
        "kling/v2-1", "kling/v2-1-master" -> "kling-v2.1-i2v-master"
        "kling/o1-i2v", "kling/o1-r2v", "kling/o1-t2v" -> "kling-video-o1"
        "google/veo3-1-i2v", "google/veo3-1-t2v" -> "google/veo3.1"
        "google/veo3-1-fast-i2v", "google/veo3-1-fast-r2v", "google/veo3-1-fast-t2v" -> "google/veo3.1-fast"
        else -> id
    }
    return releases.firstOrNull { it.pattern.containsMatchIn(canonical) }?.release
}

internal fun groupReleaseLabel(routes: List<NexusModel>): String {
    val dates = routes.map(::modelRelease)
    val known = dates.filterNotNull().map { it.month }.distinct()
    if (known.isEmpty()) return "Fecha no verificada"
    // Mixed versions/unknown variants must not borrow the earliest or latest known launch.
    if (known.size > 1 || dates.any { it == null }) return "Fechas según variante"
    return dates.filterNotNull().first().label
}
