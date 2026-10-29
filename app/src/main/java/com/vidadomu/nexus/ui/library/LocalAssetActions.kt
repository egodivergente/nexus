package com.vidadomu.nexus.ui.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.compose.AsyncImage
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.Asset
import com.vidadomu.nexus.agent.core.assets.AssetType
import com.vidadomu.nexus.agent.core.tools.Attachment
import com.vidadomu.nexus.assistant.AssistantMediaPreview
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.ui.theme.*
import kotlinx.coroutines.*
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LocalAssetTile(asset:Asset,loader:ImageLoader,showDetails:Boolean,mark:Boolean?,duplicate:Boolean=false,onPreview:()->Unit) {
    val visual=asset.type in setOf(AssetType.IMAGE,AssetType.VIDEO)
    // Mantener pulsado lo gestiona la cuadrícula (marcar y arrastrar), igual que en los archivos generados.
    Surface(shape=NexusTokens.thumbnail,modifier=Modifier.aspectRatio(1f).clickable(
        role=Role.Button,onClickLabel="Abrir ${asset.displayName}",onClick=onPreview)) {
        Box(contentAlignment=Alignment.Center) {
            if(visual) {
                val isVideo=asset.type==AssetType.VIDEO
                when(val thumb=rememberThumb(asset.uri,isVideo)) {
                    is Thumb.Ready -> androidx.compose.foundation.Image(thumb.bitmap.asImageBitmap(),asset.displayName,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    Thumb.Failed -> Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Icon(if(isVideo) Icons.Default.Movie else Icons.Default.BrokenImage,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No disponible",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Thumb.Loading -> Unit
                }
            }
            else Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(NexusTokens.space8).padding(bottom=if(showDetails) NexusTokens.space24 else 0.dp)) {
                Icon(if(asset.type==AssetType.AUDIO) Icons.Default.GraphicEq else Icons.Default.Description,null)
                Text(asset.displayName,style=MaterialTheme.typography.labelMedium,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            // Mantener pulsado marca; ▶ y duración arriba, duplicado debajo de ella; metadatos abajo.
            if(mark!=null) TileMark(mark,Modifier.align(Alignment.TopStart))
            Column(modifier=Modifier.align(Alignment.TopEnd),horizontalAlignment=Alignment.End) {
                if(asset.type in setOf(AssetType.VIDEO,AssetType.AUDIO))
                    TileMediaBadge(asset.type==AssetType.VIDEO,mediaDuration(asset.durationSec?.toFloat()))
                if(duplicate) TileDuplicateBadge()
            }
            if(showDetails) {
                val known=asset.width?.takeIf {it>0}!=null && asset.height?.takeIf {it>0}!=null
                val facts=rememberTileFacts(asset.uri,asset.mimeOrDefault(),dimensions=visual && !known)
                TileDetailsCaption(
                    quality=if(visual) tileQuality(asset.type==AssetType.IMAGE,asset.width?.takeIf {it>0} ?: facts?.width,asset.height?.takeIf {it>0} ?: facts?.height) else null,
                    bytes=facts?.bytes,epochMillis=asset.createdAt,modifier=Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

private fun Asset.mimeOrDefault():String = mimeType?.takeIf {it.isNotBlank()} ?: when(type) {
    AssetType.IMAGE->"image/*";AssetType.VIDEO->"video/*";AssetType.AUDIO->"audio/*";else->"application/octet-stream"
}

/** Imported history entries often carry no MIME: the extension, then the asset type, decide it so the preview opens instead of a generic file card. */
internal fun Asset.resolvedMime():String {
    mimeType?.takeIf {it.isNotBlank() && it!="application/octet-stream"}?.let {return it}
    val extension=android.webkit.MimeTypeMap.getFileExtensionFromUrl(Uri.parse(uri).lastPathSegment ?: displayName).ifBlank {displayName.substringAfterLast('.',"")}
    return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: mimeOrDefault()
}

/** «1080p · 12,4 MB · 16 oct · 07:14 · video/mp4» bajo el título de la hoja. */
@Composable
internal fun LocalAssetFacts(asset:Asset) {
    val visual=asset.type in setOf(AssetType.IMAGE,AssetType.VIDEO)
    val facts=rememberTileFacts(asset.uri,asset.mimeOrDefault(),dimensions=visual)
    val line=listOfNotNull(
        if(visual) tileQuality(asset.type==AssetType.IMAGE,asset.width?.takeIf {it>0} ?: facts?.width,asset.height?.takeIf {it>0} ?: facts?.height) else null,
        facts?.bytes?.let(::tileSize),
        mediaDuration(asset.durationSec?.toFloat() ?: facts?.durationSeconds),
        tileDateTime(asset.createdAt),
        asset.mimeType,
    ).joinToString(" · ")
    Text(line,Modifier.fillMaxWidth(),style=MaterialTheme.typography.labelMedium.tabularMoney(),color=TextSecondary)
}

