package com.devxrohan.smusic.utils

import android.app.backup.BackupManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.devxrohan.smusic.db.InternalDatabase
import com.devxrohan.smusic.db.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Backup coordinator.
 *
 * Android OS backup is native: Android backs up the real app data selected by the XML backup rules and restores it
 * during installation, before the launcher activity can be opened. The local .backup file is only the portable
 * manual/device-storage format; it is never used as a fake replacement for the OS backup transport.
 */
object AutoBackupManager {
    const val BACKUP_FILENAME = "smusic_backup.backup"
    private const val PREFS_NAME = "auto_backup_state"
    const val KEY_LAST_BACKUP_TIME = "last_android_os_backup_time"
    private const val KEY_RESTART_ATTEMPTS = "restart_attempts"

    const val STORAGE_FOLDER_NAME = "SMusic"
    const val STORAGE_BACKUP_FILENAME = "smusic_backup.backup"
    private const val KEY_AUTO_BACKUP_STORAGE = "auto_backup_to_storage"
    private const val BACKUP_SETTINGS_PREFS = "backup_settings"

    fun isStartupRestoreCheckDone(): Boolean = true

    fun getAutoBackupFile(context: Context): File = File(context.filesDir, BACKUP_FILENAME)

    fun getDeviceId(context: Context): String =
        runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown_device"

    fun getDeviceCloudFilePath(context: Context): String =
        "smusic/devices/${getDeviceId(context)}/$BACKUP_FILENAME"

    /** Approximate amount of user data covered by the native OS backup. */
    fun getBackupSize(context: Context): Long {
        var total = 0L
        fun addTree(file: File) {
            if (!file.exists()) return
            if (file.isFile) total += file.length()
            else file.listFiles()?.forEach(::addTree)
        }
        addTree(context.getDatabasePath(InternalDatabase.DB_NAME))
        addTree(File(context.filesDir, "datastore"))
        addTree(File(context.filesDir.parentFile, "shared_prefs"))
        addTree(File(context.filesDir, "playlist_images"))
        addTree(File(context.filesDir, "lyrics_translations"))
        addTree(File(context.filesDir, "pulse"))
        return total
    }

    fun getLastBackupTime(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_BACKUP_TIME, 0L)

