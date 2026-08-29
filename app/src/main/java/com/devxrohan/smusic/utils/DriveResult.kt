package com.devxrohan.smusic.utils

/**
 * Result type for Google Drive backup/restore operations
 * (BackupRestoreViewModel, DailyBackupWorker, App.kt, BackupAndRestore.kt).
 * Genuinely missing from the project before this — used everywhere but
 * never actually declared.
 */
sealed class DriveResult<out T> {
    data class Success<T>(val data: T) : DriveResult<T>()
    data class Error(val exception: Exception) : DriveResult<Nothing>()
}
