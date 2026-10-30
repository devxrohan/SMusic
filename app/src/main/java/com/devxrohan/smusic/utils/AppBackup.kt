/*
 * SMusic Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.devxrohan.smusic.utils

import android.content.Context
import android.content.Intent
import com.devxrohan.smusic.db.InternalDatabase
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.playback.MusicService
import com.devxrohan.smusic.ui.component.NamePreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

/**
 * One place that knows what a SMusic backup contains and how to put it back.
 *
 * Backup layout (zip):
 *  - datastore/<file>         every DataStore file (settings, user_name_preferences, app_settings, ...)
 *  - shared_prefs/<file>.xml  the user-data SharedPreferences (playlist covers, scoped search history, stats id)
 *  - files/<path>             user files: playlist_images/, lyrics_translations/, pulse/, guest_history_snapshot.tsv
 *  - google_account.json      linked account e-mail
 *  - song.db (+ -wal)         the Room database
 *
 * Restore never touches live data. The zip is first unpacked into a staging folder, validated, and then the app
 * restarts; [applyPendingRestore] moves the staged files into place at the very start of the next process, before
 * the database, DataStore or SharedPreferences are opened. Overwriting those while the app is running (and closing
 * the database under the UI) is what used to crash the app and leave it closed.
 */
object AppBackup {
    private const val STAGING_DIR = "pending_restore"
    private const val READY_MARKER = ".ready"
    private const val PENDING_GOOGLE_EMAIL = "pending_google_email.txt"

    const val SETTINGS_FILENAME = "settings.preferences_pb"
    const val USER_NAME_PREFS_FILENAME = "user_name_preferences.preferences_pb"
    const val GOOGLE_ACCOUNT_FILENAME = "google_account.json"

    private const val DATASTORE_PREFIX = "datastore/"
    private const val PREFS_PREFIX = "shared_prefs/"
    private const val FILES_PREFIX = "files/"

    /** SharedPreferences that hold user data (device-specific ones such as backup_settings are left out). */
    private val PREFS_TO_BACKUP = listOf("playlist_images", "scoped_search_history", "smusic_global_stats")

    /** Folders / files inside filesDir that hold user data. Caches (exoplayer, download, update_cache) are not included. */
    private val FILES_TO_BACKUP = listOf("playlist_images", "lyrics_translations", "pulse", "guest_history_snapshot.tsv")

    // ───────────────────────────── backup ─────────────────────────────

    /** Writes a complete backup zip to [rawStream]. Must be called off the main thread. */
    fun writeBackup(context: Context, database: MusicDatabase?, rawStream: OutputStream) {
        // Flush the WAL into the main database file so the copy below is complete.
        runCatching {
            val db = database ?: runCatching { com.devxrohan.smusic.App.instance.database }.getOrNull()
            runBlocking(Dispatchers.IO) { db?.checkpoint() }
        }.onFailure { Timber.w(it, "Database checkpoint failed during backup") }

        // Make sure pending SharedPreferences writes are on disk before the XML is copied.
        runCatching {
            PREFS_TO_BACKUP.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().commit() }
        }

