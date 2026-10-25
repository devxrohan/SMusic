package com.devxrohan.smusic.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.devxrohan.smusic.BuildConfig
import com.devxrohan.smusic.constants.UpdateSnoozeUntilKey
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.getSuspend
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
}

sealed class UpdateState {
    data object Idle : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateState()
    data class ReadyToInstall(val info: UpdateInfo, val apkFile: File) : UpdateState()
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState()
}

/**
 * In-app updater: GitHub Releases (several raced routes, DNS-over-HTTPS
 * fallback) is the source for versions and APKs, the Worker manifest is the
 * last-resort version source, and if even that fails the browser is opened on
 * the direct APK. The APK is streamed into the app cache and the system
 * package installer handles the final install.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateChecker: UpdateChecker,
    private val releaseFetcher: GitHubReleaseFetcher,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _suppressGlobalDialog = MutableStateFlow(false)
    val suppressGlobalDialog: StateFlow<Boolean> = _suppressGlobalDialog.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var downloadCancelled = false
    private var downloadJob: kotlinx.coroutines.Job? = null

    fun setSuppressGlobalDialog(suppress: Boolean) {
        _suppressGlobalDialog.value = suppress
    }

    private fun existingApkFor(info: UpdateInfo): File? {
        val dir = File(context.cacheDir, "updates")
        return File(dir, "smusic-${info.versionName}.apk")
            .takeIf { it.exists() && it.length() > 0L }
    }

    private fun showAvailable(info: UpdateInfo) {
        _state.value = existingApkFor(info)?.let { UpdateState.ReadyToInstall(info, it) }
            ?: UpdateState.Available(info)
    }

    /**
     * Called on every successful "you're on the newest version" result (and
     * after an app update): removes the persistent notification, the bell
     * badge data, the snooze and the downloaded APK.
     */
    private suspend fun onUpToDate() {
        AppNotificationManager.clearPendingUpdate(context)
        context.dataStore.edit { it.remove(UpdateSnoozeUntilKey) }
        UpdateFiles.deleteInstalledOrOutdated(context, BuildConfig.VERSION_NAME)
        if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }

    @Volatile private var lastSilentCheckAt = 0L

    /**
     * Foreground check. [force] = app open / onboarding finished: always hits the
     * network. Otherwise it is throttled so the 60 s foreground poll doesn't
     * hammer GitHub. "Later" hides the popup for a while (snooze) but the
     * update stays pending (bell badge + notification) until it is installed.
     */
    suspend fun checkSilently(force: Boolean = false) {
        if (_state.value != UpdateState.Idle && _state.value !is UpdateState.Available) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSilentCheckAt < SILENT_CHECK_THROTTLE_MS) return
        lastSilentCheckAt = now

        // A previous update may have just been installed: clear leftovers right away.
        UpdateFiles.deleteInstalledOrOutdated(context, BuildConfig.VERSION_NAME)

        // Instant popup from what we already know (no network needed): this is what makes the
        // popup appear the moment the app opens, even offline or on a network that blocks GitHub.
        val snoozedUntil = context.dataStore.getSuspend(UpdateSnoozeUntilKey) ?: 0L
        fun snoozed() = System.currentTimeMillis() < snoozedUntil
        if (_state.value == UpdateState.Idle && !snoozed()) {
            AppNotificationManager.getPendingUpdate(context)?.let { showAvailable(it) }
        }

        val outcome = runCatching { updateChecker.checkForUpdate() }
        if (outcome.isFailure) {
            // Offline / blocked: try again in ~2 minutes instead of waiting out the full throttle.
            lastSilentCheckAt = System.currentTimeMillis() - SILENT_CHECK_THROTTLE_MS + RETRY_AFTER_FAILURE_MS
            return
        }
        val info = outcome.getOrNull()
        if (info == null) {
            onUpToDate()
            return
        }
        AppNotificationManager.savePendingUpdate(context, info)
        if (snoozed()) return // user tapped Later recently
        val current = _state.value
        if (current == UpdateState.Idle || current is UpdateState.Available) showAvailable(info)
    }

    /** Shows the already-known pending update (no network), ignoring the snooze. Used by the bell / notification / update screen. */
    suspend fun restorePending() {
        val info = AppNotificationManager.getPendingUpdate(context) ?: return
        // Viewing the update page is not the same as opening the in-app Notifications inbox.
        if (_state.value != UpdateState.Idle) return
        showAvailable(info)
    }

    /** "Later": hide the popup for [SNOOZE_MS]; the update remains pending in the bell and notification. */
    suspend fun later() {
        context.dataStore.edit { it[UpdateSnoozeUntilKey] = System.currentTimeMillis() + SNOOZE_MS }
        if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }

    /** Deletes every downloaded update APK. Returns freed bytes. */
    fun clearDownloadedApks(): Long {
        val freed = UpdateFiles.clear(context)
        val current = _state.value
        if (current is UpdateState.ReadyToInstall) _state.value = UpdateState.Available(current.info)
        return freed
    }

    suspend fun releaseHistory(): List<GitHubRelease> = releaseFetcher.fetchReleases()

    suspend fun checkNow(): UpdateCheckResult {
        return try {
            val info = updateChecker.checkForUpdate()
            if (info == null) {
                onUpToDate()
                return UpdateCheckResult.UpToDate
            }
            AppNotificationManager.savePendingUpdate(context, info)
            context.dataStore.edit { it.remove(UpdateSnoozeUntilKey) }
            if (_state.value == UpdateState.Idle) showAvailable(info)
            UpdateCheckResult.Available(info)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            // checkForUpdate() already tried every GitHub route (each with a
            // DNS-over-HTTPS retry) and then the Worker. Nothing worked, so
            // hand the user the direct APK / latest release in the browser.
            // Refresh the bell announcements in the background, without making
            // the browser fallback wait for it.
            scope.launch { runCatching { UpdateCheckWorker.runAnnouncementFallback(context) } }
            openUrlInBrowser(releaseFetcher.fallbackDownloadUrl())
            UpdateCheckResult.Failed(error.message ?: "Couldn't reach GitHub Releases")
        }
    }

    fun startDownload(info: UpdateInfo) {
        if (info.apkUrl.isBlank()) {
            openReleaseInBrowser(info)
            _state.value = UpdateState.Failed(info, "No APK asset was found. The GitHub release page was opened.")
            return
        }
        downloadCancelled = false
        _state.value = UpdateState.Downloading(info, 0)
        val dir = UpdateFiles.dir(context)
        val target = File(dir, "smusic-${info.versionName}.apk")
        downloadJob = scope.launch {
            runCatching {
                dir.listFiles()?.forEach { it.delete() }

                releaseFetcher.downloadApk(info.apkUrl, target) { progress ->
                    if (!downloadCancelled) {
                        _state.value = UpdateState.Downloading(info, progress)
                    }
                }
                if (!downloadCancelled && target.exists() && target.length() > 0L) {
                    _state.value = UpdateState.ReadyToInstall(info, target)
                } else if (!downloadCancelled) {
                    error("The downloaded APK is empty")
                }
            }.onFailure { error ->
                // Never leave a partial APK behind: it would look like a finished download later.
                runCatching { target.delete() }
                if (!downloadCancelled) {
                    openReleaseInBrowser(info)
                    _state.value = UpdateState.Failed(
                        info,
                        "Couldn't download the update in-app. The direct APK download was opened in your browser.",
                    )
                } else {
                    _state.value = UpdateState.Available(info)
                }
            }
        }
    }

    fun cancelDownload() {
        downloadCancelled = true
        downloadJob?.cancel()
    }

    fun openReleaseInBrowser(info: UpdateInfo) {
        // A direct APK URL lets Chrome/other browser download the selected ABI
        // immediately. If the asset URL is unavailable, use the release page.
        val url = info.apkUrl.takeIf { it.isNotBlank() }
            ?: info.releaseUrl.takeIf { it.isNotBlank() }
            ?: releaseFetcher.latestReleasePage
        openUrlInBrowser(url)
    }

    private fun openUrlInBrowser(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun install(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
        context.startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun dismiss() {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Idle
    }

    private companion object {
        /** How long "Later" hides the popup. */
        const val SNOOZE_MS = 3 * 60 * 60 * 1000L
        /** Minimum gap between non-forced silent checks (the foreground poll runs every minute). */
        const val SILENT_CHECK_THROTTLE_MS = 15 * 60 * 1000L
        /** After a failed silent check (offline / blocked), the next attempt comes this soon. */
        const val RETRY_AFTER_FAILURE_MS = 2 * 60 * 1000L
    }
}
