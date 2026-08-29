package com.devxrohan.smusic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.R

private data class LibraryCategoryItem(
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val iconBg: Color,
    val route: String
)

/**
 * SMusic-style Library — 2-column grid of tinted icon cards,
 * matching SMusic's LibraryScreen layout exactly.
 */
@Composable
fun LibraryScreen(navController: NavController) {
    val items = listOf(
        LibraryCategoryItem(
            title = "Liked songs",
            subtitle = "Your favorites",
            iconRes = R.drawable.favorite,
            iconBg = Color(0xFF633B48),
            route = "auto_playlist/liked"
        ),
        LibraryCategoryItem(
            title = "Playlists",
            subtitle = "Your collections",
            iconRes = R.drawable.queue_music,
            iconBg = Color(0xFF324F34),
            route = "library/playlists"
        ),
        LibraryCategoryItem(
            title = "History",
            subtitle = "Recently played",
            iconRes = R.drawable.history,
            iconBg = Color(0xFF4A3000),
            route = "history"
        ),
        LibraryCategoryItem(
            title = "Downloads",
            subtitle = "Available offline",
            iconRes = R.drawable.offline,
            iconBg = Color(0xFF1A4A5C),
            route = "auto_playlist/downloaded"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top + WindowInsetsSides.Bottom
                )
            )
    ) {
        // SMusic-style section header
        SMusicSectionHeader(title = "Your library")

        BoxWithConstraints(modifier = Modifier.padding(horizontal = 16.dp)) {
            val gap = 14.dp
            val cardSize = (maxWidth - gap) / 2

            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                items.chunked(2).forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowItems.forEach { item ->
                            SMusicLibraryCard(
                                item = item,
                                size = cardSize,
                                onClick = { navController.navigate(item.route) }
                            )
                        }
                        // Fill empty slot in last odd row
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.size(cardSize))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SMusicLibraryCard(
    item: LibraryCategoryItem,
    size: Dp,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .size(size)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Tinted icon box
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(item.iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun SMusicSectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                )
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
