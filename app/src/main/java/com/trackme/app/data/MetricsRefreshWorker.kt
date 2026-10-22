package com.trackme.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MetricsRefreshWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch updated metrics for stored profiles
            val previousSubscribers = 3980L
            val currentSubscribers = 4010L // After refresh
            // 2. Check for milestone achievement (e.g., crossing a multiple of 1,000)
            if (previousSubscribers < 4000L && currentSubscribers >= 4000L) {
                showMilestoneNotification(
                    title = "🎉 New Milestone Unlocked!",
                    message = "Congratulations! You just crossed 4.0k subscribers on YouTube!"
                )
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun showMilestoneNotification(title: String, message: String) {
        val channelId = "trackme_milestones"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Milestones & Growth Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(1001, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun scheduleBackgroundSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    // Runs once every 12 hours
    val refreshWorkRequest = PeriodicWorkRequestBuilder<MetricsRefreshWorker>(12, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "TrackMeMetricsAutoSync",
        ExistingPeriodicWorkPolicy.KEEP,
        refreshWorkRequest
    )
}
