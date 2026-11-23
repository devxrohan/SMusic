package com.devxrohan.smusic

import android.content.Context
import com.devxrohan.smusic.update.VersionCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class HomeReleaseVersion(val name: String?)
private val homeReleaseChecks = com.devxrohan.smusic.utils.RequestCoalescer<String, HomeReleaseVersion>(ttlMillis = 5 * 60_000L)

suspend fun checkForUpdates(context: Context): String? = withContext(Dispatchers.IO) {
    homeReleaseChecks.load(BuildConfig.GITHUB_REPO) {
        runCatching {
            HomeReleaseVersion(com.devxrohan.smusic.update.GitHubReleaseFetcher(context.applicationContext)
                .fetchLatestRelease()?.versionName)
        }
    }.getOrNull()?.name
}

fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean =
    VersionCompare.isNewer(remoteVersion, currentVersion)
