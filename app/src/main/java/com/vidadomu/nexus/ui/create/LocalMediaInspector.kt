package com.vidadomu.nexus.ui.create
import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever

data class LocalMediaFacts(val bytes:Long,val width:Int?=null,val height:Int?=null,val seconds:Double?=null,val hasAudio:Boolean?=null) {
    val summary:String get()=listOfNotNull(width?.let {"$it × $height"},seconds?.let {"%.1f s".format(it)},if(bytes<0) null else if(bytes<1048576) "%.1f KB".format(bytes/1024.0) else "%.1f MB".format(bytes/1048576.0),hasAudio?.let {if(it) "Con audio" else "Sin audio"}).joinToString(" · ")
}
object LocalMediaInspector {
    fun inspect(context:Context,uri:Uri,mime:String):LocalMediaFacts {
        require(uri.scheme in setOf("file","content")) {"La inspección requiere un archivo local."}
        val bytes=if(uri.scheme=="file") java.io.File(uri.path.orEmpty()).length() else context.contentResolver.openAssetFileDescriptor(uri,"r")?.use {it.length} ?: -1
        require(bytes!=0L) {"El archivo está vacío."}
        require(bytes<=512L*1024*1024) {"El archivo supera el límite local de 512 MB."}
        if(mime.startsWith("image/")) {
            val opts=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            context.contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,opts)}
            require(opts.outWidth>0 && opts.outHeight>0) {"No se puede leer esta imagen. Prueba con PNG, JPEG o WebP."}
            return LocalMediaFacts(bytes,opts.outWidth,opts.outHeight)
        }
        if(mime.startsWith("video/") || mime.startsWith("audio/")) {
            val r=MediaMetadataRetriever()
            try {
                r.setDataSource(context,uri)
                val seconds=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull()?.div(1000)
                require(seconds!=null && seconds>0) {"El archivo multimedia no tiene una duración válida."}
                return LocalMediaFacts(bytes,r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),seconds,
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)=="yes")
            } finally {r.release()}
        }
        throw IllegalArgumentException("Formato de archivo no compatible.")
    }
}
