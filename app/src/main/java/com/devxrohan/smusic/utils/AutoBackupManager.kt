/*
 * SMusic Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.devxrohan.smusic.utils

import android.content.Context
import android.net.Uri
import android.provider.Settings
import com.devxrohan.smusic.db.InternalDatabase
import com.devxrohan.smusic.db.MusicDatabase
import com.devxrohan.smusic.extensions.div
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Automatic / device / cloud backups. Same feature set as AirBeats' AutoBackupManager:
 *  - one local snapshot (`smusic_backup.backup`) that Android's OS backup (see SMusicBackupAgent) carries across installs
 *  - a copy in Documents/SMusic that survives uninstalling the app
 *  - a per-device cloud copy
 *  - restoring any of them on app open / on demand
 *
 * What goes into a snapshot and how a snapshot is restored (staged, applied on the next start) lives in [AppBackup].
 */
object AutoBackupManager {

    const val BACKUP_FILENAME = "smusic_backup.backup"
    private const val PREFS_NAME = "auto_backup_state"
    const val KEY_LAST_BACKUP_TIME = "last_android_os_backup_time"
    private const val KEY_LAST_RESTORED_SIG = "last_restored_sig"
    private const val KEY_RESTART_ATTEMPTS = "restart_attempts"

    const val STORAGE_FOLDER_NAME = "SMusic"
    const val STORAGE_BACKUP_FILENAME = "smusic_backup.backup"
    private const val PERSISTENT_BACKUP_FILENAME = "smusic_auto_backup.backup"
    private const val KEY_AUTO_BACKUP_STORAGE = "auto_backup_to_storage"
    private const val BACKUP_SETTINGS_PREFS = "backup_settings"

    // ───────────────────────────── local snapshot ─────────────────────────────

    fun getAutoBackupFile(context: Context): File = File(context.filesDir, BACKUP_FILENAME)

    fun getDeviceId(context: Context): String =
        runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown_device"

    fun getDeviceCloudFilePath(context: Context): String = "smusic/devices/${getDeviceId(context)}/$BACKUP_FILENAME"

    fun getBackupSize(context: Context): Long {
        val file = getAutoBackupFile(context)
        if (file.exists() && file.length() > 0) return file.length()

        var total = 0L
        val dbFile = context.getDatabasePath(InternalDatabase.DB_NAME)
        if (dbFile.exists()) total += dbFile.length()
        (context.filesDir / "datastore").listFiles()?.forEach { total += it.length() }
        (context.filesDir / "playlist_images").listFiles()?.forEach { total += it.length() }
        return total
    }

    fun getLastBackupTime(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_LAST_BACKUP_TIME, 0L)

