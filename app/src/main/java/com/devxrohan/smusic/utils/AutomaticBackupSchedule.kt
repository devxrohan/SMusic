package com.devxrohan.smusic.utils

/** Manual backup actions bypass this automatic snapshot interval. */
internal object AutomaticBackupSchedule {
    const val LOCAL_INTERVAL = 30 * 60_000L
    fun due(last: Long, now: Long, interval: Long): Boolean = last <= 0 || now < last || now - last >= interval
}
