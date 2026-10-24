package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.ai.AiRecommendationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.devxrohan.smusic.R

@Composable
fun AiPlaylistPromptDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val database = com.devxrohan.smusic.LocalDatabase.current
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var completed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!generating) onDismiss() },
        title = { Text(if (generating) "Building your AI playlist" else "Create an AI playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (generating) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    Text(status ?: "Analyzing your taste…", style = MaterialTheme.typography.bodyMedium)
                } else if (completed) {
                    Text(status ?: "Your playlist is ready.")
                } else {
                    Text(stringResource(R.string.ui_describe_the_vibe_genre_activity_language))
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.ui_your_playlist_prompt)) },
                        placeholder = { Text(stringResource(R.string.ui_e_g_late_night_hindi_indie)) },
                        minLines = 3,
                        maxLines = 6,
                    )
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (!generating && !completed) TextButton(onClick = {
                generating = true; error = null
                scope.launch {
                    val result = AiRecommendationHelper.generateRecommendations(
                        context = context,
                        database = database,
                        customPrompt = prompt.trim().takeIf { it.isNotBlank() },
                        onLog = { message -> withContext(Dispatchers.Main) { status = message } },
                    )
                    generating = false
                    result.onSuccess { count -> completed = true; status = "Added $count songs to '${AiRecommendationHelper.PLAYLIST_NAME}'." }
                        .onFailure { error = it.localizedMessage ?: "AI playlist generation failed."; status = null }
                }
            }) { Text(stringResource(R.string.ui_generate_playlist)) }
            else if (completed || error != null) TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { if (!generating && !completed) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
