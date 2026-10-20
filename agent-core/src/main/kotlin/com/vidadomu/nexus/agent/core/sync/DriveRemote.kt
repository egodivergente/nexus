package com.vidadomu.nexus.agent.core.sync

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody

/** Drive access is restricted to files created/selected by this Google OAuth application. */
class DriveRemote(
    private val token:()->String,private val rootId:String,
    private val client:OkHttpClient=OkHttpClient.Builder().followRedirects(false).connectTimeout(20,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS).build(),
    private val sessionDirectory:File?=null,
) : SyncRemote {
    companion object {
        const val SCOPE="https://www.googleapis.com/auth/drive.file"
        private const val API="https://www.googleapis.com/drive/v3"
        private const val UPLOAD="https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable"
        private val JSON="application/json; charset=utf-8".toMediaType()
        fun findOrCreateRoot(token:()->String):String {
            val drive=DriveRemote(token,"")
            val roots=drive.list("trashed = false and mimeType = 'application/vnd.google-apps.folder' and appProperties has { key='nexusSyncRoot' and value='1' }")
            require(roots.size<=1) {"Hay varias carpetas Nexus. Selecciona la carpeta compartida antes de continuar."}
            roots.firstOrNull()?.let {return it.id}
            val metadata=buildJsonObject {put("name","Nexus");put("mimeType","application/vnd.google-apps.folder");putJsonObject("appProperties"){put("nexusSyncRoot","1")}}
            return drive.objectResponse(Request.Builder().url(API+"/files?fields=id").post(metadata.toString().toRequestBody(JSON)))["id"]!!.jsonPrimitive.content
        }
    }
    private val known=mutableMapOf<String,SyncObject>()
    init {require(rootId.isEmpty() || rootId.matches(Regex("[a-zA-Z0-9_-]{1,200}")))}
    private fun request(builder:Request.Builder,allowed:Set<Int> = emptySet()):Response {
        val response=client.newCall(builder.header("Authorization","Bearer "+token()).build()).execute()
        if(response.isSuccessful || response.code==308 || response.code in allowed)return response
        val code=response.code;response.close()
        error(when(code){401->"Vuelve a conectar Google Drive.";403->"Google Drive no permite esta operación. Comprueba permisos y cuota.";429->"Google Drive está ocupado. Se reintentará.";else->"Google Drive no respondió correctamente ($code)."})
    }
    private fun objectResponse(builder:Request.Builder):JsonObject=request(builder).use {
        val body=it.body?:error("Respuesta vacía de Google Drive.")
        require(body.contentLength()<=8_388_608)
        val raw=bounded(body.byteStream())
        require(raw.size<=8_388_608)
        Json.parseToJsonElement(raw.decodeToString()).jsonObject
    }
    private fun bounded(source:java.io.InputStream):ByteArray=source.use {
        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(65536)
        while(true){val count=it.read(buffer);if(count<0)break;require(output.size()+count<=8_388_608);output.write(buffer,0,count)}
        output.toByteArray()
    }
    private fun list(query:String):List<SyncObject> {
        val result=mutableListOf<SyncObject>();var page:String?=null
        do {
            val url=HttpUrl.Builder().scheme("https").host("www.googleapis.com").addPathSegments("drive/v3/files")
                .addQueryParameter("q",query).addQueryParameter("fields","nextPageToken,files(id,name,size)")
                .addQueryParameter("pageSize","1000")
            page?.let {url.addQueryParameter("pageToken",it)}
            val data=objectResponse(Request.Builder().url(url.build()).get())
            for(row in data["files"]!!.jsonArray){val f=row.jsonObject;result+=SyncObject(f["id"]!!.jsonPrimitive.content,f["name"]!!.jsonPrimitive.content,f["size"]?.jsonPrimitive?.longOrNull?:0)}
            page=data["nextPageToken"]?.jsonPrimitive?.contentOrNull
            require(result.size<=100_000) {"La biblioteca supera el límite de esta versión."}
        }while(page!=null)
        return result
    }
    override fun commits():List<SyncObject> = list("'$rootId' in parents and trashed = false and name contains 'commit-'")
        .filter {it.name.matches(Regex("commit-[a-f0-9-]{36}\\.json"))}
    override fun readCommit(item:SyncObject):ByteArray=request(Request.Builder().url(API+"/files/"+item.id+"?alt=media").get()).use {
        val bytes=bounded(it.body!!.byteStream())
        require(bytes.size<=8_388_608);bytes
    }
    private fun find(name:String):SyncObject? {
        known[name]?.let {return it}
        val found=list("'$rootId' in parents and trashed = false and name = '$name'")
        return found.firstOrNull()?.also {known[name]=it}
    }
    private fun upload(name:String,size:Long,read:(Long,Int)->ByteArray) {
        val sessionFile=sessionDirectory?.let {dir->dir.mkdirs();File(dir,name+".session")}
        fun validate(location:String):String {
            val url=location.toHttpUrl()
            require(url.scheme=="https" && url.host=="www.googleapis.com" && url.encodedPath.startsWith("/upload/"))
            return location
        }
        val metadata=buildJsonObject {put("name",name);putJsonArray("parents"){add(rootId)}}
        fun start():String=request(Request.Builder().url(UPLOAD).header("X-Upload-Content-Type","application/octet-stream")
            .header("X-Upload-Content-Length",size.toString()).post(metadata.toString().toRequestBody(JSON))).use {
            validate(it.header("Location")?:error("Google Drive no inició la transferencia."))
        }
        var location=sessionFile?.takeIf {it.exists()}?.readText()?.let(::validate)?:start().also {sessionFile?.let {f->SyncJournal.atomic(f,it)}}
        var offset=0L
        fun position(response:Response):Long=response.header("Range")?.let {Regex("bytes=0-([0-9]+)").matchEntire(it)?.groupValues?.get(1)?.toLong()?.plus(1)}?:0
        if(sessionFile?.exists()==true) request(Request.Builder().url(location).header("Content-Range","bytes */$size").put(ByteArray(0).toRequestBody()),setOf(404,410)).use {
            if(it.code in setOf(404,410)){location=start();SyncJournal.atomic(sessionFile,location)}
            else if(it.isSuccessful){val result=Json.parseToJsonElement(it.body!!.string()).jsonObject;known[name]=SyncObject(result["id"]!!.jsonPrimitive.content,name,size);sessionFile.delete();return}
            else offset=position(it)
        }
        do {
            require(offset in 0..size)
            val count=minOf(4_194_304L,size-offset).toInt();val bytes=read(offset,count);require(bytes.size==count)
            val range=if(size==0L)"bytes */0" else "bytes $offset-${offset+count-1}/$size"
            request(Request.Builder().url(location).header("Content-Range",range).put(bytes.toRequestBody("application/octet-stream".toMediaType()))).use {
                if(it.isSuccessful){val result=Json.parseToJsonElement(it.body!!.string()).jsonObject;known[name]=SyncObject(result["id"]!!.jsonPrimitive.content,name,size);sessionFile?.delete();return}
                val next=position(it);require(next>offset && next<=size) {"La transferencia no ha progresado. Se reintentará."};offset=next
            }
        }while(offset<size)
        error("La transferencia no se ha confirmado. Se reintentará.")
    }
    override fun putCommit(commit:SyncCommit,bytes:ByteArray) {
        val name="commit-"+commit.id+".json"
        if(find(name)!=null)return
        upload(name,bytes.size.toLong()){offset,count->bytes.copyOfRange(offset.toInt(),offset.toInt()+count)}
    }
    override fun putBlob(name:String,file:File) {
        require(SyncBlobs.NAME.matches(name))
        if(find("blob-"+name)!=null)return
        upload("blob-"+name,file.length()){offset,count->java.io.RandomAccessFile(file,"r").use {source->source.seek(offset);ByteArray(count).also {source.readFully(it)}}}
    }
    override fun getBlob(name:String,destination:File) {
        require(SyncBlobs.NAME.matches(name));val item=find("blob-"+name)?:error("El archivo todavía no está disponible en Drive.")
        request(Request.Builder().url(API+"/files/"+item.id+"?alt=media").get()).use {response->
            response.body!!.byteStream().use {source->destination.outputStream().use {target->source.copyTo(target,65536);target.fd.sync()}}
        }
    }
}
