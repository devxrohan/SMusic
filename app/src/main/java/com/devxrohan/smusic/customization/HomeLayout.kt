package com.devxrohan.smusic.customization

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Stable section IDs shared by the Home renderer, editor, preview and profile validator. */
object HomeLayout {
    data class Preset(val name: String, val description: String, val values: Map<String, JsonElement>)
    val sections = linkedMapOf(
        "quick" to "SMusic Quick finds",
        "speed_dial" to "Speed dial",
        "recent" to "The Usuals",
        "youtube" to "YouTube Quick Picks",
        "recommendations" to "Personal recommendations",
        "discover" to "Discover",
        "account" to "YouTube home shelves",
        "forgotten" to "Forgotten favourites",
    )
    val defaultOrder = sections.keys.toList()
    val visibilityKeys = mapOf(
        "speed_dial" to "homeSpeedDialVisible",
        "quick" to "homeQuickPicksVisible", "recent" to "homeKeepListeningVisible",
        "youtube" to "homeYoutubePicksVisible", "recommendations" to "homeRecommendationsVisible",
        "discover" to "homeDiscoverVisible", "account" to "homeAccountShelvesVisible",
        "forgotten" to "homeForgottenFavoritesVisible",
    )
    fun isValid(raw: String): Boolean {
        if (raw in setOf("PICKS_FIRST", "RECENT_FIRST", "YOUTUBE_FIRST")) return true
        val ids = raw.split(',')
        return ids.distinct().size == ids.size && (ids.toSet() == sections.keys || ids.toSet() == sections.keys - "speed_dial")
    }
    fun decode(raw: String?): List<String> = when (raw) {
        "RECENT_FIRST" -> listOf("recent", "quick", "youtube") + defaultOrder.filterNot { it in setOf("recent", "quick", "youtube") }
        "YOUTUBE_FIRST" -> listOf("youtube", "quick", "recent") + defaultOrder.filterNot { it in setOf("recent", "quick", "youtube") }
        null, "PICKS_FIRST" -> defaultOrder
        else -> if (isValid(raw)) raw.split(',').let { if ("speed_dial" in it) it else it.toMutableList().apply { add((indexOf("quick") + 1).coerceAtLeast(0), "speed_dial") } } else defaultOrder
    }
    /** An upper bound, never a request to fabricate or repeat missing recommendations. */
    fun itemLimit(raw: String?): Int? = raw?.toIntOrNull()?.takeIf { it in setOf(6, 12, 18, 24) }
    fun <T> limitItems(items: List<T>, raw: String?): List<T> = itemLimit(raw)?.let(items::take) ?: items

    fun encode(order: List<String>): String = order.joinToString(",").also { require(isValid(it)) }

    private fun preset(name: String, description: String, first: List<String>, visible: Set<String>, count: String, header: String, width: Float, height: Float): Preset {
        val order = first + defaultOrder.filterNot { it in first }
        return Preset(name, description, visibilityKeys.map { (id, key) -> key to JsonPrimitive(id == "speed_dial" || id in visible) }.toMap() + mapOf(
            "homeSectionPriority" to JsonPrimitive(encode(order)), "homeVisibleItems" to JsonPrimitive(count),
            "homeHeaderStyle" to JsonPrimitive(header), "quickPicksCardWidth" to JsonPrimitive(width),
            "quickPicksCardHeight" to JsonPrimitive(height),
        ))
    }
    val presets by lazy {
        listOf(
            preset("Classic", "All sections in the original order", emptyList(), sections.keys, "DEFAULT", "NONE", 196f, 265f),
            preset("Focus", "Recent listening and familiar favourites", listOf("recent", "forgotten", "quick"), setOf("recent", "forgotten", "quick"), "6", "SMUSIC", 160f, 220f),
        )
    }
}
