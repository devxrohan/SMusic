package com.devxrohan.smusic.update

import android.content.Context
import android.net.Uri
import com.devxrohan.smusic.update.UpdateHttp.executeCancellable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GitHubReleaseAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0L,
    val state: String = "",
)

@Serializable
data class GitHubRelease(
    val tag_name: String = "",
    val name: String? = null,
    val body: String? = null,
    val published_at: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val html_url: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList(),
) {
    val versionCode: Int?
        get() {
            val raw = tag_name.removePrefix("v").trim()
            val build = raw.substringAfter('+', "").substringBefore('-')
            return build.toIntOrNull()
        }

    val versionName: String
        get() = tag_name.removePrefix("v").ifBlank { name.orEmpty() }
}

@Serializable
data class GitHubUserProfile(
    val login: String,
    val name: String? = null,
    val bio: String? = null,
    val avatar_url: String? = null,
    val html_url: String? = null,
)

/**
 * GitHub transport for releases, release history and the developer profile.
 *
 * Every lookup is a prioritised race between several independent GitHub
 * endpoints (fastest / richest first, others join after a short stagger or as
 * soon as the previous one fails), and every request additionally falls back
 * from system DNS to DNS-over-HTTPS (see [UpdateHttp]). Successful results for
 * release history and the developer profile are cached on disk so the
 * About / Previous versions / Developer screens still show something real
 * when the network blocks GitHub.
 *
 * Endpoints used (all GitHub, none needs a token):
 *  - api.github.com/repos/{repo}/releases[/latest]   full data, but rate limited per IP (60/h,
 *                                                      easily exhausted on a shared Wi-Fi)
 *  - github.com/{repo}/releases.atom                   tag, title, changelog; NOT rate limited
 *  - github.com/{repo}/releases/latest (redirect)     tag only; NOT rate limited
 *  - github.com/{repo}/releases/expanded_assets/{tag} APK asset links for a tag; NOT rate limited
 */
