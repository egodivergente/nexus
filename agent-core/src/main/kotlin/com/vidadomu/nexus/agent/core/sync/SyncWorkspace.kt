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
    storyRemote:SyncRemote?=null,private val storyEnabled:()->Boolean={true},
) {
    private val blobs=SyncBlobs(File(directory,"blobs"),remote)
    private val journal=SyncJournal(File(directory,"journal.json"),remote,deviceLabel)
    private val storyJournal=storyRemote?.let {SyncJournal(File(directory,"story-journal-v1.json"),it,deviceLabel,setOf("story"))}
    private val collections=listOf("projects","scenes","shots","characters","assets","generations")
    suspend fun cycle(downloadProjects:Set<String>?,extras:Map<String,JsonObject> = emptyMap(),applyExtras:(Map<String,JsonObject>)->Unit = {}):JsonObject {
        val expected=index.snapshot()
        val useStory=storyJournal!=null && storyEnabled()
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
            var row=StorySyncCodec.legacyRow(kind,element.jsonObject)
            if(kind=="assets")row=JsonObject(row+mapOf("uri" to JsonPrimitive(portable(row["uri"]!!.jsonPrimitive.content))))
            if(kind=="generations")row=JsonObject(row+mapOf("referenceUris" to JsonArray(row["referenceUris"]?.jsonArray.orEmpty().map {JsonPrimitive(portable(it.jsonPrimitive.content))})))
            rows[kind+":"+row["id"]!!.jsonPrimitive.content]=row
        }
        rows.putAll(extras.mapValues {(_,value)->SyncBlobs.rewrite(value){uri->if(uri.startsWith("file:")||uri.startsWith("content:")||uri.startsWith("/media/"))portable(uri)else replacements[uri]?:uri}.jsonObject})
        val capturedStory=StorySyncCodec.capture(expected)
        val storyRows=if(!useStory)capturedStory else capturedStory.mapValues {(_,value)->SyncBlobs.rewrite(value){uri->
            if(uri.startsWith("file:")||uri.startsWith("content:")||uri.startsWith("/media/"))portable(uri)else replacements[uri]?:uri
        }.jsonObject}
        val result=journal.exchange(rows)
        val storyResult=if(useStory)storyJournal!!.exchange(storyRows)else null
        val retained=result.rows.toMutableMap()
        val retainedStory=(storyResult?.rows?:storyRows).toMutableMap()
        // The editor keeps its local text while every competing version remains in the journal.
        if(useStory)for(conflict in result.conflicts)rows[conflict.key]?.let {retained[conflict.key]=it}?:retained.remove(conflict.key)
        for(conflict in storyResult?.conflicts.orEmpty())storyRows[conflict.key]?.let {retainedStory[conflict.key]=it}?:retainedStory.remove(conflict.key)
        val downloaded=mutableMapOf<String,String>()
        val selectedStoryAssets=if(downloadProjects==null)mutableSetOf()else StorySyncCodec.referencedAssetIds(
            retainedStory.filterValues {it["projectId"]?.jsonPrimitive?.contentOrNull in downloadProjects}).toMutableSet()
        if(downloadProjects!=null)for((key,row) in retained)if(row["projectId"]?.jsonPrimitive?.contentOrNull in downloadProjects && key.substringBefore(':') in setOf("characters","generations")) {
            selectedStoryAssets+=row["referenceAssetIds"]?.jsonArray.orEmpty().map {it.jsonPrimitive.content}
        }
        fun deviceUri(uri:String,download:Boolean):String {
            if(!uri.startsWith(SyncBlobs.PREFIX))return uri
            downloaded[uri]?.let {return it}
            val file=if(download)blobs.obtain(uri) else blobs.existing(uri)
            return file?.let(localUri)?.also {downloaded[uri]=it}?:uri
        }
        fun localize(key:String,row:JsonObject):JsonObject {
            val project=row["projectId"]?.jsonPrimitive?.contentOrNull
                ?:if(key.startsWith("projects:"))row["id"]?.jsonPrimitive?.contentOrNull else null
            val linked=key.startsWith("assets:") && row["id"]?.jsonPrimitive?.contentOrNull in selectedStoryAssets
            return SyncBlobs.rewrite(row){deviceUri(it,linked || downloadProjects==null || project==null || project in downloadProjects)}.jsonObject
        }
        val localized=retained.mapValues {(key,row)->localize(key,row)}.toMutableMap()
        val localizedStory=retainedStory.mapValues {(key,row)->localize(key,row)}
        // Manual copies can have different asset IDs. Keep their history/references,
        // but show identical bytes only once in each project's library.
        var deduplicated=0
        val protected=expected.story.links.map {it.assetId}.toMutableSet()
        protected+=StorySyncCodec.referencedAssetIds(retainedStory)
        for(conflict in storyResult?.conflicts.orEmpty())for(version in conflict.versions)version.value?.let {protected+=StorySyncCodec.referencedAssetIds(mapOf(conflict.key to it))}
        for(row in localized.filterKeys {it.startsWith("characters:")}.values)protected+=row["referenceAssetIds"]?.jsonArray.orEmpty().map {it.jsonPrimitive.content}
        for(row in localized.filterKeys {it.startsWith("generations:")}.values)protected+=row["outputAssetIds"]?.jsonArray.orEmpty().map {it.jsonPrimitive.content}
        for(row in localized.filterKeys {it.startsWith("assets:")}.values)if(row["role"]?.jsonPrimitive?.content=="FINAL")row["id"]?.jsonPrimitive?.contentOrNull?.let {protected+=it}
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
        for(group in copies.values)for(duplicate in group.sortedBy {it.key}.drop(1).filter {it.value["id"]?.jsonPrimitive?.content !in protected}) {
            val row=localized.getValue(duplicate.key)
            localized[duplicate.key]=JsonObject(row+mapOf("libraryDeleted" to JsonPrimitive(true)))
            deduplicated++
        }
        val next=buildJsonObject {
            put("schemaVersion",1)
            for(kind in collections)put(kind,JsonArray(localized.filterKeys {it.startsWith(kind+":")}.values.toList()))
        }
        val decoded=AssetIndex.JSON.decodeFromJsonElement<IndexData>(next).copy(story=expected.story)
        val restored=StorySyncCodec.restore(decoded,expected,localizedStory,if(useStory)storyJournal!!.knownKeys()else storyRows.keys)
        val pending=restored.pendingKeys
        if(pending.isEmpty() && restored.snapshot.copy(projectSessions=expected.projectSessions)!=expected) {
            SyncJournal.atomic(File(directory,"index-before-apply.json"),encoded.toString())
            index.applySyncedSnapshot(expected,restored.snapshot)
        }
        if(pending.isEmpty()) {
            applyExtras(localized.filterKeys {it.substringBefore(':') !in collections})
            journal.acknowledge(result.rows,if(useStory)result.conflicts.map {it.key}.toSet()else emptySet())
            if(useStory)storyJournal!!.acknowledge(storyResult!!.rows,storyResult.conflicts.map {it.key}.toSet())
        }
        // No acknowledgement on an incomplete graph: next cycle must not upload retained local data
        // as a deletion or implicit resolution of the unapplied remote snapshot.
        val allConflicts=result.conflicts+storyResult?.conflicts.orEmpty()
        val narrativeConflicts=(storyResult?.conflicts?.size?:0)+result.conflicts.count {it.key.substringBefore(':') in setOf("projects","scenes","shots","characters")}
        val conflicts=JsonArray(allConflicts.map {conflict->buildJsonObject {
            put("key",conflict.key);put("versions",JsonArray(conflict.versions.map {version->buildJsonObject {put("id",version.id);put("device",version.device);put("at",version.at);put("value",version.value?:JsonNull)}}))
        }})
        SyncJournal.atomic(File(directory,"conflicts.json"),conflicts.toString())
        return buildJsonObject {
            put("state",if(conflicts.isNotEmpty())"conflicts" else if(missing.isNotEmpty() || pending.isNotEmpty())"partial"else "synced")
            put("uploaded",result.uploaded+(storyResult?.uploaded?:0));put("downloaded",result.downloaded+(storyResult?.downloaded?:0));put("missingMedia",missing.size);put("deduplicated",deduplicated);put("conflicts",conflicts);put("completedAt",System.currentTimeMillis())
            put("narrativeSync",buildJsonObject {
                put("supported",storyJournal!=null);put("enabled",useStory)
                put("state",if(storyJournal==null)"unsupported" else if(!useStory)"disabled" else if(narrativeConflicts>0)"conflicts" else if(pending.isNotEmpty())"partial" else "synced")
                put("conflicts",if(useStory)narrativeConflicts else 0);put("pendingDependencies",pending.size)
                put("uploaded",storyResult?.uploaded?:0);put("downloaded",storyResult?.downloaded?:0)
                if(pending.isNotEmpty())put("reason","Hay elementos relacionados que aún no han llegado. El guion local se conserva y se reintentará sin reenviar generaciones.")
                if(useStory && pending.isEmpty())put("completedAt",System.currentTimeMillis())
            })
        }
    }
    fun resolve(key:String,revision:String) {
        if(key.startsWith("story:")) {
            check(storyEnabled()) {"Activa la sincronización del guion para revisar sus conflictos."}
            (storyJournal?:error("Este dispositivo no admite el canal de guion.")).resolve(key,revision)
        } else journal.resolve(key,revision)
    }
}
