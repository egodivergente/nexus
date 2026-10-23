package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.registry.TextLimit
import com.vidadomu.nexus.core.model.NexusModel
import com.vidadomu.nexus.provider.atlas.NativeModelSchema

/** Same reviewed contract snapshot as the desktop. Unknown limits remain unknown. */
internal object ProviderPromptLimits {
    fun forModel(model: NexusModel): TextLimit? {
        if(model.providerId == "atlas_cloud") return NativeModelSchema.promptLimit(model.id)
        if(model.providerId == "crun") return model.promptMaxLength?.let {TextLimit("unicode_code_points",minimum=if(model.promptRequired) 1 else 0,maximum=it,source="Contrato OpenAPI de Crun")}
        if(model.providerId == "google_flow") return null
        if(model.providerId != "runway") return null
        val known = when(model.contractEndpoint) {
            "image_to_video" -> mapOf("gen4.5" to 1000,"gen4_turbo" to 1000,"veo3.1" to 1000,"veo3.1_fast" to 1000,"happyhorse_1_0" to 2500)
            "text_to_video" -> mapOf("gen4.5" to 1000,"veo3.1" to 1000,"veo3.1_fast" to 1000,"happyhorse_1_0" to 2500)
            "text_to_image" -> mapOf("gen4_image" to 1000,"gen4_image_turbo" to 1000)
            else -> emptyMap()
        }
        val maximum = known[model.providerModelId] ?: return null
        return TextLimit("utf16_code_units",minimum=if(model.promptRequired) 1 else 0,maximum=maximum,
            source="https://github.com/runwayml/sdk-python/blob/main/src/runwayml/types/${model.contractEndpoint}_create_params.py")
    }
}
