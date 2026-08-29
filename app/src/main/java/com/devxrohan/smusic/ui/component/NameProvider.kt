package com.devxrohan.smusic.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.devxrohan.smusic.App
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val LocalUserName = staticCompositionLocalOf { "" }

@Composable
fun NameProvider(
    namePreferenceManager: NamePreferenceManager,
    content: @Composable () -> Unit
) {
    val userName by namePreferenceManager.userName.collectAsState(initial = "")
    var showNameDialog by remember { mutableStateOf(false) }
    var isInitialized by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Check if name is set when component first loads
    LaunchedEffect(Unit) {
        val isNameSet = namePreferenceManager.isNameSet.first()
        if (!isNameSet) {
            showNameDialog = true
        }
        isInitialized = true
    }

    // Handle name confirmation
    val onNameConfirmed: (String) -> Unit = { name ->
        if (name.isNotBlank()) {
            showNameDialog = false
            scope.launch {
                namePreferenceManager.saveUserName(name)
            }
        }
    }

    CompositionLocalProvider(
        LocalUserName provides userName
    ) {
        content()
    }
}
