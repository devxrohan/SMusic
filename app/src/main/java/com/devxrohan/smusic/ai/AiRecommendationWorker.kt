package com.devxrohan.smusic.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import timber.log.Timber

class AiRecommendationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Timber.tag("AiRecommendationWorker").d("Running periodic AI recommendation generation...")
            val result = AiRecommendationHelper.generateRecommendations(applicationContext)
            if (result.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.tag("AiRecommendationWorker").e(e, "Error executing AI recommendation work")
            Result.retry()
        }
    }
}
