package com.vidadomu.nexus.agent

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object AgentDraftStore {
    private val json=Json {ignoreUnknownKeys=false}
    private fun file(context:Context,id:String):File {
        require(Regex("[a-f0-9-]{36}").matches(id)) {"Identificador de borrador inválido."}
        return File(File(context.filesDir,"agent_drafts"),"$id.json")
    }
    @Synchronized fun save(context:Context,draft:AgentDraft) {
        val f=file(context,draft.id);f.parentFile!!.mkdirs()
        check(f.parentFile!!.listFiles().orEmpty().count {it.extension=="json"}<20) {"Hay 20 borradores preparados. Revisa los existentes antes de añadir otro."}
        check(!f.exists()) {"El borrador ya existe."}
        val temporary=File(f.parentFile,f.name+".tmp")
        try {
            temporary.writeText(json.encodeToString(draft),Charsets.UTF_8)
            check(temporary.renameTo(f)) {"No se pudo guardar el borrador."}
        } finally {temporary.delete()}
    }
    @Synchronized fun read(context:Context,id:String):AgentDraft = json.decodeFromString(file(context,id).readText(Charsets.UTF_8))
    @Synchronized fun list(context:Context):List<AgentDraft> = File(context.filesDir,"agent_drafts")
        .listFiles().orEmpty().filter {it.extension=="json"}
        .mapNotNull {runCatching {json.decodeFromString<AgentDraft>(it.readText(Charsets.UTF_8))}.getOrNull()}
        .sortedByDescending {it.createdAt}
    @Synchronized fun discard(context:Context,id:String):Boolean = file(context,id).delete()
}
