package com.devxrohan.smusic.pulse

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persist versioned Pulse state atomically; missing or corrupt data triggers history backfill. */
object PulseStorage {
    const val FILE_NAME = "pulse_state.json"

    fun file(filesDir: File): File = File(File(filesDir, "pulse").apply { mkdirs() }, FILE_NAME)

    fun load(file: File): PulseState =
        runCatching {
            if (!file.exists()) return PulseState()
            fromJson(JSONObject(file.readText()))
        }.getOrElse { PulseState() }

    fun save(file: File, state: PulseState) {
        check(file.parentFile!!.exists() || file.parentFile!!.mkdirs())
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        java.io.FileOutputStream(tmp).use { output ->
            output.write(toJson(state).toString().toByteArray(Charsets.UTF_8)); output.fd.sync()
        }
        check(tmp.renameTo(file)) { "Could not save Pulse profile" }
    }

    // ---- encode ----

    fun toJson(s: PulseState): JSONObject = JSONObject().apply {
        put("v", s.schemaVersion)
        put("backfillVersion", s.backfillVersion)
        put("totalPlays", s.totalPlays)
        put("appetite", s.discoveryAppetite)
        put("artistVariety", s.artistVariety)
        put("recentCooldownHours", s.recentCooldownHours)
        s.discoveryPreference?.let { put("discoveryPreference", it) }
        put("rotDecay", s.lastRotationDecay)
        put("sessSong", s.sessionLastSong ?: JSONObject.NULL)
        put("sessArtist", s.sessionLastArtist ?: JSONObject.NULL)
        put("sessTs", s.sessionLastTs)
        put("novelShown", s.novelShown)
        put("novelPlayed", s.novelPlayed)
        put("albums", JSONObject().also { o ->
            s.albums.forEach { (k, a) -> o.put(k, JSONArray().put(a.plays).put(a.completions).put(a.lastPlayed)) }
        })
        put("related", doubleMap(s.relatedEdges))
        put("sessRecent", JSONArray(s.sessionRecent))
        put("playlistsMade", JSONArray(s.smartPlaylistIds.toList()))
        put("playlistsDismissed", JSONArray(s.dismissedPlaylists.toList()))
        put("artists", JSONObject().also { o ->
            s.artists.forEach { (k, a) ->
                o.put(k, JSONArray().put(a.name).put(a.plays).put(a.score).put(a.firstPlayed)
                    .put(a.lastPlayed).put(a.skips).put(a.liked))
            }
        })
        put("songs", JSONObject().also { o ->
            s.songs.forEach { (k, t) ->
                o.put(k, JSONArray().put(t.title).put(JSONArray(t.artistIds)).put(t.plays).put(t.replays)
                    .put(t.skips).put(t.completions).put(t.firstPlayed).put(t.lastPlayed).put(t.liked)
                    .put(JSONArray(t.recent)).put(t.skipSeverity))
            }
        })
        put("rotation", doubleMap(s.recentRotation))
        put("cooc", doubleMap(s.artistCooc))
        put("transitions", doubleMap(s.songTransitions))
        put("timeBuckets", JSONObject().also { o ->
            s.timeBuckets.forEach { (b, m) -> o.put(b.name, doubleMap(m)) }
        })
        put("seen", JSONArray(s.seenArtists.toList()))
        put("exposure", JSONObject().also { o ->
            s.exposure.forEach { (k, e) ->
                o.put(k, JSONArray().put(e.shown).put(e.lastShown).put(e.played).put(e.completed).put(e.skipped).put(e.novel)
                    .put(e.source).put(e.shelf).put(e.position).put(e.reason))
            }
        })
        put("moreLikeThis", JSONObject().also { objectValue ->
            s.positiveFeedback.forEach { (id, preference) ->
                objectValue.put(id, JSONArray().put(preference.timestamp).put(JSONArray(preference.artistIds)).put(preference.title))
            }
        })
        put("notForMe", longMap(s.notForMeSongs))
        put("disliked", longMap(s.dislikedArtists))
        put("blocked", JSONArray(s.blockedArtists.toList()))
        put("subscribedArtists", JSONArray(s.subscribedArtists.toList()))
        put("downloadedSongs", JSONArray(s.downloadedSongs.toList()))
    }

