package com.devxrohan.smusic

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.devxrohan.smusic.constants.AlbumSortDescendingKey
import com.devxrohan.smusic.constants.MixSortDescendingKey
import com.devxrohan.smusic.utils.LibrarySortPreferenceMigration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibrarySortPreferenceMigrationTest {
    @Test fun legacyOrderIsPreservedAndFutureChangesAreIndependent() = runBlocking {
        for (legacy in listOf(true, false)) {
            val original = mutablePreferencesOf(AlbumSortDescendingKey to legacy)
            assertTrue(LibrarySortPreferenceMigration.shouldMigrate(original))
            val migrated = LibrarySortPreferenceMigration.migrate(original).toMutablePreferences()
            assertEquals(legacy, migrated[MixSortDescendingKey])
            assertEquals(legacy, migrated[AlbumSortDescendingKey])
            migrated[MixSortDescendingKey] = !legacy
            assertEquals(legacy, migrated[AlbumSortDescendingKey])
            assertFalse(LibrarySortPreferenceMigration.shouldMigrate(migrated))
        }
    }
    @Test fun existingSeparateMixPreferenceIsNeverOverwritten() = runBlocking {
        val prefs = mutablePreferencesOf(AlbumSortDescendingKey to false, MixSortDescendingKey to true)
        assertFalse(LibrarySortPreferenceMigration.shouldMigrate(prefs))
        assertEquals(true, LibrarySortPreferenceMigration.migrate(prefs)[MixSortDescendingKey])
    }
    @Test fun freshInstallKeepsDefaultsWithoutCreatingPreferences() = runBlocking {
        val prefs = mutablePreferencesOf()
        assertFalse(LibrarySortPreferenceMigration.shouldMigrate(prefs))
        assertTrue(LibrarySortPreferenceMigration.migrate(prefs).asMap().isEmpty())
    }
}
