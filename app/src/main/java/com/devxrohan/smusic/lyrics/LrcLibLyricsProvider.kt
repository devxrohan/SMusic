package com.devxrohan.smusic.lyrics

import android.content.Context
import com.devxrohan.smusic.lrclib.LrcLib
import com.devxrohan.smusic.constants.EnableLrcLibKey
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get

object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLrcLibKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = LrcLib.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        LrcLib.getAllLyrics(title, artist, duration, null, callback)
    }
}
