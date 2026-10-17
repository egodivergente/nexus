package com.vidadomu.nexus.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.ui.components.StudioTextField
import com.vidadomu.nexus.ui.components.StudioCredentialField
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@Composable
internal fun AgentInternetSettings() {
    val app=LocalContext.current.applicationContext as NexusApplication
    val internet=remember {AgentInternet(app)}
    val scope=rememberCoroutineScope()
    var expanded by remember {mutableStateOf(false)}
    var servers by remember {mutableStateOf(internet.servers())}
    var id by remember {mutableStateOf("")}
    var endpoint by remember {mutableStateOf("")}
    var token by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    FoldHeader("Internet y herramientas MCP","${servers.size} servidor(es)",expanded) {expanded=!expanded}
    if(expanded) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Búsqueda, lectura web y herramientas MCP de consulta disponibles desde el chat. Puedes pedir al agente que conecte un servidor MCP o añadirlo aquí.",style=MaterialTheme.typography.bodySmall)
        servers.forEach {(name,url) -> Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {Text(name);Text(url,style=MaterialTheme.typography.bodySmall)}
            TextButton(enabled=!busy,onClick={scope.launch {
                internet.run("disconnect_mcp_server",buildJsonObject {put("server_id",name)})
                servers=internet.servers()
            }}) {Text("Quitar")}
        }}
        StudioTextField(id,{id=it},label={Text("Nombre del servidor")},singleLine=true,modifier=Modifier.fillMaxWidth())
        StudioTextField(endpoint,{endpoint=it},label={Text("URL MCP")},singleLine=true,modifier=Modifier.fillMaxWidth())
        StudioCredentialField(token,{token=it},label={Text("Token Bearer (si lo requiere)")})
        FilledTonalButton(enabled=!busy && id.isNotBlank() && endpoint.isNotBlank(),onClick={
            busy=true;message=null
            scope.launch {
                runCatching {
                    require(id.matches(Regex("[a-zA-Z0-9_-]{1,60}"))) {"Usa letras, números, guion o guion bajo para el nombre."}
                    if(token.isNotBlank()) app.secureStorage.saveProviderApiKey(AgentInternet.credentialKey(endpoint.trim()),token.trim())
                    internet.run("connect_mcp_server",buildJsonObject {put("server_id",id);put("url",endpoint.trim())})
                }.onSuccess {servers=internet.servers();message="Conectado. Sus herramientas de consulta están disponibles para el agente.";token=""}
                    .onFailure {message=it.message ?: "No se pudo conectar."}
                busy=false
            }
        }) {Text(if(busy) "Conectando…" else "Conectar herramientas")}
        message?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
    }
}