        val filesDir = context.filesDir
        ZipOutputStream(rawStream.buffered()).use { zip ->
            // DataStore files (all of them, so nothing added later is forgotten)
            (File(filesDir, "datastore").listFiles() ?: emptyArray())
                .filter { it.isFile && !it.name.endsWith(".tmp") }
                .forEach { addFile(zip, DATASTORE_PREFIX + it.name, it) }

            // SharedPreferences
            val sharedPrefsDir = File(context.filesDir.parentFile, "shared_prefs")
            PREFS_TO_BACKUP.forEach { name ->
                val f = File(sharedPrefsDir, "$name.xml")
                if (f.isFile) addFile(zip, "$PREFS_PREFIX$name.xml", f)
            }

            // User files
            FILES_TO_BACKUP.forEach { name ->
                val f = File(filesDir, name)
                when {
                    f.isFile -> addFile(zip, FILES_PREFIX + name, f)
                    f.isDirectory -> f.walkTopDown()
                        .filter { it.isFile && !it.name.endsWith(".tmp") }
                        .forEach { addFile(zip, FILES_PREFIX + it.relativeTo(filesDir).path.replace(File.separatorChar, '/'), it) }
                }
            }

            // Linked account e-mail
            val accountEmail = runCatching { runBlocking { NamePreferenceManager(context).accountEmail.first() } }
                .getOrDefault("")
            if (accountEmail.isNotBlank()) {
                zip.putNextEntry(ZipEntry(GOOGLE_ACCOUNT_FILENAME))
                zip.write(
                    JSONObject().put("email", accountEmail).put("previouslyLoggedIn", true)
                        .toString().toByteArray(Charsets.UTF_8)
                )
                zip.closeEntry()
            }

            // Database
            val dbFile = context.getDatabasePath(InternalDatabase.DB_NAME)
            check(dbFile.isFile) { "Database file is missing" }
            addFile(zip, InternalDatabase.DB_NAME, dbFile)
            val walFile = context.getDatabasePath("${InternalDatabase.DB_NAME}-wal")
            if (walFile.isFile && walFile.length() > 0) addFile(zip, "${InternalDatabase.DB_NAME}-wal", walFile)
        }
    }

    private fun addFile(zip: ZipOutputStream, entryName: String, file: File) {
        runCatching {
            file.inputStream().buffered().use { input ->
                zip.putNextEntry(ZipEntry(entryName))
                input.copyTo(zip)
                zip.closeEntry()
            }
        }.onFailure { Timber.w(it, "Skipping %s in backup", entryName) }
    }

    // ───────────────────────────── restore ─────────────────────────────

    /**
     * Unpacks and validates the backup into the staging folder. Returns true when a restore is staged and the app
     * should now be restarted with [restartApp]. Live data is not modified.
     */
    fun stageRestore(context: Context, openStream: () -> InputStream?): Boolean {
        val staging = File(context.filesDir, STAGING_DIR)
        staging.deleteRecursively()
        staging.mkdirs()
        return try {
            val stream = openStream() ?: return false
            ZipInputStream(stream.buffered()).use { zip ->
                var entry = runCatching { zip.nextEntry }.getOrNull()
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val target = safeChild(staging, entry.name)
                        if (target != null) {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                        }
                    }
                    entry = runCatching { zip.nextEntry }.getOrNull()
                }
            }
            if (!isValidStaging(staging)) {
                Timber.w("Restore aborted: not a valid SMusic backup")
                staging.deleteRecursively()
                return false
            }
            File(staging, READY_MARKER).writeText("1")
            true
        } catch (e: Exception) {
            Timber.e(e, "Staging restore failed")
            reportException(e)
            staging.deleteRecursively()
            false
        }
    }

    private fun isValidStaging(staging: File): Boolean {
        val db = File(staging, InternalDatabase.DB_NAME)
        if (db.isFile) {
            val header = ByteArray(16)
            val read = db.inputStream().use { it.read(header) }
            if (read < 16 || String(header, Charsets.ISO_8859_1) != "SQLite format 3\u0000") return false
            return true
        }
        // A settings-only backup is still acceptable.
        return File(staging, DATASTORE_PREFIX + SETTINGS_FILENAME).isFile || File(staging, SETTINGS_FILENAME).isFile
    }

    /**
     * Moves a staged restore into place. Called from Application.attachBaseContext, i.e. before any ContentProvider,
     * the database, DataStore or SharedPreferences are touched. Never throws, never runs twice.
     */
    fun applyPendingRestore(context: Context) {
        val filesDir = context.filesDir
        val staging = File(filesDir, STAGING_DIR)
        if (!staging.exists()) return
        val marker = File(staging, READY_MARKER)
        if (!marker.exists()) { // half-written staging from an interrupted restore
            staging.deleteRecursively()
            return
        }
        marker.delete() // from here on a crash can never make this run again
        try {
            val dataDir = filesDir.parentFile
            val dsDir = File(filesDir, "datastore").apply { mkdirs() }

            // DataStore: new layout first, then the legacy single-file entries (older backups).
            File(staging, "datastore").listFiles()?.forEach { copyTo(it, File(dsDir, it.name)) }
            listOf(SETTINGS_FILENAME, USER_NAME_PREFS_FILENAME).forEach { name ->
                val legacy = File(staging, name)
                if (legacy.isFile && !File(staging, DATASTORE_PREFIX + name).exists()) copyTo(legacy, File(dsDir, name))
            }

            // SharedPreferences
            if (dataDir != null) {
                val prefsDir = File(dataDir, "shared_prefs").apply { mkdirs() }
                File(staging, "shared_prefs").listFiles()?.forEach { copyTo(it, File(prefsDir, it.name)) }
                File(staging, "smusic_global_stats.xml").takeIf { it.isFile }
                    ?.let { copyTo(it, File(prefsDir, "smusic_global_stats.xml")) } // legacy
            }

            // User files: a staged folder replaces the live one so the result matches the backup.
            File(staging, "files").listFiles()?.forEach { staged ->
                val dest = File(filesDir, staged.name)
                if (staged.isDirectory) {
                    dest.deleteRecursively()
                    staged.copyRecursively(dest, overwrite = true)
                } else {
                    copyTo(staged, dest)
                }
            }
            File(staging, "pulse_state.json").takeIf { it.isFile }?.let { // legacy
                File(filesDir, "pulse").deleteRecursively()
                copyTo(it, File(File(filesDir, "pulse"), "pulse_state.json"))
            }

            // Database: remove every old companion file so a stale WAL can never be replayed onto the new data.
            val stagedDb = File(staging, InternalDatabase.DB_NAME)
            if (stagedDb.isFile) {
                val dbFile = context.getDatabasePath(InternalDatabase.DB_NAME)
                dbFile.parentFile?.mkdirs()
                listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
                copyTo(stagedDb, dbFile)
                File(staging, "${InternalDatabase.DB_NAME}-wal").takeIf { it.isFile }
                    ?.let { copyTo(it, File(dbFile.path + "-wal")) }
            }

            // The saved queue / player state refer to the old database.
            listOf(
                MusicService.PERSISTENT_QUEUE_FILE,
                MusicService.PERSISTENT_AUTOMIX_FILE,
                MusicService.PERSISTENT_PLAYER_STATE_FILE,
            ).forEach { File(filesDir, it).delete() }

            // The account e-mail is written through DataStore, which can't be used this early: finishPendingRestore does it.
            File(staging, GOOGLE_ACCOUNT_FILENAME).takeIf { it.isFile }?.let { f ->
                val email = runCatching { JSONObject(f.readText()).optString("email").trim() }.getOrDefault("")
                if (email.isNotBlank()) File(filesDir, PENDING_GOOGLE_EMAIL).writeText(email)
            }
        } catch (e: Exception) {
            Timber.e(e, "Applying pending restore failed")
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Second half of a restore, from Application.onCreate once DataStore can be used. */
    fun finishPendingRestore(context: Context) {
        val f = File(context.filesDir, PENDING_GOOGLE_EMAIL)
        if (!f.isFile) return
        val email = runCatching { f.readText().trim() }.getOrDefault("")
        f.delete()
        if (email.isNotBlank()) {
            runCatching { runBlocking { NamePreferenceManager(context).rememberGoogleLoginEmail(email) } }
        }
    }

    // ───────────────────────────── restart ─────────────────────────────

    const val EXTRA_RELAUNCH_INTENT = "smusic.relaunch_intent"
    const val EXTRA_OLD_PID = "smusic.old_pid"

    /** True inside the helper process used only for a manual restore relaunch. */
    fun isRestartProcess(): Boolean = runCatching {
        val name = if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.app.Application.getProcessName()
        } else {
            File("/proc/self/cmdline").readText().trim('\u0000')
        }
        name?.endsWith(":restart") == true
    }.getOrDefault(false)

    private fun launchIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

    /** Relaunches only for an explicit manual restore. Android OS restore never calls this. */
    fun restartApp(context: Context) {
        runCatching { context.stopService(Intent(context, MusicService::class.java)) }
        val launch = launchIntent(context) ?: return
        val phoenix = Intent(context, RestartActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(EXTRA_RELAUNCH_INTENT, launch)
            putExtra(EXTRA_OLD_PID, android.os.Process.myPid())
        }
        val started = runCatching { context.startActivity(phoenix) }.isSuccess
        if (!started) runCatching { context.startActivity(launch) }
        runCatching { Thread.sleep(150) }
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(0)
    }

    fun cancelRestartFallback(context: Context) = Unit

    // ───────────────────────────── helpers ─────────────────────────────

    private fun copyTo(src: File, dest: File) {
        dest.parentFile?.mkdirs()
        src.copyTo(dest, overwrite = true)
    }

    /** Resolves a zip entry name inside [root]; null for anything that would escape it (zip-slip). */
    private fun safeChild(root: File, name: String): File? {
        val clean = name.replace('\\', '/').trimStart('/')
        if (clean.isEmpty() || clean.split('/').any { it == ".." }) return null
        val f = File(root, clean)
        return if (f.canonicalPath.startsWith(root.canonicalPath + File.separator)) f else null
    }
}
