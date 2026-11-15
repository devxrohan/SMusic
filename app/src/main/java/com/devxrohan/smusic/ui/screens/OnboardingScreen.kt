package com.devxrohan.smusic.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.imePadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.devxrohan.smusic.R
import com.devxrohan.smusic.ui.component.CommunityLinksRow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.devxrohan.smusic.constants.DarkModeKey
import com.devxrohan.smusic.constants.GuestProfileNameKey
import com.devxrohan.smusic.constants.GuestProfilePhotoPathKey
import com.devxrohan.smusic.constants.ThemePresetKey
import com.devxrohan.smusic.ui.screens.settings.DarkMode
import com.devxrohan.smusic.ui.theme.ThemePreset
import com.devxrohan.smusic.ui.theme.ThemePresets
import com.devxrohan.smusic.utils.rememberEnumPreference
import com.devxrohan.smusic.utils.rememberPreference
import com.devxrohan.smusic.utils.rememberGuestProfilePhotoPicker
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.ui.res.stringResource

/**
 * First-run "Configure" screen: one soft floating card over slowly drifting
 * colour blooms, a few quick setup tiles (look, theme, permissions) and a
 * single arrow to continue. Everything is optional: the arrow always works,
 * permissions can also be granted later when a feature needs them.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onAddAccount: () -> Unit = onFinished,
    currentStep: Int = 0,
    onStepChange: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var showGuestProfileSetup by remember { mutableStateOf(false) }
    if (showGuestProfileSetup) {
        GuestProfileSetupScreen(
            onBack = { showGuestProfileSetup = false },
            onFinished = onFinished,
        )
        return
    }
    // HorizontalPager (the same, heavily-tested Compose component every major
    // onboarding flow uses) replaces a hand-rolled detectHorizontalDragGestures
    // detector here. That custom detector shared the screen with the step
    // buttons' own clickable handlers and could lose a touch-event race on
    // some devices/firmware (reported stuck-on-first-step on a Samsung Galaxy
    // S20 5G) — the swipe never registered and even the "Get started" button
    // stopped responding. HorizontalPager's gesture handling is first-party
    // and specifically built to coexist correctly with content inside it.
    val pagerState = rememberPagerState(
        initialPage = currentStep.coerceIn(0, 3),
        pageCount = { 4 },
    )
    val pagerScope = rememberCoroutineScope()
    val step = pagerState.currentPage
    fun updateStep(value: Int) {
        pagerScope.launch { pagerState.animateScrollToPage(value.coerceIn(0, 3)) }
    }
    // Keep the caller's external step (used to persist/restore progress) in
    // sync as the pager settles on a page, however it got there (tap, swipe,
    // or updateStep above).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onStepChange(it) }
    }
    // If the caller changes currentStep from the outside (e.g. restoring a
    // saved step), follow it without fighting the user's own in-progress swipe.
    LaunchedEffect(currentStep) {
        val target = currentStep.coerceIn(0, 3)
        if (pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(target)
        }
    }
    var darkMode by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    var themePresetId by rememberPreference(ThemePresetKey, ThemePresets.SMUSIC_DEFAULT.id)
    val activity = context as? Activity
    val notificationPermission: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun isGranted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun notificationsAllowed() = if (notificationPermission != null) isGranted(notificationPermission) else NotificationManagerCompat.from(context).areNotificationsEnabled()
    var notificationsGranted by remember { mutableStateOf(notificationsAllowed()) }
    var audioGranted by remember { mutableStateOf(isGranted(audioPermission)) }
    var notificationAskedOnce by remember { mutableStateOf(false) }
    // Songs stopping mid-playback on some phones is almost always this: the
    // standard Android "ignore battery optimizations" permission was never
    // requested up front (it used to live only inside Voice Assistant
    // settings, which most people never open), so Doze/App Standby — or an
    // OEM's own battery manager — eventually suspends the playback service.
    // Asking here, during onboarding, is what fixes that for new installs.
    var batteryUnrestricted by remember {
        mutableStateOf(com.devxrohan.smusic.utils.OemBackgroundHelper.isIgnoringBatteryOptimizations(context))
    }

    fun openNotificationSettings() {
        runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        notificationsGranted = notificationsAllowed(); audioGranted = isGranted(audioPermission)
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsGranted = notificationsAllowed(); audioGranted = isGranted(audioPermission)
                batteryUnrestricted = com.devxrohan.smusic.utils.OemBackgroundHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer); onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun requestNotifications() {
        if (notificationsGranted) return
        val permission = notificationPermission
        if (permission == null) { openNotificationSettings(); return }
        if (notificationAskedOnce && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) openNotificationSettings()
        else { notificationAskedOnce = true; permissionLauncher.launch(arrayOf(permission)) }
    }
    fun requestAudio() { if (!audioGranted) permissionLauncher.launch(arrayOf(audioPermission)) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        FloatingBlooms(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                repeat(4) { index ->
                    val dotColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (index <= step) colors.primary else colors.surfaceVariant,
                        animationSpec = tween(250),
                        label = "onboardingDot",
                    )
                    Box(Modifier.weight(1f).padding(horizontal = 3.dp).height(4.dp).clip(CircleShape).background(dotColor))
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                // With zero spacing, the previous page's content touches the
                // next page's content with no visual gap for the entire swipe,
                // which is what made the two steps look stuck/glued together
                // mid-swipe instead of reading as two separate screens sliding
                // past each other.
                pageSpacing = 20.dp,
            ) { page ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            // Per-page scale + fade driven by how far this page sits from the
                            // settled page (0 = settled, ±1 = fully off to a side). This is what
                            // makes the swipe itself feel animated instead of just "a page moved
                            // across the screen": the outgoing page shrinks and dims while the
                            // incoming one grows and brightens as it slides in.
                            .graphicsLayer {
                                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                val closeness = 1f - abs(pageOffset).coerceIn(0f, 1f)
                                scaleX = lerp(0.88f, 1f, closeness)
                                scaleY = lerp(0.88f, 1f, closeness)
                                alpha = lerp(0.4f, 1f, closeness)
                            }
                            .padding(vertical = 22.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                when (page) {
                    0 -> {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.smusic_logo),
                            contentDescription = stringResource(R.string.ui_smusic_app_logo),
                            modifier = Modifier.size(132.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                        Spacer(Modifier.height(28.dp))
                        Text(stringResource(R.string.onboarding_welcome_title), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.ui_your_music_your_mood_lets_make), color = colors.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(28.dp))
                        val githubRepo = com.devxrohan.smusic.BuildConfig.GITHUB_REPO
                        if (githubRepo.isNotBlank()) {
                            val starToast = stringResource(R.string.onboarding_github_star_toast)
                            GithubStarTile(
                                title = stringResource(R.string.onboarding_github_star_title),
                                subtitle = "devxrohan/SMusic · ${stringResource(R.string.onboarding_github_star_desc)}",
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                android.net.Uri.parse("https://github.com/$githubRepo")
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                        android.widget.Toast.makeText(context, starToast, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        CommunityLinksRow()
                    }
                    1 -> {
                        StepHeading(Icons.Outlined.Notifications, stringResource(R.string.ui_set_up_your_permissions), stringResource(R.string.ui_allow_notifications_and_music_access_to))
                        Spacer(Modifier.height(22.dp))
                        ConfigTile(Icons.Outlined.Notifications, stringResource(R.string.notifications), stringResource(R.string.ui_updates_and_important_messages), { PermissionStatus(notificationsGranted) }, { requestNotifications() })
                        Spacer(Modifier.height(12.dp))
                        ConfigTile(Icons.Outlined.LibraryMusic, stringResource(R.string.ui_local_music), stringResource(R.string.ui_find_audio_files_stored_on_your), { PermissionStatus(audioGranted) }, { requestAudio() })
                        Spacer(Modifier.height(12.dp))
                        ConfigTile(
                            Icons.Outlined.BatteryFull,
                            stringResource(R.string.onboarding_keep_playing_in_background_title),
                            stringResource(R.string.onboarding_keep_playing_in_background_desc),
                            { PermissionStatus(batteryUnrestricted) },
                            {
                                if (!batteryUnrestricted) {
                                    com.devxrohan.smusic.utils.OemBackgroundHelper.requestIgnoreBatteryOptimizations(context)
                                }
                            },
                        )
                        if (com.devxrohan.smusic.utils.OemBackgroundHelper.isKnownAggressiveOem()) {
                            Spacer(Modifier.height(10.dp))
                            TextButton(onClick = { com.devxrohan.smusic.utils.OemBackgroundHelper.openAutostartSettings(context) }) {
                                Text(
                                    stringResource(
                                        R.string.onboarding_also_allow_oem_autostart,
                                        com.devxrohan.smusic.utils.OemBackgroundHelper.autostartScreenLabel(),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.ui_you_can_change_permissions_any_time), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                    2 -> {
                        StepHeading(Icons.Outlined.Palette, stringResource(R.string.ui_personalise_your_experience), stringResource(R.string.ui_choose_your_appearance_and_accent_colour))
                        Spacer(Modifier.height(20.dp))
                        ConfigTile(Icons.Outlined.Brightness6, stringResource(R.string.appearance), stringResource(R.string.ui_light_dark_or_follow_your_device)) {
                            Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ModeChip(stringResource(R.string.ui_system), darkMode == DarkMode.AUTO, Modifier.weight(1f)) { darkMode = DarkMode.AUTO }
                                ModeChip(stringResource(R.string.ui_light), darkMode == DarkMode.OFF, Modifier.weight(1f)) { darkMode = DarkMode.OFF }
                                ModeChip(stringResource(R.string.ui_dark), darkMode == DarkMode.ON, Modifier.weight(1f)) { darkMode = DarkMode.ON }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        ConfigTile(Icons.Outlined.Palette, stringResource(R.string.ui_accent_theme), stringResource(R.string.ui_tap_a_colour_to_preview_it)) {
                            Spacer(Modifier.height(12.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ThemePresets.ALL.forEach { preset -> PresetSwatch(preset, preset.id == themePresetId) { themePresetId = preset.id } }
                            }
                        }
                    }
                    3 -> {
                        StepHeading(Icons.Outlined.LibraryMusic, stringResource(R.string.ui_choose_your_listening_experience), stringResource(R.string.ui_connect_your_youtube_account_for_your))
                        Spacer(Modifier.height(24.dp))
                        Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onAddAccount() }, color = colors.primary, shape = RoundedCornerShape(20.dp)) {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(id = R.drawable.youtube_logo),
                                    contentDescription = stringResource(R.string.account_youtube),
                                    modifier = Modifier.size(26.dp)
                                ); Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) { Text(stringResource(R.string.ui_add_youtube_account), color = colors.onPrimary, fontWeight = FontWeight.Bold); Text(stringResource(R.string.ui_use_your_library_likes_and_playlists), color = colors.onPrimary.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { showGuestProfileSetup = true }, color = colors.surfaceContainerHigh, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, colors.outlineVariant)) {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.MusicNote, null, tint = colors.primary, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.ui_continue_as_guest), color = colors.onSurface, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.ui_start_listening_without_signing_in), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.Outlined.ArrowForward, null, tint = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
                    }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0 && step < 3) TextButton(onClick = { updateStep(step - 1) }, modifier = Modifier.heightIn(min = 54.dp)) { Text(stringResource(R.string.back)) }
                    // heightIn(min=...) instead of a fixed height: on a narrow phone, or with
                    // the device font/display size turned up, "Continue without an account"
                    // can need two lines — a fixed height would clip it, this just grows.
                    Surface(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).clickable {
                        if (step < 3) updateStep(step + 1) else showGuestProfileSetup = true
                    }, color = colors.primary, shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (step == 3) "Continue as Guest" else if (step == 0) "Get started" else "Continue",
                                color = colors.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                            if (step != 3) {
                                Spacer(Modifier.width(10.dp))
                                Icon(Icons.Outlined.ArrowForward, null, tint = colors.onPrimary)
                            }
                        }
                    }
                }
        }

    }


}

@Composable
private fun GuestProfileSetupScreen(onBack: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var guestProfileName by rememberPreference(GuestProfileNameKey, "")
    var guestProfilePhotoPath by rememberPreference(GuestProfilePhotoPathKey, "")
    var nameInput by remember { mutableStateOf(guestProfileName) }
    val pickPhoto = rememberGuestProfilePhotoPicker { newPath ->
        val oldPath = guestProfilePhotoPath
        guestProfilePhotoPath = newPath
        if (oldPath.isNotBlank() && oldPath != newPath) runCatching { File(oldPath).delete() }
    }
    // There's no on-screen Back button on this step any more — the device/
    // gesture back action takes its place and returns to onboarding instead
    // of closing the app.
    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            // ime is excluded here and handled by imePadding below instead,
            // so the keyboard's inset isn't applied twice and the content
            // only shifts up by exactly the keyboard's height.
            .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime))
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Guest profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.onBackground)

        // The photo + name fields live in their own scrollable, weighted
        // area between the title and the Continue button. When the keyboard
        // opens and shrinks the space this area has, it's this area that
        // shrinks and (if needed) scrolls — the Continue button below it is
        // a separate sibling, so it never gets squeezed into/over the name
        // field; it just settles in place right above the keyboard.
        //
        // Plain top-down scroll on purpose, not a centered Box: a scroll
        // container that tries to vertically centre content taller than
        // itself ends up pinning that content's top above the scroll's own
        // top edge — with no way to scroll further up to reach it, that
        // top part (the photo, here) is permanently cut off on any screen
        // short enough for this area to actually need scrolling.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(132.dp).clip(CircleShape).background(colors.surfaceContainerHigh).clickable { pickPhoto() },
                contentAlignment = Alignment.Center,
            ) {
                if (guestProfilePhotoPath.isNotBlank() && File(guestProfilePhotoPath).exists()) {
                    AsyncImage(File(guestProfilePhotoPath), contentDescription = "Guest profile photo", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Icon(painterResource(R.drawable.person), contentDescription = null, tint = colors.primary, modifier = Modifier.size(58.dp))
                }
            }
            TextButton(onClick = { pickPhoto() }) { Text(if (guestProfilePhotoPath.isBlank()) "Add profile photo" else "Change profile photo") }
            Spacer(Modifier.height(28.dp))
            Text("What should we call you?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = colors.onBackground)
            Text("Your profile stays on this device.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
            OutlinedTextField(value = nameInput, onValueChange = { nameInput = it.take(40) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        Surface(Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(18.dp)).clickable {
            guestProfileName = nameInput.trim().ifBlank { "Guest" }
            onFinished()
        }, color = colors.primary, shape = RoundedCornerShape(18.dp)) {
            Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) { Text("Continue", color = colors.onPrimary, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Welcome-step CTA inviting the user to star the project on GitHub — a small ask, framed as support for the project. */
