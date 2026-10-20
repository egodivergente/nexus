package com.vidadomu.nexus.agent.core.sync

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.*

/** Conversations are imported as inert history. Plans and credentials cannot execute on import. */
class SyncDocuments(private val directory:File) {
    private val cache=File(directory,"documents.json")
    private fun cached():Map<String,JsonObject> = if(cache.exists())Json.parseToJsonElement(cache.readText()).jsonObject.mapValues {it.value.jsonObject}else emptyMap()
    fun capture(chats:List<JsonObject>,montages:Map<String,JsonObject>):Map<String,JsonObject> {
        val rows=cached().toMutableMap()
        for(chat in chats) {
            val items=chat["items"]?.jsonArray.orEmpty().mapNotNull {entry->
                val item=entry.jsonObject
                val role=(item["role"]?:item["type"]?:item["kind"])?.jsonPrimitive?.content?.substringAfterLast('.')?.lowercase()
                val text=item["text"]?.jsonPrimitive?.contentOrNull
                if(role !in setOf("user","assistant","notice") || text.isNullOrBlank())null
                else buildJsonObject {put("role",if(role=="notice")"assistant"else role!!);put("text",text)}
            }
            if(items.isEmpty())continue
            val content=buildJsonObject {put("items",JsonArray(items))}
            val id=MessageDigest.getInstance("SHA-256").digest(content.toString().toByteArray()).joinToString(""){"%02x".format(it)}
            rows["conversations:$id"]=JsonObject(content+mapOf("id" to JsonPrimitive(id)))
        }
        for((id,document) in montages) {
            require(id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
            rows["montages:$id"]=buildJsonObject {put("id",id);put("projectId",id);put("document",document)}
        }
        return rows
    }
    fun apply(rows:Map<String,JsonObject>,archive:(String,JsonObject)->Unit) {
        for((key,row) in rows) {
            require(key.substringBefore(':') in setOf("conversations","montages"))
            val id=key.substringAfter(':');require(id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
            if(key.startsWith("conversations:"))archive("sync_$id",row)
        }
        SyncJournal.atomic(cache,JsonObject(rows).toString())
    }
}
