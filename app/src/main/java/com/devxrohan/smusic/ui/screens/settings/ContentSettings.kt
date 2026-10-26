package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.devxrohan.smusic.NotificationPermissionPreference
import com.devxrohan.smusic.R
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.constants.ContentCountryKey
import com.devxrohan.smusic.constants.PrioritizeJioSaavn320Key
import com.devxrohan.smusic.constants.MusicProviderKey
import com.devxrohan.smusic.constants.ContentLanguageKey
import com.devxrohan.smusic.constants.CountryCodeToName
import com.devxrohan.smusic.constants.EnableKugouKey
import com.devxrohan.smusic.constants.EnableLrcLibKey
import com.devxrohan.smusic.constants.EnableSimpMusicLyricsKey
import com.devxrohan.smusic.constants.EnableUnisonLyricsKey
import com.devxrohan.smusic.constants.EnableYouLyLyricsKey
import com.devxrohan.smusic.constants.EnableMegalobizLyricsKey
import com.devxrohan.smusic.constants.EnablePaxsenixLyricsKey
import com.devxrohan.smusic.constants.EnablePortatoLyricsKey
import com.devxrohan.smusic.constants.EnableBetterLyricsKey
import com.devxrohan.smusic.constants.EnableYouTubeSubtitleLyricsKey
import com.devxrohan.smusic.constants.EnableYouTubeMusicLyricsKey
import com.devxrohan.smusic.constants.HideExplicitKey
import com.devxrohan.smusic.constants.HistoryDuration
import com.devxrohan.smusic.constants.LanguageCodeToName
import com.devxrohan.smusic.constants.PreferredLyricsProvider
import com.devxrohan.smusic.constants.PreferredLyricsProviderKey
import com.devxrohan.smusic.constants.ProxyEnabledKey
import com.devxrohan.smusic.constants.ProxyTypeKey
import com.devxrohan.smusic.constants.ProxyUrlKey
import com.devxrohan.smusic.constants.QuickPicks
import com.devxrohan.smusic.constants.QuickPicksKey
import com.devxrohan.smusic.constants.SYSTEM_DEFAULT
import com.devxrohan.smusic.constants.TopSize
import com.devxrohan.smusic.ui.component.EditTextPreference
import com.devxrohan.smusic.ui.component.ListPreference
import com.devxrohan.smusic.ui.component.SettingsGeneralCategory
import com.devxrohan.smusic.ui.component.SettingsPage
import com.devxrohan.smusic.ui.component.SliderPreference
import com.devxrohan.smusic.ui.component.SwitchPreference
import com.devxrohan.smusic.utils.rememberEnumPreference
import com.devxrohan.smusic.utils.rememberPreference
import java.net.Proxy
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (contentLanguage, onContentLanguageChange) = rememberPreference(
        key = ContentLanguageKey,
        defaultValue = SYSTEM_DEFAULT
    )
    // The browse/catalog provider is intentionally fixed to YouTube Music, including old installs.
    val (musicProvider, onMusicProviderChange) = rememberPreference(MusicProviderKey, "YT")
    LaunchedEffect(musicProvider) {
        if (musicProvider != "YT") onMusicProviderChange("YT")
    }
    val (prioritizeJioSaavn, onPrioritizeJioSaavnChange) = rememberPreference(
        key = PrioritizeJioSaavn320Key,
        defaultValue = false
    )
    val (contentCountry, onContentCountryChange) = rememberPreference(
        key = ContentCountryKey,
        defaultValue = SYSTEM_DEFAULT
    )
    val (hideExplicit, onHideExplicitChange) = rememberPreference(
        key = HideExplicitKey,
        defaultValue = false
    )
    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(
        key = ProxyEnabledKey,
        defaultValue = false
    )
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(
        key = ProxyTypeKey,
        defaultValue = Proxy.Type.HTTP
    )
    val (proxyUrl, onProxyUrlChange) = rememberPreference(
        key = ProxyUrlKey,
        defaultValue = "host:port"
    )
    val (lengthTop, onLengthTopChange) = rememberPreference(
        key = TopSize,
        defaultValue = "50"
    )
    val (historyDuration, onHistoryDurationChange) = rememberPreference(
        key = HistoryDuration,
        defaultValue = 30f
    )
    val (quickPicks, onQuickPicksChange) = rememberEnumPreference(
        key = QuickPicksKey,
        defaultValue = QuickPicks.QUICK_PICKS
    )
    val (enableKugou, onEnableKugouChange) = rememberPreference(
        key = EnableKugouKey,
        defaultValue = true
    )
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(
        key = EnableLrcLibKey,
        defaultValue = true
    )
    val (enableSimpMusic, onEnableSimpMusicChange) = rememberPreference(EnableSimpMusicLyricsKey, true)
    val (enableUnison, onEnableUnisonChange) = rememberPreference(EnableUnisonLyricsKey, true)
    val (enableYouLy, onEnableYouLyChange) = rememberPreference(EnableYouLyLyricsKey, true)
    val (enableMegalobiz, onEnableMegalobizChange) = rememberPreference(EnableMegalobizLyricsKey, true)
    val (enablePaxsenix, onEnablePaxsenixChange) = rememberPreference(EnablePaxsenixLyricsKey, true)
    val (enablePortato, onEnablePortatoChange) = rememberPreference(EnablePortatoLyricsKey, true)
    val (enableBetterLyrics, onEnableBetterLyricsChange) = rememberPreference(EnableBetterLyricsKey, true)
    val (enableYouTubeSubtitles, onEnableYouTubeSubtitlesChange) = rememberPreference(EnableYouTubeSubtitleLyricsKey, true)
    val (enableYouTubeMusicLyrics, onEnableYouTubeMusicLyricsChange) = rememberPreference(EnableYouTubeMusicLyricsKey, true)
    val (preferredProvider, onPreferredProviderChange) = rememberEnumPreference(
        key = PreferredLyricsProviderKey,
        defaultValue = PreferredLyricsProvider.LRCLIB
    )


    SettingsPage(
        title = stringResource(R.string.content),
        navController = navController,
        scrollBehavior = scrollBehavior
    ) {
        // General settings
        SettingsGeneralCategory(
            title = stringResource(R.string.general),
            items = listOf(
                {SwitchPreference(
                    title = { Text(stringResource(R.string.ui_prioritize_jiosaavn_320_kbps)) },
                    description = stringResource(R.string.ui_try_a_matching_high_quality_jiosaavn),
                    icon = { Icon(painterResource(R.drawable.music_note), null) },
                    checked = prioritizeJioSaavn,
                    onCheckedChange = onPrioritizeJioSaavnChange,
                )},
                {ListPreference(
                    title = { Text(stringResource(R.string.content_language)) },
                    icon = { Icon(painterResource(R.drawable.language), null) },
                    selectedValue = contentLanguage,
                    values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
                    valueText = {
                        LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                    },
                    onValueSelected = { selectedLanguage ->
                        onContentLanguageChange(selectedLanguage)
                        // The request client reads this value at request time. Updating it
                        // here makes Content settings take effect immediately, instead of
                        // waiting for the next full app process restart.
                        val effectiveLanguage = selectedLanguage
                            .takeIf { it != SYSTEM_DEFAULT }
                            ?: Locale.getDefault().language.takeIf { it in LanguageCodeToName }
                            ?: "en"
                        YouTube.locale = YouTube.locale.copy(hl = effectiveLanguage)

                    },
                )},
                {ListPreference(
                    title = { Text(stringResource(R.string.content_country)) },
                    icon = { Icon(painterResource(R.drawable.location_on), null) },
                    selectedValue = contentCountry,
                    values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
                    valueText = {
                        CountryCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
                    },
                    onValueSelected = { selectedCountry ->
                        onContentCountryChange(selectedCountry)
                        val effectiveCountry = selectedCountry
                            .takeIf { it != SYSTEM_DEFAULT }
                            ?: Locale.getDefault().country.takeIf { it in CountryCodeToName }
                            ?: "US"
                        YouTube.locale = YouTube.locale.copy(gl = effectiveCountry)
                    },
                )},

                // Hide explicit content
                {SwitchPreference(
                    title = { Text(stringResource(R.string.hide_explicit)) },
                    icon = { Icon(painterResource(R.drawable.explicit), null) },
                    checked = hideExplicit,
                    onCheckedChange = onHideExplicitChange,
                )},

                {NotificationPermissionPreference()},
            )
        )

        // Proxy settings
        SettingsGeneralCategory(
            title = stringResource(R.string.proxy),
            items = listOf(
                {SwitchPreference(
                    title = { Text(stringResource(R.string.enable_proxy)) },
                    icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                    checked = proxyEnabled,
                    onCheckedChange = onProxyEnabledChange,
                )},
                {if (proxyEnabled) {
                    Column {
                        ListPreference(
                            title = { Text(stringResource(R.string.proxy_type)) },
                            selectedValue = proxyType,
                            values = listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS),
                            valueText = { it.name },
                            onValueSelected = onProxyTypeChange,
                        )
                        EditTextPreference(
                            title = { Text(stringResource(R.string.proxy_url)) },
                            value = proxyUrl,
                            onValueChange = onProxyUrlChange,
                        )
                    }
                }}
            )
        )

        // Lyrics settings: each toggle controls the real lookup provider in LyricsHelper.
        SettingsGeneralCategory(
            title = stringResource(R.string.lyrics),
            items = listOf(
                { ListPreference(
                    title = { Text(stringResource(R.string.ui_prefer_lyrics_provider)) },
                    icon = { Icon(painterResource(R.drawable.lyrics), null) },
                    selectedValue = preferredProvider,
                    values = enumValues<PreferredLyricsProvider>().toList(),
                    valueText = { it.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase) },
                    onValueSelected = onPreferredProviderChange,
                ) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_lrc_lib)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_lrclib), null) }, checked = enableLrclib, onCheckedChange = onEnableLrclibChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_kugou)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_kugou), null) }, checked = enableKugou, onCheckedChange = onEnableKugouChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_simpmusic)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_simpmusic), null) }, checked = enableSimpMusic, onCheckedChange = onEnableSimpMusicChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_unison)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_unison), null) }, checked = enableUnison, onCheckedChange = onEnableUnisonChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_youlyplus)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_youly), null) }, checked = enableYouLy, onCheckedChange = onEnableYouLyChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_megalobiz)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_megalobiz), null) }, checked = enableMegalobiz, onCheckedChange = onEnableMegalobizChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_paxsenix)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_paxsenix), null) }, checked = enablePaxsenix, onCheckedChange = onEnablePaxsenixChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_portato)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_portato), null) }, checked = enablePortato, onCheckedChange = onEnablePortatoChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_betterlyrics)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_betterlyrics), null) }, checked = enableBetterLyrics, onCheckedChange = onEnableBetterLyricsChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_youtube_subtitles)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_youtube_subtitles), null) }, checked = enableYouTubeSubtitles, onCheckedChange = onEnableYouTubeSubtitlesChange) },
                { SwitchPreference(title = { Text(stringResource(R.string.ui_youtube_music)) }, icon = { Icon(painterResource(R.drawable.lyrics_provider_youtube_music), null) }, checked = enableYouTubeMusicLyrics, onCheckedChange = onEnableYouTubeMusicLyricsChange) },
            )
        )

        // Misc settings
        SettingsGeneralCategory(
            title = stringResource(R.string.misc),
            items = listOf(
                {EditTextPreference(
                    title = { Text(stringResource(R.string.top_length)) },
                    icon = { Icon(painterResource(R.drawable.trending_up), null) },
                    value = lengthTop,
                    isInputValid = { it.toIntOrNull()?.let { num -> num > 0 } == true },
                    onValueChange = onLengthTopChange,
                )},
                {ListPreference(
                    title = { Text(stringResource(R.string.set_quick_picks)) },
                    icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                    selectedValue = quickPicks,
                    values = listOf(QuickPicks.QUICK_PICKS, QuickPicks.LAST_LISTEN),
                    valueText = {
                        when (it) {
                            QuickPicks.QUICK_PICKS -> stringResource(R.string.quick_picks)
                            QuickPicks.LAST_LISTEN -> stringResource(R.string.last_song_listened)
                        }
                    },
                    onValueSelected = onQuickPicksChange,
                )},
                {SliderPreference(
                    title = { Text(stringResource(R.string.history_duration)) },
                    icon = { Icon(painterResource(R.drawable.history), null) },
                    value = historyDuration,
                    onValueChange = onHistoryDurationChange,
                )},
            )
        )
    }
}
