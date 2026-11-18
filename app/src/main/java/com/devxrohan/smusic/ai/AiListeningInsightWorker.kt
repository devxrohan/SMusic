package com.devxrohan.smusic.ai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.devxrohan.smusic.MainActivity
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AiListeningNotificationsKey
import com.devxrohan.smusic.db.InternalDatabase
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AiListeningInsightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!applicationContext.dataStore.get(AiListeningNotificationsKey, false) || !canNotify(applicationContext)) {
            return@withContext Result.success()
        }
        val db = try { InternalDatabase.newInstance(applicationContext) }
            catch (_: Exception) { return@withContext Result.retry() }
        try {
            val mostPlayed = db.mostPlayedSongs(fromTimeStamp = 0L, limit = 12).firstOrNull().orEmpty()
            val recent = db.recentSongs(limit = 8).firstOrNull().orEmpty()
            val songs = (mostPlayed + recent).distinctBy { it.id }.take(16)
            val taste = songs.joinToString("\n") { it.title + " — " + it.artists.joinToString { artist -> artist.name } }
            val generated = if (songs.isEmpty()) null else AiTextClient.complete(
                applicationContext,
                "Write one friendly music discovery insight in 1-2 sentences from these listening signals. " +
                    "Do not invent counts or listening dates. Return plain text only.\n" + taste,
                120,
            ).getOrNull()
            val song = songs.firstOrNull()
            val fallback = if (song == null) applicationContext.getString(R.string.ai_insight_empty_history)
                else applicationContext.getString(R.string.ai_insight_history_summary,
                    song.title, song.artists.joinToString { it.name }.ifBlank { applicationContext.getString(R.string.ai_insight_unknown_artist) })
            if (applicationContext.dataStore.get(AiListeningNotificationsKey, false)) {
                postInsight(applicationContext, generated ?: fallback, generated != null)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        } finally {
            db.close()
        }
    }

    private fun postInsight(context: Context, message: String, aiGenerated: Boolean) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID,
                context.getString(R.string.ai_insight_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notifications)
            .setContentTitle(context.getString(if (aiGenerated) R.string.ai_insight_title else R.string.ai_insight_snapshot_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "ai_listening_insights"
        private const val NOTIFICATION_ID = 74102
        private const val WORK_NAME = "smusic_ai_listening_insights"
        private const val IMMEDIATE_WORK_NAME = "smusic_ai_listening_insight_now"

        fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AiListeningInsightWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork(WORK_NAME)
                manager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<AiListeningInsightWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
