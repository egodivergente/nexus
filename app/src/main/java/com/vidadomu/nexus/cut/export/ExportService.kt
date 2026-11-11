package com.vidadomu.nexus.cut.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.InvalidForegroundServiceTypeException
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.vidadomu.nexus.MainActivity
import com.vidadomu.nexus.cut.engine.CompositionFactory
import com.vidadomu.nexus.cut.media.LutStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.abs

/**
 * Exportación en primer plano: Transformer renderiza a un temporal, se valida (pistas y duración), se mueve a
 * su sitio definitivo y entra en la Biblioteca de Nexus. Nunca queda en la Biblioteca un render a medias;
 * si falla o se cancela, el temporal se borra.
 */
@UnstableApi
class ExportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var transformer: Transformer? = null
    private var progressJob: Job? = null
    private var job: ExportJob? = null
    private var tmp: File? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotified = -1
    /** Set once the job reached Done/Failed/Cancelled, so late callbacks cannot overwrite the result. */
    private var ended = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> cancel()
            else -> start()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        if (job != null) return // already exporting; ExportManager only hands out one job
        createChannel()
        val j = ExportManager.take()
        // startForegroundService() obliges us to go foreground even if there turns out to be no job.
        if (!enterForeground(notification(j?.project?.name ?: "", 0, "Preparando…", ongoing = true))) {
            j?.let { job = it }
            ExportManager.update(ExportState.Failed(j?.project?.id, "Android no ha permitido iniciar la exportación en primer plano. Abre la app y vuelve a intentarlo."))
            stopSelf()
            return
        }
        if (j == null) { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf(); return }
        job = j
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nexus:cut-export").apply { acquire(6 * 60 * 60 * 1000L) }

        scope.launch { prepareAndRender(j) }
    }

    /** File work (old temporaries, free space, LUT files) runs on IO; Transformer is driven from main. */
    private suspend fun prepareAndRender(j: ExportJob) {
        val dir = File(cacheDir, "cut-export")
        val luts = j.project.timeline.allTracks.flatMap { it.clips }.filter { it.mediaId in j.online }.mapNotNull { it.video.color.lutUri }.distinct()
        val (avail, lutErrors) = withContext(Dispatchers.IO) {
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() } // leftovers of a killed export
            val errors = luts.mapNotNull { u -> (LutStore.load(this@ExportService, u) as? LutStore.Loaded.Failed)?.let { "${LutStore.label(u)}: ${it.reason}" } }
            StatFs(dir.path).availableBytes to errors
        }
        if (ended) return // cancelled while preparing
        if (lutErrors.isNotEmpty()) {
            fail("No se puede aplicar un LUT del proyecto (${lutErrors.joinToString("; ")}). Quítalo o vuelve a elegirlo en Color.")
            return
        }
        val needed = (j.plan.estimatedBytes * 1.2).toLong() + 64L * 1024 * 1024
        if (avail < needed) {
            fail("Espacio insuficiente: hacen falta ~${ExportMath.formatBytes(needed)} libres en el almacenamiento interno y hay ${ExportMath.formatBytes(avail)}.")
            return
        }
        val built = try {
            CompositionFactory.build(j.project, { it in j.online }, j.plan.width, j.plan.height, j.plan.frameRate, luts = { LutStore.peek(it) })
        } catch (e: IllegalArgumentException) {
            fail("No se pudo preparar la composición: ${e.message}"); return
        } catch (e: IllegalStateException) {
            fail("No se pudo preparar la composición: ${e.message}"); return
        }
        if (built == null || !built.hasPicture) {
            fail("No hay vídeo que exportar en la línea de tiempo."); return
        }
        val out = File(dir, "render_${System.currentTimeMillis()}.mp4")
        tmp = out
        val encoders = DefaultEncoderFactory.Builder(this)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(j.plan.videoBitrate).build())
            .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(j.plan.audioBitrate).build())
            .setEnableFallback(true)
            .build()
        val t = Transformer.Builder(this)
            .setVideoMimeType(j.plan.videoMime)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoders)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onRendered(out, exportResult)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    val hint = if (j.plan.videoMime == MimeTypes.VIDEO_H265) " Prueba con H.264." else ""
                    fail("Falló el render (${exportException.errorCodeName}): ${exportException.message ?: ""}.$hint")
                }
            })
            .build()
        transformer = t
        try {
            t.start(built.composition, out.absolutePath)
        } catch (e: IllegalStateException) {
            fail("No se pudo iniciar el render: ${e.message}"); return
        }
        val holder = ProgressHolder()
        progressJob = scope.launch {
            while (isActive) {
                val tr = transformer ?: break
                if (tr.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) report(holder.progress.coerceIn(0, 99), "Renderizando")
                delay(400)
            }
        }
    }

    /**
     * mediaProcessing (API 35+) is the right type for a render, but some builds do not register it in
     * their FGS policy table and reject it as "type none" (seen on a Samsung S25 Ultra, Android 16).
     * In that case fall back to dataSync, which the manifest also declares.
     */
    private fun enterForeground(n: Notification): Boolean {
        if (Build.VERSION.SDK_INT >= 35 && tryMediaProcessing(n)) return true
        return try {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            true
        } catch (e: IllegalStateException) { // ForegroundServiceStartNotAllowedException and type errors
            Log.w(TAG, "dataSync FGS rejected", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "dataSync FGS permission rejected", e)
            false
        }
    }

    @RequiresApi(35)
    private fun tryMediaProcessing(n: Notification): Boolean = try {
        ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        true
    } catch (e: InvalidForegroundServiceTypeException) {
        Log.w(TAG, "mediaProcessing FGS type rejected by this device, using dataSync", e)
        false
    }

    /** Android 15+: the system's time limit for this FGS type ran out; stop cleanly or the app is killed. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        if (job != null && transformer != null) {
            transformer?.cancel()
            fail("El sistema ha agotado el tiempo máximo permitido para exportar en segundo plano. Vuelve a exportar con la app abierta.")
        } else {
            stopSelf()
        }
    }

    private fun report(progress: Int, stage: String) {
        val j = job ?: return
        ExportManager.update(ExportState.Running(j.project.id, j.project.name, progress, stage))
        if (progress != lastNotified) {
            lastNotified = progress
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(j.project.name, progress, stage, ongoing = true))
        }
    }

    private fun onRendered(file: File, result: ExportResult) {
        progressJob?.cancel()
        val j = job ?: return
        report(99, "Verificando y guardando")
        scope.launch {
            val error = withContext(Dispatchers.IO) { validate(file, j.plan.durationUs) }
            if (error != null) { fail("El archivo renderizado no es válido: $error"); return@launch }
            val moved = withContext(Dispatchers.IO) {
                try {
                    j.destination.parentFile?.mkdirs()
                    if (!file.renameTo(j.destination)) { file.copyTo(j.destination, overwrite = true); file.delete() }
                    null
                } catch (e: IOException) {
                    "no se pudo guardar el archivo (${e.message})"
                }
            }
            if (moved != null) { fail("Render correcto pero $moved."); return@launch }
            tmp = null
            if (ended) { j.destination.delete(); return@launch }
            val size = j.destination.length()
            val assetId = try {
                j.save(j.destination)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                j.destination.delete()
                fail("El vídeo se renderizó pero no se pudo añadir a la Biblioteca: ${e.message}"); return@launch
            }
            if (ended) return@launch
            ended = true
            val detail = "${result.width}×${result.height} · ${result.videoEncoderName ?: "?"} · ${(result.averageVideoBitrate / 1000).coerceAtLeast(0)} kbps"
            ExportManager.update(ExportState.Done(j.project.id, assetId, j.destination, size, j.plan.durationUs, detail))
            finish("Exportado: ${j.project.name}", "En la Biblioteca · ${ExportMath.formatBytes(size)}")
        }
    }

    /** Null when OK, otherwise the reason. */
    private fun validate(file: File, expectedUs: Long): String? {
        if (!file.exists() || file.length() == 0L) return "archivo vacío"
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
            val mimes = (0 until ex.trackCount).mapNotNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
            if (mimes.none { it.startsWith("video/") }) return "no tiene pista de vídeo"
        } catch (e: IOException) {
            return "no se puede leer (${e.message})"
        } finally {
            ex.release()
        }
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return "sin duración"
            val tolerance = maxOf(500L, expectedUs / 1000 / 50)
            if (abs(ms - expectedUs / 1000) > tolerance) return "dura ${ms} ms y se esperaban ${expectedUs / 1000} ms"
        } catch (e: RuntimeException) {
            return "metadatos ilegibles (${e.message})"
        } finally {
            mmr.release()
        }
        return null
    }

    private fun cancel() {
        if (ended) return
        ended = true
        val t = transformer
        transformer = null
        progressJob?.cancel()
        t?.cancel()
        tmp?.delete()
        ExportManager.update(ExportState.Cancelled(job?.project?.id))
        finish("Exportación cancelada", job?.project?.name ?: "")
    }

    private fun fail(message: String) {
        Log.w(TAG, message)
        if (ended) return
        ended = true
        transformer = null
        progressJob?.cancel()
        tmp?.delete()
        ExportManager.update(ExportState.Failed(job?.project?.id, message))
        finish("Error al exportar", message)
    }

    private fun finish(title: String, text: String) {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        getSystemService(NotificationManager::class.java).notify(
            NOTIF_ID,
            NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp()).setAutoCancel(true).build(),
        )
        stopSelf()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notification(name: String, progress: Int, stage: String, ongoing: Boolean): Notification {
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, ExportService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Exportando «$name»")
            .setContentText("$stage · $progress %")
            .setProgress(100, progress, progress == 0)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(0, "Cancelar", cancel)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Exportación de Nexus Cut", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NexusCutExport"
        private const val CHANNEL = "cut-export"
        private const val NOTIF_ID = 4_204
        const val ACTION_START = "com.vidadomu.nexus.cut.export.START"
        const val ACTION_CANCEL = "com.vidadomu.nexus.cut.export.CANCEL"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ExportService::class.java).setAction(ACTION_START))
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, ExportService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
