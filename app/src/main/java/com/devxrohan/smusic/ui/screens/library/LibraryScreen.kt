package com.devxrohan.smusic.ui.screens.library

import com.devxrohan.smusic.ui.utils.bottomSafeArea
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.devxrohan.smusic.ui.component.themeGlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.ui.component.CategoryCard
import com.devxrohan.smusic.ui.component.CategoryCardGrid
import com.devxrohan.smusic.ui.component.CategoryBadgeShape
import androidx.navigation.NavController
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.R
import androidx.compose.ui.res.stringResource

private data class LibraryCategoryItem(
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val route: String
)

/** Library destinations use the same compact category grid as Settings. */
@Composable
fun LibraryScreen(navController: NavController) {
    val items = listOf(
        LibraryCategoryItem(
            title = stringResource(R.string.liked_songs),
            subtitle = stringResource(R.string.category_liked_description),
            iconRes = R.drawable.favorite,
            route = "auto_playlist/liked"
        ),
        LibraryCategoryItem(
            title = stringResource(R.string.playlists),
            subtitle = stringResource(R.string.ui_your_collections),
            iconRes = R.drawable.queue_music,
            route = "library/playlists"
        ),
        LibraryCategoryItem(
            title = stringResource(R.string.history),
            subtitle = stringResource(R.string.ui_recently_played),
            iconRes = R.drawable.history,
            route = "history"
        ),
        LibraryCategoryItem(
            title = stringResource(R.string.ui_downloads),
            subtitle = stringResource(R.string.ui_available_offline),
            iconRes = R.drawable.offline,
            route = "auto_playlist/downloaded"
        ),
        LibraryCategoryItem(
            title = stringResource(R.string.ui_local_music),
            subtitle = stringResource(R.string.ui_on_this_phone),
            iconRes = R.drawable.library_music,
            route = "local_songs"
        ),
        LibraryCategoryItem(
            title = stringResource(R.string.artists),
            subtitle = stringResource(R.string.category_artists_description),
            iconRes = R.drawable.artist,
            route = "library/artists"
        )
    ).let { libraryItems ->
        libraryItems + LibraryCategoryItem(
            title = stringResource(R.string.ui_listening_stats),
            subtitle = stringResource(R.string.category_stats_description),
            iconRes = R.drawable.settings_statistics_rounded,
            route = "stats"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background).themeGlow()
            // Bottom safe area is added inside the scrolling column below, so the cards scroll
            // behind the bottom bar instead of being cut off above an empty strip.
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
            )
    ) {
        // SMusic-style section header
        SMusicSectionHeader(
            title = stringResource(R.string.ui_your_library),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 12.dp)
        )

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryCardGrid(items) { item ->
                CategoryCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.iconRes,
                    onClick = { navController.navigate(item.route) },
                    badgeShape = when (item.route) {
                        "library/playlists", "local_songs" -> CategoryBadgeShape.CLOVER
                        "history", "auto_playlist/downloaded" -> CategoryBadgeShape.SOFT_STAR
                        "stats" -> CategoryBadgeShape.BLOOM
                        else -> CategoryBadgeShape.FLOWER
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Spacer(Modifier.height(16.dp + bottomSafeArea()))
        }
    }
}

@Composable
fun SMusicSectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 12.dp).height(48.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
