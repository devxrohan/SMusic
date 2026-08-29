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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.constants.*
import com.devxrohan.smusic.ui.component.*
import me.saket.squiggles.SquigglySlider
import timber.log.Timber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AudioNormalizationKey
import com.devxrohan.smusic.constants.AudioQuality
import com.devxrohan.smusic.constants.AudioQualityKey
import com.devxrohan.smusic.constants.AutoLoadMoreKey
import com.devxrohan.smusic.constants.AutoSkipNextOnErrorKey
import com.devxrohan.smusic.constants.PermanentShuffleKey
import com.devxrohan.smusic.constants.PersistentQueueKey
import com.devxrohan.smusic.constants.SimilarContent
import com.devxrohan.smusic.constants.SkipSilenceKey
import com.devxrohan.smusic.constants.StopMusicOnTaskClearKey
import com.devxrohan.smusic.ui.component.EnumListPreference
import com.devxrohan.smusic.ui.component.IconButton
import com.devxrohan.smusic.ui.component.PreferenceGroupTitle
import com.devxrohan.smusic.ui.component.SettingsGeneralCategory
import com.devxrohan.smusic.ui.component.SettingsPage
import com.devxrohan.smusic.ui.component.SwitchPreference
import com.devxrohan.smusic.ui.utils.backToMain
import com.devxrohan.smusic.utils.rememberEnumPreference
import com.devxrohan.smusic.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.AUTO
    )
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(
        PersistentQueueKey,
        defaultValue = true
    )
    val (permanentShuffle, onPermanentShuffleChange) = rememberPreference(
        PermanentShuffleKey,
        defaultValue = false
    )
    val (skipSilence, onSkipSilenceChange) = rememberPreference(
        SkipSilenceKey,
        defaultValue = false
    )
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(
        AudioNormalizationKey,
        defaultValue = true
    )
    val (autoLoadMore, onAutoLoadMoreChange) = rememberPreference(
        AutoLoadMoreKey,
        defaultValue = true
    )
    val (similarContentEnabled, similarContentEnabledChange) = rememberPreference(
        key = SimilarContent,
        defaultValue = true
    )
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) = rememberPreference(
        AutoSkipNextOnErrorKey,
        defaultValue = false
    )
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(
        StopMusicOnTaskClearKey,
        defaultValue = false
    )

    val (playerScreenStyle, onPlayerScreenStyleChange) =
        rememberEnumPreference<PlayerScreenStyle>(
            PlayerScreenStyleKey,
            defaultValue = PlayerScreenStyle.CLASSIC,
        )
    val (colourfullPlayerColor, onColourfullPlayerColorChange) = rememberPreference(
        ColourfullPlayerColorKey,
        defaultValue = 0xFF4CAF50.toInt()
    )
    val (sliderStyle, onSliderStyleChange) = rememberEnumPreference(
        SliderStyleKey,
        defaultValue = SliderStyle.SQUIGGLY
    )
    val (rotateBackground, onRotateBackgroundChange) = rememberPreference(
        key = RotateBackgroundKey,
        defaultValue = false
    )
    var showSliderOptionDialog by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var showColorPickerOptionDialog by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    SettingsPage(
        title = stringResource(R.string.player_and_audio),
        navController = navController,
        scrollBehavior = scrollBehavior
    ) {
    if (showColorPickerOptionDialog) {
        DefaultDialog(
            buttons = {
                TextButton(
                    onClick = { showColorPickerOptionDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
            onDismiss = {
                showColorPickerOptionDialog = false
            }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onColourfullPlayerColorChange(0)
                            showColorPickerOptionDialog = false
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Magenta)
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.auto_from_song), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                val colors = listOf(
                    0xFF4CAF50.toInt(), // Green
                    0xFFF44336.toInt(), // Red
                    0xFF2196F3.toInt(), // Blue
                    0xFFFF9800.toInt(), // Orange
                    0xFF9C27B0.toInt(), // Purple
                    0xFF00BCD4.toInt(), // Cyan
                    0xFFE91E63.toInt(), // Pink
                    0xFFFFEB3B.toInt(), // Yellow
                    0xFF8BC34A.toInt(), // Light Green
                    0xFF3F51B5.toInt(), // Indigo
                    0xFF009688.toInt(), // Teal
                    0xFFFF5722.toInt(), // Deep Orange
                    0xFF795548.toInt(), // Brown
                    0xFF607D8B.toInt(), // Blue Grey
                    0xFF673AB7.toInt()  // Deep Purple
                )
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(colors.size) { index ->
                        val colorInt = colors[index]
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(Color(colorInt))
                                .clickable {
                                    onColourfullPlayerColorChange(colorInt)
                                    showColorPickerOptionDialog = false
                                }
                        )
                    }
                }
            }
        }
    }

    if (showSliderOptionDialog) {
        DefaultDialog(
            buttons = {
                TextButton(
                    onClick = { showSliderOptionDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
            onDismiss = {
                showSliderOptionDialog = false
            }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            if (sliderStyle == SliderStyle.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            onSliderStyleChange(SliderStyle.DEFAULT)
                            showSliderOptionDialog = false
                        }
                        .padding(16.dp)
                ) {
                    var sliderValue by remember { mutableFloatStateOf(0.5f) }
                    Slider(
                        value = sliderValue,
                        valueRange = 0f..1f,
                        onValueChange = { sliderValue = it },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.default_),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            if (sliderStyle == SliderStyle.SQUIGGLY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            onSliderStyleChange(SliderStyle.SQUIGGLY)
                            showSliderOptionDialog = false
                        }
                        .padding(16.dp)
                ) {
                    var sliderValue by remember { mutableFloatStateOf(0.5f) }
                    SquigglySlider(
                        value = sliderValue,
                        valueRange = 0f..1f,
                        onValueChange = { sliderValue = it },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.squiggly),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            if (sliderStyle == SliderStyle.SLIM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            onSliderStyleChange(SliderStyle.SLIM)
                            showSliderOptionDialog = false
                        }
                        .padding(16.dp)
                ) {
                    var sliderValue by remember { mutableFloatStateOf(0.5f) }
                    Slider(
                        value = sliderValue,
                        valueRange = 0f..1f,
                        onValueChange = { sliderValue = it },
                        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                        track = { sliderState ->
                            PlayerSliderTrack(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors()
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                detectTapGestures(onPress = {})
                            }
                    )
                    Text(
                        text = stringResource(R.string.slim),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
        SettingsGeneralCategory(
            title = stringResource(R.string.player),
            items = listOf(
                {EnumListPreference(
                    title = { Text(stringResource(R.string.audio_quality)) },
                    icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                    selectedValue = audioQuality,
                    onValueSelected = onAudioQualityChange,
                    valueText = {
                        when (it) {
                            AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                            AudioQuality.MAX -> stringResource(R.string.audio_quality_max)
                            AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                            AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                        }
                    }
                )},

                {SwitchPreference(
                    title = { Text(stringResource(R.string.permanent_shuffle)) },
                    description = stringResource(R.string.permanent_shuffle_desc),
                    icon = { Icon(painterResource(R.drawable.shuffle), null) },
                    checked = permanentShuffle,
                    onCheckedChange = onPermanentShuffleChange
                )},

                {SwitchPreference(
                    title = { Text(stringResource(R.string.skip_silence)) },
                    icon = { Icon(painterResource(R.drawable.fast_forward), null) },
                    checked = skipSilence,
                    onCheckedChange = onSkipSilenceChange
                )},

                {SwitchPreference(
                    title = { Text(stringResource(R.string.audio_normalization)) },
                    icon = { Icon(painterResource(R.drawable.volume_up), null) },
                    checked = audioNormalization,
                    onCheckedChange = onAudioNormalizationChange
                )},
            )
        )


                // Player Category
                SettingsGeneralCategory(
                    title = "Player Style",
                    items = listOf(
                        {
                            EnumListPreference(
                                title = { Text(stringResource(R.string.player_screen_style)) },
                                icon = { Icon(painterResource(R.drawable.palette), null) },
                                selectedValue = playerScreenStyle,
                                onValueSelected = onPlayerScreenStyleChange,
                                valueText = {
                                    when (it) {
                                        PlayerScreenStyle.PAPER -> stringResource(R.string.paper_player)
                                        PlayerScreenStyle.CLASSIC -> stringResource(R.string.classic_player)
                                        PlayerScreenStyle.MODERN -> stringResource(R.string.modern_player)
                                        PlayerScreenStyle.SPOTIFY -> stringResource(R.string.spotify_player)
                                        PlayerScreenStyle.LIQUID -> stringResource(R.string.liquid_player)
                                        PlayerScreenStyle.CLOUDGLOW -> "CloudGlow"
                                        PlayerScreenStyle.FROST -> "Frost"
                                        PlayerScreenStyle.FOLD -> "Fold"
                                        PlayerScreenStyle.GROOVE -> "Groove"
                                        PlayerScreenStyle.POPSY -> "Popsy"
                                        PlayerScreenStyle.MINIMAL -> "Minimal"
                                        PlayerScreenStyle.COLOURFULL -> "Colourfull"
                                        PlayerScreenStyle.APPLE -> "Apple"
                                        PlayerScreenStyle.GALAXY -> "Galaxy"
                                        PlayerScreenStyle.IOS_STYLED -> "IOS Styled"
                                    }
                                },
                            )
                        },

                        *(if (playerScreenStyle == PlayerScreenStyle.COLOURFULL || playerScreenStyle == PlayerScreenStyle.APPLE || playerScreenStyle == PlayerScreenStyle.GALAXY) arrayOf(
                            {
                                PreferenceEntry(
                                    title = { Text(stringResource(R.string.player_colour)) },
                                    description = "Choose a custom background color",
                                    icon = { Icon(painterResource(R.drawable.palette), null) },
                                    onClick = { showColorPickerOptionDialog = true }
                                )
                            }
                        ) else emptyArray()),

                        {
                            ThumbnailCornerRadiusSelectorButton(
                                onRadiusSelected = { selectedRadius ->
                                    Timber.tag("Thumbnail").d("Selected radio: $selectedRadius")
                                }
                            )
                        },

                        {
                            PreferenceEntry(
                                title = { Text(stringResource(R.string.player_slider_style)) },
                                description = when (sliderStyle) {
                                    SliderStyle.DEFAULT -> stringResource(R.string.default_)
                                    SliderStyle.SQUIGGLY -> stringResource(R.string.squiggly)
                                    SliderStyle.SLIM -> stringResource(R.string.slim)
                                },
                                icon = { Icon(painterResource(R.drawable.sliders), null) },
                                onClick = { showSliderOptionDialog = true },
                            )
                        },

                        *(if (playerScreenStyle == PlayerScreenStyle.GALAXY) arrayOf(
                            {
                                val (showGalaxySlider, onShowGalaxySliderChange) = rememberPreference(
                                    ShowGalaxySliderKey,
                                    defaultValue = true
                                )
                                SwitchPreference(
                                    title = { Text(stringResource(R.string.show_galaxy_slider)) },
                                    description = stringResource(R.string.show_galaxy_slider_desc),
                                    icon = { Icon(painterResource(R.drawable.sliders), null) },
                                    checked = showGalaxySlider,
                                    onCheckedChange = onShowGalaxySliderChange
                                )
                            }
                        ) else emptyArray()),

                        {
                            SwitchPreference(
                                title = { Text(stringResource(R.string.Rotatelyricsbackground)) },
                                description = null,
                                icon = { Icon(painterResource(R.drawable.album), null) },
                                checked = rotateBackground,
                                onCheckedChange = onRotateBackgroundChange
                            )
                        },
                    )
                )

        SettingsGeneralCategory(
            title = stringResource(R.string.queue),
            items = listOf(
                {SwitchPreference(
                    title = { Text(stringResource(R.string.persistent_queue)) },
                    description = stringResource(R.string.persistent_queue_desc),
                    icon = { Icon(painterResource(R.drawable.queue_music), null) },
                    checked = persistentQueue,
                    onCheckedChange = onPersistentQueueChange
                )},

                {SwitchPreference(
                    title = { Text(stringResource(R.string.auto_load_more)) },
                    description = stringResource(R.string.auto_load_more_desc),
                    icon = { Icon(painterResource(R.drawable.playlist_add), null) },
                    checked = autoLoadMore,
                    onCheckedChange = onAutoLoadMoreChange
                )},

                {SwitchPreference(
                    title = { Text(stringResource(R.string.enable_similar_content)) },
                    description = stringResource(R.string.similar_content_desc),
                    icon = { Icon(painterResource(R.drawable.similar), null) },
                    checked = similarContentEnabled,
                    onCheckedChange = similarContentEnabledChange,
                )},

                {SwitchPreference(
                    title = { Text(stringResource(R.string.auto_skip_next_on_error)) },
                    description = stringResource(R.string.auto_skip_next_on_error_desc),
                    icon = { Icon(painterResource(R.drawable.skip_next), null) },
                    checked = autoSkipNextOnError,
                    onCheckedChange = onAutoSkipNextOnErrorChange
                )},
            )
        )

        SettingsGeneralCategory(
            title = stringResource(R.string.misc),
            items = listOf(
                {SwitchPreference(
                    title = { Text(stringResource(R.string.stop_music_on_task_clear)) },
                    icon = { Icon(painterResource(R.drawable.clear_all), null) },
                    checked = stopMusicOnTaskClear,
                    onCheckedChange = onStopMusicOnTaskClearChange
                )},
            )
        )
    }
}
