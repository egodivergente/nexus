package com.vidadomu.nexus.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vidadomu.nexus.MainActivity
import com.vidadomu.nexus.R
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*

/** Executes user-requested turns independently of the Activity. Never replays interrupted requests. */
class AssistantTurnService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val active = mutableMapOf<String, Turn>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var renewal: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            interrupt(CancellationException("Cancelado por el usuario."))
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra(EXTRA_TURN)
        val turn = id?.let { pending.remove(it) }
        if (id == null || turn == null) {
            if (active.isEmpty()) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        active[id] = turn
        try {
            createChannel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else startForeground(NOTIFICATION_ID, buildNotification())
            keepAwake()
        } catch (error: Exception) {
            active.remove(id)
            turn.fail(IllegalStateException("No se pudo mantener el agente en segundo plano (${error.javaClass.simpleName}).", error))
            if (active.isEmpty()) finishService(startId)
            return START_NOT_STICKY
        }
        turn.job = scope.launch(start = CoroutineStart.LAZY) {
            try { turn.execute() }
            catch (error: CancellationException) { turn.fail(error); throw error }
            catch (error: Exception) { turn.fail(error) }
            finally {
                active.remove(id)
                if (active.isEmpty()) finishService(startId)
            }
        }
        turn.job!!.start()
        return START_NOT_STICKY
    }

    private fun keepAwake() {
        if (wakeLock == null) wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG).apply { setReferenceCounted(false) }
        wakeLock!!.acquire(WAKE_LEASE_MS)
        if (renewal == null) renewal = scope.launch {
            while (isActive) {
                delay(WAKE_LEASE_MS / 2)
                if (active.isNotEmpty()) wakeLock?.acquire(WAKE_LEASE_MS)
            }
        }
    }

    private fun releaseAwake() {
        renewal?.cancel(); renewal = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun finishService(startId: Int? = null) {
        releaseAwake()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (startId == null) stopSelf() else stopSelfResult(startId)
    }

    private fun interrupt(error: Exception) {
        active.values.toList().forEach { it.fail(error); it.job?.cancel() }
        active.clear()
        finishService()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        interrupt(IllegalStateException("Android ha agotado el tiempo de segundo plano del agente. El turno se ha detenido sin reenviarlo."))
    }

    override fun onDestroy() {
        active.values.toList().forEach {
            it.fail(IllegalStateException("Android ha detenido el servicio del agente. La petición no se ha reenviado."))
        }
        active.clear()
        scope.cancel()
        releaseAwake()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Asistente en segundo plano", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Indica cuándo Nexus está atendiendo una petición del asistente." },
        )
    }

    private fun buildNotification(): Notification {
        val cancel = PendingIntent.getService(this, 1,
            Intent(this, AssistantTurnService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Nexus · Asistente")
            .setContentText("El agente sigue trabajando. Toca para volver a la conversación.")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(0, "Parar", cancel)
            .build()
    }

    private class Turn(val execute: suspend () -> Unit, val fail: (Throwable) -> Unit) {
        @Volatile var job: Job? = null
    }

    companion object {
        private const val CHANNEL_ID = "nexus_assistant_turn"
        private const val NOTIFICATION_ID = 4107
        private const val EXTRA_TURN = "turn_id"
        private const val ACTION_CANCEL = "com.vidadomu.nexus.assistant.CANCEL"
        internal const val WAKE_TAG = "Nexus:AssistantTurn"
        private const val WAKE_LEASE_MS = 10 * 60 * 1000L
        private val pending = ConcurrentHashMap<String, Turn>()

        /** Await service-owned work; cancellation stops the same request, without a retry. */
        suspend fun <T> runTurn(context: Context, block: suspend () -> T): T {
            val result = CompletableDeferred<T>()
            val id = UUID.randomUUID().toString()
            val turn = Turn(execute = { if (result.isActive) result.complete(block()) },
                fail = { result.completeExceptionally(it) })
            pending[id] = turn
            try {
                ContextCompat.startForegroundService(context.applicationContext,
                    Intent(context, AssistantTurnService::class.java).putExtra(EXTRA_TURN, id))
                return result.await()
            } finally {
                pending.remove(id)
                if (!result.isCompleted) { result.cancel(); turn.job?.cancel() }
            }
        }
    }
}
