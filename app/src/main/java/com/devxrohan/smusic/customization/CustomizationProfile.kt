package com.devxrohan.smusic.customization

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.devxrohan.smusic.constants.*
import com.devxrohan.smusic.ui.screens.settings.DarkMode
import com.devxrohan.smusic.ui.theme.ThemePresets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

enum class CustomizationCategory(val title: String) {
    THEME("Theme & typography"), PLAYER("Player & gestures"), LYRICS("Lyrics"), LAYOUT("Home & navigation")
}

@Serializable
data class CustomizationProfile(
    val schemaVersion: Int = 1,
    val name: String,
    val values: Map<String, JsonElement>,
)

enum class SettingKind { CHOICE, BOOLEAN, NUMBER }

/** Only explicitly registered, non-sensitive settings can enter a shareable profile. */
class CustomizationSetting internal constructor(
    val key: String,
    val title: String,
    val category: CustomizationCategory,
    val kind: SettingKind,
    val default: JsonPrimitive,
    val choices: Map<String, String> = emptyMap(),
    val range: ClosedFloatingPointRange<Float>? = null,
    val read: (Preferences) -> JsonElement,
    private val writeValue: (MutablePreferences, JsonElement) -> Unit,
    private val accepts: (JsonElement) -> Boolean,
) {
    fun validate(value: JsonElement) {
        require(value == JsonNull || accepts(value)) { "Invalid value for $title" }
    }
    fun write(preferences: MutablePreferences, value: JsonElement) {
        validate(value)
        writeValue(preferences, value)
    }
    fun describe(value: JsonElement): String = when {
        value == JsonNull -> "App default"
        key == HomeSectionPriorityKey.name -> "Ready-made layouts, drag to arrange, and live preview"
        kind == SettingKind.CHOICE -> choices[(value as JsonPrimitive).content].orEmpty()
        kind == SettingKind.BOOLEAN -> if ((value as JsonPrimitive).boolean) "On" else "Off"
        else -> (value as JsonPrimitive).content
    }
}

object CustomizationSchema {
    const val MAX_BYTES = 128 * 1024
    val json = Json { prettyPrint = true; ignoreUnknownKeys = false; isLenient = false }

    private fun choice(key: Preferences.Key<String>, title: String, category: CustomizationCategory,
                       default: String, choices: Map<String, String>) = CustomizationSetting(
        key.name, title, category, SettingKind.CHOICE, JsonPrimitive(default), choices,
        read = { it[key]?.let(::JsonPrimitive) ?: JsonNull },
        writeValue = { p, v -> if (v == JsonNull) p.remove(key) else p[key] = (v as JsonPrimitive).content },
        accepts = { it is JsonPrimitive && it.isString && it.content in choices },
    )
    private inline fun <reified T : Enum<T>> enumSetting(key: Preferences.Key<String>, title: String,
                                                category: CustomizationCategory, default: T) =
        choice(key, title, category, default.name, enumValues<T>().associate { it.name to it.name.lowercase().replace('_', ' ') })
    private fun bool(key: Preferences.Key<Boolean>, title: String, category: CustomizationCategory, default: Boolean) =
        CustomizationSetting(key.name, title, category, SettingKind.BOOLEAN, JsonPrimitive(default),
            read = { it[key]?.let(::JsonPrimitive) ?: JsonNull },
            writeValue = { p, v -> if (v == JsonNull) p.remove(key) else p[key] = (v as JsonPrimitive).boolean },
            accepts = { it is JsonPrimitive && !it.isString && it.booleanOrNull != null })
    private fun number(key: Preferences.Key<Float>, title: String, category: CustomizationCategory,
                       default: Float, range: ClosedFloatingPointRange<Float>) =
        CustomizationSetting(key.name, title, category, SettingKind.NUMBER, JsonPrimitive(default), range = range,
            read = { it[key]?.let(::JsonPrimitive) ?: JsonNull },
            writeValue = { p, v -> if (v == JsonNull) p.remove(key) else p[key] = (v as JsonPrimitive).float },
            accepts = { it is JsonPrimitive && !it.isString && it.floatOrNull?.let { n -> n.isFinite() && n in range } == true })

