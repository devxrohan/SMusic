package com.devxrohan.smusic.ui.component

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import java.util.concurrent.ConcurrentHashMap

private val releaseImagePatterns = listOf(
    Regex("""!\[[^\]]*]\(\s*(https://[^\s)]+)""", RegexOption.IGNORE_CASE),
    Regex("""<img\b[^>]*\bsrc\s*=\s*["'](https://[^"']+)["']""", RegexOption.IGNORE_CASE),
)
private val warming = ConcurrentHashMap.newKeySet<String>()

internal fun releaseArtworkUrls(body: String): List<String> = releaseImagePatterns
    .flatMap { pattern -> pattern.findAll(body).map { it.range.first to it.groupValues[1].replace("&amp;", "&") }.toList() }
    .sortedBy { it.first }.map { it.second }.distinct().take(2)

/** Warm at most two release images in the same cache used by the notes renderer. */
internal fun preloadReleaseArtwork(context: Context, body: String) {
    if (context.imageLoader.diskCache == null) return
    releaseArtworkUrls(body).forEach { url ->
        if (warming.add(url)) context.imageLoader.enqueue(ImageRequest.Builder(context)
            .data(url).size(1200)
            .listener(onSuccess = { _, _ -> warming.remove(url) }, onError = { _, _ -> warming.remove(url) }, onCancel = { warming.remove(url) })
            .build())
    }
}
