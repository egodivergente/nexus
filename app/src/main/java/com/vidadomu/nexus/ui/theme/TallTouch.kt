package com.vidadomu.nexus.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Makes a short, flat control (the time under a message, a small label) answer to touch across at least
 * [NexusTokens.minimumTouch] of height without moving anything: the extra height comes from the empty room around it,
 * at most [maxAbove] above and the rest below, and the layout keeps the control's own height. The ripple stays on
 * the control's [shape], not on the invisible part. Meant for content that wraps its height.
 */
fun Modifier.tallClickable(
    onClickLabel: String,
    shape: Shape,
    maxAbove: Dp = 6.dp,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val reach = remember { TouchReach() }
    this
        // Outside: report the control's own height, so nothing around it moves.
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val height = (placeable.height - reach.extra).coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(placeable.width, height) { placeable.place(0, -reach.above) }
        }
        .clickable(interactionSource = interaction, indication = null, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
        // Inside: the clickable gets the tall area, the control sits within it where it was.
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            reach.measure(placeable.height, NexusTokens.minimumTouch.roundToPx(), maxAbove.roundToPx())
            layout(placeable.width, placeable.height + reach.extra) { placeable.place(0, reach.above) }
        }
        .clip(shape)
        .indication(interaction, ripple())
}

/** How much touch room a control of a given height needs, and how much of it goes above. Written and read in one measure pass. */
internal class TouchReach {
    var extra = 0
        private set
    var above = 0
        private set

    fun measure(height: Int, minimum: Int, maxAbove: Int) {
        extra = (minimum - height).coerceAtLeast(0)
        above = minOf(extra / 2, maxAbove.coerceAtLeast(0))
    }
}
