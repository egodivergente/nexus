package com.vidadomu.nexus.ui.settings

import androidx.compose.runtime.Composable
import com.vidadomu.nexus.ui.flow.FlowStudioScreen

@Composable
fun FlowCreateScreen(
    onBack: () -> Unit,
    onNavigateToOutput: (String) -> Unit,
    onOpenModels: () -> Unit = onBack
) {
    FlowStudioScreen(onBack = onBack, onNavigateToOutput = onNavigateToOutput, onOpenModels = onOpenModels)
}
