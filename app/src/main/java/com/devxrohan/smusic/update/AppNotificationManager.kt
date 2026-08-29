package com.devxrohan.smusic.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.devxrohan.smusic.BuildConfig
import com.devxrohan.smusic.MainActivity
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AnnouncementsCacheKey
import com.devxrohan.smusic.constants.EnableUpdateNotificationKey
import com.devxrohan.smusic.constants.LastNotifiedVersionKey
import com.devxrohan.smusic.constants.LastUpdateCheckKey
import com.devxrohan.smusic.constants.SeenAnnouncementIdsKey
import com.devxrohan.smusic.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Single entry point for background app-info checking: fetches the
 * combined update+announcements manifest from the same worker, shows a
 * system notification for whichever is new, and remembers what it has
 * already shown so it never repeats itself.
 *
 * Two triggers feed into the same [checkNowAndNotify]: a silent check on
 * app-open (throttled to once per 6 hours) and an independent WorkManager
 * periodic job that keeps checking even while the app isn't open.
 */
object AppNotificationManager {
    private const val UPDATE_CHANNEL_ID = "update_notification_channel"
    private const val ANNOUNCEMENT_CHANNEL_ID = "announcement_notification_channel"
    private const val UPDATE_NOTIFICATION_ID = 9999
    private const val ANNOUNCEMENT_NOTIFICATION_ID = 9998
    private const val WORK_NAME = "app_info_check_work"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

        notificationManager.createNotificationChannel(
            NotificationChannel(
                UPDATE_CHANNEL_ID,
                context.getString(R.string.update_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.update_notification_channel_desc) }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                ANNOUNCEMENT_CHANNEL_ID,
                context.getString(R.string.announcement_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.announcement_notification_channel_desc) }
        )
    }

    fun schedulePeriodicCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<AppCheckWorker>(6, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelPeriodicCheck(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Silent check on app-open — throttled, never shows an error, just does nothing if there's nothing new. */
    fun checkOnAppOpen(context: Context) {
        scope.launch {
            val dataStore = context.dataStore
            val isEnabled = dataStore.data.map { it[EnableUpdateNotificationKey] ?: true }.first()
            if (!isEnabled) {
                cancelPeriodicCheck(context)
                return@launch
            }
            schedulePeriodicCheck(context)
            checkNowAndNotify(context)
        }
    }

    /** Does the actual fetch + decides what (if anything) to notify about. Safe to call from the worker too. */
    suspend fun checkNowAndNotify(context: Context) {
        runCatching {
            val dataStore = context.dataStore
            dataStore.edit { it[LastUpdateCheckKey] = System.currentTimeMillis() }

            val manifest = UpdateChecker().checkManifest() ?: return

            // --- Update ---
            val update = manifest.update
            if (update.versionCode > BuildConfig.VERSION_CODE && update.apkUrl.isNotBlank()) {
                val skippedVersionCode = dataStore.data.map { it[com.devxrohan.smusic.constants.SkippedUpdateVersionCodeKey] ?: -1 }.first()
                val lastNotified = dataStore.data.map { it[LastNotifiedVersionKey] ?: "" }.first()
                if (update.versionName != lastNotified && update.versionCode != skippedVersionCode) {
                    showUpdateNotification(context, update)
                    dataStore.edit { it[LastNotifiedVersionKey] = update.versionName }
                }
            }

            // --- Announcements (list) ---
            val enabledAnnouncements = manifest.announcements.filter { it.enabled && it.id.isNotBlank() }
            dataStore.edit {
                it[AnnouncementsCacheKey] = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(AnnouncementInfo.serializer()),
                    enabledAnnouncements
                )
            }

            val seenIds = dataStore.data.map { it[SeenAnnouncementIdsKey] ?: "" }.first()
                .split(",").filter { it.isNotBlank() }.toSet()
            val unseen = enabledAnnouncements.filterNot { it.id in seenIds }

            if (unseen.isNotEmpty()) {
                showAnnouncementNotification(context, unseen)
            }
        }
    }

    private fun showUpdateNotification(context: Context, update: UpdateInfo) {
        createNotificationChannels(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "settings/update")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.update_notification_title))
            .setContentText(context.getString(R.string.update_notification_text, update.versionName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        notifySafely(context, UPDATE_NOTIFICATION_ID, notification)
    }

    /** One notification covering however many new announcements arrived at once. */
    private fun showAnnouncementNotification(context: Context, unseen: List<AnnouncementInfo>) {
        createNotificationChannels(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "announcement")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 2, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = Intent(context, AnnouncementDismissReceiver::class.java)
        val deletePendingIntent = PendingIntent.getBroadcast(
            context, 3, deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, ANNOUNCEMENT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        if (unseen.size == 1) {
            val only = unseen.first()
            builder
                .setContentTitle(only.title.ifBlank { context.getString(R.string.announcement_default_title) })
                .setContentText(only.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(only.message))
        } else {
            builder
                .setContentTitle(context.getString(R.string.announcement_default_title))
                .setContentText(context.getString(R.string.announcement_multiple_text, unseen.size))
                .setStyle(
                    NotificationCompat.InboxStyle().also { style ->
                        unseen.take(5).forEach { style.addLine(it.title.ifBlank { it.message }) }
                    }
                )
        }

        notifySafely(context, ANNOUNCEMENT_NOTIFICATION_ID, builder.build())
    }

    private fun notifySafely(context: Context, id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS permission — silently skip, the
            // in-app bell badge still reflects the new announcement(s).
        }
    }

    /** Parses whatever list is currently cached from the last successful check. */
    suspend fun getCachedAnnouncements(context: Context): List<AnnouncementInfo> {
        val raw = context.dataStore.data.map { it[AnnouncementsCacheKey] ?: "" }.first()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(AnnouncementInfo.serializer()),
                raw
            )
        }.getOrDefault(emptyList())
    }

    suspend fun hasUnseenAnnouncements(context: Context): Boolean {
        val cached = getCachedAnnouncements(context)
        if (cached.isEmpty()) return false
        val seenIds = context.dataStore.data.map { it[SeenAnnouncementIdsKey] ?: "" }.first()
            .split(",").filter { it.isNotBlank() }.toSet()
        return cached.any { it.id !in seenIds }
    }

    /** Called when the person opens the bell/announcement screen — marks every currently-known announcement as seen. */
    suspend fun markAllAnnouncementsSeen(context: Context) {
        val cached = getCachedAnnouncements(context)
        if (cached.isEmpty()) return
        val dataStore = context.dataStore
        val seenIds = dataStore.data.map { it[SeenAnnouncementIdsKey] ?: "" }.first()
            .split(",").filter { it.isNotBlank() }.toMutableSet()
        seenIds.addAll(cached.map { it.id })
        // Keep this from growing forever — only the most recent 100 ids matter.
        val trimmed = seenIds.toList().takeLast(100)
        dataStore.edit { it[SeenAnnouncementIdsKey] = trimmed.joinToString(",") }
    }
}
