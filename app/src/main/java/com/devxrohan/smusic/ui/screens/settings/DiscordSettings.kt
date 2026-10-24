package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.DiscordNameKey
import com.devxrohan.smusic.constants.DiscordTokenKey
import com.devxrohan.smusic.constants.DiscordUseDetailsKey
import com.devxrohan.smusic.constants.DiscordUsernameKey
import com.devxrohan.smusic.constants.EnableDiscordRPCKey
import com.devxrohan.smusic.ui.component.InfoLabel
import com.devxrohan.smusic.ui.component.PreferenceEntry
import com.devxrohan.smusic.ui.component.PreferenceGroupTitle
import com.devxrohan.smusic.ui.component.SettingsGeneralCategory
import com.devxrohan.smusic.ui.component.SwitchPreference
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.rememberPreference
import kotlinx.coroutines.launch

/**
 * Ported from SMusic' settings/DiscordSettings.kt and adapted to SMusic's own settings UI
 * primitives (per request) rather than porting SMusic' bespoke 800+ line implementation
 * verbatim, since that version pulls in SMusic-only branding/remote-config components that
 * don't exist in SMusic. The underlying behaviour is the same real thing SMusic does:
 *
 * This is a Rich Presence connection screen, not a separate "Discord account" with its own
 * profile/feed — the reference project has no Discord music feed, playlists, or social data to
 * surface here, only a token used to open a Gateway WebSocket for presence updates. That
 * limitation is accurately reflected below rather than invented away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val (discordToken) = rememberPreference(DiscordTokenKey, "")
    val (discordUsername) = rememberPreference(DiscordUsernameKey, "")
    val (discordName) = rememberPreference(DiscordNameKey, "")
    val (enableRPC, onEnableRPCChange) = rememberPreference(EnableDiscordRPCKey, true)
    val (useDetails, onUseDetailsChange) = rememberPreference(DiscordUseDetailsKey, false)

    val isLoggedIn = discordToken.isNotEmpty()

    com.devxrohan.smusic.ui.component.SettingsPage(
        title = stringResource(R.string.account_discord),
        navController = navController,
        scrollBehavior = scrollBehavior,
    ) {
        InfoLabel(text = stringResource(R.string.discord_rich_presence_description))

        PreferenceGroupTitle(title = stringResource(R.string.account_discord))

        SettingsGeneralCategory(
            title = null,
            items = listOf(
                {
                    PreferenceEntry(
                        title = { Text(if (isLoggedIn) (discordName.ifBlank { discordUsername }) else stringResource(R.string.account_sign_in)) },
                        description = if (isLoggedIn) {
                            "@$discordUsername"
                        } else {
                            stringResource(R.string.account_not_signed_in)
                        },
                        icon = { Icon(painterResource(R.drawable.account), contentDescription = null) },
                        onClick = {
                            if (isLoggedIn) {
                                // Logging out here only clears Discord's own keys, never
                                // YouTube account state is separate from Discord credentials.
                                coroutineScope.launch {
                                    context.dataStore.edit { settings ->
                                        settings.remove(DiscordTokenKey)
                                        settings.remove(DiscordUsernameKey)
                                        settings.remove(DiscordNameKey)
                                    }
                                }
                            } else {
                                navController.navigate("settings/discord/login")
                            }
                        },
                        trailingContent = if (isLoggedIn) {
                            {
                                Icon(painterResource(R.drawable.logout), contentDescription = stringResource(R.string.account_sign_out))
                            }
                        } else null,
                    )
                },
            ),
        )

        PreferenceGroupTitle(title = stringResource(R.string.discord_rich_presence))

        SettingsGeneralCategory(
            title = null,
            items = listOf(
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.discord_rich_presence)) },
                        description = if (!isLoggedIn) stringResource(R.string.account_not_signed_in) else null,
                        icon = { Icon(painterResource(R.drawable.info), contentDescription = null) },
                        checked = enableRPC && isLoggedIn,
                        onCheckedChange = onEnableRPCChange,
                        isEnabled = isLoggedIn,
                    )
                },
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.discord_show_details)) },
                        icon = { Icon(painterResource(R.drawable.info), contentDescription = null) },
                        checked = useDetails,
                        onCheckedChange = onUseDetailsChange,
                        isEnabled = isLoggedIn && enableRPC,
                    )
                },
            ),
        )
    }
}