    val settings = listOf(
        enumSetting(DarkModeKey, "Dark theme", CustomizationCategory.THEME, DarkMode.AUTO),
        choice(ThemePresetKey, "Theme preset", CustomizationCategory.THEME, ThemePresets.SMUSIC_DEFAULT.id,
            ThemePresets.ALL.associate { it.id to it.displayName }),
        choice(AppFontKey, "App font", CustomizationCategory.THEME, AppFont.APPLE.key,
            AppFont.entries.associate { it.key to it.title }),
        number(AppTextScaleKey, "Text size", CustomizationCategory.THEME, 1f, 0.9f..1.2f),
        enumSetting(PlayerScreenStyleKey, "Player style", CustomizationCategory.PLAYER, PlayerScreenStyle.CLASSIC),
        enumSetting(SliderStyleKey, "Seekbar style", CustomizationCategory.PLAYER, SliderStyle.SLIM),
        choice(MiniPlayerProgressPositionKey, "Progress bar position", CustomizationCategory.PLAYER, "AUTO",
            mapOf("AUTO" to "Match player style", "TOP" to "Top", "BOTTOM" to "Bottom")),
        bool(MiniPlayerProgressKey, "Mini-player progress bar", CustomizationCategory.PLAYER, true),
        bool(MiniPlayerSwipeKey, "Swipe mini-player to change track", CustomizationCategory.PLAYER, true),
        bool(SwipeThumbnailKey, "Swipe artwork to change track", CustomizationCategory.PLAYER, true),
        bool(ShowLyricsKey, "Show player lyrics", CustomizationCategory.LYRICS, false),
        enumSetting(LyricsScreenStyleKey, "Lyrics style", CustomizationCategory.LYRICS, LyricsScreenStyle.LYRICS_2),
        choice(LyricsColorStyleKey, "Lyrics colour", CustomizationCategory.LYRICS, "THEME",
            mapOf("THEME" to "Follow theme", "SOLID" to "Solid colour", "GRADIENT" to "Gradient")),
        CustomizationSetting(LyricsColorPrimaryKey.name, "Primary lyrics colour", CustomizationCategory.LYRICS, SettingKind.CHOICE, JsonPrimitive("FFFFFF"),
            read = { it[LyricsColorPrimaryKey]?.let(::JsonPrimitive) ?: JsonNull },
            writeValue = { p, v -> if (v == JsonNull) p.remove(LyricsColorPrimaryKey) else p[LyricsColorPrimaryKey] = v.jsonPrimitive.content },
            accepts = { it is JsonPrimitive && it.isString && Regex("[0-9a-fA-F]{6}").matches(it.content) }),
        CustomizationSetting(LyricsColorSecondaryKey.name, "Secondary lyrics colour", CustomizationCategory.LYRICS, SettingKind.CHOICE, JsonPrimitive("C084FC"),
            read = { it[LyricsColorSecondaryKey]?.let(::JsonPrimitive) ?: JsonNull },
            writeValue = { p, v -> if (v == JsonNull) p.remove(LyricsColorSecondaryKey) else p[LyricsColorSecondaryKey] = v.jsonPrimitive.content },
            accepts = { it is JsonPrimitive && it.isString && Regex("[0-9a-fA-F]{6}").matches(it.content) }),
        choice(LyricsAlignmentKey, "Lyrics alignment", CustomizationCategory.LYRICS, "AUTO",
            mapOf("AUTO" to "Recording / duet layout", "START" to "Left aligned", "CENTER" to "Centred", "END" to "Right aligned")),
        number(LyricsOffsetMsKey, "Lyrics sync offset (milliseconds)", CustomizationCategory.LYRICS, 0f, -10000f..10000f),
        number(LyricsTextSizeKey, "Lyrics text size", CustomizationCategory.LYRICS, 26f, 14f..48f),
        number(LyricsLineSpacingKey, "Lyrics line spacing", CustomizationCategory.LYRICS, 1.3f, 1f..2.5f),
        bool(LyricsScrollKey, "Auto-scroll lyrics", CustomizationCategory.LYRICS, true),
        bool(ReplaceOriginalLyricsWithTranslationKey, "Replace original with translation", CustomizationCategory.LYRICS, false),
        bool(HomeRecommendationsVisibleKey, "Show personal recommendations", CustomizationCategory.LAYOUT, true),
        bool(HomeDiscoverVisibleKey, "Show Discover", CustomizationCategory.LAYOUT, true),
        bool(HomeAccountShelvesVisibleKey, "Show YouTube home shelves", CustomizationCategory.LAYOUT, true),
        bool(HomeForgottenFavoritesVisibleKey, "Show forgotten favourites", CustomizationCategory.LAYOUT, true),
        CustomizationSetting(HomeSectionPriorityKey.name, "Home layout · arrange & preview", CustomizationCategory.LAYOUT,
            SettingKind.CHOICE, JsonPrimitive("PICKS_FIRST"),
            read = { it[HomeSectionPriorityKey]?.let(::JsonPrimitive) ?: JsonNull },
            writeValue = { p, v -> if (v == JsonNull) p.remove(HomeSectionPriorityKey) else p[HomeSectionPriorityKey] = (v as JsonPrimitive).content },
            accepts = { it is JsonPrimitive && it.isString && HomeLayout.isValid(it.content) }),
        choice(HomeVisibleItemsKey, "Items per Home section", CustomizationCategory.LAYOUT, "DEFAULT",
            mapOf("DEFAULT" to "App default", "6" to "Up to 6 items", "12" to "Up to 12 items", "18" to "Up to 18 items", "24" to "Up to 24 items")),
        bool(ProfileBeatMotionKey, "Profile artwork motion", CustomizationCategory.LAYOUT, true),
        bool(HomeSpeedDialVisibleKey, "Show Speed dial", CustomizationCategory.LAYOUT, true),
        bool(HomeQuickPicksVisibleKey, "Show SMusic Quick finds", CustomizationCategory.LAYOUT, true),
        bool(HomeKeepListeningVisibleKey, "Show The Usuals", CustomizationCategory.LAYOUT, true),
        bool(HomeYoutubePicksVisibleKey, "Show YouTube Quick Picks", CustomizationCategory.LAYOUT, true),
        enumSetting(HomeHeaderStyleKey, "Home header", CustomizationCategory.LAYOUT, HomeHeaderStyle.NONE),
        enumSetting(GridItemsSizeKey, "Library grid size", CustomizationCategory.LAYOUT, GridItemSize.BIG),
        enumSetting(NavBarStyleKey, "Navigation style", CustomizationCategory.LAYOUT, NavBarStyle.FLOATING),
        number(NavBarScaleKey, "Navigation scale", CustomizationCategory.LAYOUT, 1.11f, 0.75f..1.25f),
        number(QuickPicksCardWidthKey, "Quick finds card width", CustomizationCategory.LAYOUT, 196f, 120f..360f),
        number(QuickPicksCardHeightKey, "Quick finds card height", CustomizationCategory.LAYOUT, 265f, 180f..440f),
    )
    val byKey = settings.associateBy { it.key }

