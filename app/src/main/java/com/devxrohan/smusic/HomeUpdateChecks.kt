package com.devxrohan.smusic

import android.content.Context
import com.devxrohan.smusic.update.VersionCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun checkForUpdates(context: Context): String? = withContext(Dispatchers.IO) {
    // Same resilient GitHub routes (+ DNS-over-HTTPS fallback) as the update popup.
    runCatching {
        com.devxrohan.smusic.update.GitHubReleaseFetcher(context.applicationContext)
            .fetchLatestRelease()
            ?.versionName
    }.getOrNull()
}

fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean =
    VersionCompare.isNewer(remoteVersion, currentVersion)
