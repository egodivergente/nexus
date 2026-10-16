package com.vidadomu.nexus.assistant

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.assets.*
import com.vidadomu.nexus.agent.core.tools.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

/** Actual downloadable artifacts, indexed alongside imported media. */
internal class AgentArtifacts(private val app:NexusApplication,private val assets:AssetIndex) {
    suspend fun create(input:JsonObject,session:AgentSession):JsonObject = withContext(Dispatchers.IO) {
        val format=input.requiredText("format").lowercase()
        if(format !in setOf("pdf","txt","md","html","csv","json","py","js","sh","ps1","srt")) throw ToolInputException("Formato de documento no admitido.")
        val title=input.requiredText("title")
        val folder=File(app.filesDir,"agent_exports").apply {mkdirs()}
        val base=title.replace(Regex("[^\\p{L}\\p{N} _-]"),"_").take(70).ifBlank {"Documento"}
        val output=File(folder,"${base}_${UUID.randomUUID().toString().take(8)}.$format")
        var pages=0
        try {
            if(format=="pdf") {
                val blocks=input.text("blocks_json")?.let {Json.parseToJsonElement(it) as? JsonArray}
                    ?: JsonArray(listOf(buildJsonObject {put("text",input.requiredText("content"))}))
                val pdf=PdfDocument()
                try {
                    var page:PdfDocument.Page?=null; var y=48f
                    fun nextPage() {page?.let(pdf::finishPage);pages++;page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,pages).create());y=48f}
                    suspend fun text(body:String,size:Float=12f) {
                        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(42,36,31);textSize=size;typeface=Typeface.create("sans-serif",if(size>14) Typeface.BOLD else Typeface.NORMAL)}
                        val layout=StaticLayout.Builder.obtain(body,0,body.length,paint,499).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(3f,1f).build()
                        for(line in 0 until layout.lineCount) {
                            currentCoroutineContext().ensureActive()
                            val h=(layout.getLineBottom(line)-layout.getLineTop(line)).toFloat()
                            if(page==null || y+h>794) nextPage()
                            val canvas=page!!.canvas;canvas.save();canvas.clipRect(48f,y,547f,y+h)
                            canvas.translate(48f,y-layout.getLineTop(line));layout.draw(canvas);canvas.restore();y+=h
                        }; y+=12
                    }
                    text(title,22f)
                    for(block in blocks) {
                        val item=block.jsonObject
                        item.text("text")?.let {text(it)}
                        item.text("image_asset_id")?.let {id ->
                            val asset=assets.asset(id)?.takeIf {it.type==AssetType.IMAGE && !it.libraryDeleted}
                                ?: throw ToolInputException("No se encontró la imagen $id en la biblioteca.")
                            val uri=Uri.parse(AgentVideoOperations(app).source(asset.id).uri)
                            if(uri.scheme !in setOf("file","content")) throw ToolInputException("Descarga la imagen antes de insertarla en el PDF.")
                            val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                            app.contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,bounds)}
                            val options=BitmapFactory.Options().apply {inSampleSize=1;while(maxOf(bounds.outWidth,bounds.outHeight)/inSampleSize>1600) inSampleSize*=2}
                            val bitmap=app.contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,options)}
                                ?: throw ToolInputException("No se pudo leer la imagen.")
                            try {
                                val scale=minOf(499f/bitmap.width,600f/bitmap.height)
                                val w=bitmap.width*scale;val h=bitmap.height*scale
                                if(y+h>794) nextPage()
                                page!!.canvas.drawBitmap(bitmap,null,RectF(48f,y,48f+w,y+h),Paint(Paint.FILTER_BITMAP_FLAG));y+=h+12
                            } finally {bitmap.recycle()}
                            item.text("caption")?.let {text(it,10f)}
                        }
                    }
                    page?.let(pdf::finishPage)
                    output.outputStream().use {pdf.writeTo(it);it.fd.sync()}
                } finally {pdf.close()}
            } else output.writeText(input.requiredText("content"),Charsets.UTF_8)
            val mime=when(format) {"pdf"->"application/pdf";"html"->"text/html";"json"->"application/json";else->"text/plain"}
            val asset=assets.addAsset(Asset(id="",uri=Uri.fromFile(output).toString(),type=AssetType.OTHER,role=AssetRole.OUTPUT,
                displayName=output.name,mimeType=mime,projectId=session.activeProjectId,createdAt=System.currentTimeMillis()))
            session.showMedia(Attachment(asset.id,asset.uri,mime,asset.displayName,asset.width,asset.height,asset.durationSec,asset.id))
            buildJsonObject {put("asset_id",asset.id);put("name",asset.displayName);put("uri",asset.uri);put("bytes",output.length());put("pages",pages);put("location","Biblioteca → Importados y ediciones");put("script_executed",false)}
        } catch(e:Exception) {output.delete();throw e}
    }
    fun read(input:JsonObject):JsonObject {
        val asset=assets.asset(input.requiredText("asset_id")) ?: throw ToolInputException("Archivo desconocido.")
        if(asset.mimeType?.startsWith("text/")!=true && asset.mimeType!="application/json") throw ToolInputException("Usa los adjuntos para leer PDF o las herramientas de medios para audio y vídeo.")
        val uri=Uri.parse(asset.uri)
        if(uri.scheme !in setOf("file","content")) throw ToolInputException("Descarga el archivo primero.")
        val content=app.contentResolver.openInputStream(uri)?.bufferedReader()?.use {val chars=CharArray(40001);var n=0;while(n<chars.size){val c=it.read(chars,n,chars.size-n);if(c<0)break;n+=c};String(chars,0,n)} ?: throw ToolInputException("No se pudo leer el archivo.")
        return buildJsonObject {put("asset_id",asset.id);put("text",content.take(40000));put("truncated",content.length>40000)}
    }
}