    fun validate(profile: CustomizationProfile): CustomizationProfile {
        require(profile.schemaVersion == 1) { "Unsupported profile version: ${profile.schemaVersion}" }
        require(profile.name.isNotBlank() && profile.name.length <= 60 && profile.name.none { it.isISOControl() }) { "Use a profile name of 1–60 characters" }
        require(profile.values.isNotEmpty() && profile.values.size <= settings.size) { "Profile has no supported settings" }
        profile.values.forEach { (key, value) ->
            val setting = byKey[key] ?: error("Unsupported profile setting: $key")
            setting.validate(value)
        }
        return profile
    }
    fun decode(text: String): CustomizationProfile {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Profile file is too large" }
        return validate(json.decodeFromString(CustomizationProfile.serializer(), text))
    }
    fun encode(profile: CustomizationProfile): String = json.encodeToString(CustomizationProfile.serializer(), validate(profile))
    fun snapshot(preferences: Preferences, name: String = "My profile") =
        CustomizationProfile(name = name, values = settings.associate { setting ->
            val value = setting.read(preferences)
            setting.key to if (runCatching { setting.validate(value) }.isSuccess) value else JsonNull
        })

    /** Missing keys mean leave unchanged; explicit null means return to the existing app default. */
    fun apply(preferences: MutablePreferences, profile: CustomizationProfile, categories: Set<CustomizationCategory>) {
        validate(profile) // Validate the entire file before any mutation, including unselected categories.
        require(categories.isNotEmpty()) { "Select at least one category" }
        profile.values.forEach { (key, value) ->
            byKey.getValue(key).takeIf { it.category in categories }?.write(preferences, value)
        }
    }
}
