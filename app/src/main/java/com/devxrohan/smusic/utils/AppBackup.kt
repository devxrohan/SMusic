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
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

/** Coordinates app-data archives and journaled restore for settings, libraries and playback state. */
object AppBackup {
    private const val MANIFEST_FILENAME = "smusic_backup_manifest.json"
    private const val BACKUP_FORMAT_VERSION = 1
    private const val MAX_MANIFEST_BYTES = 16 * 1024

    private const val STAGING_DIR = "pending_restore"
    private const val READY_MARKER = ".ready"
    private const val PENDING_GOOGLE_EMAIL = "pending_google_email.txt"

    const val SETTINGS_FILENAME = "settings.preferences_pb"
    const val USER_NAME_PREFS_FILENAME = "user_name_preferences.preferences_pb"
    const val GOOGLE_ACCOUNT_FILENAME = "google_account.json"
    private const val RESTORE_STATUS = "restore_status.txt"
    fun restoreStatus(context: Context): String? = runCatching { File(context.filesDir, RESTORE_STATUS).takeIf { it.isFile }?.readText() }.getOrNull()
    fun dismissRestoreStatus(context: Context) { File(context.filesDir, RESTORE_STATUS).delete() }
    private fun recordRestoreStatus(context: Context, message: String) { runCatching { File(context.filesDir, RESTORE_STATUS).writeText(message) } }


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
        val db = database ?: com.devxrohan.smusic.App.instance.database
        val snapshot = File(context.cacheDir, "backup_snapshot_${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
        val connection = db.openHelper.writableDatabase
        try {
            connection.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "Database is busy; backup was not created" }
            }
            connection.beginTransaction()
            try {
                val source = context.getDatabasePath(InternalDatabase.DB_NAME)
                check(source.isFile) { "Database file is missing" }
                source.copyTo(File(snapshot, InternalDatabase.DB_NAME))
                File(source.path + "-wal").takeIf { it.isFile && it.length() > 0 }?.copyTo(File(snapshot, InternalDatabase.DB_NAME + "-wal"))
            } finally { connection.endTransaction() }

        // Make sure pending SharedPreferences writes are on disk before the XML is copied.
        PREFS_TO_BACKUP.forEach {
            check(context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().commit()) { "Could not flush backup preferences" }
        }

