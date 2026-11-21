@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.*
import com.devxrohan.smusic.lyrics.PaxsenixSpotifyLyricsProvider
import com.devxrohan.smusic.lyrics.PaxsenixMusixmatchLyricsProvider
import com.devxrohan.smusic.ui.component.*
import com.devxrohan.smusic.utils.rememberPreference

@Composable
internal fun LyricsProviderControls() {
    val (enableKugou, onEnableKugouChange) = rememberPreference(
        key = EnableKugouKey,
        defaultValue = true
    )
    val (enableLrcRed, onEnableLrcRedChange) = rememberPreference(
        key = EnableLrcRedKey,
        defaultValue = true
    )
    val (enableBini, onEnableBiniChange) = rememberPreference(EnableBiniLyricsKey, true)
    val (enableGenius, onEnableGeniusChange) = rememberPreference(EnableGeniusLyricsKey, true)
    val (enableMusixmatch, onEnableMusixmatchChange) = rememberPreference(EnableMusixmatchLyricsKey, true)
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(
        key = EnableLrcLibKey,
        defaultValue = true
    )
    val (enableSimpMusic, onEnableSimpMusicChange) = rememberPreference(EnableSimpMusicLyricsKey, true)
    val (enableUnison, onEnableUnisonChange) = rememberPreference(EnableUnisonLyricsKey, true)
    val (enableYouLy, onEnableYouLyChange) = rememberPreference(EnableYouLyLyricsKey, true)
    val (enableMegalobiz, onEnableMegalobizChange) = rememberPreference(EnableMegalobizLyricsKey, true)
    val (enablePaxsenix, onEnablePaxsenixChange) = rememberPreference(EnablePaxsenixLyricsKey, true)
    val (paxsenixApiKey, onPaxsenixApiKeyChange) = rememberPreference(PaxsenixLyricsApiKey, "")
    LaunchedEffect(paxsenixApiKey) {
        PaxsenixSpotifyLyricsProvider.setApiKey(paxsenixApiKey)
        PaxsenixMusixmatchLyricsProvider.setApiKey(paxsenixApiKey)
    }
    val (enablePortato, onEnablePortatoChange) = rememberPreference(EnablePortatoLyricsKey, true)
    val (enableBetterLyrics, onEnableBetterLyricsChange) = rememberPreference(EnableBetterLyricsKey, true)
    val (enableYouTubeSubtitles, onEnableYouTubeSubtitlesChange) = rememberPreference(EnableYouTubeSubtitleLyricsKey, true)
    val (enableYouTubeMusicLyrics, onEnableYouTubeMusicLyricsChange) = rememberPreference(EnableYouTubeMusicLyricsKey, true)
        // Lyrics settings: each toggle controls the real lookup provider in LyricsHelper.
        SettingsGeneralCategory(
            title = "Lyrics providers",
            items = listOf(
                { SwitchPreference(title = { Text(stringResource(R.string.ui_lrc_red)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_lrc_red), null) }, checked = enableLrcRed, onCheckedChange = onEnableLrcRedChange) },
                { SwitchPreference(title = { Text("BiniLyrics") }, icon = { Icon(painterResource(R.drawable.lyrics_provider_bini), null) }, checked = enableBini, onCheckedChange = onEnableBiniChange) },
                { SwitchPreference(title = { Text("Genius (plain lyrics)") }, icon = { Icon(painterResource(R.drawable.lyrics_provider_genius), null) }, checked = enableGenius, onCheckedChange = onEnableGeniusChange) },
                { SwitchPreference(title = { Text("Musixmatch") }, icon = { Icon(painterResource(R.drawable.lyrics_provider_musixmatch), null) }, checked = enableMusixmatch, onCheckedChange = onEnableMusixmatchChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_lrc_lib)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_lrclib), null) }, checked = enableLrclib, onCheckedChange = onEnableLrclibChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_kugou)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_kugou), null) }, checked = enableKugou, onCheckedChange = onEnableKugouChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_simpmusic)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_simpmusic), null) }, checked = enableSimpMusic, onCheckedChange = onEnableSimpMusicChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_unison)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_unison), null) }, checked = enableUnison, onCheckedChange = onEnableUnisonChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_youlyplus)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_youly), null) }, checked = enableYouLy, onCheckedChange = onEnableYouLyChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_megalobiz)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_megalobiz), null) }, checked = enableMegalobiz, onCheckedChange = onEnableMegalobizChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_paxsenix)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_paxsenix), null) }, checked = enablePaxsenix, onCheckedChange = onEnablePaxsenixChange) },
                { EditTextPreference(title = { Text("PaxSenix API key (Spotify / Musixmatch)") }, icon = { Icon(painterResource(R.drawable.lyrics_provider_paxsenix), null) }, value = paxsenixApiKey, onValueChange = onPaxsenixApiKeyChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_portato)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_portato), null) }, checked = enablePortato, onCheckedChange = onEnablePortatoChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_betterlyrics)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_betterlyrics), null) }, checked = enableBetterLyrics, onCheckedChange = onEnableBetterLyricsChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_youtube_subtitles)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_youtube_subtitles), null) }, checked = enableYouTubeSubtitles, onCheckedChange = onEnableYouTubeSubtitlesChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_youtube_music)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_youtube_music), null) }, checked = enableYouTubeMusicLyrics, onCheckedChange = onEnableYouTubeMusicLyricsChange) },
            )
        )

}
