package com.devxrohan.smusic

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.constants.*
import com.devxrohan.smusic.customization.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CustomizationRepositoryTest {
    @Test fun savedProfilesPersistAndApplyingUpdatesOnlySelectedPreferences() = runBlocking {
        val directory = Files.createTempDirectory("smusic-profile").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { directory.resolve("test.preferences_pb") }
            store.edit { it[DarkModeKey] = "OFF"; it[InnerTubeCookieKey] = "secret" }
            val repo = CustomizationRepository(store)
            val profile = CustomizationProfile(name = "Night", values = mapOf(
                DarkModeKey.name to JsonPrimitive("ON"), PlayerScreenStyleKey.name to JsonPrimitive("PAPER")))
            repo.save(profile)
            assertEquals(profile, CustomizationRepository(store).profiles.first().single())
            repo.apply(profile, setOf(CustomizationCategory.PLAYER))
            val preferences = store.data.first()
            assertEquals("PAPER", preferences[PlayerScreenStyleKey])
            assertEquals("OFF", preferences[DarkModeKey])
            assertEquals("secret", preferences[InnerTubeCookieKey])
            var rejected = false
            try { repo.save(profile.copy(name = "night")) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(1, repo.profiles.first().size)
            repo.delete("Night")
            assertTrue(repo.profiles.first().isEmpty())
            assertEquals("PAPER", store.data.first()[PlayerScreenStyleKey])
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
