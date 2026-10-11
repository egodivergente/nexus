package com.vidadomu.nexus.assistant

import android.net.Uri
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.tools.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Native Converse documents. Original files stay private and are never converted or truncated. */
internal object AgentDocuments {
    const val MAX_BYTES = 4_500_000L
    val types = arrayOf("application/pdf", "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain", "text/markdown", "text/html", "text/csv",
        "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    fun format(mime: String, name: String?): String? {
        if(mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")) return null
        return when(mime) {
        "application/pdf" -> "pdf"
        "application/msword" -> "doc"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/vnd.ms-excel" -> "xls"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
        "text/markdown" -> "md"
        "text/html" -> "html"
        "text/csv" -> "csv"
        "text/plain" -> "txt"
        else -> name?.substringAfterLast('.')?.lowercase()?.takeIf {it in setOf("pdf","doc","docx","xls","xlsx","md","txt","html","csv")}
    }
    }
    fun mimeType(kind:String)=types.firstOrNull {format(it,null)==kind} ?: "text/plain"
    suspend fun load(attachment: Attachment): ContentBlock.Document? = withContext(Dispatchers.IO) {
        val format=format(attachment.mimeType,attachment.name) ?: return@withContext null
        val uri=Uri.parse(attachment.uri)
        require(uri.scheme=="file") {"Vuelve a adjuntar el documento para recuperar su copia local."}
        val file=File(uri.path.orEmpty())
        require(file.isFile && file.length() in 1..MAX_BYTES) {"Bedrock admite documentos de hasta 4,5 MB. Divide el archivo en capítulos y vuelve a adjuntarlo."}
        // Neutral ASCII name: original filename is already present in attachment metadata.
        ContentBlock.Document(format,"Document " + attachment.id.filter {it.isLetterOrDigit()}.take(40),file.readBytes())
    }
}
