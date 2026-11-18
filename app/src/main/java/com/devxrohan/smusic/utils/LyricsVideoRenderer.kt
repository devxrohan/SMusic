/*
 * SMusic Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.devxrohan.smusic.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.GLUtils
import android.media.MediaCodec
import android.media.AudioFormat
import android.os.SystemClock
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AppFont
import com.devxrohan.smusic.constants.AudioQuality
import com.devxrohan.smusic.lyrics.LyricsEntry
import com.devxrohan.smusic.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * Turns a picked passage of lyrics into a shareable MP4: the same backdrop,
 * brand mark and artwork the still "share card" draws (see
 * [LyricsShareRenderer], whose background/brand/artwork drawing this reuses
 * as-is so the look stays identical), the song's own audio underneath it,
 * and the selected lyrics staying in fixed positions. The active line fades
 * in and out as the song progresses; the lyric block never scrolls and
 * there is no word-by-word karaoke effect.
 *
 * Frames are drawn on a Canvas bitmap and uploaded through EGL to the encoder's
 * input Surface. EGL assigns explicit presentation timestamps starting at zero,
 * so export runs as fast as the device encoder allows and the MP4 duration does
 * not inherit the phone's uptime. Audio is decoded once and re-encoded to AAC
 * (Android's `MediaMuxer` can only write an MP4 audio track in a codec it
 * understands, and this app's own stream picker actually prefers Opus —
 * see `codecRank` in [YTPlayerUtils] — so assuming AAC and copying bytes
 * straight through silently produced a silent video for almost every
 * song); an already-AAC source just has its samples copied through as-is.
 */
object LyricsVideoRenderer {

    sealed class AudioSource {
        data class Remote(val url: String) : AudioSource()
        data class Local(val uri: Uri) : AudioSource()
    }

    data class Progress(val fraction: Float, val stage: String)

    data class RenderResult(val uri: Uri, val hasAudio: Boolean)

    private data class WordWindow(val text: String, val startMs: Long, val endMs: Long)

    private data class TimedLine(
        val text: String,
        val startMs: Long,
        val endMs: Long,
        val words: List<WordWindow>,
    )

    /** One selected lyric line, laid out by the same plan used for still cards. */
    private data class LineLayout(
        val line: TimedLine,
        val rows: List<ShareLyricRow>,
        val topY: Float,
    )

    private const val FPS = 30
    private const val VIDEO_BITRATE = 2_800_000
    private const val AUDIO_BITRATE = 128_000

    private const val DEFAULT_LINE_MS = 2_600L
    // Was 420ms — the fade-in/fade-out still uses the same smoothstep curve
    // below, just stretched out so the active line's brightness change reads
    // as a smooth ease instead of a near-instant snap.
    private const val FADE_MS = 650L
    private const val INACTIVE_ALPHA = 0.48f

