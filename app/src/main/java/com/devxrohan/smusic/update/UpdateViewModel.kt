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

    fun checkSilently() {
        viewModelScope.launch { updateManager.checkSilently() }
    }

    suspend fun checkNow(): UpdateCheckResult = updateManager.checkNow()

    fun startDownload(info: UpdateInfo) = updateManager.startDownload(info)

    fun install(apkFile: java.io.File) = updateManager.install(apkFile)

    fun skip(info: UpdateInfo) {
        viewModelScope.launch { updateManager.skip(info) }
    }

    fun dismiss() = updateManager.dismiss()
}
