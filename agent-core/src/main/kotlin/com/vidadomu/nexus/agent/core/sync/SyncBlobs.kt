package com.vidadomu.nexus.agent.core.sync

import com.vidadomu.nexus.agent.core.storage.ContentHashes
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*

/**
 * Device-independent references. Copies are published only after hash verification.
 *
 * With [hashes] and [adoptable] (Android), a private Nexus file is not copied into this folder: its hash is
 * remembered, it is uploaded from where it is, and the reference resolves back to it. Before, every synchronised
 * attachment or export existed twice on the phone (measured on the S25: 7,6 GB of copies, 31/10/2026).
 * Without them (Desktop and tests), behaviour is the previous one: a verified copy per reference.
 */
class SyncBlobs(val directory:File,private val remote:SyncRemote,
                private val hashes:ContentHashes?=null,private val adoptable:(File)->Boolean={false}) {
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
        private val POINTERS=MapSerializer(String.serializer(),String.serializer())
    }
    init {directory.mkdirs()}
    private val pointerFile=File(directory,"local-originals.json")
    private val pointers:MutableMap<String,String> by lazy {
        runCatching {Json.decodeFromString(POINTERS,pointerFile.readText()).toMutableMap()}.getOrElse {mutableMapOf()}
    }
    private fun sha(file:File)=hashes?.sha256(file)?:hash(file)

    fun capture(input:InputStream,extension:String):String {
        require(extension.matches(Regex("[a-z0-9]{1,8}")))
        val stage=File.createTempFile("capture-",".partial",directory)
        try {
            input.use {source->stage.outputStream().use {target->source.copyTo(target,65536);target.fd.sync()}}
            val name=hash(stage)+"."+extension;val destination=File(directory,name)
            if(!destination.exists())java.nio.file.Files.move(stage.toPath(),destination.toPath())
            hashes?.record(destination,name.substringBefore('.'))
            remote.putBlob(name,destination)
            return PREFIX+name
        } finally {stage.delete()}
    }

    /** Like [capture], but a local file that is already stored (here, or as an adoptable original) is never copied. */
    fun capture(input:SyncInput):String {
        val local=input.file?.takeIf {it.isFile}
        if(local!=null && input.extension.matches(Regex("[a-z0-9]{1,8}"))) {
            val name=sha(local)+"."+input.extension
            val destination=File(directory,name)
            val source=when {
                destination.isFile->destination
                adoptable(local)->local.also {remember(name,it)}
                else->null
            }
            if(source!=null) {
                runCatching {input.stream.close()}
                remote.putBlob(name,source)
                return PREFIX+name
            }
        }
        return capture(input.stream,input.extension)
    }

    @Synchronized private fun remember(name:String,file:File) {
        if(pointers[name]==file.absolutePath)return
        pointers[name]=file.absolutePath
        SyncJournal.atomic(pointerFile,Json.encodeToString(POINTERS,pointers))
    }

    /** The adopted original for [name], if it still has exactly those bytes. */
    @Synchronized private fun original(name:String):File? {
        val expected=name.substringBefore('.')
        val pointed=pointers[name]?:return null
        File(pointed).takeIf {it.isFile && sha(it)==expected}?.let {return it}
        // The adopted file was renamed, or removed as an exact duplicate: follow a remaining twin instead of
        // downloading the same bytes again.
        val twin=hashes?.find(expected,null) {adoptable(it)}?:return null
        remember(name,twin)
        return twin
    }

    fun existing(reference:String):File? {
        val name=reference.removePrefix(PREFIX);if(!reference.startsWith(PREFIX)||!NAME.matches(name))return null
        return File(directory,name).takeIf {it.isFile}?:original(name)
    }
    fun obtain(reference:String):File {
        require(reference.startsWith(PREFIX));val name=reference.removePrefix(PREFIX);require(NAME.matches(name))
        val destination=File(directory,name)
        if(destination.exists() && sha(destination)==name.substringBefore('.'))return destination
        original(name)?.let {return it}
        val stage=File(directory,name+".partial")
        try {
            remote.getBlob(name,stage)
            require(hash(stage)==name.substringBefore('.')) {"El archivo descargado no supera la comprobación de integridad."}
            java.nio.file.Files.move(stage.toPath(),destination.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            hashes?.record(destination,name.substringBefore('.'))
            return destination
        } finally {stage.delete()}
    }
}
