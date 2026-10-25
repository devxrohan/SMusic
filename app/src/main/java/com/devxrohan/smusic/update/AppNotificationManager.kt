package com.devxrohan.smusic.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.BuildConfig
import com.devxrohan.smusic.MainActivity
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AnnouncementsCacheKey
import com.devxrohan.smusic.constants.EnableUpdateNotificationKey
import com.devxrohan.smusic.constants.LastNotifiedVersionKey
import com.devxrohan.smusic.constants.LastUpdateFetchKey
import com.devxrohan.smusic.constants.LastUpdateCheckKey
import com.devxrohan.smusic.constants.PendingUpdateJsonKey
import com.devxrohan.smusic.constants.PushAnnouncementsKey
import com.devxrohan.smusic.constants.SeenUpdateVersionKey
import com.devxrohan.smusic.constants.SeenAnnouncementIdsKey
import com.devxrohan.smusic.constants.UpdateNotifiedAtKey
import com.devxrohan.smusic.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json

/**
 * Handles update/announcement notifications. App updates are read directly
 * from GitHub Releases. Developer announcements retain their existing Worker
 * manifest source and are delivered independently from the update pipeline.
 */
object AppNotificationManager {
    private const val UPDATE_CHANNEL_ID = "update_notification_channel"
    private const val ANNOUNCEMENT_CHANNEL_ID = "announcement_notification_channel"
    private const val UPDATE_NOTIFICATION_ID = 9999
    private const val ANNOUNCEMENT_NOTIFICATION_ID = 9998
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