        val filesDir = context.filesDir
        ZipOutputStream(rawStream.buffered()).use { zip ->
            val manifest = JSONObject()
                .put("formatVersion", BACKUP_FORMAT_VERSION)
                .put("applicationId", context.packageName)
                .put("versionCode", com.devxrohan.smusic.BuildConfig.VERSION_CODE)
                .put("versionName", com.devxrohan.smusic.BuildConfig.VERSION_NAME)
                .put("createdAt", System.currentTimeMillis())
                .put("databaseIncluded", true)
                .put("settingsIncluded", File(filesDir, "datastore/$SETTINGS_FILENAME").isFile)
            zip.putNextEntry(ZipEntry(MANIFEST_FILENAME))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
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
            val dbFile = File(snapshot, InternalDatabase.DB_NAME)
            check(dbFile.isFile) { "Database file is missing" }
            addFile(zip, InternalDatabase.DB_NAME, dbFile)
            val walFile = File(snapshot, "${InternalDatabase.DB_NAME}-wal")
            if (walFile.isFile && walFile.length() > 0) addFile(zip, "${InternalDatabase.DB_NAME}-wal", walFile)
        }
        } finally { snapshot.deleteRecursively() }
    }

    private fun addFile(zip: ZipOutputStream, entryName: String, file: File) {
        file.inputStream().buffered().use { input ->
            zip.putNextEntry(ZipEntry(entryName))
            input.copyTo(zip)
            zip.closeEntry()
        }
    }

    // ───────────────────────────── restore ─────────────────────────────

    /** Unpacks and validates the backup into the staging folder. Returns true when a restore is staged
     * and the app should now be restarted with [restartApp]. Live data is not modified. */
    fun stageRestore(context: Context, openStream: () -> InputStream?): Boolean {
        val staging = File(context.filesDir, STAGING_DIR)
        staging.deleteRecursively()
        staging.mkdirs()
        return try {
            val stream = openStream() ?: error("Could not open backup")
            BackupArchive.extract(stream, staging, ::isAllowedEntry)
            validateManifest(context, staging)
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

    /** Backups predating the manifest retain their existing content-validation path. */
    private fun validateManifest(context: Context, staging: File) {
        val file = File(staging, MANIFEST_FILENAME)
        if (!file.exists()) return
        require(file.isFile && file.length() in 1..MAX_MANIFEST_BYTES.toLong()) { "Invalid backup manifest" }
        val manifest = JSONObject(file.readText(Charsets.UTF_8))
        require(manifest.getInt("formatVersion") == BACKUP_FORMAT_VERSION) {
            "This backup format is not supported by this version of SMusic"
        }
        require(manifest.getString("applicationId") == context.packageName) { "This backup belongs to another app" }
        require(manifest.getLong("createdAt") > 0) { "Invalid backup creation date" }
        if (manifest.getBoolean("databaseIncluded")) {
            require(File(staging, InternalDatabase.DB_NAME).isFile) { "Backup database is missing" }
        }
        if (manifest.getBoolean("settingsIncluded")) {
            require(File(staging, DATASTORE_PREFIX + SETTINGS_FILENAME).isFile ||
                File(staging, SETTINGS_FILENAME).isFile) { "Backup settings are missing" }
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

    /** Apply staged restores before providers and stores initialize; recovery must not abort startup. */
    fun applyPendingRestore(context: Context) {
        val filesDir = context.filesDir
        val staging = File(filesDir, STAGING_DIR)
        val transaction = RestoreTransaction(requireNotNull(filesDir.parentFile), File(filesDir, "restore_transaction"))
        try {
            val recovery = transaction.recover()
            if (recovery != RestoreTransaction.Recovery.NONE) {
                recordRestoreStatus(context, if (recovery == RestoreTransaction.Recovery.ROLLED_BACK) "An interrupted restore was rolled back to your previous data." else "Your backup was restored successfully.")
                staging.deleteRecursively()
                return
            }
            if (!staging.exists()) return
            if (!File(staging, READY_MARKER).isFile) { staging.deleteRecursively(); return }
            validateManifest(context, staging)
            check(isValidStaging(staging)) { "Staged backup is no longer valid" }
            val replacements = linkedMapOf<File, File?>()
            fun replace(source: File, target: File) { if (source.exists()) replacements[target] = source }
            val ds = File(filesDir, "datastore")
            File(staging, "datastore").listFiles()?.forEach { replace(it, File(ds, it.name)) }
            listOf(SETTINGS_FILENAME, USER_NAME_PREFS_FILENAME).forEach { name ->
                if (!File(staging, DATASTORE_PREFIX + name).exists()) replace(File(staging, name), File(ds, name))
            }
            val prefs = File(filesDir.parentFile, "shared_prefs")
            File(staging, "shared_prefs").listFiles()?.forEach { replace(it, File(prefs, it.name)) }
            if (!File(staging, "shared_prefs/smusic_global_stats.xml").exists()) replace(File(staging, "smusic_global_stats.xml"), File(prefs, "smusic_global_stats.xml"))
            File(staging, "files").listFiles()?.forEach { replace(it, File(filesDir, it.name)) }
            if (!File(staging, "files/pulse").exists()) File(staging, "pulse_state.json").takeIf { it.isFile }?.let { legacy ->
                val folder = File(staging, "legacy_pulse").apply { mkdirs() }
                legacy.copyTo(File(folder, "pulse_state.json"), overwrite = true)
                replacements[File(filesDir, "pulse")] = folder
            }
            File(staging, InternalDatabase.DB_NAME).takeIf { it.isFile }?.let { db ->
                val destination = context.getDatabasePath(InternalDatabase.DB_NAME)
                replacements[destination] = db
                listOf("-wal", "-shm", "-journal").forEach { suffix ->
                    replacements[File(destination.path + suffix)] = File(staging, InternalDatabase.DB_NAME + suffix).takeIf { suffix == "-wal" && it.isFile }
                }
            }
            listOf(MusicService.PERSISTENT_QUEUE_FILE, MusicService.PERSISTENT_AUTOMIX_FILE, MusicService.PERSISTENT_PLAYER_STATE_FILE, "persistent_session.data", "persistent_session.data.bak", "persistent_session.data.new").forEach { replacements[File(filesDir, it)] = null }
            replacements[File(filesDir, PENDING_GOOGLE_EMAIL)] = null
            File(staging, GOOGLE_ACCOUNT_FILENAME).takeIf { it.isFile }?.let {
                val email = JSONObject(it.readText()).optString("email").trim()
                if (email.isNotBlank()) {
                    val pending = File(staging, "restored_email.txt").apply { writeText(email) }
                    replacements[File(filesDir, PENDING_GOOGLE_EMAIL)] = pending
                }
            }
            transaction.apply(replacements.map { RestoreTransaction.Change(it.key, it.value) })
            recordRestoreStatus(context, "Your backup was restored successfully.")
            staging.deleteRecursively()
        } catch (e: Exception) {
            // Keep journal/recovery copies on failure. The next startup retries recovery before opening stores.
            Timber.e(e, "Restore failed; recovery journal retained if recovery is incomplete")
            recordRestoreStatus(context, if ((File(filesDir, "restore_transaction/journal.properties").exists() || File(filesDir, "restore_transaction/journal.previous").exists())) "Restore recovery is incomplete. Keep app data intact and restart after making storage available." else "Restore could not be completed. Your previous data was kept or recovered.")
            if (!(File(filesDir, "restore_transaction/journal.properties").exists() || File(filesDir, "restore_transaction/journal.previous").exists())) staging.deleteRecursively()
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

    private fun isAllowedEntry(name: String): Boolean {
        if (name in setOf(MANIFEST_FILENAME, InternalDatabase.DB_NAME, "${InternalDatabase.DB_NAME}-wal",
                SETTINGS_FILENAME, USER_NAME_PREFS_FILENAME, GOOGLE_ACCOUNT_FILENAME,
                "smusic_global_stats.xml", "pulse_state.json")) return true
        if (name.startsWith(DATASTORE_PREFIX)) {
            val file = name.removePrefix(DATASTORE_PREFIX)
            return '/' !in file && file.endsWith(".preferences_pb")
        }
        if (name.startsWith(PREFS_PREFIX)) {
            return name.removePrefix(PREFS_PREFIX) in PREFS_TO_BACKUP.map { "$it.xml" }
        }
        return FILES_TO_BACKUP.any { name == FILES_PREFIX + it || name.startsWith("$FILES_PREFIX$it/") }
    }
}
