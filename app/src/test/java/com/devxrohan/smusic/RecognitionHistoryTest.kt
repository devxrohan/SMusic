package com.devxrohan.smusic

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.constants.PauseListenHistoryKey
import com.devxrohan.smusic.constants.PauseRecognitionHistoryKey
import com.devxrohan.smusic.recognition.RecognitionHistory
import com.devxrohan.smusic.shazamkit.models.RecognitionResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class RecognitionHistoryTest {
    @Test fun recognitionDeduplicatesAndHonorsCommittedHistoryPrivacy() = runBlocking {
        val dir = Files.createTempDirectory("smusic-recognition").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { dir.resolve("history.preferences_pb") }
            val repo = RecognitionHistory(store)
            fun result(id: String) = RecognitionResult(id, "Song $id", "Artist", null, null, null, null, null, null, null, null, null, null, null)
            repo.add(result("one")); repo.add(result("one"))
            assertEquals(1, repo.tracks.first().size)
            store.edit { it[PauseListenHistoryKey] = true }
            repo.add(result("two"))
            assertEquals("one", repo.tracks.first().single().id)
            store.edit { it[PauseListenHistoryKey] = false }
            repo.add(result("two"))
            assertEquals("two", repo.tracks.first().first().id)
            repo.remove(repo.tracks.first().first())
            assertEquals(1, repo.tracks.first().size)
            repo.clear()
            store.edit { it[PauseRecognitionHistoryKey] = true }
            repo.add(result("paused-recognition"))
            assertTrue(repo.tracks.first().isEmpty())
            store.edit { it[PauseRecognitionHistoryKey] = false }
            repeat(60) { repo.add(result("limit-$it")) }
            assertEquals(50, repo.tracks.first().size)
            assertEquals("limit-59", repo.tracks.first().first().id)
            repo.clear()
            assertTrue(repo.tracks.first().isEmpty())
        } finally { scope.coroutineContext[Job]?.cancelAndJoin(); dir.deleteRecursively() }
    }
}
