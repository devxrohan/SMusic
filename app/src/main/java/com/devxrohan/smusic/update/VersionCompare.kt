package com.devxrohan.smusic.update

/** Version-name comparison shared by the checker, the notification code and APK cleanup. */
object VersionCompare {
    private data class ParsedVersion(
        val parts: List<Int>,
        val preRelease: String?,
    )

    private fun parseVersion(raw: String): ParsedVersion {
        val clean = raw.trim().removePrefix("v").substringBefore('+')
        val dash = clean.indexOf('-')
        val base = if (dash >= 0) clean.substring(0, dash) else clean
        return ParsedVersion(
            parts = base.split('.').map { it.toIntOrNull() ?: 0 },
            preRelease = if (dash >= 0) clean.substring(dash + 1).takeIf { it.isNotBlank() } else null,
        )
    }

    fun compare(latest: String, current: String): Int {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }

        // A stable release is newer than any pre-release with the same base version.
        if (l.preRelease == null && c.preRelease != null) return 1
        if (l.preRelease != null && c.preRelease == null) return -1
        if (l.preRelease == null && c.preRelease == null) return 0

        val left = l.preRelease!!.split('.', '-')
        val right = c.preRelease!!.split('.', '-')
        for (i in 0 until maxOf(left.size, right.size)) {
            if (i >= left.size) return -1
            if (i >= right.size) return 1
            val a = left[i]
            val b = right[i]
            val an = a.toIntOrNull()
            val bn = b.toIntOrNull()
            val result = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1
                bn != null -> 1
                else -> a.compareTo(b, ignoreCase = true)
            }
            if (result != 0) return result
        }
        return 0
    }

    fun isNewer(latest: String, current: String): Boolean = compare(latest, current) > 0
}
