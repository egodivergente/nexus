package com.vidadomu.nexus.core.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.vidadomu.nexus.MainActivity
import com.vidadomu.nexus.NexusApplication
import com.vidadomu.nexus.R
import com.vidadomu.nexus.core.model.GenerationStatus
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class GenerationPollingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val app = applicationContext as? NexusApplication ?: return Result.failure()
        app.ready.await()
        val repository = app.generationRepository

        var attempts = 0
        val maxAttempts = 60 // Max 5-10 minutes with backoff

        while (attempts < maxAttempts) {
            attempts++
            val pollResult = repository.pollJob(jobId)

            if (pollResult.isSuccess) {
                val job = pollResult.getOrThrow()
                if (job.status == GenerationStatus.COMPLETED) {
                    val modalityLabel = when (job.modality.name) {
                        "VIDEO" -> "Vídeo listo"
                        "IMAGE" -> "Imagen lista"
                        "AUDIO" -> "Audio listo"
                        else -> "Resultado listo"
                    }
                    showCompletionNotification(
                        title = "Nexus: $modalityLabel",
                        message = "Generación completada: «${job.prompt.take(40)}…»",
                        jobId = jobId
                    )
                    return Result.success()
                } else if (job.status == GenerationStatus.FAILED) {
                    showCompletionNotification(
                        title = if(job.parameters["__tracking_paused"]=="true") "Nexus: seguimiento pausado" else "Nexus: Generación fallida",
                        message = job.error ?: "No se pudo completar la generación.",
                        jobId = jobId
                    )
                    return Result.failure()
                } else if (job.status == GenerationStatus.CANCELLED) {
                    return Result.success()
                }
            } else if (repository.getJob(jobId) == null) {
                return Result.failure()
            }

            // Adaptive backoff: 3s early, 6s later to protect battery
            val waitTime = if (attempts < 5) 3000L else 6000L
            delay(waitTime)
        }

        return Result.retry()
    }

    private fun showCompletionNotification(title: String, message: String, jobId: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_JOB_ID, jobId)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            jobId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(jobId.hashCode(), notification)
    }

    private fun createChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Generaciones de Nexus",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos cuando terminan tus generaciones en segundo plano"
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val KEY_JOB_ID = "nexus_polling_job_id"
        const val EXTRA_NAVIGATE_JOB_ID = "extra_navigate_job_id"
        private const val CHANNEL_ID = "nexus_generations_channel"

        fun enqueue(context: Context, jobId: String) {
            val data = workDataOf(KEY_JOB_ID to jobId)
            val request = OneTimeWorkRequestBuilder<GenerationPollingWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "poll_$jobId",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
