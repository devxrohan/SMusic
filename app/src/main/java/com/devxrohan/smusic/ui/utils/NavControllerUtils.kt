package com.devxrohan.smusic.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.devxrohan.smusic.ui.screens.Screens

val NavController.canNavigateUp: Boolean
    get() = currentBackStackEntry?.destination?.parent?.route != null

fun NavController.backToMain() {
    val mainDestination = currentBackStack.value.lastOrNull { entry ->
        Screens.MainScreens.fastAny { it.route == entry.destination.route }
    }?.destination?.route ?: graph.startDestinationRoute ?: Screens.Home.route

    popBackStack(mainDestination, inclusive = false)
}

@Composable
inline fun <reified VM : ViewModel> safeHiltViewModel(): VM? {
    val owner = LocalViewModelStoreOwner.current ?: return null
    if (owner is NavBackStackEntry) {
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            return null
        }
    }
    return androidx.hilt.navigation.compose.hiltViewModel<VM>()
}
