package com.vidadomu.nexus.agent.core.sync

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.serialization.json.*

/** Device-independent references. Copies are published only after hash verification. */
class SyncBlobs(val directory:File,private val remote:SyncRemote) {
    companion object {
        const val PREFIX="nexus-sync:"
        val NAME=Regex("[a-f0-9]{64}\\.[a-z0-9]{1,8}")
        fun hash(file:File):String=file.inputStream().use {source->
            val h=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536)
            while(true){val count=source.read(buffer);if(count<0)break;h.update(buffer,0,count)}
            h.digest().joinToString(""){"%02x".format(it)}
        }
        fun rewrite(value:JsonElement,transform:(String)->String):JsonElement=when(value) {
            is JsonObject->JsonObject(value.mapValues {rewrite(it.value,transform)})
            is JsonArray->JsonArray(value.map {rewrite(it,transform)})
            is JsonPrimitive->if(value.isString)JsonPrimitive(transform(value.content))else value
        }
    }
    init {directory.mkdirs()}
    fun capture(input:InputStream,extension:String):String {
        require(extension.matches(Regex("[a-z0-9]{1,8}")))
        val stage=File.createTempFile("capture-",".partial",directory)
        try {
            input.use {source->stage.outputStream().use {target->source.copyTo(target,65536);target.fd.sync()}}
            val name=hash(stage)+"."+extension;val destination=File(directory,name)
            if(!destination.exists())java.nio.file.Files.move(stage.toPath(),destination.toPath())
            remote.putBlob(name,destination)
            return PREFIX+name
        } finally {stage.delete()}
    }
    fun existing(reference:String):File? {
        val name=reference.removePrefix(PREFIX);if(!reference.startsWith(PREFIX)||!NAME.matches(name))return null
        return File(directory,name).takeIf {it.isFile}
    }
    fun obtain(reference:String):File {
        require(reference.startsWith(PREFIX));val name=reference.removePrefix(PREFIX);require(NAME.matches(name))
        val destination=File(directory,name)
        if(destination.exists() && hash(destination)==name.substringBefore('.'))return destination
        val stage=File(directory,name+".partial")
        try {
            remote.getBlob(name,stage)
            require(hash(stage)==name.substringBefore('.')) {"El archivo descargado no supera la comprobación de integridad."}
            java.nio.file.Files.move(stage.toPath(),destination.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            return destination
        } finally {stage.delete()}
    }
}
