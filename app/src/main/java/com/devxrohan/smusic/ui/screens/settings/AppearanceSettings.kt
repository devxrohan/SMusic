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

    val (navBarScale, onNavBarScaleChange) = rememberPreference(
        com.devxrohan.smusic.constants.NavBarScaleKey,
        defaultValue = 1f
    )

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
                    .padding(innerPadding)
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
                        }
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.color_theme),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(com.devxrohan.smusic.ui.theme.ThemePresets.ALL) { preset ->
                        val selected = preset.id == themePresetId
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(64.dp)
                                .clickable { onThemePresetIdChange(preset.id) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(preset.accent)
                                    .border(
                                        width = if (selected) 3.dp else 0.dp,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(preset.background)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = preset.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.nav_bar_size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Slider(
                        value = navBarScale,
                        onValueChange = onNavBarScaleChange,
                        valueRange = 0.75f..1.25f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(navBarScale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .width(48.dp)
                    )
                }

                SettingsGeneralCategory(
                    title = stringResource(R.string.app_language),
                    items = listOf(
                        { LanguagePreference() }
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
                            SwitchPreference(
                                title = { Text(stringResource(R.string.slim_navbar)) },
                                icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                                checked = slimNav,
                                onCheckedChange = onSlimNavChange
                            )
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


