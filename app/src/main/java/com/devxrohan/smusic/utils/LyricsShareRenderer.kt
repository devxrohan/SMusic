/*
 * SMusic Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.devxrohan.smusic.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AppFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Draws the picked lyric lines as one shareable picture.
 *
 * The card is a plain [Canvas] drawing rather than a captured Composable: a
 * screenshot of the lyrics screen would carry the scrubber, whatever lines
 * happened to be on screen and that device's own status bar, so two people
 * sharing the same verse would end up with two different-looking pictures.
 * Drawing it by hand means the card is always just the verse, the sleeve and
 * the song credit, sized to exactly how much room the picked lines need
 * instead of a fixed box with empty space in it.
 */
object LyricsShareRenderer {
    const val MAX_SELECTED_LINES = 10


    suspend fun render(
        context: Context,
        lyricsText: String,
        songTitle: String,
        artistName: String,
        artworkUrl: String?,
        appFont: AppFont,
        story: Boolean = false,
        style: LyricsCardStyle = LyricsCardStyle.CLASSIC,
    ): Bitmap = withContext(Dispatchers.Default) {
        val lines = lyricsText
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("") }

        val type = Fonts(context, appFont)
        val cover = artworkUrl?.let { loadBitmap(context, it) }

        renderStyledLyricsCard(context, lines, songTitle, artistName, cover, type, style, story)
    }

    /** Writes [bitmap] to the gallery (API 29+) or the app cache, and returns a shareable [Uri]. */
    suspend fun saveToFile(context: Context, bitmap: Bitmap, fileName: String): Uri =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(context, bitmap, fileName)
            } else {
                saveToCache(context, bitmap, fileName)
            }
        }

    private suspend fun saveToMediaStore(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SMusic")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create an image record")
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            currentCoroutineContext().ensureActive()
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        }
    }

    private fun saveToCache(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val cachePath = File(context.cacheDir, "images").apply { mkdirs() }
        val imageFile = File(cachePath, "$fileName.png")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
    }

    internal suspend fun loadBitmap(context: Context, url: String): Bitmap? = try {
        withTimeoutOrNull(4_000L) {
            val request = ImageRequest.Builder(context.applicationContext)
                .data(url)
                .size(720, 720)
                .allowHardware(false)
                .build()
            context.applicationContext.imageLoader.execute(request).drawable?.toBitmap()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

// ── Fitting the passage ─────────────────────────────────────────────────

internal data class ShareLyricRow(
    val text: String,
    val paint: Paint,
    val height: Float,
    val gapBefore: Float,
    val lineIndex: Int,
)

internal class ShareLyricPlan(val rows: List<ShareLyricRow>, val ascent: Float) {
    val content: Float
        get() = ascent + rows.fold(0f) { total, row -> total + row.gapBefore + row.height }
}

/** The largest size on [FIT_LADDER] whose rows still fit in [room], or the bottom rung when none do. */
internal fun fittestPlan(type: Fonts, lines: List<String>, room: Float): ShareLyricPlan {
    for (size in FIT_LADDER) {
        val plan = planAt(type, lines, size)
        if (plan.content <= room) return plan
    }
    return planAt(type, lines, FIT_LADDER.last())
}

/** Keep complete leading lines at a readable minimum; overflowing trailing lines are excluded. */
internal fun fittingCardPlan(type: Fonts, lines: List<String>, room: Float): ShareLyricPlan {
    val candidates = lines.take(LyricsShareRenderer.MAX_SELECTED_LINES)
    for (count in candidates.size downTo 1) {
        val visible = candidates.take(count)
        if (planAt(type, visible, 32f).content <= room) return fittestPlan(type, visible, room)
    }
    return ShareLyricPlan(emptyList(), 0f)
}

private fun planAt(type: Fonts, lines: List<String>, size: Float): ShareLyricPlan {
    val primary = type.heading(size, Color.WHITE).apply { letterSpacing = TRACKING }
    val rows = mutableListOf<ShareLyricRow>()
    lines.forEachIndexed { index, line ->
        var firstOfLine = true
        wrap(line, primary, CARD_CONTENT_W).forEach { text ->
            val gap = when {
                rows.isEmpty() -> 0f
                firstOfLine && index > 0 -> size * 0.55f
                else -> 0f
            }
            rows += ShareLyricRow(text, primary, size * LEAD, gap, index)
            firstOfLine = false
        }
    }
    return ShareLyricPlan(rows, -primary.ascent())
}

/** Greedy wrap at [width], breaking on the character that fits and only then walking back to a space. */
internal fun wrap(text: String, paint: Paint, width: Float): List<String> {
    val source = text.trim()
    if (source.isEmpty()) return listOf("")

    val out = mutableListOf<String>()
    var rest = source
    while (rest.isNotEmpty()) {
        val fit = paint.breakText(rest, true, width, null)
        if (fit <= 0) {
            out += rest
            break
        }
        val space = rest.lastIndexOf(' ', fit - 1)
        val take = if (space > 0 && fit < rest.length) space else fit
        if (take <= 0) {
            out += rest
            break
        }
        out += rest.substring(0, take).trimEnd()
        rest = rest.substring(take).trimStart()
    }
    return out.filter { it.isNotEmpty() }.ifEmpty { listOf("") }
}

// ── Pieces ───────────────────────────────────────────────────────────────

/**
 * The mark, top right: the app icon beside its name, with the "S" picked
 * out in the brand red and the rest in white. Both carry a soft dark
 * shadow so the mark stays legible however light or busy the backdrop's
 * gradient turns out at that corner.
 */
internal fun drawBrand(canvas: Canvas, context: Context, type: Fonts, onLight: Boolean = false) {
    val white = type.heading(62f, if (onLight) 0xFF24201D.toInt() else 0xF2FFFFFF.toInt())
    val red = type.heading(62f, BRAND_RED)
    if (!onLight) listOf(white, red).forEach { it.setShadowLayer(10f, 0f, 3f, 0x80000000.toInt()) }

    val musicWidth = white.measureText("MUSIC")
    val sWidth = red.measureText("S")
    val textRight = CARD_W - CARD_MARGIN

    var x = textRight - musicWidth
    canvas.drawText("MUSIC", x, HEADER_BASELINE, white)
    x -= sWidth
    canvas.drawText("S", x, HEADER_BASELINE, red)

    drawShareBrandIcon(canvas, context, x - LOGO_GAP - LOGO_SIZE,
        HEADER_BASELINE - LOGO_SIZE * 0.80f, LOGO_SIZE)
}

/** Uses the original app artwork; transparent asset padding must not shrink the exported mark. */
internal fun drawShareBrandIcon(canvas: Canvas, context: Context, left: Float, top: Float, size: Float) {
    val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.smusic_logo,
        BitmapFactory.Options().apply { inScaled = false }) ?: return
    try {
        var minX = bitmap.width
        var minY = bitmap.height
        var maxX = -1
        var maxY = -1
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) if (Color.alpha(row[x]) > 8) {
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
        if (maxX < minX || maxY < minY) return
        val source = Rect(minX, minY, maxX + 1, maxY + 1)
        val scale = size / maxOf(source.width(), source.height())
        val width = source.width() * scale
        val height = source.height() * scale
        val x = left + (size - width) / 2f
        val y = top + (size - height) / 2f
        canvas.drawBitmap(bitmap, source, RectF(x, y, x + width, y + height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    } finally {
        bitmap.recycle()
    }
}

/** A cover, cropped square and rounded — or a lettered stand-in tile where one never loaded. */
internal fun drawArtwork(canvas: Canvas, bitmap: Bitmap?, fallback: String, x: Float, y: Float, size: Float) {
    val bounds = RectF(x, y, x + size, y + size)
    val radius = size * 0.10f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (bitmap != null) {
        val scale = size / minOf(bitmap.width, bitmap.height).toFloat()
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                x - (bitmap.width * scale - size) / 2f,
                y - (bitmap.height * scale - size) / 2f,
            )
        }
        paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            .apply { setLocalMatrix(matrix) }
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = null
    } else {
        val hue = (fallback.hashCode().toFloat() % 360f + 360f) % 360f
        paint.color = ColorUtils.HSLToColor(floatArrayOf(hue, 0.55f, 0.45f))
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }
}

/** Trims [text] to [width], with an ellipsis, the way a single-line row would. */
internal fun ellipsised(text: String, paint: Paint, width: Float): String {
    if (paint.measureText(text) <= width) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.take(end) + "…") > width) end--
    return text.take(end).trimEnd() + "…"
}

