package com.devxrohan.smusic.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val published_at: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val html_url: String? = null,
)

@Serializable
data class GitHubUserProfile(
    val login: String,
    val name: String? = null,
    val bio: String? = null,
    val avatar_url: String? = null,
    val html_url: String? = null,
)

/**
 * Fetches the changelog text for past releases straight from GitHub's
 * own API — nothing is downloaded here, just release metadata and the
 * changelog body text for each version.
 */
@Singleton
class GitHubReleaseFetcher @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }
    private val repo = com.devxrohan.smusic.BuildConfig.GITHUB_REPO
    private val owner = repo.substringBefore("/")

    val repoUrl: String get() = if (repo.isNotBlank()) "https://github.com/$repo" else ""

    suspend fun fetchReleases(): List<GitHubRelease> = withContext(Dispatchers.IO) {
        if (repo.isBlank()) return@withContext emptyList()
        runCatching {
            val connection = URL("https://api.github.com/repos/$repo/releases").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.connect()

            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val bodyText = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(GitHubRelease.serializer()),
                bodyText
            ).filter { !it.draft }
        }.getOrDefault(emptyList())
    }

    /** The repo owner's public GitHub profile — used for the About/Developer bio, live from GitHub. */
    suspend fun fetchUserProfile(): GitHubUserProfile? = withContext(Dispatchers.IO) {
        if (owner.isBlank()) return@withContext null
        runCatching {
            val connection = URL("https://api.github.com/users/$owner").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.connect()

            if (connection.responseCode !in 200..299) return@withContext null
            val bodyText = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString(GitHubUserProfile.serializer(), bodyText)
        }.getOrNull()
    }
}
