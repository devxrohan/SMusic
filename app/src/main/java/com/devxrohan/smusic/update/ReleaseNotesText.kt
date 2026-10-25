package com.devxrohan.smusic.update

/**
 * Turns a GitHub release body (Markdown, or the plain text made from the Atom feed) into clean,
 * readable plain text:
 *  - line breaks are kept as written (GitHub shows single newlines as line breaks too),
 *  - never more than ONE blank line in a row, no leading/trailing blank lines,
 *  - Markdown syntax is removed (# headings, ** bold **, `code`, [links](url), images, quotes, rules),
 *  - bullets become "•", leaked HTML (<br>, <p>, comments ...) is cleaned up.
 */
fun formatReleaseNotes(raw: String): String {
    var text = raw.replace("\r\n", "\n").replace('\r', '\n')
    if (text.isBlank()) return ""

    // HTML that ended up inside the Markdown.
    text = text
        .replace(Regex("(?s)<!--.*?-->"), "")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)<li[^>]*>"), "\n- ")
        .replace(Regex("(?i)</?(p|div|ul|ol|h[1-6]|details|summary|blockquote|pre|table|tr)[^>]*>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    val lines = ArrayList<String>()
    var inCodeBlock = false
    for (rawLine in text.split('\n')) {
        if (rawLine.trim().startsWith("```")) {
            inCodeBlock = !inCodeBlock
            continue
        }
        if (inCodeBlock) {
            lines += rawLine.trimEnd()
            continue
        }

        var line = rawLine.trim()
        if (line.isEmpty()) {
            lines += ""
            continue
        }
        if (Regex("^([-*_]\\s*){3,}$").matches(line)) { // horizontal rule
            lines += ""
            continue
        }

        line = line
            .replace(Regex("^#{1,6}\\s*"), "")                    // headings
            .replace(Regex("^>\\s?"), "")                         // quotes
            .replace(Regex("^[-*+]\\s+\\[[ xX]]\\s+"), "• ")      // task lists
            .replace(Regex("^[-*+]\\s+"), "• ")                   // bullets
            .replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")    // images -> alt text
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")     // [text](url) -> text
            .replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2")          // bold
            .replace("`", "")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()
        lines += line
    }

    // At most one blank line in a row; nothing blank at the start or end.
    val out = ArrayList<String>()
    for (line in lines) {
        if (line.isEmpty() && (out.isEmpty() || out.last().isEmpty())) continue
        out += line
    }
    while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.size - 1)
    return out.joinToString("\n")
}
