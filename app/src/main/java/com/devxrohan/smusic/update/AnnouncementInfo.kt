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
    val enabled: Boolean = false
)

/** The combined shape returned by the worker: update info + a list of announcements, in one response. */
@Serializable
data class AppManifest(
    val update: UpdateInfo = UpdateInfo(),
    val announcements: List<AnnouncementInfo> = emptyList()
)
