@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens.musicrecognition

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.DisableAnimationsKey
import com.devxrohan.smusic.ui.component.AppIconButton
import com.devxrohan.smusic.utils.rememberPreference

@Composable
internal fun RecognitionListenControl(
    modifier: Modifier,
    isActive: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
) {
    val reduceMotion by rememberPreference(DisableAnimationsKey, false)
    val description = stringResource(when {
        isProcessing -> R.string.music_recognition_processing
        isActive -> R.string.music_recognition_listening
        else -> R.string.music_recognition_tap_to_listen
    })
    Box(modifier.semantics { stateDescription = description }, contentAlignment = Alignment.Center) {
        if (isActive || isProcessing) {
            if (reduceMotion) CircularProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxSize().padding(8.dp))
            else CircularWavyProgressIndicator(modifier = Modifier.fillMaxSize().padding(8.dp))
        }
        AppIconButton(onClick = onClick, modifier = Modifier.size(176.dp),
            colors = IconButtonDefaults.filledIconButtonColors()) {
            Icon(painterResource(when {
                isProcessing -> R.drawable.cached
                isActive -> R.drawable.listening
                else -> R.drawable.graphic_eq
            }), description, Modifier.size(76.dp))
        }
    }
}
