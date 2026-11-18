package com.devxrohan.smusic.ui.utils

import androidx.compose.ui.util.fastAny
import androidx.navigation.NavController
import com.devxrohan.smusic.ui.screens.Screens

fun NavController.backToMain() {
    val mainDestination = currentBackStack.value.lastOrNull { entry ->
        Screens.MainScreens.fastAny { it.route == entry.destination.route }
    }?.destination?.route ?: graph.startDestinationRoute ?: Screens.Home.route

    popBackStack(mainDestination, inclusive = false)
}