    /**
     * @param entries the selected lines with their own timing, when the caller has
     *   it (the lyrics screen does); pass an empty list when only plain text is
     *   available (e.g. shared from the lyrics overflow menu) and [fallbackText]
     *   alone drives an evenly-paced animation instead.
     */
    suspend fun render(
        context: Context,
        entries: List<LyricsEntry>,
        fallbackText: String,
        songTitle: String,
        artistName: String,
        artworkUrl: String?,
        appFont: AppFont,
        mediaMetadata: MediaMetadata?,
        style: LyricsVideoStyle = LyricsVideoStyle.LYRICS,
        onProgress: (Progress) -> Unit = {},
    ): RenderResult = withContext(Dispatchers.Default) {
        onProgress(Progress(0f, context.getString(R.string.lyrics_share_stage_preparing)))

        val rawLines = buildTimedLines(entries, fallbackText)
        require(rawLines.isNotEmpty()) { "Nothing to animate" }

        val window = lyricsClipWindow(rawLines.first().startMs, rawLines.last().endMs)
        val clipStart = window.startMs
        val totalMs = window.durationMs

        val lines = rawLines.mapNotNull { line ->
            val s = (line.startMs - clipStart).coerceIn(0L, totalMs)
            val e = (line.endMs - clipStart).coerceIn(0L, totalMs)
            if (e <= s) return@mapNotNull null
            val words = line.words.map { w ->
                w.copy(
                    startMs = (w.startMs - clipStart).coerceIn(s, e),
                    endMs = (w.endMs - clipStart).coerceIn(s, e),
                )
            }
            line.copy(startMs = s, endMs = e, words = words)
        }
        require(lines.isNotEmpty()) { "Nothing to animate" }

        val type = Fonts(context, appFont)
        val cover = artworkUrl?.let { LyricsShareRenderer.loadBitmap(context, it) }
        val width = CARD_W
        val height = MAX_CARD_H.roundToInt()

        val backdrop = buildBackdrop(context, cover, songTitle, artistName, type, width, height, style)
        val layouts = layoutLines(lines, type)

        val audioSource = mediaMetadata?.let { metadata ->
            withTimeoutOrNull(20_000L) { resolveAudioSource(context, metadata, forceAac = true) }
                ?: withTimeoutOrNull(20_000L) { resolveAudioSource(context, metadata) }
        }

        val workDir = File(context.cacheDir, "videos").apply { mkdirs() }
        val outFile = File(workDir, "lyrics_${System.currentTimeMillis()}.mp4")

        try {
            val hasAudio = encode(
                context = context,
                outFile = outFile,
                width = width,
                height = height,
                totalMs = totalMs,
                backdrop = backdrop,
                layouts = layouts,
                type = type,
                audioSource = audioSource,
                mediaMetadata = mediaMetadata,
                clipStartMs = clipStart,
                cover = cover,
                style = style,
                songTitle = songTitle,
                artistName = artistName,
                onProgress = onProgress,
            )

            onProgress(Progress(0.95f, context.getString(R.string.lyrics_share_stage_saving)))
            val uri = saveToGallery(context, outFile)
            onProgress(Progress(1f, context.getString(R.string.lyrics_share_stage_done)))
            RenderResult(uri, hasAudio)
        } catch (failure: Throwable) {
            outFile.delete()
            throw failure
        } finally {
            backdrop.recycle()
        }
    }

    /** A still first-frame preview using the same fixed-position fade treatment
     * as the exported video. */
    suspend fun renderPreview(
        context: Context,
        entries: List<LyricsEntry>,
        fallbackText: String,
        songTitle: String,
        artistName: String,
        artworkUrl: String?,
        appFont: AppFont,
        style: LyricsVideoStyle = LyricsVideoStyle.LYRICS,
    ): Bitmap = withContext(Dispatchers.Default) {
        val lines = buildTimedLines(entries, fallbackText)
        val type = Fonts(context, appFont)
        val cover = artworkUrl?.let { LyricsShareRenderer.loadBitmap(context, it) }
        val width = CARD_W
        val height = MAX_CARD_H.roundToInt()
        val backdrop = buildBackdrop(context, cover, songTitle, artistName, type, width, height, style)
        if (lines.isEmpty()) return@withContext backdrop
        val layouts = layoutLines(lines, type)
        val canvas = Canvas(backdrop)
        val tMs = (lines.first().startMs + 120L).coerceAtMost(lines.first().endMs - 1).coerceAtLeast(0L)
        if (style == LyricsVideoStyle.LYRICS) drawLyricStack(canvas, width, layouts, tMs)
        else drawLyricsVideoScene(canvas, cover, type, style, 120L, lines.first().text, songTitle, artistName)
        backdrop
    }

    /** The backdrop + brand + song footer never change frame to frame in the
     * real export, so they're drawn once and just blitted whole into every
     * frame — only the animating lyric stack is redrawn per frame. */
    private fun buildBackdrop(
        context: Context,
        cover: Bitmap?,
        songTitle: String,
        artistName: String,
        type: Fonts,
        width: Int,
        height: Int,
        style: LyricsVideoStyle,
    ): Bitmap {
        val backdrop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(backdrop)
        drawBackdrop(c, cover, height.toFloat())
        drawBrand(c, context, type)
        if (style != LyricsVideoStyle.LYRICS) return backdrop
        val thumbY = height - FOOTER_PAD - FOOTER_THUMB
        drawArtwork(c, cover, songTitle, CARD_MARGIN, thumbY, FOOTER_THUMB)
        val textX = CARD_MARGIN + FOOTER_THUMB + 34f
        val room = width - CARD_MARGIN - textX
        val titlePaint = type.body(50f, Color.WHITE, bold = true)
        c.drawText(ellipsised(songTitle, titlePaint, room), textX, thumbY + 74f, titlePaint)
        val artistPaint = type.body(42f, 0x99FFFFFF.toInt())
        c.drawText(ellipsised(artistName, artistPaint, room), textX, thumbY + 132f, artistPaint)
        return backdrop
    }

