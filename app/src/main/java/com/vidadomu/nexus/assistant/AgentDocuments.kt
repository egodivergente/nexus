package com.vidadomu.nexus.assistant

import android.net.Uri
import com.vidadomu.nexus.agent.core.llm.ContentBlock
import com.vidadomu.nexus.agent.core.tools.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.File

/** Read text locally where possible; preserve every original document. */
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
    /**
     * [textOnly]: the agent only receives text (Antigravity), so a PDF always goes as its extracted text and other binary
     * formats cannot be read. [maxBytes]: the provider's limit for an original document (Bedrock: 4,5 MB); null = no limit here.
     */
    suspend fun load(attachment: Attachment, textOnly: Boolean = false, maxBytes: Long? = MAX_BYTES): ContentBlock.Document? = withContext(Dispatchers.IO) {
        val format=format(attachment.mimeType,attachment.name) ?: return@withContext null
        val uri=Uri.parse(attachment.uri)
        require(uri.scheme=="file") {"Vuelve a adjuntar el documento para recuperar su copia local."}
        val file=File(uri.path.orEmpty())
        require(file.isFile && file.length()>0) {"Vuelve a adjuntar el documento: su copia local está vacía o no existe."}
        // Un PDF por encima del límite del proveedor no se rechaza: se envía todo su texto (las imágenes son lo que pesa).
        val tooBig=maxBytes!=null && file.length()>maxBytes
        if(format=="pdf" && android.os.Build.VERSION.SDK_INT>=35) {
            val extracted=extractPdf(file,keepImagePages=tooBig || textOnly)
            if(extracted!=null) return@withContext ContentBlock.Document("txt","Document " + attachment.id.filter {it.isLetterOrDigit()}.take(40),extracted.toByteArray(Charsets.UTF_8))
        }
        if(textOnly) require(format in setOf("txt","md","csv","html")) {
            "Antigravity solo recibe texto y no se ha podido sacar el texto de este ${format.uppercase()}. Envíalo como PDF con texto o como .txt."
        }
        require(!tooBig) {"El documento pesa %.1f MB, este agente admite hasta %.1f MB y no tiene texto que se pueda extraer. Divídelo en partes y vuelve a adjuntarlo."
            .format(java.util.Locale("es","ES"),file.length()/1_000_000.0,(maxBytes ?: 0L)/1_000_000.0)}
        // Neutral ASCII name: original filename is already present in attachment metadata.
        ContentBlock.Document(format,"Document " + attachment.id.filter {it.isLetterOrDigit()}.take(40),file.readBytes())
    }
    @android.annotation.TargetApi(35)
    /**
     * Text of every page. Normally a page with images or without text means the original PDF must go instead (null).
     * With [keepImagePages] (the original is too big to send) those pages are noted instead, and null means no text at all.
     */
    private suspend fun extractPdf(file:File,keepImagePages:Boolean=false):String? {
        val text=StringBuilder(if(keepImagePages) "Extracción textual del PDF (el agente no puede recibir el original). Las imágenes y gráficos no se han enviado; el original se conserva.\n"
            else "Extracción textual del PDF. Las imágenes y gráficos no se han analizado; el original se conserva.\n")
        var anyText=false
        android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            android.graphics.pdf.PdfRenderer(descriptor).use { pdf ->
                // Pages without extractable text require the original visual document.
                for(i in 0 until pdf.pageCount) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    pdf.openPage(i).use {page ->
                        val content=page.textContents.joinToString("\n") {it.text}
                        val images=page.imageContents.isNotEmpty()
                        if(!keepImagePages && (content.isBlank() || images)) return null
                        text.append("\n[Pagina ${i+1}]\n").append(content)
                        if(images || content.isBlank()) text.append("\n(Esta página tiene imágenes que no se han enviado.)")
                        anyText=anyText || content.isNotBlank()
                    }
                }
            }
        }
        return text.toString().takeIf {anyText}
    }
}