/**
 * The face drawn onto the card — whichever font the person has set for the
 * app, resolved to an Android [Typeface] rather than a Compose [androidx.compose.ui.text.font.FontFamily],
 * since this is a plain [Canvas] drawing. The card's lettering changes with
 * the app's own font setting instead of always being set in one fixed face.
 */
internal class Fonts(context: Context, appFont: AppFont) {
    private val display = appFont.getTypeface(context, bold = true)
    private val regular = appFont.getTypeface(context, bold = false)

    fun heading(size: Float, color: Int) = paint(display, size, color)

    fun body(size: Float, color: Int, bold: Boolean = false) =
        paint(if (bold) display else regular, size, color)

    private fun paint(face: Typeface, size: Float, color: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color
        }
}

// ── Card geometry ────────────────────────────────────────────────────────

internal const val CARD_W = 1080

/** 9:16 is a ceiling on the fitted card's height, and the fixed height of a "story" card. */
internal const val MAX_CARD_H = 1920f

internal const val CARD_MARGIN = 72f
internal const val CARD_CONTENT_W = CARD_W - CARD_MARGIN * 2

/** A clean, legible red for the "S" in the mark — distinct from the artwork-driven backdrop behind it. */
internal const val BRAND_RED = 0xFFFF3B30.toInt()

private const val LOGO_SIZE = 62f
private const val LOGO_GAP = 16f

internal const val HEADER_BASELINE = 132f
internal const val HEADER_BOTTOM = 170f

internal const val SHARE_BODY_TOP = HEADER_BOTTOM + 20f

internal const val FOOTER_THUMB = 160f
internal const val FOOTER_PAD = 96f
internal const val SHARE_BODY_BOTTOM = MAX_CARD_H - FOOTER_THUMB - FOOTER_PAD - 20f

/** Sizes tried, largest first; the last rung is a floor, not a last resort — the frame grows past it instead of the type shrinking. */
private val FIT_LADDER = floatArrayOf(72f, 64f, 56f, 50f, 44f, 38f, 32f, 28f, 24f)

internal const val TRACKING = -0.7f / 34f
private const val LEAD = 41f / 34f