@Singleton
class GitHubReleaseFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val repo = com.devxrohan.smusic.BuildConfig.GITHUB_REPO.trim().removeSuffix(".git").ifBlank { "devxrohan/SMusic" }
    private val owner = repo.substringBefore("/")

    val latestReleasePage: String
        get() = "https://github.com/$repo/releases/latest"

    val releasesPage: String
        get() = "https://github.com/$repo/releases"

    val repoUrl: String
        get() = if (repo.isNotBlank()) "https://github.com/$repo" else ""

    private companion object {
        const val API_ACCEPT = "application/vnd.github+json"
        const val HTML_ACCEPT = "text/html,application/xhtml+xml"
        const val ATOM_ACCEPT = "application/atom+xml,application/xml,text/xml"
        const val CACHE_RELEASES = "releases.json"
        const val CACHE_LATEST = "latest_release.json"
        const val CACHE_PROFILE = "developer_profile.json"
    }

    // ---- Disk cache ------------------------------------------------------

    private fun cacheFile(name: String): File =
        File(File(context.filesDir, "update_cache").apply { mkdirs() }, name)

    private fun writeCache(name: String, text: String) {
        runCatching { cacheFile(name).writeText(text) }
    }

    private fun readCache(name: String): String? =
        runCatching { cacheFile(name).takeIf { it.exists() }?.readText() }.getOrNull()

    // ---- Parsing helpers -------------------------------------------------

    private fun xmlUnescape(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&#x27;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")

    /** Atom `<content>` is escaped HTML; turn it into readable plain text. */
    private fun htmlToText(raw: String): String {
        var html = xmlUnescape(raw)
        // In HTML, newlines in the source are plain whitespace; only <br> and block tags break lines.
        // (Keeping them produced a blank line after every <br> and between every bullet.)
        html = html.replace(Regex("\\s*\\n\\s*"), " ")
        html = html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)<li[^>]*>"), "\n\u2022 ")
            .replace(Regex("(?i)</li>"), "")
            .replace(Regex("(?i)</?(p|div|ul|ol|h[1-6]|pre|blockquote|table|tr|details|summary)[^>]*>"), "\n")
            .replace(Regex("<[^>]+>"), "")
        return xmlUnescape(html)
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun decodeTag(raw: String): String =
        runCatching { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)

    private fun parseAtom(xml: String): List<GitHubRelease> {
        val entryRegex = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
        val tagRegex = Regex("""/releases/tag/([^"'<\s]+)""")
        val titleRegex = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
        val updatedRegex = Regex("<updated>(.*?)</updated>")
        val contentRegex = Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL)
        return entryRegex.findAll(xml).mapNotNull { match ->
            val entry = match.groupValues[1]
            val rawTag = tagRegex.find(entry)?.groupValues?.get(1) ?: return@mapNotNull null
            GitHubRelease(
                tag_name = decodeTag(rawTag),
                name = titleRegex.find(entry)?.groupValues?.get(1)?.let(::xmlUnescape)?.trim()?.takeIf { it.isNotBlank() },
                body = contentRegex.find(entry)?.groupValues?.get(1)?.let(::htmlToText),
                published_at = updatedRegex.find(entry)?.groupValues?.get(1),
                html_url = "https://github.com/$repo/releases/tag/$rawTag",
            )
        }.toList()
    }

    /** APK links for a tag from GitHub's HTML asset list (not subject to the API rate limit). */
    private suspend fun assetsForTag(rawTag: String): List<GitHubReleaseAsset> =
        UpdateHttp.fetch("https://github.com/$repo/releases/expanded_assets/$rawTag", HTML_ACCEPT) { html, _ ->
            val regex = Regex("""href="(/[^"]*/releases/download/[^"]+?\.apk)"""", RegexOption.IGNORE_CASE)
            regex.findAll(html)
                .map { it.groupValues[1] }
                .distinct()
                .map { path ->
                    GitHubReleaseAsset(
                        name = decodeTag(path.substringAfterLast('/')),
                        browser_download_url = "https://github.com$path",
                    )
                }
                .toList()
                .takeIf { it.isNotEmpty() }
        }.orEmpty()

    private fun validRelease(release: GitHubRelease?): GitHubRelease? =
        release?.takeUnless { it.draft || it.prerelease || it.tag_name.isBlank() }

    // ---- Latest release --------------------------------------------------

    /** Source 1: official API. Full data (assets, size, changelog). */
    private suspend fun latestFromApi(): GitHubRelease? =
        UpdateHttp.fetch("https://api.github.com/repos/$repo/releases/latest", API_ACCEPT) { body, _ ->
            validRelease(runCatching { json.decodeFromString(GitHubRelease.serializer(), body) }.getOrNull())
        }

    /** Source 2: releases.atom. Tag + changelog, no rate limit; APK links come from expanded_assets. */
    private suspend fun latestFromAtom(): GitHubRelease? {
        val first = UpdateHttp.fetch("https://github.com/$repo/releases.atom", ATOM_ACCEPT) { xml, _ ->
            parseAtom(xml).firstOrNull()
        } ?: return null
        val rawTag = first.html_url?.substringAfter("/releases/tag/") ?: return first
        return first.copy(assets = assetsForTag(rawTag))
    }

    /** Source 3: follow /releases/latest redirect to learn the tag, then read the asset list. */
    private suspend fun latestFromRedirect(): GitHubRelease? {
        val rawTag = UpdateHttp.fetch(latestReleasePage, HTML_ACCEPT) { _, finalUrl ->
            finalUrl.substringAfter("/releases/tag/", "").substringBefore('?').substringBefore('#').takeIf { it.isNotBlank() }
        } ?: return null
        return GitHubRelease(
            tag_name = decodeTag(rawTag),
            name = decodeTag(rawTag),
            html_url = "https://github.com/$repo/releases/tag/$rawTag",
            assets = assetsForTag(rawTag),
        )
    }

    suspend fun fetchLatestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        if (repo.isBlank() || !repo.contains('/')) return@withContext null
        val release = UpdateHttp.race(
            UpdateHttp.step(0L) { latestFromApi() },
            UpdateHttp.step(1_200L) { latestFromAtom() },
            UpdateHttp.step(2_500L) { latestFromRedirect() },
        )
        if (release != null) {
            writeCache(CACHE_LATEST, json.encodeToString(GitHubRelease.serializer(), release))
        }
        release
    }

    /**
     * Where to send the browser when the in-app check completely failed.
     * Uses GitHub's permanent ".../releases/latest/download/<file>" link when the
     * APK name is stable (no version number in it), so the browser downloads the
     * newest APK directly; otherwise the Releases "latest" page.
     */
    fun fallbackDownloadUrl(): String {
        val cached = readCache(CACHE_LATEST)
            ?.let { runCatching { json.decodeFromString(GitHubRelease.serializer(), it) }.getOrNull() }
        val name = cached?.let { selectApkAsset(it)?.name }
        if (name != null && !Regex("""\d+\.\d+""").containsMatchIn(name)) {
            return "https://github.com/$repo/releases/latest/download/${Uri.encode(name)}"
        }
        return latestReleasePage
    }

    // ---- Release history -------------------------------------------------

    private suspend fun releasesFromApi(): List<GitHubRelease>? =
        UpdateHttp.fetch("https://api.github.com/repos/$repo/releases", API_ACCEPT) { body, _ ->
            runCatching {
                json.decodeFromString(ListSerializer(GitHubRelease.serializer()), body)
                    .filter { !it.draft && !it.prerelease }
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }

    private suspend fun releasesFromAtom(): List<GitHubRelease>? =
        UpdateHttp.fetch("https://github.com/$repo/releases.atom", ATOM_ACCEPT) { xml, _ ->
            parseAtom(xml).takeIf { it.isNotEmpty() }
        }

    /** Never throws. Live data if any GitHub route works, otherwise the last good copy from disk. */
    suspend fun fetchReleases(): List<GitHubRelease> = withContext(Dispatchers.IO) {
        if (repo.isBlank() || !repo.contains('/')) return@withContext emptyList()
        val live = UpdateHttp.race(
            UpdateHttp.step(0L) { releasesFromApi() },
            UpdateHttp.step(1_500L) { releasesFromAtom() },
        )
        if (live != null) {
            writeCache(CACHE_RELEASES, json.encodeToString(ListSerializer(GitHubRelease.serializer()), live))
            return@withContext live
        }
        readCache(CACHE_RELEASES)
            ?.let { runCatching { json.decodeFromString(ListSerializer(GitHubRelease.serializer()), it) }.getOrNull() }
            .orEmpty()
    }

    fun selectApkAsset(release: GitHubRelease): GitHubReleaseAsset? {
        val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) && it.browser_download_url.isNotBlank() }
        if (apks.isEmpty()) return null

        // SMusic is distributed as one universal APK. Prefer a normal SMusic
        // release asset and deliberately ignore ABI-specific split artifacts.
        return apks.firstOrNull { asset ->
            val name = asset.name.lowercase()
            name.contains("smusic") &&
                !name.contains("arm64-v8a") &&
                !name.contains("armeabi-v7a") &&
                !name.contains("x86_64") &&
                !name.contains("x86-") &&
                !name.contains("universal")
        } ?: apks.firstOrNull { asset ->
            val name = asset.name.lowercase()
            !name.contains("arm64-v8a") &&
                !name.contains("armeabi-v7a") &&
                !name.contains("x86_64") &&
                !name.contains("x86-")
        } ?: apks.firstOrNull()
    }

    // ---- APK download ----------------------------------------------------

    private suspend fun streamTo(client: OkHttpClient, url: String, target: File, onProgress: (Int) -> Unit) {
        client.newCall(UpdateHttp.request(url, "application/vnd.android.package-archive,application/octet-stream"))
            .executeCancellable().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty download body")
                val total = body.contentLength().takeIf { it > 0L }
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var readTotal = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            readTotal += read
                            total?.let { onProgress(((readTotal * 100L) / it).toInt().coerceIn(0, 100)) }
                        }
                        // A truncated APK can never install; fail so we retry / fall back to the browser.
                        if (total != null && readTotal != total) throw IOException("Incomplete download")
                    }
                }
            }
    }

    /**
     * Streams a release asset into [target]. Tries system DNS first and then
     * DNS-over-HTTPS, two attempts each, with no overall time limit (only a
     * per-read stall timeout) so big APKs on slow Wi-Fi are not cut off.
     */
    suspend fun downloadApk(url: String, target: File, onProgress: (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            var lastError: Throwable? = null
            for (client in listOf(UpdateHttp.systemDownloadClient, UpdateHttp.dohDownloadClient)) {
                repeat(2) {
                    try {
                        streamTo(client, url, target, onProgress)
                        return@withContext
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                        runCatching { target.delete() }
                        delay(400L)
                    }
                }
            }
            throw IOException("Unable to reach GitHub", lastError)
        }

    // ---- Announcement manifest (Worker) ----------------------------------

    /**
     * Worker manifest (announcements, and the last-resort update/developer
     * fallback). Same DNS-over-HTTPS safety net as everything else.
     */
    suspend fun fetchAnnouncementManifest(url: String): AppManifest? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        UpdateHttp.fetch(url, "application/json") { body, _ ->
            runCatching { json.decodeFromString(AppManifest.serializer(), body) }.getOrNull()
        }
    }

    // ---- Developer profile -----------------------------------------------

    private suspend fun profileFromApi(): GitHubUserProfile? =
        UpdateHttp.fetch("https://api.github.com/users/$owner", API_ACCEPT) { body, _ ->
            runCatching { json.decodeFromString(GitHubUserProfile.serializer(), body) }.getOrNull()
        }

    /** Public profile page (no rate limit): bio is in a data-bio-text attribute, name in itemprop="name". */
    private suspend fun profileFromHtml(): GitHubUserProfile? =
        UpdateHttp.fetch("https://github.com/$owner", HTML_ACCEPT) { html, _ ->
            val bio = Regex("""data-bio-text="([^"]*)"""").find(html)?.groupValues?.get(1)?.let(::xmlUnescape)?.trim()
            val name = Regex("""itemprop="name"[^>]*>\s*([^<]+?)\s*<""").find(html)?.groupValues?.get(1)?.let(::xmlUnescape)?.trim()
            if (bio.isNullOrBlank() && name.isNullOrBlank()) null
            else GitHubUserProfile(login = owner, name = name?.takeIf { it.isNotBlank() }, bio = bio?.takeIf { it.isNotBlank() })
        }

    /** Optional `developer` block of the Worker manifest (name/bio), as the third route. */
    private suspend fun profileFromWorker(): GitHubUserProfile? {
        val dev = fetchAnnouncementManifest(com.devxrohan.smusic.BuildConfig.UPDATE_MANIFEST_URL)?.developer ?: return null
        if (dev.name.isBlank() && dev.bio.isBlank()) return null
        return GitHubUserProfile(login = owner, name = dev.name.takeIf { it.isNotBlank() }, bio = dev.bio.takeIf { it.isNotBlank() })
    }

    /** Never throws. Live profile if any route works, else last good copy, else null (UI uses its built-in text). */
    suspend fun fetchUserProfile(): GitHubUserProfile? = withContext(Dispatchers.IO) {
        if (owner.isBlank()) return@withContext null
        val live = UpdateHttp.race(
            UpdateHttp.step(0L) { profileFromApi() },
            UpdateHttp.step(1_500L) { profileFromHtml() },
            UpdateHttp.step(3_000L) { profileFromWorker() },
        )
        if (live != null) {
            writeCache(CACHE_PROFILE, json.encodeToString(GitHubUserProfile.serializer(), live))
            return@withContext live
        }
        readCache(CACHE_PROFILE)
            ?.let { runCatching { json.decodeFromString(GitHubUserProfile.serializer(), it) }.getOrNull() }
    }
}
