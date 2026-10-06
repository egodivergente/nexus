package com.vidadomu.nexus.agent

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.util.Base64
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.core.model.*
import com.vidadomu.nexus.provider.atlas.NativeModelSchema
import com.vidadomu.nexus.ui.models.*
import com.vidadomu.nexus.ui.create.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID

/** ADB-only debug bridge. No sockets, credentials, file uploads or generation operation. */
class NexusAgentProvider:ContentProvider() {
    override fun onCreate()=true
    override fun call(method:String,arg:String?,extras:Bundle?):Bundle {
        check(BuildConfig.DEBUG && Process.myUid()/100000==0) {"Puente disponible solo en depuración y perfil personal."}
        check(Binder.getCallingUid() in setOf(2000,Process.myUid())) {"El puente requiere ADB autorizado."}
        val response=try {
            require(arg==null || arg.length<=96000) {"Solicitud demasiado grande."}
            val input=arg?.let {Json.parseToJsonElement(String(Base64.decode(it,Base64.URL_SAFE),Charsets.UTF_8)).jsonObject} ?: JsonObject(emptyMap())
            runBlocking(Dispatchers.IO) {withTimeout(12000) {execute(method,input)}}
        } catch(e:Exception) {
            buildJsonObject {put("ok",false);put("error",when(e) {
                is TimeoutCancellationException->"Nexus aún está cargando. Vuelve a consultar el estado."
                is kotlinx.serialization.SerializationException->"La solicitud no tiene un formato válido."
                is IllegalArgumentException->e.message?.take(160) ?: "Solicitud inválida."
                else->"No se pudo completar la operación local (${e.javaClass.simpleName})."
            })}
        }
        return Bundle().apply {putString("nexus_result",Base64.encodeToString(response.toString().toByteArray(Charsets.UTF_8),Base64.NO_WRAP or Base64.URL_SAFE))}
    }
    private suspend fun execute(method:String,input:JsonObject):JsonObject {
        val app=context!!.applicationContext as NexusApplication
        fun text(key:String)=input[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        if(method=="status") return buildJsonObject {
            put("ok",true);put("appVersion",BuildConfig.VERSION_NAME);put("protocol",1)
            put("catalogReady",app.modelCatalogRepository.models.value.isNotEmpty())
            put("paidExecutionEnabled",false);put("profile",0)
        }
        if(method=="discard_draft") return buildJsonObject {put("ok",AgentDraftStore.discard(app,text("draftId")))}
        if(method=="list_drafts") return buildJsonObject {put("ok",true);put("drafts",JsonArray(AgentDraftStore.list(app).map {draft ->
            buildJsonObject {put("draftId",draft.id);put("modelId",draft.modelId);put("providerId",draft.providerId);put("createdAt",draft.createdAt)}
        }))}
        val models=app.modelCatalogRepository.models.first {it.isNotEmpty()}
        if(method=="catalog") {
            val query=text("query").lowercase();val modality=text("modality").uppercase()
            val filtered=models.filter {m->(modality.isBlank() || m.modalities.any {it.name==modality}) &&
                (query.isBlank() || query in (m.displayName+" "+m.id+" "+modelGuide(m).purpose).lowercase())}
            val ordered=filtered.sortedWith(compareByDescending<NexusModel> {modelRelease(it)?.month.orEmpty()}.thenBy {it.displayName}.thenBy {it.id})
            val offset=(input["offset"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0)
            val limit=(input["limit"]?.jsonPrimitive?.intOrNull ?: 20).coerceIn(1,50)
            return buildJsonObject {put("ok",true);put("total",ordered.size);put("offset",offset)
                put("models",JsonArray(ordered.drop(offset).take(limit).map {m->buildJsonObject {
                    put("id",m.id);put("providerId",m.providerId);put("name",m.displayName)
                    put("modalities",JsonArray(m.modalities.map {JsonPrimitive(it.name)}))
                    put("purpose",modelGuide(m).purpose);put("strength",modelGuide(m).strength)
                    modelRelease(m)?.let {put("releaseMonth",it.month);put("releaseSource",it.source)}
                    m.basePriceUsd?.let {put("basePriceUsd",it)};m.priceUnit?.let {put("priceUnit",it)}
                    put("priceUnitVerified",m.priceUnitVerified)
                }}))}
        }
        require(method in setOf("model","prepare_draft")) {"Operación no admitida. El puente no ejecuta generaciones."}
        val model=models.singleOrNull {it.id==text("modelId") && it.providerId==text("providerId")}
            ?: throw IllegalArgumentException("Selecciona un modelo visible del catálogo y su proveedor exacto.")
        val config=if(model.providerId=="atlas_cloud") NativeModelSchema.load(model) else null
        val configured=config?.model ?: model.copy(parameters=FormContract.normalize(model.parameters))
        if(method=="model") return buildJsonObject {put("ok",true);put("model",Json.encodeToJsonElement(NexusModel.serializer(),configured))
            put("unsupportedRequired",JsonArray(config?.unsupportedRequired.orEmpty().map(::JsonPrimitive)))
            put("paidExecutionEnabled",false)}
        require(config?.unsupportedRequired.isNullOrEmpty()) {"El esquema contiene entradas obligatorias aún no compatibles."}
        val params=(input["parameters"] as? JsonObject).orEmpty().mapValues {it.value.jsonPrimitive.content}
        val prompt=text("prompt");AgentDraftContract.validate(configured,prompt,params)
        val values=FormContract.restoredValues(configured,params)
        val draft=AgentDraft(UUID.randomUUID().toString(),model.providerId,model.id,prompt,values)
        AgentDraftStore.save(app,draft)
        val readiness=creationReadiness(CreateUiState(selectedModel=configured,selectedModality=configured.modalities.first(),prompt=prompt,parameters=values,schemaReady=true))
        return buildJsonObject {put("ok",true);put("draftId",draft.id);put("model",model.displayName)
            put("status","prepared_not_submitted");put("readyWithoutMedia",readiness.canSubmit)
            readiness.message?.let {put("missing",it)};put("costStatus","Revisar la estimación en Nexus antes de generar.")
            put("paidExecutionEnabled",false)}
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor?=null
    override fun getType(uri:Uri):String?=null
    override fun insert(uri:Uri,values:ContentValues?):Uri?=null
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
}
