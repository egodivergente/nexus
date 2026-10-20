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

/** ADB-only debug bridge. Private recovery pairing, no uploads or generation operation. */
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
        app.ready.await() // arranque en frío: no tocar los lateinit antes de Application.onCreate
        if(method=="agent_model_availability") {
            require(input.keys==setOf("modelId")) {"La consulta solo admite modelId."}
            return com.vidadomu.nexus.assistant.agentModelAvailability(app,input["modelId"]!!.jsonPrimitive.content)
        }
        if(method in setOf("local_whisper_prepare","local_whisper_status")) {
            require(input.isEmpty()) {"Este método no admite rutas ni modelos externos."}
            val store=com.vidadomu.nexus.assistant.WhisperModelStore(app)
            val state=if(method=="local_whisper_prepare" && !store.verifyExisting()) store.enqueue() else store.status()
            return buildJsonObject {put("ok",true);put("whisper",state)}
        }
        fun text(key:String)=input[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        if(method=="device_sync_probe") {
            val id=text("id")
            require(id.matches(Regex("sync_probe_[a-f0-9]{32}")))
            val runtime=com.vidadomu.nexus.assistant.AssistantRuntime.get(app)
            val index=runtime.assets
            val key="nexus_$id"
            when(text("action")) {
                "create" -> {
                    index.mergeFromPeer(com.vidadomu.nexus.agent.core.assets.IndexData(projects=listOf(
                        com.vidadomu.nexus.agent.core.assets.Project(id,"Comprobación de sincronización",description=id,createdAt=System.currentTimeMillis(),
                            memory=com.vidadomu.nexus.agent.core.assets.ProjectMemory(tone=text("marker"))))),null,false)
                    app.secureStorage.saveProviderApiKey(key,text("marker"))
                }
                "remove" -> {
                    val before=index.snapshot()
                    require(before.projects.none {it.id==id && it.description!=id})
                    require(before.assets.none {it.projectId==id})
                    index.mergeFromPeer(before.copy(projects=before.projects.filterNot {it.id==id}),before,true)
                    app.secureStorage.clearProviderApiKey(key)
                }
                "read" -> Unit
                else -> error("Acción de prueba desconocida")
            }
            return buildJsonObject {put("ok",true);put("memoryMatches",index.findProject(id)?.memory?.tone==text("marker"))
                put("credentialMatches",app.secureStorage.getProviderApiKey(key)==text("marker"))}
        }
        if(method=="device_sync_status") {
            val sync=com.vidadomu.nexus.sync.DeviceSync.get(app)
            return buildJsonObject {put("ok",true);put("enabled",sync.options().enabled);put("status",sync.status.value)
                put("conversations",java.io.File(app.filesDir,"assistant/conversations").listFiles().orEmpty().count {it.extension=="json"})}
        }
        if(method=="device_sync_configure") {
            require(input.keys.all {it in setOf("peer","key","enabled","interval","port")})
            val sync=com.vidadomu.nexus.sync.DeviceSync.get(app)
            val o=sync.options().copy(peer=text("peer"),enabled=input["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                intervalSeconds=input["interval"]?.jsonPrimitive?.longOrNull ?: 30,port=input["port"]?.jsonPrimitive?.intOrNull ?: 8795)
            sync.configure(o,text("key").ifBlank {sync.pairingKey()})
            return buildJsonObject {put("ok",true)}
        }
        if(method=="flow_recovery_pair") {
            val token=text("token")
            require(token.matches(Regex("[A-Za-z0-9_-]{43}"))) {"Clave privada no v\u00e1lida."}
            app.secureStorage.saveProviderApiKey(com.vidadomu.nexus.ui.settings.FLOW_RECOVERY_KEY,token)
            return buildJsonObject {put("ok",true)}
        }
        if(method=="status") return buildJsonObject {
            put("ok",true);put("appVersion",BuildConfig.VERSION_NAME);put("protocol",1)
            put("catalogReady",app.modelCatalogRepository.models.value.isNotEmpty())
            put("paidExecutionEnabled",false);put("profile",0)
            put("credentialStorageAvailable",app.secureStorage.credentialStorageAvailable)
            put("flowLinked",app.secureStorage.hasProviderApiKey(com.vidadomu.nexus.ui.settings.FLOW_RECOVERY_KEY))
            put("jobs",app.generationRepository.allJobs.value.size)
            put("historyLoading",app.generationRepository.historyLoading.value)
            put("historyError",app.generationRepository.historyError.value != null)
            val runtime = com.vidadomu.nexus.assistant.AssistantRuntime.get(app)
            put("agentMode",runtime.settings.load().mode.name)
            put("agentModel",runtime.settings.load().modelId)
            put("agentBackend",runtime.settings.load().backend)
            put("skills",runtime.skills.all().size)
            put("skillErrors",runtime.skills.errors.size)
            put("projects",runtime.assets.projects().size)
            put("assets",runtime.assets.snapshot().assets.size)
        }
        if(method=="flow_status") return app.flowBridgeProvider.bridgeStatus().fold(
            onSuccess = { status -> buildJsonObject { put("ok",true);put("connected",status.connected);put("ready",status.generationEnabled);put("promptLimit",status.promptLimit) } },
            onFailure = { buildJsonObject { put("ok",false);put("error",it.message.orEmpty()) } })
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
