package com.vidadomu.nexus.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.core.model.GenerationJob
import com.vidadomu.nexus.core.model.GenerationStatus
import com.vidadomu.nexus.core.model.Modality
import com.vidadomu.nexus.ui.theme.AccentEmerald
import com.vidadomu.nexus.ui.theme.BorderSubtle
import com.vidadomu.nexus.ui.theme.PrimaryNeon
import com.vidadomu.nexus.ui.theme.SecondaryVioletLight
import com.vidadomu.nexus.ui.theme.SurfaceDark
import com.vidadomu.nexus.ui.theme.TextMuted
import com.vidadomu.nexus.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun GenerationProgressPanel(
    job: GenerationJob,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var now by remember(job.id) { mutableLongStateOf(System.currentTimeMillis()) }
    val active = job.status !in setOf(
        GenerationStatus.COMPLETED,
        GenerationStatus.FAILED,
        GenerationStatus.CANCELLED
    )

    LaunchedEffect(job.id, active) {
        while (active) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    val elapsedMs = (now - job.startedAt).coerceAtLeast(0L)
    val expectedMs = remember(job.modality, job.modelId) {
        when (job.modality) {
            Modality.IMAGE -> if (job.modelId.contains("flux", ignoreCase = true)) 15_000L else 10_000L
            Modality.VIDEO -> 75_000L
            Modality.AUDIO -> 45_000L
            Modality.CHAT -> 10_000L
        }
    }
    val timeEstimate = ((elapsedMs.toFloat() / expectedMs.toFloat()) * 0.92f).coerceIn(0.02f, 0.94f)
    val stageAdjusted = when (job.status) {
        GenerationStatus.UPLOADING -> timeEstimate.coerceAtMost(0.12f)
        GenerationStatus.QUEUED -> timeEstimate.coerceAtMost(0.18f)
        GenerationStatus.GENERATING, GenerationStatus.PROCESSING -> timeEstimate.coerceAtLeast(0.18f)
        GenerationStatus.RENDERING -> timeEstimate.coerceAtLeast(0.64f)
        GenerationStatus.COMPLETED -> 1f
        GenerationStatus.FAILED, GenerationStatus.CANCELLED -> job.progress ?: timeEstimate
    }
    val targetProgress = maxOf(stageAdjusted, job.progress ?: 0f).coerceIn(0f, if (active) 0.98f else 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 450),
        label = "generationProgress"
    )
    val elapsedSeconds = (elapsedMs / 1_000L).coerceAtLeast(0L)
    val remainingSeconds = ((expectedMs * (1f - animatedProgress)) / 1_000f).roundToInt().coerceAtLeast(0)
    val percentage = (animatedProgress * 100f).roundToInt().coerceIn(0, 100)
    val liveStage = liveStage(job, animatedProgress)

    Surface(
        modifier = modifier,
        color = SurfaceDark,
        shape = RoundedCornerShape(if (compact) 10.dp else 16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 10.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = liveStage,
                        color = PrimaryNeon,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!compact) {
                        Text(
                            text = "Seguimiento en vivo de Atlas Cloud",
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text(
                    text = "$percentage%",
                    color = TextPrimary,
                    fontSize = if (compact) 22.sp else 36.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 5.dp else 9.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(BorderSubtle)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(if (compact) 5.dp else 9.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(PrimaryNeon, SecondaryVioletLight, AccentEmerald)
                            )
                        )
                )
            }

            Text(
                text = "Tiempo transcurrido: ${elapsedSeconds}s  •  Restante estimado: ~${remainingSeconds}s",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun liveStage(job: GenerationJob, progress: Float): String {
    val atlasStatus = job.technicalDetails
        ?.lineSequence()
        ?.firstOrNull { it.startsWith("Atlas Status:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.lowercase()

    return when {
        atlasStatus?.contains("queue") == true || atlasStatus == "pending" -> "En cola en servidor"
        atlasStatus?.contains("transcod") == true -> "Transcodificando"
        atlasStatus?.contains("render") == true -> "Renderizando fotogramas"
        atlasStatus?.contains("upload") == true -> "Subiendo entrada"
        job.status == GenerationStatus.QUEUED -> "En cola en servidor"
        job.status == GenerationStatus.UPLOADING -> "Subiendo entrada"
        job.status == GenerationStatus.RENDERING -> "Renderizando fotogramas"
        job.modality == Modality.VIDEO && progress >= 0.78f -> "Transcodificando"
        job.modality == Modality.VIDEO -> "Renderizando fotogramas"
        job.status == GenerationStatus.GENERATING -> "Generando resultado"
        else -> "Procesando en Atlas Cloud"
    }
}
