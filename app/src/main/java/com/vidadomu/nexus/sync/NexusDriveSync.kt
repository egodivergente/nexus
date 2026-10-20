package com.vidadomu.nexus.sync

import android.accounts.Account
import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.work.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.vidadomu.nexus.BuildConfig
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.assistant.AssistantRuntime
import com.vidadomu.nexus.agent.core.sync.*
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

class NexusDriveSync private constructor(val app:NexusApplication) {
    val prefs=app.getSharedPreferences("nexus_drive_sync_v1",Context.MODE_PRIVATE)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var watching=false
    val status=MutableStateFlow(readStatus())
    companion object {
        private var instance:NexusDriveSync?=null
        internal val gate=Mutex()
        @Synchronized fun get(context:Context):NexusDriveSync=instance?:NexusDriveSync(context.applicationContext as NexusApplication).also {instance=it}
    }
    fun request():AuthorizationRequest {
        val builder=AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveRemote.SCOPE)))
        prefs.getString("account",null)?.let {builder.setAccount(Account(it,"com.google"))}
        return builder.build()
    }
    @Suppress("DEPRECATION") fun authorized(result:AuthorizationResult) {
        require(!result.hasResolution() && !result.accessToken.isNullOrBlank() && DriveRemote.SCOPE in result.grantedScopes)
        val account=result.toGoogleSignInAccount()?.email?:error("Google no identificó la cuenta autorizada.")
        val prior=prefs.getString("account",null)
        require(prior==null || prior==account) {"Desconecta la cuenta anterior antes de cambiarla."}
        prefs.edit().putString("account",account).putBoolean("enabled",true).apply()
        initialize();enqueue()
    }
    fun initialize() {
        if(BuildConfig.OFFLINE_QA || !prefs.getBoolean("enabled",false))return
        val work=PeriodicWorkRequestBuilder<NexusDriveSyncWorker>(15,TimeUnit.MINUTES).setConstraints(constraints()).build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("nexus-drive-periodic",ExistingPeriodicWorkPolicy.UPDATE,work)
        if(watching)return
        watching=true
        app.registerActivityLifecycleCallbacks(object:Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity:Activity){enqueue()}
            override fun onActivityCreated(activity:Activity,state:Bundle?){}
            override fun onActivityStarted(activity:Activity){}
            override fun onActivityPaused(activity:Activity){}
            override fun onActivityStopped(activity:Activity){}
            override fun onActivitySaveInstanceState(activity:Activity,state:Bundle){}
            override fun onActivityDestroyed(activity:Activity){}
        })
        scope.launch {
            AssistantRuntime.get(app).assets.changes.debounce(4000).collect {prefs.edit().putBoolean("dirty",true).apply();enqueue()}
        }
    }
    private fun constraints()=Constraints.Builder().setRequiredNetworkType(if(prefs.getBoolean("wifi",true))NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresStorageNotLow(true).build()
    fun enqueue(manual:Boolean=false) {
        if(BuildConfig.OFFLINE_QA || prefs.getString("account",null)==null || (!manual && !prefs.getBoolean("enabled",false)))return
        WorkManager.getInstance(app).enqueueUniqueWork("nexus-drive-now",ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NexusDriveSyncWorker>().setInputData(workDataOf("manual" to manual)).setConstraints(constraints()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,10,TimeUnit.SECONDS).build())
    }
    fun disconnect() {
        prefs.edit().putBoolean("enabled",false).remove("account").remove("folder").apply()
        WorkManager.getInstance(app).cancelUniqueWork("nexus-drive-now")
        WorkManager.getInstance(app).cancelUniqueWork("nexus-drive-periodic")
        setStatus(buildJsonObject {put("state","idle")})
    }
    fun setEnabled(enabled:Boolean) {prefs.edit().putBoolean("enabled",enabled).apply();if(enabled){initialize();enqueue()}else {WorkManager.getInstance(app).cancelUniqueWork("nexus-drive-now");WorkManager.getInstance(app).cancelUniqueWork("nexus-drive-periodic")}}
    fun setWifi(value:Boolean) {prefs.edit().putBoolean("wifi",value).apply();if(prefs.getBoolean("enabled",false)){initialize();enqueue()}}
    fun selectProject(id:String,selected:Boolean) {
        val ids=prefs.getStringSet("projects",emptySet())!!.toMutableSet();if(selected)ids+=id else ids-=id
        prefs.edit().putStringSet("projects",ids).apply();enqueue(manual=true)
    }
    fun resolve(key:String,revision:String) {prefs.edit().putString("resolution",buildJsonObject {put("key",key);put("revision",revision)}.toString()).apply();enqueue(manual=true)}
    private fun readStatus():JsonObject=runCatching {Json.parseToJsonElement(File(app.filesDir,"assistant/sync/status.json").readText()).jsonObject}.getOrDefault(buildJsonObject {put("state","idle")})
    fun setStatus(value:JsonObject) {SyncJournal.atomic(File(app.filesDir,"assistant/sync/status.json"),value.toString());status.value=value}
    private fun token():String {
        val result=Tasks.await(Identity.getAuthorizationClient(app).authorize(request()),30,TimeUnit.SECONDS)
        check(!result.hasResolution()) {"authorization_required"}
        return result.accessToken?:error("authorization_required")
    }
    suspend fun synchronize(manual:Boolean=false):Boolean=gate.withLock {
        val connectedAccount=prefs.getString("account",null)
        if(BuildConfig.OFFLINE_QA || connectedAccount==null || (!manual && !prefs.getBoolean("enabled",false)))return@withLock true
        prefs.edit().putBoolean("dirty",false).apply()
        setStatus(buildJsonObject {put("state","syncing")})
        try {
            var cachedToken=token();var tokenAt=System.currentTimeMillis()
            val workContext=currentCoroutineContext()
            val tokens={workContext.ensureActive();check(prefs.getString("account",null)==connectedAccount){"authorization_required"};if(System.currentTimeMillis()-tokenAt>45*60_000){cachedToken=token();tokenAt=System.currentTimeMillis()};cachedToken}
            val folder=prefs.getString("folder",null)?:DriveRemote.findOrCreateRoot(tokens).also {prefs.edit().putString("folder",it).apply()}
            val directory=File(app.filesDir,"assistant/sync/workspaces/$folder")
            val remote=DriveRemote(tokens,folder,sessionDirectory=File(directory,"uploads"))
            val http=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).build()
            val workspace=SyncWorkspace(AssistantRuntime.get(app).assets,directory,remote,open={value->
                val uri=Uri.parse(value)
                val mime=runCatching {app.contentResolver.getType(uri)}.getOrNull()
                val ext=when(mime){"image/png"->"png";"image/jpeg"->"jpg";"image/webp"->"webp";"video/mp4"->"mp4";"audio/mpeg"->"mp3";else->uri.lastPathSegment?.substringAfterLast('.',"")?.lowercase()?.takeIf {it.matches(Regex("[a-z0-9]{1,8}"))}?:"bin"}
                when(uri.scheme){
                    "content","file"->runCatching {app.contentResolver.openInputStream(uri)?.let {SyncInput(ext,it)}}.getOrNull()
                    "https"->{val response=http.newCall(Request.Builder().url(value).build()).execute();if(!response.isSuccessful){response.close();null}else {val body=response.body!!;SyncInput(ext,object:java.io.FilterInputStream(body.byteStream()){override fun close(){super.close();response.close()}})}}
                    else->null
                }
            },localUri={Uri.fromFile(it).toString()},deviceLabel=android.os.Build.MODEL)
            val documents=SyncDocuments(directory)
            val conversationDir=File(app.filesDir,"assistant/conversations")
            val chatFiles=conversationDir.listFiles().orEmpty().filter {it.extension=="json" && !it.name.startsWith("sync_")}+listOf(File(app.filesDir,"assistant/chat-v1.json")).filter {it.exists()}
            val chats=chatFiles.map {file->require(file.length()<=8_000_000);Json.parseToJsonElement(file.readText()).jsonObject}
            prefs.getString("resolution",null)?.let {raw->
                val resolution=Json.parseToJsonElement(raw).jsonObject
                workspace.resolve(resolution["key"]!!.jsonPrimitive.content,resolution["revision"]!!.jsonPrimitive.content)
                prefs.edit().remove("resolution").apply()
            }
            val active=AssistantRuntime.get(app).settings.activeProjectId()
            val selected=prefs.getStringSet("projects",emptySet())!!.toSet()+listOfNotNull(active)
            val result=workspace.cycle(selected,documents.capture(chats,emptyMap())) {rows->
                documents.apply(rows){id,row->
                    val messages=row["items"]!!.jsonArray.mapIndexed {position,element->
                        val item=element.jsonObject;val text=item["text"]!!.jsonPrimitive.content
                        if(item["role"]!!.jsonPrimitive.content=="user")com.vidadomu.nexus.assistant.ChatItem.User(id+"_"+position,text,emptyList())
                        else com.vidadomu.nexus.assistant.ChatItem.Assistant(id+"_"+position,text,emptyList(),"END_TURN")
                    }
                    val chat=com.vidadomu.nexus.assistant.SavedAssistantChat(messages,emptyMap())
                    SyncJournal.atomic(File(conversationDir,"$id.json"),Json.encodeToString(com.vidadomu.nexus.assistant.SavedAssistantChat.serializer(),chat))
                }
            }
            setStatus(result)
            !prefs.getBoolean("dirty",false)
        }catch(e:CancellationException){throw e}
        catch(e:Exception){setStatus(buildJsonObject {put("state",if(e.message=="authorization_required")"authorization_required" else "retry");put("message",if(e.message=="authorization_required")"Vuelve a conectar Google Drive." else "Sincronización pendiente. Tus archivos locales se conservan.")});false}
    }
}

class NexusDriveSyncWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {if(NexusDriveSync.get(applicationContext).synchronize(inputData.getBoolean("manual",false)))Result.success()else Result.retry()}
}
