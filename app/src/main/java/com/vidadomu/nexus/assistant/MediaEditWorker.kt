package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.core.work.promoteToForeground
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.R
import com.vidadomu.nexus.agent.core.tools.ToolInputException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/** Durable, serial local exports with visible foreground work; no automatic paid retries. */
class MediaEditWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as NexusApplication
        app.ready.await()
        val id=inputData.getString("edit_job_id") ?: return Result.success()
        val store=MediaEditJobs(app)
        val original=runCatching {store.read(id)}.getOrNull() ?: return Result.success()
        if(original.state=="COMPLETED") return Result.success()
        var job=original.copy(state="PROCESSING",error=null,updatedAt=System.currentTimeMillis())
        store.write(job)
        try {
            promoteToForeground(foreground(applicationContext,id,"Nexus está editando un medio")) // Android may refuse the promotion; the edit still runs.
            val outputs=AgentVideoOperations(app).execute(job) {progress ->
                if(progress!=job.progress) {job=job.copy(progress=progress,updatedAt=System.currentTimeMillis());store.write(job)}
            }
            store.write(job.copy(state="COMPLETED",progress=100,outputAssetIds=outputs,updatedAt=System.currentTimeMillis()))
            return Result.success()
        } catch(e:TimeoutCancellationException) {
            store.write(job.copy(state="FAILED",error="La edición agotó su tiempo. Prueba con un intervalo más corto; los originales se conservan.",updatedAt=System.currentTimeMillis()))
            return Result.success() // The job records failure; release the next independent edit.
        } catch(e:CancellationException) {
            store.write(job.copy(state="PAUSED",error="La edición se interrumpió. Los originales se conservan.",updatedAt=System.currentTimeMillis()))
            throw e
        } catch(e:OutOfMemoryError) {
            store.write(job.copy(state="FAILED",error="No hay memoria suficiente. Prueba con un intervalo más corto; los originales se conservan.",updatedAt=System.currentTimeMillis()))
            return Result.success()
        } catch(e:Exception) {
            store.write(job.copy(state="FAILED",error=(e as? ToolInputException)?.message ?: "No se pudo completar la edición. Los originales se conservan.",updatedAt=System.currentTimeMillis()))
            return Result.success()
        }
    }
    companion object {
    internal fun foreground(context:Context,id:String,title:String):ForegroundInfo {
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("nexus_local_edits","Edición de medios",NotificationManager.IMPORTANCE_LOW))
        val notification=NotificationCompat.Builder(context,"nexus_local_edits").setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText("Se conservan los archivos originales.").setOngoing(true).build()
        return if(Build.VERSION.SDK_INT>=29) ForegroundInfo(id.hashCode(),notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(id.hashCode(),notification)
    }
    }
}
