package com.devxrohan.smusic.pulse.language

import android.content.Context
import android.util.AtomicFile
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.models.MediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Learns track languages locally from saved lyrics without making metadata network requests. */
internal class PulseRadioLanguages(context: Context, private val database: MusicDatabase) {
    private data class Entry(val evidence: PulseLanguageEvidence?, val until: Long, val lyricsHash: Int?)
    private val file = AtomicFile(File(context.filesDir, "pulse_languages.json"))
    private val cache = linkedMapOf<String, Entry>()
    private val mutex = Mutex()
    private var loaded = false
    private var dirty = false
    private val day = 86_400_000L

    suspend fun <T> filter(seed: MediaMetadata, items: List<T>, trackOf: (T) -> MediaMetadata?): List<T> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                load()
                suspend fun evidence(track: MediaMetadata): PulseLanguageEvidence? {
                    val cached = cache[track.id]?.takeIf { it.until > System.currentTimeMillis() }
                    cached?.evidence?.let { return it }
                    val lyrics = database.lyrics(track.id).first()?.lyrics?.takeIf { it.isNotBlank() } ?: return null
                    val lyricsHash = lyrics.hashCode()
                    if (cached != null && cached.lyricsHash == lyricsHash) return null
                    return try {
                        var finished = false
                        val inferred = withTimeoutOrNull(750) {
                            PulseLyricsLanguageDetector.detect(lyrics).also { finished = true }
                        }
                        // A cancelled or timed-out inference must remain eligible for a later attempt.
                        if (finished) remember(track.id, inferred, if (inferred != null) 30 * day else day, lyricsHash)
                        inferred
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                }

                var seedEvidence = cache[seed.id]?.takeIf { it.until > System.currentTimeMillis() }?.evidence
                val known = mutableMapOf<String, PulseLanguageEvidence?>()
                withTimeoutOrNull(1500) {
                    seedEvidence = evidence(seed)
                    for (item in items) {
                        val track = trackOf(item) ?: continue
                        if (track.id != seed.id) known[track.id] = evidence(track)
                    }
                }
                persist()
                items.filter { item ->
                    val track = trackOf(item)
                    track == null || track.id == seed.id ||
                        PulseLanguagePolicy.compatible(seedEvidence, known[track.id] ?: cache[track.id]
                            ?.takeIf { it.until > System.currentTimeMillis() }?.evidence)
                }
            }
        }

    private fun remember(id: String, evidence: PulseLanguageEvidence?, lifetime: Long, lyricsHash: Int?) {
        cache.remove(id)
        cache[id] = Entry(evidence, System.currentTimeMillis() + lifetime, lyricsHash)
        while (cache.size > 1024) cache.remove(cache.keys.first())
        dirty = true
    }

    private fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val rows = JSONArray(file.openRead().bufferedReader().use { it.readText() })
            for (index in 0 until minOf(rows.length(), 1024)) {
                val row = rows.getJSONObject(index)
                // Keep locally learned results and discard obsolete external-provider cache entries.
                if (row.optString("source") != "lyrics-model") {
                    dirty = true
                    continue
                }
                val until = row.getLong("until")
                if (until <= System.currentTimeMillis()) continue
                val languages = row.optJSONArray("languages")
                val codes = (0 until (languages?.length() ?: 0)).mapNotNull { languages?.optString(it) }.toSet()
                val evidence = codes.takeIf { it.isNotEmpty() }?.let {
                    PulseLanguageEvidence(it, "lyrics-model", row.optDouble("confidence"))
                }
                cache[row.getString("id")] = Entry(evidence, until,
                    row.optInt("lyricsHash").takeIf { row.has("lyricsHash") })
            }
        }
    }

    private fun persist() {
        if (!dirty) return
        val rows = JSONArray()
        cache.forEach { (id, entry) -> rows.put(JSONObject().put("id", id).put("until", entry.until)
            .put("languages", JSONArray(entry.evidence?.languages?.toList().orEmpty()))
            .put("source", "lyrics-model").put("confidence", entry.evidence?.confidence ?: 0.0)
            .put("lyricsHash", entry.lyricsHash)) }
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(rows.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
            dirty = false
        } catch (_: Exception) {
            stream?.let { file.failWrite(it) }
        }
    }
}
