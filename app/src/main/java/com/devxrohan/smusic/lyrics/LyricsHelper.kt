package com.devxrohan.smusic.lyrics

import android.content.Context
import android.util.LruCache
import com.devxrohan.smusic.constants.PreferredLyricsProvider
import com.devxrohan.smusic.constants.PreferredLyricsProviderKey
import com.devxrohan.smusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.devxrohan.smusic.extensions.toEnum
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import androidx.annotation.Keep

@Keep
class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private val allProviders: List<LyricsProvider> = listOf(
        LrcLibLyricsProvider,
        KuGouLyricsProvider,
        SimpMusicLyricsProvider,
        UnisonLyricsProvider,
        YouLyLyricsProvider,
        MegalobizLyricsProvider,
        PaxsenixLyricsProvider,
        PortatoLyricsProvider,
        BetterLyricsProvider,
        YouTubeSubtitleLyricsProvider,
        YouTubeLyricsProvider,
    )

    val preferred = context.dataStore.data
        .map { it[PreferredLyricsProviderKey].toEnum(PreferredLyricsProvider.LRCLIB) }
        .distinctUntilChanged()

    private suspend fun providersInPreferredOrder(): List<LyricsProvider> {
        val preferredProvider = when (context.dataStore.data.first()[PreferredLyricsProviderKey].toEnum(PreferredLyricsProvider.LRCLIB)) {
            PreferredLyricsProvider.LRCLIB -> LrcLibLyricsProvider
            PreferredLyricsProvider.KUGOU -> KuGouLyricsProvider
            PreferredLyricsProvider.SIMP_MUSIC -> SimpMusicLyricsProvider
            PreferredLyricsProvider.YOUTUBE_SUBTITLES -> YouTubeSubtitleLyricsProvider
            PreferredLyricsProvider.PAXSENIX -> PaxsenixLyricsProvider
            PreferredLyricsProvider.UNISON -> UnisonLyricsProvider
            PreferredLyricsProvider.BETTER_LYRICS -> BetterLyricsProvider
            PreferredLyricsProvider.PORTATO -> PortatoLyricsProvider
            PreferredLyricsProvider.YOULY -> YouLyLyricsProvider
            PreferredLyricsProvider.MEGALOBIZ -> MegalobizLyricsProvider
            PreferredLyricsProvider.YOUTUBE_MUSIC -> YouTubeLyricsProvider
        }
        return listOf(preferredProvider) + allProviders.filterNot { it === preferredProvider }
    }
    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)

    suspend fun getLyrics(mediaMetadata: MediaMetadata): String {
        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return cached.lyrics
        }
        providersInPreferredOrder().forEach { provider ->
            if (provider.isEnabled(context)) {
                provider
                    .getLyrics(
                        mediaMetadata.id,
                        mediaMetadata.title,
                        mediaMetadata.artists.joinToString { it.name },
                        mediaMetadata.duration,
                    ).onSuccess { lyrics ->
                        return lyrics
                    }.onFailure {
                        reportException(it)
                    }
            }
        }
        return LYRICS_NOT_FOUND
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ) {
        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }
        val allResult = mutableListOf<LyricsResult>()
        providersInPreferredOrder().forEach { provider ->
            if (provider.isEnabled(context)) {
                provider.getAllLyrics(mediaId, songTitle, songArtists, duration) { lyrics ->
                    val result = LyricsResult(provider.name, lyrics)
                    allResult += result
                    callback(result)
                }
            }
        }
        cache.put(cacheKey, allResult)
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
