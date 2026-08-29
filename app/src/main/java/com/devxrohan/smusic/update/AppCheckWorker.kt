package com.devxrohan.smusic.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AppCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            AppNotificationManager.checkNowAndNotify(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
