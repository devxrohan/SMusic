package com.devxrohan.smusic.lyrics

import android.content.Context
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.constants.EnableYouTubeSubtitleLyricsKey
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    override val name = "YouTube Subtitle"

    override fun isEnabled(context: Context) = context.dataStore[EnableYouTubeSubtitleLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = YouTube.transcript(id)
}
