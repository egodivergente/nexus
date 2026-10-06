package com.vidadomu.nexus.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.ui.theme.BorderSubtle

@Composable
fun BrandLogo(
    organization: String? = null,
    family: String? = null,
    modelId: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    containerSize: Dp? = null,
    shape: Shape? = null
) {
    val asset = remember(organization, family, modelId) {
        ProviderBrandRegistry.resolve(
            organization = organization,
            family = family,
            modelId = modelId
        )
    }

    val actualContainerSize = containerSize ?: size
    val actualShape = shape ?: RoundedCornerShape(actualContainerSize / 3.5f)

    when (asset) {
        is BrandAsset.Resource -> {
            if (containerSize != null) {
                Box(
                    modifier = modifier
                        .size(actualContainerSize)
                        .clip(actualShape)
                        .background(asset.backgroundColor)
                        .border(0.5.dp, BorderSubtle, actualShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (asset.isBitmap) {
                        Image(
                            painter = painterResource(id = asset.resId),
                            contentDescription = organization ?: modelId,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(size)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = asset.resId),
                            contentDescription = organization ?: modelId,
                            tint = asset.tint,
                            modifier = Modifier.size(size)
                        )
                    }
                }
            } else {
                if (asset.isBitmap) {
                    Image(
                        painter = painterResource(id = asset.resId),
                        contentDescription = organization ?: modelId,
                        contentScale = ContentScale.Fit,
                        modifier = modifier.size(size)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = asset.resId),
                        contentDescription = organization ?: modelId,
                        tint = asset.tint,
                        modifier = modifier.size(size)
                    )
                }
            }
        }
        is BrandAsset.Monogram -> {
            val textSizeSp = (actualContainerSize.value * 0.42f).sp
            Box(
                modifier = modifier
                    .size(actualContainerSize)
                    .clip(actualShape)
                    .background(asset.backgroundColor)
                    .border(0.5.dp, BorderSubtle, actualShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = asset.initials,
                    color = asset.textColor,
                    fontSize = textSizeSp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }
        }
    }
}
