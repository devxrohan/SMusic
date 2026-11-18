package com.devxrohan.smusic.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import timber.log.Timber
import com.devxrohan.smusic.constants.AiRecommendationsKey
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import kotlinx.coroutines.CancellationException

class AiRecommendationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!applicationContext.dataStore.get(AiRecommendationsKey, false)) return Result.success()
        return try {
            Timber.tag("AiRecommendationWorker").d("Running periodic AI recommendation generation...")
            val result = AiRecommendationHelper.generateRecommendations(applicationContext)
            if (result.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.tag("AiRecommendationWorker").e(e, "Error executing AI recommendation work")
            Result.retry()
        }
    }
}
