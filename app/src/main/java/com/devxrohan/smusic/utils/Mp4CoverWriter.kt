package com.devxrohan.smusic.utils

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/** Adds portable iTunes tags to an AAC export without loading the audio into memory. */
internal object Mp4CoverWriter {
    private data class Atom(val offset: Long, val size: Long, val header: Int, val type: String) {
        val end get() = offset + size
    }
    private val containers = setOf("moov", "trak", "mdia", "minf", "stbl")

    fun write(source: File, target: File, title: String, artist: String, album: String?, cover: ByteArray?) {
        require(source.canonicalFile != target.canonicalFile)
        RandomAccessFile(source, "r").use { input ->
            val roots = atoms(input.length()) { offset, length -> input.seek(offset); ByteArray(length).also(input::readFully) }
            require(roots.none { it.type == "moof" }) { "Export must be remuxed before tagging" }
            require(roots.any { it.type == "mdat" }) { "Export has no media data" }
            val moov = roots.single { it.type == "moov" }
            require(moov.size <= 16 * 1024 * 1024) { "Export metadata is too large" }
            val original = ByteArray(moov.size.toInt()).also { input.seek(moov.offset); input.readFully(it) }
            val children = atoms(original.size.toLong(), moov.header.toLong()) { offset, length ->
                original.copyOfRange(offset.toInt(), offset.toInt() + length)
            }
            val fields = listOfNotNull(text("©nam", title), text("©ART", artist), album?.let { text("©alb", it) },
                cover?.takeIf { it.isNotEmpty() }?.let { atom("covr", data(13, it)) })
            val handler = ByteArray(25).also { "mdir".toByteArray(Charsets.ISO_8859_1).copyInto(it, 8) }
            val tags = atom("udta", atom("meta", ByteArray(4) + atom("hdlr", handler) + atom("ilst", join(fields))))
            val retained = children.filter { it.type != "udta" }.map { child ->
                original.copyOfRange(child.offset.toInt(), child.end.toInt()).also {
                    if (uint(it, 0) == 0L) ByteBuffer.wrap(it).putInt(it.size)
                }
            }
            val updated = atom("moov", join(retained + listOf(tags)))
            val delta = updated.size.toLong() - moov.size
            patchOffsets(updated, 8, updated.size, moov.end, delta)
            try {
                FileOutputStream(target).use { output ->
                    copy(input, output, 0, moov.offset)
                    output.write(updated)
                    copy(input, output, moov.end, input.length() - moov.end)
                }
            } catch (failure: Exception) { target.delete(); throw failure }
        }
    }

    private fun patchOffsets(bytes: ByteArray, start: Int, end: Int, insertion: Long, delta: Long) {
        val children = atoms(end.toLong(), start.toLong()) { offset, length -> bytes.copyOfRange(offset.toInt(), offset.toInt() + length) }
        children.forEach { box ->
            val payload = (box.offset + box.header).toInt()
            when (box.type) {
                "stco", "co64" -> {
                    require(box.size >= box.header + 8)
                    val count = uint(bytes, payload + 4)
                    val width = if (box.type == "stco") 4 else 8
                    require(count <= (box.size - box.header - 8) / width)
                    repeat(count.toInt()) { index ->
                        val position = payload + 8 + index * width
                        val old = if (width == 4) uint(bytes, position) else ByteBuffer.wrap(bytes, position, 8).long
                        require(old >= 0)
                        if (old >= insertion) {
                            val adjusted = Math.addExact(old, delta)
                            require(adjusted >= 0 && (width == 8 || adjusted <= 0xffff_ffffL))
                            if (width == 4) ByteBuffer.wrap(bytes, position, 4).putInt(adjusted.toInt())
                            else ByteBuffer.wrap(bytes, position, 8).putLong(adjusted)
                        }
                    }
                }
                in containers -> patchOffsets(bytes, payload, box.end.toInt(), insertion, delta)
            }
        }
    }

    private fun atoms(end: Long, start: Long = 0, read: (Long, Int) -> ByteArray): List<Atom> {
        val result = ArrayList<Atom>()
        var position = start
        while (position < end) {
            require(end - position >= 8) { "Incomplete MP4 box" }
            val header = read(position, 8)
            val raw = uint(header, 0)
            val size = when (raw) { 0L -> end - position; 1L -> {
                require(end - position >= 16)
                ByteBuffer.wrap(read(position + 8, 8)).long
            }; else -> raw }
            val headerSize = if (raw == 1L) 16 else 8
            require(size >= headerSize && size <= end - position) { "Invalid MP4 box size" }
            require(result.size < 100_000) { "Too many MP4 boxes" }
            result += Atom(position, size, headerSize, String(header, 4, 4, Charsets.ISO_8859_1))
            position += size
        }
        return result
    }

    private fun copy(input: RandomAccessFile, output: FileOutputStream, start: Long, length: Long) {
        input.seek(start)
        var remaining = length
        val buffer = ByteArray(64 * 1024)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            check(read > 0) { "Incomplete export" }
            output.write(buffer, 0, read)
            remaining -= read
        }
    }
    private fun uint(bytes: ByteArray, offset: Int) = ByteBuffer.wrap(bytes, offset, 4).int.toLong() and 0xffff_ffffL
    private fun atom(type: String, payload: ByteArray): ByteArray = ByteBuffer.allocate(8 + payload.size)
        .putInt(8 + payload.size).put(type.toByteArray(Charsets.ISO_8859_1)).put(payload).array()
    private fun data(kind: Int, payload: ByteArray) = atom("data", ByteBuffer.allocate(8 + payload.size).putInt(kind).putInt(0).put(payload).array())
    private fun text(type: String, value: String) = atom(type, data(1, value.toByteArray(Charsets.UTF_8)))
    private fun join(parts: List<ByteArray>): ByteArray = ByteArray(parts.sumOf { it.size }).also { result ->
        var offset = 0
        parts.forEach { it.copyInto(result, offset); offset += it.size }
    }
}
