package com.vidadomu.nexus.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.vidadomu.nexus.core.model.*
import java.io.File
import com.vidadomu.nexus.ui.theme.NexusSheetSystemBars
import com.vidadomu.nexus.ui.theme.NexusTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryActions(job:GenerationJob,output:GenerationOutput,localUri:String?,favorite:Boolean,hidden:Boolean,
    onDismiss:()->Unit,onOpen:()->Unit,onFavorite:()->Unit,onHide:()->Unit,onError:(String)->Unit) {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest=onDismiss,shape=NexusTokens.sheet) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().padding(horizontal=NexusTokens.space16).padding(bottom=NexusTokens.space24)) {
            Text("Opciones del archivo",style=MaterialTheme.typography.titleMedium)
            ListItem(headlineContent={Text("Abrir")},leadingContent={Icon(Icons.Default.OpenInFull,null)},
                modifier=Modifier.actionClick {onDismiss();onOpen()})
            ListItem(headlineContent={Text(if(favorite) "Quitar de favoritos" else "Marcar favorito")},leadingContent={Icon(Icons.Default.Star,null)},
                modifier=Modifier.actionClick {onFavorite();onDismiss()})
            ListItem(headlineContent={Text("Copiar prompt")},leadingContent={Icon(Icons.Default.ContentCopy,null)},
                modifier=Modifier.actionClick {clipboard.setText(AnnotatedString(job.prompt));onDismiss()})
            ListItem(headlineContent={Text(if(localUri==null) "Compartir enlace" else "Compartir archivo")},leadingContent={Icon(Icons.Default.Share,null)},
                modifier=Modifier.actionClick {
                    runCatching {
                        val intent=Intent(Intent.ACTION_SEND)
                        if(localUri!=null) {
                            val uri=Uri.parse(localUri)
                            val shared=if(uri.scheme=="file") FileProvider.getUriForFile(context,context.packageName+".fileprovider",File(uri.path!!)) else uri
                            intent.type=output.mimeType.ifBlank {"application/octet-stream"}
                            intent.putExtra(Intent.EXTRA_STREAM,shared)
                            intent.clipData=android.content.ClipData.newRawUri("Creación",shared)
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } else {intent.type="text/plain";intent.putExtra(Intent.EXTRA_TEXT,output.url)}
                        context.startActivity(Intent.createChooser(intent,"Compartir creación"))
                        onDismiss()
                    }.onFailure {onError("No se pudo compartir este archivo. Ábrelo para descargarlo de nuevo.")}
                })
            ListItem(headlineContent={Text(if(hidden) "Restaurar en la biblioteca" else "Ocultar de la biblioteca")},supportingContent={Text("Conserva el archivo y los trabajos anteriores")},leadingContent={Icon(Icons.Default.VisibilityOff,null)},
                modifier=Modifier.actionClick {onDismiss();onHide()})
        }
    }
}

private fun Modifier.actionClick(action:()->Unit):Modifier = this
    .then(Modifier.heightIn(min=48.dp))
    .then(Modifier.clickable(role=androidx.compose.ui.semantics.Role.Button,onClick=action))
