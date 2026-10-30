@file:Suppress("DEPRECATION")
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.innertube.models.WatchEndpoint
import com.devxrohan.smusic.jossredconnect.JossRedClient
import com.devxrohan.smusic.MainActivity
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AudioNormalizationKey
import com.devxrohan.smusic.constants.AudioFxAndDjEnabledKey
import com.devxrohan.smusic.constants.BypassAllAudioEffectsKey
import com.devxrohan.smusic.constants.AudioBoostEnabledKey
import com.devxrohan.smusic.constants.AudioBoostPercentKey
import com.devxrohan.smusic.constants.EchoEnabledKey
import com.devxrohan.smusic.constants.EchoDelayMsKey
import com.devxrohan.smusic.constants.EchoFeedbackKey
import com.devxrohan.smusic.constants.EchoWetMixKey
import com.devxrohan.smusic.constants.EchoPingPongKey
import com.devxrohan.smusic.constants.DjFilterSweepKey
import com.devxrohan.smusic.constants.DjFlangerEnabledKey
import com.devxrohan.smusic.constants.DjFlangerRateKey
import com.devxrohan.smusic.constants.DjFlangerDepthKey
import com.devxrohan.smusic.constants.DjSaturationKey
import com.devxrohan.smusic.constants.DjTurntableLinkedKey
import com.devxrohan.smusic.constants.DjTempoSpeedKey
import com.devxrohan.smusic.constants.DjPitchKey
import com.devxrohan.smusic.constants.AudioQuality
import com.devxrohan.smusic.constants.AudioQualityKey
import com.devxrohan.smusic.constants.AutoLoadMoreKey
import com.devxrohan.smusic.constants.AutoMixBetaKey
import com.devxrohan.smusic.constants.AutoSkipNextOnErrorKey
import com.devxrohan.smusic.constants.DisableLoadMoreWhenRepeatAllKey
import com.devxrohan.smusic.constants.EqualizerEnabledKey
import com.devxrohan.smusic.constants.CrossfadeDurationKey
import com.devxrohan.smusic.constants.AudioStudioBassKey
import com.devxrohan.smusic.constants.AudioStudioVirtualizerKey
import com.devxrohan.smusic.constants.AudioStudioReverbKey
import com.devxrohan.smusic.constants.AudioStudioEnabledKey
import com.devxrohan.smusic.constants.HideExplicitKey
import com.devxrohan.smusic.constants.HistoryDuration
import com.devxrohan.smusic.constants.MediaSessionConstants.CommandToggleLike
import com.devxrohan.smusic.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.devxrohan.smusic.constants.MediaSessionConstants.CommandToggleShuffle
import com.devxrohan.smusic.constants.PauseListenHistoryKey
import com.devxrohan.smusic.constants.PermanentShuffleKey
import com.devxrohan.smusic.constants.PersistentQueueKey
import com.devxrohan.smusic.constants.PlayerVolumeKey
import com.devxrohan.smusic.constants.RepeatModeKey
import com.devxrohan.smusic.constants.SimilarContent
import com.devxrohan.smusic.constants.SkipSilenceKey
import com.devxrohan.smusic.constants.StopMusicOnTaskClearKey
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.db.entities.Event
import com.devxrohan.smusic.db.entities.FormatEntity
import com.devxrohan.smusic.db.entities.LyricsEntity
import com.devxrohan.smusic.db.entities.RelatedSongMap
import com.devxrohan.smusic.di.DownloadCache
import com.devxrohan.smusic.di.PlayerCache
import com.devxrohan.smusic.extensions.SilentHandler
import com.devxrohan.smusic.extensions.collect
import com.devxrohan.smusic.extensions.collectLatest
import com.devxrohan.smusic.extensions.currentMetadata
import com.devxrohan.smusic.extensions.findNextMediaItemById
import com.devxrohan.smusic.extensions.mediaItems
import com.devxrohan.smusic.extensions.metadata
import com.devxrohan.smusic.extensions.toMediaItem
import com.devxrohan.smusic.extensions.toQueue
import com.devxrohan.smusic.lyrics.LyricsHelper
import com.devxrohan.smusic.models.PersistPlayerState
import com.devxrohan.smusic.models.PersistQueue
import com.devxrohan.smusic.models.toMediaMetadata
import com.devxrohan.smusic.playback.queues.EmptyQueue
import com.devxrohan.smusic.playback.queues.Queue
import com.devxrohan.smusic.playback.queues.YouTubeQueue
import com.devxrohan.smusic.playback.queues.filterExplicit
import com.devxrohan.smusic.utils.CoilBitmapLoader
import com.devxrohan.smusic.utils.NetworkConnectivityObserver
import com.devxrohan.smusic.utils.YTPlayerUtils
import com.devxrohan.smusic.utils.isYouTubeAccountWriteBackAllowed
import com.devxrohan.smusic.utils.isLowDataModeActive
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.enumPreference
import com.devxrohan.smusic.utils.get
import com.devxrohan.smusic.utils.getSuspend
import com.devxrohan.smusic.utils.initializeCache
import com.devxrohan.smusic.utils.reportException
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

data class EqualizerUiState(
    val isAvailable: Boolean = false,
    val enabled: Boolean = false,
    val bandLevels: List<Short> = emptyList(),
    val centerFrequencies: List<Int> = emptyList(),
    val minBandLevel: Short = -1500,
    val maxBandLevel: Short = 1500,
)

