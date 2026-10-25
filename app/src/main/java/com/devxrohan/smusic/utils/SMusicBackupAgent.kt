/*
 * SMusic Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.devxrohan.smusic.utils

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import timber.log.Timber
import java.io.File

/**
 * Android OS backup (Google backup / device transfer). The OS carries one file: the complete SMusic snapshot
 * (`smusic_backup.backup`, see backup_rules.xml). After a reinstall the snapshot is restored on first open by
 * [AutoBackupManager.checkAndRestoreOnOpen].
 */
class SMusicBackupAgent : BackupAgent() {

    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) {
        // Key/value pass is not used; the full-backup pass below carries everything.
    }

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) {
        // Key/value pass is not used.
    }

    override fun onFullBackup(data: FullBackupDataOutput) {
        Timber.i("SMusicBackupAgent: Android OS full backup triggered")
        runCatching {
            val backupFile = AutoBackupManager.getAutoBackupFile(this)
            // Refresh the snapshot so the OS gets the latest state
            AutoBackupManager.createAutoBackup(this, null, notifyBackupManager = false)
            if (backupFile.exists() && backupFile.length() > 0) {
                fullBackupFile(backupFile, data)
            } else {
                Timber.w("SMusicBackupAgent: snapshot is empty, skipping OS backup")
            }
        }.onFailure { Timber.e(it, "SMusicBackupAgent: onFullBackup failed") }
    }

    override fun onRestoreFile(data: ParcelFileDescriptor?, size: Long, destination: File?, type: Int, mode: Long, mtime: Long) {
        super.onRestoreFile(data, size, destination, type, mode, mtime)
    }

    override fun onRestoreFinished() {
        super.onRestoreFinished()
        // Don't restart from inside the restore callback; the first app open restores the snapshot (App.onCreate).
        Timber.i("SMusicBackupAgent: OS restore finished, snapshot will be applied on first open")
    }
}
