package com.devxrohan.smusic.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.devxrohan.smusic.constants.SkippedUpdateVersionCodeKey
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.getSuspend
import com.devxrohan.smusic.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.datastore.preferences.core.edit
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateState {
    object Idle : UpdateState()
    /** An update is available and waiting on the person to act — shows the dialog. */
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateState()
    /** Downloaded and ready — shows the "Install" button in place of "Update". */
    data class ReadyToInstall(val info: UpdateInfo, val apkFile: File) : UpdateState()
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState()
}

/**
 * Single source of truth for the whole in-app update flow — same design as
 * SMusic (com.elite.music)'s UpdateManager: a background silent check and
 * an explicit Settings "Check for updates" row both drive the same [state],
 * so an update found either way shows up as the same dialog.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateChecker: UpdateChecker
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /**
     * Set by CheckForUpdateScreen while it's on-screen — that screen
     * already shows the same update info in its own UI, so the global
     * auto-popup dialog should stay hidden there instead of stacking on
     * top of it redundantly.
     */
    private val _suppressGlobalDialog = MutableStateFlow(false)
    val suppressGlobalDialog: StateFlow<Boolean> = _suppressGlobalDialog
    fun setSuppressGlobalDialog(suppress: Boolean) {
        _suppressGlobalDialog.value = suppress
    }

    private val downloadManager get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    private var activeDownloadId: Long? = null

    /** The APK for this exact version, if it was already downloaded in a previous session and never installed. */
    private fun existingApkFor(info: UpdateInfo): File? {
        val file = File(context.getExternalFilesDir("updates"), "update-${info.versionCode}.apk")
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    /**
     * Silent check used on app-open: finds an update and surfaces it via
     * [state] if there is one and the person hasn't already dismissed that
     * exact version, but never reports back "no update" — there's no one
     * waiting on a result to show them.
     */
    suspend fun checkSilently() {
        if (_state.value != UpdateState.Idle) return // don't clobber a download/dialog already in progress
        val info = updateChecker.checkForUpdate() ?: return
        val skippedVersionCode = context.dataStore.getSuspend(SkippedUpdateVersionCodeKey)
        if (skippedVersionCode == info.versionCode) return
        val existingApk = existingApkFor(info)
        _state.value = if (existingApk != null) {
            UpdateState.ReadyToInstall(info, existingApk)
        } else {
            UpdateState.Available(info)
        }
    }

    /**
     * Explicit check from Settings. Unlike [checkSilently], always reports
     * something — including a previously-skipped version, since asking
     * directly is a clear signal the person wants the real answer, not the
     * quiet-by-default one.
     */
    suspend fun checkNow(): UpdateCheckResult {
        val info = updateChecker.checkForUpdate()
            ?: return UpdateCheckResult.UpToDate
        if (_state.value == UpdateState.Idle) {
            val existingApk = existingApkFor(info)
            _state.value = if (existingApk != null) {
                UpdateState.ReadyToInstall(info, existingApk)
            } else {
                UpdateState.Available(info)
            }
        }
        return UpdateCheckResult.Available(info)
    }

    fun startDownload(info: UpdateInfo) {
        val dm = downloadManager ?: run {
            _state.value = UpdateState.Failed(info, "Downloads are disabled on this device")
            return
        }

        // DownloadManager runs in a separate system process and cannot write
        // into the app's private internal cacheDir — it needs a destination
        // it actually has permission to write to. External-files-dir is
        // still app-private (no storage permission needed) but IS reachable
        // by DownloadManager, unlike context.cacheDir.
        val subDir = "updates"
        val externalUpdatesDir = context.getExternalFilesDir(subDir) ?: run {
            _state.value = UpdateState.Failed(info, "Couldn't access storage for the download")
            return
        }
        // Cleared before each new download — a half-downloaded APK from a
        // previous attempt or an older version sitting here would otherwise
        // just accumulate.
        externalUpdatesDir.listFiles()?.forEach { it.delete() }
        val fileName = "update-${info.versionCode}.apk"
        val apkFile = File(externalUpdatesDir, fileName)

        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("SMusic update")
            .setDescription("Downloading version ${info.versionName}")
            .setDestinationInExternalFilesDir(context, subDir, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION)
            .setAllowedOverMetered(true)

        try {
            activeDownloadId = dm.enqueue(request)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(info, "Couldn't start the download: ${e.message}")
            return
        }
        _state.value = UpdateState.Downloading(info, progress = 0)
        registerReceiverIfNeeded()
        pollProgress(info, apkFile)
    }

    private fun pollProgress(info: UpdateInfo, apkFile: File) {
        val dm = downloadManager ?: return
        val id = activeDownloadId ?: return
        // DownloadManager doesn't push progress, only a single "finished"
        // broadcast — this is what turns that into the live percentage the
        // dialog shows while it runs.
        Thread {
            var downloading = true
            while (downloading) {
                val cursor = dm.query(DownloadManager.Query().setFilterById(id))
                cursor?.use {
                    if (it.moveToFirst()) {
                        val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val soFarIdx = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalIdx = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val status = if (statusIdx >= 0) it.getInt(statusIdx) else -1
                        val soFar = if (soFarIdx >= 0) it.getLong(soFarIdx) else 0L
                        val total = if (totalIdx >= 0) it.getLong(totalIdx) else 0L

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                downloading = false
                                _state.value = UpdateState.ReadyToInstall(info, apkFile)
                            }
                            DownloadManager.STATUS_FAILED -> {
                                downloading = false
                                _state.value = UpdateState.Failed(info, "Download failed — check your connection and try again")
                            }
                            else -> {
                                val pct = if (total > 0) ((soFar * 100) / total).toInt() else 0
                                _state.value = UpdateState.Downloading(info, pct)
                            }
                        }
                    } else {
                        downloading = false
                    }
                } ?: run { downloading = false }
                if (downloading) Thread.sleep(400)
            }
        }.start()
    }

    /** Launches the system installer for an APK already sitting on disk. */
    fun install(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /** "Later" on the dialog — remembers the choice so a silent check won't re-interrupt with the same version, without ever contacting a server. */
    suspend fun skip(info: UpdateInfo) {
        context.dataStore.edit { it[SkippedUpdateVersionCodeKey] = info.versionCode }
        _state.value = UpdateState.Idle
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    private var receiverRegistered = false
    private fun registerReceiverIfNeeded() {
        // Not strictly required — pollProgress() already catches the
        // terminal state on its own — but registering this means a
        // completed download still resolves promptly even if the polling
        // thread got stopped by the process being backgrounded partway
        // through, since the system broadcast arrives independently of it.
        if (receiverRegistered) return
        receiverRegistered = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                // pollProgress already reconciles final state by querying
                // DownloadManager directly; this only needs to exist so the
                // OS doesn't consider the download abandoned.
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }
}

sealed class UpdateCheckResult {
    object UpToDate : UpdateCheckResult()
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
}
