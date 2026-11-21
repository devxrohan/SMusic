package com.devxrohan.smusic.update

/** Never compare a fabricated semantic version code to Android's independent build counter. */
internal object UpdateEligibility {
    private val versionPattern = Regex("""[vV]?\d+(?:\.\d+)*(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?""")

    fun newerCode(latest: String, publishedCode: Int?, current: String, currentCode: Int): Int? {
        val code = publishedCode?.takeIf { it > 0 }
        val latestName = latest.trim().removePrefix("V")
        val currentName = current.trim().removePrefix("V")
        val namesValid = versionPattern.matches(latestName) && versionPattern.matches(currentName)
        if (namesValid && VersionCompare.compare(latestName, currentName) < 0) return null
        if (code != null) return code.takeIf { it > currentCode }
        if (!namesValid || !VersionCompare.isNewer(latestName, currentName)) return null
        // Releases without build metadata can be offered by name. This is only a notification
        // marker; Android checks the APK's real versionCode when installing it.
        return currentCode.takeIf { it < Int.MAX_VALUE }?.plus(1)
    }
}
