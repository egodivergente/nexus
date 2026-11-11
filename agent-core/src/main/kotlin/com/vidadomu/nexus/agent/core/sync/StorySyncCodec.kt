package com.vidadomu.nexus.agent.core.sync

import com.vidadomu.nexus.agent.core.assets.*
import java.security.MessageDigest
import kotlinx.serialization.json.*

data class StorySyncRestore(val snapshot:IndexData,val pendingKeys:Set<String>)

/** A transport projection of the existing index, never another editable screenplay or an execution grant. */
object StorySyncCodec {
    const val PROTOCOL=1
    private val json=Json {encodeDefaults=true;ignoreUnknownKeys=true}
    private val extended=mapOf(
        "projects" to setOf("treatment","format","targetDurationSec","revision"),
        "scenes" to setOf("order","group","description","locationIds","timeOfDay","light","revision","contentRevision"),
        "shots" to setOf("order","title","intent","characterIds","framing","camera","performance","dialogue","sound","durationSec","narrativeState","productionState","variantOf","revision","contentRevision"),
        "characters" to setOf("variants","notes","revision"),
        "generations" to setOf("storyContext"),
    )
    private val lists=setOf("blocks","links","elements","preferences","proposals","tasks","receipts","history")
    private fun hash(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun canonical(value:JsonElement):String=when(value) {
        is JsonObject -> value.toSortedMap().entries.joinToString(prefix="{",postfix="}"){JsonPrimitive(it.key).toString()+":"+canonical(it.value)}
        is JsonArray -> value.joinToString(prefix="[",postfix="]"){canonical(it)}
        else -> value.toString()
    }
    private fun JsonObject.str(key:String)=(get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.number(key:String)=(get(key) as? JsonPrimitive)?.longOrNull?:0L
    private fun common(kind:String,row:JsonObject)=JsonObject(row.filterKeys {it !in extended.getValue(kind)})
    fun legacyRow(kind:String,row:JsonObject)=JsonObject(row.filterKeys {it !in extended[kind].orEmpty()})
    private fun meaningful(kind:String,row:JsonObject):JsonObject=JsonObject(row.filterKeys {it in when(kind) {
        "shots" -> setOf("description","sceneId")
        "characters" -> setOf("description","referenceAssetIds")
        "projects" -> setOf("memory")
        else -> emptySet()
    }})
    private fun task(row:JsonObject):JsonObject = if(row.str("status")=="running" && row.str("providerJobId")==null) JsonObject(row+mapOf(
        "status" to JsonPrimitive("pending_resume"),"executor" to JsonNull,
        "detail" to JsonPrimitive("Trabajo iniciado en otro dispositivo; pendiente de reanudación explícita. No se ha reenviado ninguna generación."),
    )) else row

    fun capture(index:IndexData):Map<String,JsonObject> {
        val encoded=json.encodeToJsonElement(IndexData.serializer(),index).jsonObject
        val rows=linkedMapOf<String,JsonObject>()
        fun add(kind:String,id:String,project:String,payload:JsonObject,commonHash:String?=null) {
            rows["story:$kind:$id"]=buildJsonObject {put("format",PROTOCOL);put("id",id);put("projectId",project);put("payload",payload);commonHash?.let {put("commonHash",it)}}
        }
        for((kind,fields) in extended)for(value in encoded[kind]?.jsonArray.orEmpty()) {
            val row=value.jsonObject;val id=row.str("id")?:continue
            val project=if(kind=="projects")id else row.str("projectId")?:continue
            if(kind=="generations" && row["storyContext"] in setOf(null,JsonNull))continue
            add(kind,id,project,JsonObject(row.filterKeys {it in fields}),hash(canonical(common(kind,row))))
        }
        val story=encoded.getValue("story").jsonObject
        for(kind in lists)for(value in story[kind]?.jsonArray.orEmpty()) {
            var row=value.jsonObject;val project=row.str("projectId")?:continue
            val id=if(kind=="receipts")hash(project+":"+row.str("operationId"))else row.str("id")?:continue
            if(kind=="tasks")row=task(row)
            add(kind,id,project,row)
        }
        for(project in index.story.legacyAdoptedProjectIds)add("adopted",project,project,buildJsonObject {put("projectId",project)})
        return rows
    }

    /** Current context remains untouched when referenced rows have not arrived yet. */
    fun restore(base:IndexData,expected:IndexData,rows:Map<String,JsonObject>,knownKeys:Set<String>):StorySyncRestore {
        val encoded=json.encodeToJsonElement(IndexData.serializer(),base).jsonObject.toMutableMap()
        val old=json.encodeToJsonElement(IndexData.serializer(),expected).jsonObject
        val pending=linkedSetOf<String>()
        val valid=rows.filter { (key,value) ->
            val pieces=key.split(':',limit=3)
            val good=pieces.size==3 && pieces[0]=="story" && (pieces[1] in extended || pieces[1] in lists || pieces[1]=="adopted") &&
                value.number("format")==PROTOCOL.toLong() && value.str("id")==pieces[2] && value.str("projectId")!=null && value["payload"] is JsonObject
            if(!good)pending+=key
            good
        }
        for((kind,fields) in extended) {
            val originals=old[kind]?.jsonArray.orEmpty().associateBy {it.jsonObject.str("id")}
            encoded[kind]=JsonArray(encoded[kind]?.jsonArray.orEmpty().map { raw ->
                val row=raw.jsonObject;val id=row.str("id")?:return@map row
                val extension=valid["story:$kind:$id"]?:return@map row
                if(extension.str("projectId")!=(if(kind=="projects")id else row.str("projectId"))) {pending+="story:$kind:$id";return@map row}
                val payload=extension.getValue("payload").jsonObject
                if(payload.keys.any {it !in fields}) {pending+="story:$kind:$id";return@map row}
                var combined=JsonObject(row+payload)
                val before=originals[id]?.jsonObject
                val compatible=extension.str("commonHash")==hash(canonical(common(kind,row)))
                if(kind!="generations" && before!=null && !compatible) {
                    val changed=common(kind,row)!=common(kind,before)
                    val revision=maxOf(before.number("revision"),combined.number("revision")) + if(changed)1 else 0
                    combined=JsonObject(combined+mapOf("revision" to JsonPrimitive(revision)))
                    if("contentRevision" in fields) {
                        val semantic=meaningful(kind,row)!=meaningful(kind,before)
                        combined=JsonObject(combined+mapOf("contentRevision" to JsonPrimitive(maxOf(before.number("contentRevision"),combined.number("contentRevision"))+if(semantic)1 else 0)))
                    }
                }
                combined
            })
            val identities=encoded[kind]?.jsonArray.orEmpty().mapNotNull {it.jsonObject.str("id")}.toSet()
            valid.keys.filter {it.startsWith("story:$kind:") && it.substringAfterLast(':') !in identities}.forEach {pending+=it}
        }
        val localStory=old.getValue("story").jsonObject
        val resultStory=localStory.toMutableMap()
        for(kind in lists) {
            val local=localStory[kind]?.jsonArray.orEmpty().associateBy { value -> if(kind=="receipts")hash(value.jsonObject.str("projectId")+":"+value.jsonObject.str("operationId"))else value.jsonObject.str("id") }.toMutableMap()
            for(key in knownKeys.filter {it.startsWith("story:$kind:")}) if(key !in valid)local.remove(key.substringAfterLast(':'))
            for((key,wrapper) in valid.filterKeys {it.startsWith("story:$kind:")}) {
                val id=key.substringAfterLast(':');val payload=wrapper.getValue("payload").jsonObject
                if(payload.str("projectId")!=wrapper.str("projectId") || (kind!="receipts" && payload.str("id")!=id) ||
                    kind=="receipts" && hash(payload.str("projectId")+":"+payload.str("operationId"))!=id) {pending+=key;continue}
                val existing=local[id]?.jsonObject
                local[id]=if(kind=="tasks" && existing?.str("status")=="running" && task(existing)==payload)existing else if(kind=="tasks")task(payload)else payload
            }
            resultStory[kind]=JsonArray(local.values.toList())
        }
        val adopted=expected.story.legacyAdoptedProjectIds.toMutableSet()
        for(key in knownKeys.filter {it.startsWith("story:adopted:")})if(key !in valid)adopted.remove(key.substringAfterLast(':'))
        valid.filterKeys {it.startsWith("story:adopted:")}.values.mapNotNullTo(adopted){it.str("projectId")}
        resultStory["legacyAdoptedProjectIds"]=JsonArray(adopted.map(::JsonPrimitive))
        // Execution policy is device-local: importing a screenplay cannot activate a worker or an agent.
        resultStory["autonomy"]=localStory.getValue("autonomy")
        encoded["story"]=JsonObject(resultStory)
        val candidate=runCatching {json.decodeFromJsonElement<IndexData>(JsonObject(encoded))}.getOrElse {
            return StorySyncRestore(expected,pending+valid.keys)
        }
        pending+=invalidReferences(candidate)-invalidReferences(expected)
        return StorySyncRestore(if(pending.isEmpty())candidate else expected,pending)
    }

    /** IDs remain stable even if two records point to the same verified bytes. */
    fun referencedAssetIds(rows:Map<String,JsonObject>):Set<String> = buildSet {
        for((key,wrapper) in rows) {
            val value=(wrapper["payload"] as? JsonObject)?:continue
            if(key.startsWith("story:links:"))value.str("assetId")?.let(::add)
            if(key.startsWith("story:generations:"))(value["storyContext"] as? JsonObject)?.get("referenceAssetIds")?.jsonArray.orEmpty().forEach {add(it.jsonPrimitive.content)}
        }
    }
    private fun invalidReferences(d:IndexData):Set<String> = buildSet {
        val projects=d.projects.map {it.id}.toSet();val scenes=d.scenes.associateBy {it.id};val shots=d.shots.associateBy {it.id}
        val characters=d.characters.associateBy {it.id};val blocks=d.story.blocks.associateBy {it.id};val elements=d.story.elements.associateBy {it.id};val assets=d.assets.map {it.id}.toSet()
        for(scene in d.scenes)if(scene.projectId !in projects || scene.locationIds.any {elements[it]?.projectId!=scene.projectId})add("story:scenes:${scene.id}")
        for(shot in d.shots)if(shot.projectId !in projects || shot.sceneId?.let {scenes[it]?.projectId!=shot.projectId}==true || shot.characterIds.any {characters[it]?.projectId!=shot.projectId})add("story:shots:${shot.id}")
        for(character in d.characters)if(character.referenceAssetIds.any {it !in assets})add("story:characters:${character.id}")
        for(block in d.story.blocks)if(scenes[block.sceneId]?.projectId!=block.projectId || block.shotId?.let {shots[it]?.projectId!=block.projectId || shots[it]?.sceneId!=block.sceneId}==true)add("story:blocks:${block.id}")
        for(link in d.story.links) {
            val valid=link.projectId in projects && link.assetId in assets && (link.characterId==null || characters[link.characterId]?.projectId==link.projectId) && when(link.scope) {
                "project" -> link.targetId==link.projectId;"scene" -> scenes[link.targetId]?.projectId==link.projectId;"shot" -> shots[link.targetId]?.projectId==link.projectId
                "block" -> blocks[link.targetId]?.projectId==link.projectId;"character" -> characters[link.targetId]?.projectId==link.projectId;"element" -> elements[link.targetId]?.projectId==link.projectId;else -> false
            }
            if(!valid)add("story:links:${link.id}")
        }
        // An element's old scene can be intentionally deleted while its costume/location notes remain recoverable.
        for(row in d.story.elements)if(row.projectId !in projects)add("story:elements:${row.id}")
        for(row in d.story.preferences)if(row.projectId !in projects)add("story:preferences:${row.id}")
    }
}
