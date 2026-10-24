package com.devxrohan.smusic.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import com.devxrohan.smusic.ui.component.BottomSheetState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.devxrohan.smusic.BuildConfig
import com.devxrohan.smusic.ui.screens.artist.ArtistItemsScreen
import com.devxrohan.smusic.ui.screens.artist.ArtistScreen
import com.devxrohan.smusic.ui.screens.artist.ArtistSongsScreen
import androidx.compose.ui.res.stringResource
import com.devxrohan.smusic.R
import com.devxrohan.smusic.ui.component.LibraryPage
import com.devxrohan.smusic.ui.screens.library.CachePlaylistScreen
import com.devxrohan.smusic.ui.screens.library.LibraryScreen
import com.devxrohan.smusic.ui.screens.library.LibraryAlbumsScreen
import com.devxrohan.smusic.ui.screens.library.LibraryArtistsScreen
import com.devxrohan.smusic.ui.screens.library.LibrarySongsScreen
import com.devxrohan.smusic.ui.screens.library.LibraryPlaylistsScreen
import com.devxrohan.smusic.ui.screens.playlist.AutoPlaylistScreen
import com.devxrohan.smusic.ui.screens.playlist.LocalPlaylistScreen
import com.devxrohan.smusic.ui.screens.playlist.OnlinePlaylistScreen
import com.devxrohan.smusic.ui.screens.playlist.TopPlaylistScreen
import com.devxrohan.smusic.ui.screens.search.OnlineSearchResult
import com.devxrohan.smusic.ui.screens.settings.AppearanceSettings
import com.devxrohan.smusic.ui.screens.settings.BackupAndRestore
import com.devxrohan.smusic.ui.screens.settings.ContentSettings
import com.devxrohan.smusic.ui.screens.settings.PlayerSettings
import com.devxrohan.smusic.ui.screens.settings.PrivacySettings
import com.devxrohan.smusic.ui.screens.settings.SettingsScreen
import com.devxrohan.smusic.ui.screens.settings.StorageSettings

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
    playerBottomSheetState: BottomSheetState,
    onSearchClick: () -> Unit,
    onboardingLoginReady: () -> Unit = {},
) {
    composable(Screens.Home.route) {
        HomeScreen(navController = navController)
    }

    composable(
        Screens.Library.route,
    ) {
        LibraryScreen(navController)
    }
    composable(Screens.Explore.route) {
        ExploreScreen(navController, scrollBehavior)
    }
    composable(Screens.Search.route) {
        OnlineSearchResult(navController)
    }
    composable("history") {
        HistoryScreen(navController)
    }
    composable("stats") {
        StatsScreen(navController)
    }
    composable("local_songs") {
        com.devxrohan.smusic.ui.screens.library.LocalSongsScreen(navController)
    }
    composable("new_release") {
        NewReleaseScreen(navController, scrollBehavior)
    }
    composable("insight") {
        InsightScreen(navController)
    }
    composable("pulse") {
        PulseScreen(navController)
    }

    composable("generator") {
        com.devxrohan.smusic.ui.screens.generator.GenerateScreen(
            navController = navController,
            onBack = { navController.popBackStack() },
            onNavigateToPlaylist = { playlistId ->
                navController.popBackStack()
                navController.navigate("local_playlist/$playlistId")
            },
        )
    }

    composable(com.devxrohan.smusic.ui.screens.musicrecognition.MusicRecognitionRoute) {
        com.devxrohan.smusic.ui.screens.musicrecognition.MusicRecognitionScreen(navController)
    }






    composable(
        route = "search/{query}",
        arguments =
            listOf(
                navArgument("query") {
                    type = NavType.StringType
                    nullable = false
                    defaultValue = ""
                },
            ),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            if (targetState.destination.route?.startsWith("search/") == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route?.startsWith("search/") == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            fadeOut(tween(200))
        },
    ) {
        OnlineSearchResult(navController)
    }
    composable(
        route = "album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) {
        AlbumScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) { backStackEntry ->
        val artistId = backStackEntry.arguments?.getString("artistId")!!
        if (artistId.startsWith("LA")) {
            ArtistSongsScreen(navController, scrollBehavior)
        } else {
            ArtistScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "artist/{artistId}/songs",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/items?browseId={browseId}&params={params}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        ArtistItemsScreen(navController, scrollBehavior)
    }
    composable(
        route = "online_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        OnlinePlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "local_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        LocalPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "auto_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        AutoPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        CachePlaylistScreen(navController, scrollBehavior)
    }



    composable(
        route = "top_playlist/{top}",
        arguments =
            listOf(
                navArgument("top") {
                    type = NavType.StringType
                },
            ),
    ) {
        TopPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        YouTubeBrowseScreen(navController)
    }


    composable("library/albums") {
        LibraryPage(stringResource(R.string.albums), navController, scrollBehavior) {
            LibraryAlbumsScreen(navController, onDeselect = { })
        }
    }
    composable("library/artists") {
        LibraryPage(stringResource(R.string.artists), navController, scrollBehavior) {
            LibraryArtistsScreen(navController, onDeselect = { })
        }
    }
    composable("library/songs") {
        LibraryPage(stringResource(R.string.songs), navController, scrollBehavior) {
            LibrarySongsScreen(navController, onDeselect = { })
        }
    }
    composable("library/playlists") {
        LibraryPage(stringResource(R.string.playlists), navController, scrollBehavior) {
            LibraryPlaylistsScreen(navController, filterContent = { })
        }
    }
        composable("settings") {
        val latestVersion by mutableLongStateOf(BuildConfig.VERSION_CODE.toLong())
        SettingsScreen(latestVersion, navController, scrollBehavior)
    }
    composable("settings/appearance") {
        AppearanceSettings(navController, scrollBehavior)
    }
    composable("settings/content") {
        ContentSettings(navController, scrollBehavior)
    }
    composable("settings/player") {
        PlayerSettings(navController, scrollBehavior)
    }
    composable("settings/storage") {
        StorageSettings(navController, scrollBehavior)
    }
    composable("settings/privacy") {
        PrivacySettings(navController, scrollBehavior)
    }
    // --- Account integration (ported from SMusic) ---
    composable("settings/account") {
        com.devxrohan.smusic.ui.screens.settings.AccountSettings(navController, scrollBehavior)
    }
    composable("account") {
        com.devxrohan.smusic.ui.screens.AccountScreen(navController, scrollBehavior)
    }
    composable("login") {
        com.devxrohan.smusic.ui.screens.LoginScreen(navController)
    }
    composable("youtube_login") {
        com.devxrohan.smusic.ui.screens.YouTubeLoginScreen(navController, onScreenReady = onboardingLoginReady)
    }
    composable("settings/discord") {
        com.devxrohan.smusic.ui.screens.settings.DiscordSettings(navController, scrollBehavior)
    }
    composable("settings/discord/login") {
        com.devxrohan.smusic.ui.screens.settings.DiscordLoginScreen(navController)
    }
    composable("settings/ai") {
        com.devxrohan.smusic.ui.screens.settings.AiSettings(navController, scrollBehavior)
    }
    composable("settings/voice") {
        com.devxrohan.smusic.ui.screens.settings.VoiceAssistantSettings(navController, scrollBehavior)
    }
    composable("settings/android_auto") {
        com.devxrohan.smusic.ui.screens.settings.AndroidAutoSettings(navController, scrollBehavior)
    }
    composable("settings/backup_restore") {
        BackupAndRestore(navController, scrollBehavior)
    }
    composable(com.devxrohan.smusic.ui.screens.settings.CheckForUpdateRoute) {
        com.devxrohan.smusic.ui.screens.settings.CheckForUpdateScreen(navController)
    }
    composable(com.devxrohan.smusic.ui.screens.settings.AboutRoute) {
        com.devxrohan.smusic.ui.screens.settings.AboutScreen(navController, scrollBehavior)
    }
    composable(com.devxrohan.smusic.ui.screens.settings.DeveloperRoute) {
        com.devxrohan.smusic.ui.screens.settings.DeveloperScreen(navController)
    }
    composable(com.devxrohan.smusic.ui.screens.settings.PreviousVersionsRoute) {
        com.devxrohan.smusic.ui.screens.settings.PreviousVersionsScreen(navController)
    }
    composable(com.devxrohan.smusic.ui.screens.settings.AnnouncementRoute) {
        com.devxrohan.smusic.ui.screens.settings.AnnouncementScreen(navController)
    }
}