    /** Starts both the immediate app-open check and a real background WorkManager check. */
    fun schedulePeriodicCheck(context: Context) {
        scope.launch {
            val enabled = context.dataStore.data.map { it[EnableUpdateNotificationKey] ?: true }.first()
            if (!enabled) {
                cancelPeriodicCheck(context)
                return@launch
            }
            checkOnAppOpen(context)
            val request = androidx.work.PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, java.util.concurrent.TimeUnit.HOURS)
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                15,
                java.util.concurrent.TimeUnit.MINUTES,
            )
            .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "SMusicUpdateCheckWorker",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    fun cancelPeriodicCheck(context: Context) {
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("SMusicUpdateCheckWorker")
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
            checkNowAndNotify(context)
        }
    }

    /**
     * Runs the two background pipelines independently. GitHub update failures
     * must never prevent the developer announcement endpoint from being read.
     * This is important because announcements are intentionally authored and
     * delivered through the Worker endpoint.
     */
    suspend fun checkNowAndNotify(context: Context): Boolean {
        val dataStore = context.dataStore
        dataStore.edit { it[LastUpdateCheckKey] = System.currentTimeMillis() }

        var updateSucceeded = false
        var announcementSucceeded = false

        // UPDATE PIPELINE: GitHub routes first (raced). The Worker is only a last-resort fallback inside checkForUpdate().
        // Throttled: this function also runs every minute while the app is open for announcements,
        // and hitting GitHub that often would burn through its 60 requests/hour anonymous API limit.
        val lastFetch = dataStore.data.map { it[LastUpdateFetchKey] ?: 0L }.first()
        val updateDue = System.currentTimeMillis() - lastFetch >= UPDATE_FETCH_INTERVAL_MS
        if (updateDue) runCatching {
            dataStore.edit { it[LastUpdateFetchKey] = System.currentTimeMillis() }
            val update = UpdateChecker(GitHubReleaseFetcher(context.applicationContext)).checkForUpdate()
            updateSucceeded = true
            if (update == null) {
                // Up to date (or just updated): nothing pending any more.
                clearPendingUpdate(context)
                UpdateFiles.deleteInstalledOrOutdated(context, BuildConfig.VERSION_NAME)
            } else {
                savePendingUpdate(context, update)
                if (dataStore.data.map { it[EnableUpdateNotificationKey] ?: true }.first()) {
                    ensureUpdateNotification(context, update)
                }
            }
        }

        // ANNOUNCEMENT PIPELINE: always fetch the Worker independently.
        // A GitHub outage/network block must not suppress developer messages.
        // One source of truth: when the phone has Google Play services, developer messages come ONLY from
        // Firebase push (so the Worker can never show a second, different message). The Worker list is
        // just a fallback for phones without Play services.
        if (hasPlayServices(context)) announcementSucceeded = true else runCatching {
            val manifest = UpdateChecker(GitHubReleaseFetcher(context.applicationContext)).checkManifest()
                ?: return@runCatching
            val enabledAnnouncements = manifest.announcements
                .filter { it.enabled && it.id.isNotBlank() }

            dataStore.edit {
                it[AnnouncementsCacheKey] = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(AnnouncementInfo.serializer()),
                    enabledAnnouncements
                )
            }

            val seenIds = dataStore.data.map { it[SeenAnnouncementIdsKey] ?: "" }.first()
                .split(",").filter { it.isNotBlank() }.toSet()
            val unseen = enabledAnnouncements.filterNot { it.id in seenIds }
            if (unseen.isNotEmpty()) showAnnouncementNotification(context, unseen)
            announcementSucceeded = true
        }

        return updateSucceeded || announcementSucceeded
    }

    private fun showUpdateNotification(context: Context, update: UpdateInfo) {
        createNotificationChannels(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.devxrohan.smusic.OPEN_UPDATE_NOTIFICATION"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "settings/update")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 9901, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = PendingIntent.getBroadcast(
            context, 2, Intent(context, UpdateDismissReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.update_notification_title))
            .setContentText(context.getString(R.string.update_notification_text, update.versionName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            // Swipeable system notification. Swiping it only dismisses the phone copy;
            // the in-app notification badge remains until the user opens Notifications.
            .setOngoing(false)
            .setAutoCancel(true)
            .setDeleteIntent(dismissIntent)
            .setOnlyAlertOnce(true)
            .build()

        notifySafely(context, UPDATE_NOTIFICATION_ID, notification)
    }

    private const val UPDATE_FETCH_INTERVAL_MS = 30 * 60 * 1000L

    /**
     * Background / fetch path: posts the "update available" notification only the
     * first time a given version is found. (If the user swiped it, background
     * checks must not nag again; it returns on the next app open instead, see
     * [notifyPendingUpdateOnAppOpen].)
     */
    private suspend fun ensureUpdateNotification(context: Context, update: UpdateInfo) {
        val dataStore = context.dataStore
        val lastVersion = dataStore.data.map { it[LastNotifiedVersionKey] ?: "" }.first()
        if (update.versionName == lastVersion) return
        showUpdateNotification(context, update)
        dataStore.edit {
            it[LastNotifiedVersionKey] = update.versionName
            it[UpdateNotifiedAtKey] = System.currentTimeMillis()
        }
    }

    /**
     * Called when the app is opened / brought to the foreground (not by the 60 s
     * poll). If an update is still pending and its notification isn't in the
     * shade (user swiped it), post it again. If the app was updated meanwhile,
     * remove the notification and all pending state instead.
     */
    fun notifyPendingUpdateOnAppOpen(context: Context) {
        scope.launch {
            val pending = getPendingUpdate(context)
            if (pending == null) {
                clearPendingUpdate(context)
                return@launch
            }
            val enabled = context.dataStore.data.map { it[EnableUpdateNotificationKey] ?: true }.first()
            if (!enabled) return@launch
            if (!isUpdateNotificationActive(context)) {
                showUpdateNotification(context, pending)
            }
        }
    }

    /** The user has seen this update (swiped the notification, opened the bell or the update page): hide the badge. */
    suspend fun markUpdateSeen(context: Context) {
        val pending = getPendingUpdate(context) ?: return
        context.dataStore.edit { it[SeenUpdateVersionKey] = pending.versionName }
    }

    /** True when there is a pending update the user hasn't seen yet (drives the bell badge). */
    suspend fun hasUnseenUpdate(context: Context): Boolean =
        // Always on while an update is waiting, whatever the notification switch says and whether or not the
        // user has looked at it. It only goes away once the update is installed.
        getPendingUpdate(context) != null

    /** Removes the "update available" notification from the phone's notification shade. */
    fun cancelUpdateNotification(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(UPDATE_NOTIFICATION_ID) }
    }

    /**
     * The "Update notifications" switch. Off: no update notifications on the phone (the update still
     * shows in the app's Notifications screen, with its badge, until installed). On: the previous behaviour.
     */
    suspend fun setUpdateNotificationsEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[EnableUpdateNotificationKey] = enabled }
        if (enabled) {
            schedulePeriodicCheck(context)
            notifyPendingUpdateOnAppOpen(context) // shows it again right away if an update is waiting
        } else {
            cancelUpdateNotification(context)
            cancelPeriodicCheck(context)
        }
    }

    private fun isUpdateNotificationActive(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { manager.activeNotifications.any { it.id == UPDATE_NOTIFICATION_ID } }.getOrDefault(false)
    }

    // ---- Pending update (bell badge + persistent notification) -------------

    /** Remembers the newest update that exists but isn't installed yet. */
    suspend fun savePendingUpdate(context: Context, info: UpdateInfo) {
        context.dataStore.edit {
            it[PendingUpdateJsonKey] = json.encodeToString(UpdateInfo.serializer(), info)
        }
    }

    /** The pending update, or null if none / it is no longer newer than the installed version. */
    suspend fun getPendingUpdate(context: Context): UpdateInfo? {
        val raw = context.dataStore.data.map { it[PendingUpdateJsonKey] ?: "" }.first()
        if (raw.isBlank()) return null
        val info = runCatching { json.decodeFromString(UpdateInfo.serializer(), raw) }.getOrNull() ?: return null
        return info.takeIf { VersionCompare.isNewer(it.versionName, BuildConfig.VERSION_NAME) }
    }

    suspend fun hasPendingUpdate(context: Context): Boolean = getPendingUpdate(context) != null

    /** Called once the app is up to date: removes the notification and all pending state. */
    suspend fun clearPendingUpdate(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(UPDATE_NOTIFICATION_ID) }
        context.dataStore.edit {
            it.remove(PendingUpdateJsonKey)
            it.remove(SeenUpdateVersionKey)
            it.remove(UpdateNotifiedAtKey)
            it.remove(LastNotifiedVersionKey)
        }
    }

    /** One notification covering however many new announcements arrived at once. */
    private fun showAnnouncementNotification(context: Context, unseen: List<AnnouncementInfo>) {
        createNotificationChannels(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.devxrohan.smusic.OPEN_ANNOUNCEMENT_NOTIFICATION"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "announcement")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 9902, openAppIntent,
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
    private val announcementListSerializer =
        kotlinx.serialization.builtins.ListSerializer(AnnouncementInfo.serializer())

    private suspend fun readList(context: Context, key: androidx.datastore.preferences.core.Preferences.Key<String>): List<AnnouncementInfo> {
        val raw = context.dataStore.data.map { it[key] ?: "" }.first()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString(announcementListSerializer, raw) }.getOrDefault(emptyList())
    }

    /** True when Google Play services (needed for Firebase push) are on the phone. */
    fun hasPlayServices(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo("com.google.android.gms", 0); true }.getOrDefault(false)

    private fun isExpired(info: AnnouncementInfo, now: Long = System.currentTimeMillis()): Boolean =
        info.expiresAt > 0L && now >= info.expiresAt

    /**
     * What the bell shows: Firebase push messages (plus the Worker list only on phones without Play
     * services), minus anything whose time has run out, de-duplicated by id.
     */
    suspend fun getCachedAnnouncements(context: Context): List<AnnouncementInfo> {
        val now = System.currentTimeMillis()
        val worker = if (hasPlayServices(context)) emptyList() else readList(context, AnnouncementsCacheKey)
        val push = readList(context, PushAnnouncementsKey)
        return (worker + push).filterNot { isExpired(it, now) }.distinctBy { it.id }
    }

    /**
     * A message delivered by Firebase Cloud Messaging. Stored for the bell (apart from the Worker cache)
     * and, when [notify] is true and not yet seen, shown as its own notification. Messages that already
     * ran out of time are ignored.
     */
    suspend fun onPushAnnouncement(context: Context, info: AnnouncementInfo, notify: Boolean = true) {
        if (info.id.isBlank() || (info.title.isBlank() && info.message.isBlank())) return
        if (isExpired(info)) return
        val dataStore = context.dataStore
        val updated = (readList(context, PushAnnouncementsKey).filterNot { it.id == info.id || isExpired(it) } +
            info.copy(enabled = true)).takeLast(30)
        dataStore.edit { it[PushAnnouncementsKey] = json.encodeToString(announcementListSerializer, updated) }
        if (!notify) return
        val seenIds = dataStore.data.map { it[SeenAnnouncementIdsKey] ?: "" }.first()
            .split(",").filter { it.isNotBlank() }.toSet()
        if (info.id !in seenIds) showPushNotification(context, info)
    }

    /**
     * The user swiped a push notification away on the phone. It never comes back as a phone
     * notification, and it is NOT removed from the in-app Notifications screen.
     */
    suspend fun onPushNotificationSwiped(context: Context, id: String) {
        // Intentionally nothing: swiping the phone notification only dismisses that copy.
        // The message stays in the app's Notifications screen until its time is up, or until
        // the user slides it away there. It is not re-posted as a phone notification.
    }

    private suspend fun showPushNotification(context: Context, info: AnnouncementInfo) {
        createNotificationChannels(context)
        val notificationId = 20_000 + (info.id.hashCode() and 0xFFFF)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "announcement")
        }
        val contentIntent = PendingIntent.getActivity(
            context, 2, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // requestCode differs per message so each notification keeps its own message id.
        val deleteIntent = PendingIntent.getBroadcast(
            context, notificationId,
            Intent(context, PushDismissReceiver::class.java).putExtra("id", info.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, ANNOUNCEMENT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle(info.title.ifBlank { context.getString(R.string.announcement_default_title) })
            .setContentText(info.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.message))
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        // Download the optional public image URL for Android's expanded big-picture notification.
        // The same URL is retained in AnnouncementInfo for the in-app notification card.
        val imageBitmap = info.imageUrl.takeIf { it.startsWith("https://", ignoreCase = true) }
            ?.let { downloadNotificationImage(it) }
        if (imageBitmap != null) {
            val notificationBitmap = centerCropNotificationImage(imageBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(notificationBitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .setBigContentTitle(info.title.ifBlank { context.getString(R.string.announcement_default_title) })
                    .setSummaryText(info.message)
            )
        }
        // Time-limited message: the system removes the notification itself when the time is up.
        if (info.expiresAt > 0L) builder.setTimeoutAfter((info.expiresAt - System.currentTimeMillis()).coerceAtLeast(1_000L))

        notifySafely(context, notificationId, builder.build())
    }

    /** Crop around the image center to a wide notification-friendly aspect ratio. */
    private fun centerCropNotificationImage(source: Bitmap, targetRatio: Float = 2f): Bitmap {
        val sourceRatio = source.width.toFloat() / source.height.coerceAtLeast(1)
        val cropWidth: Int
        val cropHeight: Int
        if (sourceRatio > targetRatio) {
            cropHeight = source.height
            cropWidth = (cropHeight * targetRatio).toInt().coerceAtMost(source.width)
        } else {
            cropWidth = source.width
            cropHeight = (cropWidth / targetRatio).toInt().coerceAtMost(source.height)
        }
        val left = ((source.width - cropWidth) / 2).coerceAtLeast(0)
        val top = ((source.height - cropHeight) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(source, left, top, cropWidth.coerceAtLeast(1), cropHeight.coerceAtLeast(1))
    }

    private suspend fun downloadNotificationImage(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(imageUrl).openConnection() as? HttpURLConnection) ?: return@withContext null
            connection.connectTimeout = 5_000
            connection.readTimeout = 7_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "image/*")
            connection.connect()
            if (connection.responseCode !in 200..299) return@withContext null
            val maxBytes = 5 * 1024 * 1024
            val length = connection.contentLengthLong
            if (length > maxBytes) return@withContext null
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) return@withContext null
                    output.write(buffer, 0, read)
                }
                BitmapFactory.decodeByteArray(output.toByteArray(), 0, total)
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Remove an announcement from the in-app notification list and dismiss its tray copy. */
    suspend fun dismissAnnouncement(context: Context, id: String) {
        if (id.isBlank()) return
        val dataStore = context.dataStore
        val push = readList(context, PushAnnouncementsKey).filterNot { it.id == id }
        val worker = readList(context, AnnouncementsCacheKey).filterNot { it.id == id }
        val seen = dataStore.data.map { it[SeenAnnouncementIdsKey] ?: "" }.first()
            .split(",").filter { it.isNotBlank() }.toMutableSet()
        seen.add(id)
        dataStore.edit {
            it[PushAnnouncementsKey] = json.encodeToString(announcementListSerializer, push)
            it[AnnouncementsCacheKey] = json.encodeToString(announcementListSerializer, worker)
            it[SeenAnnouncementIdsKey] = seen.toList().takeLast(100).joinToString(",")
        }
        runCatching {
            NotificationManagerCompat.from(context).cancel(20_000 + (id.hashCode() and 0xFFFF))
            NotificationManagerCompat.from(context).cancel(ANNOUNCEMENT_NOTIFICATION_ID)
        }
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
