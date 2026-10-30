package com.devxrohan.smusic.ui.menu

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import com.devxrohan.smusic.constants.AiProviderKey
import com.devxrohan.smusic.constants.AiTranslationLanguages
import com.devxrohan.smusic.constants.CustomPromptKey
import com.devxrohan.smusic.constants.DeeplApiKey
import com.devxrohan.smusic.constants.OpenRouterApiKey
import com.devxrohan.smusic.constants.OpenRouterBaseUrlKey
import com.devxrohan.smusic.constants.OpenRouterModelKey
import com.devxrohan.smusic.constants.ReplaceOriginalLyricsWithTranslationKey
import com.devxrohan.smusic.constants.TranslateLanguageKey
import com.devxrohan.smusic.constants.TranslateModeKey
import com.devxrohan.smusic.lyrics.LyricsTranslationHelper
import com.devxrohan.smusic.utils.rememberPreference
import android.app.SearchManager
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.R
import com.devxrohan.smusic.db.entities.LyricsEntity
import com.devxrohan.smusic.models.MediaMetadata
import com.devxrohan.smusic.ui.component.DefaultDialog
import com.devxrohan.smusic.ui.component.GridMenu
import com.devxrohan.smusic.ui.component.GridMenuItem
import com.devxrohan.smusic.ui.component.ListDialog
import com.devxrohan.smusic.ui.component.TextFieldDialog
import com.devxrohan.smusic.viewmodels.LyricsMenuViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsMenu(
    lyricsProvider: () -> LyricsEntity?,
    mediaMetadataProvider: () -> MediaMetadata,
    onDismiss: () -> Unit,
    onLyricsUpdated: () -> Unit = {}, // NUEVO: Callback para notificar actualización
    navController: androidx.navigation.NavController? = null,
    viewModel: LyricsMenuViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    // AI translation (Google / Puter / Custom need no API key)
    val openRouterApiKey by rememberPreference(OpenRouterApiKey, "")
    val openRouterBaseUrl by rememberPreference(OpenRouterBaseUrlKey, "https://api.puter.com/v1/chat/completions")
    val openRouterModel by rememberPreference(OpenRouterModelKey, "gpt-4o-mini")
    val aiProvider by rememberPreference(AiProviderKey, "Google")
    val deeplApiKey by rememberPreference(DeeplApiKey, "")
    val translateMode by rememberPreference(TranslateModeKey, "Literal")
    var selectedTargetLanguage by rememberPreference(TranslateLanguageKey, "auto")
    var customPromptText by rememberPreference(CustomPromptKey, "")
    var replaceOriginalLyrics by rememberPreference(ReplaceOriginalLyricsWithTranslationKey, false)
    val hasApiKey = when (aiProvider) {
        "DeepL" -> deeplApiKey.isNotBlank()
        "Puter", "Custom", "Google" -> true
        else -> openRouterApiKey.isNotBlank()
    }
    val translationStatus by LyricsTranslationHelper.status.collectAsState()
    val hasTranslations by LyricsTranslationHelper.hasActiveTranslations.collectAsState()
    var showTranslateDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageSelectorDialog by rememberSaveable { mutableStateOf(false) }

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        TextFieldDialog(
            onDismiss = { showEditDialog = false },
            icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
            title = { Text(text = mediaMetadataProvider().title) },
            initialTextFieldValue = TextFieldValue(lyricsProvider()?.lyrics.orEmpty()),
            singleLine = false,
            onDone = {
                database.query {
                    upsert(
                        LyricsEntity(
                            id = mediaMetadataProvider().id,
                            lyrics = it,
                        ),
                    )
                }
                // NUEVO: Notificar que las letras fueron actualizadas
                onLyricsUpdated()
                onDismiss()
            },
        )
    }

    var showSearchDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showSearchResultDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val searchMediaMetadata =
        remember(showSearchDialog) {
            mediaMetadataProvider()
        }
    val (titleField, onTitleFieldChange) =
        rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(
                    text = mediaMetadataProvider().title,
                ),
            )
        }
    val (artistField, onArtistFieldChange) =
        rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(
                    text = mediaMetadataProvider().artists.joinToString { it.name },
                ),
            )
        }

    if (showSearchDialog) {
        DefaultDialog(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            onDismiss = { showSearchDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.search_lyrics)) },
            buttons = {
                TextButton(
                    onClick = { showSearchDialog = false },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        showSearchDialog = false
                        onDismiss()
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_WEB_SEARCH).apply {
                                    putExtra(
                                        SearchManager.QUERY,
                                        "${artistField.text} ${titleField.text} lyrics"
                                    )
                                },
                            )
                        } catch (_: Exception) {
                        }
                    },
                ) {
                    Text(stringResource(R.string.search_online))
                }

                Spacer(Modifier.width(8.dp))

                TextButton(
                    onClick = {
                        viewModel.search(
                            searchMediaMetadata.id,
                            titleField.text,
                            artistField.text,
                            searchMediaMetadata.duration
                        )
                        showSearchResultDialog = true
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            OutlinedTextField(
                value = titleField,
                onValueChange = onTitleFieldChange,
                singleLine = true,
                label = { Text(stringResource(R.string.song_title)) },
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = artistField,
                onValueChange = onArtistFieldChange,
                singleLine = true,
                label = { Text(stringResource(R.string.song_artists)) },
            )
        }
    }

    if (showSearchResultDialog) {
        val results by viewModel.results.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()

        var expandedItemIndex by rememberSaveable {
            mutableStateOf(-1)
        }

        ListDialog(
            onDismiss = { showSearchResultDialog = false },
        ) {
            itemsIndexed(results) { index, result ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.cancelSearch()
                                database.query {
                                    upsert(
                                        LyricsEntity(
                                            id = searchMediaMetadata.id,
                                            lyrics = result.lyrics,
                                        ),
                                    )
                                }
                                // NUEVO: Notificar actualización antes de cerrar
                                onLyricsUpdated()
                                showSearchResultDialog = false
                                onDismiss()
                            }
                            .padding(12.dp)
                            .animateContentSize(),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = result.lyrics,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (index == expandedItemIndex) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = result.providerName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                            )
                            if (result.lyrics.startsWith("[")) {
                                Icon(
                                    painter = painterResource(R.drawable.sync),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier =
                                        Modifier
                                            .padding(start = 4.dp)
                                            .size(18.dp),
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            expandedItemIndex = if (expandedItemIndex == index) -1 else index
                        },
                    ) {
                        Icon(
                            painter = painterResource(if (index == expandedItemIndex) R.drawable.expand_less else R.drawable.expand_more),
                            contentDescription = null,
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (!isLoading && results.isEmpty()) {
                item {
                    Text(
                        text = context.getString(R.string.lyrics_not_found),
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showLanguageSelectorDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val allLanguages = remember {
            AiTranslationLanguages.entries.toList().sortedWith(
                compareBy<Map.Entry<String, String>> { (code, _) ->
                    // Pin Hinglish and common languages at the top
                    when (code) {
                        "auto" -> 0
                        "hi-Latn" -> 1
                        "en" -> 2
                        "hi" -> 3
                        else -> 4
                    }
                }.thenBy { it.value }
            )
        }

        val filteredLanguages = remember(searchQuery) {
            if (searchQuery.isBlank()) {
                allLanguages
            } else {
                allLanguages.filter { (code, name) ->
                    name.contains(searchQuery, ignoreCase = true) || code.contains(searchQuery, ignoreCase = true)
                }
            }
        }

        DefaultDialog(
            onDismiss = { showLanguageSelectorDialog = false },
            icon = { Icon(painter = painterResource(R.drawable.translate), contentDescription = null) },
            title = { Text(stringResource(R.string.translate_to)) },
            buttons = {
                TextButton(onClick = { showLanguageSelectorDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.ui_search_language_2)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
            ) {
                items(items = filteredLanguages, key = { it.key }) { entry ->
                    val code = entry.key
                    val name = entry.value
                    val isSelected = selectedTargetLanguage == code
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable {
                                selectedTargetLanguage = code
                                showLanguageSelectorDialog = false
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (code == "auto") {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(start = 6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.ui_smart),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // AI Lyrics Translation Dialog
    if (showTranslateDialog) {
        val currentLyricsEntity = lyricsProvider()
        val rawLyrics = currentLyricsEntity?.lyrics.orEmpty()
        val isTranslating = translationStatus is LyricsTranslationHelper.TranslationStatus.Translating
        val resolvedTarget = remember(currentLyricsEntity?.id, selectedTargetLanguage) {
            LyricsTranslationHelper.resolveTargetLanguage(
                selectedTargetLanguage,
                LyricsTranslationHelper.parseLyricsToEntries(rawLyrics).map { it.text }
            )
        }
        val isCached = remember(currentLyricsEntity?.id, resolvedTarget) {
            LyricsTranslationHelper.hasCachedTranslation(context, mediaMetadataProvider().id, resolvedTarget)
        }

        DefaultDialog(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            onDismiss = { showTranslateDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.translate),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.lyrics_translation)) },
            buttons = {
                if (translationStatus is LyricsTranslationHelper.TranslationStatus.Success) {
                    Button(
                        onClick = {
                            LyricsTranslationHelper.resetStatus()
                            showTranslateDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.done))
                    }
                } else {
                    TextButton(
                        onClick = { showTranslateDialog = false }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }

                    if (hasTranslations || isCached) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                LyricsTranslationHelper.clearTranslations(
                                    context = context,
                                    songId = mediaMetadataProvider().id
                                )
                                Toast.makeText(context, "Translation cleared", Toast.LENGTH_SHORT).show()
                                showTranslateDialog = false
                            }
                        ) {
                            Text(
                                stringResource(R.string.clear_translation),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    TextButton(
                        modifier = Modifier.sizeIn(minWidth = 72.dp),
                        enabled = !isTranslating && rawLyrics.isNotBlank(),
                        onClick = {
                            if (!hasApiKey) {
                                showTranslateDialog = false
                                onDismiss()
                                navController?.navigate("settings/ai")
                                Toast.makeText(context, "Please configure your AI API key first", Toast.LENGTH_LONG).show()
                                return@TextButton
                            }

                            LyricsTranslationHelper.translateLyrics(
                                rawLyrics = rawLyrics,
                                targetLanguageCode = selectedTargetLanguage,
                                apiKey = if (aiProvider == "DeepL") deeplApiKey else openRouterApiKey,
                                baseUrl = openRouterBaseUrl,
                                model = openRouterModel,
                                mode = translateMode,
                                customPrompt = customPromptText.takeIf { it.isNotBlank() },
                                provider = aiProvider,
                                context = context,
                                songId = mediaMetadataProvider().id,
                                scope = coroutineScope
                            )
                        }
                    ) {
                        Text(
                            text = if (hasTranslations || isCached) "Retry" else "Translate",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // API Key Warning Banner if missing
                if (!hasApiKey) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.ui_api_key_missing),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(R.string.api_key_required_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                            )
                            TextButton(
                                onClick = {
                                    showTranslateDialog = false
                                    onDismiss()
                                    navController?.navigate("settings/ai")
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.auto_awesome),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.ai_settings))
                            }
                        }
                    }
                }

                // Target Language Selector
                Text(
                    text = stringResource(R.string.translate_to),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLanguageSelectorDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.translate),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = AiTranslationLanguages[selectedTargetLanguage] ?: selectedTargetLanguage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            painter = painterResource(R.drawable.expand_more),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Custom Prompt Input
                Text(
                    text = stringResource(R.string.custom_prompt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                OutlinedTextField(
                    value = customPromptText,
                    onValueChange = { customPromptText = it },
                    label = { Text(stringResource(R.string.ui_custom_prompt_optional)) },
                    placeholder = { Text(stringResource(R.string.ui_e_g_translate_into_poetic_hinglish), fontSize = 12.sp) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // Replace Original Lyrics Checkmark / Toggle
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { replaceOriginalLyrics = !replaceOriginalLyrics }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = replaceOriginalLyrics,
                            onCheckedChange = { replaceOriginalLyrics = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.replace_original_lyrics),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.replace_original_lyrics_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Status & Progress Feedback
                when (val status = translationStatus) {
                    is LyricsTranslationHelper.TranslationStatus.Translating -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.ui_translating_lyrics),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = status.logs.lastOrNull() ?: "Processing lines...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(
                                    onClick = { LyricsTranslationHelper.cancelTranslation() }
                                ) {
                                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    is LyricsTranslationHelper.TranslationStatus.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = status.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    is LyricsTranslationHelper.TranslationStatus.Success -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.check_circle),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.ui_translation_applied_successfully),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        LyricsTranslationHelper.resetStatus()
                                        showTranslateDialog = false
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.done), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    else -> {
                        if (hasTranslations || isCached) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.auto_awesome),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.ui_active_translation_in, AiTranslationLanguages[resolvedTarget] ?: resolvedTarget),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    GridMenu(
        contentPadding =
            PaddingValues(
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        GridMenuItem(
            icon = R.drawable.edit,
            title = R.string.edit,
        ) {
            showEditDialog = true
        }
        GridMenuItem(
            icon = R.drawable.cached,
            title = R.string.refetch,
        ) {
            viewModel.refetchLyrics(mediaMetadataProvider(), lyricsProvider())
            // NUEVO: Notificar actualización cuando se recargan las letras
            onLyricsUpdated()
            onDismiss()
        }
        GridMenuItem(
            icon = R.drawable.search,
            title = R.string.search,
        ) {
            showSearchDialog = true
        }
        GridMenuItem(
            icon = R.drawable.translate,
            title = R.string.Translate,
        ) {
            showTranslateDialog = true
        }
    }
}
