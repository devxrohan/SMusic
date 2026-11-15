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
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
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
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AppFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun render(
        context: Context,
        lyricsText: String,
        songTitle: String,
        artistName: String,
        artworkUrl: String?,
        appFont: AppFont,
        story: Boolean = false,
    ): Bitmap = withContext(Dispatchers.Default) {
        val lines = lyricsText
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("") }

        val type = Fonts(context, appFont)
        val cover = artworkUrl?.let { loadBitmap(context, it) }

        // The passage gets its own height first, and the card is built down
        // from that number rather than the other way round: a short line
        // comes out as a strip just tall enough for it, not a 9:16 sheet
        // with a sentence floating in the middle — unless a full-frame
        // "story" picture was asked for, in which case the passage is
        // centred inside the fixed 9:16 frame instead.
        val headTop = SHARE_BODY_TOP
        val frameRoom = SHARE_BODY_BOTTOM - SHARE_BODY_TOP
        val plan = fittestPlan(type, lines, frameRoom)
        val spare = if (story) (frameRoom - plan.content).coerceAtLeast(0f) else 0f
        val bodyTop = headTop + spare / 2f
        val bodyBottom = bodyTop + plan.content
        val cardH = if (story) MAX_CARD_H else bodyBottom + GAP_BELOW_BODY + FOOTER_THUMB + FOOTER_PAD

        val bitmap = Bitmap.createBitmap(CARD_W, cardH.roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackdrop(canvas, cover, cardH)
        drawBrand(canvas, context, type)

        // The passage itself.
        var y = bodyTop
        for (row in plan.rows) {
            val baseline = y + row.gapBefore - row.paint.ascent()
            canvas.drawText(row.text, CARD_MARGIN, baseline, row.paint)
            y += row.gapBefore + row.height
        }

        // Footer — which song this is.
        val thumbY = cardH - FOOTER_PAD - FOOTER_THUMB
        drawArtwork(canvas, cover, songTitle, CARD_MARGIN, thumbY, FOOTER_THUMB)
        val textX = CARD_MARGIN + FOOTER_THUMB + 34f
        val room = CARD_W - CARD_MARGIN - textX
        val titlePaint = type.body(50f, Color.WHITE, bold = true)
        canvas.drawText(ellipsised(songTitle, titlePaint, room), textX, thumbY + 74f, titlePaint)
        val artistPaint = type.body(42f, 0x99FFFFFF.toInt())
        canvas.drawText(ellipsised(artistName, artistPaint, room), textX, thumbY + 132f, artistPaint)

        bitmap
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

    private fun saveToMediaStore(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SMusic")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Failed to create new MediaStore record")
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return uri
    }

    private fun saveToCache(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val cachePath = File(context.cacheDir, "images").apply { mkdirs() }
        val imageFile = File(cachePath, "$fileName.png")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
    }

    internal suspend fun loadBitmap(context: Context, url: String): Bitmap? = runCatching {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            // Palette needs pixel access, which a hardware bitmap can't give.
            .allowHardware(false)
            .build()
        loader.execute(request).drawable?.toBitmap()
    }.getOrNull()
}

// ── Backdrop ─────────────────────────────────────────────────────────────

/**
 * The card's backdrop: a dim base in the cover's own colour, four soft glows
 * lifted straight off the artwork's palette, and a dark gradient laid over
 * the top so white text stays readable no matter where it lands.
 */
internal fun drawBackdrop(canvas: Canvas, lead: Bitmap?, height: Float) {
    val colors = paletteOf(lead)
    canvas.drawColor(dimmed(colors.first()))

    val anchors = listOf(
        0.20f to 0.16f,
        0.84f to 0.22f,
        0.76f to 0.66f,
        0.18f to 0.78f,
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    colors.forEachIndexed { index, color ->
        val (fx, fy) = anchors[index]
        val cx = CARD_W * fx
        val cy = height * fy
        val radius = CARD_W * 0.95f
        paint.shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(
                ColorUtils.setAlphaComponent(color, 210),
                ColorUtils.setAlphaComponent(color, 0),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, paint)
    }
    paint.shader = null

    paint.shader = LinearGradient(
        0f,
        0f,
        0f,
        height,
        intArrayOf(0x8C000000.toInt(), 0x59000000, 0xCC000000.toInt()),
        floatArrayOf(0f, 0.42f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, CARD_W.toFloat(), height, paint)
    paint.shader = null
}

/** Four colours off the sleeve, saturated and held to a mid lightness so any artwork yields a rich backdrop. */
private fun paletteOf(bitmap: Bitmap?): List<Int> {
    val fallback = listOf(0xFF3A1C71.toInt(), 0xFFD76D77.toInt(), 0xFF2B5876.toInt(), 0xFFFFAF7B.toInt())
    val source = bitmap ?: return fallback
    val swatches = runCatching {
        Palette.from(source).maximumColorCount(24).generate().swatches
            .sortedByDescending { it.population }
            .map { it.rgb }
    }.getOrNull().orEmpty()
    if (swatches.isEmpty()) return fallback
    return (swatches + fallback).take(4).map(::tuned)
}

private fun tuned(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.58f)
    return ColorUtils.HSLToColor(hsl)
}

private fun dimmed(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[2] = 0.10f
    return ColorUtils.HSLToColor(hsl)
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
internal fun drawBrand(canvas: Canvas, context: Context, type: Fonts) {
    val white = type.heading(46f, 0xF2FFFFFF.toInt())
    val red = type.heading(46f, BRAND_RED)
    listOf(white, red).forEach { it.setShadowLayer(10f, 0f, 3f, 0x80000000.toInt()) }

    val musicWidth = white.measureText("Music")
    val sWidth = red.measureText("S")
    val textRight = CARD_W - CARD_MARGIN

    var x = textRight - musicWidth
    canvas.drawText("Music", x, HEADER_BASELINE, white)
    x -= sWidth
    canvas.drawText("S", x, HEADER_BASELINE, red)

    val logo = runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.smusic_logo) }.getOrNull()
    if (logo != null) {
        val logoLeft = x - LOGO_GAP - LOGO_SIZE
        val logoTop = HEADER_BASELINE - LOGO_SIZE * 0.80f
        val dst = RectF(logoLeft, logoTop, logoLeft + LOGO_SIZE, logoTop + LOGO_SIZE)
        canvas.drawBitmap(logo, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
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

private const val LOGO_SIZE = 50f
private const val LOGO_GAP = 16f

internal const val HEADER_BASELINE = 132f
internal const val HEADER_BOTTOM = 170f

private const val GAP_ABOVE_BODY = 20f
private const val GAP_BELOW_BODY = 20f
internal const val SHARE_BODY_TOP = HEADER_BOTTOM + 20f

internal const val FOOTER_THUMB = 160f
internal const val FOOTER_PAD = 96f
internal const val SHARE_BODY_BOTTOM = MAX_CARD_H - FOOTER_THUMB - FOOTER_PAD - 20f

/** Sizes tried, largest first; the last rung is a floor, not a last resort — the frame grows past it instead of the type shrinking. */
private val FIT_LADDER = floatArrayOf(96f, 88f, 80f, 72f, 64f, 56f, 50f, 44f, 38f, 32f)

internal const val TRACKING = -0.7f / 34f
private const val LEAD = 41f / 34f
