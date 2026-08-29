package com.devxrohan.smusic.ui.screens.search

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.devxrohan.smusic.R
import com.devxrohan.smusic.viewmodels.OnlineSearchSuggestionViewModel

/**
 * The Search tab's actual entry point — this was missing entirely
 * (NavigationBuilder referenced a "SpotifySearchScreen" that had no
 * definition anywhere in the project). Rebuilt using the app's own
 * existing pieces: a plain search field here, with OnlineSearchScreen
 * — which already exists and handles suggestions/history — as the body.
 */
@Composable
fun SearchTabScreen(
    navController: NavController,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel()
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submitSearch(text: String) {
        if (text.isBlank()) return
        val encoded = Uri.encode(text)
        navController.navigate("search/$encoded")
        keyboardController?.hide()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                viewModel.query.value = it.text
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResourceCompat(R.string.search)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (textFieldValue.text.isNotEmpty()) {
                    IconButton(onClick = {
                        textFieldValue = TextFieldValue("")
                        viewModel.query.value = ""
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                } else {
                    IconButton(onClick = {
                        navController.navigate(com.devxrohan.smusic.ui.screens.musicrecognition.MusicRecognitionRoute)
                    }) {
                        Icon(painterResource(R.drawable.mic), contentDescription = null)
                    }
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { submitSearch(textFieldValue.text) }
            ),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors()
        )

        OnlineSearchScreen(
            query = textFieldValue.text,
            onQueryChange = {
                textFieldValue = it
                viewModel.query.value = it.text
            },
            navController = navController,
            onSearch = { submitSearch(it) },
            onDismiss = { },
            pureBlack = false,
            viewModel = viewModel
        )
    }
}

@Composable
private fun stringResourceCompat(id: Int): String = androidx.compose.ui.res.stringResource(id)
