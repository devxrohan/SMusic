package com.devxrohan.smusic.lyrics

import android.content.Context
import com.devxrohan.smusic.constants.PreferredLyricsProvider
import com.devxrohan.smusic.constants.PaxsenixLyricsApiKey
import com.devxrohan.smusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.getSuspend
import com.devxrohan.smusic.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import androidx.annotation.Keep

/**
 * Coordinates lyrics providers for the current song.
 *
 * All enabled providers are started together. Answers are considered in fixed
 * priority order; word-timed results win while line-timed and plain text remain
 * fallbacks. This prevents a quick fuzzy match from pre-empting a better source.
 */

@Keep
@Singleton
class LyricsHelper
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val selectedProviderByMediaId = ConcurrentHashMap<String, String>()

    /** Provider that supplied the currently cached automatic lyrics for a track. */
    fun providerNameFor(mediaId: String): String? = selectedProviderByMediaId[mediaId]

    fun rememberProviderName(mediaId: String, providerName: String) {
        if (mediaId.isNotBlank() && providerName.isNotBlank()) selectedProviderByMediaId[mediaId] = providerName
    }

    // Fixed recording-first priority. Normal playback no longer has a user-selected
    // "try first" source; explicit per-provider lookups remain supported.
    private val allProviders: List<LyricsProvider> = listOf(
        LrcRedLyricsProvider,
        BiniLyricsProvider,
        BetterLyricsProvider,
        PortatoLyricsProvider,
        PaxsenixLyricsProvider,
        PaxsenixSpotifyLyricsProvider,
        PaxsenixMusixmatchLyricsProvider,
        YouLyLyricsProvider,
        UnisonLyricsProvider,
        SimpMusicLyricsProvider,
        YouTubeSubtitleLyricsProvider,
        YouTubeLyricsProvider,
        KuGouLyricsProvider,
        LrcLibLyricsProvider,
        MusixmatchLyricsProvider,
    )

    private fun providerFor(preference: PreferredLyricsProvider): LyricsProvider = when (preference) {
        PreferredLyricsProvider.LRC_RED -> LrcRedLyricsProvider
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

    // Fixed provider priority; requests run concurrently, results are selected in this order.
    private suspend fun providersInPreferredOrder(): List<LyricsProvider> = allProviders

    private suspend fun configureAuthenticatedProviders() {
        val key = context.dataStore.getSuspend(PaxsenixLyricsApiKey).orEmpty()
        PaxsenixSpotifyLyricsProvider.setApiKey(key)
        PaxsenixMusixmatchLyricsProvider.setApiKey(key)
    }

    suspend fun getLyrics(
        mediaMetadata: MediaMetadata,
        forceProviderOnly: PreferredLyricsProvider? = null,
    ): String {
        configureAuthenticatedProviders()
        val providers = (if (forceProviderOnly != null) {
            listOf(providerFor(forceProviderOnly))
        } else {
            providersInPreferredOrder()
        }).filter { it.isEnabled(context) && it !== GeniusLyricsProvider }

        selectedProviderByMediaId.remove(mediaMetadata.id)
        if (providers.isEmpty()) return LYRICS_NOT_FOUND

        // Providers include a few legacy blocking HTTP clients. Run them in a
        // lookup-owned supervisor so cancelling the lookup never has to wait for
        // a non-cooperative socket call to unwind on the caller's coroutine.
        val lookupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val searchTitle = mediaMetadata.title.forLyricsSearch()
        val searchArtist = mediaMetadata.artists.joinToString { it.name }.artistForLyricsSearch()

        // Resolve the recording once before the race. lrc.red and BiniLyrics
        // then consume the same ISRC/document rather than independently fuzzy-matching
        // a title that may exist in several edits.
        if (forceProviderOnly == null && BiniLyricsProvider in providers) {
            withTimeoutOrNull(1_500L) {
                BiniLyricsProvider.identify(
                    mediaMetadata.id, searchTitle, searchArtist, mediaMetadata.duration,
                )
            }
        }

        val jobs: List<Pair<LyricsProvider, Deferred<String?>>> = providers.map { provider ->
            provider to lookupScope.async {
                provider.getLyrics(
                    mediaMetadata.id,
                    searchTitle,
                    searchArtist,
                    mediaMetadata.duration,
                ).onFailure { reportException(it) }.getOrNull()
            }
        }

        return try {
            var lineSyncedFallback: String? = null
            var plainFallback: String? = null
            var selectedWordTimed: String? = null

            // All providers are already running concurrently. Keep the fixed
            // priority, but never let one stalled provider hold the lyrics page
            // in its loading state indefinitely. A short per-provider wait lets
            // already-completed lower-priority requests be considered as well.
            withTimeoutOrNull(7_000L) {
                for ((provider, job) in jobs) {
                    val lyrics = withTimeoutOrNull(900L) { job.await() }
                        ?.takeIf { it.isNotBlank() && it != LYRICS_NOT_FOUND }
                        ?: continue
                    val parsed = runCatching { SMusicLyricsUtils.parseAnyLyrics(lyrics) }
                        .getOrDefault(emptyList())
                    val hasWordTiming = parsed.any { !it.words.isNullOrEmpty() }
                    val hasLineTiming = SMusicLyricsUtils.isRecognizedTimedLyrics(lyrics) &&
                        parsed.any { it.time >= 0L }

                    if (hasWordTiming) {
                        selectedWordTimed = lyrics
                        selectedProviderByMediaId[mediaMetadata.id] = provider.name
                        break
                    }
                    if (hasLineTiming) {
                        if (lineSyncedFallback == null) {
                            lineSyncedFallback = lyrics
                            selectedProviderByMediaId[mediaMetadata.id] = provider.name
                        }
                    } else if (plainFallback == null) {
                        plainFallback = lyrics
                        if (lineSyncedFallback == null) selectedProviderByMediaId[mediaMetadata.id] = provider.name
                    }
                }
            }

            selectedWordTimed ?: lineSyncedFallback ?: plainFallback
                ?: if (forceProviderOnly == null && GeniusLyricsProvider.isEnabled(context)) {
                    withTimeoutOrNull(1_500L) {
                        GeniusLyricsProvider.getLyrics(
                            mediaMetadata.id, searchTitle, searchArtist, mediaMetadata.duration,
                        ).getOrNull()?.takeIf { it.isNotBlank() }?.also {
                            selectedProviderByMediaId[mediaMetadata.id] = GeniusLyricsProvider.name
                        }
                    } ?: LYRICS_NOT_FOUND
                } else LYRICS_NOT_FOUND
        } finally {
            lookupScope.cancel()
        }
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ): Unit = coroutineScope {
        configureAuthenticatedProviders()
        (providersInPreferredOrder() + GeniusLyricsProvider)
            .distinctBy { it.name }
            .filter { it.isEnabled(context) }
            .map { provider ->
                async(Dispatchers.IO) {
                    // Each provider is isolated: a stalled endpoint must not keep
                    // the provider chooser's progress indicator alive forever.
                    withTimeoutOrNull(7_000L) {
                        provider.getAllLyrics(
                            mediaId,
                            songTitle.forLyricsSearch(),
                            songArtists.artistForLyricsSearch(),
                            duration,
                        ) { lyrics ->
                            if (lyrics.isNotBlank() && lyrics != LYRICS_NOT_FOUND) {
                                callback(LyricsResult(provider.name, lyrics))
                            }
                        }
                    }
                }
            }
            .awaitAll()
        Unit
    }

}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
