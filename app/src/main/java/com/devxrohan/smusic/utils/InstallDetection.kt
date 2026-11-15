package com.devxrohan.smusic.utils

import android.content.Context

/**
 * Detects a genuinely new package installation.
 *
 * PackageInfo timestamps survive app updates and clearing app data. Therefore an
 * existing installation is not mistaken for a first install just because its
 * Room database is missing, empty, or still being created. The caller persists
 * the result once in DataStore so an update during onboarding cannot change the
 * original decision.
 */
fun Context.isFreshInstall(): Boolean = runCatching {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    packageInfo.firstInstallTime == packageInfo.lastUpdateTime
}.getOrDefault(false)
