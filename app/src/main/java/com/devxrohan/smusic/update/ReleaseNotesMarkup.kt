package com.devxrohan.smusic.update

import org.jsoup.Jsoup

private val releaseHtmlTables = Regex("<table\\b[^>]*>.*?</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val tableCellNewlines = Regex("\\s*\\n\\s*")

/** Atom supplies HTML tables; convert those to GFM tables supported by the native renderer. */
internal fun releaseNotesMarkup(raw: String): String = releaseHtmlTables.replace(raw) { match ->
    val table = Jsoup.parseBodyFragment(match.value).selectFirst("table")
    val rows = table?.select("tr")?.map { row ->
        row.children().filter { it.tagName() == "th" || it.tagName() == "td" }
            .map { it.html().replace(tableCellNewlines, " ").replace("|", "\\|").trim() }
    }?.filter { it.isNotEmpty() }.orEmpty()
    if (rows.isEmpty()) match.value else {
        val columns = rows.maxOf { it.size }
        fun line(cells: List<String>) = "| " + (cells + List(columns - cells.size) { "" }).joinToString(" | ") + " |"
        "\n\n" + line(rows.first()) + "\n" + line(List(columns) { "---" }) + "\n" +
            rows.drop(1).joinToString("\n", transform = ::line) + "\n\n"
    }
}
