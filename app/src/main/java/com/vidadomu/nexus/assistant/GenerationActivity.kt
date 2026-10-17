package com.vidadomu.nexus.assistant

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vidadomu.nexus.agent.core.orders.Order
import com.vidadomu.nexus.agent.core.orders.OrderStatus
import com.vidadomu.nexus.ui.theme.NexusTokens
import kotlin.math.abs
import kotlin.math.roundToInt

/** Fase en la que esta vista pinta actividad; cualquier otro estado del encargo no anima nada. */
internal enum class RenderPhase { NONE, SUBMITTING, PROCESSING }

/** Por qué hay (o no hay) un porcentaje que mostrar. Nunca se rellena con una estimación. */
internal enum class ProgressSource { MEASURED, NOT_STARTED, PROVIDER_MILESTONES, SIMULATED, NOT_REPORTED, INVALID_VALUE }

/** Aviso fijo: lo que se mueve es una animación, no un adelanto del resultado. */
internal const val ACTIVITY_DISCLAIMER = "Animación de actividad, no vista previa."

internal fun orderProviderId(order: Order): String = order.modelRef.substringBefore(':')

/** Flow (y variantes) publica hitos fijos, no un porcentaje medido: no se puede pintar como avance. */
internal fun reportsMilestonesNotPercentage(providerId: String): Boolean = providerId.contains("flow", ignoreCase = true)

/**
 * Avance realmente medido por el proveedor, o null. Solo cuenta con el trabajo ya aceptado
 * (PROCESSING): durante el envío o un reintento, un valor heredado sería avance de otro intento.
 * Un valor no finito o fuera de 0..1 se descarta como desconocido: no se recorta, porque
 * recortarlo inventaría un avance.
 */
internal fun measuredOrderProgress(order: Order): Float? {
    if (order.status != OrderStatus.PROCESSING) return null
    val raw = order.progress ?: return null
    if (!raw.isFinite() || raw < 0f || raw > 1f) return null
    if (reportsMilestonesNotPercentage(orderProviderId(order)) || orderProviderId(order) == "mock_provider") return null
    return raw
}

internal fun progressSource(order: Order): ProgressSource {
    if (order.status == OrderStatus.SUBMITTING) return ProgressSource.NOT_STARTED
    if (order.status != OrderStatus.PROCESSING) return ProgressSource.NOT_REPORTED
    val raw = order.progress ?: return ProgressSource.NOT_REPORTED
    return when {
        !raw.isFinite() || raw < 0f || raw > 1f -> ProgressSource.INVALID_VALUE
        reportsMilestonesNotPercentage(orderProviderId(order)) -> ProgressSource.PROVIDER_MILESTONES
        orderProviderId(order) == "mock_provider" -> ProgressSource.SIMULATED
        else -> ProgressSource.MEASURED
    }
}

/** Pulso triangular 0..1 usado por la animación; expuesto para poder comprobarlo sin pantalla. */
internal fun sweepIntensity(t: Float): Float {
    if (!t.isFinite()) return 0f
    val x = ((t % 1f) + 1f) % 1f
    return 1f - abs(2f * x - 1f)
}

internal data class GenerationActivityState(
    val phase: RenderPhase,
    val title: String,
    /** Solo avance medido: null significa que no se pinta barra determinada ni porcentaje. */
    val progress: Float?,
    val source: ProgressSource,
    val progressText: String,
    val animated: Boolean,
    val contentDescription: String,
) {
    val active: Boolean get() = phase != RenderPhase.NONE
}

/**
 * Todo lo que la vista decide, sin Compose ni Android: fase, textos honestos y si debe animar.
 * [animationsAllowed] ya incorpora el parámetro `animated`, los animadores del sistema y el
 * ciclo de vida. No hay tiempo transcurrido: Order no guarda el instante en que arrancó el
 * intento en curso (createdAt es la creación del borrador y la fecha del error anterior no es
 * el arranque del reintento), así que se omite en lugar de inventar un cronómetro.
 */
