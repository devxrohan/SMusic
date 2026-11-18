package com.devxrohan.smusic.lyrics

import java.text.Normalizer
import java.util.Locale

/**
 * Prepare YouTube playback metadata for lyric catalogues.
 *
 * YouTube titles often contain upload labels and feature credits which are
 * absent from catalogue entries. Recording/version labels (Live, Remix,
 * Acoustic, Slowed, etc.) are deliberately preserved.
 */
fun String.forLyricsSearch(): String {
    var value = this.trim()
    TITLE_PREFIX.replace(value, "").also { value = it }
    TITLE_SUFFIXES.forEach { pattern -> value = pattern.replace(value, " ") }
    return value.replace(WHITESPACE, " ").trim()
        .trimEnd(',', '-', '–', '—', '|', ':').trim()
        .ifBlank { this.trim() }
}

/** Normalize YouTube artist/channel metadata without merging away collaborators. */
fun String.artistForLyricsSearch(): String {
    var value = this.trim()
    ARTIST_PREFIXES.forEach { value = it.replace(value, " ") }
    ARTIST_SUFFIXES.forEach { value = it.replace(value, " ") }
    value = value.replace(TOPIC_SUFFIX, "")
        .replace(WHITESPACE, " ")
        .trim().trim(',', '·', '|', '-', '–', '—')
    return value.ifBlank { this.trim() }
}

/** Catalogue-safe comparison key; accents and punctuation do not affect a match. */
internal fun String.lyricsMatchKey(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .replace(WHITESPACE, " ")
        .trim()

/** Split collaborator credits so artist order and separators do not matter. */
internal fun String.lyricsArtistKeys(): Set<String> =
    split(ARTIST_SPLIT)
        .map { it.lyricsMatchKey() }
        .filter { it.isNotBlank() }
        .toSet()

private val WHITESPACE = Regex("""\s+""")
private val COMBINING_MARKS = Regex("""\p{Mn}+""")
private val TITLE_PREFIX = Regex("""^\s*(?:official\s+)?song\s+title\s*[:：-]?\s*""", RegexOption.IGNORE_CASE)
private val TITLE_SUFFIXES = listOf(
    Regex("""\s*[(\[]\s*(feat|ft|featuring|with)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE),
    Regex("""\s+(feat|ft|featuring)\.?\s+.*$""", RegexOption.IGNORE_CASE),
    Regex("""\s*[(\[]\s*(official\s*)?(music\s*)?(video|audio|visuali[sz]er|lyrics?\s*video|lyrics?|m/?v|hd|hq|4k|full\s*song)\s*[)\]]""", RegexOption.IGNORE_CASE),
    Regex("""\s*[(\[]\s*official\s*[)\]]""", RegexOption.IGNORE_CASE),
    Regex("""\s+song\s+artist\b.*$""", RegexOption.IGNORE_CASE),
    Regex("""\s+song\s+title\b.*$""", RegexOption.IGNORE_CASE),
    Regex("""\s*\d+(?:[.,]\d+)?\s*[KMB]?\s*(?:plays?|views?|streams?)\b.*$""", RegexOption.IGNORE_CASE),
    Regex("""\s*[|·]\s*(?:official\s+)?(?:audio|video|lyrics?)\b.*$""", RegexOption.IGNORE_CASE),
    Regex("""\s+official\s+(?:music\s+)?(?:video|audio|lyric(?:s)?(?:\s+video)?|visuali[sz]er)\s*$""", RegexOption.IGNORE_CASE),
)
private val ARTIST_PREFIXES = listOf(
    // A bare "Artist" can be part of the performer's name, not a metadata label.
    Regex("""^\s*(?:song\s+artist\b\s*[:：-]?|artist\s*[:：-])\s*""", RegexOption.IGNORE_CASE),
    Regex("""^\s*various\s+artists\s*[:：-]?\s*""", RegexOption.IGNORE_CASE),
)
private val ARTIST_SUFFIXES = listOf(
    Regex("""\s+\d+(?:[.,]\d+)?\s*[KMB]?\s*(?:plays?|views?|streams?)\b.*$""", RegexOption.IGNORE_CASE),
    Regex("""\s*[|·]\s*(?:official\s+)?(?:topic|channel)\b.*$""", RegexOption.IGNORE_CASE),
)
private val TOPIC_SUFFIX = Regex("""\s+-\s+Topic$""", RegexOption.IGNORE_CASE)
private val ARTIST_SPLIT = Regex(
    """\s*(?:,|&|;|/|\s+and\s+|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*""",
    RegexOption.IGNORE_CASE,
)
