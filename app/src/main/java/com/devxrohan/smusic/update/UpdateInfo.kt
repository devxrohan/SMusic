package com.devxrohan.smusic.update

import kotlinx.serialization.Serializable

/**
 * The remote update manifest, fetched from the update worker URL in
 * [UpdateChecker]. Ported as-is from the SMusic (com.elite.music) project's
 * update system — same JSON shape, same fields, same meaning.
 */
@Serializable
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val changelog: String = "",
    /** Size of the APK in bytes, so the update screen can show it as MB before downloading. */
    val apkSize: Long = 0L,
    /** Reserved for a future "can't be skipped" update — not enforced yet, just carried through so the manifest shape doesn't need to change to add it later. */
    val mandatory: Boolean = false
)
