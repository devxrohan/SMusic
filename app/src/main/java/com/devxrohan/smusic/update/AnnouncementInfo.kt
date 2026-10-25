package com.devxrohan.smusic.update

import kotlinx.serialization.Serializable

/**
 * A developer-written announcement, served from the same worker endpoint
 * as the update manifest. The developer edits this in the worker's source
 * and bumps [id] whenever there's something new — the app treats a
 * different id as "new" and shows it via the notification bell / a system
 * notification, and treats the same id as "already seen, don't repeat".
 */
@Serializable
data class AnnouncementInfo(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val enabled: Boolean = false,
    /**
     * Push messages only. Epoch millis after which the message disappears from the bell (and from
     * the notification tray). 0 = no time limit: it stays until the user swipes the notification away.
     */
    val expiresAt: Long = 0L,
    /** Optional publicly accessible HTTPS image URL attached to a push announcement. */
    val imageUrl: String = ""
)

/**
 * Optional developer card text served by the worker. Only used as a last
 * fallback for the Developer screen when GitHub cannot be reached. Leave it
 * out of the worker response if you don't need it.
 */
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