internal fun generationActivityState(order: Order, animationsAllowed: Boolean): GenerationActivityState {
    val phase = when (order.status) {
        OrderStatus.SUBMITTING -> RenderPhase.SUBMITTING
        OrderStatus.PROCESSING -> RenderPhase.PROCESSING
        else -> RenderPhase.NONE
    }
    if (phase == RenderPhase.NONE) {
        return GenerationActivityState(phase, "", null, ProgressSource.NOT_REPORTED, "", false, "")
    }
    // PROCESSING solo acredita que el proveedor aceptó el trabajo: puede estar en cola, así que
    // no se afirma qué está haciendo ni de qué medio se trata.
    val title = if (phase == RenderPhase.SUBMITTING) "Enviando el encargo al proveedor" else "Generación en curso"
    val progress = measuredOrderProgress(order)
    val source = progressSource(order)
    val progressText = when (source) {
        ProgressSource.MEASURED -> "${((progress ?: 0f) * 100).roundToInt()} % informado por el proveedor"
        ProgressSource.NOT_STARTED -> "Sin porcentaje: el encargo todavía se está enviando."
        ProgressSource.PROVIDER_MILESTONES -> "Este proveedor informa por hitos, no con un porcentaje medido."
        ProgressSource.SIMULATED -> "Proveedor de prueba: actividad simulada, sin porcentaje real."
        ProgressSource.NOT_REPORTED -> "Sin porcentaje: este proveedor no informa del avance."
        ProgressSource.INVALID_VALUE -> "Sin porcentaje: el proveedor envió un avance no válido y se ignora."
    }
    return GenerationActivityState(
        phase = phase, title = title, progress = progress, source = source, progressText = progressText,
        animated = animationsAllowed,
        contentDescription = "$title · $progressText · $ACTIVITY_DISCLAIMER",
    )
}

/**
 * Actividad de render de un encargo en curso: animación estilizada que solo dice «esto sigue
 * trabajando». No simula el resultado ni estima porcentajes ni tiempos; la barra determinada
 * aparece únicamente con el avance medido por el proveedor. No pinta nada en estados terminales.
 */
@Composable
internal fun GenerationActivity(order: Order, animated: Boolean, compact: Boolean, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember(lifecycleOwner) { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { owner, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Ajuste de accesibilidad del sistema: si el usuario desactivó las animaciones, no se anima.
    val animatorsEnabled = ValueAnimator.areAnimatorsEnabled()
    val state = generationActivityState(order, animated && animatorsEnabled && resumed)
    if (!state.active) return

    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth().semantics { contentDescription = state.contentDescription; stateDescription = state.progressText },
        color = colors.surfaceContainer, contentColor = colors.onSurface, shape = NexusTokens.field,
    ) {
        Column(
            Modifier.padding(horizontal = if (compact) NexusTokens.space8 else NexusTokens.space12, vertical = if (compact) NexusTokens.space8 else NexusTokens.space12),
            verticalArrangement = Arrangement.spacedBy(if (compact) NexusTokens.space4 else 6.dp),
        ) {
            Text(state.title, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall, maxLines = 1)
            RenderActivityStrip(animated = state.animated, height = if (compact) 8.dp else 14.dp, color = colors.primary)
            state.progress?.let { value ->
                LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth().clearAndSetSemantics {})
            }
            Text(state.progressText, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            if (!compact) Text(ACTIVITY_DISCLAIMER, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

/** Barras que recorren un pulso de izquierda a derecha; estáticas y atenuadas si no se anima. */
@Composable
private fun RenderActivityStrip(animated: Boolean, height: Dp, color: Color) {
    val bars = 7
    val phase = if (animated) {
        val transition = rememberInfiniteTransition(label = "renderActivity")
        transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "renderSweep",
        ).value
    } else 0f
    Row(
        Modifier.fillMaxWidth().height(height).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(bars) { index ->
            val intensity = if (animated) sweepIntensity(phase + index / bars.toFloat()) else 0.35f
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(NexusTokens.thumbnail)
                    .background(color.copy(alpha = 0.18f + 0.62f * intensity))
            )
        }
    }
}
