package com.vidadomu.nexus.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.vidadomu.nexus.core.model.*
import java.io.File
import com.vidadomu.nexus.ui.output.OutputMetadataDetails
import com.vidadomu.nexus.ui.theme.NexusSheetSystemBars
import com.vidadomu.nexus.ui.theme.NexusTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The same options for every Library file, generated or imported; only what does not apply to it is left out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryFileSheet(entry:LibraryEntry,name:String,localUri:String?,favorite:Boolean,hidden:Boolean,
    onDismiss:()->Unit,onRename:()->Unit,onSelect:()->Unit,onFavorite:()->Unit,onHide:()->Unit,onDelete:()->Unit,onMessage:(String)->Unit,
    onAddToChat:(()->Unit)?,onUseInCanvas:(()->Unit)?,onOpenResult:(()->Unit)?,onCrop:(()->Unit)?=null,onMove:(()->Unit)?=null,
    /** Opens the full-screen local editor (mask, erase, inpaint) on this image. */
    onEdit:(()->Unit)?=null) {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    val scope=rememberCoroutineScope()
    val output=(entry as? LibraryEntry.Out)?.let {it.job.outputs[it.index]}
    val asset=(entry as? LibraryEntry.Local)?.asset
    val mime=output?.mimeType?.ifBlank {null} ?: asset?.resolvedMime() ?: "application/octet-stream"
    val exporter:com.vidadomu.nexus.ui.output.OutputExportViewModel=androidx.lifecycle.viewmodel.compose.viewModel()
    val export by exporter.state.collectAsState()
    var copying by remember {mutableStateOf(false)}
    val savePicker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument(mime)) {destination ->
        if(output!=null) exporter.save(destination)
        else if(destination!=null && asset!=null) {copying=true;scope.launch {
            try {withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(Uri.parse(asset.uri))!!.use {input ->
                    context.contentResolver.openOutputStream(destination,"w")!!.use {out -> input.copyTo(out)}
                }
            };onMessage("Archivo guardado");onDismiss()}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                runCatching {android.provider.DocumentsContract.deleteDocument(context.contentResolver,destination)}
                onMessage("No se pudo guardar el archivo")
            }
            finally {copying=false}
        }}
    }
    LaunchedEffect(export.message) {export.message?.let {onMessage(it);exporter.dismissMessage()}}
    val busy=export.busy || copying
    fun send(view:Boolean) {
        runCatching {
            val intent=Intent(if(view) Intent.ACTION_VIEW else Intent.ACTION_SEND)
            val raw=localUri ?: asset?.uri
            if(raw!=null) {
                val uri=Uri.parse(raw).let {if(it.scheme=="file") FileProvider.getUriForFile(context,context.packageName+".fileprovider",File(it.path!!)) else it}
                if(view) intent.setDataAndType(uri,mime) else {intent.type=mime;intent.putExtra(Intent.EXTRA_STREAM,uri)}
                intent.clipData=android.content.ClipData.newRawUri(name,uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else if(output!=null) {intent.type="text/plain";intent.putExtra(Intent.EXTRA_TEXT,output.url)}
            context.startActivity(Intent.createChooser(intent,if(view) "Abrir con" else "Compartir"))
            onDismiss()
        }.onFailure {onMessage("No hay una aplicación disponible para este archivo")}
    }
    ModalBottomSheet(onDismissRequest={if(!busy) onDismiss()},shape=NexusTokens.sheet) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=NexusTokens.space16).padding(bottom=NexusTokens.space24)) {
            Text(name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            if(entry is LibraryEntry.Out) OutputMetadataDetails(entry.job,entry.job.outputs[entry.index],localUri)
            if(asset!=null) LocalAssetFacts(asset)
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=NexusTokens.space8))
            onAddToChat?.let {add -> Option(Icons.Default.SmartToy,"Al Agente") {onDismiss();add()}}
            onUseInCanvas?.let {use -> Option(Icons.Default.AutoAwesome,"Usar en Lienzo") {onDismiss();use()}}
            Option(Icons.Default.CheckCircle,"Seleccionar") {onDismiss();onSelect()}
            Option(Icons.Default.Edit,"Cambiar el nombre") {onRename()}
            onMove?.let {move -> Option(Icons.Default.DriveFileMove,"Cambiar de proyecto") {onDismiss();move()}}
            onEdit?.let {edit -> Option(Icons.Default.Brush,"Editar foto") {onDismiss();edit()}}
            onCrop?.let {crop -> Option(Icons.Default.Crop,"Recortar") {onDismiss();crop()}}
            Option(Icons.Default.Star,if(favorite) "Quitar de favoritos" else "Marcar favorito") {onFavorite();onDismiss()}
            Option(Icons.Default.Share,if(localUri==null && asset==null) "Compartir enlace" else "Compartir") {send(false)}
            Option(Icons.Default.Download,if(busy) "Guardando…" else "Guardar una copia") {
                if(!busy) {
                    if(output!=null) exporter.prepare(output,localUri)
                    savePicker.launch(asset?.displayName ?: "Nexus-${output?.id}."+(android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"))
                }
            }
            if(localUri!=null || asset!=null) Option(Icons.Default.OpenInNew,"Abrir con otra aplicación") {send(true)}
            if(entry is LibraryEntry.Out) {
                if(entry.job.prompt.isNotBlank()) Option(Icons.Default.ContentCopy,"Copiar prompt") {clipboard.setText(AnnotatedString(entry.job.prompt));onDismiss()}
                onOpenResult?.let {open -> Option(Icons.Default.Info,"Ficha del resultado") {onDismiss();open()}}
            }
            Option(Icons.Default.VisibilityOff,if(hidden) "Restaurar en la biblioteca" else "Ocultar de la biblioteca") {onDismiss();onHide()}
            Option(Icons.Default.DeleteOutline,"Eliminar de la biblioteca") {onDismiss();onDelete()}
        }
    }
}

