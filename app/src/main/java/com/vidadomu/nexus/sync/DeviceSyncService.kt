package com.vidadomu.nexus.sync

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vidadomu.nexus.MainActivity
import com.vidadomu.nexus.R
import kotlinx.coroutines.*

/** Cross-device conversation continuity, foreground remoteMessaging on Android 14+. */
class DeviceSyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sync = DeviceSync.get(this)
        if (!sync.options().enabled) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("nexus_continuity", "Continuidad entre móviles", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pause = PendingIntent.getService(this, 1, Intent(this, DeviceSyncService::class.java).setAction("pause"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, "nexus_continuity")
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("Nexus · Continuidad")
            .setContentText("Chats, memoria y skills entre tus móviles").setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).addAction(0, "Pausar", pause).build()
        ServiceCompat.startForeground(this, 4110, notification, if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0)
        if (intent?.action == "pause") { sync.configure(sync.options().copy(enabled = false)); return START_NOT_STICKY }
        val previous = job
        sync.close()
        previous?.cancel()
        job = scope.launch {
            previous?.join()
            try { sync.run(this) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { sync.report("No se pudo iniciar la continuidad (${e.javaClass.simpleName}). Revisa el puerto en Ajustes."); stopSelf() }
        }
        return START_STICKY
    }
    override fun onDestroy() { DeviceSync.get(this).close(); scope.cancel(); super.onDestroy() }
    companion object {
        fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, DeviceSyncService::class.java)) }
        fun stop(context: Context) { context.stopService(Intent(context, DeviceSyncService::class.java)) }
        fun resume(context: Context) {
            if (DeviceSync.get(context).options().enabled) runCatching { start(context) }
        }
    }
}

class DeviceSyncBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && DeviceSync.get(context).options().afterBoot) DeviceSyncService.resume(context)
    }
}
