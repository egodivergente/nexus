package com.vidadomu.nexus.assistant

import android.net.Uri
import android.text.Html
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.agent.core.tools.ToolInputException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit
import java.util.UUID

/** Web reading and extensible remote tools; no always-running service. */
internal class AgentInternet(private val app:NexusApplication) {
    companion object {
        fun credentialKey(endpoint:String):String = "mcp_" + java.security.MessageDigest.getInstance("SHA-256")
            .digest(endpoint.toHttpUrlOrNull().toString().toByteArray()).joinToString("") {"%02x".format(it)}.take(40)
    }
    private val prefs=app.getSharedPreferences("agent_mcp_servers_v1",0)
    private val http=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
        .callTimeout(45,TimeUnit.SECONDS).followRedirects(false).build()
    fun servers():List<Pair<String,String>> = prefs.all.mapNotNull {(key,value)->(value as? String)?.let {key to it}}
    private fun text(input:JsonObject,key:String)=input[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun url(raw:String):okhttp3.HttpUrl = raw.toHttpUrlOrNull()?.takeIf {it.username.isEmpty() && it.password.isEmpty()}
        ?: throw ToolInputException("Indica una URL HTTP o HTTPS válida, sin credenciales incrustadas.")
    private fun requirePublicHost(target:okhttp3.HttpUrl):okhttp3.HttpUrl {
        val host=target.host.lowercase().trim('[',']')
        if(host=="localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw ToolInputException("Por seguridad, read_web_page solo accede a páginas públicas de Internet.")
        }
        val addr=runCatching {java.net.InetAddress.getByName(host)}.getOrNull()
        if(addr!=null && (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress || addr.isAnyLocalAddress || addr.hostAddress?.startsWith("100.64.")==true || addr.hostAddress?.startsWith("169.254.")==true)) {
            throw ToolInputException("Por seguridad, read_web_page no accede a direcciones locales o privadas.")
        }
        return target
    }
    private fun bounded(response:okhttp3.Response):String {
        val body=response.body ?: throw ToolInputException("La respuesta está vacía.")
        val source=body.source();source.request(2_000_001)
        if(source.buffer.size>2_000_000) throw ToolInputException("La página es demasiado grande. Abre una sección concreta.")
        return source.readUtf8()
    }
    private fun plain(html:String)=Html.fromHtml(html.replace(Regex("(?is)<(script|style|nav|footer)[^>]*>.*?</\\1>")," "),Html.FROM_HTML_MODE_LEGACY)
        .toString().replace(Regex("[ \\t]+")," ").replace(Regex("\\n{3,}"),"\n\n").trim()
    suspend fun run(operation:String,input:JsonObject):JsonObject=withContext(Dispatchers.IO) {
        when(operation) {
            "web_search" -> search(text(input,"query"))
            "read_web_page" -> page(text(input,"url"))
            "list_mcp_servers" -> buildJsonObject {
                put("servers",buildJsonArray {
                    servers().forEach { (id,endpoint) ->
                        add(buildJsonObject {put("id",id);put("url",endpoint)})
                    }
                })
            }
            "connect_mcp_server" -> {
                val id=text(input,"server_id")
                if(!id.matches(Regex("[a-zA-Z0-9_-]{1,60}"))) throw ToolInputException("Usa un identificador corto: letras, números, guion o guion bajo.")
                val endpoint=url(text(input,"url")).toString()
                val result=McpSession(id,endpoint).use {it.tools()}
                prefs.edit().putString(id,endpoint).apply()
                buildJsonObject {put("connected",true);put("server_id",id);put("catalog",result)}
            }
            "disconnect_mcp_server" -> {val id=text(input,"server_id");prefs.edit().remove(id).apply();buildJsonObject {put("removed",id)}}
            "list_mcp_tools", "call_mcp_tool" -> {
                val id=text(input,"server_id")
                val endpoint=prefs.getString(id,null) ?: throw ToolInputException("Conecta primero ese servidor MCP.")
                McpSession(id,endpoint).use {session ->
                    if(operation=="list_mcp_tools") session.tools(text(input,"cursor").takeIf {it.isNotBlank()})
                    else {
                        val args=runCatching {Json.parseToJsonElement(text(input,"arguments_json").ifBlank {"{}"}).jsonObject}
                            .getOrElse {throw ToolInputException("arguments_json debe ser un objeto JSON válido.")}
                        session.call(text(input,"tool_name"),args)
                    }
                }
            }
            else -> throw ToolInputException("Operación desconocida.")
        }
    }
    private fun search(query:String):JsonObject {
        if(query.isBlank()) throw ToolInputException("Escribe qué quieres buscar.")
        val endpoint="https://www.bing.com/search?format=rss&q="+Uri.encode(query)
        val xml=http.newCall(Request.Builder().url(endpoint).header("User-Agent","NexusStudio/1.8").build()).execute().use {
            if(!it.isSuccessful) throw ToolInputException("El buscador no respondió (HTTP ${it.code}). Prueba otra consulta o lee una URL directamente.")
            bounded(it)
        }
        val results=Regex("(?s)<item>(.*?)</item>").findAll(xml).take(10).map {item ->
            fun field(tag:String)=Regex("(?s)<$tag>(.*?)</$tag>").find(item.groupValues[1])?.groupValues?.get(1)?.let(::plain).orEmpty()
            buildJsonObject {put("title",field("title"));put("url",field("link"));put("snippet",field("description"))}
        }.toList()
        return buildJsonObject {put("query",query);put("results",JsonArray(results));put("source","Bing RSS");
            if(results.isEmpty()) put("message","El buscador no devolvió resultados legibles. No inventes resultados: prueba otra consulta o una URL.")}
    }
    private fun page(raw:String):JsonObject {
        var current=requirePublicHost(url(raw))
        repeat(6) {
            http.newCall(Request.Builder().url(current).header("User-Agent","Mozilla/5.0 (compatible; NexusStudio/1.8)").build()).execute().use {response ->
                if(response.isRedirect) {current=requirePublicHost(current.resolve(response.header("Location").orEmpty()) ?: throw ToolInputException("Redirección no válida."));return@repeat}
                if(!response.isSuccessful) throw ToolInputException("La página respondió HTTP ${response.code}. Puede requerir acceso o no estar disponible.")
                val type=response.header("Content-Type").orEmpty()
                if(type.isNotBlank() && !type.startsWith("text/") && "json" !in type && "xml" !in type) throw ToolInputException("Este enlace es un archivo. Adjunta PDF o Word al chat para leer documentos.")
                val html=bounded(response)
                val body=plain(html)
                val title=Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.let(::plain).orEmpty()
                val links=Regex("(?is)<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>").findAll(html).mapNotNull {m ->
                    current.resolve(Html.fromHtml(m.groupValues[1],0).toString())?.takeIf {it.scheme in listOf("http","https")}?.let {u ->
                        buildJsonObject {put("url",u.toString());put("text",plain(m.groupValues[2]).take(160))}
                    }
                }.distinctBy {it["url"]}.take(40).toList()
                return buildJsonObject {put("url",current.toString());put("title",title);put("text",body.take(40000));put("truncated",body.length>40000);put("links",JsonArray(links));put("mode","HTML text; no JavaScript or signed-in browser")}
            }
        }
        throw ToolInputException("Demasiadas redirecciones.")
    }
    private inner class McpSession(id:String,private val endpoint:String):java.io.Closeable {
        private var sessionId:String?=null
        private var version="2025-03-26"
        private val token=app.secureStorage.getProviderApiKey(credentialKey(endpoint))
        init {
            val init=rpc("initialize",buildJsonObject {
                put("protocolVersion",version);put("capabilities",buildJsonObject {})
                put("clientInfo",buildJsonObject {put("name","NexusStudio");put("version","1.8.8")})
            })
            version=init["protocolVersion"]?.jsonPrimitive?.contentOrNull ?: version
            if(version !in setOf("2024-11-05","2025-03-26","2025-06-18","2025-11-25")) throw ToolInputException("Versión MCP no compatible: $version")
            rpc("notifications/initialized",buildJsonObject {},true)
        }
        private fun request(method:String,body:String?=null):Request.Builder {
            val request=Request.Builder().url(url(endpoint)).header("Accept","application/json, text/event-stream").header("MCP-Protocol-Version",version)
            sessionId?.let {request.header("Mcp-Session-Id",it)}
            if(token.isNotBlank()) request.header("Authorization","Bearer $token")
            return if(method=="DELETE") request.delete() else request.post(body.orEmpty().toRequestBody("application/json".toMediaType()))
        }
        private fun rpc(method:String,params:JsonObject,notification:Boolean=false):JsonObject {
            val id=UUID.randomUUID().toString()
            val body=buildJsonObject {put("jsonrpc","2.0");if(!notification) put("id",id);put("method",method);put("params",params)}
            http.newCall(request("POST",body.toString()).build()).execute().use {response ->
                if(response.code==401 || response.code==403) throw ToolInputException("Este servidor requiere autenticación. Configura su token en Ajustes del agente → Herramientas MCP.")
                if(!response.isSuccessful) throw ToolInputException("El servidor MCP respondió HTTP ${response.code}. No se ha repetido la operación.")
                response.header("Mcp-Session-Id")?.let {sessionId=it}
                if(notification) return buildJsonObject {}
                fun result(raw:String):JsonObject? {
                    val json=runCatching {Json.parseToJsonElement(raw).jsonObject}.getOrNull() ?: return null
                    if(json["id"]?.jsonPrimitive?.contentOrNull!=id) return null
                    if(json["error"]!=null) throw ToolInputException("El servidor MCP rechazó $method. Revisa su esquema y argumentos.")
                    return json["result"] as? JsonObject ?: buildJsonObject {put("result",json["result"] ?: JsonNull)}
                }
                if(response.header("Content-Type").orEmpty().contains("text/event-stream")) {
                    val source=response.body!!.source();val data=StringBuilder();var total=0
                    while(!source.exhausted()) {
                        val line=source.readUtf8Line() ?: break;total+=line.length
                        if(total>2_000_000) throw ToolInputException("Respuesta MCP demasiado grande.")
                        if(line.isEmpty()) {result(data.toString())?.let {return it};data.clear()}
                        else if(line.startsWith("data:")) {if(data.isNotEmpty()) data.append('\n');data.append(line.substring(5).trimStart())}
                    }
                    result(data.toString())?.let {return it}
                } else result(bounded(response))?.let {return it}
                throw ToolInputException("El servidor MCP no devolvió un resultado para esta llamada.")
            }
        }
        fun tools(cursor:String?=null)=rpc("tools/list",buildJsonObject {cursor?.let {put("cursor",it)}})
        fun call(name:String,args:JsonObject):JsonObject {
            var cursor:String?=null
            var selected:JsonObject?=null
            val cursors=mutableSetOf<String>()
            for(page in 0 until 20) {
                val catalog=tools(cursor)
                selected=(catalog["tools"] as? JsonArray)?.mapNotNull {it as? JsonObject}?.firstOrNull {it["name"]?.jsonPrimitive?.contentOrNull==name}
                if(selected!=null) break
                cursor=catalog["nextCursor"]?.jsonPrimitive?.contentOrNull ?: break
                if(!cursors.add(cursor)) break
            }
            val tool=selected ?: throw ToolInputException("La herramienta no aparece en el catalogo actual del servidor.")
            val readOnly=(tool["annotations"] as? JsonObject)?.get("readOnlyHint")?.jsonPrimitive?.booleanOrNull==true
            if(!readOnly || Regex("generate|create|delete|remove|submit|purchase|pay|execute|send|write|update",RegexOption.IGNORE_CASE).containsMatchIn(name)) {
                throw ToolInputException("Esta conexion MCP admite consultas. Para generar usa las herramientas nativas de NEXUS con el modelo y coste reales.")
            }
            val schema=tool["inputSchema"] as? JsonObject ?: throw ToolInputException("El servidor no publica un esquema para esta herramienta.")
            val required=(schema["required"] as? JsonArray)?.mapNotNull {it.jsonPrimitive.contentOrNull}.orEmpty()
            if(required.any {it !in args}) throw ToolInputException("Faltan argumentos obligatorios. Consulta list_mcp_tools y usa su esquema.")
            val result=rpc("tools/call",buildJsonObject {put("name",name);put("arguments",args)})
            if(result["isError"]?.jsonPrimitive?.booleanOrNull==true) {
                val detail=(result["content"] as? JsonArray)?.mapNotNull {(it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull}?.joinToString(" ")?.take(1500)
                throw ToolInputException("La herramienta MCP fallo: ${detail ?: "sin detalle"}")
            }
            return result
        }
        override fun close() {if(sessionId!=null) runCatching {http.newCall(request("DELETE").build()).execute().close()}}
    }
}
