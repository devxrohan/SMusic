package com.devxrohan.smusic.ui.screens

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.devxrohan.smusic.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    data object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.home_outlined,
        iconIdActive = R.drawable.home_filled,
        route = "home"
    )

    data object Explore : Screens(
        titleId = R.string.explore,
        iconIdInactive = R.drawable.explore_outlined,
        iconIdActive = R.drawable.explore_filled,
        route = "explore"
    )

    data object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.custom_media_library,
        iconIdActive = R.drawable.custom_media_library,
        route = "library"
    )

    data object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.custom_search,
        iconIdActive = R.drawable.custom_search,
        route = "search/{query}"
    )

    data object Settings : Screens(
        titleId = R.string.settings,
        iconIdInactive = R.drawable.settings,
        iconIdActive = R.drawable.settingsv2,
        route = "settings"
    )

    companion object {
        val MainScreens = listOf(Home, Search, Explore, Library)

        /** Builds the concrete Search destination from a user query. */
        fun SearchRoute(query: String = ""): String =
            "search/${Uri.encode(query)}"
    }
}
