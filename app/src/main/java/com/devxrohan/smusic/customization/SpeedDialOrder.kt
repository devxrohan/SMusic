package com.devxrohan.smusic.customization

import kotlinx.serialization.json.*

/** Stored chronology spans songs and collections; legacy pins retain their visible order. */
internal object SpeedDialOrder {
    fun decode(raw: String): List<String> = runCatching {
        Json.parseToJsonElement(raw).jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    }.getOrDefault(emptyList())
    fun encode(keys: List<String>): String = JsonArray(keys.distinct().map(::JsonPrimitive)).toString()
    fun reconcile(stored: List<String>, available: List<String>): List<String> {
        val valid = available.toSet()
        val retained = stored.filter { it in valid }.distinct()
        val retainedSet = retained.toHashSet()
        return retained + available.filterNot { it in retainedSet }.distinct()
    }
    fun toggle(order: List<String>, key: String): List<String> =
        if (key in order) order.filterNot { it == key } else order + key
}
