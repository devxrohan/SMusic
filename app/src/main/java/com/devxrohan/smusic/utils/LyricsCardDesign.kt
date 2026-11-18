package com.devxrohan.smusic.utils

import android.content.Context
import android.graphics.*
import kotlin.math.roundToInt
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

enum class LyricsCardStyle { CLASSIC, PAPER, DARK }

/** Export palettes use explicit foreground/background pairs, independent of the cover brightness. */
internal object ShareCardColors {
    const val INK = 0xFF171719.toInt()
    const val IVORY = 0xFFF5F1EB.toInt()
    const val ACCENT = 0xFFEF5B55.toInt()
    const val MUTED = 0xFFBDB8B3.toInt()
}

internal fun renderStyledLyricsCard(
    context: Context, lines: List<String>, title: String, artist: String,
    cover: Bitmap?, fonts: Fonts, style: LyricsCardStyle, story: Boolean,
): Bitmap {
    if (style == LyricsCardStyle.CLASSIC) return renderClassicCard(context, lines, title, artist, cover, fonts, story)
    val height = if (story) MAX_CARD_H else CARD_W.toFloat()
    val bitmap = Bitmap.createBitmap(CARD_W, height.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val dark = style == LyricsCardStyle.DARK
    val foreground = if (dark) ShareCardColors.IVORY else ShareCardColors.INK
    val secondary = if (dark) ShareCardColors.MUTED else 0xFF615C58.toInt()
    canvas.drawColor(if (dark) ShareCardColors.INK else ShareCardColors.IVORY)
    drawBrand(canvas, context, fonts, onLight = !dark)
    val top = 220f
    val bottom = height - 300f
    val plan = fittingCardPlan(fonts, lines, bottom - top)
    var y = top + ((bottom - top - plan.content) / 2f).coerceAtLeast(0f)
    canvas.save()
    canvas.clipRect(CARD_MARGIN, top, CARD_W - CARD_MARGIN, bottom)
    for (row in plan.rows) {
        val paint = Paint(row.paint).apply { color = foreground }
        y += row.gapBefore
        canvas.drawText(row.text, CARD_MARGIN, y - paint.ascent(), paint)
        y += row.height
    }
    canvas.restore()
    drawCardCredits(canvas, cover, title, artist, fonts, height, foreground, secondary)
    return bitmap
}

private fun renderClassicCard(context: Context, lines: List<String>, songTitle: String, artistName: String, cover: Bitmap?, type: Fonts, story: Boolean): Bitmap {
        val headTop = 220f
        val cardH = if (story) MAX_CARD_H else CARD_W.toFloat()
        val frameRoom = cardH - 300f - headTop
        val plan = fittingCardPlan(type, lines, frameRoom)
        val spare = (frameRoom - plan.content).coerceAtLeast(0f)
        val bodyTop = headTop + spare / 2f

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

        drawCardCredits(canvas, cover, songTitle, artistName, type, cardH, ShareCardColors.IVORY, ShareCardColors.MUTED)

        return bitmap
}

private fun drawCardCredits(canvas: Canvas, cover: Bitmap?, title: String, artist: String,
    fonts: Fonts, height: Float, foreground: Int, secondary: Int) {
    val top = height - FOOTER_PAD - FOOTER_THUMB
    drawArtwork(canvas, cover, title, CARD_MARGIN, top, FOOTER_THUMB)
    val x = CARD_MARGIN + FOOTER_THUMB + 34f
    val width = CARD_W - CARD_MARGIN - x
    val name = fonts.body(50f, foreground, bold = true)
    canvas.drawText(ellipsised(title, name, width), x, top + 74f, name)
    val by = fonts.body(42f, secondary)
    canvas.drawText(ellipsised(artist, by, width), x, top + 132f, by)
}

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
