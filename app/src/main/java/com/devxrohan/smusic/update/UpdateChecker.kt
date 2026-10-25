package com.devxrohan.smusic.update

import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Update check order (fastest GitHub route wins, Worker is strictly last):
 *
 *  1. GitHub: API, releases.atom and the /releases/latest redirect are raced
 *     (see [GitHubReleaseFetcher.fetchLatestRelease]); each one also retries
 *     through DNS-over-HTTPS if the network's own DNS is broken.
 *  2. Worker manifest, only if every GitHub route failed.
 *  3. Otherwise this throws and [UpdateManager] opens the direct APK /
 *     release page in the browser.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val releaseFetcher: GitHubReleaseFetcher,
) {
    suspend fun checkForUpdate(): UpdateInfo? {
        val current = com.devxrohan.smusic.BuildConfig.VERSION_NAME

        val release = withTimeoutOrNull(GITHUB_BUDGET_MS) { releaseFetcher.fetchLatestRelease() }
        if (release != null) {
            val latest = release.versionName
            if (!VersionCompare.isNewer(latest, current)) return null
            val apk = releaseFetcher.selectApkAsset(release)
            return UpdateInfo(
                versionCode = release.versionCode
                    ?: semanticVersionCode(latest)
                    ?: (com.devxrohan.smusic.BuildConfig.VERSION_CODE + 1),
                versionName = latest,
                apkUrl = apk?.browser_download_url.orEmpty(),
                apkSize = apk?.size ?: 0L,
                changelog = release.body.orEmpty(),
                releaseUrl = release.html_url.orEmpty(),
                mandatory = false,
            )
        }

        // Every GitHub route failed: ask the Worker as the very last resort.
        val worker = withTimeoutOrNull(WORKER_BUDGET_MS) { runCatching { checkManifest() }.getOrNull() }
            ?.update
            ?.takeIf { it.versionName.isNotBlank() || it.versionCode > 0 }
        if (worker != null) {
            val newer = if (worker.versionName.isNotBlank()) {
                VersionCompare.isNewer(worker.versionName, current)
            } else {
                worker.versionCode > com.devxrohan.smusic.BuildConfig.VERSION_CODE
            }
            if (!newer) return null
            return worker.copy(
                versionName = worker.versionName.ifBlank { worker.versionCode.toString() },
                releaseUrl = worker.releaseUrl.ifBlank { releaseFetcher.latestReleasePage },
            )
        }

        throw IllegalStateException("Unable to reach GitHub Releases or the update server")
    }

    /** Worker manifest: announcements (primary use) and the last-resort update info. */
    suspend fun checkManifest(): AppManifest? =
        releaseFetcher.fetchAnnouncementManifest(com.devxrohan.smusic.BuildConfig.UPDATE_MANIFEST_URL)

    private companion object {
        /** Whole GitHub stage (3 racing routes, each with system-DNS + DoH attempts). */
        const val GITHUB_BUDGET_MS = 18_000L
        const val WORKER_BUDGET_MS = 12_000L
    }

    private fun semanticVersionCode(version: String): Int? {
        val parts = version.trim().removePrefix("v").substringBefore('-').substringBefore('+')
            .split('.')
            .map { it.toIntOrNull() ?: return null }
        if (parts.isEmpty()) return null
        return parts.getOrElse(0) { 0 } * 1_000_000 +
            parts.getOrElse(1) { 0 } * 1_000 +
            parts.getOrElse(2) { 0 }
    }


}

/** Version-name comparison shared by the checker, the notification code and APK cleanup. */
object VersionCompare {
    private data class ParsedVersion(
        val parts: List<Int>,
        val preRelease: Boolean,
    )

    private fun parseVersion(raw: String): ParsedVersion {
        val clean = raw.trim().removePrefix("v").substringBefore('+')
        val dash = clean.indexOf('-')
        val base = if (dash >= 0) clean.substring(0, dash) else clean
        return ParsedVersion(
            parts = base.split('.').map { it.toIntOrNull() ?: 0 },
            preRelease = dash >= 0,
        )
    }

    fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        // A stable release supersedes a pre-release with the same numbers.
        return c.preRelease && !l.preRelease
    }
}
