/* GPL-3.0; copyright and upstream attribution are recorded in README.md. */
package com.devxrohan.smusic.utils

import android.graphics.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class LyricsVideoStyle { LYRICS, PHOTO, HALO }

/** Frame art is drawn from clip-relative timestamps, so preview and export share one scene. */
internal fun drawLyricsVideoScene(
    canvas: Canvas, cover: Bitmap?, fonts: Fonts, style: LyricsVideoStyle,
    timeMs: Long, line: String, songTitle: String, artistName: String,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    when (style) {
        LyricsVideoStyle.PHOTO -> {
            // Credits belong on the instant-photo frame, not in a duplicate footer.
            canvas.save()
            canvas.rotate(-2.5f + 1.6f * sin(timeMs / 1000f * .9f), 540f, 680f)
            paint.color = 0xFFFAF8F3.toInt()
            canvas.drawRoundRect(200f, 280f, 880f, 1060f, 12f, 12f, paint)
            drawArtwork(canvas, cover, songTitle, 225f, 305f, 630f)
            val titlePaint = fonts.heading(42f, 0xFF16141A.toInt())
            canvas.drawText(ellipsised(songTitle, titlePaint, 580f), 250f, 986f, titlePaint)
            val artistPaint = fonts.body(32f, 0xFF615C58.toInt())
            canvas.drawText(ellipsised(artistName, artistPaint, 580f), 250f, 1031f, artistPaint)
            canvas.restore()
        }
        LyricsVideoStyle.HALO -> {
            val name = fonts.heading(52f, ShareCardColors.IVORY).apply { textAlign = Paint.Align.CENTER }
            val by = fonts.body(36f, ShareCardColors.MUTED).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText(ellipsised(songTitle, name, 900f), 540f, 245f, name)
            canvas.drawText(ellipsised(artistName, by, 900f), 540f, 300f, by)
            drawHaloScene(canvas, cover, timeMs, paint)
        }
        LyricsVideoStyle.LYRICS -> Unit
    }
    val plan = fittestPlan(fonts, listOf(line), 360f)
    var y = 1130f + ((360f - plan.content) / 2f).coerceAtLeast(0f)
    canvas.save()
    canvas.clipRect(CARD_MARGIN, 1110f, CARD_W - CARD_MARGIN, 1530f)
    for (row in plan.rows) {
        val textPaint = Paint(row.paint).apply {
            color = ShareCardColors.IVORY
            textAlign = Paint.Align.CENTER
        }
        y += row.gapBefore
        canvas.drawText(row.text, 540f, y - textPaint.ascent(), textPaint)
        y += row.height
    }
    canvas.restore()

}

// Upstream haloLevel combines neighbouring waves; this is decorative motion, not an audio meter.
private fun drawHaloScene(canvas: Canvas, cover: Bitmap?, timeMs: Long, paint: Paint) {
    val t = timeMs / 1000f
    val radius = 350f
    val inner = radius * .72f
    val cx = 540f
    val cy = 750f
    paint.strokeWidth = radius * .022f; paint.strokeCap = Paint.Cap.ROUND
    repeat(72) { i ->
        val angle = i / 72f * 2 * PI - PI / 2
        val x = i * .35f
        val level = (.5f + .25f * sin(x + t * 2.1f) + .15f * sin(x * 2.3f - t * 3.7f) + .1f * sin(x * 5.1f + t * 6.3f)).coerceIn(0f, 1f)
        val from = inner + radius * .03f
        val end = from + radius * (.04f + .2f * level)
        val dx = cos(angle).toFloat(); val dy = sin(angle).toFloat()
        paint.color = 0x1FFFFFFF
        canvas.drawLine(cx + dx * from, cy + dy * from, cx + dx * (from + radius * .24f), cy + dy * (from + radius * .24f), paint)
        paint.color = ShareCardColors.ACCENT
        paint.alpha = ((.55f + .45f * level) * 255).toInt()
        canvas.drawLine(cx + dx * from, cy + dy * from, cx + dx * end, cy + dy * end, paint)
    }
    paint.alpha = 255
    val art = 278f * (1f + .012f * sin(t * 3.2f))
    canvas.save()
    canvas.clipPath(Path().apply { addCircle(cx, cy, art, Path.Direction.CW) })
    drawArtwork(canvas, cover, "SMUSIC", cx - art, cy - art, art * 2)
    canvas.restore()
}
