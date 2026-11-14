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
        Text("Google Drive",style=MaterialTheme.typography.titleMedium)
        val state=status["state"]?.jsonPrimitive?.content
        Text(when(state){"syncing"->"Sincronizando…";"synced"->"Al día";"partial"->"Sincronización incompleta";"conflicts"->"Versiones para revisar";"authorization_required"->"Vuelve a conectar Google";"retry"->"Pendiente de reintento";else->"Comparte tus proyectos con tus otros dispositivos"})
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
            val storyEnabled=remember(revision){manager.prefs.getBoolean("story",false)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text("Guion, storyboard y vínculos de material",Modifier.weight(1f))
                Switch(checked=storyEnabled,onCheckedChange={manager.setStoryEnabled(it);revision++})
            }
            Text("Actívalo después de actualizar tus dispositivos.",style=MaterialTheme.typography.bodySmall)
            val narrative=driveNarrativeStatus(status,storyEnabled)
            Text(narrative.title,style=MaterialTheme.typography.labelLarge,
                color=if(narrative.attention)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            narrative.detail?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
            Text("Proyectos disponibles sin conexión",style=MaterialTheme.typography.labelLarge)
            Text("El proyecto activo también se descarga al sincronizar.",style=MaterialTheme.typography.bodySmall)
            for(project in projects.projects){
                val selected=remember(revision,project.id){project.id in manager.prefs.getStringSet("projects",emptySet())!!}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(project.name,Modifier.weight(1f));Checkbox(checked=selected,onCheckedChange={manager.selectProject(project.id,it);revision++})}
            }
        }
        val projectNames=projects.projects.associate {it.id to it.name}
        val relatedNames=remember(projects) {buildMap<String,String> {
            projects.scenes.forEach {put(it.id,it.title?:it.code)}
            projects.shots.forEach {put(it.id,it.title?:it.code)}
            projects.characters.forEach {put(it.id,it.name)}
            projects.assets.forEach {put(it.id,it.displayName)}
            projects.generations.forEach {put(it.id,it.prompt.take(55))}
            projects.story.elements.forEach {put(it.id,it.name)}
            projects.story.blocks.forEach {put(it.id,it.text.take(55))}
        }}
        val summary=remember(status,projectNames,relatedNames) {DriveConflictPresentation.summarize(status,projectNames,relatedNames=relatedNames)}
        // A malformed pending value still blocks new choices; the manager would overwrite it.
        val pendingKey=remember(status,revision) {
            manager.prefs.getString("resolution",null)?.let {raw->runCatching {Json.parseToJsonElement(raw).jsonObject["key"]?.jsonPrimitive?.contentOrNull}.getOrNull()?:""}
        }
        DriveConflictReview(summary,pendingKey,onResolve={key,version->manager.resolve(key,version);revision++},
            storyEnabled=remember(revision){manager.prefs.getBoolean("story",false)})
    }
}
