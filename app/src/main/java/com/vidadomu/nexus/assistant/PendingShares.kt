package com.vidadomu.nexus.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.vidadomu.nexus.agent.core.tools.Attachment

@Serializable internal data class SharedFile(val uri: String, val attachment: Attachment? = null, val error: String? = null)
@Serializable internal data class PendingShare(val id: String, val text: String = "", val files: List<SharedFile> = emptyList()) {
    val ready get() = files.all {it.attachment!=null}
}

/** Copies while Android's temporary grant is alive. The journal survives the destination chooser/process. */
internal class PendingShares(private val context: Context) {
    private val folder=File(context.filesDir,"assistant/incoming-shares").apply {mkdirs()}
    private val json=Json {ignoreUnknownKeys=true}
    fun pending(): List<PendingShare> = folder.listFiles().orEmpty().filter {it.extension=="json"}.sortedBy {it.lastModified()}
        .mapNotNull {runCatching {json.decodeFromString<PendingShare>(it.readText())}.getOrNull()}
    private fun write(batch: PendingShare) {
        val file=AtomicFile(File(folder,"${batch.id}.json"));val out=file.startWrite()
        try {out.write(json.encodeToString(batch).toByteArray(Charsets.UTF_8));file.finishWrite(out)}
        catch(e:Exception) {file.failWrite(out);throw e}
    }
    @Suppress("DEPRECATION") suspend fun receive(intent: Intent): PendingShare = withContext(Dispatchers.IO) {
        val streams=if(intent.action==Intent.ACTION_SEND_MULTIPLE) intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        val clips=intent.clipData?.let {clip->(0 until clip.itemCount).mapNotNull {clip.getItemAt(it).uri}}.orEmpty()
        var batch=PendingShare(UUID.randomUUID().toString(),intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty(),
            (streams+clips).distinct().map {SharedFile(it.toString())})
        write(batch)
        for(index in batch.files.indices) {
            val file=batch.files[index]
            val uri=Uri.parse(file.uri)
            runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            val imported=runCatching {AgentMediaStore.persist(context,uri,null)}
            batch=batch.copy(files=batch.files.mapIndexed {i,f->if(i!=index) f else f.copy(attachment=imported.getOrNull(),
                error=imported.exceptionOrNull()?.let {"No se pudo copiar este archivo. El lote se conserva para volver a intentarlo."})})
            write(batch)
        }
        batch
    }
    suspend fun retry(batch: PendingShare): PendingShare = withContext(Dispatchers.IO) {
        var next=batch
        batch.files.forEachIndexed {index,file->if(file.attachment==null) {
            val result=runCatching {AgentMediaStore.persist(context,Uri.parse(file.uri),null)}
            next=next.copy(files=next.files.mapIndexed {i,f->if(i!=index) f else f.copy(attachment=result.getOrNull(),error=if(result.isFailure) "El origen ya no permite leer el archivo. Vuelve a compartirlo; el resto del lote se conserva." else null)})
            write(next)
        }}
        next
    }
    fun consumed(id: String) {require(Regex("[a-zA-Z0-9-]+").matches(id));AtomicFile(File(folder,"$id.json")).delete()}
}
