package com.devxrohan.smusic.db

import android.content.Context
import com.devxrohan.smusic.db.entities.SearchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Recent searches are LOCAL ONLY now (Room `search_history`). There is no guest/account split any more.
 *
 * This class only reads what the previous builds kept in SharedPreferences so that it can be merged into Room
 * once ([MIGRATED]); everything that belonged to a connected YouTube account is discarded.
 */
class SearchHistoryScopeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("scoped_search_history", Context.MODE_PRIVATE)

    fun isMigrated(): Boolean = prefs.getBoolean(MIGRATED, false)

    /** Old guest searches, newest first. */
    @Synchronized fun legacyLocalHistory(): List<String> = decode(prefs.getString("history_guest", null))

    /** Old guest searches the user had removed with X (they must not come back from Room). */
    @Synchronized fun legacyRemoved(): List<String> = decode(prefs.getString("history_hidden_guest", null))

    /** Marks the merge as done and throws away every account-scoped entry (tokens, tombstones, cached remote rows). */
    @Synchronized fun finishMigration() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.contains("account_") }.forEach { editor.remove(it) }
        editor.remove("history_guest").remove("history_hidden_guest").remove("history_tokens_guest")
        editor.putBoolean(MIGRATED, true).apply()
    }

    @Synchronized fun clearAll() {
        // Keep the migrated flag so cleared history is not re-imported.
        prefs.edit().clear().putBoolean(MIGRATED, true).apply()
    }

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { json.optString(it).takeIf(String::isNotBlank) }
        }.getOrElse { raw.split('|').filter(String::isNotBlank) }
    }

    private companion object {
        const val MIGRATED = "migrated_local_only_v1"
    }
}

private fun normalizeSearchQuery(query: String): String = query.trim().replace(Regex("\\s+"), " ").lowercase()

/**
 * One-time, idempotent merge of the previous build's guest history into Room:
 * entries the user had deleted are removed from Room, entries only kept in the old store are added.
 */
suspend fun migrateSearchHistoryToLocalOnly(context: Context, database: MusicDatabase) {
    val store = SearchHistoryScopeStore(context)
    if (store.isMigrated()) return
    val removed = store.legacyRemoved().map(::normalizeSearchQuery).toSet()
    val old = store.legacyLocalHistory()
    withContext(Dispatchers.IO) {
        val existing = database.searchHistory().first()
        // Rows the user had deleted must not be resurrected from the old mirrored Room copy.
        existing.filter { normalizeSearchQuery(it.query) in removed }.forEach { database.delete(it) }
        val have = existing.map { normalizeSearchQuery(it.query) }.toMutableSet()
        // Oldest first so the newest ends up on top (Room orders by id DESC).
        old.asReversed().forEach { q ->
            val key = normalizeSearchQuery(q)
            if (key.isNotBlank() && key !in removed && have.add(key)) {
                database.insert(SearchHistory(query = q.trim()))
            }
        }
    }
    store.finishMigration()
}
