@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.*
import com.devxrohan.smusic.ui.component.EnumListPreference
import com.devxrohan.smusic.ui.component.SettingsGeneralCategory
import com.devxrohan.smusic.ui.component.SettingsPage
import com.devxrohan.smusic.utils.rememberEnumPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettings(navController: NavController, scrollBehavior: TopAppBarScrollBehavior) {
    val (style, changeStyle) = rememberEnumPreference(LyricsScreenStyleKey, LyricsScreenStyle.LYRICS_2)
    SettingsPage("Lyrics", navController, scrollBehavior) {
        SettingsGeneralCategory(title = "Display", items = listOf({
            EnumListPreference(title = { Text("Lyrics display style") }, icon = { Icon(painterResource(R.drawable.lyrics), null) },
                selectedValue = style, onValueSelected = changeStyle,
                valueText = { if (it == LyricsScreenStyle.CLASSIC) "Classic lyrics" else "Karaoke / synced lyrics" })
        }))
        LyricsAppearanceEditor()
        AdditionalAppearanceControls(lyricsOnly = true)
        LyricsProviderControls()
    }
}
