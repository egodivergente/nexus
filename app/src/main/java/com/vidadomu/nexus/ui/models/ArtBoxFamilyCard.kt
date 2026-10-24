package com.vidadomu.nexus.ui.models

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.components.BrandLogo
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.core.model.*

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun ArtBoxFamilyCard(section: CatalogSection, onOpen: ()->Unit, onOptions: ()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(shape=NexusTokens.card,color=colors.surface.copy(alpha=.82f),border=BorderStroke(.5.dp,colors.primary.copy(alpha=.16f)),
        modifier=Modifier.fillMaxWidth().combinedClickable(onClick=onOpen,onLongClick=onOptions,onLongClickLabel="Opciones de ${section.family}")) {
        Column(Modifier.fillMaxWidth().heightIn(min=154.dp).background(Brush.linearGradient(listOf(colors.primary.copy(alpha=.035f),colors.surface.copy(alpha=0f))))
            .padding(horizontal=8.dp,vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
            BrandLogo(family=section.family,size=48.dp,containerSize=52.dp)
            Text(section.family,style=MaterialTheme.typography.titleSmall,minLines=3,maxLines=3,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Text(pluralModelos(section.models.map(::modelGroupTitle).distinct().size),style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
        }
    }
}

/** Only discover operations present in the real catalog; this never creates a synthetic endpoint. */
internal fun toolCategory(model: NexusModel): String? {
    val tasks=catalogWorkflows(model)
    return when {
        CatalogWorkflow.MOTION in tasks -> "Movimiento y pose"
        CatalogWorkflow.LIPSYNC in tasks -> "Sincronización labial"
        CatalogWorkflow.EXTEND in tasks -> "Extender vídeo"
        CatalogWorkflow.UPSCALE in tasks -> if(Modality.VIDEO in model.modalities) "Mejorar vídeo" else "Mejorar imagen"
        tasks.any {it in setOf(CatalogWorkflow.INPAINT,CatalogWorkflow.OUTPAINT,CatalogWorkflow.LAYERS,CatalogWorkflow.REMOVE_BACKGROUND)} -> "Edición de imagen"
        CatalogWorkflow.EDIT_IMAGE in tasks && CatalogWorkflow.CREATE_IMAGE !in tasks -> "Edición de imagen"
        CatalogWorkflow.EDIT_VIDEO in tasks -> "Edición de vídeo"
        else -> null
    }
}

internal fun configuredWorkflowKey(model: NexusModel): String = when(catalogWorkflows(model).firstOrNull()) {
    CatalogWorkflow.REFERENCES -> "reference_to_video"
    CatalogWorkflow.ANIMATE -> "image_to_video"
    CatalogWorkflow.FRAMES -> "start_end_frame_video"
    CatalogWorkflow.CREATE_VIDEO -> "text_to_video"
    CatalogWorkflow.EDIT_IMAGE -> if(model.maxImages>1) "compose_multi_reference" else "reference_edit"
    CatalogWorkflow.CREATE_IMAGE -> "text_to_image"
    else -> "tool"
}
