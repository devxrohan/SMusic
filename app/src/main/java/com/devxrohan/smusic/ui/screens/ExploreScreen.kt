package com.devxrohan.smusic.ui.screens

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.R
import com.devxrohan.smusic.utils.ExplorePrefetcher
import com.devxrohan.smusic.viewmodels.MoodAndGenresViewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.res.stringResource

private val EXPLORE_CARD_SHAPE = RoundedCornerShape(18.dp)
private val EXPLORE_SPACING = 12.dp
private const val EXPLORE_CARD_ASPECT = 1.72f
private const val SKELETON_ROWS = 7

@Composable
fun ExploreScreen(
    navController: NavController,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == ORIENTATION_LANDSCAPE) 3 else 2
    val sections by viewModel.moodAndGenres.collectAsState()
    val artwork by viewModel.artwork.collectAsState()
    // Computed once per data/column change (not on every recomposition). null while loading.
    val rows = remember(sections, columns) {
        sections
            ?.flatMap { it.items }
            ?.distinctBy { it.endpoint.browseId to it.endpoint.params }
            ?.chunked(columns)
    }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            .asPaddingValues(),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
    ) {
        item {
            Text(
                text = stringResource(R.string.explore),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
            )
        }

        if (rows == null) {
            item(key = "explore-skeleton") {
                ExploreSkeleton(columns = columns)
            }
        } else {
            if (rows.isEmpty()) {
                item(key = "explore-empty") {
                    TextButton(
                        onClick = viewModel::retry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.retry),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            itemsIndexed(
                items = rows,
                key = { index, _ -> "explore-row-$index" },
            ) { _, row ->
                // Only the cards that are actually composed (on / next to the screen) ask for artwork.
                LaunchedEffect(row) { viewModel.requestArtwork(row) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EXPLORE_SPACING),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = EXPLORE_SPACING),
                ) {
                    row.forEach { item ->
                        ExploreMoodGenreCard(
                            title = item.title,
                            stripeColor = item.stripeColor,
                            thumbnailUrl = item.thumbnailUrl ?: artwork[viewModel.keyOf(item.endpoint)],
                            modifier = Modifier.weight(1f),
                            onClick = {
                                navController.navigate(
                                    "youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}"
                                )
                            },
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Loading skeleton with the exact geometry of the real Explore grid (same columns, card shape,
 * aspect ratio, padding), so nothing jumps when the data arrives. It is a single soft pulse applied
 * in the layer phase - no library shimmer, no offscreen blend pass, no recomposition per frame.
 */
@Composable
private fun ExploreSkeleton(columns: Int) {
    val transition = rememberInfiniteTransition(label = "exploreSkeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "exploreSkeletonPulse",
    )
    val cardColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    androidx.compose.foundation.layout.Column(
        modifier = Modifier.graphicsLayer { alpha = pulse },
    ) {
        repeat(SKELETON_ROWS) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(EXPLORE_SPACING),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = EXPLORE_SPACING),
            ) {
                repeat(columns) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(EXPLORE_CARD_ASPECT)
                            .clip(EXPLORE_CARD_SHAPE)
                            .background(cardColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreMoodGenreCard(
    title: String,
    stripeColor: Long,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Everything that does not change per frame is computed once per card, not per recomposition.
    val artBrush = remember(stripeColor) {
        val base = Color(stripeColor.toInt())
        val shadow = androidx.compose.ui.graphics.lerp(base, Color.Black, 0.66f)
        val highlight = androidx.compose.ui.graphics.lerp(base, Color.White, 0.28f)
        Brush.linearGradient(0f to shadow, 0.55f to highlight, 1f to shadow)
    }
    val scrim = remember {
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.42f),
            0.58f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.10f),
        )
    }
    val context = LocalContext.current
    val request = remember(thumbnailUrl) {
        thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
            // Same URL the prefetcher already downloaded into the disk cache.
            ImageRequest.Builder(context)
                .data(ExplorePrefetcher.cardImageUrl(url))
                .crossfade(120)
                .build()
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(EXPLORE_CARD_ASPECT)
            .clip(EXPLORE_CARD_SHAPE)
            .background(artBrush)
            .clickable(onClick = onClick),
    ) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrim),
        )
        // No text shadow blur here: it is expensive to draw on every card while scrolling, and the
        // scrim above already keeps the title readable.
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}
