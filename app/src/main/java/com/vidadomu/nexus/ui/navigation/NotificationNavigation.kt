package com.vidadomu.nexus.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vidadomu.nexus.MainActivity

/** Notification delivery reuses the existing Activity; each launch is handled once. */
object NotificationNavigation {
    const val EXTRA_JOB_ID = "extra_navigate_job_id"

    fun intent(context: Context, jobId: String): Intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        // PendingIntent identity must distinguish jobs even when String.hashCode() collides.
        data = Uri.Builder().scheme("nexus").authority("notification").appendPath(jobId).build()
        putExtra(EXTRA_JOB_ID, jobId)
    }

    fun takeJobId(intent: Intent): String? {
        val jobId = intent.getStringExtra(EXTRA_JOB_ID)
        intent.removeExtra(EXTRA_JOB_ID)
        return jobId?.takeIf { it.isNotBlank() }
    }
}
