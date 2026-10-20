package com.vidadomu.nexus.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.Identity
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.assistant.AssistantRuntime
import kotlinx.serialization.json.*
import kotlinx.coroutines.launch

@Composable fun DriveSyncSettings() {
    val context=LocalContext.current
    val manager=remember {NexusDriveSync.get(context)}
    val status by manager.status.collectAsState()
    var revision by remember {mutableIntStateOf(0)}
    var message by remember {mutableStateOf<String?>(null)}
    var connecting by remember {mutableStateOf(false)}
    val client=remember {Identity.getAuthorizationClient(context)}
    val scope=rememberCoroutineScope()
    fun complete(result:com.google.android.gms.auth.api.identity.AuthorizationResult) {
        scope.launch {
            runCatching {manager.authorized(result)}.onFailure {message="No se pudo completar la conexión de Google."}
            connecting=false;revision++
        }
    }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {result->
        val intent=result.data
        if(intent!=null)runCatching {client.getAuthorizationResultFromIntent(intent)}.onSuccess(::complete).onFailure {connecting=false;message="Conexión de Google cancelada o no disponible."}
        else connecting=false
    }
    val projects by AssistantRuntime.get(context.applicationContext as NexusApplication).assets.changes.collectAsState()
    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Sincronización",style=MaterialTheme.typography.titleMedium)
        val state=status["state"]?.jsonPrimitive?.content
        Text(when(state){"syncing"->"Sincronizando…";"synced"->"Al día";"partial"->"Faltan originales por recuperar";"conflicts"->"Versiones para revisar";"authorization_required"->"Vuelve a conectar Google";"retry"->"Pendiente de reintento";else->"Comparte tus proyectos con tus otros dispositivos"})
        message?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        val connected=remember(revision){manager.prefs.getString("account",null)!=null}
        if(!connected || state=="authorization_required")Button(enabled=!connecting,onClick={
            connecting=true;message=null
            client.authorize(manager.request()).addOnSuccessListener {result->
                if(result.hasResolution())result.pendingIntent?.let {launcher.launch(IntentSenderRequest.Builder(it).build())}
                else complete(result)
            }.addOnFailureListener {connecting=false;message="Google no pudo autorizar Nexus. Comprueba que su cliente Android está registrado."}
        }){Text(if(connecting)"Conectando…"else "Conectar Google Drive")}
        else {
            Text(manager.prefs.getString("account","")?:"",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={manager.enqueue(manual=true)}){Text("Sincronizar ahora")}
                TextButton(onClick={manager.disconnect();revision++}){Text("Desconectar")}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Sincronización automática");Switch(checked=remember(revision){manager.prefs.getBoolean("enabled",false)},onCheckedChange={manager.setEnabled(it);revision++})}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Solo Wi-Fi");Switch(checked=remember(revision){manager.prefs.getBoolean("wifi",true)},onCheckedChange={manager.setWifi(it);revision++})}
            Text("Proyectos disponibles sin conexión",style=MaterialTheme.typography.labelLarge)
            Text("El proyecto activo también se descarga al sincronizar.",style=MaterialTheme.typography.bodySmall)
            for(project in projects.projects){
                val selected=remember(revision,project.id){project.id in manager.prefs.getStringSet("projects",emptySet())!!}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(project.name,Modifier.weight(1f));Checkbox(checked=selected,onCheckedChange={manager.selectProject(project.id,it);revision++})}
            }
        }
        val missing=status["missingMedia"]?.jsonPrimitive?.intOrNull?:0
        if(missing>0)Text("$missing archivos necesitan recuperar el original.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        for(entry in status["conflicts"]?.jsonArray.orEmpty()) {
            val conflict=entry.jsonObject
            Text("Versiones conservadas · "+conflict["key"]!!.jsonPrimitive.content,style=MaterialTheme.typography.labelLarge)
            for(element in conflict["versions"]!!.jsonArray){
                val version=element.jsonObject;val value=version["value"]?.takeIf {it!=JsonNull}?.jsonObject
                TextButton(onClick={manager.resolve(conflict["key"]!!.jsonPrimitive.content,version["id"]!!.jsonPrimitive.content)}){Text((value?.get("name")?.jsonPrimitive?.content?:value?.get("displayName")?.jsonPrimitive?.content?:if(value==null)"Confirmar retirada"else "Conservar versión")+" · "+(version["device"]?.jsonPrimitive?.content?:"Dispositivo"))}
            }
        }
    }
}
