package com.vidadomu.nexus.agent.core.sync

import com.vidadomu.nexus.agent.core.assets.AssetIndex
import com.vidadomu.nexus.agent.core.assets.IndexData
import java.io.File
import java.io.InputStream
import kotlinx.serialization.json.*

data class SyncInput(val extension:String,val stream:InputStream)

/** The same adapter is used by Android and the Windows JVM process. */
class SyncWorkspace(
    private val index:AssetIndex,private val directory:File,private val remote:SyncRemote,
    private val open:(String)->SyncInput?,private val localUri:(File)->String={it.toURI().toString()},deviceLabel:String="Dispositivo",
) {
    private val blobs=SyncBlobs(File(directory,"blobs"),remote)
    private val journal=SyncJournal(File(directory,"journal.json"),remote,deviceLabel)
    private val collections=listOf("projects","scenes","shots","characters","assets","generations")
    suspend fun cycle(downloadProjects:Set<String>?,extras:Map<String,JsonObject> = emptyMap(),applyExtras:(Map<String,JsonObject>)->Unit = {}):JsonObject {
        val expected=index.snapshot()
        val encoded=AssetIndex.JSON.encodeToJsonElement(IndexData.serializer(),expected).jsonObject
        val missing=linkedSetOf<String>();val replacements=mutableMapOf<String,String>()
        fun portable(uri:String):String {
            if(uri.startsWith(SyncBlobs.PREFIX))return uri
            replacements[uri]?.let {return it}
            return try {
                val input=open(uri)?:return uri.also {missing+=uri}
                blobs.capture(input.stream,input.extension).also {replacements[uri]=it}
            }catch(e:java.io.IOException){missing+=uri;uri}
        }
        val rows=linkedMapOf<String,JsonObject>()
        for(kind in collections)for(element in encoded[kind]?.jsonArray.orEmpty()) {
            var row=element.jsonObject
            if(kind=="assets")row=JsonObject(row+mapOf("uri" to JsonPrimitive(portable(row["uri"]!!.jsonPrimitive.content))))
            if(kind=="generations")row=JsonObject(row+mapOf("referenceUris" to JsonArray(row["referenceUris"]?.jsonArray.orEmpty().map {JsonPrimitive(portable(it.jsonPrimitive.content))})))
            rows[kind+":"+row["id"]!!.jsonPrimitive.content]=row
        }
        rows.putAll(extras.mapValues {(_,value)->SyncBlobs.rewrite(value){uri->if(uri.startsWith("file:")||uri.startsWith("content:")||uri.startsWith("/media/"))portable(uri)else replacements[uri]?:uri}.jsonObject})
        val result=journal.exchange(rows)
        val downloaded=mutableMapOf<String,String>()
        fun deviceUri(uri:String,download:Boolean):String {
            if(!uri.startsWith(SyncBlobs.PREFIX))return uri
            downloaded[uri]?.let {return it}
            val file=if(download)blobs.obtain(uri) else blobs.existing(uri)
            return file?.let(localUri)?.also {downloaded[uri]=it}?:uri
        }
        val localized=result.rows.mapValues {(key,row)->
            val project=row["projectId"]?.jsonPrimitive?.contentOrNull
                ?:if(key.startsWith("projects:"))row["id"]?.jsonPrimitive?.contentOrNull else null
            SyncBlobs.rewrite(row){deviceUri(it,downloadProjects==null || project==null || project in downloadProjects)}.jsonObject
        }.toMutableMap()
        // Manual copies can have different asset IDs. Keep their history/references,
        // but show identical bytes only once in each project's library.
        var deduplicated=0
        val copies=result.rows.filter { (key,row)->
            val uri=row["uri"]?.jsonPrimitive?.contentOrNull.orEmpty()
            key.startsWith("assets:") && row["libraryDeleted"]?.jsonPrimitive?.booleanOrNull!=true &&
                (uri.startsWith(SyncBlobs.PREFIX) || uri.startsWith("https://"))
        }.entries.groupBy { (_,row)->
            val uri=row["uri"]!!.jsonPrimitive.content
            // An unavailable original is still one library pointer when the URL is
            // exactly equal. Keep both historical IDs, and keep reporting it missing.
            (row["projectId"]?.toString()?:"null")+":"+
                if(uri.startsWith(SyncBlobs.PREFIX))"sha:"+uri.removePrefix(SyncBlobs.PREFIX).substringBefore('.')else "url:"+uri
        }
        for(group in copies.values)for(duplicate in group.sortedBy {it.key}.drop(1)) {
            val row=localized.getValue(duplicate.key)
            localized[duplicate.key]=JsonObject(row+mapOf("libraryDeleted" to JsonPrimitive(true)))
            deduplicated++
        }
        val next=buildJsonObject {
            put("schemaVersion",1)
            for(kind in collections)put(kind,JsonArray(localized.filterKeys {it.startsWith(kind+":")}.values.toList()))
        }
        val decoded=AssetIndex.JSON.decodeFromJsonElement<IndexData>(next)
        if(decoded.copy(projectSessions=expected.projectSessions)!=expected) {
            SyncJournal.atomic(File(directory,"index-before-apply.json"),encoded.toString())
            index.applySyncedSnapshot(expected,decoded)
        }
        applyExtras(localized.filterKeys {it.substringBefore(':') !in collections})
        journal.acknowledge(result.rows)
        val conflicts=JsonArray(result.conflicts.map {conflict->buildJsonObject {
            put("key",conflict.key);put("versions",JsonArray(conflict.versions.map {version->buildJsonObject {put("id",version.id);put("device",version.device);put("at",version.at);put("value",version.value?:JsonNull)}}))
        }})
        SyncJournal.atomic(File(directory,"conflicts.json"),conflicts.toString())
        return buildJsonObject {put("state",if(conflicts.isNotEmpty())"conflicts" else if(missing.isNotEmpty())"partial"else "synced");put("uploaded",result.uploaded);put("downloaded",result.downloaded);put("missingMedia",missing.size);put("deduplicated",deduplicated);put("conflicts",conflicts);put("completedAt",System.currentTimeMillis())}
    }
    fun resolve(key:String,revision:String)=journal.resolve(key,revision)
}
