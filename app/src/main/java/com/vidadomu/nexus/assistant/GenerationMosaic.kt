@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.vidadomu.nexus.assistant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import com.vidadomu.nexus.ui.components.BrandLogo
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.rememberNexusMotionAllowed
import kotlin.math.roundToInt

/** One stable slot per output; pending slot zero keeps its key when the result arrives. */
internal data class MosaicTile(
    val key: String,
    val orderId: String,
    val jobId: String?,
    val model: String,
    val provider: String,
    val modelId: String,
    val modality: String,
    val status: OrderStatus,
    val uri: String?,
    /** Real provider measurement only; null is never an estimated percentage. */
    val progress: Float?,
)

internal fun mosaicEligible(status: OrderStatus, modality: String): Boolean =
    modality in setOf("IMAGE", "VIDEO") &&
        status in setOf(OrderStatus.SUBMITTING, OrderStatus.PROCESSING, OrderStatus.COMPLETED)

internal fun mosaicColumns(widthDp: Float): Int = when {
    widthDp >= 720f -> 4
    widthDp >= 520f -> 3
    else -> 2
}

/** Only media extends to screen edges; ordinary chat messages keep their margins. */
private fun Modifier.fullBleedChat(margin: Dp): Modifier = this.layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val inset = margin.roundToPx()
        val width = constraints.maxWidth + inset * 2
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-inset, 0) }
    }
}

/** Compact in-chat live wall: individual completions reveal in place without blocking siblings. */
@Composable
internal fun GenerationMosaic(
    tiles: List<MosaicTile>,
    onOpen: (MosaicTile) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tiles.isEmpty()) return
    val active = tiles.any { it.uri == null }
    val motion = rememberNexusMotionAllowed()
    val shimmer = if (active && motion) {
        val animation = rememberInfiniteTransition(label = "mosaicActivity")
        val phase by animation.animateFloat(0f, 1f,
            infiniteRepeatable(tween(2100, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmer")
        phase
    } else 0.35f

    BoxWithConstraints(modifier.fillMaxWidth().fullBleedChat(NexusTokens.screenMargin)) {
        val columns = mosaicColumns(maxWidth.value)
        val gap = 2.dp
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            when {
                tiles.size == 1 -> MosaicCell(tiles[0], shimmer, onOpen, onExpand,
                    Modifier.fillMaxWidth().aspectRatio(if (tiles[0].modality == "VIDEO") 16f / 9f else 1.25f))
                tiles.size == 3 && columns == 2 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    MosaicCell(tiles[0], shimmer, onOpen, onExpand, Modifier.weight(1f).aspectRatio(.5f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                        MosaicCell(tiles[1], shimmer, onOpen, onExpand, Modifier.fillMaxWidth().aspectRatio(1f))
                        MosaicCell(tiles[2], shimmer, onOpen, onExpand, Modifier.fillMaxWidth().aspectRatio(1f))
                    }
                }
                else -> tiles.chunked(columns).forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        line.forEach { tile ->
                            MosaicCell(tile, shimmer, onOpen, onExpand, Modifier.weight(1f)
                                .aspectRatio(if (line.size == 1 && tiles.size > 1) 1.65f else 1f))
                        }
                    }
                }
            }
        }
        val finished = tiles.count { it.uri != null }
        Surface(onClick = onExpand,
            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
            color = Color.Black.copy(alpha = .62f),
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(start = 9.dp, end = 5.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (tiles.size == 1) "Detalles" else "$finished/${tiles.size}",
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.MoreVert, "Ver tarjetas y detalles", Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MosaicCell(
    tile: MosaicTile,
    shimmer: Float,
    onOpen: (MosaicTile) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val video = tile.modality == "VIDEO"
    val state = when {
        tile.uri != null -> "Completado"
        tile.status == OrderStatus.SUBMITTING -> "Enviando"
        tile.status == OrderStatus.COMPLETED -> "Preparando archivo"
        else -> "Generando"
    }
    Box(
        modifier.clip(RoundedCornerShape(4.dp))
            .background(colors.surfaceContainerHighest)
            .combinedClickable(
                onClick = { if (tile.uri != null) onOpen(tile) else onExpand() },
                onLongClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onExpand()
                },
                onLongClickLabel = "Ver detalles de ${tile.model}",
            )
            .semantics {
                contentDescription = "${tile.model}: $state"
                stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = tile.uri,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) },
            label = "mosaicResult",
        ) { uri ->
            if (uri == null) {
                Box(Modifier.fillMaxSize().drawBehind {
                    val w = size.width
                    val x = w * (shimmer * 2.5f - 1f)
                    drawRect(Brush.linearGradient(
                        listOf(colors.surfaceContainerHighest, colors.primary.copy(alpha = .18f),
                            colors.surfaceContainerHighest),
                        start = Offset(x - w, 0f), end = Offset(x + w, size.height)))
                })
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(uri).size(720)
                        .apply {
                            if (video) decoderFactory { result, options, _ ->
                                coil.decode.VideoFrameDecoder(result.source, options)
                            }
                        }.build(),
                    contentDescription = null, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        if (video && tile.uri != null) Icon(Icons.Default.PlayArrow, null,
            modifier = Modifier.size(38.dp).background(Color.Black.copy(alpha = .45f), RoundedCornerShape(24.dp)),
            tint = Color.White)
        Row(Modifier.fillMaxWidth().align(Alignment.BottomStart)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f))))
            .padding(start = 5.dp, end = 5.dp, top = 20.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            BrandLogo(organization = tile.provider.replace('_', ' '), family = com.vidadomu.nexus.ui.models.catalogFamilyById(tile.modelId) ?: tile.model,
                modelId = tile.modelId, size = 17.dp, containerSize = 22.dp)
            Column(Modifier.weight(1f)) {
                Text(tile.model, color = Color.White, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tile.uri == null) Text(
                    tile.progress?.let { "$state · ${(it * 100).roundToInt()} %" } ?: state,
                    color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
