package com.vidadomu.nexus.ui.models

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import com.vidadomu.nexus.ui.theme.stylusHover
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.customActions
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
@Composable internal fun ArtBoxFamilyCard(section: CatalogSection, onOpen: ()->Unit, onOptions: ()->Unit, toolCategory: Boolean=false,
    /** Long-press-and-drag handle: when present, a long press lifts the card instead of opening a dialog. */
    dragModifier: Modifier? = null, dragging: Boolean = false, onMoveUp: (()->Unit)? = null, onMoveDown: (()->Unit)? = null,
    /** The user's own name for this entry (falls back to the provider's family name). */
    title: String = section.family, columns: Int = 2) {
    // Con más de dos columnas la tarjeta y su contenido se reducen a la par.
    val f=if(columns<=2) 1f else (2f/columns).coerceIn(.6f,1f)
    val colors=MaterialTheme.colorScheme
    val view=androidx.compose.ui.platform.LocalView.current // sonido de toque del sistema: respeta el ajuste del móvil
    val count=pluralModelos(section.models.map(::modelGroupTitle).distinct().size) // solo para accesibilidad
    // The lifted card grows, gains a soft shadow and an accent edge; the rest of the grid slides out of its way.
    var logoHeld by remember {mutableStateOf(false)}
    var adjusting by remember {mutableStateOf(false)}
    var movingName by remember {mutableStateOf(false)}
    val appContext=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val labels=rememberCatalogLabels()
    val glyphKey="family:"+section.family
    val lift by androidx.compose.animation.core.animateFloatAsState(if(dragging) 1.07f else if(adjusting) 1.04f else 1f,androidx.compose.animation.core.spring(dampingRatio=.62f,stiffness=420f),label="Card lift")
    val reorderActions=listOfNotNull(
        onMoveUp?.let {action -> androidx.compose.ui.semantics.CustomAccessibilityAction("Subir ${section.family}") {action();true}},
        onMoveDown?.let {action -> androidx.compose.ui.semantics.CustomAccessibilityAction("Bajar ${section.family}") {action();true}})
    Surface(shape=NexusTokens.card,color=if(dragging) colors.surfaceContainerHigh else colors.surfaceContainerLow,
        shadowElevation=if(dragging) 18.dp else 3.dp,
        border=BorderStroke(if(dragging||adjusting) 1.5.dp else .5.dp,if(dragging||adjusting) colors.primary else colors.outlineVariant.copy(alpha=.7f)),
        modifier=Modifier.fillMaxWidth().stylusHover(enabled=!dragging && !adjusting).graphicsLayer {scaleX=lift;scaleY=lift}
            .combinedClickable(onClick={view.playSoundEffect(android.view.SoundEffectConstants.CLICK);onOpen()},onLongClick=if(dragModifier==null) ({if(!logoHeld) onOptions()}) else null,onLongClickLabel=if(dragModifier==null) "Opciones de ${section.family}" else null)
            .semantics(mergeDescendants=true) {contentDescription="${section.family}, $count"; if(reorderActions.isNotEmpty()) customActions=reorderActions}) {
        Box {
        IconButton(onClick=onOptions,modifier=Modifier.align(Alignment.TopEnd).size(40.dp)) {
            Icon(Icons.Default.MoreVert,"Opciones de ${section.family}",Modifier.size(18.dp),tint=colors.onSurfaceVariant.copy(alpha=.7f))
        }
        // Número de modelos en una insignia tenue (arriba a la izquierda); con uno solo no aporta nada y se oculta.
        // Mientras se ajusta o se mueve algo, la misma insignia muestra el porcentaje.
        val modelCount=section.models.map(::modelGroupTitle).distinct().size
        val badgeText=when {movingName->"↕"; adjusting->(labels.scale(glyphKey)*100).toInt().toString()+" %"; modelCount>1->modelCount.toString(); else->null}
        if(badgeText!=null) Box(Modifier.align(Alignment.TopStart).padding(8.dp).defaultMinSize(minWidth=24.dp,minHeight=24.dp)
            .clip(CircleShape).background((if(adjusting||movingName) colors.primary else colors.onSurface).copy(alpha=if(adjusting||movingName) .22f else .08f))
            .padding(horizontal=6.dp),contentAlignment=Alignment.Center) {
            Text(badgeText,style=MaterialTheme.typography.labelSmall,color=if(adjusting||movingName) colors.primary else colors.onSurfaceVariant,maxLines=1)
        }
        // Same structure and minimum height for every card (families and tools): logo, a two-line title and the count.
        // Short names no longer leave a blank third line (revision video 1, 11:22; video 2, 02:49). A minimum, not a
        // fixed height: with bold or larger system text (Pixel 8 Pro, 25/10) a fixed height clipped the count.
        Column(Modifier.fillMaxWidth().heightIn(min=ArtBoxCardMetrics.height*f).padding(horizontal=if(columns>=4) 4.dp else 8.dp,vertical=if(columns>=4) 8.dp else 12.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterVertically)) {
            // Fixed slot: the card never changes size, only the logo inside it does.
            Box(Modifier.size(ArtBoxCardMetrics.logoSlot*f).logoResizeGesture(enabled=true,key=glyphKey,current={labels.scale(glyphKey)},
                min=CatalogLabelStore.MIN_SCALE,max=CatalogLabelStore.MAX_SCALE,vibrate={com.vidadomu.nexus.ui.theme.NexusVibration.play(appContext,it)},onHold={logoHeld=it;GestureLock.onCard=it},onAdjusting={adjusting=it},
                onScale={labels.setScaleLive(glyphKey,it)},onCommit={labels.commitScale(glyphKey)}),contentAlignment=Alignment.TopCenter) {
                // Sin tope de hueco: un logo grande crece hacia abajo y el nombre se escribe por encima.
                Box(Modifier.wrapContentSize(Alignment.TopCenter,unbounded=true)) {
                CatalogGlyph(glyphKey,ArtBoxCardMetrics.logo*f,labels) {size ->
                    if(toolCategory) {
                        Box(Modifier.size(size).clip(CircleShape).background(colors.primary.copy(alpha=.14f)),contentAlignment=Alignment.Center) {
                            Icon(toolCategoryIcon(section.family),null,Modifier.size(size*.57f),tint=colors.primary)
                        }
                    } else BrandLogo(family=section.family,modelId=section.models.firstOrNull()?.id,size=size,bare=true)
                }
                }
            }
            // Mantener pulsado el nombre lo levanta y se coloca donde quieras; la fila inferior («N modelos») levanta la tarjeta
            // para reordenarla.
            val density=androidx.compose.ui.platform.LocalDensity.current
            val place=labels.placement(glyphKey,"name")
            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(title,style=if(columns>=4) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,minLines=2,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center,
                    modifier=Modifier.offset(place.first.dp,place.second.dp).graphicsLayer {val s=if(movingName) 1.08f else 1f;scaleX=s;scaleY=s}
                        .holdAndMoveGesture(glyphKey+"|name",current={labels.placement(glyphKey,"name")},
                            vibrate={com.vidadomu.nexus.ui.theme.NexusVibration.play(appContext,it)},onHold={logoHeld=it;GestureLock.onCard=it},onMoving={movingName=it;adjusting=it},
                            onMove={x,y->labels.setPlacementLive(glyphKey,"name",x,y)},onCommit={labels.commitPlacement(glyphKey,"name")},
                            pxPerDp={density.density}))
                // Franja inferior: mantener pulsada levanta la tarjeta para reordenarla.
                Box(Modifier.fillMaxWidth().heightIn(min=20.dp).then(dragModifier ?: Modifier))
            }
        }
        }
    }
}

