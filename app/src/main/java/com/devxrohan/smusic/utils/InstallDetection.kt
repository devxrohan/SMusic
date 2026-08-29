package com.devxrohan.smusic.utils

import android.content.Context

fun Context.isFreshInstall(): Boolean {
    val genuinelyFreshApk = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)
    if (genuinelyFreshApk) return true

    // Not a fresh APK install — but Clear Data wipes the Room database too
    // (unlike a normal app update, which leaves it untouched). A song
    // database that doesn't exist yet, or is empty/schema-only, means
    // there's no real prior library to speak of, so treat this the same
    // as fresh for onboarding purposes.
    return runCatching {
        val dbFile = getDatabasePath("song.db")
        !dbFile.exists() || dbFile.length() < 50_000L
    }.getOrDefault(false)
}
