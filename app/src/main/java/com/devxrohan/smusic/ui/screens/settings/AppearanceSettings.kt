package com.devxrohan.smusic.ui.screens.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.*
import com.devxrohan.smusic.ui.component.*
import com.devxrohan.smusic.utils.rememberEnumPreference
import com.devxrohan.smusic.utils.rememberPreference
import com.devxrohan.smusic.utils.isLowRamDevice
import me.saket.squiggles.SquigglySlider
import timber.log.Timber
import kotlin.math.roundToInt

// ==================== MAIN APPEARANCE SETTINGS SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (darkMode, onDarkModeChange) = rememberEnumPreference(
        DarkModeKey,
        defaultValue = DarkMode.AUTO
    )

    val (gridItemSize, onGridItemSizeChange) = rememberEnumPreference(
        GridItemsSizeKey,
        defaultValue = GridItemSize.BIG
    )

    val (slimNav, onSlimNavChange) = rememberPreference(SlimNavBarKey, defaultValue = false)

    val (appFontKey, onAppFontKeyChange) = rememberPreference(
        com.devxrohan.smusic.constants.AppFontKey,
        defaultValue = com.devxrohan.smusic.constants.AppFont.LINOTTE.key
    )
    val appFont = remember(appFontKey) { com.devxrohan.smusic.constants.AppFont.fromKey(appFontKey) }

    val (themePresetId, onThemePresetIdChange) = rememberPreference(
        com.devxrohan.smusic.constants.ThemePresetKey,
        defaultValue = com.devxrohan.smusic.ui.theme.ThemePresets.SMUSIC_DEFAULT.id
    )

    val (navBarStyle, onNavBarStyleChange) = rememberEnumPreference(
        com.devxrohan.smusic.constants.NavBarStyleKey,
        defaultValue = com.devxrohan.smusic.constants.NavBarStyle.FLOATING
    )

    val (navBarScale, onNavBarScaleChange) = rememberPreference(
        com.devxrohan.smusic.constants.NavBarScaleKey,
        defaultValue = 1f
    )

    val (homeHeaderStyle, onHomeHeaderStyleChange) = rememberEnumPreference(
        com.devxrohan.smusic.constants.HomeHeaderStyleKey,
        defaultValue = com.devxrohan.smusic.constants.HomeHeaderStyle.SMUSIC
    )

    val (quickPicksCardWidth, onQuickPicksCardWidthChange) = rememberPreference(
        com.devxrohan.smusic.constants.QuickPicksCardWidthKey,
        defaultValue = 196f
    )
    val (quickPicksCardHeight, onQuickPicksCardHeightChange) = rememberPreference(
        com.devxrohan.smusic.constants.QuickPicksCardHeightKey,
        defaultValue = 262f
    )
    var showQuickPicksSizeDialog by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val defaultDisableAnimations = remember(context) { context.isLowRamDevice() }
    val (disableAnimations, onDisableAnimationsChange) = rememberPreference(
        com.devxrohan.smusic.constants.DisableAnimationsKey,
        defaultValue = defaultDisableAnimations
    )
    val (lowDataMode, onLowDataModeChange) = rememberPreference(
        com.devxrohan.smusic.constants.LowDataModeKey,
        defaultValue = false
    )

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = remember(darkMode, isSystemInDarkTheme) {
        if (darkMode == DarkMode.AUTO) isSystemInDarkTheme else darkMode == DarkMode.ON
    }

    if (showQuickPicksSizeDialog) {
        AlertDialog(
            onDismissRequest = { showQuickPicksSizeDialog = false },
            title = { Text(stringResource(R.string.ui_quick_picks_size)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.ui_width_dp, quickPicksCardWidth.roundToInt()))
                    Slider(
                        value = quickPicksCardWidth,
                        onValueChange = { onQuickPicksCardWidthChange(it) },
                        valueRange = 120f..360f
                    )
                    Text(stringResource(R.string.ui_height_dp, quickPicksCardHeight.roundToInt()))
                    Slider(
                        value = quickPicksCardHeight,
                        onValueChange = { onQuickPicksCardHeightChange(it) },
                        valueRange = 180f..440f
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickPicksSizeDialog = false }) { Text(stringResource(R.string.done)) }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.appearance),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = navController::navigateUp) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = innerPadding.calculateTopPadding())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
            ) {
                // Theme Category
                SettingsGeneralCategory(
                    title = stringResource(R.string.theme),
                    items = listOf(
                        {
                            EnumListPreference(
                                title = { Text(stringResource(R.string.dark_theme)) },
                                icon = { Icon(painterResource(R.drawable.dark_mode), null) },
                                selectedValue = darkMode,
                                onValueSelected = onDarkModeChange,
                                valueText = {
                                    when (it) {
                                        DarkMode.ON -> stringResource(R.string.dark_theme_on)
                                        DarkMode.OFF -> stringResource(R.string.dark_theme_off)
                                        DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
                                    }
                                },
                            )
                        },
                        {
                            ListPreference(
                                title = { Text(stringResource(R.string.color_theme)) },
                                icon = { Icon(painterResource(R.drawable.palette), null) },
                                selectedValue = themePresetId,
                                values = com.devxrohan.smusic.ui.theme.ThemePresets.ALL.map { it.id },
                                onValueSelected = { presetId ->
                                    onThemePresetIdChange(presetId)
                                    // A Color Theme also determines whether the app is
                                    // rendered as light or dark. Keep Dark Theme in sync
                                    // so system bars/status-bar icons follow the selected preset.
                                    val selectedPreset = com.devxrohan.smusic.ui.theme.ThemePresets.fromId(presetId)
                                    onDarkModeChange(
                                        if (selectedPreset.isDark) DarkMode.ON else DarkMode.OFF
                                    )
                                },
                                valueText = { presetId ->
                                    com.devxrohan.smusic.ui.theme.ThemePresets.fromId(presetId).displayName
                                },
                            )
                        }
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsGeneralCategory(
                    title = stringResource(R.string.navigation),
                    items = listOf(
                        {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(painterResource(R.drawable.nav_bar), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.nav_bar_size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                        Text("${(navBarScale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Slider(value = navBarScale, onValueChange = onNavBarScaleChange, valueRange = 0.75f..1.25f)
                            }
                        },
                        {
                            EnumListPreference(
                                title = { Text(stringResource(R.string.navigation_bar_style)) },
                                icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                                selectedValue = navBarStyle,
                                onValueSelected = onNavBarStyleChange,
                                valueText = {
                                    when (it) {
                                        com.devxrohan.smusic.constants.NavBarStyle.FLOATING -> stringResource(R.string.navigation_bar_style_floating)
                                        com.devxrohan.smusic.constants.NavBarStyle.WITHOUT_FLOATING -> stringResource(R.string.navigation_bar_style_docked)
                                    }
                                },
                            )
                        },
                        {
                            SwitchPreference(
                                title = { Text(stringResource(R.string.slim_navbar)) },
                                description = stringResource(R.string.slim_navbar_desc),
                                icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                                checked = slimNav,
                                onCheckedChange = onSlimNavChange
                            )
                        }
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsGeneralCategory(
                    title = stringResource(R.string.performance),
                    items = listOf(
                        {
                            SwitchPreference(
                                title = { Text(stringResource(R.string.reduce_animations)) },
                                description = stringResource(R.string.reduce_animations_desc),
                                icon = { Icon(painterResource(R.drawable.speed), null) },
                                checked = disableAnimations,
                                onCheckedChange = onDisableAnimationsChange
                            )
                        },
                        {
                            SwitchPreference(
                                title = { Text(stringResource(R.string.low_data_mode)) },
                                description = stringResource(R.string.low_data_mode_desc),
                                icon = { Icon(painterResource(R.drawable.signal_cellular_nodata), null) },
                                checked = lowDataMode,
                                onCheckedChange = onLowDataModeChange
                            )
                        },
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Misc Category
                SettingsGeneralCategory(
                    title = stringResource(R.string.misc),
                    items = listOf(
                        {
                            EnumListPreference(
                                title = { Text(stringResource(R.string.home_header_style)) },
                                icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                                selectedValue = homeHeaderStyle,
                                onValueSelected = onHomeHeaderStyleChange,
                                valueText = {
                                    when (it) {
                                        com.devxrohan.smusic.constants.HomeHeaderStyle.SMUSIC -> stringResource(R.string.home_header_style_smusic)
                                        com.devxrohan.smusic.constants.HomeHeaderStyle.BITCHORD -> stringResource(R.string.home_header_style_bitchord)
                                        com.devxrohan.smusic.constants.HomeHeaderStyle.NONE -> stringResource(R.string.home_header_style_none)
                                    }
                                }
                            )
                        },
                        {
                            if (homeHeaderStyle == com.devxrohan.smusic.constants.HomeHeaderStyle.NONE) {
                                PreferenceEntry(
                                    title = { Text(stringResource(R.string.ui_quick_picks_size)) },
                                    description = stringResource(R.string.ui_adjust_quick_picks_width_and_height),
                                    icon = { Icon(painterResource(R.drawable.tune), null) },
                                    onClick = { showQuickPicksSizeDialog = true }
                                )
                            }
                        },
                        {
                            EnumListPreference(
                                title = { Text(stringResource(R.string.grid_cell_size)) },
                                icon = { Icon(painterResource(R.drawable.grid_view), null) },
                                selectedValue = gridItemSize,
                                onValueSelected = onGridItemSizeChange,
                                valueText = {
                                    when (it) {
                                        GridItemSize.SMALL -> stringResource(R.string.small)
                                        GridItemSize.BIG -> stringResource(R.string.big)
                                    }
                                },
                            )
                        },
                        {
                            EnumListPreference(
                                title = { Text(stringResource(R.string.app_font)) },
                                icon = { Icon(painterResource(R.drawable.edit), null) },
                                selectedValue = appFont,
                                onValueSelected = { onAppFontKeyChange(it.key) },
                                valueText = { it.title },
                            )
                        }
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    EXPLORE,
    LIBRARY,
}


