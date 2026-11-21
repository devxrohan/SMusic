package com.devxrohan.smusic.utils

/** Represents success or failure of Drive backup and restore operations. */
sealed class DriveResult<out T> {
    data class Success<T>(val data: T) : DriveResult<T>()
    data class Error(val exception: Exception) : DriveResult<Nothing>()
}
