package com.devxrohan.smusic

import com.devxrohan.smusic.utils.Mp4CoverWriter
import java.nio.ByteBuffer
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class Mp4CoverWriterTest {
    private val audio = ByteArray(200) { (it % 127).toByte() }
    private val image = byteArrayOf(-1, -40, -1, -39)
    private fun box(type: String, data: ByteArray) = ByteBuffer.allocate(data.size + 8)
        .putInt(data.size + 8).put(type.toByteArray(Charsets.ISO_8859_1)).put(data).array()
    private fun movie(offset: Long, wide: Boolean): ByteArray {
        val data = ByteBuffer.allocate(if (wide) 16 else 12).putInt(0).putInt(1)
        if (wide) data.putLong(offset) else data.putInt(offset.toInt())
        return box("moov", box("trak", box("mdia", box("minf", box("stbl", box(if (wide) "co64" else "stco", data.array()))))))
    }
    private fun fixture(front: Boolean, wide: Boolean): ByteArray {
        val header = box("ftyp", "M4A ".toByteArray())
        val placeholder = movie(0, wide)
        val position = header.size + 8 + if (front) placeholder.size else 0
        val moov = movie(position.toLong(), wide)
        return if (front) header + moov + box("mdat", audio) else header + box("mdat", audio) + moov
    }
    private fun locate(bytes: ByteArray, type: String): Int {
        val id = type.toByteArray(Charsets.ISO_8859_1)
        return (0..bytes.size - id.size).first { p -> id.indices.all { bytes[p + it] == id[it] } }
    }
    @Test fun artworkAndTextTagsPreserveAudioForBothMoovLayoutsAndOffsetWidths() {
        for (front in listOf(true, false)) for (wide in listOf(true, false)) {
            val root = Files.createTempDirectory("mp4-cover-test").toFile()
            try {
                val source = root.resolve("input.m4a").apply { writeBytes(fixture(front, wide)) }
                val output = root.resolve("output.m4a")
                Mp4CoverWriter.write(source, output, "A café song", "Artist", "Album", image)
                val bytes = output.readBytes()
                val offset = locate(bytes, if (wide) "co64" else "stco") + 12
                val dataPosition = if (wide) ByteBuffer.wrap(bytes, offset, 8).long.toInt() else ByteBuffer.wrap(bytes, offset, 4).int
                assertArrayEquals(audio, bytes.copyOfRange(dataPosition, dataPosition + audio.size))
                assertTrue(String(bytes, Charsets.UTF_8).contains("A café song"))
                val cover = locate(bytes, "covr")
                assertEquals(13, ByteBuffer.wrap(bytes, cover + 12, 4).int)
                assertArrayEquals(image, bytes.copyOfRange(cover + 20, cover + 20 + image.size))
            } finally { root.deleteRecursively() }
        }
    }
    @Test fun sequentialExportsKeepTheirOwnEmbeddedArtwork() {
        val root = Files.createTempDirectory("mp4-distinct-cover").toFile()
        try {
            val source = root.resolve("input.m4a").apply { writeBytes(fixture(false, false)) }
            val first = root.resolve("first.m4a")
            val second = root.resolve("second.m4a")
            val otherImage = byteArrayOf(-1, -40, 1, 2, 3, -1, -39)
            Mp4CoverWriter.write(source, first, "First", "Artist", "First single", image)
            Mp4CoverWriter.write(source, second, "Second", "Artist", "Second single", otherImage)
            for ((file, expected) in listOf(first to image, second to otherImage)) {
                val bytes = file.readBytes()
                val cover = locate(bytes, "covr")
                assertArrayEquals(expected, bytes.copyOfRange(cover + 20, cover + 20 + expected.size))
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun malformedAndFragmentedInputsAreRejectedBeforePublication() {
        val root = Files.createTempDirectory("mp4-cover-invalid").toFile()
        try {
            val source = root.resolve("input.m4a")
            val output = root.resolve("output.m4a")
            for (bytes in listOf(byteArrayOf(1, 2, 3), box("moof", byteArrayOf()) + fixture(false, false))) {
                source.writeBytes(bytes)
                assertThrows(Exception::class.java) { Mp4CoverWriter.write(source, output, "Song", "Artist", null, image) }
                assertFalse(output.exists())
                assertArrayEquals(bytes, source.readBytes())
            }
        } finally { root.deleteRecursively() }
    }
}
