package com.vidadomu.nexus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.ui.theme.NexusMotionPolicy
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.ui.theme.TextSecondary
import com.vidadomu.nexus.ui.theme.rememberLifecycleResumed
import com.vidadomu.nexus.ui.theme.rememberSystemAnimatorsEnabled
import com.vidadomu.nexus.ui.theme.tabularMoney
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Isla flotante de producción en vivo (Nexus Studio 2.0):
 * Acompaña al usuario sobre la barra inferior cuando hay generaciones activas en curso
 * o recién completadas (últimos 20 s), con acceso directo al visor o adjunto inmediato al chat.
 */
@Composable
fun LiveRenderPill(
    onOpenOutput: (String) -> Unit,
    onSendToAgent: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NexusApplication
    val jobs by app.generationRepository.allJobs.collectAsState()
    val haptic = LocalHapticFeedback.current
    val conflicts = remember(jobs) { com.vidadomu.nexus.core.model.OutputFiles.conflictingLocalUris(jobs.flatMap { it.outputs }) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dismissedIds by remember { mutableStateOf(setOf<String>()) }
    var notifiedCompletedIds by remember { mutableStateOf(setOf<String>()) }

    // A job update after a long idle period must refresh the clock before checking completion.
    val observedAt = remember(jobs) { System.currentTimeMillis() }
    val currentTime = maxOf(now, observedAt)
    val snapshot = remember(jobs, currentTime, dismissedIds) { liveRenderSnapshot(jobs, currentTime, dismissedIds) }
    val activeJobs = snapshot.active
    val recentCompleted = snapshot.completed

    val resumed = rememberLifecycleResumed()
    val motionAllowed = NexusMotionPolicy.allowsInfiniteMotion(
        requested = true, animatorsEnabled = rememberSystemAnimatorsEnabled(refreshKey = resumed), visible = resumed)
    // Hidden screens do not tick; on resume the effect restarts and refreshes the clock at once.
    val needsTicker = resumed && (activeJobs.isNotEmpty() || recentCompleted != null)
    LaunchedEffect(needsTicker) {
        while (needsTicker) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    LaunchedEffect(recentCompleted?.id) {
        val completedId = recentCompleted?.id ?: return@LaunchedEffect
        if (completedId !in notifiedCompletedIds) {
            notifiedCompletedIds = notifiedCompletedIds + completedId
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val targetJob = activeJobs.firstOrNull() ?: recentCompleted
    AnimatedVisibility(
        visible = targetJob != null,
        enter = fadeIn(tween(180)) + expandVertically(),
        exit = fadeOut(tween(150)) + shrinkVertically(),
        modifier = modifier
    ) {
        val job = targetJob ?: return@AnimatedVisibility
        val isActive = job.status != GenerationStatus.COMPLETED
        val pulsing = isActive && motionAllowed
        val measuredPct = job.progress?.takeIf {
            it.isFinite() && it in 0f..1f && !job.providerId.contains("flow", ignoreCase = true)
        }
        val elapsedSec = ((currentTime - job.startedAt).coerceAtLeast(0L)) / 1000L
        val modelShort = remember(job.modelId) {
            job.modelId.substringAfterLast('/').replace('-', ' ').replaceFirstChar { it.uppercase() }
        }
        val subtitle = if (isActive) {
            val pctText = measuredPct?.let { "${(it * 100).roundToInt()} % · " }.orEmpty()
            val extraCount = if (activeJobs.size > 1) " (+${activeJobs.size - 1} más)" else ""
            "${pctText}${elapsedSec / 60}:${"%02d".format(elapsedSec % 60)}$extraCount"
        } else {
            "Listo · Toca para abrir"
        }

        Surface(
            shape = NexusTokens.pill,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(
                1.dp,
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NexusTokens.screenMargin, vertical = 2.dp)
                .clickable(role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenOutput(job.id)
                }
                .semantics {
                    contentDescription = "Producción en vivo: $modelShort, $subtitle"
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        LiveRenderDot(pulsing = pulsing, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    val firstOut = job.outputs.firstOrNull()
                    val thumbUri = firstOut?.let { com.vidadomu.nexus.core.model.OutputFiles.trustedLocalUri(it, conflicts) ?: it.url }?.takeIf { it.isNotBlank() }
                    if (thumbUri != null && firstOut?.type in setOf(com.vidadomu.nexus.core.model.Modality.IMAGE, com.vidadomu.nexus.core.model.Modality.VIDEO)) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(context).data(thumbUri).apply {
                                if (firstOut?.type == com.vidadomu.nexus.core.model.Modality.VIDEO)
                                    decoderFactory { result, options, _ -> coil.decode.VideoFrameDecoder(result.source, options) }
                            }.build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(NexusTokens.thumbnail)
                        )
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modelShort,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium.tabularMoney(),
                        color = if (isActive) MaterialTheme.colorScheme.primary else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isActive && measuredPct != null) {
                    CircularProgressIndicator(
                        progress = { measuredPct },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp
                    )
                } else if (!isActive) {
                    val firstOutputId = job.outputs.firstOrNull()?.id
                    if (onSendToAgent != null && firstOutputId != null) {
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dismissedIds = dismissedIds + job.id
                                onSendToAgent(job.id, firstOutputId)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Al chat", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    IconButton(
                        onClick = { dismissedIds = dismissedIds + job.id },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Ocultar aviso de producción",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** The infinite transition exists only while [pulsing]; otherwise the dot is static at full strength. */
@Composable
private fun LiveRenderDot(pulsing: Boolean, color: Color) {
    val dot = Modifier.size(10.dp).clip(CircleShape)
    if (pulsing) {
        val pulse = rememberInfiniteTransition(label = "LiveRenderPulse")
        val alpha = pulse.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "PulseAlpha"
        )
        Box(dot.drawBehind { drawRect(color.copy(alpha = alpha.value)) })
    } else {
        Box(dot.background(color))
    }
}
