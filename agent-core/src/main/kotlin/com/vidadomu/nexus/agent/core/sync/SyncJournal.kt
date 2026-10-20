package com.vidadomu.nexus.agent.core.sync

import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Immutable revisions make uploads from disconnected devices commute. */
@Serializable data class SyncRevision(val id:String, val key:String, val parents:List<String>, val value:JsonObject?,val device:String="Dispositivo",val at:Long=0)
@Serializable data class SyncCommit(val format:Int=1,val id:String,val device:String,val revisions:List<SyncRevision>)
@Serializable data class SyncState(
    val format:Int=1,val device:String=UUID.randomUUID().toString(),
    val revisions:Map<String,SyncRevision> = emptyMap(),val received:Set<String> = emptySet(),
    val pending:List<SyncCommit> = emptyList(),val baseline:Map<String,JsonObject> = emptyMap(),
    val baselineHeads:Map<String,List<String>> = emptyMap(),
    val label:String="Dispositivo",
)
data class SyncObject(val id:String,val name:String,val size:Long)
interface SyncRemote {
    fun commits():List<SyncObject>
    fun readCommit(item:SyncObject):ByteArray
    fun putCommit(commit:SyncCommit,bytes:ByteArray)
    fun putBlob(name:String,file:File)
    fun getBlob(name:String,destination:File)
}
data class SyncConflict(val key:String,val versions:List<SyncRevision>)
data class SyncResult(val rows:Map<String,JsonObject>,val conflicts:List<SyncConflict>,val uploaded:Int,val downloaded:Int)