/** Rename a Library file. The name only changes how Nexus shows it; the file itself is untouched. */
@Composable
internal fun RenameFileDialog(current:String,onDismiss:()->Unit,title:String="Cambiar el nombre",onSave:(String)->Unit) {
    var text by remember {mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(current,androidx.compose.ui.text.TextRange(0,current.substringBeforeLast('.').length)))}
    val focus=remember {androidx.compose.ui.focus.FocusRequester()}
    LaunchedEffect(Unit) {focus.requestFocus()}
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},
        text={OutlinedTextField(text,{text=it},singleLine=true,modifier=Modifier.fillMaxWidth().focusRequester(focus))},
        confirmButton={TextButton(enabled=text.text.isNotBlank(),onClick={onSave(text.text.trim())}) {Text("Guardar")}},
        dismissButton={TextButton(onClick=onDismiss) {Text("Cancelar")}})
}

/** Where to move the marked files: a project folder, out of any folder, or a new folder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveToFolderSheet(count:Int,folders:List<com.vidadomu.nexus.agent.core.assets.Project>,current:String?,
    onDismiss:()->Unit,onNewFolder:()->Unit,onPick:(String?)->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss,shape=NexusTokens.sheet) {
        NexusSheetSystemBars()
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=NexusTokens.space16).padding(bottom=NexusTokens.space24)) {
            Text(if(count==1) "Mover 1 archivo a…" else "Mover $count archivos a…",style=MaterialTheme.typography.titleMedium)
            folders.filter {it.id!=current}.forEach {folder -> Option(Icons.Default.Folder,folder.name) {onPick(folder.id)}}
            if(current!=null) Option(Icons.Default.PhotoLibrary,"Biblioteca (sin proyecto)") {onPick(null)}
            Option(Icons.Default.CreateNewFolder,"Nueva carpeta…",onNewFolder)
        }
    }
}

/** A project shown as a folder in the Library. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun FolderTile(name:String,count:Int,onLongClick:(()->Unit)?=null,onOpen:()->Unit) {
    // Uniform tiles: the name always reserves two lines and the count is a short badge beside the icon,
    // so «950 archivos» no longer wraps and makes one folder taller than its neighbours.
    Surface(shape=NexusTokens.thumbnail,color=MaterialTheme.colorScheme.surfaceContainer,
        modifier=Modifier.fillMaxWidth().semantics(mergeDescendants=true) {contentDescription="Carpeta $name, ${if(count==1) "1 archivo" else "$count archivos"}"}
            .combinedClickable(onClickLabel="Abrir carpeta",onLongClickLabel="Opciones de la carpeta",onClick=onOpen,onLongClick=onLongClick)) {
        Column(Modifier.padding(NexusTokens.space12),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.Folder,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text(folderCount(count),style=MaterialTheme.typography.labelSmall,color=com.vidadomu.nexus.ui.theme.TextSecondary,maxLines=1)
            }
            Text(name,style=MaterialTheme.typography.labelLarge,minLines=2,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Option(icon:ImageVector,label:String,action:()->Unit) {
    ListItem(headlineContent={Text(label)},leadingContent={Icon(icon,null)},
        modifier=Modifier.heightIn(min=48.dp).clickable(role=androidx.compose.ui.semantics.Role.Button,onClick=action))
}

/** Short folder count for the badge: 950, 1,2 mil… (the full number is read out by TalkBack). */
internal fun folderCount(count: Int): String = when {
    count < 1000 -> count.toString()
    count < 10_000 -> "%.1f mil".format(java.util.Locale("es", "ES"), count / 1000.0).replace(",0 mil", " mil")
    else -> "${count / 1000} mil"
}
