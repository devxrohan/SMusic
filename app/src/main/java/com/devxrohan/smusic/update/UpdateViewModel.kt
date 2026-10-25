package com.devxrohan.smusic.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    val updateManager: UpdateManager
) : ViewModel() {

    val state = updateManager.state

    fun checkSilently(force: Boolean = false) {
        viewModelScope.launch { updateManager.checkSilently(force) }
    }

    fun restorePending() {
        viewModelScope.launch { updateManager.restorePending() }
    }

    fun clearDownloadedApks(): Long = updateManager.clearDownloadedApks()

    suspend fun checkNow(): UpdateCheckResult = updateManager.checkNow()

    suspend fun releaseHistory(): List<GitHubRelease> = updateManager.releaseHistory()

    fun startDownload(info: UpdateInfo) = updateManager.startDownload(info)

    fun install(apkFile: java.io.File) = updateManager.install(apkFile)

    fun cancelDownload() = updateManager.cancelDownload()

    fun openReleaseInBrowser(info: UpdateInfo) = updateManager.openReleaseInBrowser(info)

    /** "Later": popup comes back after a few hours; the update stays pending in the bell/notification. */
    fun later() {
        viewModelScope.launch { updateManager.later() }
    }

    fun dismiss() = updateManager.dismiss()
}
