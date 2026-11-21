package com.devxrohan.smusic.update

import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Checks GitHub first; the worker manifest is used only if all GitHub transports fail. */
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
            val releaseCode = UpdateEligibility.newerCode(latest, release.versionCode, current, currentCode)
                ?: return null
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
            val workerCode = UpdateEligibility.newerCode(worker.versionName, worker.versionCode, current, currentCode)
                ?: return null
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

}