    private fun doubleMap(m: Map<String, Double>) = JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, v) } }
    private fun longMap(m: Map<String, Long>) = JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, v) } }

    // ---- decode ----

    fun fromJson(o: JSONObject): PulseState {
        val s = PulseState()
        s.schemaVersion = o.optInt("v", PulseParams.SCHEMA_VERSION)
        s.backfillVersion = o.optInt("backfillVersion", 0)
        s.totalPlays = o.optInt("totalPlays", 0)
        s.discoveryAppetite = o.optDouble("appetite", PulseParams.DISCOVERY_NEUTRAL)
        s.artistVariety = o.optDouble("artistVariety", 0.5).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.5
        s.recentCooldownHours = o.optDouble("recentCooldownHours", 6.0).takeIf { it.isFinite() }?.coerceIn(0.0, 48.0) ?: 6.0
        s.discoveryPreference = if (o.has("discoveryPreference")) o.optDouble("discoveryPreference").takeIf { it.isFinite() && it in 0.1..0.7 } else null
        s.lastRotationDecay = o.optLong("rotDecay", 0L)
        s.sessionLastSong = o.optString("sessSong", "").ifEmpty { null }
        s.sessionLastArtist = o.optString("sessArtist", "").ifEmpty { null }
        s.sessionLastTs = o.optLong("sessTs", 0L)
        s.novelShown = o.optInt("novelShown", 0)
        s.novelPlayed = o.optInt("novelPlayed", 0)
        o.optJSONObject("albums")?.let { j ->
            j.keys().forEach { k ->
                val a = j.getJSONArray(k)
                s.albums[k] = AlbumStats(a.optInt(0), a.optInt(1), a.optLong(2))
            }
        }
        readDoubles(o.optJSONObject("related"), s.relatedEdges)
        o.optJSONArray("sessRecent")?.let { a -> for (i in 0 until a.length()) s.sessionRecent.add(a.getString(i)) }
        o.optJSONArray("playlistsMade")?.let { a -> for (i in 0 until a.length()) s.smartPlaylistIds.add(a.getString(i)) }
        o.optJSONArray("playlistsDismissed")?.let { a -> for (i in 0 until a.length()) s.dismissedPlaylists.add(a.getString(i)) }
        s.graphVersion = 1
        o.optJSONObject("artists")?.let { j ->
            j.keys().forEach { k ->
                val a = j.getJSONArray(k)
                s.artists[k] = ArtistAffinity(a.optString(0), a.optInt(1), a.optDouble(2), a.optLong(3),
                    a.optLong(4), a.optInt(5), a.optBoolean(6))
            }
        }
        o.optJSONObject("songs")?.let { j ->
            j.keys().forEach { k ->
                val a = j.getJSONArray(k)
                val ids = a.optJSONArray(1)?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: emptyList()
                val rec = a.optJSONArray(9)?.let { arr -> MutableList(arr.length()) { arr.getLong(it) } } ?: ArrayList()
                s.songs[k] = SongStats(a.optString(0), ids, a.optInt(2), a.optInt(3), a.optInt(4), a.optInt(5),
                    a.optLong(6), a.optLong(7), a.optBoolean(8), rec, a.optDouble(10, 0.0))
            }
        }
        readDoubles(o.optJSONObject("rotation"), s.recentRotation)
        readDoubles(o.optJSONObject("cooc"), s.artistCooc)
        readDoubles(o.optJSONObject("transitions"), s.songTransitions)
        o.optJSONObject("timeBuckets")?.let { j ->
            j.keys().forEach { name ->
                val b = runCatching { PulseTimeBucket.valueOf(name) }.getOrNull() ?: return@forEach
                val m = HashMap<String, Double>(); readDoubles(j.optJSONObject(name), m); s.timeBuckets[b] = m
            }
        }
        o.optJSONArray("seen")?.let { a -> for (i in 0 until a.length()) s.seenArtists.add(a.getString(i)) }
        o.optJSONObject("exposure")?.let { j ->
            j.keys().forEach { k ->
                val a = j.getJSONArray(k)
                s.exposure[k] = Exposure(
                    a.optInt(0), a.optLong(1), a.optInt(2), a.optInt(3), a.optInt(4), a.optBoolean(5),
                    a.optString(6), a.optString(7), a.optInt(8, -1), a.optString(9)
                )
            }
        }
        o.optJSONObject("moreLikeThis")?.let { preferences ->
            preferences.keys().asSequence().take(500).forEach { id ->
                val entry = preferences.optJSONArray(id) ?: return@forEach
                val timestamp = entry.optLong(0)
                val artists = entry.optJSONArray(1)?.let { a -> List(a.length()) { a.optString(it) }.filter(String::isNotBlank).distinct() }.orEmpty()
                if (id.isNotBlank() && timestamp > 0) s.positiveFeedback[id] = PulsePreference(timestamp, artists, entry.optString(2))
            }
        }
        readLongs(o.optJSONObject("notForMe"), s.notForMeSongs)
        readLongs(o.optJSONObject("disliked"), s.dislikedArtists)
        o.optJSONArray("blocked")?.let { a -> for (i in 0 until a.length()) s.blockedArtists.add(a.getString(i)) }
        o.optJSONArray("subscribedArtists")?.let { a -> for (i in 0 until a.length()) s.subscribedArtists.add(a.getString(i)) }
        o.optJSONArray("downloadedSongs")?.let { a -> for (i in 0 until a.length()) s.downloadedSongs.add(a.getString(i)) }
        return s
    }

    private fun readDoubles(j: JSONObject?, into: MutableMap<String, Double>) {
        j ?: return; j.keys().forEach { into[it] = j.optDouble(it) }
    }

    private fun readLongs(j: JSONObject?, into: MutableMap<String, Long>) {
        j ?: return; j.keys().forEach { into[it] = j.optLong(it) }
    }
}
