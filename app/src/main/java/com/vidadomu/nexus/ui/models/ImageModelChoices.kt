package com.vidadomu.nexus.ui.models

import com.vidadomu.nexus.core.model.*

/** Product priorities affect presentation only. Capabilities and native keys remain authoritative. */
internal object ImageModelChoices {
    val families = listOf("Seedream", "GPT Image", "FLUX", "Midjourney", "Nano Banana")
    fun priority(family: String) = families.indexOf(family).let { if (it < 0) families.size else it }
    fun acceptsReferences(model: NexusModel): Boolean = if (model.contractEndpoint != null) {
        model.promptRequired && model.contractMedia.any { it.mime.startsWith("image/") && !it.key.contains("mask", true) }
    } else CatalogWorkflow.EDIT_IMAGE in catalogWorkflows(model)

    fun acceptsText(model: NexusModel): Boolean = if (model.contractEndpoint != null) {
        model.promptRequired && model.contractMedia.none { it.required }
    } else CatalogWorkflow.CREATE_IMAGE in catalogWorkflows(model)

    private fun version(model: NexusModel): Double = Regex("(?:seedream|gpt[-_]image|flux|nano[-_]banana|gemini[-_]image)[-_ .]*v?([0-9]+(?:[.][0-9]+)?)")
        .find((model.providerModelId ?: model.id).lowercase())?.groupValues?.get(1)?.toDoubleOrNull() ?: model.versionNumeric

    val comparator: Comparator<NexusModel> = compareBy<NexusModel> { priority(catalogFamily(it)) }
        .thenBy { catalogFamily(it) }
        .thenByDescending { version(it) }
        .thenBy { model -> listOf("developer", "preview", "experimental", "sequential").any { it in model.id.lowercase() } }
        .thenByDescending { it.variantRank }
        .thenByDescending { it.providerPriority }
        .thenBy { it.id }

    fun shortlist(models: List<NexusModel>): List<NexusModel> {
        val sorted = models.sortedWith(comparator)
        val preferred = sorted.filter { catalogFamily(it) in families }.distinctBy(::catalogFamily)
        return preferred.ifEmpty { sorted.distinctBy(::catalogFamily).take(5) }
    }
}