class SyncJournal(private val file:File,private val remote:SyncRemote,deviceLabel:String="Dispositivo") {
    companion object {
        val json=Json { encodeDefaults=true;ignoreUnknownKeys=false }
        val kinds=setOf("projects","scenes","shots","characters","assets","generations","conversations","montages")
        fun validKey(key:String)=key.substringBefore(':') in kinds && key.length in 3..250 && !key.any {it<' '}
        fun atomic(file:File,text:String) {
            file.parentFile?.mkdirs()
            val temporary=File(file.parentFile,file.name+".tmp")
            temporary.outputStream().use {it.write(text.toByteArray());it.fd.sync()}
            java.nio.file.Files.move(temporary.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
    private var state=if(file.exists())json.decodeFromString<SyncState>(file.readText()) else SyncState(label=deviceLabel)
    private fun save(next:SyncState) { atomic(file,json.encodeToString(next));state=next }
    private fun heads(key:String):List<SyncRevision> {
        val rows=state.revisions.values.filter {it.key==key}
        val superseded=rows.flatMap {it.parents}.toSet()
        return rows.filter {it.id !in superseded}.sortedWith(compareBy<SyncRevision> {
            if(it.key.startsWith("assets:") && it.value?.get("uri")?.jsonPrimitive?.contentOrNull?.startsWith(SyncBlobs.PREFIX)==true)1 else 0
        }.thenBy {it.id})
    }
    /** A verified portable copy supersedes a device path, without hiding real byte changes. */
    private fun values(heads:List<SyncRevision>):List<JsonObject?> {
        if(heads.firstOrNull()?.key?.startsWith("generations:")==true)return generationValues(heads)
        if(heads.firstOrNull()?.key?.startsWith("assets:")!=true)return heads.map {it.value}
        val portable=heads.mapNotNull {it.value?.get("uri")?.jsonPrimitive?.contentOrNull?.takeIf {uri->uri.startsWith(SyncBlobs.PREFIX)}}.distinct()
        if(portable.isEmpty() || portable.map {it.removePrefix(SyncBlobs.PREFIX).substringBefore('.')}.distinct().size!=1)return heads.map {it.value}
        val canonical=portable.sorted().last()
        return heads.map {head->head.value?.let {row->if(row["uri"]?.jsonPrimitive?.isString==true)JsonObject(row+mapOf("uri" to JsonPrimitive(canonical)))else row}}
    }
    private fun generationValues(versions:List<SyncRevision>):List<JsonObject?> {
        if(versions.any {it.value==null})return versions.map {it.value}
        var rows=versions.map {it.value!!}
        fun normalize(field:String,canonical:(List<String>)->String?) {
            val arrays=rows.map {it[field] as? JsonArray?:return}
            if(arrays.map {it.size}.distinct().size!=1)return
            val replacements=(0 until arrays.first().size).map {position->
                val strings=arrays.map {it[position].jsonPrimitive.content}
                canonical(strings)
            }
            rows=rows.mapIndexed {index,row->JsonObject(row+mapOf(field to JsonArray(arrays[index].mapIndexed {position,value->replacements[position]?.let(::JsonPrimitive)?:value})))}
        }
        normalize("referenceUris") {uris->
            val portable=uris.filter {it.startsWith(SyncBlobs.PREFIX) && SyncBlobs.NAME.matches(it.removePrefix(SyncBlobs.PREFIX))}
            if(portable.size==uris.size && portable.isNotEmpty() && portable.map {it.removePrefix(SyncBlobs.PREFIX).substringBefore('.')}.distinct().size==1)portable.maxOrNull()else null
        }
        normalize("outputAssetIds") {ids->
            val identities=ids.map {id->
                val originals=heads("assets:$id").mapNotNull {it.value}
                val hashes=originals.mapNotNull {it["uri"]?.jsonPrimitive?.contentOrNull?.takeIf {uri->uri.startsWith(SyncBlobs.PREFIX) && SyncBlobs.NAME.matches(uri.removePrefix(SyncBlobs.PREFIX))}?.removePrefix(SyncBlobs.PREFIX)?.substringBefore('.')}.distinct()
                val projects=originals.map {it["projectId"]?:JsonNull}.distinct()
                if(hashes.size==1 && projects.size==1)projects.single().toString()+":"+hashes.single()else null
            }
            if(identities.all {it!=null} && identities.distinct().size==1)ids.minOrNull()else null
        }
        return rows
    }
    private fun append(revisions:List<SyncRevision>,baseline:Map<String,JsonObject> = state.baseline,baselineHeads:Map<String,List<String>> = state.baselineHeads) {
        if(revisions.isEmpty())return
        val commits=mutableListOf<SyncCommit>();var batch=mutableListOf<SyncRevision>();var bytes=0
        for(revision in revisions) {
            val size=json.encodeToString(revision).toByteArray().size
            require(size<7_000_000) {"Un documento es demasiado grande para sincronizarlo."}
            if(batch.isNotEmpty() && bytes+size>7_000_000){commits+=SyncCommit(id=UUID.randomUUID().toString(),device=state.device,revisions=batch);batch=mutableListOf();bytes=0}
            batch+=revision;bytes+=size
        }
        if(batch.isNotEmpty())commits+=SyncCommit(id=UUID.randomUUID().toString(),device=state.device,revisions=batch)
        save(state.copy(revisions=state.revisions+revisions.associateBy {it.id},pending=state.pending+commits,baseline=baseline,baselineHeads=baselineHeads))
    }
    /** Called before touching local data. An interrupted apply can be retried without re-uploading. */
    @Synchronized fun exchange(local:Map<String,JsonObject>):SyncResult {
        require(local.keys.all(::validKey)) {"Entidad de sincronización no admitida."}
        val bases=state.baselineHeads.toMutableMap()
        val changes=(local.keys+state.baseline.keys).filter {local[it]!=state.baseline[it]}.mapNotNull {key->
            val matching=heads(key).filter {it.value==local[key]}
            if(matching.isNotEmpty()){bases[key]=matching.map {it.id};null}
            else SyncRevision(UUID.randomUUID().toString(),key,bases[key].orEmpty(),local[key],state.label,System.currentTimeMillis()).also {bases[key]=listOf(it.id)}
        }
        if(changes.isEmpty()){if(local!=state.baseline || bases!=state.baselineHeads)save(state.copy(baseline=local,baselineHeads=bases))}
        else append(changes,local,bases)
        var uploaded=0
        for(commit in state.pending.toList()) {
            remote.putCommit(commit,json.encodeToString(commit).toByteArray())
            save(state.copy(pending=state.pending.filterNot {it.id==commit.id},received=state.received+commit.id))
            uploaded++
        }
        var downloaded=0
        for(item in remote.commits()) {
            val id=item.name.removePrefix("commit-").removeSuffix(".json")
            if(id in state.received)continue
            require(item.size in 1..8_388_608) {"Registro remoto demasiado grande."}
            val bytes=remote.readCommit(item)
            require(bytes.size<=8_388_608) {"Registro remoto demasiado grande."}
            val commit=json.decodeFromString<SyncCommit>(bytes.decodeToString())
            require(commit.format==1 && commit.id==id && commit.revisions.size<=20_000) {"Registro remoto incompatible."}
            for(r in commit.revisions) {
                require(validKey(r.key) && runCatching {UUID.fromString(r.id)}.isSuccess && r.id !in r.parents && r.parents.size<=1000)
                val existing=state.revisions[r.id]
                require(existing==null || existing==r) {"Una revisión remota ha cambiado de contenido."}
            }
            save(state.copy(revisions=state.revisions+commit.revisions.associateBy {it.id},received=state.received+id))
            downloaded++
        }
        // Missing parents or cross-entity ancestry must never silently discard a local version.
        for(r in state.revisions.values) for(parent in r.parents) {
            require(state.revisions[parent]?.key==r.key) {"Falta una revisión del proyecto. Reintenta la sincronización."}
        }
        val rows=linkedMapOf<String,JsonObject>();val conflicts=mutableListOf<SyncConflict>()
        for(key in state.revisions.values.map {it.key}.distinct().sorted()) {
            val h=heads(key)
            require(h.isNotEmpty()) {"Historial de sincronización inválido."}
            val normalized=values(h)
            if(h.size>1 && normalized.distinct().size>1)conflicts+=SyncConflict(key,h)
            normalized.last()?.let {rows[key]=it}
        }
        return SyncResult(rows,conflicts,uploaded,downloaded)
    }
    @Synchronized fun acknowledge(rows:Map<String,JsonObject>) {
        val bases=state.revisions.values.map {it.key}.distinct().associateWith {key->
            val h=heads(key);if(values(h).distinct().size>1)listOf(h.last().id)else h.map {it.id}
        }
        save(state.copy(baseline=rows,baselineHeads=bases))
    }
    @Synchronized fun resolve(key:String,revisionId:String) {
        val h=heads(key);val selected=h.firstOrNull {it.id==revisionId}?:error("La versión ya no está disponible.")
        append(listOf(SyncRevision(UUID.randomUUID().toString(),key,h.map {it.id},values(h)[h.indexOf(selected)],state.label,System.currentTimeMillis())))
    }
}
