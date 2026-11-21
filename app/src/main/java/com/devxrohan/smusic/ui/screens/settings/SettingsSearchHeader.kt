package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R

@Composable
internal fun SettingsSearchHeader(query: String, onQueryChange: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    fun collapseSearch() { expanded = false; onQueryChange(""); focus.clearFocus(); keyboard?.hide() }
    androidx.activity.compose.BackHandler(enabled = expanded) { collapseSearch() }
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
        .padding(horizontal = 16.dp, vertical = 12.dp).animateContentSize(),
        verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(!expanded, exit = shrinkHorizontally(), enter = expandHorizontally()) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
        }
        if (expanded) SettingsSearchField(query, onQueryChange, Modifier.weight(1f))
        else Spacer(Modifier.weight(1f))
        IconButton(onClick = { if (expanded) collapseSearch() else expanded = true }) {
            Icon(painterResource(if (expanded) R.drawable.arrow_back else R.drawable.search),
                if (expanded) "Close settings search" else "Search settings")
        }
    }
}
