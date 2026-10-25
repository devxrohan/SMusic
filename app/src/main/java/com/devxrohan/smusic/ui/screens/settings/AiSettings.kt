package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AiProviderKey
import com.devxrohan.smusic.constants.AutoTranslateKey
import com.devxrohan.smusic.constants.CustomPromptKey
import com.devxrohan.smusic.constants.DeeplApiKey
import com.devxrohan.smusic.constants.DeeplFormalityKey
import com.devxrohan.smusic.constants.AiTranslationLanguages
import com.devxrohan.smusic.constants.OpenRouterApiKey
import com.devxrohan.smusic.constants.OpenRouterBaseUrlKey
import com.devxrohan.smusic.constants.OpenRouterModelKey
import com.devxrohan.smusic.constants.ReplaceOriginalLyricsWithTranslationKey
import com.devxrohan.smusic.constants.TranslateLanguageKey
import com.devxrohan.smusic.constants.TranslateModeKey
import com.devxrohan.smusic.ui.component.EditTextPreference
import com.devxrohan.smusic.ui.component.ListDialog
import com.devxrohan.smusic.ui.component.ListPreference
import com.devxrohan.smusic.ui.component.PreferenceEntry
import com.devxrohan.smusic.ui.component.SettingsGeneralCategory
import com.devxrohan.smusic.ui.component.SettingsPage
import com.devxrohan.smusic.ui.component.SwitchPreference
import androidx.compose.ui.res.stringResource
import com.devxrohan.smusic.constants.AiRecommendationsKey
import com.devxrohan.smusic.ui.component.RefreshAiRecommendationDialog
import com.devxrohan.smusic.utils.rememberPreference
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Button
import com.devxrohan.smusic.constants.AiListeningNotificationsKey
import com.devxrohan.smusic.ai.AiListeningInsightWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var listeningInsights by rememberPreference(AiListeningNotificationsKey, false)
    LaunchedEffect(listeningInsights) {
        AiListeningInsightWorker.setEnabled(context.applicationContext, listeningInsights)
    }

    var aiProvider by rememberPreference(AiProviderKey, "Puter")
    var openRouterApiKey by rememberPreference(OpenRouterApiKey, "")
    var openRouterBaseUrl by rememberPreference(OpenRouterBaseUrlKey, "https://api.puter.com/v1/chat/completions")
    var openRouterModel by rememberPreference(OpenRouterModelKey, "gpt-4o-mini")
    var translateLanguage by rememberPreference(TranslateLanguageKey, "auto")
    var translateMode by rememberPreference(TranslateModeKey, "Literal")
    var customPrompt by rememberPreference(CustomPromptKey, "")
    var autoTranslate by rememberPreference(AutoTranslateKey, false)
    var replaceOriginalLyrics by rememberPreference(ReplaceOriginalLyricsWithTranslationKey, false)
    var deeplApiKey by rememberPreference(DeeplApiKey, "")
    var aiRecommendations by rememberPreference(AiRecommendationsKey, false)
    var showRefreshAiDialog by remember { mutableStateOf(false) }

    val aiProviders = listOf(
        "OpenRouter", "OpenAI", "Perplexity", "Claude", "Gemini",
        "XAi", "Mistral", "Nvidia", "OrcaRouter", "Groq",
        "Puter", "DeepL", "Custom"
    )

    val modelsByProvider = mapOf(
        "OpenRouter" to listOf(
            "google/gemini-2.5-flash-lite",
            "google/gemini-2.5-flash",
            "deepseek/deepseek-chat",
            "openai/gpt-4o-mini",
            "x-ai/grok-4.1-fast",
            "meta-llama/llama-3.3-70b-instruct",
            "Custom"
        ),
        "OpenAI" to listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "gpt-4-turbo",
            "Custom"
        ),
        "Perplexity" to listOf(
            "sonar",
            "sonar-pro",
            "sonar-reasoning",
            "Custom"
        ),
        "Claude" to listOf(
            "claude-3-5-haiku-latest",
            "claude-3-5-sonnet-latest",
            "Custom"
        ),
        "Gemini" to listOf(
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "Custom"
        ),
        "XAi" to listOf(
            "grok-2-latest",
            "grok-beta",
            "Custom"
        ),
        "Mistral" to listOf(
            "mistral-small-latest",
            "mistral-large-latest",
            "codestral-latest",
            "Custom"
        ),
        "Nvidia" to listOf(
            "meta/llama-3.1-70b-instruct",
            "meta/llama-3.1-8b-instruct",
            "mistralai/mistral-large-2-instruct",
            "Custom"
        ),
        "OrcaRouter" to listOf(
            "gpt-4o-mini",
            "claude-3-5-sonnet",
            "Custom"
        ),
        "Groq" to listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "gemma2-9b-it",
            "Custom"
        ),
        "Puter" to listOf(
            "gpt-4o-mini",
            "claude-3-5-sonnet",
            "deepseek-chat",
            "Custom"
        ),
        "DeepL" to listOf("default"),
        "Custom" to listOf("Custom")
    )

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showCustomModelDialog by remember { mutableStateOf(false) }
    var showLanguageSearchDialog by remember { mutableStateOf(false) }

    // API Key entry dialog with hide/show toggle and link to get key
    if (showApiKeyDialog) {
        var keyInput by remember { mutableStateOf(if (aiProvider == "DeepL") deeplApiKey else openRouterApiKey) }
        var isPasswordVisible by remember { mutableStateOf(false) }

        val portalUrl = when (aiProvider) {
            "OpenRouter" -> "https://openrouter.ai/keys"
            "OpenAI" -> "https://platform.openai.com/api-keys"
            "Perplexity" -> "https://www.perplexity.ai/settings/api"
            "Claude" -> "https://console.anthropic.com/settings/keys"
            "Gemini" -> "https://aistudio.google.com/app/apikey"
            "XAi" -> "https://console.x.ai/"
            "Mistral" -> "https://console.mistral.ai/api-keys/"
            "Nvidia" -> "https://build.nvidia.com/"
            "OrcaRouter" -> "https://orcarouter.com/"
            "Groq" -> "https://console.groq.com/keys"
            "Puter" -> "https://puter.com/"
            "DeepL" -> "https://www.deepl.com/pro-api"
            else -> null
        }

        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.ui_api_key_2, aiProvider), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.ui_enter_your_api_key_it_is, aiProvider),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        shape = RoundedCornerShape(18.dp),
                        label = { Text(stringResource(R.string.ui_api_key)) },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    painter = painterResource(if (isPasswordVisible) R.drawable.visibility else R.drawable.visibility_off),
                                    contentDescription = stringResource(R.string.ui_toggle_key_visibility)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (portalUrl != null) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { uriHandler.openUri(portalUrl) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(R.string.ui_get_key, aiProvider))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (aiProvider == "DeepL") {
                            deeplApiKey = keyInput.trim()
                        } else {
                            openRouterApiKey = keyInput.trim()
                        }
                        showApiKeyDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCustomModelDialog) {
        var customModelInput by remember { mutableStateOf(openRouterModel) }
        AlertDialog(
            onDismissRequest = { showCustomModelDialog = false },
            title = { Text(stringResource(R.string.ui_custom_model_name)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.ui_enter_the_exact_model_identifier_e),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customModelInput,
                        onValueChange = { customModelInput = it },
                        label = { Text(stringResource(R.string.ui_model_id)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customModelInput.isNotBlank()) {
                            openRouterModel = customModelInput.trim()
                        }
                        showCustomModelDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomModelDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Searchable language selector dialog
    if (showLanguageSearchDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredLanguages = remember(searchQuery) {
            AiTranslationLanguages.filter { (key, value) ->
                key.contains(searchQuery, ignoreCase = true) || value.contains(searchQuery, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { showLanguageSearchDialog = false },
            title = { Text(stringResource(R.string.ui_target_language)) },
            text = {
                Column(modifier = Modifier.height(400.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.ui_search_language)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        for ((code, name) in filteredLanguages) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        translateLanguage = code
                                        showLanguageSearchDialog = false
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = translateLanguage == code,
                                    onClick = {
                                        translateLanguage = code
                                        showLanguageSearchDialog = false
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageSearchDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    SettingsPage(
        title = stringResource(R.string.ui_ai_integration),
        navController = navController,
        scrollBehavior = scrollBehavior
    ) {
        SettingsGeneralCategory(
            title = stringResource(R.string.ui_ai_provider_amp_model),
            items = listOf(
                {
                    ListPreference(
                        title = { Text(stringResource(R.string.ui_ai_provider)) },
                        icon = { Icon(painterResource(R.drawable.auto_awesome), contentDescription = null) },
                        selectedValue = aiProvider,
                        values = aiProviders,
                        valueText = { it },
                        onValueSelected = { selected ->
                            aiProvider = selected
                            // Auto-set default recommended model for chosen provider
                            when (selected) {
                                "OpenRouter" -> {
                                    openRouterModel = "google/gemini-2.5-flash-lite"
                                    openRouterBaseUrl = "https://openrouter.ai/api/v1/chat/completions"
                                }
                                "OpenAI" -> {
                                    openRouterModel = "gpt-4o-mini"
                                    openRouterBaseUrl = "https://api.openai.com/v1/chat/completions"
                                }
                                "Perplexity" -> {
                                    openRouterModel = "sonar"
                                    openRouterBaseUrl = "https://api.perplexity.ai/chat/completions"
                                }
                                "Claude" -> {
                                    openRouterModel = "claude-3-5-haiku-latest"
                                    openRouterBaseUrl = "https://api.anthropic.com/v1/messages"
                                }
                                "Gemini" -> {
                                    openRouterModel = "gemini-2.5-flash-lite"
                                    openRouterBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
                                }
                                "XAi" -> {
                                    openRouterModel = "grok-2-latest"
                                    openRouterBaseUrl = "https://api.x.ai/v1/chat/completions"
                                }
                                "Mistral" -> {
                                    openRouterModel = "mistral-small-latest"
                                    openRouterBaseUrl = "https://api.mistral.ai/v1/chat/completions"
                                }
                                "Nvidia" -> {
                                    openRouterModel = "meta/llama-3.1-70b-instruct"
                                    openRouterBaseUrl = "https://integrate.api.nvidia.com/v1/chat/completions"
                                }
                                "OrcaRouter" -> {
                                    openRouterModel = "gpt-4o-mini"
                                    openRouterBaseUrl = "https://api.orcarouter.com/v1/chat/completions"
                                }
                                "Groq" -> {
                                    openRouterModel = "llama-3.3-70b-versatile"
                                    openRouterBaseUrl = "https://api.groq.com/openai/v1/chat/completions"
                                }
                                "Puter" -> {
                                    openRouterModel = "gpt-4o-mini"
                                    openRouterBaseUrl = "https://api.puter.com/v1/chat/completions"
                                }
                                "DeepL" -> {
                                    openRouterBaseUrl = "https://api.deepl.com/v2/translate"
                                }
                            }
                        }
                    )
                },
                {
                    val currentKey = if (aiProvider == "DeepL") deeplApiKey else openRouterApiKey
                    val masked = if (currentKey.isNotBlank()) "••••••••" + currentKey.takeLast(4) else "Not configured"
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ui_api_key)) },
                        description = masked,
                        icon = { Icon(painterResource(R.drawable.lock), contentDescription = null) },
                        onClick = { showApiKeyDialog = true }
                    )
                },
                {
                    if (aiProvider != "DeepL") {
                        val availableModels = modelsByProvider[aiProvider] ?: listOf("Custom")
                        ListPreference(
                            title = { Text(stringResource(R.string.ui_model)) },
                            icon = { Icon(painterResource(R.drawable.tune), contentDescription = null) },
                            selectedValue = if (openRouterModel in availableModels) openRouterModel else "Custom",
                            values = availableModels,
                            valueText = { it },
                            onValueSelected = { selected ->
                                if (selected == "Custom") {
                                    showCustomModelDialog = true
                                } else {
                                    openRouterModel = selected
                                }
                            }
                        )
                    }
                },
                {
                    if (aiProvider == "Custom") {
                        EditTextPreference(
                            title = { Text(stringResource(R.string.ui_base_url)) },
                            icon = { Icon(painterResource(R.drawable.language), contentDescription = null) },
                            value = openRouterBaseUrl,
                            onValueChange = { openRouterBaseUrl = it }
                        )
                    }
                }
            )
        )

        SettingsGeneralCategory(
            title = stringResource(R.string.lyrics_translation),
            items = listOf(
                {
                    val currentLangName: String = AiTranslationLanguages[translateLanguage] ?: translateLanguage
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ui_default_target_language)) },
                        description = currentLangName,
                        icon = { Icon(painterResource(R.drawable.translate), contentDescription = null) },
                        onClick = { showLanguageSearchDialog = true }
                    )
                },
                {
                    ListPreference(
                        title = { Text(stringResource(R.string.ui_translation_mode)) },
                        icon = { Icon(painterResource(R.drawable.translate), contentDescription = null) },
                        selectedValue = translateMode,
                        values = listOf("Literal", "Romanized", "Transcribed", "Custom"),
                        valueText = {
                            when (it) {
                                "Literal" -> "Literal (Accurate & Poetic)"
                                "Romanized" -> "Romanized (Simple English Alphabet)"
                                "Transcribed" -> "Transcribed (Phonetic Native Script)"
                                "Custom" -> "Custom Prompt"
                                else -> it
                            }
                        },
                        onValueSelected = { translateMode = it }
                    )
                },
                {
                    EditTextPreference(
                        title = { Text(stringResource(R.string.ui_custom_prompt_instructions)) },
                        icon = { Icon(painterResource(R.drawable.edit), contentDescription = null) },
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        singleLine = false
                    )
                },
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.ui_auto_translate_lyrics)) },
                        description = stringResource(R.string.ui_automatically_trigger_ai_translation_when_ope),
                        icon = { Icon(painterResource(R.drawable.cached), contentDescription = null) },
                        checked = autoTranslate,
                        onCheckedChange = { autoTranslate = it }
                    )
                },
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.ui_replace_original_lyrics)) },
                        description = stringResource(R.string.replace_original_lyrics_desc),
                        icon = { Icon(painterResource(R.drawable.sync), contentDescription = null) },
                        checked = replaceOriginalLyrics,
                        onCheckedChange = { replaceOriginalLyrics = it }
                    )
                }
            )
        )

        SettingsGeneralCategory(
            title = stringResource(R.string.ai_recommendations),
            items = listOf(
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.ai_recommendations)) },
                        description = stringResource(R.string.ai_recommendations_summary),
                        icon = { Icon(painterResource(R.drawable.auto_awesome), contentDescription = null) },
                        checked = aiRecommendations,
                        onCheckedChange = { aiRecommendations = it }
                    )
                },
                {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.refresh_ai_recommendation)) },
                        description = stringResource(R.string.refresh_ai_recommendation_summary),
                        icon = { Icon(painterResource(R.drawable.cached), contentDescription = null) },
                        onClick = { showRefreshAiDialog = true },
                        isEnabled = aiRecommendations
                    )
                }
            )
        )

        SettingsGeneralCategory(
            title = stringResource(R.string.ui_listening_insights),
            items = listOf(
                {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.ui_daily_ai_listening_insight)) },
                        description = stringResource(R.string.ui_opt_in_to_a_daily_notification),
                        icon = { Icon(painterResource(R.drawable.auto_awesome), contentDescription = null) },
                        checked = listeningInsights,
                        onCheckedChange = { listeningInsights = it }
                    )
                },
                {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ui_generate_insight_now)) },
                        description = stringResource(R.string.ui_send_one_insight_notification_using_your),
                        icon = { Icon(painterResource(R.drawable.cached), contentDescription = null) },
                        onClick = { AiListeningInsightWorker.runNow(context.applicationContext) },
                        isEnabled = listeningInsights
                    )
                }
            )
        )
    }

    if (showRefreshAiDialog) {
        RefreshAiRecommendationDialog(
            onDismiss = { showRefreshAiDialog = false }
        )
    }
}
