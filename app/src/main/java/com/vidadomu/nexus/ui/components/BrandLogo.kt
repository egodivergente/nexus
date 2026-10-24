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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.ui.theme.NexusTokens
import com.vidadomu.nexus.R
import com.vidadomu.nexus.ui.theme.BorderSubtle

/** Average brightness and colourfulness of a logo's visible pixels, measured once on a tiny decode. */
internal class LogoTone(val luminance: Float, val saturation: Float) {
    val monochrome get() = saturation < .15f

    companion object {
        private val cache = java.util.concurrent.ConcurrentHashMap<Int, LogoTone>()
        private val unknown = LogoTone(.5f, 1f)

        fun of(context: android.content.Context, resId: Int): LogoTone? =
            cache.getOrPut(resId) { measure(context, resId) ?: unknown }.takeIf { it !== unknown }

        private fun measure(context: android.content.Context, resId: Int): LogoTone? {
            val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, resId,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = 16 }) ?: return null
            return try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                var weight = 0.0; var lum = 0.0; var sat = 0.0
                for (p in pixels) {
                    val a = (p ushr 24) / 255.0
                    if (a < .12) continue
                    val r = ((p shr 16) and 255) / 255.0; val g = ((p shr 8) and 255) / 255.0; val b = (p and 255) / 255.0
                    val high = maxOf(r, g, b); val low = minOf(r, g, b)
                    weight += a; lum += a * (.2126 * r + .7152 * g + .0722 * b); sat += a * if (high > 0) (high - low) / high else 0.0
                }
                if (weight <= 0.0) null else LogoTone((lum / weight).toFloat(), (sat / weight).toFloat())
            } finally { bitmap.recycle() }
        }
    }
}

@Composable
internal fun DefaultBrandLogo(
    organization: String? = null,
    family: String? = null,
    modelId: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    containerSize: Dp? = null,
    shape: Shape? = null,
    /** Catalog mode: the official artwork is drawn on its own transparent canvas, never inside a coloured square. */
    bare: Boolean = false
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

    if ((bare || containerSize == null) && asset is BrandAsset.Resource) {
        val context = LocalContext.current
        val backgroundLuminance = MaterialTheme.colorScheme.background.luminance()
        // One-colour marks (white or black artwork) follow the theme's text colour so they never vanish; coloured marks are untouched.
        val tone = remember(asset.resId) { if (asset.isBitmap) LogoTone.of(context, asset.resId) else null }
        val adapt = if (tone != null) tone.monochrome && abs(tone.luminance - backgroundLuminance) < .45f
            else needsContrastBacking && backgroundLuminance > .5f
        val tint = MaterialTheme.colorScheme.onSurface
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            if (asset.isBitmap) {
                AsyncImage(model = asset.resId, contentDescription = organization ?: modelId,
                    contentScale = ContentScale.Fit, modifier = Modifier.size(size),
                    colorFilter = if (adapt) ColorFilter.tint(tint) else null)
            } else {
                Icon(painter = painterResource(id = asset.resId), contentDescription = organization ?: modelId,
                    tint = if (adapt) tint else asset.tint, modifier = Modifier.size(size))
            }
        }
        return
    }

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
