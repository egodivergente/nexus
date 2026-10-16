package com.vidadomu.nexus.ui.library

import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidadomu.nexus.ui.output.OutputFileFacts
import com.vidadomu.nexus.ui.output.inspectOutputFile
import com.vidadomu.nexus.ui.theme.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.SimpleDateFormat
import java.util.*

private val es = Locale("es", "ES")

/** «1080p», «4K» para vídeo; «2048×2048» para imagen. Nada si no se conoce. */
internal fun tileQuality(image: Boolean, width: Int?, height: Int?): String? {
    if (width == null || height == null || width <= 0 || height <= 0) return null
    if (image) return "$width×$height"
    val short = minOf(width, height)
    return when {
        short >= 2000 -> "4K"
        short >= 1400 -> "1440p"
        else -> "${short}p"
    }
}

/** «12,4 MB», «350 KB», «1,2 GB». */
internal fun tileSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(es, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(es, "%.1f MB", bytes / (1L shl 20).toDouble())
    else -> "${maxOf(1L, bytes / 1024)} KB"
}

/** «16 oct · 07:14»; con año si no es el actual. */
internal fun tileDateTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    fun year(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)
    val pattern = if (year(epochMillis) == year(now)) "d MMM · HH:mm" else "d MMM yyyy · HH:mm"
    return SimpleDateFormat(pattern, es).format(Date(epochMillis)).replace(".", "")
}

/** Lo ya medido no se vuelve a leer al desplazar la cuadrícula; dos lecturas a la vez como mucho. */
private object TileFactsCache {
    val facts = LruCache<String, OutputFileFacts>(600)
    val reads = Semaphore(2)
}

/** Tamaño (y resolución si [dimensions]) del fichero local; null mientras se mide o si no hay copia local. */
@Composable
internal fun rememberTileFacts(localUri: String?, mimeType: String, dimensions: Boolean): OutputFileFacts? {
    val context = LocalContext.current.applicationContext
    val key = localUri?.let { "$it|$mimeType|$dimensions" }
    var facts by remember(key) { mutableStateOf(key?.let { TileFactsCache.facts.get(it) }) }
    LaunchedEffect(key) {
        if (key == null || localUri == null || facts != null) return@LaunchedEffect
        val read = TileFactsCache.reads.withPermit { inspectOutputFile(context, localUri, mimeType, dimensions) }
        TileFactsCache.facts.put(key, read)
        facts = read
    }
    return facts
}

/** Pie discreto de una miniatura: resolución · tamaño y, debajo, fecha y hora. */
@Composable
internal fun TileDetailsCaption(quality: String?, bytes: Long?, epochMillis: Long?, modifier: Modifier = Modifier) {
    val first = listOfNotNull(quality, bytes?.let(::tileSize)).joinToString(" · ")
    val second = remember(epochMillis) { epochMillis?.let(::tileDateTime) }
    if (first.isEmpty() && second == null) return
    // Degradado que ya oscurece a la altura de la primera línea y sombra leve: legible también sobre fotos blancas.
    val style = MaterialTheme.typography.labelSmall.copy(lineHeight = 13.sp,
        shadow = Shadow(Color.Black.copy(alpha = .6f), Offset(0f, 1f), 3f)).tabularMoney()
    Column(modifier.fillMaxWidth()
        .background(Brush.verticalGradient(0f to Color.Transparent, .3f to Color.Black.copy(alpha = .5f), 1f to Color.Black.copy(alpha = .8f)))
        .padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 4.dp)) {
        if (first.isNotEmpty()) Text(first, style = style, color = MediaForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (second != null) Text(second, style = style, color = MediaForeground.copy(alpha = .8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** ▶ y duración arriba a la derecha, para que no tape el pie de metadatos. */
@Composable
internal fun TileMediaBadge(video: Boolean, duration: String?, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = .78f), contentColor = MediaForeground,
        shape = NexusTokens.thumbnail, modifier = modifier.padding(NexusTokens.space4)) {
        Row(Modifier.padding(NexusTokens.space4), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexusTokens.space4)) {
            Icon(if (video) Icons.Default.PlayArrow else Icons.Default.GraphicEq, null, Modifier.size(NexusTokens.space16))
            if (duration != null) Text(duration, style = MaterialTheme.typography.labelMedium.tabularMoney())
        }
    }
}
