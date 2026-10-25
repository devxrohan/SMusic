package com.devxrohan.smusic.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.utils.dataStore

/**
 * Background WorkManager job: runs the combined update + announcement check.
 * (The Worker *endpoint* is also the last-resort update source inside
 * [UpdateChecker.checkForUpdate], after every GitHub route has failed.)
 */
class UpdateCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val success = runFallbackCheck(applicationContext)
        return if (success) Result.success() else Result.retry()
    }

    companion object {
        /** Fetch the Worker endpoint's update payload as the popup's last resort. */
        suspend fun fetchWorkerUpdate(context: Context): UpdateInfo? {
            val manifest = UpdateChecker(GitHubReleaseFetcher(context.applicationContext)).checkManifest() ?: return null
            val info = manifest.update
            return info.takeIf { it.versionName.isNotBlank() && it.apkUrl.isNotBlank() }
        }

        /** Announcement-only fallback; never depends on GitHub update success. */
        suspend fun runAnnouncementFallback(context: Context): Boolean {
            return runCatching {
                val manifest = UpdateChecker(GitHubReleaseFetcher(context.applicationContext)).checkManifest() ?: return false
                // Reuse the normal notification/cache pipeline without running
                // another GitHub update check. The manifest is still Worker data.
                val enabled = manifest.announcements.filter { it.enabled && it.id.isNotBlank() }
                val store = context.dataStore
                store.edit { it[com.devxrohan.smusic.constants.AnnouncementsCacheKey] =
                    kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(AnnouncementInfo.serializer()), enabled) }
                true
            }.getOrDefault(false)
        }

        /** Legacy combined background path retained for WorkManager. */
        suspend fun runFallbackCheck(context: Context): Boolean =
            AppNotificationManager.checkNowAndNotify(context)
    }
}
