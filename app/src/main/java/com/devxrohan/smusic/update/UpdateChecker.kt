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
        val currentCode = com.devxrohan.smusic.BuildConfig.VERSION_CODE
        val betaEnabled = releaseFetcher.betaUpdatesEnabled()

        val release = if (betaEnabled) {
            withTimeoutOrNull(GITHUB_BUDGET_MS) {
                releaseFetcher.fetchLatestEligibleRelease(current, currentCode, includePrerelease = true)
            }
        } else {
            withTimeoutOrNull(GITHUB_BUDGET_MS) { releaseFetcher.fetchLatestRelease() }
        }

        if (release != null) {
            val latest = release.versionName
            val releaseCode = release.versionCode ?: semanticVersionCode(latest) ?: (currentCode + 1)
            // Android's versionCode is authoritative for installability. A beta
            // and its final stable release may share the same semantic base name.
            if (releaseCode <= currentCode) return null
            val apk = releaseFetcher.selectApkAsset(release)
            return UpdateInfo(
                versionCode = releaseCode,
                versionName = latest,
                apkUrl = apk?.browser_download_url.orEmpty(),
                apkSize = apk?.size ?: 0L,
                changelog = release.body.orEmpty(),
                releaseUrl = release.html_url.orEmpty(),
                mandatory = false,
                prerelease = release.prerelease,
            )
        }

        // Every GitHub route failed: ask the Worker as the very last resort.
        val worker = withTimeoutOrNull(WORKER_BUDGET_MS) { runCatching { checkManifest() }.getOrNull() }
            ?.update
            ?.takeIf { it.versionName.isNotBlank() || it.versionCode > 0 }
        if (worker != null) {
            val workerIsPrerelease = worker.prerelease ||
                Regex("""-(alpha|beta|rc|pre|preview)([.-]|$)""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(worker.versionName)
            if (workerIsPrerelease && !betaEnabled) return null
            val workerCode = worker.versionCode.takeIf { it > 0 }
                ?: semanticVersionCode(worker.versionName)
                ?: 0
            if (workerCode <= currentCode) return null
            return worker.copy(
                versionCode = workerCode,
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
        val preRelease: String?,
    )

    private fun parseVersion(raw: String): ParsedVersion {
        val clean = raw.trim().removePrefix("v").substringBefore('+')
        val dash = clean.indexOf('-')
        val base = if (dash >= 0) clean.substring(0, dash) else clean
        return ParsedVersion(
            parts = base.split('.').map { it.toIntOrNull() ?: 0 },
            preRelease = if (dash >= 0) clean.substring(dash + 1).takeIf { it.isNotBlank() } else null,
        )
    }

    fun compare(latest: String, current: String): Int {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }

        // A stable release is newer than any pre-release with the same base version.
        if (l.preRelease == null && c.preRelease != null) return 1
        if (l.preRelease != null && c.preRelease == null) return -1
        if (l.preRelease == null && c.preRelease == null) return 0

        val left = l.preRelease!!.split('.', '-')
        val right = c.preRelease!!.split('.', '-')
        for (i in 0 until maxOf(left.size, right.size)) {
            if (i >= left.size) return -1
            if (i >= right.size) return 1
            val a = left[i]
            val b = right[i]
            val an = a.toIntOrNull()
            val bn = b.toIntOrNull()
            val result = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1
                bn != null -> 1
                else -> a.compareTo(b, ignoreCase = true)
            }
            if (result != 0) return result
        }
        return 0
    }

    fun isNewer(latest: String, current: String): Boolean = compare(latest, current) > 0
}
