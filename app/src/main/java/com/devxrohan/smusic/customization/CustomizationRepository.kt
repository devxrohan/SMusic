package com.devxrohan.smusic.customization

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.constants.CustomizationProfilesKey
import com.devxrohan.smusic.utils.dataStoreCache
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

class CustomizationRepository(private val store: DataStore<Preferences>) {
    private val serializer = ListSerializer(CustomizationProfile.serializer())
    private fun decodeSaved(raw: String?): List<CustomizationProfile> =
        if (raw == null) emptyList() else CustomizationSchema.json.decodeFromString(serializer, raw)
            .also { list -> require(list.size <= 50) { "Too many saved profiles" }; list.forEach(CustomizationSchema::validate) }

    val profiles = store.data.map { decodeSaved(it[CustomizationProfilesKey]) }
    suspend fun snapshot() = CustomizationSchema.snapshot(store.data.first())

    suspend fun save(profile: CustomizationProfile) {
        CustomizationSchema.validate(profile)
        store.edit { prefs ->
            val saved = decodeSaved(prefs[CustomizationProfilesKey])
            require(saved.none { it.name.equals(profile.name, ignoreCase = true) }) { "That name is already saved. Choose another name." }
            require(saved.size < 50) { "You can save up to 50 profiles" }
            prefs[CustomizationProfilesKey] = CustomizationSchema.json.encodeToString(serializer, saved + profile)
        }
    }

    suspend fun delete(name: String) {
        store.edit { prefs ->
            prefs[CustomizationProfilesKey] = CustomizationSchema.json.encodeToString(serializer,
                decodeSaved(prefs[CustomizationProfilesKey]).filterNot { it.name == name })
        }
    }

    suspend fun apply(profile: CustomizationProfile, categories: Set<CustomizationCategory>) {
        val committed = store.edit { CustomizationSchema.apply(it, profile, categories) }
        val raw = committed.asMap().mapKeys { it.key.name }
        // Existing playback/settings code still uses the synchronous cache. Publish only after
        // DataStore commits, and only the keys actually touched by this profile.
        profile.values.keys.filter { CustomizationSchema.byKey.getValue(it).category in categories }
            .forEach { dataStoreCache[it] = raw[it] }
    }
}
