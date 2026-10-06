package com.vidadomu.nexus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Backward-compatible adapter that delegates directly to [BrandLogo]
 * with full brand identity resolution.
 */
@Composable
fun VendorLogo(
    organization: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    containerSize: Dp? = null
) {
    BrandLogo(
        organization = organization,
        modifier = modifier,
        size = size,
        containerSize = containerSize
    )
}
