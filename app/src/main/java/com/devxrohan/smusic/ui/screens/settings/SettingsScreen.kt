package com.devxrohan.smusic.ui.screens.settings

import com.devxrohan.smusic.ui.utils.bottomSafeArea
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.devxrohan.smusic.BuildConfig
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.ui.screens.library.SMusicSectionHeader
import com.devxrohan.smusic.R
import com.devxrohan.smusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==================== DIVIDER COMPONENT ====================

@Composable
fun SettingsDivider(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.1f),
    thickness: Dp = 0.5.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}

// ==================== SETTINGS CATEGORY COMPONENTS ====================

data class SettingsCategoryItem(
    val icon: androidx.compose.ui.graphics.painter.Painter,
    val title: @Composable () -> Unit,
    val trailingContent: @Composable (() -> Unit)? = null,
    val onClick: () -> Unit
)

@Composable
fun SettingsCategory(
    title: String,
    items: List<SettingsCategoryItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items.forEachIndexed { index, item ->
                    SettingsCategoryItemContent(
                        item = item,
                        isLast = index == items.lastIndex
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCategoryItemContent(
    item: SettingsCategoryItem,
    isLast: Boolean
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isPressed) 0.98f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isPressed) {
                    Modifier.background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.1f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        )
                    )
                } else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                item.onClick()
                isPressed = false
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Title
            Box(modifier = Modifier.weight(1f)) {
                item.title()
            }

            // Trailing content
            if (item.trailingContent != null) {
                item.trailingContent()
            } else {
                Icon(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (!isLast) {
        SettingsDivider(
            modifier = Modifier.padding(start = 72.dp, end = 16.dp)
        )
    }
}

// ==================== GLASS CARD COMPONENT ====================

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

// ==================== WATER DROP BUTTON COMPONENTS ====================

@Composable
fun WaterDropButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    text: String? = null,
    colors: List<Color> = listOf(
        Color(0xFF6C5CE7),
        Color(0xFFA463F5),
        Color(0xFFC45AF0)
    )
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .scale(if (isPressed) 0.95f else 1f)
            .shadow(
                elevation = if (isPressed) 2.dp else 8.dp,
                shape = RoundedCornerShape(30.dp),
                clip = false
            )
            .background(
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(0f, 0f),
                    end = Offset(100f, 100f)
                ),
                shape = RoundedCornerShape(30.dp)
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.14f),
                        Color.Transparent,
                    ),
                    endY = 60f
                ),
                shape = RoundedCornerShape(30.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
                isPressed = false
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                icon()
                if (text != null) Spacer(modifier = Modifier.width(8.dp))
            }
            if (text != null) {
                Text(
                    text = text,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun WaterDropIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .scale(if (isPressed) 0.9f else 1f)
            .shadow(
                elevation = if (isPressed) 2.dp else 6.dp,
                shape = CircleShape,
                clip = false
            )
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
                CircleShape
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
                isPressed = false
            },
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

// ==================== ORIGINAL FUNCTIONS ====================

@SuppressLint("ObsoleteSdkInt")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun getAppVersion(context: Context): String {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                0
            )
        }
        packageInfo.versionName ?: "Unknown"
    } catch (e: PackageManager.NameNotFoundException) {
        "Unknown"
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun VersionCard(navController: NavController) {
    val context = LocalContext.current
    val appVersion = remember { getAppVersion(context) }

    Spacer(Modifier.height(16.dp))

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.app_info),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                // Version item
                SettingsCategoryItemContent(
                    item = SettingsCategoryItem(
                        icon = painterResource(R.drawable.info),
                        title = {
                            Column {
                                Text(
                                    text = stringResource(R.string.Version),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = appVersion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        },
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.arrow_forward),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        onClick = {
                            navController.navigate(com.devxrohan.smusic.ui.screens.settings.CheckForUpdateRoute)
                        }
                    ),
                    isLast = true
                )
            }
        }
    }
}

@Composable
fun UpdateCard(updateViewModel: com.devxrohan.smusic.update.UpdateViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) {
        updateViewModel.checkSilently()
    }
    UpdateDownloadDialog(updateViewModel = updateViewModel)
}

