package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets

/**
 * Own full-screen layout for Library pages (Songs, Albums, Artists, Playlists), the same way the
 * Settings pages have one: a real top bar with title + back, the page background, and a content area
 * that starts below the bar.
 *
 * The page content keeps using `LocalPlayerAwareWindowInsets` for its list `contentPadding`; inside this
 * page that local is narrowed to horizontal + bottom only, because the top is already handled by the bar.
 * That gives a safe area at the bottom (clear of the docked/floating nav bar and mini player) while the
 * list still scrolls under the bar instead of ending in an empty strip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryPage(
    title: String,
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val playerInsets = LocalPlayerAwareWindowInsets.current
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SettingsTopAppBar(
                title = title,
                navController = navController,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        CompositionLocalProvider(
            LocalPlayerAwareWindowInsets provides
                playerInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
            ) {
                content()
            }
        }
    }
}
