package com.devxrohan.smusic.ui.screens.settings

import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.navigation.NavController
import com.devxrohan.smusic.R
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.constants.AccountChannelHandleKey
import com.devxrohan.smusic.constants.AccountEmailKey
import com.devxrohan.smusic.constants.AccountNameKey
import com.devxrohan.smusic.constants.AccountPhotoUrlKey
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
    var discordToken by rememberPreference(DiscordTokenKey, "")
    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")
    var discordRpcEnabled by rememberPreference(EnableDiscordRPCKey, false)
    val isYouTubeLoggedIn = remember(innerTubeCookie) {
        innerTubeCookie.isNotBlank() && "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val isDiscordLoggedIn = discordToken.isNotBlank() && discordToken != "error" && discordToken != "null"
    var showAdvancedLogin by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf("") }
    var visitorInput by remember { mutableStateOf("") }

    SettingsPage(title = stringResource(R.string.account), navController = navController, scrollBehavior = scrollBehavior) {
        SettingsGeneralCategory(
            title = stringResource(R.string.ui_youtube_music),
            items = listOf(
                {
                    PreferenceEntry(
                        title = { Text(if (isYouTubeLoggedIn) accountName.ifBlank { accountEmail.ifBlank { "YouTube account connected" } } else "Connect YouTube Music") },
                        description = if (isYouTubeLoggedIn) accountEmail.ifBlank { accountChannelHandle.ifBlank { "Connected" } } else "Sign in through YouTube Music to sync your library and history.",
                        icon = { Icon(painterResource(R.drawable.play), contentDescription = null) },
                        onClick = { navController.navigate(if (isYouTubeLoggedIn) "account" else "login") },
                    )
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
                        onClick = { cookieInput = ""; visitorInput = visitorData; showAdvancedLogin = true },
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

    if (showAdvancedLogin) {
        AlertDialog(
            onDismissRequest = { showAdvancedLogin = false },
            title = { Text(stringResource(R.string.ui_advanced_youtube_login)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(stringResource(R.string.ui_paste_cookies_copied_from_music_youtube))
                    OutlinedTextField(
                        value = cookieInput,
                        onValueChange = { cookieInput = it },
                        label = { Text(stringResource(R.string.ui_cookie_string)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        minLines = 3,
                        maxLines = 6,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        value = visitorInput,
                        onValueChange = { visitorInput = it },
                        label = { Text(stringResource(R.string.ui_visitor_data_optional)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = parseCookieString(cookieInput.trim())
                    if (cookieInput.isBlank() || "SAPISID" !in parsed) {
                        Toast.makeText(context, "Invalid cookie: SAPISID was not found", Toast.LENGTH_LONG).show()
                    } else {
                        val cookieToSave = cookieInput.trim()
                        val visitorToSave = visitorInput.trim()
                        scope.launch {
                            innerTubeCookie = cookieToSave
                            visitorData = visitorToSave
                            // accountInfo() reads the active InnerTube session; cookie login must
                            // initialize the same in-memory cookie as the WebView login flow.
                            YouTube.cookie = cookieToSave
                            showAdvancedLogin = false
                            Toast.makeText(context, "Cookie saved. Loading YouTube profile…", Toast.LENGTH_LONG).show()
                            scope.launch {
                                var resolved = false
                                repeat(4) { attempt ->
                                    if (!resolved) {
                                        val result = withContext(Dispatchers.IO) {
                                            YouTube.accountInfo()
                                        }
                                        val info = result.getOrNull()
                                        if (info != null && (info.name.isNotBlank() || !info.email.isNullOrBlank())) {
                                            accountName = info.name.takeIf { it.isNotBlank() }.orEmpty()
                                            accountEmail = info.email.orEmpty()
                                            accountChannelHandle = info.channelHandle.orEmpty()
                                            accountPhotoUrl = info.photoUrl?.takeIf { it.startsWith("https://") }.orEmpty()
                                            resolved = true
                                        } else if (attempt < 3) {
                                            delay(900L)
                                        }
                                    }
                                }
                                if (!resolved) {
                                    Toast.makeText(context, "Cookie saved, but YouTube profile could not be loaded. Check that the cookie is current.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "YouTube profile loaded", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }) { Text(stringResource(R.string.ui_save_session)) }
            },
            dismissButton = { TextButton(onClick = { showAdvancedLogin = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
