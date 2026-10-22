package com.vidadomu.nexus.ui.flow

import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationRequest
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.MediaInput
import com.vidadomu.nexus.core.model.MediaSlotType
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.provider.flow.FlowBridgeProvider
import java.util.UUID

@kotlinx.serialization.Serializable
data class FlowStudioDraft(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String = "",
    val count: Int = 1,
    val modality: Modality = Modality.IMAGE
)

@kotlinx.serialization.Serializable
data class FlowStudioReference(val uri: String, val mimeType: String, val fileName: String?, val remoteId: String? = null)

object FlowStudioPlan {
    const val MIN_COUNT = 1
    const val MAX_COUNT = 4
    const val MAX_DRAFTS = 12
    const val MAX_REFERENCES = 12
    val REFERENCE_MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp")

    const val DOWNLOAD_FORMAT_REASON =
        "El puente solo entrega el archivo original de cada resultado. PNG/JPG y resoluciones solo existen en la importación de proyectos."
    const val EXTEND_VIDEO_REASON =
        "El puente de Flow no expone una operación para extender vídeos; se activará cuando la API la ofrezca."

    fun clampCount(value: Int): Int = value.coerceIn(MIN_COUNT, MAX_COUNT)

    fun sendable(drafts: List<FlowStudioDraft>, promptLimit: Int, accepted: Set<Modality>): List<FlowStudioDraft> =
        drafts.filter { it.prompt.isNotBlank() && it.prompt.trim().length <= promptLimit && it.modality in accepted }

    fun mediaInputs(references: List<FlowStudioReference>): List<MediaInput> = references.mapNotNull { ref ->
        ref.remoteId?.let {
            MediaInput(type = Modality.IMAGE, uriString = ref.uri, mimeType = ref.mimeType, fileName = ref.fileName,
                remoteUrl = it, slotType = MediaSlotType.REFERENCE_IMAGE)
        }
    }

    fun request(draft: FlowStudioDraft, references: List<MediaInput>, id: String = UUID.randomUUID().toString()): GenerationRequest {
        val parameters = buildMap {
            put(FlowBridgeProvider.EXPECTED_OUTPUT_KEY, if (draft.modality == Modality.VIDEO) "video" else "image")
            put("count", clampCount(draft.count).toString())
            if (draft.modality == Modality.VIDEO) put("mode", if (references.isEmpty()) "Texto" else "Referencias")
        }
        return GenerationRequest(
            id = id,
            providerId = FlowBridgeProvider.PROVIDER_ID,
            modelId = FlowBridgeProvider.CURRENT_PROJECT_MODEL,
            modality = draft.modality,
            prompt = draft.prompt.trim(),
            parameters = parameters,
            mediaInputs = references
        )
    }

    fun expectedCells(job: GenerationJob): Int =
        maxOf(job.parameters["count"]?.toIntOrNull()?.let(::clampCount) ?: 1, job.outputs.size)

    fun isActive(status: GenerationStatus): Boolean =
        status in setOf(GenerationStatus.UPLOADING, GenerationStatus.QUEUED, GenerationStatus.GENERATING,
            GenerationStatus.PROCESSING, GenerationStatus.RENDERING)

    fun statusLabel(status: GenerationStatus): String = when (status) {
        GenerationStatus.UPLOADING -> "Subiendo"
        GenerationStatus.QUEUED -> "En cola"
        GenerationStatus.GENERATING -> "Generando"
        GenerationStatus.PROCESSING -> "Procesando"
        GenerationStatus.RENDERING -> "Renderizando"
        GenerationStatus.COMPLETED -> "Listo"
        GenerationStatus.FAILED -> "Error"
        GenerationStatus.CANCELLED -> "Detenido"
    }

    fun encodeIds(ids: List<String>): String = ids.joinToString(",")
    fun decodeIds(value: String): List<String> = value.split(',').filter { it.isNotBlank() }
}
