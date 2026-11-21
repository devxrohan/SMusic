package com.devxrohan.smusic.utils

import android.content.Context

/** Distinguishes installation from app-data recreation using package timestamps. */
fun Context.isFreshInstall(): Boolean = runCatching {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    packageInfo.firstInstallTime == packageInfo.lastUpdateTime
}.getOrDefault(false)
