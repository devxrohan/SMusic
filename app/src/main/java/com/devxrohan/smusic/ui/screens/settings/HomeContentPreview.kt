@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.devxrohan.smusic.R
import com.devxrohan.smusic.ui.screens.HomeScreen
import com.devxrohan.smusic.viewmodels.HomeViewModel
import kotlinx.serialization.json.JsonElement

@Composable
internal fun HomeContentPreview(values: Map<String, JsonElement>, navController: NavController) {
    val owner = remember(navController) { runCatching { navController.getBackStackEntry("home") }.getOrNull() }
    val model: HomeViewModel = if (owner != null) hiltViewModel(owner) else hiltViewModel()
    Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.background, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Box(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                // Keep vertical/horizontal scrolling, consume simple taps before player/navigation handlers.
                awaitPointerEventScope {
                    var origin = androidx.compose.ui.geometry.Offset.Zero
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { change ->
                            if (change.pressed && !change.previousPressed) { origin = change.position; moved = false }
                            if ((change.position - origin).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (!change.pressed && change.previousPressed && !moved) change.consume()
                        }
                    }
                }
            }) { HomeScreen(navController, viewModel = model, layoutOverride = values) }
            val connection = com.devxrohan.smusic.LocalPlayerConnection.current
            val metadata = connection?.mediaMetadata?.collectAsState()?.value
            if (metadata != null && connection != null) {
                val state = com.devxrohan.smusic.ui.component.rememberBottomSheetState(0.dp, 1.dp)
                Box(Modifier.fillMaxWidth().pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } } } }) {
                    com.devxrohan.smusic.ui.player.MiniPlayer(position = connection.player.currentPosition.coerceAtLeast(0), duration = connection.player.duration.coerceAtLeast(0), state = state)
                }
            }
            val navStyle by com.devxrohan.smusic.utils.rememberEnumPreference(com.devxrohan.smusic.constants.NavBarStyleKey, com.devxrohan.smusic.constants.NavBarStyle.FLOATING)
            val hideLabels by com.devxrohan.smusic.utils.rememberPreference(com.devxrohan.smusic.constants.SlimNavBarKey, false)
            val navScale by com.devxrohan.smusic.utils.rememberPreference(com.devxrohan.smusic.constants.NavBarScaleKey, 1.11f)
            com.devxrohan.smusic.ui.component.SMusicBottomNav(
                modifier = Modifier.fillMaxWidth(), scale = navScale,
                items = listOf(com.devxrohan.smusic.ui.screens.Screens.Home, com.devxrohan.smusic.ui.screens.Screens.Search, com.devxrohan.smusic.ui.screens.Screens.Library, com.devxrohan.smusic.ui.screens.Screens.Settings).map {
                    com.devxrohan.smusic.ui.component.CurvedBottomNavigationItem(it.iconIdActive, it.iconIdInactive, it.titleId)
                }, selectedIndex = 0, onItemSelected = {}, showLabels = !hideLabels, style = navStyle,
            )
        }
    }
}