    fun setLastBackupTime(context: Context, time: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_BACKUP_TIME, time).commit()
    }

    fun resetRestartAttempts(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_RESTART_ATTEMPTS, 0).apply()
    }

    /** A portable backup is useful only when there is actual user data. */
    fun hasBackableData(context: Context): Boolean = getBackupSize(context) > 0L

    /** Creates the portable snapshot and requests an Android OS backup pass. */
    fun createAutoBackup(context: Context, database: MusicDatabase?, notifyBackupManager: Boolean = true): Boolean {
        return try {
            if (!hasBackableData(context)) return false
            val target = getAutoBackupFile(context)
            val tmp = File(context.filesDir, "$BACKUP_FILENAME.tmp")
            FileOutputStream(tmp).use { AppBackup.writeBackup(context, database, it) }
            if (!tmp.isFile || tmp.length() <= 0L) {
                tmp.delete()
                return false
            }
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            setLastBackupTime(context, System.currentTimeMillis())
            // Keep the persistent external copy synchronized with every OS backup request.
            // This is deliberately independent of the optional cloud upload.
            if (isAutoBackupToStorageEnabled(context) && hasStoragePermission(context)) {
                saveBackupToStorage(context, database)
            }
            if (notifyBackupManager) requestAndroidOsBackup(context)
            true
        } catch (e: Exception) {
            Timber.e(e, "AutoBackupManager: portable backup failed")
            reportException(e)
            false
        }
    }

    fun requestAndroidOsBackup(context: Context) {
        runCatching { BackupManager(context.applicationContext).dataChanged() }
            .onFailure { Timber.w(it, "Android OS backup request failed") }
    }

    // ───────────────────────────── Documents/SMusic ─────────────────────────────

    fun getDocumentsBackupDir(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        STORAGE_FOLDER_NAME
    )

    fun getDocumentsBackupFile(): File = File(getDocumentsBackupDir(), STORAGE_BACKUP_FILENAME)

    fun isAutoBackupToStorageEnabled(context: Context): Boolean =
        context.getSharedPreferences(BACKUP_SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_BACKUP_STORAGE, true)

    fun setAutoBackupToStorageEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(BACKUP_SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_BACKUP_STORAGE, enabled).apply()
    }

    fun hasStoragePermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    fun requestStoragePermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.startActivity(android.content.Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }.onFailure {
                runCatching { context.startActivity(android.content.Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }) }
            }
        }
    }

    /** Writes exactly one canonical external backup; no Downloads copy and no timestamped auto-backup files. */
    fun saveBackupToStorage(context: Context, database: MusicDatabase?): Boolean {
        return try {
            if (!hasBackableData(context)) return false
            val dir = getDocumentsBackupDir().apply { mkdirs() }
            val destination = getDocumentsBackupFile()
            val tmp = File(dir, "$STORAGE_BACKUP_FILENAME.tmp")
            FileOutputStream(tmp).use { AppBackup.writeBackup(context, database, it) }
            if (!tmp.isFile || tmp.length() <= 0L) {
                tmp.delete()
                return false
            }
            destination.delete()
            if (!tmp.renameTo(destination)) {
                tmp.copyTo(destination, overwrite = true)
                tmp.delete()
            }
            // Remove only legacy automatic copies created by older SMusic builds.
            // User-created timestamped manual backups are never touched.
            runCatching {
                val downloads = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), STORAGE_FOLDER_NAME)
                listOf("smusic_auto_backup.backup", "smusic_backup.backup", "smusic_backup.backup.tmp").forEach { File(downloads, it).delete() }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "AutoBackupManager: Documents backup failed")
            false
        }
    }

    fun performAutoBackupToStorageIfEnabled(context: Context, database: MusicDatabase?) {
        // Storage backup is enabled by default. If the user has not granted the special
        // all-files access yet, the normal Android backup flow still works; the next
        // successful storage write will create the persistent external copy.
        if (isAutoBackupToStorageEnabled(context) && hasStoragePermission(context)) {
            saveBackupToStorage(context, database)
        }
    }

    /**
     * Finds the single canonical persistent backup first, while accepting legacy files
     * from older SMusic versions only as a read-only fallback.
     */
    fun findStorageBackupFile(context: Context? = null): File? {
        val candidates = listOf(
            getDocumentsBackupFile(),
            File(getDocumentsBackupDir(), "smusic_auto_backup.backup"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$STORAGE_FOLDER_NAME/$STORAGE_BACKUP_FILENAME"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$STORAGE_FOLDER_NAME/smusic_auto_backup.backup")
        )
        candidates.firstOrNull { runCatching { it.isFile && it.length() > 0L }.getOrDefault(false) }?.let { return it }

        // Last fallback: pick the newest valid backup from the two public folders.
        val dirs = listOf(getDocumentsBackupDir(), File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), STORAGE_FOLDER_NAME))
        for (dir in dirs) {
            runCatching {
                dir.listFiles { f -> f.isFile && f.name.endsWith(".backup") && f.length() > 0L }
                    ?.maxByOrNull { it.lastModified() }
                    ?.let { return it }
            }
        }
        return null
    }

    fun restoreFromStorageBackup(context: Context, shouldRestart: Boolean = false): Boolean {
        val source = findStorageBackupFile() ?: return false
        return runCatching {
            FileInputStream(source).use { restoreFromInputStream(context, it, shouldRestart) }
        }.getOrElse {
            Timber.e(it, "AutoBackupManager: Documents restore failed")
            false
        }
    }

    // ───────────────────────────── portable/manual restore ─────────────────────────────

    fun restoreFromUri(context: Context, uri: Uri, shouldRestart: Boolean = false): Boolean =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { restoreFromInputStream(context, it, shouldRestart) } == true
        }.getOrElse {
            Timber.e(it, "AutoBackupManager: restoreFromUri failed")
            false
        }

    fun restoreFromInputStream(context: Context, rawStream: InputStream, shouldRestart: Boolean = false): Boolean {
        val staged = AppBackup.stageRestore(context) { rawStream }
        if (staged && shouldRestart) AppBackup.restartApp(context)
        return staged
    }

    /** Legacy helper retained for the existing settings screen. Native OS restore is automatic; this restores a portable snapshot. */
    fun restoreAutoBackup(context: Context, shouldRestart: Boolean = true): Boolean {
        val file = getAutoBackupFile(context).takeIf { it.isFile && it.length() > 0L } ?: return false
        return runCatching {
            FileInputStream(file).use { restoreFromInputStream(context, it, shouldRestart) }
        }.getOrDefault(false)
    }

    fun findAvailableAutoBackup(context: Context): File? =
        getAutoBackupFile(context).takeIf { it.isFile && it.length() > 0L }

    /**
     * On a fresh install, recover the persistent external snapshot before the first
     * Activity is created. This is the fallback used on devices where the Android
     * backup transport does not retain an uninstall/install dataset.
     */
    fun restorePersistentBackupBeforeFirstLaunch(context: Context): Boolean {
        return runCatching {
            val db = context.getDatabasePath(InternalDatabase.DB_NAME)
            if (db.exists() && db.length() > 0L) return false
            val source = findStorageBackupFile(context) ?: return false
            if (!hasStoragePermission(context)) return false
            val staged = AppBackup.stageRestore(context) { source.inputStream() }
            if (!staged) return false
            AppBackup.applyPendingRestore(context)
            true
        }.onFailure {
            Timber.w(it, "AutoBackupManager: startup persistent restore failed")
        }.getOrDefault(false)
    }

    // ───────────────────────────── cloud ─────────────────────────────

    suspend fun uploadToCloud(context: Context, backupFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!backupFile.isFile || backupFile.length() == 0L) return@withContext false
        runCatching { CloudBackupClient().uploadFile(getDeviceCloudFilePath(context), backupFile) }.getOrDefault(false)
    }

    suspend fun downloadFromCloud(context: Context, destinationFile: File): Boolean = withContext(Dispatchers.IO) {
        runCatching { CloudBackupClient().downloadFile(getDeviceCloudFilePath(context), destinationFile) }.getOrDefault(false)
    }

    suspend fun deleteFromCloud(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching { CloudBackupClient().deleteFile(getDeviceCloudFilePath(context)) }.getOrDefault(false)
    }

    suspend fun checkAndRestoreDeviceCloudBackup(context: Context): Boolean = false

    fun deleteBackup(context: Context): Boolean =
        runCatching { getAutoBackupFile(context).delete() }.getOrDefault(false)
}
