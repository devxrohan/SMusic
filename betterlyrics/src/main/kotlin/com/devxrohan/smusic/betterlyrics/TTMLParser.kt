package com.devxrohan.smusic.betterlyrics

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader

/** Parses TTML paragraphs and their innermost timed spans. */
object TTMLParser {
    data class ParsedLine(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val words: List<ParsedWord>,
        val isBackground: Boolean = false,
        val agent: String? = null,
    )

    data class ParsedWord(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val isBackground: Boolean = false,
        val syllables: List<ParsedSyllable> = emptyList(),
    )

    data class ParsedSyllable(
        val startTime: Double,
        val endTime: Double,
        val startChar: Int,
        val endChar: Int,
    )

    private val skippedRoles = setOf("x-translation", "x-roman")
    private const val BACKGROUND_ROLE = "x-bg"

    fun parseTTML(ttml: String): List<ParsedLine> = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { isExpandEntityReferences = false }
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
        val elements = document.getElementsByTagName("*")
        val result = ArrayList<ParsedLine>()
        for (index in 0 until elements.length) {
            val paragraph = elements.item(index) as? Element ?: continue
            if (!paragraph.tagName.substringAfter(':').equals("p", ignoreCase = true)) continue
            parseParagraph(paragraph)?.let(result::add)
        }
        result.sortedBy { it.startTime }
    }.getOrDefault(emptyList())

    private fun parseParagraph(paragraph: Element): ParsedLine? {
        val leadPieces = mutableListOf<Piece>()
        val backingPieces = mutableListOf<Piece>()
        collect(paragraph, leadPieces, backingPieces, false)
        val leadWords = mergeIntoWords(leadPieces, false)
        val backingWords = mergeIntoWords(backingPieces, true)
        val begin = time(paragraph.getAttribute("begin"))
        val explicitEnd = time(paragraph.getAttribute("end"))
        val agent = paragraph.qualified("ttm:agent").ifBlank { null }

        if (leadWords.isEmpty() && backingWords.isEmpty()) {
            val text = paragraph.textContent.orEmpty().trim()
            val start = begin ?: return null
            if (text.isEmpty()) return null
            return ParsedLine(
                text = text,
                startTime = start / 1000.0,
                endTime = (explicitEnd?.takeIf { it > start } ?: start) / 1000.0,
                words = emptyList(),
                agent = agent,
            )
        }

        val allWords = buildList {
            addAll(leadWords)
            addAll(backingWords)
        }
        val startMs = minOf(begin ?: leadWords.firstOrNull()?.startMs ?: backingWords.first().startMs,
            leadWords.firstOrNull()?.startMs ?: begin ?: backingWords.first().startMs)
        val endMs = maxOf(explicitEnd ?: 0L, allWords.maxOfOrNull { it.endMs } ?: startMs)
        val displayWords = allWords.map { word ->
            ParsedWord(
                text = word.text,
                startTime = word.startMs / 1000.0,
                endTime = word.endMs / 1000.0,
                isBackground = word.background,
                syllables = word.syllables.map { part ->
                    ParsedSyllable(
                        startTime = part.startMs / 1000.0,
                        endTime = part.endMs / 1000.0,
                        startChar = part.startChar,
                        endChar = part.endChar,
                    )
                },
            )
        }
        return ParsedLine(
            text = allWords.joinToString(" ") { it.text },
            startTime = startMs / 1000.0,
            endTime = endMs / 1000.0,
            words = displayWords,
            agent = agent,
        )
    }

    private fun collect(
        node: Node,
        lead: MutableList<Piece>,
        backing: MutableList<Piece>,
        insideBackground: Boolean,
    ) {
        val children = node.childNodes
        for (index in 0 until children.length) {
            when (val child = children.item(index)) {
                is Element -> {
                    val role = child.qualified("ttm:role")
                    if (role in skippedRoles) continue
                    val isBackground = insideBackground || role == BACKGROUND_ROLE
                    val sink = if (isBackground) backing else lead
                    val start = time(child.getAttribute("begin"))
                    val end = time(child.getAttribute("end"))
                    if (start != null && end != null && !hasTimedChild(child)) {
                        val value = child.textContent.orEmpty()
                        if (value.isNotBlank()) sink += Piece.Timed(value, start, end)
                    } else {
                        collect(child, lead, backing, isBackground)
                    }
                }
                else -> if (child.nodeType == Node.TEXT_NODE) {
                    val value = child.textContent.orEmpty()
                    if (value.isNotEmpty()) (if (insideBackground) backing else lead) += Piece.Text(value)
                }
            }
        }
    }

    private fun hasTimedChild(element: Element): Boolean {
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.getAttribute("begin").isNotEmpty() || hasTimedChild(child)) return true
        }
        return false
    }

    private fun mergeIntoWords(pieces: List<Piece>, background: Boolean): List<TimedWord> {
        val words = mutableListOf<TimedWord>()
        val text = StringBuilder()
        val syllables = mutableListOf<TimedSyllable>()
        var start = 0L
        var end = 0L
        var timed = false

        fun flush() {
            val value = text.toString().trim()
            if (value.isNotEmpty() && timed) {
                val normalized = normalizeSyllables(syllables, value.length)
                words += TimedWord(start, end, value, background, normalized)
            }
            text.setLength(0)
            syllables.clear()
            timed = false
        }

        pieces.forEach { piece ->
            when (piece) {
                is Piece.Text -> when {
                    piece.value.isBlank() -> flush()
                    timed -> text.append(piece.value)
                }
                is Piece.Timed -> {
                    if (piece.value.isBlank()) return@forEach
                    if (piece.value.first().isWhitespace()) flush()
                    if (text.isEmpty()) start = piece.start
                    val from = text.length
                    val clean = piece.value.trim()
                    text.append(clean)
                    syllables += TimedSyllable(piece.start, piece.end, from, text.length)
                    end = piece.end
                    timed = true
                    if (piece.value.last().isWhitespace()) flush()
                }
            }
        }
        flush()
        return words
    }

    private fun normalizeSyllables(parts: List<TimedSyllable>, length: Int): List<TimedSyllable> {
        val usable = parts.filter { it.endChar > it.startChar && it.startChar < length }
            .sortedBy { it.startChar }
        if (usable.size < 2) return emptyList()
        return usable.mapIndexed { index, part ->
            part.copy(
                startChar = if (index == 0) 0 else part.startChar,
                endChar = usable.getOrNull(index + 1)?.startChar ?: length,
            )
        }
    }

    private fun Element.qualified(name: String): String {
        getAttribute(name).takeIf { it.isNotEmpty() }?.let { return it }
        val local = name.substringAfter(':')
        val attrs = attributes ?: return ""
        for (index in 0 until attrs.length) {
            val attr = attrs.item(index) ?: continue
            if (attr.nodeName == name || attr.nodeName == local || attr.localName == local) {
                return attr.nodeValue.orEmpty()
            }
        }
        return ""
    }

    /** TTML clock values in milliseconds: seconds, mm:ss, hh:mm:ss, or ms units. */
    private fun time(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.endsWith("ms")) return raw.dropLast(2).toDoubleOrNull()?.let { it.toLong() }
        val parts = raw.removeSuffix("s").split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()
            2 -> parts[0].toDoubleOrNull()?.let { minutes ->
                parts[1].toDoubleOrNull()?.let { minutes * 60.0 + it }
            }
            3 -> parts[0].toDoubleOrNull()?.let { hours ->
                parts[1].toDoubleOrNull()?.let { minutes ->
                    parts[2].toDoubleOrNull()?.let { hours * 3600.0 + minutes * 60.0 + it }
                }
            }
            else -> null
        } ?: return null
        return (seconds * 1000.0).toLong()
    }

    private data class TimedWord(
        val startMs: Long,
        val endMs: Long,
        val text: String,
        val background: Boolean = false,
        val syllables: List<TimedSyllable> = emptyList(),
    )
    private data class TimedSyllable(
        val startMs: Long,
        val endMs: Long,
        val startChar: Int,
        val endChar: Int,
    )

    private sealed interface Piece {
        data class Text(val value: String) : Piece
        data class Timed(val value: String, val start: Long, val end: Long) : Piece
    }
}
