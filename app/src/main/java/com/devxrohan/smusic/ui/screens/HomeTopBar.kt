package com.devxrohan.smusic.ui.screens

import java.io.File
import com.devxrohan.smusic.constants.InnerTubeCookieKey
import com.devxrohan.smusic.constants.AccountPhotoUrlKey
import com.devxrohan.smusic.constants.AccountEmailKey
import com.devxrohan.smusic.constants.AccountNameKey
import com.devxrohan.smusic.constants.GuestProfileNameKey
import com.devxrohan.smusic.constants.GuestProfilePhotoPathKey
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import com.devxrohan.smusic.BuildConfig
import com.devxrohan.smusic.checkForUpdates
import com.devxrohan.smusic.isNewerVersion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.devxrohan.smusic.ui.component.AppIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.HomeHeaderStyle
import com.devxrohan.smusic.utils.rememberPreference
import com.devxrohan.smusic.utils.signOutYouTubeAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.clickable

@Composable
fun ModernHomeTopBarInline(
    navController: NavController,
    homeHeaderStyle: HomeHeaderStyle,
    preview: Boolean = false,
) {
    val currentVersion = BuildConfig.VERSION_NAME
    var showUpdateIcon by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val appContext = context.applicationContext

    LaunchedEffect(Unit) {
        if (preview) return@LaunchedEffect
        try {
            val latestVersion = withContext(Dispatchers.IO) { checkForUpdates(appContext) }
            showUpdateIcon = latestVersion?.let { isNewerVersion(it, currentVersion) } ?: false
        } catch (_: Exception) {
            showUpdateIcon = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(if (preview) WindowInsets(0, 0, 0, 0) else WindowInsets.systemBars.only(WindowInsetsSides.Top))
    ) {
        // SMusic-style top bar: wordmark left, action icons right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SMusic wordmark
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.smusic_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Text(
                    text = "S",
                    style = MaterialTheme.typography.headlineMedium,
                    color = androidx.compose.ui.graphics.Color(0xFFE53935),
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Text(
                    text = stringResource(R.string.ui_music_2),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Action buttons — bell shows a badge when there's at least
            // one new developer announcement the person hasn't opened yet.
            val context = LocalContext.current
            val hasUnseenAnnouncement by produceState(initialValue = false) {
                context.dataStore.data.collect {
                    value = com.devxrohan.smusic.update.AppNotificationManager.hasUnseenAnnouncements(context) ||
                        // A pending app update shows the badge until the user has seen it.
                        com.devxrohan.smusic.update.AppNotificationManager.hasUnseenUpdate(context)
                }
            }
            val (accountName, _) = rememberPreference(AccountNameKey, defaultValue = "")
            val (accountEmail, _) = rememberPreference(AccountEmailKey, defaultValue = "")
            val (accountPhotoUrl, _) = rememberPreference(AccountPhotoUrlKey, defaultValue = "")
            val (guestProfileName, _) = rememberPreference(GuestProfileNameKey, defaultValue = "")
            val (guestProfilePhotoPath, _) = rememberPreference(GuestProfilePhotoPathKey, defaultValue = "")
            val (innerTubeCookie, _) = rememberPreference(InnerTubeCookieKey, defaultValue = "")
            val hasYouTubeAccount = remember(innerTubeCookie) {
                innerTubeCookie.isNotBlank() && "SAPISID" in com.devxrohan.smusic.innertube.utils.parseCookieString(innerTubeCookie)
            }
            val profileLabel = if (!hasYouTubeAccount) guestProfileName.ifBlank { "Guest" } else accountName.ifBlank { accountEmail.ifBlank { "YouTube profile" } }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    AppIconButton(modifier = Modifier.size(48.dp), onClick = { navController.navigate("announcement") }) {
                        Icon(painter = painterResource(R.drawable.notifications), contentDescription = stringResource(R.string.notifications), modifier = Modifier.size(23.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (hasUnseenAnnouncement) {
                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(8.dp).background(androidx.compose.ui.graphics.Color(0xFFE53935), CircleShape))
                    }
                }
                AppIconButton(modifier = Modifier.size(48.dp), onClick = { navController.navigate("explore") }) {
                    Icon(painter = painterResource(R.drawable.explore_outlined), contentDescription = stringResource(R.string.explore), modifier = Modifier.size(23.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    AppIconButton(modifier = Modifier.size(48.dp), onClick = { showProfileMenu = true }) {
                        com.devxrohan.smusic.ui.component.ProfileAvatar(
                            model = when {
                                hasYouTubeAccount && accountPhotoUrl.startsWith("https://") -> accountPhotoUrl
                                !hasYouTubeAccount && guestProfilePhotoPath.isNotBlank() -> File(guestProfilePhotoPath)
                                else -> null
                            }, contentDescription = profileLabel, animateFlower = true,
                        )
                    }
                    DropdownMenu(
                        expanded = showProfileMenu,
                        onDismissRequest = { showProfileMenu = false },
                        shape = RoundedCornerShape(24.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        shadowElevation = 10.dp,
                        offset = androidx.compose.ui.unit.DpOffset(0.dp, 8.dp),
                    ) {
                        val accent = MaterialTheme.colorScheme.primary
                        Column(
                            modifier = Modifier
                                .width(288.dp)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Header card: same tinted gradient + 18dp corners as the Home greeting card.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(accent.copy(alpha = 0.14f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f))
                                        )
                                    )
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                com.devxrohan.smusic.ui.component.ProfileAvatar(
                                    model = when {
                                        hasYouTubeAccount && accountPhotoUrl.startsWith("https://") -> accountPhotoUrl
                                        !hasYouTubeAccount && guestProfilePhotoPath.isNotBlank() -> File(guestProfilePhotoPath)
                                        else -> null
                                    }, contentDescription = profileLabel, size = 46.dp,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = profileLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (hasYouTubeAccount) accountEmail.ifBlank { "YouTube Music connected" } else "Guest mode · This device only",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                                if (hasYouTubeAccount) {
                                    Icon(painterResource(R.drawable.check_circle), "Connected", Modifier.size(20.dp), tint = accent)
                                }
                            }

                            @Composable
                            fun ProfileRow(icon: Int, title: String, destructive: Boolean = false, onClick: () -> Unit) {
                                val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable(onClick = onClick)
                                        .padding(horizontal = 10.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    // The YouTube logo keeps its own colours (same as the "Import YouTube" playlist row). Tinting it
                                    // turned the picture into a flat single-colour shape.
                                    val iconTint = when {
                                        icon == R.drawable.youtube_logo -> androidx.compose.ui.graphics.Color.Unspecified
                                        destructive -> tint
                                        else -> accent
                                    }
                                    Icon(painterResource(icon), null, Modifier.size(22.dp), tint = iconTint)
                                    Column(Modifier.weight(1f)) {
                                        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tint)
                                    }
                                    if (!destructive) Icon(painterResource(R.drawable.chevron_right), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            if (!hasYouTubeAccount) {
                                ProfileRow(R.drawable.account, "Edit guest profile") {
                                    showProfileMenu = false
                                    navController.navigate("settings/account")
                                }
                            }
                            if (hasYouTubeAccount) {
                                ProfileRow(R.drawable.account, stringResource(R.string.ui_account_amp_sync)) {
                                    showProfileMenu = false; navController.navigate("account")
                                }
                            } else {
                                ProfileRow(R.drawable.youtube_logo, stringResource(R.string.ui_add_youtube_account)) {
                                    showProfileMenu = false; navController.navigate("login")
                                }
                            }
                            ProfileRow(R.drawable.stats, stringResource(R.string.ui_listening_stats)) {
                                showProfileMenu = false; navController.navigate("stats")
                            }
                            ProfileRow(R.drawable.history, stringResource(R.string.history)) {
                                showProfileMenu = false; navController.navigate("history")
                            }
                            ProfileRow(R.drawable.settings, stringResource(R.string.settings)) {
                                showProfileMenu = false; navController.navigate("settings")
                            }
                            if (hasYouTubeAccount) {
                                HorizontalDivider(Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ProfileRow(R.drawable.logout, stringResource(R.string.ui_sign_out_of_youtube), destructive = true) {
                                    showProfileMenu = false
                                    // Only the account session goes; all songs, likes, history and Pulse data stay.
                                    scope.launch { runCatching { signOutYouTubeAccount(context) } }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (homeHeaderStyle == HomeHeaderStyle.SMUSIC) {
            // SMusic-style greeting card. The greeting is optional and intentionally
            // contains no decorative emoji so the Home layout stays clean.
            val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
            val greeting = when {
                hour < 12 -> "Good Morning"
                hour < 17 -> "Good Afternoon"
                else -> "Good Evening"
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                            )
                        )
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.ui_smusic),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = TextUnit(3f, TextUnitType.Sp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = stringResource(R.string.ui_what_would_you_like_to_listen),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}



@Composable
fun AnimatedBeatsRing(
    isPlaying: Boolean,
    songColors: List<Color>,
    modifier: Modifier = Modifier
) {
    if (!isPlaying) return

    val infiniteTransition = rememberInfiniteTransition(label = "beats_anim")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val barAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar_height"
    )

    Canvas(
        modifier = modifier
            .size(72.dp)
            .graphicsLayer { rotationZ = rotation }
    ) {
        val radius = size.minDimension / 2
        val barCount = 40
        val angleStep = 360f / barCount

        for (i in 0 until barCount) {

            val angle = Math.toRadians((i * angleStep).toDouble())
            val dynamicHeight = radius * barAnim * (0.5f + (i % 5) * 0.1f)

            val startX = center.x + (radius - 12f) * cos(angle).toFloat()
            val startY = center.y + (radius - 12f) * sin(angle).toFloat()

            val endX = center.x + (radius - 12f + dynamicHeight * 0.25f) * cos(angle).toFloat()
            val endY = center.y + (radius - 12f + dynamicHeight * 0.25f) * sin(angle).toFloat()

            drawLine(
                brush = Brush.linearGradient(songColors),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }
    }
}
