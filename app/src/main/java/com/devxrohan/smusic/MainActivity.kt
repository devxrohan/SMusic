package com.devxrohan.smusic

import android.Manifest
import android.annotation.SuppressLint
import androidx.core.app.ActivityCompat
import androidx.datastore.preferences.core.edit
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.scale
import android.content.ComponentName
import androidx.compose.material3.ExperimentalMaterial3Api
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import com.devxrohan.smusic.utils.ExternalPlayerUtil
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.models.SongItem
import com.devxrohan.smusic.innertube.models.WatchEndpoint
import com.devxrohan.smusic.constants.AppBarHeight
import com.devxrohan.smusic.constants.DarkModeKey
import com.devxrohan.smusic.constants.DisableScreenshotKey
import com.devxrohan.smusic.constants.DockedContentGap
import com.devxrohan.smusic.constants.DockedNavBarSurfaceHeight
import com.devxrohan.smusic.constants.MiniPlayerHeight
import com.devxrohan.smusic.constants.PauseSearchHistoryKey
import com.devxrohan.smusic.constants.SearchSource
import com.devxrohan.smusic.constants.SearchSourceKey
import com.devxrohan.smusic.constants.SlimNavBarKey
import com.devxrohan.smusic.constants.NavBarStyle
import com.devxrohan.smusic.constants.StopMusicOnTaskClearKey
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.db.entities.SearchHistory
import com.devxrohan.smusic.models.toMediaMetadata
import com.devxrohan.smusic.playback.DownloadUtil
import com.devxrohan.smusic.playback.MusicService
import com.devxrohan.smusic.playback.MusicService.MusicBinder
import com.devxrohan.smusic.playback.PlayerConnection
import com.devxrohan.smusic.playback.queues.YouTubeQueue
import com.devxrohan.smusic.ui.component.BottomSheet
import com.devxrohan.smusic.ui.component.BottomSheetMenu
import com.devxrohan.smusic.ui.component.SMusicBottomNav
import com.devxrohan.smusic.ui.component.SpotifyLyrics
import com.devxrohan.smusic.constants.AppFont
import com.devxrohan.smusic.constants.AppFontKey
import com.devxrohan.smusic.ui.component.CurvedBottomNavigationItem
import com.devxrohan.smusic.ui.component.LocalMenuState
import com.devxrohan.smusic.ui.component.rememberBackdrop
import com.devxrohan.smusic.ui.component.layerBackdrop
import com.devxrohan.smusic.ui.component.LocalBackdrop
import com.devxrohan.smusic.constants.PlayerScreenStyleKey
import com.devxrohan.smusic.constants.PlayerScreenStyle
import com.devxrohan.smusic.ui.component.NamePreferenceManager
import com.devxrohan.smusic.ui.component.NameProvider
import com.devxrohan.smusic.ui.component.rememberBottomSheetState
import com.devxrohan.smusic.ui.component.shimmer.ShimmerTheme
import com.devxrohan.smusic.ui.menu.YouTubeSongMenu
import com.devxrohan.smusic.ui.player.BottomSheetPlayer
import com.devxrohan.smusic.ui.screens.Screens
import com.devxrohan.smusic.ui.screens.navigationBuilder
import com.devxrohan.smusic.ui.screens.settings.DarkMode
import com.devxrohan.smusic.ui.screens.settings.NavigationTab
import com.devxrohan.smusic.ui.theme.DefaultThemeColor
import com.devxrohan.smusic.ui.theme.SMusicTheme
import com.devxrohan.smusic.ui.utils.appBarScrollBehavior
import com.devxrohan.smusic.ui.utils.resetHeightOffset
import com.devxrohan.smusic.utils.SyncUtils
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import com.devxrohan.smusic.utils.getSuspend
import com.devxrohan.smusic.utils.rememberEnumPreference
import com.devxrohan.smusic.utils.rememberPreference
import com.devxrohan.smusic.utils.isFreshInstall
import com.devxrohan.smusic.utils.reportException
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.zIndex

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
// Process-level state for "is this a real app open?" (see the ON_RESUME / ON_STOP observer).
// A new process starts with coldStartHandled = false, so every fresh launch counts once.
private var updateNotifyColdStartHandled = false
private var updateNotifyLastStopAt = 0L
/** Set when the app was opened by tapping one of our notifications: that open must not re-post it. */
private var updateNotifySuppressOnce = false
/** Away at least this long (app stopped) before coming back counts as opening the app again. */
private const val UPDATE_RENOTIFY_AWAY_MS = 30 * 60 * 1000L

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var namePreferenceManager: NamePreferenceManager


    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var isServiceBound = false
    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                if (service is MusicBinder) {
                    playerConnection =
                        PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playerConnection?.dispose()
                playerConnection = null
            }
        }

    private var latestVersionName by mutableStateOf<String>(BuildConfig.VERSION_NAME)

    override fun onStart() {
        super.onStart()
        com.devxrohan.smusic.playback.AppForegroundTracker.isForeground = true
        startService(Intent(this, MusicService::class.java))
        if (!isServiceBound) {
            bindService(
                Intent(this, MusicService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
            isServiceBound = true
        }
    }

    override fun onStop() {
        com.devxrohan.smusic.playback.AppForegroundTracker.isForeground = false
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        val stopOnTaskClear = kotlinx.coroutines.runBlocking {
            dataStore.getSuspend(StopMusicOnTaskClearKey, false)
        }
        if (stopOnTaskClear && playerConnection?.isPlaying?.value == true && isFinishing
        ) {
            stopService(Intent(this, MusicService::class.java))
        }
        playerConnection = null
    }

    /** A push message the system showed itself (app was in the background) opens the app with its data
     * as intent extras. Save it to the notification bell so it isn't lost. */
    private fun savePushMessageFromTrayTap(intent: Intent?) {
        if (intent == null || !intent.hasExtra("google.message_id")) return
        val message = intent.getStringExtra("message") ?: intent.getStringExtra("body") ?: return
        val id = intent.getStringExtra("id") ?: intent.getStringExtra("google.message_id") ?: return
        val title = intent.getStringExtra("title").orEmpty()
        val expiresAt = intent.getStringExtra("expiresAt")?.toLongOrNull() ?: 0L
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.devxrohan.smusic.update.AppNotificationManager.onPushAnnouncement(
                applicationContext,
                com.devxrohan.smusic.update.AnnouncementInfo(
                    id = id,
                    title = title,
                    message = message,
                    enabled = true,
                    expiresAt = expiresAt,
                ),
                notify = false,
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !com.devxrohan.smusic.App.isReady.value }

        super.onCreate(savedInstanceState)

        // Remember if we were launched from a notification tap (cold start or already running).
        if (intent?.hasExtra("navigate_to") == true) updateNotifySuppressOnce = true
        savePushMessageFromTrayTap(intent)
        addOnNewIntentListener(androidx.core.util.Consumer<Intent> { newIntent ->
            if (newIntent.hasExtra("navigate_to")) updateNotifySuppressOnce = true
            savePushMessageFromTrayTap(newIntent)
        })

        // The notification permission is NOT requested here any more: new installs are asked (and must
        // allow it) on the onboarding screen. Installs that predate that get one prompt after launch (below).

        com.devxrohan.smusic.update.AppNotificationManager.createNotificationChannels(this)
        // Push messages from the developer (Firebase Cloud Messaging): everyone listens to one topic.
        runCatching {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .subscribeToTopic(com.devxrohan.smusic.update.SMusicMessagingService.PUSH_TOPIC)
        }
        com.devxrohan.smusic.update.AppNotificationManager.checkOnAppOpen(this)

        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }


        // Daily AI recommendations (Settings > AI > AI recommendations).
        lifecycleScope.launch {
            dataStore.data
                .map { (try { it[com.devxrohan.smusic.constants.AiRecommendationsKey] } catch (e: Exception) { null }) ?: false }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    val workManager = androidx.work.WorkManager.getInstance(this@MainActivity)
                    if (enabled) {
                        val request = androidx.work.PeriodicWorkRequestBuilder<com.devxrohan.smusic.ai.AiRecommendationWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                            .setConstraints(
                                androidx.work.Constraints.Builder()
                                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                    .build()
                            )
                            .build()
                        workManager.enqueueUniquePeriodicWork(
                            "AiRecommendationWorker",
                            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                            request
                        )
                    } else {
                        workManager.cancelUniqueWork("AiRecommendationWorker")
                    }
                }
        }

        setContent {
            LaunchedEffect(Unit) {
                // Use the same release source as the update screen.
                com.devxrohan.smusic.update.GitHubReleaseFetcher(applicationContext)
                    .fetchLatestRelease()
                    ?.versionName
                    ?.let { latestVersionName = it }
            }

            var showFullscreenLyrics by remember { mutableStateOf(false) }
            // Defer account navigation until the main NavController is in scope.
            var onboardingLoginFlowActive by rememberSaveable { mutableStateOf(false) }
            var onboardingLoginScreenOpened by rememberSaveable { mutableStateOf(false) }
            var onboardingLoginDestinationReady by rememberSaveable { mutableStateOf(false) }
            var onboardingStep by rememberSaveable { mutableStateOf(0) }
            val (onboardingYouTubeCookie, _) = rememberPreference(com.devxrohan.smusic.constants.InnerTubeCookieKey, defaultValue = "")

            val playerScreenStyle by rememberEnumPreference<PlayerScreenStyle>(PlayerScreenStyleKey, defaultValue = PlayerScreenStyle.CLASSIC)

            val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
            val pureBlack = false
            val appFontKey by rememberPreference(AppFontKey, defaultValue = AppFont.APPLE.key)
            val appFont = remember(appFontKey) { AppFont.fromKey(appFontKey) }
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme =
                remember(darkTheme, isSystemInDarkTheme) {
                    if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
                }
            LaunchedEffect(useDarkTheme) {
                setSystemBarAppearance(useDarkTheme)
            }
            val themePresetId by rememberPreference(
                com.devxrohan.smusic.constants.ThemePresetKey,
                defaultValue = com.devxrohan.smusic.ui.theme.ThemePresets.SMUSIC_DEFAULT.id
            )
            val currentArtwork by playerConnection?.mediaMetadata?.collectAsState()
                ?: remember { mutableStateOf<com.devxrohan.smusic.models.MediaMetadata?>(null) }
            val themeRefresh by rememberPreference(com.devxrohan.smusic.ui.component.ArtworkThemeRefreshKey, 0)
            val themeColor = com.devxrohan.smusic.ui.theme.rememberArtworkTheme(
                currentArtwork?.thumbnailUrl,
                themePresetId == com.devxrohan.smusic.ui.theme.ThemePresets.SMUSIC_DEFAULT.id,
                themeRefresh,
            )
            SMusicTheme(
                darkTheme = useDarkTheme,
                pureBlack = pureBlack,
                appFont = appFont,
                themeColor = themeColor,
                themePresetId = themePresetId,
                artworkPaletteAvailable = currentArtwork != null,
            ) {

                val backdrop = rememberBackdrop()

                val updateViewModel: com.devxrohan.smusic.update.UpdateViewModel = androidx.hilt.navigation.compose.hiltViewModel()

                // Check updates when the UI is ready; retry once after cold-start initialization.
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    // App open: always a real check (force), so the popup appears right away.
                    updateViewModel.checkSilently(force = true)
                }

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    var foregroundPollJob: kotlinx.coroutines.Job? = null

                    fun runCheck(force: Boolean = false) {
                        updateViewModel.checkSilently(force)
                        com.devxrohan.smusic.update.AppNotificationManager.checkOnAppOpen(this@MainActivity)
                    }

                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        when (event) {
                            androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                                // Immediate check the moment the app becomes
                                // visible — cold start, or coming back from
                                // background (throttled inside the manager).
                                runCheck()
                                // Repost dismissed update notifications only on a fresh launch or return after an
                                // absence.
                                val now = System.currentTimeMillis()
                                val coldStart = !updateNotifyColdStartHandled
                                val awayLong = updateNotifyLastStopAt > 0L &&
                                    now - updateNotifyLastStopAt >= UPDATE_RENOTIFY_AWAY_MS
                                if (updateNotifySuppressOnce) {
                                    // Opened by tapping the notification itself: it was just dismissed, keep it gone.
                                    updateNotifySuppressOnce = false
                                    updateNotifyColdStartHandled = true
                                } else if (coldStart || awayLong) {
                                    updateNotifyColdStartHandled = true
                                    com.devxrohan.smusic.update.AppNotificationManager.notifyPendingUpdateOnAppOpen(this@MainActivity)
                                }
                                // Poll announcements only while the activity is in the foreground.
                                foregroundPollJob?.cancel()
                                foregroundPollJob = lifecycleOwner.lifecycleScope.launch {
                                    while (true) {
                                        kotlinx.coroutines.delay(60 * 1000L)
                                        runCheck()
                                    }
                                }
                            }
                            androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                                foregroundPollJob?.cancel()
                                foregroundPollJob = null
                            }
                            androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                                updateNotifyLastStopAt = System.currentTimeMillis()
                            }
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        foregroundPollJob?.cancel()
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }
                val suppressUpdateDialog by updateViewModel.updateManager.suppressGlobalDialog.collectAsState()

                val isFreshInstall = remember { this@MainActivity.isFreshInstall() }
                val (onboardingCompleted, onOnboardingCompletedChange) = rememberPreference(
                    com.devxrohan.smusic.constants.OnboardingCompletedKey,
                    defaultValue = false
                )
                var onboardingShouldShow by remember { mutableStateOf<Boolean?>(null) }

                // Persist the onboarding decision once; package update timestamps must not change it.
                androidx.compose.runtime.LaunchedEffect(isFreshInstall) {
                    var shouldShow = false
                    this@MainActivity.dataStore.edit { preferences ->
                        if (preferences[com.devxrohan.smusic.constants.OnboardingDetectionInitializedKey] != true) {
                            if (!isFreshInstall && preferences[com.devxrohan.smusic.constants.OnboardingCompletedKey] != true) {
                                // Migration for users who installed SMusic before this
                                // onboarding gate existed. Never inspect database size.
                                preferences[com.devxrohan.smusic.constants.OnboardingCompletedKey] = true
                            }
                            preferences[com.devxrohan.smusic.constants.OnboardingDetectionInitializedKey] = true
                        }
                        shouldShow = preferences[com.devxrohan.smusic.constants.OnboardingCompletedKey] != true
                    }
                    onboardingShouldShow = shouldShow
                }

                val completeOnboarding: () -> Unit = {
                    onOnboardingCompletedChange(true)
                    onboardingShouldShow = false
                }

                // Defer update dialogs until onboarding finishes, while allowing release metadata to load.
                androidx.compose.runtime.LaunchedEffect(onboardingCompleted) {
                    // Fresh install: the popup waits for onboarding, then shows immediately.
                    if (onboardingCompleted) {
                        updateViewModel.checkSilently(force = true)
                    }
                }

                // Keep the onboarding surface over Home until the login destination is
                // actually active. This prevents the Home screen flashing between taps/back.
                val navController = rememberNavController()
                val onboardingNavEntry by navController.currentBackStackEntryAsState()
                val onboardingNavRoute = onboardingNavEntry?.destination?.route

                if (onboardingShouldShow == true && !(onboardingNavRoute == "youtube_login" && onboardingLoginDestinationReady)) {
                    // The onboarding screen owns its normal Compose controls and pager.
                    // This layer only draws the onboarding surface above Home; it must
                    // not install a parent click/pointer interceptor.
                    androidx.activity.compose.BackHandler(enabled = true) { }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1000f)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        com.devxrohan.smusic.ui.screens.OnboardingScreen(
                            onFinished = completeOnboarding,
                            currentStep = onboardingStep,
                            onStepChange = { onboardingStep = it },
                            onAddAccount = {
                                // Keep onboarding pending while login is in progress.
                                // The onboarding layer is hidden temporarily so the login
                                // route is visible immediately; cancel returns to onboarding.
                                onboardingLoginFlowActive = true
                                onboardingLoginScreenOpened = false
                                onboardingLoginDestinationReady = false
                                // Navigate synchronously from the tap. Deferring this to a
                                // LaunchedEffect allowed the underlying Home destination to
                                // render for a frame before the login route was pushed.
                                navController.navigate("youtube_login") {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }

                // Installs that predate the onboarding gate were asked for notifications at launch before;
                // that prompt moved, so ask them once here (never during onboarding, never again).
                androidx.compose.runtime.LaunchedEffect(onboardingCompleted) {
                    if (onboardingCompleted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        val asked = this@MainActivity.dataStore.data
                            .map { it[com.devxrohan.smusic.constants.NotificationPermissionAskedKey] ?: false }
                            .first()
                        if (!asked) {
                            this@MainActivity.dataStore.edit {
                                it[com.devxrohan.smusic.constants.NotificationPermissionAskedKey] = true
                            }
                            ActivityCompat.requestPermissions(
                                this@MainActivity,
                                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                1
                            )
                        }
                    }
                }

                if (onboardingCompleted && !suppressUpdateDialog) {
                    com.devxrohan.smusic.ui.screens.settings.UpdateDownloadDialog(updateViewModel)
                }

                NameProvider(
                        namePreferenceManager = namePreferenceManager
                    ) {
                        BoxWithConstraints(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Transparent),
                        )
                        {
                            val focusManager = LocalFocusManager.current
                            val density = LocalDensity.current
                            val windowsInsets = WindowInsets.systemBars
                            val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                            val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            LaunchedEffect(navBackStackEntry?.destination?.route, onboardingLoginFlowActive, onboardingYouTubeCookie) {
                                val route = navBackStackEntry?.destination?.route
                                if (onboardingLoginFlowActive && route == "youtube_login") {
                                    onboardingLoginScreenOpened = true
                                } else if (onboardingLoginFlowActive && onboardingLoginScreenOpened && route != null) {
                                    // Returning from login: finish onboarding only when a YouTube
                                    // session was actually saved. Back/cancel restores onboarding.
                                    if (onboardingYouTubeCookie.isNotBlank()) {
                                        completeOnboarding()
                                    }
                                    onboardingLoginFlowActive = false
                                    onboardingLoginScreenOpened = false
                                }
                            }
                            val previousTopLevelRoute = rememberSaveable { mutableStateOf(Screens.Home.route) }

                            val navigationItems = remember {
                                listOf(Screens.Home, Screens.Search, Screens.Library, Screens.Settings)
                            }
                            fun topLevelRouteIndex(route: String?): Int {
                                if (route == null) return -1
                                val normalized = if (route.startsWith("search/")) Screens.Search.route else route
                                return navigationItems.indexOfFirst { it.route == normalized }
                            }

                            fun isTopLevelRoute(route: String?): Boolean =
                                topLevelRouteIndex(route) >= 0
                            val (hideBottomNavigation) = rememberPreference(SlimNavBarKey, defaultValue = false)
                            val (reduceAnimations) = rememberPreference(
                                com.devxrohan.smusic.constants.DisableAnimationsKey,
                                defaultValue = false
                            )
                            val defaultOpenTab = NavigationTab.HOME
                            val tabOpenedFromShortcut =
                                remember {
                                    when (intent?.action) {
                                        ACTION_LIBRARY -> NavigationTab.LIBRARY
                                        ACTION_EXPLORE -> NavigationTab.EXPLORE
                                        else -> null
                                    }
                                }

                            val topLevelScreens =
                                listOf(
                                    Screens.Home.route,
                                    Screens.Search.route,
                                    Screens.Library.route,
                                    "settings",
                                )

                            val (query, onQueryChange) =
                                rememberSaveable(stateSaver = TextFieldValue.Saver) {
                                    mutableStateOf(TextFieldValue())
                                }

                            var active by rememberSaveable {
                                mutableStateOf(false)
                            }

                            val onActiveChange: (Boolean) -> Unit = { newActive ->
                                active = newActive
                                if (!newActive) {
                                    focusManager.clearFocus()
                                    if (isTopLevelRoute(navBackStackEntry?.destination?.route)) {
                                        onQueryChange(TextFieldValue())
                                    }
                                }
                            }

                            var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)

                            val searchBarFocusRequester = remember { FocusRequester() }

                            val onSearch: (String) -> Unit = {
                                if (it.isNotEmpty()) {
                                    onActiveChange(false)
                                    navController.navigate("search/${URLEncoder.encode(it, "UTF-8")}")
                                    // Recent searches are always local (same in guest and account mode).
                                    if (dataStore[PauseSearchHistoryKey] != true) {
                                        database.query { insert(SearchHistory(query = it.trim())) }
                                    }
                                }
                            }

                            var openSearchImmediately: Boolean by remember {
                                mutableStateOf(intent?.action == ACTION_SEARCH)
                            }

                            val shouldShowSearchBar =
                                remember(active, navBackStackEntry) {
                                    active ||
                                            isTopLevelRoute(navBackStackEntry?.destination?.route)
                                }

                            val navBarScaleForInsets by rememberPreference(
                                com.devxrohan.smusic.constants.NavBarScaleKey,
                                defaultValue = 1.11f
                            )
                            val (navBarStyle) = rememberEnumPreference(
                                com.devxrohan.smusic.constants.NavBarStyleKey,
                                defaultValue = NavBarStyle.FLOATING
                            )
                            // Reserve navigation and system insets separately so the mini-player clears
                            // either navigation style.
                            val floatingBarRowHeight = if (hideBottomNavigation) 56.dp else 70.dp
                            val navBarReservedHeight = if (navBarStyle == NavBarStyle.WITHOUT_FLOATING) {
                                64.dp
                            } else {
                                14.dp + floatingBarRowHeight * ((1f + navBarScaleForInsets) / 2f)
                            }

                            // Keep base navigation visibility independent of mini-player state and sheet bounds.
                            val currentDestinationRoute = navBackStackEntry?.destination?.route
                            val isAuthScreen = currentDestinationRoute == "onboarding" ||
                                currentDestinationRoute == "guest_profile_setup" ||
                                currentDestinationRoute == "login" ||
                                currentDestinationRoute == "account" ||
                                currentDestinationRoute?.startsWith("login/") == true
                            val shouldShowNavigationBarForPlayer =
                                !isAuthScreen && isTopLevelRoute(currentDestinationRoute)

                            // The mini player is available on every page (tabs, library / settings sub-pages, artist,
                            // album and playlist pages, ...) except the search tab / search results and the
                            // auth / login screens.
                            val isSearchRoute = currentDestinationRoute == Screens.Search.route ||
                                currentDestinationRoute?.startsWith("search/") == true
                            val miniPlayerAllowed = currentDestinationRoute != null &&
                                !isAuthScreen &&
                                !isSearchRoute &&
                                currentDestinationRoute != "youtube_login"
                            val miniPlayerAllowedState = rememberUpdatedState(miniPlayerAllowed)

                            val playerBottomSheetState =
                                rememberBottomSheetState(
                                    dismissedBound = 0.dp,
                                    // The collapsed bound always reserves the navigation bar height so the sheet
                                    // anchors stay stable. On pages without the nav bar the mini player is slid down
                                    // by that amount (miniPlayerNavShift) so it rests at the bottom edge.
                                    collapsedBound = bottomInset + navBarReservedHeight + MiniPlayerHeight,
                                    expandedBound = maxHeight,
                                )

                            // Top-level navigation is normally visible, but when a mini player
                            // is showing it must stay visible on every route as well.
                            // Expanding the mini-player must never bring navigation onto nested/detail pages.
                            val shouldShowNavigationBar = shouldShowNavigationBarForPlayer

                            // The mini player lives on every page. Where the navigation bar is hidden it sits at the
                            // bottom edge; the collapsed bound always reserves the nav height, so slide the mini
                            // player down by that amount (and back up) with the same motion as the bar.
                            val miniPlayerNavShift by animateDpAsState(
                                // Docked: the 72dp player sits at the top of its 80dp slot, so slide the extra 8dp as
                                // well and it ends up flush on the bottom edge instead of floating above an empty strip.
                                targetValue = if (shouldShowNavigationBar) 0.dp
                                else if (navBarStyle == NavBarStyle.WITHOUT_FLOATING) navBarReservedHeight + 8.dp
                                else navBarReservedHeight,
                                animationSpec = if (reduceAnimations) snap() else tween(durationMillis = 320, easing = FastOutSlowInEasing),
                                label = "miniPlayerNavShift"
                            )

                            val playerAwareWindowInsets =
                                remember(
                                    bottomInset,
                                    shouldShowNavigationBar,
                                    navBarReservedHeight,
                                    navBarStyle,
                                    playerBottomSheetState.isDismissed
                                ) {
                                    var bottom = bottomInset
                                    val isDocked = navBarStyle == NavBarStyle.WITHOUT_FLOATING
                                    if (shouldShowNavigationBar) bottom += navBarReservedHeight
                                    if (!playerBottomSheetState.isDismissed) {
                                        bottom += MiniPlayerHeight
                                    } else if (isDocked && shouldShowNavigationBar) {
                                        // The docked surface is 72dp tall, but 64dp is reserved so the mini player
                                        // (72dp when docked) lines up with it. With no mini player the last 8dp of
                                        // content used to hide behind the bar, so add them back here.
                                        bottom += DockedNavBarSurfaceHeight - navBarReservedHeight
                                    }
                                    // Small breathing room so the last item never touches the docked bar.
                                    // This is padding INSIDE the scroll area (contentPadding), so the list still
                                    // scrolls all the way down behind the bar; no empty strip is left under it.
                                    if (isDocked && shouldShowNavigationBar) bottom += DockedContentGap
                                    windowsInsets
                                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                                        .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                                }

                            appBarScrollBehavior(
                                canScroll = {
                                    navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                                }
                            )

                            val searchBarScrollBehavior =
                                appBarScrollBehavior(
                                    canScroll = {
                                        navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                                (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                                    },
                                )
                            val topAppBarScrollBehavior =
                                appBarScrollBehavior(
                                    canScroll = {
                                        navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                                (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                                    },
                                )

                            LaunchedEffect(navBackStackEntry) {
                                if (navBackStackEntry?.destination?.route?.startsWith("search/") == true) {
                                    val query = navBackStackEntry?.arguments?.getString("query") ?: return@LaunchedEffect
                                    val searchQuery =
                                        withContext(Dispatchers.IO) {
                                            if (query.contains("%")) {
                                                query
                                            } else {
                                                URLDecoder.decode(query, "UTF-8")
                                            }
                                        }
                                    onQueryChange(
                                        TextFieldValue(
                                            searchQuery,
                                            TextRange(searchQuery.length)
                                        )
                                    )
                                } else if (isTopLevelRoute(navBackStackEntry?.destination?.route)) {
                                    onQueryChange(TextFieldValue())
                                }
                                searchBarScrollBehavior.state.resetHeightOffset()
                                topAppBarScrollBehavior.state.resetHeightOffset()
                            }
                            LaunchedEffect(active) {
                                if (active) {
                                    searchBarScrollBehavior.state.resetHeightOffset()
                                    topAppBarScrollBehavior.state.resetHeightOffset()
                                    searchBarFocusRequester.requestFocus()
                                }
                            }

                            LaunchedEffect(playerConnection) {
                                val player = playerConnection?.player ?: return@LaunchedEffect
                                if (player.currentMediaItem == null) {
                                    if (!playerBottomSheetState.isDismissed) {
                                        playerBottomSheetState.dismiss()
                                    }
                                } else {
                                    if (playerBottomSheetState.isDismissed && miniPlayerAllowedState.value) {
                                        playerBottomSheetState.collapseSoft()
                                    }
                                }
                            }

                            // Hide the player sheet only where the mini player is not wanted (search, login screens)
                            // without stopping playback; bring the collapsed mini player back everywhere else.
                            LaunchedEffect(currentDestinationRoute, playerConnection) {
                                val player = playerConnection?.player
                                if (!miniPlayerAllowed) {
                                    if (!playerBottomSheetState.isDismissed) {
                                        playerBottomSheetState.dismiss()
                                    }
                                } else if (player?.currentMediaItem != null && playerBottomSheetState.isDismissed) {
                                    playerBottomSheetState.collapseSoft()
                                }
                            }

                            DisposableEffect(playerConnection, playerBottomSheetState) {
                                val player =
                                    playerConnection?.player ?: return@DisposableEffect onDispose { }
                                val listener =
                                    object : Player.Listener {
                                        override fun onMediaItemTransition(
                                            mediaItem: MediaItem?,
                                            reason: Int,
                                        ) {
                                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                                mediaItem != null &&
                                                miniPlayerAllowedState.value &&
                                                playerBottomSheetState.isDismissed
                                            ) {
                                                playerBottomSheetState.collapseSoft()
                                            }
                                        }
                                    }
                                player.addListener(listener)
                                onDispose {
                                    player.removeListener(listener)
                                }
                            }

                            var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                            LaunchedEffect(navBackStackEntry) {
                                shouldShowTopBar =
                                    !active && navBackStackEntry?.destination?.route in topLevelScreens && navBackStackEntry?.destination?.route != "settings"
                            }

                            val coroutineScope = rememberCoroutineScope()
                            var sharedSong: SongItem? by remember {
                                mutableStateOf(null)
                            }
                            DisposableEffect(Unit) {
                                val listener =
                                    Consumer<Intent> { intent ->
                                        // Taps on the update / announcement notifications.
                                        intent.getStringExtra("navigate_to")?.let { destination ->
                                            intent.removeExtra("navigate_to")
                                            when {
                                                destination == "settings/update" -> navController.navigate(com.devxrohan.smusic.ui.screens.settings.CheckForUpdateRoute)
                                                destination == "announcement" -> navController.navigate(com.devxrohan.smusic.ui.screens.settings.AnnouncementRoute)
                                                // Hand-off from SharedMusicIdentificationActivity (Find Music via share).
                                                destination.startsWith("search/") -> navController.navigate(destination)
                                            }
                                            return@Consumer
                                        }
                                        if (ExternalPlayerUtil.isAudioIntent(intent, this@MainActivity)) {
                                            val audioUri = ExternalPlayerUtil.getAudioUri(intent)
                                            if (audioUri != null) {
                                                coroutineScope.launch {
                                                    while (playerConnection == null) {
                                                        delay(50)
                                                    }
                                                    playerConnection?.let { pc ->
                                                        ExternalPlayerUtil.playExternalAudio(pc, this@MainActivity, audioUri)
                                                        if (playerBottomSheetState.isDismissed) {
                                                            playerBottomSheetState.collapseSoft()
                                                        }
                                                    }
                                                }
                                                return@Consumer
                                            }
                                        }

                                        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)
                                            ?.toUri() ?: return@Consumer
                                        when (val path = uri.pathSegments.firstOrNull()) {
                                            "playlist" ->
                                                (uri.getQueryParameter("id") ?: uri.getQueryParameter("list"))?.let { playlistId ->
                                                    if (playlistId.startsWith("OLAK5uy_")) {
                                                        coroutineScope.launch {
                                                            YouTube
                                                                .albumSongs(playlistId)
                                                                .onSuccess { songs ->
                                                                    songs.firstOrNull()?.album?.id?.let { browseId ->
                                                                        navController.navigate("album/$browseId")
                                                                    }
                                                                }.onFailure {
                                                                    reportException(it)
                                                                }
                                                        }
                                                    } else {
                                                        navController.navigate("online_playlist/$playlistId")
                                                    }
                                                }

                                            "browse" ->
                                                uri.lastPathSegment?.let { browseId ->
                                                    navController.navigate("album/$browseId")
                                                }

                                            "channel", "c" ->
                                                uri.lastPathSegment?.let { artistId ->
                                                    navController.navigate("artist/$artistId")
                                                }

                                            "artist" ->
                                                uri.getQueryParameter("id")?.let { artistId ->
                                                    navController.navigate("artist/$artistId")
                                                }

                                            else ->
                                                when {
                                                    path == "watch" -> uri.getQueryParameter("v")
                                                    uri.host == "youtu.be" -> path
                                                    uri.host == "play.smusic.app" && path == "song" -> uri.getQueryParameter("id")
                                                    uri.host == "play.smusic.app" -> path
                                                    else -> null
                                                }?.let { videoId ->
                                                    coroutineScope.launch {
                                                        withContext(Dispatchers.IO) {
                                                            YouTube.queue(listOf(videoId))
                                                        }.onSuccess {
                                                            playerConnection?.playQueue(
                                                                YouTubeQueue(
                                                                    WatchEndpoint(videoId = it.firstOrNull()?.id),
                                                                    it.firstOrNull()?.toMediaMetadata()
                                                                )
                                                            )
                                                        }.onFailure {
                                                            reportException(it)
                                                        }
                                                    }
                                                }
                                        }
                                    }

                                addOnNewIntentListener(listener)
                                intent?.let { initialIntent ->
                                    coroutineScope.launch {
                                        while (navController.currentDestination == null) {
                                            delay(50)
                                        }
                                        listener.accept(initialIntent)
                                    }
                                }
                                onDispose { removeOnNewIntentListener(listener) }
                            }

                            val currentTitle = remember(navBackStackEntry) {
                                when (navBackStackEntry?.destination?.route) {
                                    Screens.Home.route -> R.string.home
                                    Screens.Explore.route -> R.string.explore
                                    Screens.Library.route -> R.string.filter_library
                                    else -> null
                                }
                            }
                            val baseBg = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
                            val insetBg = if (playerBottomSheetState.progress > 0f) Color.Transparent else baseBg

                            CompositionLocalProvider(
                                LocalDatabase provides database,
                                LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.surface),
                                LocalPlayerConnection provides playerConnection,
                                LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                                LocalDownloadUtil provides downloadUtil,
                                LocalShimmerTheme provides ShimmerTheme,
                                LocalSyncUtils provides syncUtils,
                                LocalBackdrop provides backdrop,
                            ) {

                                Scaffold(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                                        .background(MaterialTheme.colorScheme.surface),
                                    containerColor = Color.Transparent,
                                    topBar = {},
                                    bottomBar = {
                                        Box {

                                            val currentRoute = navBackStackEntry?.destination?.route

                                            val isTopLevel =
                                                currentRoute == Screens.Home.route ||
                                                        currentRoute == Screens.Explore.route ||
                                                        currentRoute == Screens.Library.route

                                            val isAuthScreen = currentRoute == "onboarding" ||
                                                currentRoute == "guest_profile_setup" ||
                                                currentRoute == "login" || currentRoute == "account" ||
                                                currentRoute?.startsWith("login/") == true

                                            // The mini/full player sheet is available on every page except the
                                            // auth / login screens. It rests above the nav bar on the primary tabs
                                            // and at the very bottom everywhere else (see miniPlayerNavShift).
                                            com.devxrohan.smusic.ui.theme.PlayerIdentityTheme(useDarkTheme, pureBlack) {
                                            if (miniPlayerAllowed) {
                                                BottomSheetPlayer(
                                                    state = playerBottomSheetState,
                                                    navController = navController,
                                                    onOpenFullscreenLyrics = {
                                                        showFullscreenLyrics = true
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .offset(y = 0.dp),
                                                    docked = navBarStyle == NavBarStyle.WITHOUT_FLOATING,
                                                    collapsedOffsetY = { miniPlayerNavShift },
                                                    dockedBottomBleed = {
                                                        if (navBarStyle == NavBarStyle.WITHOUT_FLOATING) {
                                                            (miniPlayerNavShift / (navBarReservedHeight + 8.dp)).coerceIn(0f, 1f)
                                                        } else 0f
                                                    },
                                                )
                                            }
                                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                                val lyricsBottomSheetState = rememberBottomSheetState(
                                                    dismissedBound = 0.dp,
                                                    expandedBound = with(androidx.compose.ui.platform.LocalDensity.current) { constraints.maxHeight.toDp() },
                                                )

                                            LaunchedEffect(showFullscreenLyrics) {
                                                if (showFullscreenLyrics) {
                                                    lyricsBottomSheetState.expandSoft()
                                                } else {
                                                    lyricsBottomSheetState.collapseSoft()
                                                }
                                            }

                                            LaunchedEffect(lyricsBottomSheetState.isCollapsed) {
                                                if (lyricsBottomSheetState.isCollapsed && showFullscreenLyrics) {
                                                    showFullscreenLyrics = false
                                                }
                                            }

                                            BottomSheet(
                                                state = lyricsBottomSheetState,
                                                modifier = Modifier.fillMaxSize(),
                                                background = {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(MaterialTheme.colorScheme.background.copy(alpha = lyricsBottomSheetState.progress.coerceIn(0f, 1f)))
                                                    )
                                                },
                                                collapsedContent = {},
                                            ) {
                                                val playerConnection = LocalPlayerConnection.current
                                                val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState()
                                                    ?: return@BottomSheet

                                                if (mediaMetadata != null) {
                                                    com.devxrohan.smusic.ui.theme.PlayerIdentityTheme(darkTheme = true, pureBlack = pureBlack) {
                                                    if (playerScreenStyle == PlayerScreenStyle.SPOTIFY) {
                                                        SpotifyLyrics(
                                                            onNavigateBack = {
                                                                lyricsBottomSheetState.collapseSoft()
                                                            },
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        com.devxrohan.smusic.ui.player.SMusicLyricsScreen(
                                                            mediaMetadata = mediaMetadata!!,
                                                            navController = navController,
                                                            onBackClick = {
                                                                lyricsBottomSheetState.collapseSoft()
                                                            },
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                    }
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(MaterialTheme.colorScheme.background),
                                                        contentAlignment = Alignment.Center
                                                    ) {}
                                                }
                                                }
                                            }

                                            }

                                            val configuration = LocalConfiguration.current
                                            val isTabletLandscape = configuration.screenWidthDp >= 600 &&
                                                    configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                                            val shouldShowBottomNav =
                                                shouldShowNavigationBar &&
                                                        playerBottomSheetState.progress < 0.95f

                                            val navSlidePx = with(LocalDensity.current) {
                                                (navBarReservedHeight + bottomInset).roundToPx()
                                            }
                                            AnimatedVisibility(
                                                visible = shouldShowBottomNav,
                                                enter = if (reduceAnimations) androidx.compose.animation.EnterTransition.None
                                                else slideInVertically(
                                                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                                                    initialOffsetY = { navSlidePx }
                                                ) + fadeIn(animationSpec = tween(durationMillis = 220)),
                                                exit = if (reduceAnimations) androidx.compose.animation.ExitTransition.None
                                                else slideOutVertically(
                                                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                                                    targetOffsetY = { navSlidePx }
                                                ) + fadeOut(animationSpec = tween(durationMillis = 180)),
                                            ) {

                                                val offsetY = 0.dp
                                                val alpha = 1f
                                                val navBarScale by rememberPreference(
                                                    com.devxrohan.smusic.constants.NavBarScaleKey,
                                                    defaultValue = 1.11f
                                                )
                                                // Render navigation against a consistent backdrop in light and
                                                // dark themes.

                                                val surfaceColor = MaterialTheme.colorScheme.surface
                                                val onSurfaceColor = MaterialTheme.colorScheme.onSurface

                                                val curvedItems = navigationItems.map { screen ->
                                                    CurvedBottomNavigationItem(
                                                        iconInactive = screen.iconIdInactive,
                                                        iconActive = screen.iconIdActive,
                                                        titleId = screen.titleId
                                                    )
                                                }

                                                val currentRoute = navBackStackEntry?.destination?.route
                                                // While the bar slides out on a detail page keep the last tab highlighted.
                                                val selectedIndex = topLevelRouteIndex(currentRoute).takeIf { it >= 0 }
                                                    ?: topLevelRouteIndex(previousTopLevelRoute.value).takeIf { it >= 0 }
                                                    ?: 0

                                                LaunchedEffect(currentRoute) {
                                                    if (topLevelRouteIndex(currentRoute) >= 0) {
                                                        previousTopLevelRoute.value = currentRoute ?: Screens.Home.route
                                                    }
                                                }

                                                var lastTapTime by remember { mutableLongStateOf(0L) }
                                                var lastTappedIcon by remember { mutableStateOf<Int?>(null) }
                                                var navigateToExplore by remember { mutableStateOf(false) }

                                                val onItemSelectedAction: (Int) -> Unit = { index ->
                                                     val screen = navigationItems[index]
                                                     val isSelected = index == selectedIndex

                                                     val currentTapTime = System.currentTimeMillis()
                                                     val timeSinceLastTap = currentTapTime - lastTapTime
                                                     val isDoubleTap =
                                                         screen.titleId == R.string.explore &&
                                                                 lastTappedIcon == R.string.explore &&
                                                                 timeSinceLastTap < 300L

                                                     lastTapTime = currentTapTime
                                                     lastTappedIcon = screen.titleId

                                                     if (screen.titleId == R.string.explore) {
                                                         if (isDoubleTap) {
                                                             navController.navigate(Screens.SearchRoute()) {
                                                                 launchSingleTop = true
                                                                 restoreState = true
                                                             }
                                                             navigateToExplore = false
                                                         } else {
                                                             navigateToExplore = true
                                                             coroutineScope.launch {
                                                                 delay(300L)
                                                                 if (navigateToExplore) {
                                                                     navigateToScreen(navController, screen)
                                                                 }
                                                             }
                                                         }
                                                     } else if (screen == Screens.Search) {
                                                         navController.navigate(Screens.SearchRoute()) {
                                                             popUpTo(navController.graph.startDestinationId) {
                                                                 saveState = true
                                                             }
                                                             launchSingleTop = true
                                                             restoreState = true
                                                         }
                                                     } else {
                                                         if (isSelected) {
                                                             navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                                             coroutineScope.launch {
                                                                 searchBarScrollBehavior.state.resetHeightOffset()
                                                             }
                                                         } else {
                                                             navigateToScreen(navController, screen)
                                                         }
                                                     }
                                                 }

                                                 SMusicBottomNav(
                                                     items = curvedItems,
                                                     selectedIndex = selectedIndex,
                                                     onItemSelected = onItemSelectedAction,
                                                     scale = navBarScale,
                                                     showLabels = !hideBottomNavigation,
                                                     style = navBarStyle,
                                                     modifier = Modifier
                                                         .fillMaxSize()
                                                         .then(
                                                             if (navBarStyle == NavBarStyle.FLOATING) {
                                                                 Modifier.navigationBarsPadding().padding(bottom = 6.dp)
                                                             } else {
                                                                 // Docked: the nav surface reaches the physical bottom edge.
                                                                 // SMusicBottomNav keeps its buttons above the system navigation area.
                                                                 Modifier
                                                             }
                                                         )
                                                         .offset(y = offsetY)
                                                         .alpha(alpha)
                                                 )

                                            }
                                        }
                                    },
                                ) { paddingValues ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .layerBackdrop(backdrop)
                                    ) {
                                    NavHost(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .nestedScroll(
                                                if (isTopLevelRoute(navBackStackEntry?.destination?.route)) {
                                                    searchBarScrollBehavior.nestedScrollConnection
                                                } else {
                                                    topAppBarScrollBehavior.nestedScrollConnection
                                                }
                                            ),
                                        navController = navController,
                                        startDestination = when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                            NavigationTab.HOME -> Screens.Home
                                            NavigationTab.EXPLORE -> Screens.Explore
                                            NavigationTab.LIBRARY -> Screens.Library
                                        }.route,

                                        enterTransition = {
                                            val initialRoute = initialState.destination.route
                                            val targetRoute = targetState.destination.route
                                            if (targetRoute == "youtube_login") {
                                                if (reduceAnimations) androidx.compose.animation.EnterTransition.None
                                                else fadeIn(tween(220)) + slideInHorizontally(
                                                    initialOffsetX = { width -> width / 12 },
                                                    animationSpec = tween(260)
                                                )
                                            } else if (initialRoute == "youtube_login") {
                                                androidx.compose.animation.EnterTransition.None
                                            } else {
                                            val initialIndex = topLevelRouteIndex(initialRoute)
                                            val targetIndex = topLevelRouteIndex(targetRoute)
                                            val isTopLevelSwitch = initialIndex >= 0 && targetIndex >= 0
                                            if (isTopLevelSwitch) {
                                                if (reduceAnimations) androidx.compose.animation.EnterTransition.None
                                                else fadeIn(tween(220)) + slideInHorizontally(
                                                    initialOffsetX = { width -> if (targetIndex > initialIndex) width / 3 else -width / 3 },
                                                    animationSpec = tween(280)
                                                )
                                            } else {
                                                if (reduceAnimations) androidx.compose.animation.EnterTransition.None else fadeIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                                                        slideInHorizontally(
                                                            initialOffsetX = { it },
                                                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                                                        )
                                            }
                                            }
                                        },

                                        exitTransition = {
                                            val initialRoute = initialState.destination.route
                                            val targetRoute = targetState.destination.route
                                            if (initialRoute == "youtube_login" && targetRoute != "youtube_login") {
                                                if (reduceAnimations) androidx.compose.animation.ExitTransition.None
                                                else fadeOut(tween(160)) + slideOutHorizontally(
                                                    targetOffsetX = { width -> width / 12 },
                                                    animationSpec = tween(180)
                                                )
                                            } else if (targetRoute == "youtube_login") {
                                                androidx.compose.animation.ExitTransition.None
                                            } else {
                                            val initialIndex = topLevelRouteIndex(initialRoute)
                                            val targetIndex = topLevelRouteIndex(targetRoute)
                                            val isTopLevelSwitch = initialIndex >= 0 && targetIndex >= 0
                                            if (isTopLevelSwitch) {
                                                if (reduceAnimations) androidx.compose.animation.ExitTransition.None
                                                else fadeOut(tween(180)) + slideOutHorizontally(
                                                    targetOffsetX = { width -> if (targetIndex > initialIndex) -width / 3 else width / 3 },
                                                    animationSpec = tween(240)
                                                )
                                            } else {
                                                if (reduceAnimations) androidx.compose.animation.ExitTransition.None else fadeOut(spring(dampingRatio = Spring.DampingRatioLowBouncy)) +
                                                        slideOutHorizontally(
                                                            targetOffsetX = { -it / 5 },
                                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                        )
                                            }
                                            }
                                        },

                                        popEnterTransition = {
                                            val initialRoute = initialState.destination.route
                                            val targetRoute = targetState.destination.route
                                            if (initialRoute == "youtube_login") {
                                                if (reduceAnimations) androidx.compose.animation.EnterTransition.None
                                                else fadeIn(tween(200)) + slideInHorizontally(
                                                    initialOffsetX = { width -> -width / 12 },
                                                    animationSpec = tween(240)
                                                )
                                            } else if (targetRoute == "youtube_login") {
                                                androidx.compose.animation.EnterTransition.None
                                            } else {
                                            val initialIndex = topLevelRouteIndex(initialRoute)
                                            val targetIndex = topLevelRouteIndex(targetRoute)
                                            val isTopLevelSwitch = initialIndex >= 0 && targetIndex >= 0
                                            if (isTopLevelSwitch) {
                                                if (reduceAnimations) androidx.compose.animation.EnterTransition.None else fadeIn(tween(220)) +
                                                        slideInHorizontally(
                                                            initialOffsetX = { width -> if (targetIndex > initialIndex) width / 3 else -width / 3 },
                                                            animationSpec = tween(280)
                                                        )
                                            } else {
                                                if (reduceAnimations) androidx.compose.animation.EnterTransition.None else fadeIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                                                        slideInHorizontally(
                                                            initialOffsetX = { -it },
                                                            animationSpec = spring(stiffness = Spring.StiffnessLow)
                                                        )
                                            }
                                            }
                                        },

                                        popExitTransition = {
                                            val initialRoute = initialState.destination.route
                                            val targetRoute = targetState.destination.route
                                            if (targetRoute == "youtube_login") {
                                                if (reduceAnimations) androidx.compose.animation.ExitTransition.None
                                                else fadeOut(tween(150)) + slideOutHorizontally(
                                                    targetOffsetX = { width -> -width / 12 },
                                                    animationSpec = tween(180)
                                                )
                                            } else if (initialRoute == "youtube_login") {
                                                androidx.compose.animation.ExitTransition.None
                                            } else {
                                            val initialIndex = topLevelRouteIndex(initialRoute)
                                            val targetIndex = topLevelRouteIndex(targetRoute)
                                            val isTopLevelSwitch = initialIndex >= 0 && targetIndex >= 0
                                            if (isTopLevelSwitch) {
                                                if (reduceAnimations) androidx.compose.animation.ExitTransition.None else fadeOut(tween(180)) +
                                                        slideOutHorizontally(
                                                            targetOffsetX = { width -> if (targetIndex > initialIndex) -width / 3 else width / 3 },
                                                            animationSpec = tween(240)
                                                        )
                                            } else {
                                                if (reduceAnimations) androidx.compose.animation.ExitTransition.None else fadeOut(spring(dampingRatio = Spring.DampingRatioLowBouncy)) +
                                                        slideOutHorizontally(
                                                            targetOffsetX = { it },
                                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                        )
                                            }
                                            }
                                        },

                                    ) {
                                        navigationBuilder(
                                            navController = navController,
                                            scrollBehavior = if (isTopLevelRoute(navBackStackEntry?.destination?.route)
                                            ) searchBarScrollBehavior else topAppBarScrollBehavior,
                                            latestVersionName = latestVersionName,
                                            playerBottomSheetState = playerBottomSheetState,
                                            onboardingLoginReady = { onboardingLoginDestinationReady = true },
                                            onSearchClick = {
                                                navController.navigate(Screens.SearchRoute()) {
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                    }
                                    }
                                }

                                BottomSheetMenu(
                                    state = LocalMenuState.current,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )

                                sharedSong?.let { song ->
                                    playerConnection?.let {
                                        Dialog(
                                            onDismissRequest = { sharedSong = null },
                                            properties = DialogProperties(usePlatformDefaultWidth = false),
                                        ) {
                                            Surface(
                                                modifier = Modifier.padding(24.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                color = AlertDialogDefaults.containerColor,
                                                tonalElevation = AlertDialogDefaults.TonalElevation,
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                ) {
                                                    YouTubeSongMenu(
                                                        song = song,
                                                        navController = navController,
                                                        onDismiss = { sharedSong = null },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            LaunchedEffect(shouldShowSearchBar, openSearchImmediately) {
                                if (shouldShowSearchBar && openSearchImmediately) {
                                    navController.navigate(Screens.SearchRoute()) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                    openSearchImmediately = false
                                }
                            }
                        }
                    }
            }
        }
    }

    private fun navigateToScreen(
        navController: NavHostController,
        screen: Screens
    ) {
        navController.navigate(screen.route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    companion object {
        const val ACTION_SEARCH = "com.devxrohan.smusic.action.SEARCH"
        const val ACTION_EXPLORE = "com.devxrohan.smusic.action.EXPLORE"
        const val ACTION_LIBRARY = "com.devxrohan.smusic.action.LIBRARY"
    }
}