    /** Where [mediaMetadata]'s audio actually lives: a local file already has a playable [Uri] as its id; a YouTube song needs its stream URL resolved the same way the player itself resolves it. */
    private suspend fun resolveAudioSource(context: Context, mediaMetadata: MediaMetadata, forceAac: Boolean = false): AudioSource? {
        val id = mediaMetadata.id
        if (id.startsWith("content://") || id.startsWith("file://")) {
            return AudioSource.Local(Uri.parse(id))
        }
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        // AAC can be muxed directly into MP4; Opus remains the fallback for sources without AAC.
        val avoidCodecs = if (forceAac || !supportsOpusDecoder()) setOf("opus") else emptySet()
        val result = YTPlayerUtils.playerResponseForPlayback(
            videoId = id,
            audioQuality = AudioQuality.HIGH,
            connectivityManager = connectivityManager,
            avoidCodecs = avoidCodecs,
        ).getOrNull()
        return result?.streamUrl?.let { AudioSource.Remote(it) }
    }

    private fun supportsOpusDecoder(): Boolean = runCatching {
        val format = MediaFormat.createAudioFormat("audio/opus", 48_000, 2)
        MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) != null
    }.getOrDefault(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

    // ── Timed lines ──────────────────────────────────────────────────────

    private fun buildTimedLines(entries: List<LyricsEntry>, fallbackText: String): List<TimedLine> {
        val real = entries.filter { it.text.isNotBlank() && it.time >= 0L }.sortedBy { it.time }
        if (real.isNotEmpty()) {
            return real.mapIndexed { index, entry ->
                val next = real.getOrNull(index + 1)
                val endMs = (entry.sungUntilMs ?: next?.time ?: (entry.time + DEFAULT_LINE_MS))
                    .coerceAtLeast(entry.time + 300L)
                val words = entry.words
                    ?.filter { it.text.isNotBlank() }
                    ?.map { WordWindow(it.text, (it.startTime * 1000).toLong(), (it.endTime * 1000).toLong()) }
                    .orEmpty()
                TimedLine(entry.text, entry.time, endMs, words)
            }
        }
        // No timing anywhere on the selection (plain text shared from the
        // overflow menu, say) — space every picked line evenly instead of
        // leaving the clip static.
        val plain = fallbackText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { listOf(fallbackText.trim()) }
        return plain.mapIndexed { index, text ->
            val start = index * DEFAULT_LINE_MS
            TimedLine(text, start, start + DEFAULT_LINE_MS, emptyList())
        }
    }

    // ── Layout (computed once, reused by every frame) ───────────────────

    private fun layoutLines(lines: List<TimedLine>, type: Fonts): List<LineLayout> {
        // Use the still share-card's exact font ladder, tracking, wrapping and
        // row-height plan. The video must not invent a second layout algorithm.
        val availableHeight = (SHARE_BODY_BOTTOM - SHARE_BODY_TOP).coerceAtLeast(0f)
        val plan = fittestPlan(type, lines.map { it.text }, availableHeight)
        var y = 0f
        return lines.mapIndexed { index, line ->
            val rows = plan.rows.filter { it.lineIndex == index }
            val top = y
            rows.forEach { row -> y += row.gapBefore + row.height }
            LineLayout(line, rows, top)
        }
    }

    // ── Frame drawing ────────────────────────────────────────────────────

    /**
     * Draws every selected line using the same measured rows, font size,
     * tracking, wrapping and vertical centring as the still lyrics card.
     * Only the active line's opacity changes as the clip progresses.
     */
    private fun drawLyricStack(
        canvas: Canvas,
        width: Int,
        layouts: List<LineLayout>,
        tMs: Long,
    ) {
        if (layouts.isEmpty()) return

        val availableHeight = (SHARE_BODY_BOTTOM - SHARE_BODY_TOP).coerceAtLeast(0f)
        val firstAscent = layouts.first().rows.firstOrNull()?.let { -it.paint.ascent() } ?: 0f
        val totalHeight = firstAscent + layouts.last().let { last ->
            last.topY + last.rows.sumOf { (it.gapBefore + it.height).toDouble() }.toFloat()
        }
        val bodyTop = SHARE_BODY_TOP + ((availableHeight - totalHeight).coerceAtLeast(0f) / 2f)
        val activeIndex = layouts.indexOfLast { it.line.startMs <= tMs && tMs < it.line.endMs }

        canvas.save()
        layouts.forEachIndexed { index, layout ->
            val elapsed = tMs - layout.line.startMs
            val remaining = layout.line.endMs - tMs
            val fadeInRaw = (elapsed.toFloat() / FADE_MS).coerceIn(0f, 1f)
            val fadeOutRaw = (remaining.toFloat() / FADE_MS).coerceIn(0f, 1f)
            val fadeIn = fadeInRaw * fadeInRaw * (3f - 2f * fadeInRaw)
            val fadeOut = fadeOutRaw * fadeOutRaw * (3f - 2f * fadeOutRaw)
            val highlight = if (index == activeIndex) minOf(fadeIn, fadeOut) else 0f
            val alpha = INACTIVE_ALPHA + (1f - INACTIVE_ALPHA) * highlight

            var y = bodyTop + layout.topY
            layout.rows.forEach { row ->
                y += row.gapBefore
                val paint = Paint(row.paint).apply {
                    this.alpha = (255f * alpha).roundToInt().coerceIn(0, 255)
                }
                val x = CARD_MARGIN
                canvas.drawText(row.text, x, y - paint.ascent(), paint)
                y += row.height
            }
        }
        canvas.restore()
    }

    // ── Audio ────────────────────────────────────────────────────────────

    private data class AudioPlan(
        val extractor: MediaExtractor,
        val trackIndex: Int,
        val format: MediaFormat,
        val startUs: Long,
        val endUs: Long,
    )

    /** An audio track fully encoded to AAC and held in memory (a 45s-capped
     * clip is at most ~1MB as AAC, trivial to buffer) — resolved *before* the
     * video encoder starts, so its final format is already known by the time
     * the muxer needs every track added, whether it came from a straight
     * sample copy or a full decode/re-encode. */
    private data class EncodedAudio(val format: MediaFormat, val samples: List<Pair<ByteArray, MediaCodec.BufferInfo>>)

    private fun openAudioTrack(context: Context, source: AudioSource, clipStartMs: Long, totalMs: Long): AudioPlan? {
        val extractor = MediaExtractor()
        val opened = runCatching {
            when (source) {
                is AudioSource.Remote -> extractor.setDataSource(source.url, null)
                is AudioSource.Local -> extractor.setDataSource(context, source.uri, null)
            }
        }.isSuccess
        if (!opened) {
            extractor.release()
            return null
        }
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                trackIndex = i
                format = f
                break
            }
        }
        if (trackIndex < 0 || format == null) {
            extractor.release()
            return null
        }
        extractor.selectTrack(trackIndex)
        val startUs = clipStartMs * 1000L
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        return AudioPlan(extractor, trackIndex, format, startUs, startUs + totalMs * 1000L)
    }

    /** Fast path: the source is already AAC, so its samples are copied through untouched. */
    private fun bufferAacSamples(plan: AudioPlan): EncodedAudio? {
        val samples = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
        val buffer = ByteBuffer.allocate(256 * 1024)
        while (true) {
            if (plan.extractor.sampleTrackIndex != plan.trackIndex) {
                if (!plan.extractor.advance()) break else continue
            }
            val sampleTimeUs = plan.extractor.sampleTime
            if (sampleTimeUs < 0 || sampleTimeUs > plan.endUs) break
            buffer.clear()
            val size = plan.extractor.readSampleData(buffer, 0)
            if (size < 0) break
            // seekTo(PREVIOUS_SYNC) may land a little before the selected verse.
            // Drop those preroll packets rather than clamping several samples to
            // timestamp zero (which creates malformed AAC track timing).
            if (sampleTimeUs >= plan.startUs) {
                val arr = ByteArray(size)
                buffer.get(arr, 0, size)
                val flags = if (plan.extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else 0
                val info = MediaCodec.BufferInfo().apply {
                    set(0, size, sampleTimeUs - plan.startUs, flags)
                }
                samples += arr to info
            }
            if (!plan.extractor.advance()) break
        }
        return samples.takeIf { it.isNotEmpty() }?.let { EncodedAudio(plan.format, it) }
    }

    /** Slow(er) path: decode the source codec (commonly Opus — this app's own
     * stream picker ranks it above AAC, see `codecRank` in [YTPlayerUtils])
     * to PCM, then re-encode that PCM to AAC, since `MediaMuxer`'s MP4 writer
     * only reliably takes AAC on every API level this app supports. */
    private suspend fun transcodeToAac(plan: AudioPlan): EncodedAudio? {
        val exportContext = currentCoroutineContext()
        val sourceMime = plan.format.getString(MediaFormat.KEY_MIME) ?: return null
        val decoder = runCatching { MediaCodec.createDecoderByType(sourceMime) }.getOrNull() ?: return null
        val sampleRate = runCatching { plan.format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(48_000)
        val channelCount = runCatching { plan.format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(2).coerceIn(1, 2)
        val encoder = runCatching { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC) }.getOrNull()
        if (encoder == null) {
            decoder.release()
            return null
        }

        return try {
            plan.format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            decoder.configure(plan.format, null, null, 0)
            decoder.start()
            val encFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val samples = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
            var outputFormat: MediaFormat? = null
            val decoderInfo = MediaCodec.BufferInfo()
            val encoderInfo = MediaCodec.BufferInfo()
            var extractorDone = false
            var decoderInputDone = false
            var encoderEosQueued = false
            var encoderOutputDone = false
            var lastProgressAt = SystemClock.elapsedRealtime()
            var audioEndUs = 0L

            // Always drain the AAC encoder while feeding it. The previous
            // implementation dropped decoded PCM whenever dequeueInputBuffer
            // briefly returned TRY_AGAIN_LATER; on some devices that meant
            // almost every Opus frame was discarded and the exported MP4 was
            // effectively silent.
            fun drainEncoder(timeoutUs: Long): Boolean {
                var progressed = false
                while (true) {
                    exportContext.ensureActive()
                    val index = encoder.dequeueOutputBuffer(encoderInfo, timeoutUs)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            outputFormat = encoder.outputFormat
                            progressed = true
                        }
                        index >= 0 -> {
                            progressed = true
                            val encoded = encoder.getOutputBuffer(index)
                            val isConfig = encoderInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            val isEos = encoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            if (encoded != null && encoderInfo.size > 0 && !isConfig) {
                                val bytes = ByteArray(encoderInfo.size)
                                encoded.position(encoderInfo.offset)
                                encoded.limit(encoderInfo.offset + encoderInfo.size)
                                encoded.get(bytes)
                                val sampleRateOut = outputFormat?.let {
                                    runCatching { it.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrNull()
                                } ?: sampleRate
                                val framePtsUs = samples.size.toLong() * 1_024_000_000L / sampleRateOut.coerceAtLeast(1)
                                val sampleInfo = MediaCodec.BufferInfo().apply {
                                    set(0, bytes.size, framePtsUs, encoderInfo.flags)
                                }
                                samples += bytes to sampleInfo
                            }
                            encoder.releaseOutputBuffer(index, false)
                            if (isEos) encoderOutputDone = true
                        }
                        else -> return progressed
                    }
                }
            }

            while (!encoderOutputDone) {
                exportContext.ensureActive()
                check(SystemClock.elapsedRealtime() - lastProgressAt < 8_000L) { "Audio codec stopped making progress" }
                var progressed = false

                if (!decoderInputDone) {
                    if (!extractorDone && plan.extractor.sampleTrackIndex != plan.trackIndex) {
                        if (!plan.extractor.advance()) extractorDone = true
                    }
                    var timeUs = if (!extractorDone) plan.extractor.sampleTime else -1L
                    // Seek may land on a packet before the selected verse. Advance
                    // the extractor before acquiring a decoder buffer so no empty
                    // preroll buffers or duplicate zero timestamps reach the codec.
                    val seekDeadline = SystemClock.elapsedRealtime() + 8_000L
                    while (!extractorDone && timeUs >= 0L && timeUs < plan.startUs) {
                        exportContext.ensureActive()
                        check(SystemClock.elapsedRealtime() < seekDeadline) { "Audio source could not seek to the selected passage" }
                        if (!plan.extractor.advance()) {
                            extractorDone = true
                            timeUs = -1L
                        } else {
                            timeUs = plan.extractor.sampleTime
                        }
                    }

                    val inputIndex = decoder.dequeueInputBuffer(5_000)
                    if (inputIndex >= 0) {
                        progressed = true
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                        if (extractorDone || timeUs < 0L || timeUs > plan.endUs) {
                            extractorDone = true
                            decoderInputDone = true
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            val buffer = inputBuffer ?: error("Opus decoder returned no input buffer")
                            buffer.clear()
                            val size = plan.extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                decoderInputDone = true
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            } else {
                                decoder.queueInputBuffer(inputIndex, 0, size, timeUs - plan.startUs, 0)
                                plan.extractor.advance()
                            }
                        }
                    }
                }

                val decoderOutputIndex = decoder.dequeueOutputBuffer(decoderInfo, 5_000)
                if (decoderOutputIndex >= 0) {
                    progressed = true
                    val decoderEos = decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val pcmBuffer = decoder.getOutputBuffer(decoderOutputIndex)
                    val pcmBytes = if (pcmBuffer != null && decoderInfo.size > 0) {
                        ByteArray(decoderInfo.size).also { bytes ->
                            pcmBuffer.position(decoderInfo.offset)
                            pcmBuffer.limit(decoderInfo.offset + decoderInfo.size)
                            pcmBuffer.get(bytes)
                        }
                    } else null
                    val presentationTimeUs = decoderInfo.presentationTimeUs
                    decoder.releaseOutputBuffer(decoderOutputIndex, false)

                    // Keep retrying this PCM chunk until the AAC encoder accepts
                    // it. Drain encoded output between retries to avoid backpressure.
                    if (pcmBytes != null) {
                        val cursor = PcmInputCursor(pcmBytes.size, channelCount, sampleRate, presentationTimeUs)
                        var deadline = SystemClock.elapsedRealtime() + 8_000L
                        while (!cursor.complete && !encoderOutputDone) {
                            exportContext.ensureActive()
                            check(SystemClock.elapsedRealtime() < deadline) { "AAC encoder input stalled" }
                            val encoderInputIndex = encoder.dequeueInputBuffer(1_000)
                            if (encoderInputIndex >= 0) {
                                val input = encoder.getInputBuffer(encoderInputIndex)
                                    ?: error("AAC encoder returned no input buffer")
                                input.clear()
                                val slice = cursor.next(input.remaining()) ?: break
                                input.put(pcmBytes, slice.offset, slice.size)
                                encoder.queueInputBuffer(encoderInputIndex, 0, slice.size, slice.timeUs, 0)
                                audioEndUs = slice.timeUs + slice.size.toLong() / (channelCount * 2) * 1_000_000L / sampleRate
                                deadline = SystemClock.elapsedRealtime() + 8_000L
                                progressed = true
                            }
                            drainEncoder(0)
                        }
                    }

                    if (decoderEos && !encoderEosQueued) {
                        var queued = false
                        val deadline = SystemClock.elapsedRealtime() + 8_000L
                        while (!queued && !encoderOutputDone) {
                            exportContext.ensureActive()
                            check(SystemClock.elapsedRealtime() < deadline) { "AAC encoder did not accept end of stream" }
                            val encoderInputIndex = encoder.dequeueInputBuffer(10_000)
                            if (encoderInputIndex >= 0) {
                                encoder.queueInputBuffer(encoderInputIndex, 0, 0, audioEndUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                queued = true
                                encoderEosQueued = true
                            } else {
                                drainEncoder(0)
                            }
                        }
                    }
                } else if (decoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    progressed = true
                }

                if (drainEncoder(0)) progressed = true
                if (progressed) lastProgressAt = SystemClock.elapsedRealtime()
            }

            if (encoderOutputDone && outputFormat != null && samples.isNotEmpty()) {
                EncodedAudio(outputFormat!!, samples)
            } else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            runCatching { decoder.stop() }
            decoder.release()
            runCatching { encoder.stop() }
            encoder.release()
        }
    }

    // ── Encoding ─────────────────────────────────────────────────────────

    /** Encodes the frame loop, muxes in the audio when one was resolved, and returns whether audio made it in. */
    private suspend fun encode(
        context: Context,
        outFile: File,
        width: Int,
        height: Int,
        totalMs: Long,
        backdrop: Bitmap,
        layouts: List<LineLayout>,
        type: Fonts,
        audioSource: AudioSource?,
        mediaMetadata: MediaMetadata?,
        clipStartMs: Long,
        cover: Bitmap?,
        style: LyricsVideoStyle,
        songTitle: String,
        artistName: String,
        onProgress: (Progress) -> Unit,
    ): Boolean {
        // Resolved up front, before the muxer or the video encoder exist: this
        // is what lets its final (always-AAC) format be added as a track
        // before the video track's own format is even known, in whichever
        // order each actually becomes ready.
        suspend fun encodeAudio(source: AudioSource): EncodedAudio? = withContext(Dispatchers.IO) {
            val plan = openAudioTrack(context, source, clipStartMs, totalMs) ?: return@withContext null
            try {
                if (plan.format.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_AAC) {
                    bufferAacSamples(plan)
                } else {
                    transcodeToAac(plan)
                }
            } finally {
                plan.extractor.release()
            }
        }

        onProgress(Progress(0.03f, context.getString(R.string.lyrics_share_stage_audio)))
        var encodedAudio = audioSource?.let { encodeAudio(it) }
        // If a vendor Opus decoder fails at runtime, retry with the same playback
        // resolver's AAC-compatible stream instead of silently exporting muted video.
        if (encodedAudio == null && audioSource is AudioSource.Remote && mediaMetadata != null) {
            val fallback = withTimeoutOrNull(20_000L) { resolveAudioSource(context, mediaMetadata, forceAac = true) }
            if (fallback != null && fallback != audioSource) encodedAudio = encodeAudio(fallback)
        }

        val exportContext = currentCoroutineContext()
        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        fun drain(endOfStream: Boolean) {
            if (endOfStream) encoder.signalEndOfInputStream()
            var eosWaits = 0
            while (true) {
                exportContext.ensureActive()
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 1_000)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream) return
                        eosWaits++
                        if (eosWaits > 500) error("Video encoder did not finish the MP4 stream")
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                        if (encodedAudio != null) audioTrackIndex = muxer.addTrack(encodedAudio.format)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val encoded = encoder.getOutputBuffer(outIndex)
                        if (encoded != null && bufferInfo.size > 0 && muxerStarted) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, encoded, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        try {
            try {
                val totalFrames = ((totalMs * FPS) / 1000L).toInt().coerceAtLeast(1)
                val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val frameCanvas = Canvas(frameBitmap)
                EglFrameRenderer(inputSurface, width, height).use { frameRenderer ->
                    for (i in 0 until totalFrames) {
                        exportContext.ensureActive()
                        val tMs = (i * 1000L) / FPS
                        frameCanvas.drawBitmap(backdrop, 0f, 0f, null)
                        if (style == LyricsVideoStyle.LYRICS) drawLyricStack(frameCanvas, width, layouts, tMs)
                        else {
                            val active = layouts.lastOrNull { it.line.startMs <= tMs }?.line ?: layouts.first().line
                            drawLyricsVideoScene(frameCanvas, cover, type, style, tMs, active.text, songTitle, artistName)
                        }
                        // Explicit zero-based presentation timestamps prevent the
                        // phone's uptime from leaking into the MP4 track duration.
                        frameRenderer.draw(frameBitmap, tMs * 1_000_000L)
                        drain(false)

                        if (i % 6 == 0) {
                            onProgress(Progress(0.05f + 0.75f * i / totalFrames, context.getString(R.string.lyrics_share_stage_frames)))
                        }
                    }
                }
                frameBitmap.recycle()
                drain(true)
            } finally {
                runCatching { encoder.stop() }
                encoder.release()
                inputSurface.release()
            }

            var hasAudio = false
            if (encodedAudio != null && muxerStarted && audioTrackIndex >= 0) {
                onProgress(Progress(0.85f, "Adding audio"))
                encodedAudio.samples.forEach { (bytes, info) ->
                    muxer.writeSampleData(audioTrackIndex, ByteBuffer.wrap(bytes), info)
                }
                hasAudio = true
            }

            return hasAudio
        } finally {
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }

    /** EGL is used only as a timestamped bridge to MediaCodec's input Surface.
     * Canvas still does all typography/layout work; GL simply uploads that frame
     * and assigns its exact media timestamp, so rendering is not forced to wait
     * for real-world playback time. */
    private class EglFrameRenderer(
        private val inputSurface: Surface,
        private val width: Int,
        private val height: Int,
    ) : Closeable {
        private val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private val context: android.opengl.EGLContext
        private val eglSurface: android.opengl.EGLSurface
        private val program: Int
        private val texture = IntArray(1)
        private var textureInitialised = false
        private val positionBuffer: FloatBuffer = floatBuffer(floatArrayOf(
            -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f
        ))
        // Canvas coordinates start at the top-left; flip the GL texture vertically.
        private val texCoordBuffer: FloatBuffer = floatBuffer(floatArrayOf(
            0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f
        ))

        init {
            check(display != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL display" }
            val versions = IntArray(2)
            check(EGL14.eglInitialize(display, versions, 0, versions, 1)) { "Unable to initialise EGL" }
            val configAttrs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val configCount = IntArray(1)
            check(EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, configCount, 0) && configCount[0] > 0) {
                "No recordable EGL config available"
            }
            val config = configs[0] ?: error("EGL config unavailable")
            context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            check(context != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }
            eglSurface = EGL14.eglCreateWindowSurface(display, config, inputSurface, intArrayOf(EGL14.EGL_NONE), 0)
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL window surface" }
            check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "Unable to make EGL context current" }
            GLES20.glViewport(0, 0, width, height)
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            GLES20.glGenTextures(1, texture, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        fun draw(bitmap: Bitmap, presentationTimeNs: Long) {
            check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "EGL context lost" }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
            if (!textureInitialised) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                textureInitialised = true
            } else {
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
            }
            GLES20.glUseProgram(program)
            val position = GLES20.glGetAttribLocation(program, "aPosition")
            val texCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
            val sampler = GLES20.glGetUniformLocation(program, "uTexture")
            positionBuffer.position(0)
            texCoordBuffer.position(0)
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, positionBuffer)
            GLES20.glEnableVertexAttribArray(texCoord)
            GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
            GLES20.glUniform1i(sampler, 0)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(position)
            GLES20.glDisableVertexAttribArray(texCoord)
            check(EGLExt.eglPresentationTimeANDROID(display, eglSurface, presentationTimeNs)) {
                "Unable to set video presentation timestamp"
            }
            check(EGL14.eglSwapBuffers(display, eglSurface)) { "Unable to submit video frame" }
        }

        override fun close() {
            runCatching {
                EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)
                GLES20.glDeleteTextures(1, texture, 0)
                GLES20.glDeleteProgram(program)
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, eglSurface)
                EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
        }

        private fun floatBuffer(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(values)
                position(0)
            }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            fun compile(type: Int, source: String): Int {
                val shader = GLES20.glCreateShader(type)
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val status = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
                if (status[0] == 0) {
                    val message = GLES20.glGetShaderInfoLog(shader)
                    GLES20.glDeleteShader(shader)
                    error("Shader compilation failed: $message")
                }
                return shader
            }
            val vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val linked = GLES20.glCreateProgram()
            GLES20.glAttachShader(linked, vertex)
            GLES20.glAttachShader(linked, fragment)
            GLES20.glLinkProgram(linked)
            val status = IntArray(1)
            GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            if (status[0] == 0) {
                val message = GLES20.glGetProgramInfoLog(linked)
                GLES20.glDeleteProgram(linked)
                error("Program link failed: $message")
            }
            return linked
        }

        companion object {
            private const val EGL_RECORDABLE_ANDROID = 0x3142
            private const val VERTEX_SHADER = """
                attribute vec4 aPosition;
                attribute vec2 aTexCoord;
                varying vec2 vTexCoord;
                void main() { gl_Position = aPosition; vTexCoord = aTexCoord; }
            """
            private const val FRAGMENT_SHADER = """
                precision mediump float;
                uniform sampler2D uTexture;
                varying vec2 vTexCoord;
                void main() { gl_FragColor = texture2D(uTexture, vTexCoord); }
            """
        }
    }

    // ── Saving ───────────────────────────────────────────────────────────

    private suspend fun saveToGallery(context: Context, file: File): Uri = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SMusic")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create a video record")
            try {
                val exportContext = currentCoroutineContext()
                requireNotNull(resolver.openOutputStream(uri)).use { output ->
                    file.inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            exportContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
                exportContext.ensureActive()
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                file.delete()
                uri
            } catch (failure: Throwable) {
                resolver.delete(uri, null, null)
                throw failure
            }
        } else {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        }
    }
}
