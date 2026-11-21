package com.vidadomu.nexus.assistant

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vidadomu.nexus.MainActivity
import com.vidadomu.nexus.R
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Aviso de turno terminado mientras Nexus no está delante, y la única petición del permiso de notificaciones.
 * Los encargos de imagen y vídeo ya avisan solos desde GenerationPollingWorker: esto no los duplica.
 */
internal object TurnNotifications {
    private const val CHANNEL_ID = "nexus_assistant_results"
    private const val PREFS = "nexus_notifications"
    private const val KEY_ASKED = "permission_asked"
    private const val EXTRA_CONVERSATION = "extra_open_conversation"
    private const val EXTRA_PROJECT = "extra_open_conversation_project"

    // Contador y no booleano: al recrear la Activity, la nueva puede empezar antes de que la vieja pare.
    private val started = AtomicInteger(0)
    val inForeground: Boolean get() = started.get() > 0
    fun activityStarted() { started.incrementAndGet() }
    fun activityStopped() { started.updateAndGet { (it - 1).coerceAtLeast(0) } }

    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** La Activity escucha aquí y lanza el diálogo del sistema; ver [shouldAsk]. */
    val permissionRequests: SharedFlow<Unit> = requests.asSharedFlow()

    /** Al primer turno o primera generación, no al arrancar: es cuando el aviso tiene sentido. */
    fun askPermissionOnce(context: Context) { if (shouldAsk(context)) requests.tryEmit(Unit) }

    fun shouldAsk(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    /** Se marca antes de mostrar el diálogo: si el usuario lo deniega o lo cierra, no se vuelve a pedir. */
    fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }

    /** Un aviso por conversación (el del turno siguiente sustituye al anterior); nada si Nexus está delante o sin permiso. */
    fun turnEnded(context: Context, conversationId: String, projectId: String?, title: String, failed: Boolean) {
        if (inForeground) return
        val app = context.applicationContext
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Respuestas del asistente", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Avisa cuando el asistente termina un turno con Nexus en segundo plano." })
        val open = PendingIntent.getActivity(app, 0, openIntent(app, conversationId, projectId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (failed) "El turno falló" else "Respuesta lista")
            .setContentText(title)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        // El permiso puede retirarse entre la comprobación y el aviso: sin él no se avisa, y la app sigue igual.
        runCatching { manager.notify(tag(conversationId), 1, notification) }
    }

    private fun tag(conversationId: String) = "turn:$conversationId"

    private fun openIntent(context: Context, conversationId: String, projectId: String?) = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        // Los PendingIntent se distinguen por los datos, no por los extras: uno por conversación.
        data = Uri.Builder().scheme("nexus").authority("conversation").appendPath(conversationId).build()
        putExtra(EXTRA_CONVERSATION, conversationId)
        projectId?.let { putExtra(EXTRA_PROJECT, it) }
    }

    /** La conversación (y su proyecto) que pide abrir un aviso; se consume una vez. */
    fun takeConversation(intent: Intent): Pair<String, String?>? {
        val id = intent.getStringExtra(EXTRA_CONVERSATION)?.takeIf { it.isNotBlank() } ?: return null
        val project = intent.getStringExtra(EXTRA_PROJECT)
        intent.removeExtra(EXTRA_CONVERSATION); intent.removeExtra(EXTRA_PROJECT)
        return id to project
    }
}