/** Compact, scrollable update dialog. The dialog itself stays a predictable size even for long changelogs. */
@Composable
fun UpdateDownloadDialog(
    updateViewModel: com.devxrohan.smusic.update.UpdateViewModel = hiltViewModel(),
) {
    val state by updateViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    val info = when (val s = state) {
        is com.devxrohan.smusic.update.UpdateState.Available -> s.info
        is com.devxrohan.smusic.update.UpdateState.Downloading -> s.info
        is com.devxrohan.smusic.update.UpdateState.ReadyToInstall -> s.info
        is com.devxrohan.smusic.update.UpdateState.Failed -> s.info
        com.devxrohan.smusic.update.UpdateState.Idle -> return
    }

    Dialog(onDismissRequest = {
        when (state) {
            // Tapping outside / back = "Later": comes back after a few hours, stays in bell + notification.
            is com.devxrohan.smusic.update.UpdateState.Available,
            is com.devxrohan.smusic.update.UpdateState.ReadyToInstall -> updateViewModel.later()
            is com.devxrohan.smusic.update.UpdateState.Downloading -> updateViewModel.dismiss()
            else -> updateViewModel.dismiss()
        }
    }) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val maxHeight = minOf(maxHeight - 32.dp, 620.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = maxHeight),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 10.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.ui_software_update),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "v${info.versionName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        when (val s = state) {
                            is com.devxrohan.smusic.update.UpdateState.Available -> {
                                Text(
                                    stringResource(R.string.ui_a_new_version_of_smusic_is),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (s.info.changelog.isNotBlank()) {
                                    Spacer(Modifier.height(14.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .verticalScroll(rememberScrollState())
                                            .padding(15.dp),
                                    ) {
                                        Text(stringResource(R.string.whats_new), fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            com.devxrohan.smusic.update.formatReleaseNotes(s.info.changelog),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                            lineHeight = 18.sp,
                                        )
                                    }
                                }
                            }

                            is com.devxrohan.smusic.update.UpdateState.Downloading -> {
                                Text(
                                    stringResource(R.string.ui_downloading_the_update),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(14.dp))
                                LinearProgressIndicator(
                                    progress = { s.progress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(7.dp))
                                Text("${s.progress}%", fontWeight = FontWeight.SemiBold)
                            }

                            is com.devxrohan.smusic.update.UpdateState.ReadyToInstall -> {
                                Text(
                                    stringResource(R.string.ui_the_apk_is_ready_install_it),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            is com.devxrohan.smusic.update.UpdateState.Failed -> {
                                Text(
                                    s.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }

                            com.devxrohan.smusic.update.UpdateState.Idle -> Unit
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                    when (val s = state) {
                        is com.devxrohan.smusic.update.UpdateState.Available -> {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = { updateViewModel.later() },
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(R.string.ui_later)) }
                                TextButton(
                                    onClick = { updateViewModel.startDownload(s.info) },
                                    modifier = Modifier.weight(1f),
                                ) { Text(if (s.info.apkUrl.isBlank()) "Open release" else "Download") }
                            }
                        }
                        is com.devxrohan.smusic.update.UpdateState.Downloading -> {
                            TextButton(
                                onClick = updateViewModel::cancelDownload,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.cancel)) }
                        }
                        is com.devxrohan.smusic.update.UpdateState.ReadyToInstall -> {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = { updateViewModel.later() },
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(R.string.ui_later)) }
                                Button(
                                    onClick = { updateViewModel.install(s.apkFile) },
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(R.string.ui_install_now)) }
                            }
                        }
                        is com.devxrohan.smusic.update.UpdateState.Failed -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { updateViewModel.openReleaseInBrowser(s.info) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.ui_open_in_browser)) }
                                TextButton(
                                    onClick = updateViewModel::dismiss,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.close)) }
                            }
                        }
                        com.devxrohan.smusic.update.UpdateState.Idle -> Unit
                    }
                }
            }
        }
    }
}

