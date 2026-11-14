package com.vidadomu.nexus.ui.cut

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.ui.theme.NexusTokens

@Composable
internal fun CutResizeHandle(label: String, onDrag: (Float) -> Unit, onReset: () -> Unit) {
    val drag by rememberUpdatedState(onDrag)
    Box(Modifier.fillMaxWidth().height(NexusTokens.space24)
        .semantics {
            contentDescription = label
            customActions = listOf(
                CustomAccessibilityAction("Ampliar panel") { drag(-64f); true },
                CustomAccessibilityAction("Reducir panel") { drag(64f); true },
                CustomAccessibilityAction("Restablecer tamaño") { onReset(); true })
        }
        .clickable(onClickLabel = "Restablecer tamaño", onClick = onReset)
        .pointerInput(Unit) { detectVerticalDragGestures { change, amount -> change.consume(); drag(amount) } },
        contentAlignment = Alignment.Center) {
        Box(Modifier.width(NexusTokens.space32).height(NexusTokens.space4)
            .background(MaterialTheme.colorScheme.outline, NexusTokens.pill))
    }
}