internal object ArtBoxCardMetrics {
    val height = 160.dp
    val logo = 64.dp
    /** Space reserved for the logo; bigger logos overflow downwards, so every card keeps the same height. */
    val logoSlot = 90.dp
}

/** Functional symbol per category; brand logos stay for providers only. */
internal fun toolCategoryIcon(category: String): ImageVector = when(category) {
    "Edición de vídeo" -> Icons.Default.ContentCut
    "Movimiento y pose" -> Icons.Default.AccessibilityNew
    "Upscale imagen" -> Icons.Default.AutoFixHigh
    "Extender vídeo" -> Icons.Default.FastForward
    "Audio, música y voz" -> Icons.Default.GraphicEq
    "Edición de imagen" -> Icons.Default.Brush
    "Upscale vídeo" -> Icons.Default.HighQuality
    "Sincronización labial" -> Icons.Default.RecordVoiceOver
    else -> Icons.Default.Build
}

/** Only discover operations present in the real catalog; this never creates a synthetic endpoint. */
internal fun toolCategory(model: NexusModel): String? {
    // The user's own placement (group first, then family) wins over the automatic classification.
    if(!CatalogPlacements.isEmpty()) {
        val placed=CatalogPlacements.target(com.vidadomu.nexus.data.repository.groupVisibilityKey(model))
            ?: CatalogPlacements.target(com.vidadomu.nexus.data.repository.familyVisibilityKey(catalogFamily(model)))
        if(placed!=null) return if(placed.startsWith("tool:")) placed.removePrefix("tool:") else null
    }
    return automaticToolCategory(model)
}

internal fun automaticToolCategory(model: NexusModel): String? {
    val tasks=catalogWorkflows(model)
    return when {
        CatalogWorkflow.MOTION in tasks -> "Movimiento y pose"
        CatalogWorkflow.LIPSYNC in tasks -> "Sincronización labial"
        CatalogWorkflow.EXTEND in tasks -> "Extender vídeo"
        CatalogWorkflow.UPSCALE in tasks -> if(Modality.VIDEO in model.modalities) "Upscale vídeo" else "Upscale imagen"
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
