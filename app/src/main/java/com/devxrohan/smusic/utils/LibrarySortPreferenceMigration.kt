package com.devxrohan.smusic.utils

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.devxrohan.smusic.constants.AlbumSortDescendingKey
import com.devxrohan.smusic.constants.MixSortDescendingKey

/** Previously both screens wrote albumSortDescending. Preserve that value for each screen. */
internal object LibrarySortPreferenceMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[AlbumSortDescendingKey] != null && currentData[MixSortDescendingKey] == null

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        if (this[MixSortDescendingKey] == null) currentData[AlbumSortDescendingKey]?.let { this[MixSortDescendingKey] = it }
    }

    override suspend fun cleanUp() = Unit
}
