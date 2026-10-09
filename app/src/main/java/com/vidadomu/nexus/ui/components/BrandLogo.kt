package com.vidadomu.nexus.ui.components

import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage
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
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.R
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

    val actualContainerSize = if(asset is BrandAsset.Monogram) maxOf(containerSize ?: size,NexusTokens.space32) else containerSize ?: size
    val actualShape = shape ?: NexusTokens.thumbnail
    // White official artwork stays white in every theme. Only its contrast backing is fixed.
    val needsContrastBacking = asset is BrandAsset.Resource &&
        (asset.tint == Color.White || asset.resId in setOf(R.drawable.ic_logo_openai,
            R.drawable.ic_logo_runway, R.drawable.ic_logo_elevenlabs, R.drawable.ic_logo_ideogram))
    val artworkSize = if(containerSize == null && needsContrastBacking) size * .8f else size

    when (asset) {
        is BrandAsset.Resource -> {
            if (containerSize != null || needsContrastBacking) {
                Box(
                    modifier = modifier
                        .size(actualContainerSize)
                        .clip(actualShape)
                        .background(asset.backgroundColor)
                        .border(0.5.dp, BorderSubtle, actualShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (asset.isBitmap) {
                        AsyncImage(
                            model = asset.resId,
                            contentDescription = organization ?: modelId,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(artworkSize)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = asset.resId),
                            contentDescription = organization ?: modelId,
                            tint = asset.tint,
                            modifier = Modifier.size(artworkSize)
                        )
                    }
                }
            } else {
                if (asset.isBitmap) {
                    AsyncImage(
                        model = asset.resId,
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
            val textSizeSp = MaterialTheme.typography.labelMedium.fontSize
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