// ==================== MAIN SETTINGS SCREEN ====================
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    latestVersion: Long,
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    data class SettingsCard(
        val title: String,
        val subtitle: String,
        val iconRes: Int,
        val iconBg: androidx.compose.ui.graphics.Color,
        val route: String
    )

    val cards = listOf(
        SettingsCard(
            title = stringResource(R.string.account),
            subtitle = stringResource(R.string.ui_youtube_discord),
            iconRes = R.drawable.account,
            iconBg = androidx.compose.ui.graphics.Color(0xFF1B5E20),
            route = "settings/account"
        ),
        SettingsCard(
            title = stringResource(R.string.ai_integration),
            subtitle = stringResource(R.string.ui_playlists_translation),
            iconRes = R.drawable.custom_ai_sparkle,
            iconBg = androidx.compose.ui.graphics.Color(0xFF4A148C),
            route = "settings/ai"
        ),
        SettingsCard(
            title = stringResource(R.string.android_auto),
            subtitle = stringResource(R.string.ui_car_display_and_library_controls),
            iconRes = R.drawable.custom_android_auto,
            iconBg = androidx.compose.ui.graphics.Color(0xFF1A4A5C),
            route = "settings/android_auto"
        ),
        SettingsCard(
            title = stringResource(R.string.voice_assistant),
            subtitle = stringResource(R.string.ui_hands_free_playback_controls),
            iconRes = R.drawable.mic,
            iconBg = androidx.compose.ui.graphics.Color(0xFF00695C),
            route = "settings/voice"
        ),
        SettingsCard(
            title = stringResource(R.string.appearance),
            subtitle = stringResource(R.string.ui_theme_fonts_colors),
            iconRes = R.drawable.palette,
            iconBg = androidx.compose.ui.graphics.Color(0xFF7D5260),
            route = "settings/appearance"
        ),
        SettingsCard(
            title = stringResource(R.string.ui_player_amp_audio),
            subtitle = stringResource(R.string.ui_playback_equalizer),
            iconRes = R.drawable.play,
            iconBg = androidx.compose.ui.graphics.Color(0xFF324F34),
            route = "settings/player"
        ),
        SettingsCard(
            title = stringResource(R.string.content),
            subtitle = stringResource(R.string.ui_language_region),
            iconRes = R.drawable.language,
            iconBg = androidx.compose.ui.graphics.Color(0xFF1A3A5C),
            route = "settings/content"
        ),
        SettingsCard(
            title = stringResource(R.string.storage),
            subtitle = stringResource(R.string.ui_cache_amp_downloads),
            iconRes = R.drawable.custom_storage,
            iconBg = androidx.compose.ui.graphics.Color(0xFF3E4C63),
            route = "settings/storage"
        ),
        SettingsCard(
            title = stringResource(R.string.privacy),
            subtitle = stringResource(R.string.ui_history_security),
            iconRes = R.drawable.security,
            iconBg = androidx.compose.ui.graphics.Color(0xFF633B48),
            route = "settings/privacy"
        ),
        SettingsCard(
            title = stringResource(R.string.backup),
            subtitle = stringResource(R.string.feature_backup_title),
            iconRes = R.drawable.custom_backup,
            iconBg = androidx.compose.ui.graphics.Color(0xFF4A3000),
            route = "settings/backup_restore"
        ),
        SettingsCard(
            title = stringResource(R.string.about),
            subtitle = stringResource(R.string.ui_app_info_developer),
            iconRes = R.drawable.info,
            iconBg = androidx.compose.ui.graphics.Color(0xFF2E2E2E),
            route = com.devxrohan.smusic.ui.screens.settings.AboutRoute
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Bottom safe area is added inside the scrolling column below, so the cards scroll
            // behind the bottom bar instead of being cut off above an empty strip.
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
            )
    ) {
        SMusicSectionHeader(
            title = stringResource(R.string.settings),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 12.dp)
        )

        BoxWithConstraints(modifier = Modifier.padding(horizontal = 16.dp)) {
            val gap = 14.dp
            val cardSize = (maxWidth - gap) / 2

            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                cards.chunked(2).forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowItems.forEach { card ->
                            Column(
                                modifier = Modifier
                                    .size(cardSize)
                                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), clip = false)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { navController.navigate(card.route) }
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(card.iconBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(card.iconRes),
                                        contentDescription = null,
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (card.title == "Voice Assistant") {
                                        Text(
                                            text = stringResource(R.string.ui_beta),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = card.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = card.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.size(cardSize))
                        }
                    }
                }

                // Version card
                VersionCard(navController)

                Spacer(modifier = Modifier.height(16.dp + bottomSafeArea()))
            }
        }
    }
}

