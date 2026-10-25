package com.devxrohan.smusic.update

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val changelog: String = "",
    val apkSize: Long = 0L,
    val mandatory: Boolean = false,
    /** GitHub release page used as the browser fallback when in-app download fails. */
    val releaseUrl: String = "",
)
