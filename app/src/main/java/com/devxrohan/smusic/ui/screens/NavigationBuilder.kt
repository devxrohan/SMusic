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
        com.devxrohan.smusic.ui.screens.search.SearchTabScreen(navController = navController)
    }
    composable("history") {
        HistoryScreen(navController)
    }
    composable("new_release") {
        NewReleaseScreen(navController, scrollBehavior)
    }
    composable("insight") {
        InsightScreen(navController)
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
        LibraryAlbumsScreen(navController, onDeselect = { })
    }
    composable("library/artists") {
        LibraryArtistsScreen(navController, onDeselect = { })
    }
    composable("library/songs") {
        LibrarySongsScreen(navController, onDeselect = { })
    }
    composable("library/playlists") {
        LibraryPlaylistsScreen(navController, filterContent = { })
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

