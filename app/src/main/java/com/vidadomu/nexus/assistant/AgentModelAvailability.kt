package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.llm.SigV4
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

/** Read-only AWS control-plane diagnostic. No inference, subscription or secrets in output. */
internal suspend fun agentModelAvailability(app:NexusApplication,model:String):JsonObject=withContext(Dispatchers.IO) {
    require(model in setOf("anthropic.claude-sonnet-5-5","anthropic.claude-opus-5-5")) {"Modelo no admitido para esta consulta."}
    val store=AssistantRuntime.get(app).settings
    val settings=store.load()
    require(settings.direct && !settings.offline) {"La consulta necesita las credenciales Bedrock configuradas en el teléfono."}
    require(settings.region.matches(Regex("[a-z]{2}(-[a-z]+)+-\\d")))
    val host="bedrock.${settings.region}.amazonaws.com"
    val path="/foundation-model-availability/${SigV4.encode(model)}"
    val headers=SigV4.sign("GET",host,path,"",settings.region,"bedrock",store.awsCredentials(),ZonedDateTime.now(ZoneOffset.UTC))
    val request=Request.Builder().url("https://$host$path").apply {headers.forEach {(k,v)->header(k,v)}}.get().build()
    val client=OkHttpClient.Builder().callTimeout(8,TimeUnit.SECONDS).build()
    client.newCall(request).execute().use {response ->
        val body=runCatching {Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject}.getOrNull()
        buildJsonObject {
            put("modelId",model);put("httpStatus",response.code);put("inferencePerformed",false)
            put("ok",response.isSuccessful)
            if(response.isSuccessful && body!=null) {
                for(key in listOf("authorizationStatus","entitlementAvailability","regionAvailability")) body[key]?.let {put(key,it)}
                body["agreementAvailability"]?.jsonObject?.get("status")?.let {put("agreementStatus",it)}
            } else put("notice",if(response.code==403) "AWS no permite esta consulta con las claves actuales. No confirma ni descarta el permiso de inferencia." else "No se pudo verificar el acceso al modelo. No se ha ejecutado inferencia.")
        }
    }
}
