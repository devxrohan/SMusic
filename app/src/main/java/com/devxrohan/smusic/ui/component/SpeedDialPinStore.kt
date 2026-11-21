package com.devxrohan.smusic.ui.component

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.devxrohan.smusic.customization.SpeedDialOrder
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.dataStoreCache

internal val SpeedDialOrderKey = stringPreferencesKey("homeSpeedDialOrder")

/** Membership and chronology commit together, including a one-time legacy-order migration. */
internal suspend fun Context.toggleSpeedDialPin(key: String, collection: SpeedDialCollection? = null): Boolean {
    var added = false
    val saved = dataStore.edit { prefs ->
        val songs = prefs[SpeedDialSongsKey].orEmpty()
        val collections = prefs[SpeedDialCollectionsKey].orEmpty()
        val decoded = collections.mapNotNull(SpeedDialCollection::decode).sortedBy { it.title }
        val available = songs.sorted().map { "song:$it" } + decoded.map { it.route }
        val order = SpeedDialOrder.reconcile(SpeedDialOrder.decode(prefs[SpeedDialOrderKey] ?: "[]"), available)
        added = key !in order
        if (key.startsWith("song:")) {
            val id = key.removePrefix("song:")
            prefs[SpeedDialSongsKey] = if (added) songs + id else songs - id
        } else {
            require(collection != null && collection.route == key)
            val remaining = collections.filterNot { SpeedDialCollection.decode(it)?.route == key }.toSet()
            prefs[SpeedDialCollectionsKey] = if (added) remaining + collection.encode() else remaining
        }
        prefs[SpeedDialOrderKey] = SpeedDialOrder.encode(SpeedDialOrder.toggle(order, key))
    }
    dataStoreCache[SpeedDialSongsKey.name] = saved[SpeedDialSongsKey].orEmpty()
    dataStoreCache[SpeedDialCollectionsKey.name] = saved[SpeedDialCollectionsKey].orEmpty()
    dataStoreCache[SpeedDialOrderKey.name] = saved[SpeedDialOrderKey] ?: "[]"
    return added
}
