package com.devxrohan.smusic.utils

import android.content.Context
import com.devxrohan.smusic.R
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.discordrpc.rpc.DiscordRpcClient
import com.devxrohan.smusic.discordrpc.rpc.RpcImage

/**
 * Discord Rich Presence: song title/artist, cover art (via the RPC image proxy),
 * progress, and two buttons. Covers + buttons only render when the activity carries a Discord
 * application id, so set `discord.application.id=<your app id>` in local.properties (free: Discord
 * Developer Portal > New Application, name it SMusic, copy the Application ID).
 */
class DiscordRPC(
    val context: Context,
    token: String,
) : DiscordRpcClient(token) {

    suspend fun updateSong(
        song: Song,
        currentPlaybackTimeMillis: Long,
        playbackSpeed: Float = 1.0f,
        useDetails: Boolean = false,
    ) = runCatching {
        val currentTime = System.currentTimeMillis()
        val songUrl = "https://music.youtube.com/watch?v=${song.song.id}"

        val adjustedPlaybackTime = (currentPlaybackTimeMillis / playbackSpeed).toLong()
        val calculatedStartTime = currentTime - adjustedPlaybackTime

        val songTitleWithRate = if (playbackSpeed != 1.0f) {
            "${song.song.title} [${String.format("%.2fx", playbackSpeed)}]"
        } else {
            song.song.title
        }

        val remainingDuration = song.song.duration * 1000L - currentPlaybackTimeMillis
        val adjustedRemainingDuration = (remainingDuration / playbackSpeed).toLong()

        setActivity(
            name = context.getString(R.string.app_name).removeSuffix(" Debug"),
            details = songTitleWithRate,
            state = song.artists.joinToString { it.name },
            detailsUrl = songUrl,
            largeImage = song.song.thumbnailUrl?.let { RpcImage.ExternalImage(it) },
            smallImage = song.artists.firstOrNull()?.thumbnailUrl?.let { RpcImage.ExternalImage(it) },
            largeText = song.album?.title,
            smallText = song.artists.firstOrNull()?.name,
            buttons = listOf(
                "Listen on YouTube Music" to songUrl,
                "Visit SMusic" to WEBSITE_URL,
            ),
            type = Type.LISTENING,
            statusDisplayType = if (useDetails) StatusDisplayType.DETAILS else StatusDisplayType.STATE,
            since = currentTime,
            startTime = calculatedStartTime,
            endTime = currentTime + adjustedRemainingDuration,
            applicationId = APPLICATION_ID,
        )
    }

    companion object {
        // Your own Discord application ID (free, Discord Developer Portal). Set `discord.application.id`
        // in local.properties. Covers + buttons need it: Discord only resolves external images and
        // shows buttons for activities that carry an application id.
        private val APPLICATION_ID: String? = com.devxrohan.smusic.BuildConfig.DISCORD_APPLICATION_ID.ifBlank { null }
        private const val WEBSITE_URL = "https://github.com/devxrohan/SMusic"
    }
}
