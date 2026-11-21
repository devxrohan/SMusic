package com.devxrohan.smusic.ui.screens.settings

import android.widget.Toast
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.navigation.NavController
import com.devxrohan.smusic.R
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.constants.AccountChannelHandleKey
import com.devxrohan.smusic.constants.AccountEmailKey
import com.devxrohan.smusic.constants.AccountNameKey
import com.devxrohan.smusic.constants.AccountPhotoUrlKey
import com.devxrohan.smusic.constants.GuestProfileNameKey
import com.devxrohan.smusic.constants.GuestProfilePhotoPathKey
import com.devxrohan.smusic.constants.DiscordNameKey
import com.devxrohan.smusic.constants.DiscordTokenKey
import com.devxrohan.smusic.constants.DiscordUsernameKey
import com.devxrohan.smusic.constants.EnableDiscordRPCKey
import com.devxrohan.smusic.constants.InnerTubeCookieKey
import com.devxrohan.smusic.constants.VisitorDataKey
import com.devxrohan.smusic.innertube.utils.parseCookieString
import com.devxrohan.smusic.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.devxrohan.smusic.ui.component.PreferenceEntry
import com.devxrohan.smusic.ui.component.SettingsGeneralCategory
import com.devxrohan.smusic.ui.component.SettingsPage
import com.devxrohan.smusic.utils.rememberPreference
import com.devxrohan.smusic.utils.rememberGuestProfilePhotoPicker
import com.devxrohan.smusic.utils.signOutYouTubeAccount
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettings(navController: NavController, scrollBehavior: TopAppBarScrollBehavior) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var visitorData by rememberPreference(VisitorDataKey, "")
    var accountName by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
    var accountPhotoUrl by rememberPreference(AccountPhotoUrlKey, "")
    var guestProfileName by rememberPreference(GuestProfileNameKey, "")
    var guestProfilePhotoPath by rememberPreference(GuestProfilePhotoPathKey, "")
    var showGuestNameDialog by remember { mutableStateOf(false) }
    var guestNameInput by remember { mutableStateOf(guestProfileName) }
    val pickGuestPhoto = rememberGuestProfilePhotoPicker { savedPath ->
        val previousPath = guestProfilePhotoPath
        guestProfilePhotoPath = savedPath
        if (previousPath.isNotBlank() && previousPath != savedPath) runCatching { File(previousPath).delete() }
        Toast.makeText(context, "Profile picture updated", Toast.LENGTH_SHORT).show()
    }
    var discordToken by rememberPreference(DiscordTokenKey, "")
    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")
    var discordRpcEnabled by rememberPreference(EnableDiscordRPCKey, false)
    var youtubeLibrarySync by rememberPreference(com.devxrohan.smusic.constants.YtmSyncKey, true)
    val isYouTubeLoggedIn = remember(innerTubeCookie) {
        innerTubeCookie.isNotBlank() && "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val isDiscordLoggedIn = discordToken.isNotBlank() && discordToken != "error" && discordToken != "null"
    var showAdvancedLogin by remember { mutableStateOf(false) }

    SettingsPage(title = stringResource(R.string.account), navController = navController, scrollBehavior = scrollBehavior) {
        if (!isYouTubeLoggedIn) {
            SettingsGeneralCategory(
                title = "Guest mode",
                items = listOf(
                    {
                        PreferenceEntry(
                            title = { Text(guestProfileName.ifBlank { "Guest" }) },
                            description = "Your profile is saved on this device. No account or sign-in required.",
                            icon = {
                                com.devxrohan.smusic.ui.component.ProfileAvatar(
                                    model = guestProfilePhotoPath.takeIf { it.isNotBlank() }?.let(::File),
                                    contentDescription = "Guest profile picture", size = 40.dp,
                                )
                            },
                            onClick = { guestNameInput = guestProfileName; showGuestNameDialog = true },
                        )
                    },
                    {
                        PreferenceEntry(
                            title = { Text("Change profile picture") },
                            description = "Choose a photo from your phone's gallery",
                            icon = { Icon(painterResource(R.drawable.account), contentDescription = null) },
                            onClick = { pickGuestPhoto() },
                        )
                    },
                    {
                        PreferenceEntry(
                            title = { Text("Remove profile picture") },
                            description = "Use the default profile icon instead",
                            icon = { Icon(painterResource(R.drawable.delete), contentDescription = null) },
                            isEnabled = guestProfilePhotoPath.isNotBlank(),
                            onClick = {
                                runCatching { File(guestProfilePhotoPath).delete() }
                                guestProfilePhotoPath = ""
                                Toast.makeText(context, "Profile picture removed", Toast.LENGTH_SHORT).show()
                            },
                        )
                    },
                ),
            )
        }
        SettingsGeneralCategory(
            title = stringResource(R.string.ui_youtube_music),
            items = listOf(
                {
                    PreferenceEntry(
                        title = { Text(if (isYouTubeLoggedIn) accountName.ifBlank { accountEmail.ifBlank { "YouTube account connected" } } else "Connect YouTube Music") },
                        description = if (isYouTubeLoggedIn) accountEmail.ifBlank { accountChannelHandle.ifBlank { "Connected" } } else "Sign in through YouTube Music to sync your library and history.",
                        icon = {
                            if (isYouTubeLoggedIn) com.devxrohan.smusic.ui.component.ProfileAvatar(
                                model = accountPhotoUrl.takeIf { it.startsWith("https://") },
                                contentDescription = "YouTube profile", size = 40.dp,
                            ) else Icon(painterResource(R.drawable.play), contentDescription = null)
                        },
                        onClick = { navController.navigate(if (isYouTubeLoggedIn) "account" else "login") },
                    )
                },
                {
                    com.devxrohan.smusic.ui.component.SwitchPreference(title = { Text("Sync YouTube Music library") },
                        description = "Import likes, playlists, albums and followed artists.", checked = youtubeLibrarySync,
                        icon = { Icon(painterResource(R.drawable.youtube), null, Modifier.size(28.dp), tint = androidx.compose.ui.graphics.Color.Unspecified) },
                        onCheckedChange = { youtubeLibrarySync = it }, isEnabled = isYouTubeLoggedIn)
                },
                {
                    com.devxrohan.smusic.ui.component.AccountWriteBackPreference("YouTube Music", com.devxrohan.smusic.constants.YouTubeWriteBackKey, enabled = isYouTubeLoggedIn)
                },
                {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ui_copy_your_cookie)) },
                        description = stringResource(R.string.ui_copy_the_saved_youtube_music_session),
                        icon = { Icon(painterResource(R.drawable.content_copy), contentDescription = null) },
                        isEnabled = isYouTubeLoggedIn,
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("YouTube Music cookie", innerTubeCookie))
                            Toast.makeText(context, "Cookie copied. Treat it like a password.", Toast.LENGTH_LONG).show()
                        },
                    )
                },
                {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ui_advanced_login_cookies)) },
                        description = stringResource(R.string.ui_paste_a_youtube_music_cookie_string),
                        icon = { Icon(painterResource(R.drawable.lock), contentDescription = null) },
                        onClick = { showAdvancedLogin = true },
                    )
                },
                {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ui_sign_out_of_youtube)) },
                        description = stringResource(R.string.ui_disconnect_account_sync_and_clear_the),
                        icon = { Icon(painterResource(R.drawable.logout), contentDescription = null) },
                        isEnabled = isYouTubeLoggedIn || accountName.isNotBlank() || accountEmail.isNotBlank(),
                        onClick = {
                            scope.launch {
                                // Only the account session is removed; local library, history and Pulse stay.
                                runCatching { signOutYouTubeAccount(context) }
                                Toast.makeText(context, "YouTube account disconnected", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                },
            ),
        )
        com.devxrohan.smusic.spotify.SpotifyAccountPreferences(navController)
        SettingsGeneralCategory(
            title = stringResource(R.string.discord),
            items = listOf(
                {
                    PreferenceEntry(
                        title = { Text(if (isDiscordLoggedIn) discordName.ifBlank { discordUsername.ifBlank { "Discord connected" } } else "Connect Discord") },
                        description = if (isDiscordLoggedIn) "Rich Presence is ${if (discordRpcEnabled) "enabled" else "connected but disabled"}." else stringResource(R.string.discord_rich_presence),
                        icon = { Icon(painterResource(R.drawable.info), contentDescription = null) },
                        onClick = { navController.navigate("settings/discord") },
                    )
                },
                {
                    PreferenceEntry(
                        title = { Text("Advanced Discord login") },
                        description = "Sign in with a Discord token",
                        icon = { Icon(painterResource(R.drawable.lock), null) },
                        onClick = { navController.navigate("settings/discord/login?manual=true") },
                    )
                },
                {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ui_sign_out_of_discord)) },
                        description = stringResource(R.string.ui_disconnect_rich_presence_and_clear_the),
                        icon = { Icon(painterResource(R.drawable.logout), contentDescription = null) },
                        isEnabled = isDiscordLoggedIn || discordName.isNotBlank() || discordUsername.isNotBlank(),
                        onClick = {
                            discordRpcEnabled = false; discordToken = ""; discordName = ""; discordUsername = ""
                            Toast.makeText(context, "Discord disconnected", Toast.LENGTH_SHORT).show()
                        },
                    )
                },
            ),
        )
    }

    if (showGuestNameDialog) {
        AlertDialog(
            onDismissRequest = { showGuestNameDialog = false },
            title = { Text("Edit guest name") },
            text = {
                OutlinedTextField(
                    value = guestNameInput,
                    onValueChange = { guestNameInput = it.take(40) },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    guestProfileName = guestNameInput.trim().ifBlank { "Guest" }
                    showGuestNameDialog = false
                    Toast.makeText(context, "Guest name updated", Toast.LENGTH_SHORT).show()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showGuestNameDialog = false }) { Text("Cancel") } },
        )
    }

    if (showAdvancedLogin) YouTubeCookieLoginDialog(
        onDismiss = { showAdvancedLogin = false }, onConnected = { showAdvancedLogin = false })
}