@Composable
private fun GithubStarTile(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        color = colors.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.star), null, tint = colors.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.onSurface, fontWeight = FontWeight.Bold)
                Text(subtitle, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StepHeading(icon: ImageVector, title: String, subtitle: String) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = colors.primary, modifier = Modifier.size(30.dp))
    }
    Spacer(Modifier.height(18.dp))
    Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun ConfigTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        content?.invoke(this)
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.primary else colors.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colors.onPrimary else colors.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PresetSwatch(preset: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(preset.background)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) colors.primary else colors.outlineVariant,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(preset.accent),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = preset.displayName,
            fontSize = 11.sp,
            maxLines = 1,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PermissionStatus(granted: Boolean) {
    val colors = MaterialTheme.colorScheme
    if (granted) {
        AnimatedCheck(active = true)
    } else {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.primary.copy(alpha = 0.14f))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(stringResource(R.string.music_recognition_permission_action), color = colors.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AnimatedCheck(active: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (active) colors.primary else Color.Transparent)
            .border(1.5.dp, if (active) colors.primary else colors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = active,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Slow, soft colour blooms drifting behind the card (the app's accent colours, so it follows the chosen theme). */
@Composable
private fun FloatingBlooms(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "blooms")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 30_000, easing = LinearEasing), RepeatMode.Restart),
        label = "bloomTime",
    )
    val tints = listOf(colors.primary, colors.tertiary, colors.secondary, colors.primary)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // x, y (fractions), radius (fraction of the short side), drift loops per cycle, phase
        val blooms = listOf(
            floatArrayOf(0.15f, 0.18f, 0.55f, 1f, 0.0f),
            floatArrayOf(0.88f, 0.34f, 0.48f, 1f, 1.7f),
            floatArrayOf(0.25f, 0.78f, 0.60f, 2f, 3.1f),
            floatArrayOf(0.85f, 0.92f, 0.42f, 1f, 4.6f),
        )
        blooms.forEachIndexed { index, b ->
            val loops = b[3]
            val cx = w * b[0] + sin(t * loops + b[4]) * w * 0.12f
            val cy = h * b[1] + cos(t * loops + b[4]) * h * 0.07f
            val radius = min(w, h) * b[2] * (1f + 0.08f * sin(t * 2f + b[4]))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tints[index].copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
    }
}
