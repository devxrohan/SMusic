package com.devxrohan.smusic.update

import kotlinx.serialization.Serializable

/** Announcement metadata shared by foreground polling and push delivery. */
@Serializable
data class AnnouncementInfo(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val enabled: Boolean = false,
    /** Expiry applies to push announcements; zero retains them until dismissed. */
    val expiresAt: Long = 0L,
    /** Optional publicly accessible HTTPS image URL attached to a push announcement. */
    val imageUrl: String = ""
)

/** Use optional worker-provided developer text only when GitHub metadata is unavailable. */
@Serializable
data class DeveloperInfo(
    val name: String = "",
    val bio: String = "",
)

/** The combined shape returned by the worker: update info + a list of announcements, in one response. */
@Serializable
data class AppManifest(
    val update: UpdateInfo = UpdateInfo(),
    val announcements: List<AnnouncementInfo> = emptyList(),
    val developer: DeveloperInfo? = null,
)