    fun setLastBackupTime(context: Context, time: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_BACKUP_TIME, time).commit()
    }

    fun resetRestartAttempts(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_RESTART_ATTEMPTS, 0).apply()
    }

    /** True when there is something worth backing up (never overwrite a good backup with an empty state). */
    fun hasBackableData(context: Context): Boolean {
        val dbFile = context.getDatabasePath(InternalDatabase.DB_NAME)
        val walFile = context.getDatabasePath("${InternalDatabase.DB_NAME}-wal")
        if ((dbFile.exists() && dbFile.length() > 0) || (walFile.exists() && walFile.length() > 0)) return true
        val ds = context.filesDir / "datastore"
        return ds.isDirectory && ds.listFiles()?.any { it.isFile && it.length() > 0 } == true
    }

    fun createAutoBackup(context: Context, database: MusicDatabase?, notifyBackupManager: Boolean = true): Boolean {
        return try {
            if (!hasBackableData(context)) {
                Timber.d("AutoBackupManager: nothing to back up yet, keeping the existing snapshot")
                return false
            }
            val targetFile = getAutoBackupFile(context)
            val tmpFile = File(context.filesDir, "$BACKUP_FILENAME.tmp")
            FileOutputStream(tmpFile).use { AppBackup.writeBackup(context, database, it) }

            if (tmpFile.exists() && tmpFile.length() > 0) {
                targetFile.delete()
                tmpFile.renameTo(targetFile)

                val now = System.currentTimeMillis()
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putLong(KEY_LAST_BACKUP_TIME, now)
                    // a snapshot we just wrote is, by definition, already "restored"
                    .putString(KEY_LAST_RESTORED_SIG, "${targetFile.length()}_${targetFile.lastModified()}")
                    .commit()

                savePersistentExternalBackup(context, targetFile)

                if (notifyBackupManager) {
                    runCatching { android.app.backup.BackupManager(context).dataChanged() }
                }
                true
            } else {
                tmpFile.delete()
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "AutoBackupManager: createAutoBackup failed")
            reportException(e)
            false
        }
    }

    // ───────────────────────────── Documents/SMusic ─────────────────────────────

    fun getDocumentsBackupDir(): File {
        val docs = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        return File(docs, STORAGE_FOLDER_NAME)
    }

    fun getDocumentsBackupFile(): File = File(getDocumentsBackupDir(), STORAGE_BACKUP_FILENAME)

    private fun downloadsBackupDir(): File = File(
        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
        STORAGE_FOLDER_NAME
    )

    fun isAutoBackupToStorageEnabled(context: Context): Boolean =
        context.getSharedPreferences(BACKUP_SETTINGS_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_BACKUP_STORAGE, true)

    fun setAutoBackupToStorageEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(BACKUP_SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_BACKUP_STORAGE, enabled).apply()
    }

    fun hasStoragePermission(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    fun saveBackupToStorage(context: Context, database: MusicDatabase?): Boolean {
        return try {
            if (!hasBackableData(context)) return false
            val docsDir = getDocumentsBackupDir().apply { if (!exists()) mkdirs() }
            val destFile = getDocumentsBackupFile()
            val tmpFile = File(docsDir, "$STORAGE_BACKUP_FILENAME.tmp")

            FileOutputStream(tmpFile).use { AppBackup.writeBackup(context, database, it) }

            if (tmpFile.exists() && tmpFile.length() > 0) {
                destFile.delete()
                tmpFile.renameTo(destFile)
                // Extra copy in Downloads/SMusic for redundancy
                runCatching {
                    val dl = downloadsBackupDir().apply { if (!exists()) mkdirs() }
                    destFile.copyTo(File(dl, STORAGE_BACKUP_FILENAME), overwrite = true)
                }
                true
            } else {
                tmpFile.delete()
                false
            }
        } catch (e: Exception) {
            Timber.w("AutoBackupManager: could not save backup to Documents/$STORAGE_FOLDER_NAME: ${e.message}")
            false
        }
    }

    fun performAutoBackupToStorageIfEnabled(context: Context, database: MusicDatabase?) {
        if (isAutoBackupToStorageEnabled(context) && hasStoragePermission(context)) {
            saveBackupToStorage(context, database)
        }
    }

    fun findStorageBackupFile(): File? {
        val candidates = listOf(
            getDocumentsBackupFile(),
            File(getDocumentsBackupDir(), PERSISTENT_BACKUP_FILENAME),
            File(downloadsBackupDir(), STORAGE_BACKUP_FILENAME),
            File(downloadsBackupDir(), PERSISTENT_BACKUP_FILENAME),
        )
        candidates.firstOrNull { it.exists() && it.length() > 0L }?.let { return it }

        return getDocumentsBackupDir().takeIf { it.isDirectory }
            ?.listFiles { f -> f.isFile && f.name.endsWith(".backup") && f.length() > 0L }
            ?.maxByOrNull { it.lastModified() }
    }

    fun restoreFromStorageBackup(context: Context, shouldRestart: Boolean = true): Boolean {
        val file = findStorageBackupFile() ?: return false
        runCatching { file.copyTo(getAutoBackupFile(context), overwrite = true) }
        markSnapshotRestored(context)
        return runCatching {
            FileInputStream(file).use { restoreFromInputStream(context, it, shouldRestart) }
        }.getOrDefault(false)
    }

    /** Copies the snapshot to places that survive uninstalling the app (Downloads/SMusic and Documents/SMusic). */
    fun savePersistentExternalBackup(context: Context, sourceFile: File) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return
        runCatching {
            val dir = downloadsBackupDir().apply { if (!exists()) mkdirs() }
            sourceFile.copyTo(File(dir, PERSISTENT_BACKUP_FILENAME), overwrite = true)
        }
        runCatching {
            val dir = getDocumentsBackupDir().apply { if (!exists()) mkdirs() }
            sourceFile.copyTo(File(dir, PERSISTENT_BACKUP_FILENAME), overwrite = true)
            sourceFile.copyTo(File(dir, STORAGE_BACKUP_FILENAME), overwrite = true)
        }
    }

    fun findAvailableAutoBackup(context: Context): File? {
        // 1. internal snapshot (this is also what Android's OS backup brings back after a reinstall)
        getAutoBackupFile(context).takeIf { it.exists() && it.length() > 0L }?.let { return it }

        // 2. persistent copies that survive uninstall
        listOf(
            File(downloadsBackupDir(), PERSISTENT_BACKUP_FILENAME),
            File(getDocumentsBackupDir(), PERSISTENT_BACKUP_FILENAME),
        ).firstOrNull { it.exists() && it.length() > 0L }?.let { return it }

        // 3. any .backup file in Downloads/SMusic or Documents/SMusic
        for (dir in listOf(downloadsBackupDir(), getDocumentsBackupDir())) {
            if (dir.isDirectory) {
                dir.listFiles { f -> f.isFile && f.name.endsWith(".backup") && f.length() > 0L }
                    ?.maxByOrNull { it.lastModified() }
                    ?.let { return it }
            }
        }
        return null
    }

    // ───────────────────────────── restore ─────────────────────────────

    /** Remember that the snapshot currently on disk is the one being restored, so the next start doesn't restore it twice. */
    private fun markSnapshotRestored(context: Context) {
        val f = getAutoBackupFile(context)
        if (!f.exists()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_RESTORED_SIG, "${f.length()}_${f.lastModified()}")
            .putLong(KEY_LAST_BACKUP_TIME, f.lastModified())
            .commit()
    }

    fun restoreFromUri(context: Context, uri: Uri, shouldRestart: Boolean = true): Boolean {
        return try {
            val targetFile = getAutoBackupFile(context)
            context.applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { input.copyTo(it) }
            }
            if (targetFile.exists() && targetFile.length() > 0) {
                markSnapshotRestored(context)
                FileInputStream(targetFile).use { restoreFromInputStream(context, it, shouldRestart) }
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "AutoBackupManager: restoreFromUri failed")
            reportException(e)
            false
        }
    }

    /** Stages [rawStream] as the next restore and (optionally) restarts the app to apply it. */
    fun restoreFromInputStream(context: Context, rawStream: InputStream, shouldRestart: Boolean = true): Boolean {
        val staged = AppBackup.stageRestore(context) { rawStream }
        if (staged && shouldRestart) AppBackup.restartApp(context)
        return staged
    }

    fun restoreAutoBackup(context: Context, shouldRestart: Boolean = true): Boolean {
        val file = findAvailableAutoBackup(context)
        if (file == null || !file.exists() || file.length() == 0L) {
            Timber.w("AutoBackupManager: no backup file available to restore")
            return false
        }
        val targetFile = getAutoBackupFile(context)
        if (file.absolutePath != targetFile.absolutePath) {
            runCatching { file.copyTo(targetFile, overwrite = true) }
        }
        val streamFile = if (targetFile.exists() && targetFile.length() > 0L) targetFile else file
        markSnapshotRestored(context)
        return runCatching {
            FileInputStream(streamFile).use { restoreFromInputStream(context, it, shouldRestart) }
        }.getOrDefault(false)
    }

    /**
     * Called on every app start. If a snapshot exists that this install has not restored yet (a fresh install that
     * got the file back from Android's OS backup, or from Downloads/Documents), restore it once.
     */
    fun checkAndRestoreOnOpen(context: Context) {
        try {
            val backupFile = getAutoBackupFile(context)
            if (!backupFile.exists() || backupFile.length() == 0L) {
                val candidate = findAvailableAutoBackup(context)
                if (candidate != null && candidate.exists() && candidate.length() > 0L) {
                    candidate.copyTo(backupFile, overwrite = true)
                }
            }
            if (!backupFile.exists() || backupFile.length() == 0L) return

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentSig = "${backupFile.length()}_${backupFile.lastModified()}"
            if (prefs.getString(KEY_LAST_RESTORED_SIG, null) == currentSig) return

            val attempts = prefs.getInt(KEY_RESTART_ATTEMPTS, 0)
            if (attempts >= 2) {
                Timber.w("AutoBackupManager: not auto-restoring again ($attempts attempts) to avoid a restart loop")
                return
            }

            val staged = AppBackup.stageRestore(context) { FileInputStream(backupFile) }
            if (staged) {
                prefs.edit()
                    .putString(KEY_LAST_RESTORED_SIG, currentSig)
                    .putInt(KEY_RESTART_ATTEMPTS, attempts + 1)
                    .putLong(KEY_LAST_BACKUP_TIME, backupFile.lastModified())
                    .commit()
                AppBackup.restartApp(context)
            }
        } catch (t: Throwable) {
            Timber.e(t, "AutoBackupManager: checkAndRestoreOnOpen failed")
        }
    }

    // ───────────────────────────── cloud (per device) ─────────────────────────────

    suspend fun uploadToCloud(context: Context, backupFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!backupFile.exists() || backupFile.length() == 0L) return@withContext false
        runCatching { CloudBackupClient().uploadFile(getDeviceCloudFilePath(context), backupFile) }.getOrDefault(false)
    }

    suspend fun downloadFromCloud(context: Context, destinationFile: File): Boolean = withContext(Dispatchers.IO) {
        runCatching { CloudBackupClient().downloadFile(getDeviceCloudFilePath(context), destinationFile) }.getOrDefault(false)
    }

    suspend fun deleteFromCloud(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching { CloudBackupClient().deleteFile(getDeviceCloudFilePath(context)) }.getOrDefault(false)
    }

    suspend fun checkAndRestoreDeviceCloudBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val attempts = prefs.getInt(KEY_RESTART_ATTEMPTS, 0)
        if (attempts >= 2) return@withContext false

        // 1. a local / OS / persistent snapshot wins
        val available = findAvailableAutoBackup(context)
        if (available != null && available.exists() && available.length() > 0L) {
            if (restoreAutoBackup(context, shouldRestart = false)) {
                prefs.edit()
                    .putInt(KEY_RESTART_ATTEMPTS, attempts + 1)
                    .putLong(KEY_LAST_BACKUP_TIME, available.lastModified())
                    .commit()
                AppBackup.restartApp(context)
                return@withContext true
            }
        }

        // 2. otherwise the cloud copy
        val targetFile = getAutoBackupFile(context)
        if (!downloadFromCloud(context, targetFile) || !targetFile.exists() || targetFile.length() == 0L) {
            return@withContext false
        }
        val currentSig = "${targetFile.length()}_${targetFile.lastModified()}"
        if (prefs.getString(KEY_LAST_RESTORED_SIG, null) == currentSig) return@withContext false

        val staged = runCatching {
            FileInputStream(targetFile).use { restoreFromInputStream(context, it, shouldRestart = false) }
        }.getOrDefault(false)
        if (staged) {
            prefs.edit()
                .putString(KEY_LAST_RESTORED_SIG, currentSig)
                .putInt(KEY_RESTART_ATTEMPTS, attempts + 1)
                .putLong(KEY_LAST_BACKUP_TIME, targetFile.lastModified())
                .commit()
            AppBackup.restartApp(context)
            return@withContext true
        }
        false
    }

    // ───────────────────────────── delete ─────────────────────────────

    fun deleteBackup(context: Context): Boolean {
        return try {
            getAutoBackupFile(context).delete()
            runCatching {
                File(downloadsBackupDir(), PERSISTENT_BACKUP_FILENAME).delete()
                File(getDocumentsBackupDir(), PERSISTENT_BACKUP_FILENAME).delete()
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
            runCatching { android.app.backup.BackupManager(context).dataChanged() }
            true
        } catch (e: Exception) {
            Timber.e(e, "AutoBackupManager: deleteBackup failed")
            false
        }
    }
}
