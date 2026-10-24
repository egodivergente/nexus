package com.vidadomu.nexus.ui.models

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.components.BrandLogo
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.core.model.*

/** Provider card: logo first (large, on transparent canvas), then name, then the real model count.
 *  Tool categories pass [toolCategory] so a functional icon replaces the brand artwork. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun ArtBoxFamilyCard(section: CatalogSection, onOpen: ()->Unit, onOptions: ()->Unit, toolCategory: Boolean=false) {
    val colors=MaterialTheme.colorScheme
    val count=pluralModelos(section.models.map(::modelGroupTitle).distinct().size)
    Surface(shape=NexusTokens.card,color=colors.surfaceContainerLow,border=BorderStroke(.5.dp,colors.outlineVariant.copy(alpha=.7f)),
        modifier=Modifier.fillMaxWidth().combinedClickable(onClick=onOpen,onLongClick=onOptions,onLongClickLabel="Opciones de ${section.family}")
            .semantics(mergeDescendants=true) {contentDescription="${section.family}, $count"}) {
        Column(Modifier.fillMaxWidth().heightIn(min=if(toolCategory) 156.dp else 132.dp).padding(horizontal=6.dp,vertical=12.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterVertically)) {
            if(toolCategory) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(colors.primary.copy(alpha=.14f)),contentAlignment=Alignment.Center) {
                    Icon(toolCategoryIcon(section.family),null,Modifier.size(34.dp),tint=colors.primary)
                }
            } else BrandLogo(family=section.family,modelId=section.models.firstOrNull()?.id,size=60.dp,bare=true)
            Text(section.family,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center)
            Text(count,style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant,maxLines=1)
        }
    }
}

/** Functional symbol per category; brand logos stay for providers only. */
internal fun toolCategoryIcon(category: String): ImageVector = when(category) {
    "Edición de vídeo" -> Icons.Default.ContentCut
    "Movimiento y pose" -> Icons.Default.AccessibilityNew
    "Mejorar imagen" -> Icons.Default.AutoFixHigh
    "Extender vídeo" -> Icons.Default.FastForward
    "Audio, música y voz" -> Icons.Default.GraphicEq
    "Edición de imagen" -> Icons.Default.Brush
    "Mejorar vídeo" -> Icons.Default.HighQuality
    "Sincronización labial" -> Icons.Default.RecordVoiceOver
    else -> Icons.Default.Build
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
        CatalogWorkflow.EDIT_VIDEO in tasks -> "Edición de vídeo"
        CatalogWorkflow.AUDIO in tasks -> "Audio, música y voz"
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
