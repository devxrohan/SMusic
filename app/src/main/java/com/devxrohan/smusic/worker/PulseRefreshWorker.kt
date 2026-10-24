package com.devxrohan.smusic.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.devxrohan.smusic.db.InternalDatabase
import com.devxrohan.smusic.pulse.PulseEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Keeps Pulse warm away from Home rendering. It only performs cheap persisted learning/backfill;
 * network candidate generation remains demand-driven so background work never consumes the user's data.
 */
class PulseRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = InternalDatabase.newInstance(applicationContext)
        try {
            PulseEngine(applicationContext, database).backfillIfNeeded()
            Result.success()
        } catch (t: Throwable) {
            Timber.w(t, "Pulse background refresh failed")
            Result.retry()
        } finally {
            runCatching { database.close() }
        }
    }
}