data class AudioStudioUiState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bassStrength: Int = 0,
    val spaceStrength: Int = 0,
    val reverbPreset: Int = 0,
    val bassSupported: Boolean = false,
    val spaceSupported: Boolean = false,
    val reverbSupported: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var pulseEngine: com.devxrohan.smusic.pulse.PulseEngine

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: Any? = null
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        handleAudioFocusChange(focusChange)
    }
    private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    private var wasPlayingBeforeAudioFocusLoss = false
    private var hasAudioFocus = false

    private var scope = CoroutineScope(Dispatchers.Main) + Job()
    private val binder = MusicBinder()
    private var hasBoundClients = false
    private var hasCalledStartForeground = false

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(false)

    private val audioQuality by enumPreference(
        this,
        AudioQualityKey,
        com.devxrohan.smusic.constants.AudioQuality.AUTO
    )

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null

    val currentMediaMetadata = MutableStateFlow<com.devxrohan.smusic.models.MediaMetadata?>(null)
    private val currentSong =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat =
        currentMediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    val playerVolume = MutableStateFlow(
        kotlinx.coroutines.runBlocking {
            dataStore.getSuspend(PlayerVolumeKey, 1f)
        }.coerceIn(0f, 1f)
    )

    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession

    private var isAudioEffectSessionOpened = false
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var loudnessEnhancerSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var queuePageLoadInProgress = false
    private var equalizer: Equalizer? = null
    private var equalizerSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    private var studioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    val equalizerState = MutableStateFlow(EqualizerUiState())
    val audioStudioState = MutableStateFlow(AudioStudioUiState())
    val spatialAudioProcessor = SpatialAudioProcessor()
    val eightDAudioProcessor = EightDAudioProcessor()
    val djAudioProcessor = DjAudioProcessor()
    val echoEnabled = MutableStateFlow(false)
    val echoDelayMs = MutableStateFlow(280)
    val echoFeedback = MutableStateFlow(0.40f)
    val echoWetMix = MutableStateFlow(0.0f)
    val echoPingPong = MutableStateFlow(false)
    val djFilterSweep = MutableStateFlow(0.0f)
    val djFlangerEnabled = MutableStateFlow(false)
    val djFlangerRate = MutableStateFlow(0.5f)
    val djFlangerDepth = MutableStateFlow(0.0f)
    val djSaturation = MutableStateFlow(0.0f)
    val djTempoSpeed = MutableStateFlow(1.0f)
    val djPitch = MutableStateFlow(1.0f)
    val djTurntableLinked = MutableStateFlow(false)
    val audioFxAndDjEnabled = MutableStateFlow(false)
    val bypassAllAudioEffects = MutableStateFlow(false)
    val audioBoostEnabled = MutableStateFlow(false)
    val audioBoostPercent = MutableStateFlow(100)
    val visualizerManager by lazy { AudioVisualizerManager(scope) }
    private var audioBoostEnhancer: LoudnessEnhancer? = null
    private var audioBoostSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var transitionFadeJob: Job? = null
    private var lastPlaybackSpeed = 1.0f
    private var discordRpc: com.devxrohan.smusic.utils.DiscordRPC? = null
    private var discordUpdateJob: kotlinx.coroutines.Job? = null
    private var mediaController: MediaController? = null

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())

    private var consecutivePlaybackErr = 0

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        instance = this
        // Ensure the in-memory preference-cache (dataStore.get() below
        // reads from it, not from disk directly) is genuinely populated
        // before anything else in this service runs. MainActivity/App
        // also warm this cache, but asynchronously — if this service is
        // started independently (media button, Bluetooth auto-play,
        // notification action) before that finishes, every dataStore.get()
        // call in this file would silently fall back to its default value
        // instead of whatever the user actually saved. This is a single
        // blocking read of the whole preferences map, done once here, not
        // per-key, so it's cheap — not something to repeat on every event.
        runBlocking { dataStore.initializeCache() }
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            )
                .apply {
                    setSmallIcon(R.mipmap.ic_launcher_monochrome)
                },
        )
        player =
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(createMediaSourceFactory())
                .setRenderersFactory(createRenderersFactory())
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    false,
                ).setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .build()
                .apply {
                    addListener(this@MusicService)
                    sleepTimer = SleepTimer(scope, this)
                    addListener(sleepTimer)
                    addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))
                }
        loadAudioFxPreferences()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupAudioFocus()

        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleLibrary = ::toggleLibrary
        }
        mediaSession =
            MediaLibrarySession
                .Builder(this, player, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        101,
                        Intent(this, MainActivity::class.java).setAction("com.devxrohan.smusic.OPEN_PLAYER"),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(this, scope))
                .build()
        player.repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_OFF)

        // Keep a connected controller so that notification works
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                runCatching { mediaController = controllerFuture.get() }
                    .onFailure(::reportException)
            },
            MoreExecutors.directExecutor()
        )

        connectivityManager = getSystemService()!!
        connectivityObserver = NetworkConnectivityObserver(this)

        // Observar conectividad de red
        scope.launch {
            connectivityObserver.networkStatus.collect { isConnected ->
                isNetworkConnected.value = isConnected
                if (isConnected && waitingForNetworkConnection.value) {
                    // Reintentar reproducción cuando vuelve la conexión
                    waitingForNetworkConnection.value = false
                    if (player.currentMediaItem != null && player.playWhenReady) {
                        player.prepare()
                        player.play()
                    }
                }
            }
        }

        playerVolume.collectLatest(scope) {
            player.volume = it
        }

        // Sequential crossfade-style transitions: fade the outgoing track near its end,
        // then ramp the incoming track back to the user's chosen volume. This is a
        // device/source-safe fade, not overlapping dual-deck DJ mixing.
        transitionFadeJob?.cancel()
        transitionFadeJob = scope.launch {
            while (isActive) {
                try {
                    val durationSeconds = dataStore.get(CrossfadeDurationKey, 0).coerceIn(0, 8)
                    val durationMs = durationSeconds * 1000L
                    val duration = player.duration
                    val position = player.currentPosition
                    if (durationMs > 0L && player.isPlaying && duration > 0L && duration != C.TIME_UNSET) {
                        val remaining = duration - position
                        if (remaining in 1..durationMs) {
                            val factor = (remaining.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            player.volume = playerVolume.value * factor
                        }
                    }
                } catch (_: Exception) { }
                delay(100)
            }
        }

        playerVolume.debounce(1000).collect(scope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        dataStore.data.map { it[AutoMixBetaKey] ?: true }
            .distinctUntilChanged()
            .collect(scope) { enabled ->
                if (!enabled) automixItems.value = emptyList()
            }

        currentSong.debounce(1000).collect(scope) { song ->
            updateNotification()
        }

        // Discord Rich Presence (ported from SMusic utils/DiscordRPC.kt + MusicService wiring).
        // One authoritative client per service instance: connects when a token is stored and the
        // toggle is on, fully disconnects (closeRPC) when logged out or disabled so no stale
        // Gateway session lingers. Guest/no-token users are completely unaffected — this never
        // touches Pulse or playback source selection.
        dataStore.data
            .map { it[com.devxrohan.smusic.constants.DiscordTokenKey] to (it[com.devxrohan.smusic.constants.EnableDiscordRPCKey] ?: true) }
            .debounce(300)
            .distinctUntilChanged()
            .collect(scope) { (token, enabled) ->
                if (discordRpc?.isRpcRunning() == true) {
                    discordRpc?.closeRPC()
                }
                discordRpc = null
                if (!token.isNullOrEmpty() && enabled) {
                    discordRpc = com.devxrohan.smusic.utils.DiscordRPC(this, token)
                    if (player.playbackState == Player.STATE_READY && player.playWhenReady) {
                        currentSong.value?.let {
                            discordRpc?.updateSong(
                                it,
                                player.currentPosition,
                                player.playbackParameters.speed,
                                dataStore.get(com.devxrohan.smusic.constants.DiscordUseDetailsKey, false),
                            )
                        }
                    }
                }
            }

        dataStore.data
            .map { it[com.devxrohan.smusic.constants.DiscordUseDetailsKey] ?: false }
            .debounce(1000)
            .distinctUntilChanged()
            .collect(scope) { useDetails ->
                if (player.playbackState == Player.STATE_READY && player.playWhenReady) {
                    currentSong.value?.let { song ->
                        discordUpdateJob?.cancel()
                        discordUpdateJob = scope.launch {
                            delay(1000)
                            discordRpc?.updateSong(song, player.currentPosition, player.playbackParameters.speed, useDetails)
                        }
                    }
                }
            }

        currentSong.debounce(500).collect(scope) { song ->
            if (song != null && player.playbackState == Player.STATE_READY && player.playWhenReady) {
                discordRpc?.updateSong(
                    song,
                    player.currentPosition,
                    player.playbackParameters.speed,
                    dataStore.get(com.devxrohan.smusic.constants.DiscordUseDetailsKey, false),
                )
            } else if (song == null) {
                discordRpc?.stopActivity()
            }
        }

        // Pre-fetch lyrics for the current song as soon as it starts playing, not only once the Lyrics screen is
        // opened. This used to be gated behind ShowLyricsKey, a *persisted* preference for the mini-player's
        // Queue/Lyrics tab choice — so prefetching only happened to run when that tab happened to already be
        // selected, not in response to a song actually starting. In practice that meant lyrics were usually first
        // requested only when the user opened the lyrics screen and sat there waiting. Fetching unconditionally
        // here means they're normally already in the database (see the `database.lyrics(id).first() == null`
        // check below, which skips the fetch once they're saved) by the time the screen opens.
        currentMediaMetadata
            .distinctUntilChangedBy { it?.id }
            .collectLatest(scope) { mediaMetadata ->
                if (mediaMetadata != null && database.lyrics(mediaMetadata.id).first() == null) {
                    val lyrics = lyricsHelper.getLyrics(mediaMetadata)
                    database.query {
                        upsert(
                            LyricsEntity(
                                id = mediaMetadata.id,
                                lyrics = lyrics,
                            ),
                        )
                    }
                }
            }

        dataStore.data
            .map { it[SkipSilenceKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                player.skipSilenceEnabled = it
            }

        combine(
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) { (format, normalizeAudio) ->
            setupLoudnessEnhancer()
        }

        if (dataStore.get(PersistentQueueKey, true)) {
            scope.launch(SilentHandler) {
            runCatching {
                filesDir.resolve(PERSISTENT_QUEUE_FILE).inputStream().use { fis ->
                    ObjectInputStream(fis).use { oos ->
                        oos.readObject() as PersistQueue
                    }
                }
            }.onSuccess { queue ->
                // Convertir de vuelta al tipo de cola apropiado
                val restoredQueue = queue.toQueue()
                playQueue(
                    queue = restoredQueue,
                    playWhenReady = false,
                )
            }
            runCatching {
                filesDir.resolve(PERSISTENT_AUTOMIX_FILE).inputStream().use { fis ->
                    ObjectInputStream(fis).use { oos ->
                        oos.readObject() as PersistQueue
                    }
                }
            }.onSuccess { queue ->
                automixItems.value = queue.items.map { it.toMediaItem() }
            }

            // Restaurar estado del reproductor
            runCatching {
                filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).inputStream().use { fis ->
                    ObjectInputStream(fis).use { oos ->
                        oos.readObject() as PersistPlayerState
                    }
                }
            }.onSuccess { playerState ->
                // Restaurar configuración del reproductor después de cargar la cola
                scope.launch {
                    delay(1000) // Esperar a que la cola se cargue
                    player.repeatMode = playerState.repeatMode
                    player.shuffleModeEnabled = playerState.shuffleModeEnabled
                    player.volume = playerState.volume

                    // Restaurar posición si sigue siendo válida
                    if (playerState.currentMediaItemIndex < player.mediaItemCount) {
                        player.seekTo(playerState.currentMediaItemIndex, playerState.currentPosition)
                    }
                }
            }
        }
        }

        // Guardar cola periódicamente para prevenir pérdida por crash o force kill

        scope.launch {
            while (isActive) {
                delay(30.seconds)
                if (dataStore.get(PersistentQueueKey, true)) {
                    saveQueueToDisk()
                }
            }
        }

        // Guardar cola más frecuentemente cuando está reproduciendo
        scope.launch {
            while (isActive) {
                delay(10.seconds)
                if (dataStore.get(PersistentQueueKey, true) && player.isPlaying) {
                    saveQueueToDisk()
                }
            }
        }
    }

    private fun setupAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupAudioFocusRequestOreo()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupAudioFocusRequestOreo() {
        audioFocusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true

                if (wasPlayingBeforeAudioFocusLoss) {
                    player.play()
                    wasPlayingBeforeAudioFocusLoss = false
                }

                player.volume = playerVolume.value

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = false

                if (player.isPlaying) {
                    player.pause()
                }

                abandonAudioFocus()

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying

                if (player.isPlaying) {
                    player.pause()
                }

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying

                if (player.isPlaying) {
                    player.volume = (playerVolume.value * 0.2f)
                }

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
                hasAudioFocus = true

                if (wasPlayingBeforeAudioFocusLoss) {
                    player.play()
                    wasPlayingBeforeAudioFocusLoss = false
                }

                player.volume = playerVolume.value

                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                hasAudioFocus = true

                player.volume = playerVolume.value

                lastAudioFocusState = focusChange
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (audioFocusRequest as? android.media.AudioFocusRequest)?.let { request ->
                audioManager.requestAudioFocus(request)
            } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        } else {
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (hasAudioFocus) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (audioFocusRequest as? android.media.AudioFocusRequest)?.let { request ->
                    audioManager.abandonAudioFocusRequest(request)
                }
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
            hasAudioFocus = false
        }
    }

    fun hasAudioFocusForPlayback(): Boolean {
        return hasAudioFocus
    }

    private fun waitOnNetworkError() {
        waitingForNetworkConnection.value = true
    }

    private fun skipOnError() {
        /**
         * Auto skip to the next media item on error.
         *
         * To prevent a "runaway diesel engine" scenario, force the user to take action after
         * too many errors come up too quickly. Pause to show player "stopped" state
         */
        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()
            player.play()
            return
        }

        player.pause()
        consecutivePlaybackErr = 0
    }

    private fun stopOnError() {
        player.pause()
    }

    private fun updateNotification() {
        mediaSession.setCustomLayout(
            listOf(
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            if (currentSong.value?.song?.liked ==
                                true
                            ) {
                                R.string.action_remove_like
                            } else {
                                R.string.action_like
                            },
                        ),
                    )
                    .setIconResId(if (currentSong.value?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border)
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> throw IllegalStateException()
                            },
                        ),
                    ).setIconResId(
                        when (player.repeatMode) {
                            REPEAT_MODE_OFF -> R.drawable.repeat
                            REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> throw IllegalStateException()
                        },
                    ).setSessionCommand(CommandToggleRepeatMode)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setSessionCommand(CommandToggleShuffle)
                    .build(),
            ),
        )
    }

    private suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null
    ) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId)
                .getOrNull()?.videoDetails)?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else if (song.song.duration == -1) update(song.song.copy(duration = duration))
        }
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint =
                YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                    ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) {
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main) + Job()
        currentQueue = queue
        queueTitle = null
        val isPermanentShuffle = dataStore.get(PermanentShuffleKey, false)
        player.shuffleModeEnabled = isPermanentShuffle
        if (queue.preloadItem != null) {
            player.setMediaItem(queue.preloadItem!!.toMediaItem())
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        scope.launch(SilentHandler) {
            val initialStatus =
                withContext(Dispatchers.IO) {
                    queue.getInitialStatus().filterExplicit(dataStore.get(HideExplicitKey, false))
                }
            if (queue.preloadItem != null && player.playbackState == STATE_IDLE) return@launch
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            if (initialStatus.items.isEmpty()) return@launch
            if (queue.preloadItem != null) {
                player.addMediaItems(
                    0,
                    initialStatus.items.subList(0, initialStatus.mediaItemIndex)
                )
                player.addMediaItems(
                    initialStatus.items.subList(
                        initialStatus.mediaItemIndex + 1,
                        initialStatus.items.size
                    )
                )
            } else {
                player.setMediaItems(
                    initialStatus.items,
                    if (initialStatus.mediaItemIndex >
                        0
                    ) {
                        initialStatus.mediaItemIndex
                    } else {
                        0
                    },
                    initialStatus.position,
                )
                player.prepare()
                player.playWhenReady = playWhenReady
            }
            if (isPermanentShuffle) {
                refreshShuffleOrderIfNeeded()
            }
        }
    }

    fun startRadioSeamlessly() {
        val currentMediaMetadata = player.currentMetadata ?: return

        // Guardar canción actual
        val currentSong = player.currentMediaItem

        // Remover otras canciones de la cola
        if (player.currentMediaItemIndex > 0) {
            player.removeMediaItems(0, player.currentMediaItemIndex)
        }
        if (player.currentMediaItemIndex < player.mediaItemCount - 1) {
            player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
        }

        scope.launch(SilentHandler) {
            val radioQueue = YouTubeQueue(
                endpoint = WatchEndpoint(videoId = currentMediaMetadata.id)
            )
            val initialStatus = radioQueue.getInitialStatus()

            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }

            // Agregar canciones de radio después de la canción actual
            player.addMediaItems(initialStatus.items.drop(1))
            currentQueue = radioQueue
        }
    }

    fun getAutomixAlbum(albumId: String) {
        scope.launch(SilentHandler) {
            YouTube
                .album(albumId)
                .onSuccess {
                    getAutomix(it.album.playlistId)
                }
        }
    }

    fun getAutomix(playlistId: String) {
        if (dataStore.get(AutoMixBetaKey, true) && dataStore[SimilarContent] == true &&
            !(dataStore.get(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL)) {
            scope.launch(SilentHandler) {
                YouTube
                    .next(WatchEndpoint(playlistId = playlistId))
                    .onSuccess {
                        YouTube
                            .next(WatchEndpoint(playlistId = it.endpoint.playlistId))
                            .onSuccess {
                                val mixItems = it.items.map { song -> song.toMediaItem() }
                                automixItems.value = mixItems
                                // Pulse AutoMix: reorder by taste/session continuity; falls back to the original list.
                                scope.launch(Dispatchers.IO) {
                                    val ranked = pulseEngine.rankForRadio(
                                        items = mixItems,
                                        idOf = { mi -> mi.mediaId },
                                        artistIdsOf = { mi -> mi.metadata?.artists?.mapNotNull { a -> a.id }.orEmpty() },
                                        seedId = player.currentMediaItem?.mediaId,
                                        seedArtistIds = player.currentMediaItem?.metadata?.artists
                                            ?.mapNotNull { a -> a.id }.orEmpty(),
                                    )
                                    if (ranked !== mixItems) automixItems.value = ranked
                                }
                            }
                    }
            }
        }
    }

    fun addToQueueAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        addToQueue(listOf(item))
    }

    fun playNextAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        playNext(listOf(item))
    }

    fun clearAutomix() {
        automixItems.value = emptyList()
    }

    fun playNext(items: List<MediaItem>) {
        // Si la cola está vacía o el reproductor está inactivo, reproducir inmediatamente
        if (player.mediaItemCount == 0 || player.playbackState == STATE_IDLE) {
            player.setMediaItems(items)
            player.prepare()
            player.play()
            return
        }

        val insertIndex = player.currentMediaItemIndex + 1
        val shuffleEnabled = player.shuffleModeEnabled

        // Insertar items inmediatamente después del item actual en el espacio de ventana/índice
        player.addMediaItems(insertIndex, items)
        player.prepare()

        if (shuffleEnabled) {
            // Reconstruir orden aleatorio para que los items recién insertados se reproduzcan a continuación
            val timeline = player.currentTimeline
            if (!timeline.isEmpty) {
                val size = timeline.windowCount
                val currentIndex = player.currentMediaItemIndex

                // Los índices recién insertados son un rango contiguo [insertIndex, insertIndex + items.size)
                val newIndices = (insertIndex until (insertIndex + items.size)).toSet()

                // Recopilar el orden de recorrido aleatorio existente excluyendo el índice actual
                val orderAfter = mutableListOf<Int>()
                var idx = currentIndex
                while (true) {
                    idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, /*shuffleModeEnabled=*/true)
                    if (idx == C.INDEX_UNSET) break
                    if (idx != currentIndex) orderAfter.add(idx)
                }

                val prevList = mutableListOf<Int>()
                var pIdx = currentIndex
                while (true) {
                    pIdx = timeline.getPreviousWindowIndex(pIdx, Player.REPEAT_MODE_OFF, /*shuffleModeEnabled=*/true)
                    if (pIdx == C.INDEX_UNSET) break
                    if (pIdx != currentIndex) prevList.add(pIdx)
                }
                prevList.reverse() // preservar el orden hacia adelante original

                val existingOrder = (prevList + orderAfter).filter { it != currentIndex && it !in newIndices }

                // Construir nuevo orden aleatorio: actual -> recién insertados (en orden de inserción) -> resto
                val nextBlock = (insertIndex until (insertIndex + items.size)).toList()
                val finalOrder = IntArray(size)
                var pos = 0
                finalOrder[pos++] = currentIndex
                nextBlock.forEach { if (it in 0 until size) finalOrder[pos++] = it }
                existingOrder.forEach { if (pos < size) finalOrder[pos++] = it }

                // Llenar cualquier índice faltante (seguridad) para asegurar una permutación completa
                if (pos < size) {
                    for (i in 0 until size) {
                        if (!finalOrder.contains(i)) {
                            finalOrder[pos++] = i
                            if (pos == size) break
                        }
                    }
                }

                player.setShuffleOrder(DefaultShuffleOrder(finalOrder, System.currentTimeMillis()))
            }
        }
    }

    fun addToQueue(items: List<MediaItem>) {
        player.addMediaItems(items)
        player.prepare()
    }

    private fun toggleLibrary() {
        database.query {
            currentSong.value?.let {
                update(it.song.toggleLibrary())
            }
        }
    }

    fun toggleLike() {
        database.query {
            currentSong.value?.let {
                update(it.song.toggleLike())
                // Pulse: an explicit like is a strong positive (an unlike just clears it).
                val likedNow = !it.song.liked
                scope.launch(Dispatchers.IO) { pulseEngine.onFavorite(it, likedNow) }
            }
        }
    }

    private fun setupLoudnessEnhancer() {
        val audioSessionId = player.audioSessionId

        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            Log.w(TAG, "setupLoudnessEnhancer: invalid audioSessionId ($audioSessionId), cannot create effect yet")
            return
        }

        // The effect belongs to an audio session. Recreate it whenever ExoPlayer moves to a
        // different session; otherwise normalization silently stops working after transitions.
        if (loudnessEnhancer == null || loudnessEnhancerSessionId != audioSessionId) {
            releaseLoudnessEnhancer()
            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                loudnessEnhancerSessionId = audioSessionId
                Log.d(TAG, "LoudnessEnhancer created for sessionId=$audioSessionId")
            } catch (e: Exception) {
                reportException(e)
                loudnessEnhancer = null
                loudnessEnhancerSessionId = C.AUDIO_SESSION_ID_UNSET
                return
            }
        }

        scope.launch {
            try {
                val currentMediaId = withContext(Dispatchers.Main) {
                    player.currentMediaItem?.mediaId
                }

                val normalizeAudio = withContext(Dispatchers.IO) {
                    dataStore.data.map { it[AudioNormalizationKey] ?: true }.first()
                }

                if (normalizeAudio && currentMediaId != null) {
                    val format = withContext(Dispatchers.IO) {
                        database.format(currentMediaId).first()
                    }

                    val loudnessDb = format?.loudnessDb

                    withContext(Dispatchers.Main) {
                        if (loudnessDb != null) {
                            // YouTube loudnessDb is the measured track loudness. Normalize toward
                            // a practical -14 LUFS-like target instead of boosting every negative value.
                            val targetGain = ((-14.0 - loudnessDb) * 100.0).toInt()
                            val clampedGain = targetGain.coerceIn(MIN_GAIN_MB, MAX_GAIN_MB)
                            try {
                                loudnessEnhancer?.setTargetGain(clampedGain)
                                loudnessEnhancer?.enabled = true
                                Log.d(TAG, "LoudnessEnhancer gain applied: $clampedGain mB")
                            } catch (e: Exception) {
                                reportException(e)
                                releaseLoudnessEnhancer()
                            }
                        } else {
                            loudnessEnhancer?.enabled = false
                            Log.w(TAG, "setupLoudnessEnhancer: loudnessDb is null, enhancer disabled")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loudnessEnhancer?.enabled = false
                        Log.d(TAG, "setupLoudnessEnhancer: normalization disabled or mediaId unavailable")
                    }
                }
            } catch (e: Exception) {
                reportException(e)
                releaseLoudnessEnhancer()
            }
        }
    }

    private fun releaseLoudnessEnhancer() {
        try {
            loudnessEnhancer?.release()
            Log.d(TAG, "LoudnessEnhancer released")
        } catch (e: Exception) {
            reportException(e)
            Log.e(TAG, "Error releasing LoudnessEnhancer: ${e.message}")
        } finally {
            loudnessEnhancer = null
            loudnessEnhancerSessionId = C.AUDIO_SESSION_ID_UNSET
        }
    }

    private fun loadAudioFxPreferences() {
        scope.launch {
            runCatching {
                val p = dataStore.data.first()
                bypassAllAudioEffects.value = p[BypassAllAudioEffectsKey] ?: false
                audioFxAndDjEnabled.value = p[AudioFxAndDjEnabledKey] ?: false
                audioBoostEnabled.value = p[AudioBoostEnabledKey] ?: false
                audioBoostPercent.value = (p[AudioBoostPercentKey] ?: 100).coerceIn(100, 200)
                echoEnabled.value = p[EchoEnabledKey] ?: false
                echoDelayMs.value = (p[EchoDelayMsKey] ?: 280).coerceIn(20, 1000)
                echoFeedback.value = (p[EchoFeedbackKey] ?: .40f).coerceIn(0f, .85f)
                echoWetMix.value = (p[EchoWetMixKey] ?: 0f).coerceIn(0f, 1f)
                echoPingPong.value = p[EchoPingPongKey] ?: false
                djFilterSweep.value = (p[DjFilterSweepKey] ?: 0f).coerceIn(-1f, 1f)
                djFlangerEnabled.value = p[DjFlangerEnabledKey] ?: false
                djFlangerRate.value = (p[DjFlangerRateKey] ?: .5f).coerceIn(.1f, 4f)
                djFlangerDepth.value = (p[DjFlangerDepthKey] ?: 0f).coerceIn(0f, 1f)
                djSaturation.value = (p[DjSaturationKey] ?: 0f).coerceIn(0f, 1f)
                djTempoSpeed.value = (p[DjTempoSpeedKey] ?: 1f).coerceIn(.5f, 2f)
                djPitch.value = (p[DjPitchKey] ?: 1f).coerceIn(.5f, 2f)
                djTurntableLinked.value = p[DjTurntableLinkedKey] ?: false
                syncDjProcessor()
                applyAudioFxEnableState()
            }
        }
    }

    private fun syncDjProcessor() {
        djAudioProcessor.echoEnabled = echoEnabled.value
        djAudioProcessor.echoDelayMs = echoDelayMs.value
        djAudioProcessor.echoFeedback = echoFeedback.value
        djAudioProcessor.echoWetMix = echoWetMix.value
        djAudioProcessor.echoPingPong = echoPingPong.value
        djAudioProcessor.filterSweep = djFilterSweep.value
        djAudioProcessor.flangerEnabled = djFlangerEnabled.value
        djAudioProcessor.flangerRate = djFlangerRate.value
        djAudioProcessor.flangerDepth = djFlangerDepth.value
        djAudioProcessor.saturation = djSaturation.value
        djAudioProcessor.enabled = audioFxAndDjEnabled.value && !bypassAllAudioEffects.value
        djAudioProcessor.bypass = bypassAllAudioEffects.value
        spatialAudioProcessor.bypass = bypassAllAudioEffects.value
        eightDAudioProcessor.bypass = bypassAllAudioEffects.value
    }

    private fun applyAudioFxEnableState() {
        syncDjProcessor()
        if (bypassAllAudioEffects.value) {
            djAudioProcessor.resetFx()
            spatialAudioProcessor.enabled = false
            eightDAudioProcessor.enabled = false
            runCatching { equalizer?.enabled = false }
            equalizerState.value = equalizerState.value.copy(enabled = false)
            runCatching { player.playbackParameters = androidx.media3.common.PlaybackParameters.DEFAULT }
            djTempoSpeed.value = 1f
            djPitch.value = 1f
            djTurntableLinked.value = false
            audioBoostEnabled.value = false
            runCatching { audioBoostEnhancer?.enabled = false }
        } else {
            applyAudioBoost()
            runCatching {
                val eqEnabled = dataStore.get(EqualizerEnabledKey, false)
                equalizer?.enabled = eqEnabled
                equalizerState.value = equalizerState.value.copy(enabled = eqEnabled)
            }
        }
    }

    fun ensureVisualizer() {
        visualizerManager.isPlaying = player.isPlaying
        visualizerManager.start(player.audioSessionId)
    }

    fun setAudioBoostEnabled(enabled: Boolean) {
        if (bypassAllAudioEffects.value && enabled) return
        audioBoostEnabled.value = enabled
        applyAudioBoost()
        scope.launch { dataStore.edit { it[AudioBoostEnabledKey] = enabled } }
    }

    fun setAudioBoostPercent(percent: Int) {
        val value = percent.coerceIn(100, 200)
        audioBoostPercent.value = value
        applyAudioBoost()
        scope.launch { dataStore.edit { it[AudioBoostPercentKey] = value } }
    }

    private fun applyAudioBoost() {
        if (!::player.isInitialized) return
        val session = player.audioSessionId
        if (session <= 0 || session == C.AUDIO_SESSION_ID_UNSET) return
        runCatching {
            if (audioBoostEnhancer == null || audioBoostSessionId != session) {
                runCatching { audioBoostEnhancer?.release() }
                audioBoostEnhancer = LoudnessEnhancer(session)
                audioBoostSessionId = session
            }
            val enabled = audioBoostEnabled.value && !bypassAllAudioEffects.value && audioBoostPercent.value > 100
            audioBoostEnhancer?.setTargetGain(((audioBoostPercent.value - 100) * 100).coerceIn(0, 10000))
            audioBoostEnhancer?.enabled = enabled
        }.onFailure { Timber.w(it, "Could not apply Audio FX master boost") }
    }

    private fun persistFx(block: suspend androidx.datastore.preferences.core.MutablePreferences.() -> Unit) {
        scope.launch { dataStore.edit { block(it) } }
    }

    fun setEchoEnabled(v: Boolean) { echoEnabled.value=v; djAudioProcessor.echoEnabled=v; persistFx { this[EchoEnabledKey]=v } }
    fun setEchoDelayMs(v: Int) { val x=v.coerceIn(20,1000); echoDelayMs.value=x; djAudioProcessor.echoDelayMs=x; persistFx { this[EchoDelayMsKey]=x } }
    fun setEchoFeedback(v: Float) { val x=v.coerceIn(0f,.85f); echoFeedback.value=x; djAudioProcessor.echoFeedback=x; persistFx { this[EchoFeedbackKey]=x } }
    fun setEchoWetMix(v: Float) { val x=v.coerceIn(0f,1f); echoWetMix.value=x; djAudioProcessor.echoWetMix=x; persistFx { this[EchoWetMixKey]=x } }
    fun setEchoPingPong(v: Boolean) { echoPingPong.value=v; djAudioProcessor.echoPingPong=v; persistFx { this[EchoPingPongKey]=v } }
    fun setDjFilterSweep(v: Float) { val x=v.coerceIn(-1f,1f); djFilterSweep.value=x; djAudioProcessor.filterSweep=x; persistFx { this[DjFilterSweepKey]=x } }
    fun setDjFlangerEnabled(v: Boolean) { djFlangerEnabled.value=v; djAudioProcessor.flangerEnabled=v; persistFx { this[DjFlangerEnabledKey]=v } }
    fun setDjFlangerRate(v: Float) { val x=v.coerceIn(.1f,4f); djFlangerRate.value=x; djAudioProcessor.flangerRate=x; persistFx { this[DjFlangerRateKey]=x } }
    fun setDjFlangerDepth(v: Float) { val x=v.coerceIn(0f,1f); djFlangerDepth.value=x; djAudioProcessor.flangerDepth=x; persistFx { this[DjFlangerDepthKey]=x } }
    fun setDjSaturation(v: Float) { val x=v.coerceIn(0f,1f); djSaturation.value=x; djAudioProcessor.saturation=x; persistFx { this[DjSaturationKey]=x } }
    fun setDjTempoAndPitch(speed: Float, pitch: Float) {
        val sp=speed.coerceIn(.5f,2f); val pi=pitch.coerceIn(.5f,2f)
        djTempoSpeed.value=sp; djPitch.value=pi
        if (::player.isInitialized && !bypassAllAudioEffects.value) player.playbackParameters=androidx.media3.common.PlaybackParameters(sp,pi)
        persistFx { this[DjTempoSpeedKey]=sp; this[DjPitchKey]=pi }
    }
    fun setDjTurntableLinked(v: Boolean) { djTurntableLinked.value=v; if(v) setDjTempoAndPitch(djTempoSpeed.value,djTempoSpeed.value); persistFx { this[DjTurntableLinkedKey]=v } }
    fun setAudioFxAndDjEnabled(v: Boolean) { audioFxAndDjEnabled.value=v; djAudioProcessor.enabled=v && !bypassAllAudioEffects.value; if(!v) resetAudioFx(); persistFx { this[AudioFxAndDjEnabledKey]=v } }
    fun setBypassAllAudioEffects(v: Boolean) { bypassAllAudioEffects.value=v; applyAudioFxEnableState(); persistFx { this[BypassAllAudioEffectsKey]=v } }
    fun resetDjFx() {
        setEchoEnabled(false); setEchoDelayMs(280); setEchoFeedback(.40f); setEchoWetMix(0f); setEchoPingPong(false)
        setDjFilterSweep(0f); setDjFlangerEnabled(false); setDjFlangerRate(.5f); setDjFlangerDepth(0f); setDjSaturation(0f)
        setDjTurntableLinked(false); setDjTempoAndPitch(1f,1f); djAudioProcessor.resetFx()
    }
    fun applyDjPreset(preset: DjPreset) {
        if (bypassAllAudioEffects.value) return
        when (preset) {
            DjPreset.DEFAULT -> resetDjFx()
            DjPreset.CLUB_BOOTH -> { setEchoEnabled(true); setEchoDelayMs(180); setEchoFeedback(.30f); setEchoWetMix(.35f); setEchoPingPong(true); setDjFilterSweep(-.15f); setDjSaturation(.20f); setAudioBoostPercent(130); setAudioBoostEnabled(true); setDjTempoAndPitch(1f,1f) }
            DjPreset.SLOWED_REVERB -> { setEchoEnabled(true); setEchoDelayMs(380); setEchoFeedback(.55f); setEchoWetMix(.50f); setEchoPingPong(true); setDjFilterSweep(-.25f); setDjSaturation(.15f); setDjTurntableLinked(true); setDjTempoAndPitch(.85f,.85f) }
            DjPreset.NIGHTCORE -> { setEchoEnabled(false); setDjFilterSweep(.20f); setDjSaturation(.10f); setDjTurntableLinked(true); setDjTempoAndPitch(1.25f,1.25f) }
            DjPreset.BASS_BOMB -> { setEchoEnabled(false); setDjFilterSweep(-.35f); setDjSaturation(.40f); setAudioBoostPercent(160); setAudioBoostEnabled(true); setDjTempoAndPitch(1f,1f) }
            DjPreset.LOFI_VINYL -> { setEchoEnabled(true); setEchoDelayMs(120); setEchoFeedback(.25f); setEchoWetMix(.30f); setEchoPingPong(false); setDjFilterSweep(-.50f); setDjSaturation(.35f); setDjFlangerEnabled(true); setDjFlangerRate(.2f); setDjFlangerDepth(.25f); setDjTempoAndPitch(.95f,.95f) }
            DjPreset.SPACE_ECHO -> { setEchoEnabled(true); setEchoDelayMs(450); setEchoFeedback(.65f); setEchoWetMix(.60f); setEchoPingPong(true); setDjFilterSweep(0f); setDjFlangerEnabled(true); setDjFlangerRate(.4f); setDjFlangerDepth(.35f); setDjSaturation(.10f); setDjTempoAndPitch(1f,1f) }
        }
    }
    fun resetAudioFx() { setAudioBoostPercent(100); setAudioBoostEnabled(false); resetEqualizer(); resetDjFx(); djAudioProcessor.enabled=false }

    fun ensureAudioStudioEffects() = setupAudioStudioEffects()

    private fun setupAudioStudioEffects() {
        val sessionId = player.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) {
            audioStudioState.value = audioStudioState.value.copy(available = false)
            return
        }
        if (studioSessionId == sessionId && (bassBoost != null || virtualizer != null || presetReverb != null)) return
        releaseAudioStudioEffects()
        val enabled = dataStore.get(AudioStudioEnabledKey, false)
        val bass = dataStore.get(AudioStudioBassKey, 0).coerceIn(0, 1000)
        val space = dataStore.get(AudioStudioVirtualizerKey, 0).coerceIn(0, 1000)
        val reverb = dataStore.get(AudioStudioReverbKey, 0).coerceIn(0, 6)
        var bassSupported = false
        var spaceSupported = false
        var reverbSupported = false
        try {
            bassBoost = BassBoost(0, sessionId).also {
                bassSupported = it.strengthSupported
                if (bassSupported) it.setStrength(bass.toShort())
                it.enabled = enabled && bassSupported && bass > 0
            }
        } catch (e: Exception) { reportException(e); bassBoost = null }
        try {
            virtualizer = Virtualizer(0, sessionId).also {
                spaceSupported = it.strengthSupported
                if (spaceSupported) it.setStrength(space.toShort())
                it.enabled = enabled && spaceSupported && space > 0
            }
        } catch (e: Exception) { reportException(e); virtualizer = null }
        try {
            presetReverb = PresetReverb(0, sessionId).also {
                it.preset = reverb.toShort()
                reverbSupported = true
                it.enabled = enabled && reverb > 0
            }
        } catch (e: Exception) { reportException(e); presetReverb = null }
        studioSessionId = sessionId
        audioStudioState.value = AudioStudioUiState(
            available = bassBoost != null || virtualizer != null || presetReverb != null,
            enabled = enabled,
            bassStrength = bass,
            spaceStrength = space,
            reverbPreset = reverb,
            bassSupported = bassSupported,
            spaceSupported = spaceSupported,
            reverbSupported = reverbSupported,
        )
    }

    fun setAudioStudioEnabled(enabled: Boolean) {
        setupAudioStudioEffects()
        runCatching {
            bassBoost?.enabled = enabled && audioStudioState.value.bassSupported && audioStudioState.value.bassStrength > 0
            virtualizer?.enabled = enabled && audioStudioState.value.spaceSupported && audioStudioState.value.spaceStrength > 0
            presetReverb?.enabled = enabled && audioStudioState.value.reverbSupported && audioStudioState.value.reverbPreset > 0
        }.onFailure(::reportException)
        audioStudioState.value = audioStudioState.value.copy(enabled = enabled)
        scope.launch { dataStore.edit { it[AudioStudioEnabledKey] = enabled } }
    }

    fun setAudioStudioBass(strength: Int) {
        setupAudioStudioEffects()
        val value = strength.coerceIn(0, 1000)
        runCatching {
            bassBoost?.setStrength(value.toShort())
            bassBoost?.enabled = audioStudioState.value.enabled && value > 0
        }.onFailure(::reportException)
        audioStudioState.value = audioStudioState.value.copy(bassStrength = value)
        scope.launch { dataStore.edit { it[AudioStudioBassKey] = value } }
    }

    fun setAudioStudioSpace(strength: Int) {
        setupAudioStudioEffects()
        val value = strength.coerceIn(0, 1000)
        runCatching {
            virtualizer?.setStrength(value.toShort())
            virtualizer?.enabled = audioStudioState.value.enabled && value > 0
        }.onFailure(::reportException)
        audioStudioState.value = audioStudioState.value.copy(spaceStrength = value)
        scope.launch { dataStore.edit { it[AudioStudioVirtualizerKey] = value } }
    }

    fun setAudioStudioReverb(preset: Int) {
        setupAudioStudioEffects()
        val value = preset.coerceIn(0, 6)
        runCatching {
            presetReverb?.preset = value.toShort()
            presetReverb?.enabled = audioStudioState.value.enabled && value > 0
        }.onFailure(::reportException)
        audioStudioState.value = audioStudioState.value.copy(reverbPreset = value)
        scope.launch { dataStore.edit { it[AudioStudioReverbKey] = value } }
    }

    private fun releaseAudioStudioEffects() {
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { presetReverb?.release() }
        bassBoost = null
        virtualizer = null
        presetReverb = null
        studioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private fun equalizerBandKey(index: Int) = intPreferencesKey("equalizerBand$index")

    fun ensureEqualizer() {
        setupEqualizer()
    }

    private fun setupEqualizer() {
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            equalizerState.value = equalizerState.value.copy(isAvailable = false)
            return
        }

        if (equalizer != null && equalizerSessionId == audioSessionId) {
            return
        }

        releaseEqualizer()

        try {
            val newEqualizer = Equalizer(0, audioSessionId)
            val range = newEqualizer.bandLevelRange
            val enabled = dataStore.get(EqualizerEnabledKey, false)
            val bandLevels =
                (0 until newEqualizer.numberOfBands).map { index ->
                    dataStore
                        .get(equalizerBandKey(index), 0)
                        .coerceIn(range[0].toInt(), range[1].toInt())
                        .toShort()
                        .also { level ->
                            newEqualizer.setBandLevel(index.toShort(), level)
                        }
                }
            val frequencies =
                (0 until newEqualizer.numberOfBands).map { index ->
                    newEqualizer.getCenterFreq(index.toShort())
                }

            newEqualizer.enabled = enabled
            equalizer = newEqualizer
            equalizerSessionId = audioSessionId
            equalizerState.value =
                EqualizerUiState(
                    isAvailable = true,
                    enabled = enabled,
                    bandLevels = bandLevels,
                    centerFrequencies = frequencies,
                    minBandLevel = range[0],
                    maxBandLevel = range[1],
                )
        } catch (e: Exception) {
            reportException(e)
            equalizerState.value = EqualizerUiState(isAvailable = false)
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        setupEqualizer()
        try {
            equalizer?.enabled = enabled
            equalizerState.value = equalizerState.value.copy(enabled = enabled)
            scope.launch {
                dataStore.edit { settings ->
                    settings[EqualizerEnabledKey] = enabled
                }
            }
        } catch (e: Exception) {
            reportException(e)
        }
    }

    fun setEqualizerBandLevel(
        index: Int,
        level: Short,
    ) {
        setupEqualizer()
        val state = equalizerState.value
        if (index !in state.bandLevels.indices) return

        val clampedLevel =
            level
                .coerceIn(state.minBandLevel, state.maxBandLevel)
        try {
            equalizer?.setBandLevel(index.toShort(), clampedLevel)
            equalizerState.value =
                state.copy(
                    bandLevels =
                        state.bandLevels.toMutableList().also {
                            it[index] = clampedLevel
                        },
                )
            scope.launch {
                dataStore.edit { settings ->
                    settings[equalizerBandKey(index)] = clampedLevel.toInt()
                }
            }
        } catch (e: Exception) {
            reportException(e)
        }
    }

    fun resetEqualizer() {
        setupEqualizer()
        equalizerState.value.bandLevels.indices.forEach { index ->
            setEqualizerBandLevel(index, 0)
        }
    }

    private fun releaseEqualizer() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
            reportException(e)
        } finally {
            equalizer = null
            equalizerSessionId = C.AUDIO_SESSION_ID_UNSET
        }
    }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        setupLoudnessEnhancer()
        setupEqualizer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        releaseLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            },
        )
    }

    private fun refreshShuffleOrderIfNeeded() {
        if (!player.shuffleModeEnabled || player.mediaItemCount <= 1) return
        val currentIdx = player.currentMediaItemIndex
        val indices = IntArray(player.mediaItemCount) { it }
        indices.shuffle()
        val currentPos = indices.indexOf(currentIdx)
        if (currentPos >= 0) {
            indices[currentPos] = indices[0]
            indices[0] = currentIdx
        }
        player.setShuffleOrder(DefaultShuffleOrder(indices, System.currentTimeMillis()))
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        lastPlaybackSpeed = -1.0f // forzar actualización de canción

        setupLoudnessEnhancer()
        setupEqualizer()
        setupAudioStudioEffects()
        player.volume = playerVolume.value
        val fadeSeconds = dataStore.get(CrossfadeDurationKey, 0).coerceIn(0, 8)
        if (fadeSeconds > 0 && player.isPlaying) {
            val targetVolume = playerVolume.value
            player.volume = 0f
            scope.launch {
                val steps = 8
                repeat(steps) { step ->
                    delay(75L)
                    player.volume = targetVolume * ((step + 1).toFloat() / steps)
                }
            }
        }

        // Resetear errores consecutivos cuando hay transición exitosa
        consecutivePlaybackErr = 0

        // Pulse-aware auto pagination. Keep the old setting, but make it reliable: load before the
        // last few items, guard against concurrent requests, and allow Pulse queues to synthesize
        // seed-aware similar content when the provider's short page is exhausted.
        if (dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 8 &&
            currentQueue.hasNextPage() &&
            !(dataStore.get(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL) &&
            !queuePageLoadInProgress
        ) {
            queuePageLoadInProgress = true
            scope.launch(SilentHandler) {
                try {
                    val mediaItems = currentQueue.nextPage()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .distinctBy { it.mediaId }
                    if (mediaItems.isNotEmpty() && player.playbackState != STATE_IDLE) {
                        player.addMediaItems(mediaItems)
                        refreshShuffleOrderIfNeeded()
                    }
                } finally {
                    queuePageLoadInProgress = false
                }
            }
        }

        // Guardar estado cuando cambia el item de medios
        if (dataStore.get(PersistentQueueKey, true)) {
            scope.launch {
                delay(500) // Pequeño delay para asegurar que el estado esté estable
                saveQueueToDisk()
            }
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        // Guardar estado cuando cambia el estado de reproducción
        if (dataStore.get(PersistentQueueKey, true) && playbackState != Player.STATE_BUFFERING) {
            scope.launch {
                delay(500)
                saveQueueToDisk()
            }
        }

        // Cuando termina la reproducción, ocultar notificación si la cola está vacía
        if (playbackState == Player.STATE_ENDED) {
            scope.launch {
                delay(1000)
                if (!player.isPlaying && player.mediaItemCount == 0) {
                    // Limpiar metadata para forzar actualización de notificación
                    currentMediaMetadata.value = null
                }
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady) {
            setupLoudnessEnhancer()
            setupEqualizer()
        }

        // Actualizar notificación cuando cambia el estado de reproducción
        scope.launch {
            delay(300)
            updateNotification()
        }
    }

    private var pausedIdleStopJob: kotlinx.coroutines.Job? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            if (player.playWhenReady) {
                // The user wants this playing — even through a brief
                // buffering moment or an in-progress error-retry/auto-skip
                // (which can transiently put the player in STATE_IDLE).
                // playWhenReady is Media3's own signal for user-intent, not
                // "is audio actively coming out right now" — using it alone
                // here (instead of also requiring isBufferingOrReady) means
                // a temporary hiccup during active listening never starts
                // the stop-timer below.
                val focusGranted = requestAudioFocus()
                if (focusGranted) {
                    openAudioEffectSession()
                }
                pausedIdleStopJob?.cancel()
                pausedIdleStopJob = null
            } else {
                closeAudioEffectSession()
                // Abandonar foco de audio cuando no está reproduciendo
                if (!player.playWhenReady || player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    abandonAudioFocus()
                }
                schedulePausedStopIfIdle()
            }
        }

        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
            // Forzar actualización de notificación para asegurar que la imagen se cargue
            scope.launch {
                delay(200)
                updateNotification()
            }
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        if (shuffleModeEnabled) {
            // Si la cola está vacía, no mezclar
            if (player.mediaItemCount == 0) return

            // Siempre poner el item que se está reproduciendo primero
            val shuffledIndices = IntArray(player.mediaItemCount) { it }
            shuffledIndices.shuffle()
            shuffledIndices[shuffledIndices.indexOf(player.currentMediaItemIndex)] =
                shuffledIndices[0]
            shuffledIndices[0] = player.currentMediaItemIndex
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }

        // Guardar estado cuando cambia el modo aleatorio
        if (dataStore.get(PersistentQueueKey, true)) {
            scope.launch {
                delay(300)
                saveQueueToDisk()
            }
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }

        // Guardar estado cuando cambia el modo de repetición
        if (dataStore.get(PersistentQueueKey, true)) {
            scope.launch {
                delay(300)
                saveQueueToDisk()
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        Log.e(TAG, "Player error: ${error.errorCodeName}, message: ${error.message}", error)

        // If the underlying cause is a 403 from the CDN, the cached stream
        // URL for this media item is now known-bad — drop it so the next
        // playback attempt re-resolves a fresh one instead of reusing it.
        run {
            var cause: Throwable? = error
            while (cause != null) {
                if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException && cause.responseCode == 403) {
                    player.currentMediaItem?.mediaId?.let { YTPlayerUtils.invalidateCachedStreamUrls(it) }
                    break
                }
                cause = cause.cause
            }
        }

        val isConnectionError = (error.cause?.cause is PlaybackException) &&
                (error.cause?.cause as PlaybackException).errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

        if (!isNetworkConnected.value || isConnectionError) {
            waitOnNetworkError()
            return
        }

        if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
            skipOnError()
        } else {
            stopOnError()
        }
    }

    // Reuse clients instead of rebuilding connection pools for each stream resolution.
    // Keep the YouTube proxy client separate from direct-source reachability probes so
    // optional providers (JioSaavn/JossRed) retain their existing network behavior.
    private val playbackHttpClient: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .build()
    }

    private val streamProbeHttpClient: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource
                    .Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            this,
                            OkHttpDataSource.Factory(playbackHttpClient),
                        ),
                    ),
            ).setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    private fun createDataSourceFactory(): DataSource.Factory {
        data class CachedSongUrl(
            val url: String,
            val expiresAt: Long,
            val contentLength: Long?,
        )

        fun DataSpec.withStreamUrl(url: String, contentLength: Long?): DataSpec {
            val resolved = withUri(url.toUri())
            if (resolved.length != C.LENGTH_UNSET.toLong()) return resolved

            val remainingLength =
                contentLength
                    ?.takeIf { it > resolved.position }
                    ?.let { it - resolved.position }
                    ?: C.LENGTH_UNSET.toLong()

            return if (remainingLength != C.LENGTH_UNSET.toLong()) {
                resolved.buildUpon()
                    .setLength(remainingLength)
                    .build()
            } else {
                resolved
            }
        }

        val songUrlCache = java.util.concurrent.ConcurrentHashMap<String, CachedSongUrl>()
        return ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            if (dataSpec.uri.scheme == "content" || dataSpec.uri.scheme == "file") {
                return@Factory dataSpec
            }
            
            val mediaId = dataSpec.key ?: error("No media id")

            if (downloadCache.isCached(
                    mediaId,
                    dataSpec.position,
                    if (dataSpec.length >= 0) dataSpec.length else 1
                ) ||
                playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)
            ) {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                return@Factory dataSpec
            }

            songUrlCache[mediaId]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                return@Factory dataSpec.withStreamUrl(it.url, it.contentLength)
            }

            if (mediaId.startsWith("JS:")) {
                try {
                    val streamUrl = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        com.devxrohan.smusic.jiosaavn.JioSaavnApi.getStreamUrl(mediaId)
                    }
                    if (streamUrl != null) {
                        songUrlCache[mediaId] = CachedSongUrl(
                            url = streamUrl,
                            expiresAt = System.currentTimeMillis() + 3600000L,
                            contentLength = null,
                        )
                        scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                        return@Factory dataSpec.withUri(streamUrl.toUri())
                    } else {
                        throw androidx.media3.common.PlaybackException(
                            "JioSaavn Stream URL not found",
                            null,
                            androidx.media3.common.PlaybackException.ERROR_CODE_REMOTE_ERROR
                        )
                    }
                } catch (e: Exception) {
                    throw androidx.media3.common.PlaybackException(
                        "JioSaavn API error: ${e.message}",
                        e,
                        androidx.media3.common.PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }
            }

            // Optional JioSaavn-first mode for catalogue tracks. This is a best-effort
            // title/artist match; if no reliable match or stream is available, continue with YouTube.
            // This preference is already warmed into the app's DataStore cache at service
            // startup. Read the cached value synchronously instead of suspending on disk for
            // every uncached YouTube track; JioSaavn priority remains fully user-controlled.
            val preferSaavn = dataStore.get(
                com.devxrohan.smusic.constants.PrioritizeJioSaavn320Key,
                false,
            )
            if (preferSaavn && !mediaId.startsWith("JS:")) {
                runCatching {
                    val localSong = database.getSongById(mediaId)
                    if (localSong != null) {
                        val artistName = localSong.artists.joinToString(" ") { it.name }
                        val query = "${localSong.title} $artistName".trim()
                        val candidates = runBlocking(Dispatchers.IO) {
                            com.devxrohan.smusic.jiosaavn.JioSaavnApi.searchSongs(query).getOrDefault(emptyList())
                        }
                        val normalizedTitle = localSong.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                        val match = candidates.firstOrNull { candidate ->
                            val candidateTitle = candidate.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                            candidateTitle == normalizedTitle || candidateTitle.contains(normalizedTitle) || normalizedTitle.contains(candidateTitle)
                        } ?: candidates.firstOrNull()
                        val saavnUrl = match?.let { candidate ->
                            runBlocking(Dispatchers.IO) { com.devxrohan.smusic.jiosaavn.JioSaavnApi.getStreamUrl(candidate.id) }
                        }
                        if (!saavnUrl.isNullOrBlank()) {
                            val streamReachable = runCatching {
                                streamProbeHttpClient
                                    .newCall(Request.Builder().url(saavnUrl).head().build())
                                    .execute()
                                    .use { it.isSuccessful }
                            }.getOrDefault(false)
                            if (streamReachable) {
                                Timber.i("Using reachable JioSaavn-priority stream for $mediaId; YouTube remains fallback")
                                songUrlCache[mediaId] = CachedSongUrl(
                                    url = saavnUrl,
                                    expiresAt = System.currentTimeMillis() + 20 * 60 * 1000L,
                                    contentLength = null,
                                )
                                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                                return@Factory dataSpec.withUri(saavnUrl.toUri())
                            }
                            Timber.w("JioSaavn returned an unreachable stream for $mediaId; continuing with YouTube")
                        }
                    }
                }.onFailure { Timber.w(it, "JioSaavn priority lookup failed; continuing with YouTube") }
            }

            // YouTube remains the fallback/source of truth when JioSaavn cannot resolve the track.
            val ytLogTag = "YouTube"
            try {
                val effectiveAudioQuality = if (this@MusicService.isLowDataModeActive()) AudioQuality.LOW else audioQuality
                val playbackData = runBlocking(Dispatchers.IO) {
                    YTPlayerUtils.playerResponseForPlayback(
                        mediaId,
                        audioQuality = effectiveAudioQuality,
                        connectivityManager = connectivityManager,
                    )
                }.getOrElse { throwable ->
                    when (throwable) {
                        is PlaybackException -> throw throwable

                        is ConnectException, is UnknownHostException -> {
                            throw PlaybackException(
                                getString(R.string.error_no_internet),
                                throwable,
                                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                            )
                        }

                        is SocketTimeoutException -> {
                            throw PlaybackException(
                                getString(R.string.error_timeout),
                                throwable,
                                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                            )
                        }

                        else -> throw PlaybackException(
                            getString(R.string.error_unknown),
                            throwable,
                            PlaybackException.ERROR_CODE_REMOTE_ERROR
                        )
                    }
                }

                val format = playbackData.format

                // Some valid YouTube audio formats omit contentLength or the optional codecs
                // parameter. Playback must not crash just because metadata is incomplete; only
                // persist a cache-format row when all required database fields are available.
                val contentLength = format.contentLength
                val mimeType = format.mimeType.substringBefore(';')
                val codecs = Regex("codecs\\s*=\\s*\"?([^;\"]+)\"?", RegexOption.IGNORE_CASE)
                    .find(format.mimeType)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                if (contentLength != null && contentLength > 0L && codecs.isNotBlank()) {
                    runCatching {
                        database.query {
                            upsert(
                                FormatEntity(
                                    id = mediaId,
                                    itag = format.itag,
                                    mimeType = mimeType,
                                    codecs = codecs,
                                    bitrate = format.bitrate,
                                    sampleRate = format.audioSampleRate,
                                    contentLength = contentLength,
                                    loudnessDb = playbackData.audioConfig?.loudnessDb,
                                    playbackUrl = playbackData.streamUrl
                                )
                            )
                        }
                    }.onFailure { Timber.tag(ytLogTag).w(it, "Skipping incomplete YouTube format cache metadata") }
                }
                scope.launch(Dispatchers.IO) { recoverSong(mediaId, playbackData) }

                val streamUrl = playbackData.streamUrl

                songUrlCache[mediaId] = CachedSongUrl(
                    url = streamUrl,
                    expiresAt = System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L),
                    contentLength = format.contentLength,
                )
                return@Factory dataSpec.withStreamUrl(streamUrl, format.contentLength)
            } catch (e: Exception) {
                Timber.tag(ytLogTag).e(e, "YouTube playback error, trying JossRed as fallback")

                // Verificar si la fuente alternativa está habilitada
                val useAlternativeSource = dataStore.get(
                    booleanPreferencesKey("JossRedMultimedia"),
                    false,
                )

                // Si la fuente alternativa está deshabilitada, relanzar la excepción
                if (!useAlternativeSource) {
                    throw e
                }

                // Fuente alternativa: JossRed (fallback)
                val JRlogTag = "JossRed"
                try {
                    val alternativeUrl = runCatching {
                        runBlocking(Dispatchers.IO) {
                            withTimeout(5000) {
                                JossRedClient.getStreamingUrl(mediaId)
                            }
                        }
                    }.getOrNull()

                    if (alternativeUrl != null) {
                        // Keep the existing reachability check, but reuse its connection pool.
                        val request = Request.Builder()
                            .url(alternativeUrl)
                            .head()
                            .build()

                        try {
                            val response = streamProbeHttpClient.newCall(request).execute()
                            response.use {
                                if (it.isSuccessful) {
                                    Timber.tag(JRlogTag)
                                        .i("Using JossRed URL as fallback: $alternativeUrl")
                                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                                    return@Factory dataSpec.withUri(alternativeUrl.toUri())
                                } else {
                                    Timber.tag(JRlogTag)
                                        .w("JossRed URL unreachable (HTTP ${it.code}), throwing original error")
                                    throw e
                                }
                            }
                        } catch (jrException: Exception) {
                            Timber.tag(JRlogTag).e(
                                jrException,
                                "Error verifying JossRed URL, throwing original error"
                            )
                            throw e
                        }
                    } else {
                        throw e
                    }
                } catch (jrException: Exception) {
                    when (jrException) {
                        is JossRedClient.JossRedException -> {
                            Timber.tag(JRlogTag)
                                .w("JossRed error: ${jrException.message}, throwing original error")
                        }

                        is TimeoutCancellationException -> {
                            Timber.tag(JRlogTag).w("JossRed timeout, throwing original error")
                        }

                        else -> {
                            Timber.tag(JRlogTag)
                                .e(jrException, "JossRed error, throwing original error")
                        }
                    }
                    throw e
                }
            }
        }
    }

    private fun createMediaSourceFactory() =
        DefaultMediaSourceFactory(createDataSourceFactory())

    private fun createRenderersFactory() =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink
                .Builder(this@MusicService)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        arrayOf(djAudioProcessor, spatialAudioProcessor, eightDAudioProcessor),
                        SilenceSkippingAudioProcessor(2_000_000, 20_000, 256),
                        SonicAudioProcessor(),
                    ),
                ).build()
        }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        visualizerManager.isPlaying = isPlaying
        if (isPlaying && ::player.isInitialized) visualizerManager.start(player.audioSessionId)
    }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem =
            eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem

        // Pulse learns from every finished playback (including early skips), but honours "pause listen history".
        if (playbackStats.totalPlayTimeMs > 0 && !dataStore.get(PauseListenHistoryKey, false)) {
            val pulseSongId = mediaItem.mediaId
            val pulsePlayTime = playbackStats.totalPlayTimeMs
            scope.launch(Dispatchers.IO) { pulseEngine.onPlaybackFinished(pulseSongId, pulsePlayTime) }
        }

        if (playbackStats.totalPlayTimeMs >= (
                    dataStore[HistoryDuration]?.times(1000f)
                        ?: 30000f
                    ) &&
            !dataStore.get(PauseListenHistoryKey, false)
        ) {
            database.query {
                incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playbackStats.totalPlayTimeMs,
                        ),
                    )
                } catch (_: SQLException) {
                }
            }
            val PauseRemoteListenHistoryKey = booleanPreferencesKey("pauseRemoteListenHistory")
            // Remote YouTube history is account-only. Guest playback continues to
            // populate SMusic's local history but never writes to an anonymous session.
            if (!dataStore.get(PauseRemoteListenHistoryKey, false)) {
                scope.launch(Dispatchers.IO) {
                    if (!isYouTubeAccountWriteBackAllowed()) return@launch
                    val signatureTimestamp = YTPlayerUtils.signatureTimestampForHistory(mediaItem.mediaId)
                    val playbackUrl = YTPlayerUtils.playerResponseForMetadata(
                        mediaItem.mediaId,
                        null,
                        signatureTimestamp = signatureTimestamp,
                        setLogin = true,
                    ).getOrNull()?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                    // Recheck the account immediately before sending the history ping.
                    if (playbackUrl != null && isYouTubeAccountWriteBackAllowed()) {
                        YouTube.registerPlayback(null, playbackUrl)
                            .onFailure { reportException(it) }
                    }
                }
            }
        }
    }

    private fun saveQueueToDisk() {
        if (player.playbackState == STATE_IDLE && player.mediaItemCount == 0) {
            filesDir.resolve(PERSISTENT_AUTOMIX_FILE).delete()
            filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
            filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).delete()
            return
        }

        try {
            val persistQueue =
                PersistQueue(
                    title = queueTitle,
                    items = player.mediaItems.mapNotNull { it.metadata },
                    mediaItemIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                    position = if (player.currentPosition >= 0) player.currentPosition else 0,
                )
            val persistAutomix =
                PersistQueue(
                    title = "automix",
                    items = automixItems.value.mapNotNull { it.metadata },
                    mediaItemIndex = 0,
                    position = 0,
                )

            // Guardar estado del reproductor
            val playerState = PersistPlayerState(
                repeatMode = player.repeatMode,
                shuffleModeEnabled = player.shuffleModeEnabled,
                volume = player.volume,
                currentMediaItemIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                currentPosition = if (player.currentPosition >= 0) player.currentPosition else 0,
                playWhenReady = player.playWhenReady, // Estado de reproducción (si está listo para reproducir)
                playbackState = player.playbackState // Estado actual del reproductor
            )

            runCatching {
                filesDir.resolve(PERSISTENT_QUEUE_FILE).outputStream().use { fos ->
                    ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(persistQueue)
                    }
                }
            }.onFailure {
                Log.e(TAG, "Error saving queue to disk", it)
                reportException(it)
            }

            runCatching {
                filesDir.resolve(PERSISTENT_AUTOMIX_FILE).outputStream().use { fos ->
                    ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(persistAutomix)
                    }
                }
            }.onFailure {
                Log.e(TAG, "Error saving automix to disk", it)
                reportException(it)
            }

            runCatching {
                filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).outputStream().use { fos ->
                    ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(playerState)
                    }
                }
            }.onFailure {
                Log.e(TAG, "Error saving player state to disk", it)
                reportException(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveQueueToDisk", e)
            reportException(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroy() {
        if (instance === this) instance = null
        // Must be first — without cancelling this, the periodic queue-save
        // loops (10s/30s intervals) and any other scope-launched coroutine
        // keep running in the background indefinitely, since their
        // isActive check only flips false once the scope itself is
        // cancelled. This was the real cause of battery drain persisting
        // even after the service was "destroyed".
        scope.cancel()
        if (discordRpc?.isRpcRunning() == true) {
            discordRpc?.closeRPC()
        }
        discordRpc = null
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
        abandonAudioFocus()
        releaseLoudnessEnhancer()
        releaseEqualizer()
        releaseAudioStudioEffects()
        runCatching { audioBoostEnhancer?.release() }
        audioBoostEnhancer = null
        audioBoostSessionId = C.AUDIO_SESSION_ID_UNSET
        runCatching { visualizerManager.stop() }
        transitionFadeJob?.cancel()
        mediaController?.release()
        mediaController = null
        mediaSession.release()
        player.removeListener(this)
        player.removeListener(sleepTimer)
        player.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): android.os.IBinder {
        hasBoundClients = true
        pausedIdleStopJob?.cancel()
        pausedIdleStopJob = null
        return super.onBind(intent) ?: binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hasBoundClients = false
        schedulePausedStopIfIdle()
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        hasBoundClients = true
        pausedIdleStopJob?.cancel()
        pausedIdleStopJob = null
        super.onRebind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Without this, the OS's default restart-policy can bring this
        // service back after we've already decided to stop it — this was
        // the real reason "Stop music on task clear" could look like it
        // wasn't working, why the notification could reappear after being
        // dismissed, and why behavior varied across phones (different
        // OEMs handle sticky-restart differently).
        return START_NOT_STICKY
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val keepInForeground = startInForegroundRequired || hasResumablePlayback()
        if (keepInForeground) hasCalledStartForeground = true
        runCatching { super.onUpdateNotification(session, keepInForeground) }
            .onFailure { reportException(it) }
    }

    private fun hasResumablePlayback(): Boolean {
        val state = player.playbackState
        return player.mediaItemCount > 0 &&
            player.currentMediaItem != null &&
            state != Player.STATE_IDLE &&
            state != Player.STATE_ENDED
    }

    /**
     * Explicitly removes the foreground notification before stopping —
     * calling stopSelf() alone leaves a window where Media3's own
     * notification-manager can still re-post the notification, which is
     * why it could reappear even after being dismissed.
     */
    private fun stopForegroundAndSelf() {
        pausedIdleStopJob?.cancel()
        pausedIdleStopJob = null
        // Must happen first and synchronously — stopSelf() below only
        // requests the service be torn down, it doesn't guarantee it
        // happens immediately. Without explicitly pausing here, the
        // player can keep producing audio in the background for however
        // long that teardown takes, even with the notification and task
        // already gone.
        runCatching {
            player.pause()
            player.stop()
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        hasCalledStartForeground = false
        stopSelf()
    }

    /**
     * Schedules stopping the service after a short idle period, but only
     * when nothing is bound (no UI actively showing) — this avoids ever
     * interrupting playback someone is actively looking at, while still
     * genuinely closing things down soon after they've left and paused.
     */
    private fun schedulePausedStopIfIdle() {
        if (hasBoundClients) return
        if (hasResumablePlayback() && player.playWhenReady) return

        // A temporary audio-focus loss (phone call, another app briefly
        // taking audio) sets this flag so playback can resume automatically
        // once focus returns — see handleAudioFocusChange(). Stopping the
        // whole service quickly in that case would leave nothing to
        // resume when the call ends, so it gets a much longer grace
        // period instead of the normal short one. A genuinely long call
        // still eventually stops things, just not within a few seconds.
        val isTemporaryInterruption = wasPlayingBeforeAudioFocusLoss
        val stopDelayMs = if (isTemporaryInterruption) 15 * 60 * 1000L else 5_000L

        pausedIdleStopJob?.cancel()
        pausedIdleStopJob = scope.launch {
            delay(stopDelayMs)
            if (hasBoundClients) return@launch
            if (hasResumablePlayback() && player.playWhenReady) return@launch
            stopForegroundAndSelf()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // onTaskRemoved runs on the main thread and the system gives it very little headroom before flagging the
        // app as not responding. The previous `runBlocking { dataStore.getSuspend(...) }` here blocked that main
        // thread on a DataStore read — usually fast (served from the in-memory cache populated at startup — see
        // DataStore.kt's dataStoreCache), but with no guaranteed upper bound: a cache miss or a slow disk right
        // when the user swipes the app away reads from the actual DataStore Flow and can easily run past what
        // onTaskRemoved will tolerate. That's what showed up as "SMusic isn't responding" right after closing.
        // `dataStore.get(key, default)` is the synchronous, cache-only counterpart — it never touches disk, and
        // resolves to the same value here in practice since this key's cache entry (or its correct default when
        // the user has never changed the setting, which is the common case) is already populated well before
        // onTaskRemoved could ever fire.
        val stopOnTaskClear = dataStore.get(StopMusicOnTaskClearKey, false)
        if (stopOnTaskClear || player.currentMediaItem == null) {
            stopForegroundAndSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    companion object {
        @Volatile
        var instance: MusicService? = null
            private set

        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val HISTORY = "history"

        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
        const val MAX_CONSECUTIVE_ERR = 5
        const val CHUNK_LENGTH = 512 * 1024L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"

        // Constants for audio normalization
        private const val MAX_GAIN_MB = 800 // Maximum gain in millibels (8 dB)
        private const val MIN_GAIN_MB = -800 // Minimum gain in millibels (-8 dB)

        private const val TAG = "MusicService"
    }
}
