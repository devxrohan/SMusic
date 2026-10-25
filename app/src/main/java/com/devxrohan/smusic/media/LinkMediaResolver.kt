package com.devxrohan.smusic.media

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.devxrohan.smusic.constants.AudioQuality
import com.devxrohan.smusic.utils.GlobalLog
import com.devxrohan.smusic.utils.YTPlayerUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class LinkMediaResolver(
    private val context: Context,
    private val tempManager: TemporaryMediaManager = TemporaryMediaManager(context),
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "LinkMediaResolver"
        private const val MAX_CHUNK_BYTES = 12 * 1024 * 1024L // 12MB to capture full 15-30s high-res videos

        private val YT_REGEX = Regex("""(?:youtu\.be/|(?:www\.|m\.|music\.)?youtube\.com/(?:embed/|v/|watch\?v=|watch\?.+&v=|shorts/|live/))([\w-]{11})""")
        private val IG_SHORTCODE_REGEX = Regex("""(?:instagram\.com|instagr\.am)/(?:reel|reels|p|share/reel|share/p|tv)/([A-Za-z0-9_-]+)""")
        private val SNAPCHAT_REGEX = Regex("""snapchat\.com/(?:spotlight|add|p|t)/([A-Za-z0-9_-]+)""")
        private val TIKTOK_REGEX = Regex("""(?:tiktok\.com/|vm\.tiktok\.com/|vt\.tiktok\.com/)""")
    }

    var lastSuggestedTitle: String? = null
        private set
    var lastSuggestedArtist: String? = null
        private set

    suspend fun resolveMedia(url: String): File? = withContext(Dispatchers.IO) {
        lastSuggestedTitle = null
        lastSuggestedArtist = null
        val cleanUrl = url.trim()
        Log.d(TAG, "Attempting to resolve media from URL: $cleanUrl")
        GlobalLog.append(Log.INFO, TAG, "Attempting to resolve media from URL: $cleanUrl")

        // 1. YouTube & YouTube Shorts
        val ytVideoId = extractYouTubeVideoId(cleanUrl)
        if (ytVideoId != null) {
            Log.d(TAG, "Identified YouTube video ID: $ytVideoId")
            GlobalLog.append(Log.INFO, TAG, "Identified YouTube video ID: $ytVideoId")
            resolveYouTubeAudio(ytVideoId)?.let { return@withContext it }
        }

        // 2. Instagram Reels & Posts
        if (cleanUrl.contains("instagram.com") || cleanUrl.contains("instagr.am")) {
            Log.d(TAG, "Identified Instagram URL: $cleanUrl")
            GlobalLog.append(Log.INFO, TAG, "Identified Instagram URL: $cleanUrl")
            resolveInstagramMedia(cleanUrl)?.let { return@withContext it }
        }

        // 3. Snapchat Spotlight & Public Stories
        if (cleanUrl.contains("snapchat.com")) {
            Log.d(TAG, "Identified Snapchat URL: $cleanUrl")
            GlobalLog.append(Log.INFO, TAG, "Identified Snapchat URL: $cleanUrl")
            resolveSnapchatMedia(cleanUrl)?.let { return@withContext it }
        }

        // 4. TikTok Videos
        if (cleanUrl.contains("tiktok.com")) {
            Log.d(TAG, "Identified TikTok URL: $cleanUrl")
            GlobalLog.append(Log.INFO, TAG, "Identified TikTok URL: $cleanUrl")
            resolveTikTokMedia(cleanUrl)?.let { return@withContext it }
        }

        // 5. Direct media link fallback (e.g. .mp4, .mp3, .m4a hosted directly)
        if (isDirectMediaUrl(cleanUrl)) {
            Log.d(TAG, "Identified Direct Media URL: $cleanUrl")
            GlobalLog.append(Log.INFO, TAG, "Identified Direct Media URL: $cleanUrl")
            val ext = cleanUrl.substringAfterLast('.', "mp4").substringBefore('?')
            val targetFile = tempManager.createTempFile(ext)
            if (downloadMediaChunk(cleanUrl, targetFile)) {
                return@withContext targetFile
            }
            tempManager.cleanup(targetFile)
        }

        // 6. Generic web page with OpenGraph / HTML5 media tags
        resolveGenericWebMedia(cleanUrl)?.let { return@withContext it }

        Log.w(TAG, "Could not resolve audio or video stream from link: $cleanUrl")
        GlobalLog.append(Log.WARN, TAG, "Could not resolve audio or video stream from link: $cleanUrl")
        null
    }

    private fun extractYouTubeVideoId(url: String): String? {
        val match = YT_REGEX.find(url)
        return match?.groupValues?.getOrNull(1)
    }

    private suspend fun resolveYouTubeAudio(videoId: String): File? {
        GlobalLog.append(Log.INFO, TAG, "Starting YouTube resolution for videoId: $videoId")
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            var streamUrl: String? = null
            var ext = "m4a"

            // Primary: Try YTPlayerUtils with AUTO quality
            if (cm != null) {
                try {
                    val playbackData = YTPlayerUtils.playerResponseForPlayback(
                        videoId = videoId,
                        audioQuality = AudioQuality.AUTO,
                        connectivityManager = cm
                    ).getOrNull()

                    if (playbackData != null && playbackData.streamUrl.isNotBlank()) {
                        streamUrl = playbackData.streamUrl
                        val mime = playbackData.format.mimeType
                        ext = if (mime.contains("webm") || mime.contains("opus")) "webm" else "m4a"
                        val details = playbackData.videoDetails
                        if (details != null && !details.title.isNullOrBlank()) {
                            lastSuggestedTitle = details.title
                            lastSuggestedArtist = details.author
                        }
                        GlobalLog.append(Log.INFO, TAG, "YouTube stream resolved: $ext audio")
                    }
                } catch (e: Exception) {
                    GlobalLog.append(Log.WARN, TAG, "YTPlayerUtils failed: ${e.message}")
                }
            }

            // Fallback: Direct Innertube player with ANDROID_VR_NO_AUTH or IOS
            if (streamUrl.isNullOrBlank()) {
                GlobalLog.append(Log.INFO, TAG, "Trying fallback Innertube player for videoId: $videoId")
                val fallbackClients = listOf(
                    com.devxrohan.smusic.innertube.models.YouTubeClient.ANDROID_VR_NO_AUTH,
                    com.devxrohan.smusic.innertube.models.YouTubeClient.IOS,
                    com.devxrohan.smusic.innertube.models.YouTubeClient.WEB_REMIX
                )
                for (client in fallbackClients) {
                    try {
                        val resp = com.devxrohan.smusic.innertube.YouTube.player(videoId, null, client = client).getOrNull()
                        val audioFormat = resp?.streamingData?.adaptiveFormats?.firstOrNull { it.isAudio }
                        val formatUrl = audioFormat?.url
                        if (!formatUrl.isNullOrBlank()) {
                            streamUrl = formatUrl
                            val mime = audioFormat?.mimeType.orEmpty()
                            ext = if (mime.contains("webm") || mime.contains("opus")) "webm" else "m4a"
                            val details = resp?.videoDetails
                            if (details != null && !details.title.isNullOrBlank()) {
                                lastSuggestedTitle = details.title
                                lastSuggestedArtist = details.author
                            }
                            GlobalLog.append(Log.INFO, TAG, "YouTube stream resolved via fallback client: $ext audio")
                            break
                        }
                    } catch (e: Exception) {
                        GlobalLog.append(Log.WARN, TAG, "Fallback client ${client.clientName} failed: ${e.message}")
                    }
                }
            }

            if (streamUrl.isNullOrBlank()) {
                GlobalLog.append(Log.ERROR, TAG, "All YouTube stream resolution strategies failed for videoId: $videoId")
                return null
            }

            val targetFile = tempManager.createTempFile(ext)
            GlobalLog.append(Log.INFO, TAG, "Downloading YouTube audio chunk...")
            if (downloadMediaChunk(streamUrl, targetFile, 12 * 1024 * 1024L)) {
                GlobalLog.append(Log.INFO, TAG, "YouTube audio download succeeded (${targetFile.length()} bytes)")
                targetFile
            } else {
                GlobalLog.append(Log.ERROR, TAG, "YouTube audio download failed for videoId: $videoId")
                tempManager.cleanup(targetFile)
                null
            }
        } catch (e: Exception) {
            GlobalLog.append(Log.ERROR, TAG, "Exception in resolveYouTubeAudio for videoId $videoId: ${e.message}")
            Log.e(TAG, "Failed to resolve YouTube audio for videoId: $videoId", e)
            null
        }
    }

    private suspend fun resolveInstagramMedia(url: String): File? {
        val cleanUrl = url.trim()
        GlobalLog.append(Log.INFO, TAG, "Starting Instagram resolution for: $cleanUrl")
        var shortcode = IG_SHORTCODE_REGEX.find(cleanUrl)?.groupValues?.getOrNull(1)

        if (shortcode.isNullOrBlank()) {
            val canonicalUrl = resolveCanonicalUrl(cleanUrl)
            shortcode = IG_SHORTCODE_REGEX.find(canonicalUrl)?.groupValues?.getOrNull(1)
        }
        GlobalLog.append(Log.INFO, TAG, "Instagram shortcode: $shortcode")

        // Strategy 1 (Primary): In-App Fast WebView resolution
        try {
            val mediaUrl = resolveInstagramViaWebView(cleanUrl, shortcode ?: "")
            if (!mediaUrl.isNullOrBlank()) {
                GlobalLog.append(Log.INFO, TAG, "Instagram WebView resolved media URL: $mediaUrl")
                val targetFile = tempManager.createTempFile("mp4")
                if (downloadMediaChunk(mediaUrl, targetFile)) {
                    GlobalLog.append(Log.INFO, TAG, "Downloaded media from WebView (${targetFile.length()} bytes)")
                    return targetFile
                }
                tempManager.cleanup(targetFile)
            }
        } catch (e: Exception) {
            GlobalLog.append(Log.WARN, TAG, "Instagram WebView resolution error: ${e.message}")
        }

        // Strategy 2: Direct HTTP check for Instagram embed captioned page & JSON endpoint
        if (!shortcode.isNullOrBlank()) {
            try {
                val apiUrls = listOf(
                    "https://www.instagram.com/reel/$shortcode/embed/captioned/",
                    "https://www.instagram.com/p/$shortcode/embed/captioned/",
                    "https://www.instagram.com/reel/$shortcode/?__a=1&__d=1",
                    "https://www.instagram.com/p/$shortcode/?__a=1&__d=1"
                )
                for (apiEndpoint in apiUrls) {
                    GlobalLog.append(Log.INFO, TAG, "Checking Instagram endpoint: $apiEndpoint")
                    val request = Request.Builder()
                        .url(apiEndpoint)
                        .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                        .addHeader("X-IG-App-ID", "936619743392459")
                        .addHeader("Accept", "*/*")
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val html = response.body?.string().orEmpty()
                            val mediaUrl = extractMediaUrlFromHtml(html)
                            if (!mediaUrl.isNullOrBlank()) {
                                GlobalLog.append(Log.INFO, TAG, "Found media URL from $apiEndpoint: $mediaUrl")
                                val targetFile = tempManager.createTempFile("mp4")
                                if (downloadMediaChunk(mediaUrl, targetFile)) {
                                    return targetFile
                                }
                                tempManager.cleanup(targetFile)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                GlobalLog.append(Log.WARN, TAG, "Instagram HTTP API check failed: ${e.message}")
            }
        }

        // Strategy 3: Bot User-Agent OpenGraph scrape
        try {
            GlobalLog.append(Log.INFO, TAG, "Trying bot OpenGraph scrape for $cleanUrl")
            val request = Request.Builder()
                .url(cleanUrl)
                .addHeader("User-Agent", "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()
                    val mediaUrl = extractMediaUrlFromHtml(html)
                    if (!mediaUrl.isNullOrBlank()) {
                        GlobalLog.append(Log.INFO, TAG, "Found media URL via OpenGraph: $mediaUrl")
                        val targetFile = tempManager.createTempFile("mp4")
                        if (downloadMediaChunk(mediaUrl, targetFile)) {
                            return targetFile
                        }
                        tempManager.cleanup(targetFile)
                    }
                }
            }
        } catch (e: Exception) {
            GlobalLog.append(Log.WARN, TAG, "Instagram OpenGraph resolution failed: ${e.message}")
        }

        GlobalLog.append(Log.WARN, TAG, "Instagram resolution failed for: $cleanUrl")
        return null
    }

    private suspend fun resolveInstagramViaWebView(originalUrl: String, shortcode: String): String? = withContext(Dispatchers.Main) {
        val deferredMediaUrl = CompletableDeferred<String?>()
        var webView: WebView? = null
        try {
            GlobalLog.append(Log.INFO, TAG, "Creating fast in-memory WebView for Instagram shortcode: $shortcode")
            webView = WebView(context.applicationContext)
            webView.layout(0, 0, 1080, 1920)
            webView.onResume()
            webView.resumeTimers()

            val settings = webView.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                try {
                    setAcceptThirdPartyCookies(webView, true)
                } catch (_: Exception) {}
            }

            val bridge = object {
                @android.webkit.JavascriptInterface
                fun onMediaFound(url: String?) {
                    val clean = url?.trim('"', ' ', '\'', '\\')
                    if (!clean.isNullOrBlank() && clean.startsWith("http") && !clean.startsWith("blob:") && isInstagramMediaUrl(clean)) {
                        GlobalLog.append(Log.INFO, TAG, "SMusicBridge received Instagram media URL: $clean")
                        if (!deferredMediaUrl.isCompleted) {
                            deferredMediaUrl.complete(clean)
                        }
                    }
                }
            }
            webView.addJavascriptInterface(bridge, "SMusicBridge")

            val injectionScript = """
                (function() {
                    function report(url) {
                        if (!url || typeof url !== 'string') return;
                        if ((url.includes('cdninstagram.com') || url.includes('fbcdn.net')) &&
                            (url.includes('.mp4') || url.includes('/v/t50.') || url.includes('/v/t0.') || url.includes('mime_type=video'))) {
                            if (window.SMusicBridge) {
                                window.SMusicBridge.onMediaFound(url);
                            }
                        }
                    }

                    if (window.fetch) {
                        var origFetch = window.fetch;
                        window.fetch = function() {
                            var arg = arguments[0];
                            if (typeof arg === 'string') report(arg);
                            else if (arg && arg.url) report(arg.url);
                            return origFetch.apply(this, arguments);
                        };
                    }

                    if (window.XMLHttpRequest) {
                        var origOpen = XMLHttpRequest.prototype.open;
                        XMLHttpRequest.prototype.open = function(method, url) {
                            report(url);
                            return origOpen.apply(this, arguments);
                        };
                    }

                    function scan() {
                        var btns = document.querySelectorAll('button, div[role="button"], .PlayButton, .EmbeddedMediaImage, [aria-label*="play" i], [aria-label*="Play" i]');
                        for (var i = 0; i < btns.length; i++) {
                            try { btns[i].click(); } catch(e){}
                        }
                        var vids = document.querySelectorAll('video');
                        for (var i = 0; i < vids.length; i++) {
                            var v = vids[i];
                            try { v.muted = true; v.play(); } catch(e){}
                            report(v.currentSrc || v.src);
                        }
                        var srcs = document.querySelectorAll('video source');
                        for (var i = 0; i < srcs.length; i++) {
                            report(srcs[i].src);
                        }
                        var scripts = document.querySelectorAll('script');
                        for (var i = 0; i < scripts.length; i++) {
                            var txt = (scripts[i].textContent || '').replace(/\\\//g, '/').replace(/\\u0026/g, '&');
                            var m = txt.match(/https:\/\/[^"'\\s]+?(?:cdninstagram\.com|fbcdn\.net)[^"'\\s]*?\.mp4[^"'\\s]*/);
                            if (m && m[0]) report(m[0]);
                            var m2 = txt.match(/"video_url"\s*:\s*"([^"]+)"/);
                            if (m2 && m2[1]) report(m2[1]);
                        }
                    }
                    scan();
                    setInterval(scan, 400);
                })();
            """.trimIndent()

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val reqUrl = request?.url?.toString()
                    if (reqUrl != null && isInstagramMediaUrl(reqUrl)) {
                        GlobalLog.append(Log.INFO, TAG, "WebView intercepted Instagram media URL: $reqUrl")
                        if (!deferredMediaUrl.isCompleted) {
                            deferredMediaUrl.complete(reqUrl)
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view?.evaluateJavascript(injectionScript, null)
                }

                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    super.onPageFinished(view, pageUrl)
                    GlobalLog.append(Log.INFO, TAG, "WebView page finished: $pageUrl")
                    view?.evaluateJavascript(injectionScript, null)
                }
            }

            // Directly load captioned embed URL which renders video player DOM reliably without login walls
            val primaryUrl = if (shortcode.isNotBlank()) {
                "https://www.instagram.com/p/$shortcode/embed/captioned/"
            } else {
                originalUrl
            }
            GlobalLog.append(Log.INFO, TAG, "WebView loading initial URL: $primaryUrl")
            webView.loadUrl(primaryUrl)

            val maxWaitMs = 15000L
            val startTime = System.currentTimeMillis()
            var secondaryTried = false

            while (!deferredMediaUrl.isCompleted && System.currentTimeMillis() - startTime < maxWaitMs) {
                delay(300)
                if (deferredMediaUrl.isCompleted) break

                val elapsed = System.currentTimeMillis() - startTime
                if (!secondaryTried && elapsed > 4500L && shortcode.isNotBlank()) {
                    secondaryTried = true
                    val fallbackUrl = "https://www.instagram.com/reel/$shortcode/embed/captioned/"
                    GlobalLog.append(Log.INFO, TAG, "WebView trying alternate embed: $fallbackUrl")
                    webView.loadUrl(fallbackUrl)
                }

                webView.evaluateJavascript(injectionScript, null)
            }

            if (deferredMediaUrl.isCompleted) deferredMediaUrl.getCompleted() else null
        } catch (e: Exception) {
            GlobalLog.append(Log.ERROR, TAG, "WebView exception during Instagram resolution: ${e.message}")
            null
        } finally {
            try {
                webView?.stopLoading()
                webView?.webViewClient = WebViewClient()
                webView?.destroy()
            } catch (_: Exception) {}
        }
    }

    private fun isInstagramMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains("rsrc.php") || lower.contains(".js") || lower.contains(".css")) {
            return false
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") ||
            lower.contains(".jpg?") || lower.contains(".jpeg?") || lower.contains(".png?") || lower.contains(".webp?")) {
            return false
        }
        val isMetaCdn = lower.contains("cdninstagram.com") || lower.contains("fbcdn.net") || lower.contains("instagram.f")
        val isMedia = lower.contains(".mp4") || lower.contains(".m4a") || lower.contains(".mp3") ||
                lower.contains("/v/t50.") || lower.contains("/v/t0.") || lower.contains("/v/t64.") ||
                lower.contains("/m1/v/") || lower.contains("mime_type=video") || lower.contains("mime_type=audio") ||
                lower.contains("bytestart=")
        return isMetaCdn && isMedia
    }

    private fun resolveCanonicalUrl(rawUrl: String): String {
        return try {
            val request = Request.Builder()
                .url(rawUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (_: Exception) {
            rawUrl
        }
    }

    private suspend fun resolveSnapchatMedia(url: String): File? {
        try {
            var currentUrl = url
            val mobileUa = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.80 Mobile Safari/537.36"
            val request = Request.Builder()
                .url(currentUrl)
                .addHeader("User-Agent", mobileUa)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .build()

            var html = ""
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    html = response.body?.string().orEmpty()
                }
            }

            // Check if there is a canonical or og:url redirect (e.g. t.snapchat.com short links)
            val canonicalRegex = Regex("""<link\s+[^>]*rel=["']canonical["'][^>]*href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val ogUrlRegex = Regex("""<meta\s+[^>]*property=["']og:url["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val canonicalUrl = canonicalRegex.find(html)?.groupValues?.get(1)
                ?: ogUrlRegex.find(html)?.groupValues?.get(1)

            if (!canonicalUrl.isNullOrBlank() && canonicalUrl != currentUrl && (canonicalUrl.contains("snapchat.com") || canonicalUrl.contains("snap.com"))) {
                Log.d(TAG, "Following Snapchat canonical URL: $canonicalUrl")
                val redirectReq = Request.Builder()
                    .url(canonicalUrl)
                    .addHeader("User-Agent", mobileUa)
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .addHeader("Accept-Language", "en-US,en;q=0.9")
                    .build()
                okHttpClient.newCall(redirectReq).execute().use { response ->
                    if (response.isSuccessful) {
                        html = response.body?.string().orEmpty()
                    }
                }
            }

            val mediaUrl = extractSnapchatMediaUrl(html)
            if (!mediaUrl.isNullOrBlank()) {
                Log.d(TAG, "Resolved Snapchat media URL: $mediaUrl")
                val targetFile = tempManager.createTempFile("mp4")
                if (downloadMediaChunk(mediaUrl, targetFile)) {
                    return targetFile
                }
                tempManager.cleanup(targetFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve Snapchat media for URL: $url", e)
        }
        return null
    }

    private fun extractSnapchatMediaUrl(html: String): String? {
        fun isValidSnapchatVideo(u: String): Boolean {
            val lower = u.lowercase()
            if (lower.contains("largethumbnail") || lower.contains("thumbnail") ||
                lower.contains("_fmjpeg") || lower.contains(".jpg") || lower.contains(".jpeg") ||
                lower.contains(".png") || lower.contains(".webp") || lower.contains(".256.") ||
                lower.contains(".1400.") || lower.contains("snapcode") || lower.contains("profilepicture")
            ) {
                return false
            }
            return lower.contains(".27.") || lower.contains("/d/") || lower.contains("/u/") ||
                    lower.contains("/c/") || lower.contains("/h/") || lower.contains("/x/") ||
                    lower.contains(".mp4") || lower.contains("mo=")
        }

        // 1. Search in __NEXT_DATA__
        val nextDataRegex = Regex("""<script\s+id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val nextDataMatch = nextDataRegex.find(html)
        if (nextDataMatch != null) {
            val jsonStr = nextDataMatch.groupValues[1]
            val keys = listOf("contentUrl", "mediaUrl")
            for (key in keys) {
                val keyRegex = Regex(""""$key"\s*:\s*"([^"]+)"""")
                for (match in keyRegex.findAll(jsonStr)) {
                    val cand = unescapeJsonString(match.groupValues[1])
                    if (isValidSnapchatVideo(cand)) {
                        return cand
                    }
                }
            }
        }

        // 2. Search whole HTML for Snapchat CDN video URLs (cf-st.sc-cdn.net or bolt-gcdn.sc-cdn.net)
        val cdnRegex = Regex("""https://[a-zA-Z0-9.-]*(?:sc-cdn\.net|bolt-gcdn\.sc-cdn\.net)[^"'\s\\]+""")
        for (match in cdnRegex.findAll(html)) {
            val cand = unescapeJsonString(match.value)
            if (isValidSnapchatVideo(cand)) {
                return cand
            }
        }

        // 3. Fallback to OpenGraph / HTML5 video tags
        return extractMediaUrlFromHtml(html)
    }

    private suspend fun resolveTikTokMedia(url: String): File? {
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string().orEmpty()

                // TikTok music soundtrack MP3 URL
                val musicRegex = Regex(""""playUrl":\s*"([^"]+)"""")
                val musicMatch = musicRegex.find(html)
                if (musicMatch != null) {
                    val audioUrl = unescapeJsonString(musicMatch.groupValues[1])
                    val targetFile = tempManager.createTempFile("mp3")
                    if (downloadMediaChunk(audioUrl, targetFile)) {
                        return targetFile
                    }
                    tempManager.cleanup(targetFile)
                }

                // Video MP4 URL
                val videoRegex = Regex(""""playAddr":\s*"([^"]+)"""")
                val videoMatch = videoRegex.find(html)
                if (videoMatch != null) {
                    val videoUrl = unescapeJsonString(videoMatch.groupValues[1])
                    val targetFile = tempManager.createTempFile("mp4")
                    if (downloadMediaChunk(videoUrl, targetFile)) {
                        return targetFile
                    }
                    tempManager.cleanup(targetFile)
                }

                val ogUrl = extractMediaUrlFromHtml(html)
                if (!ogUrl.isNullOrBlank()) {
                    val targetFile = tempManager.createTempFile("mp4")
                    if (downloadMediaChunk(ogUrl, targetFile)) {
                        return targetFile
                    }
                    tempManager.cleanup(targetFile)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve TikTok media for URL: $url", e)
        }
        return null
    }

    private suspend fun resolveGenericWebMedia(url: String): File? {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null

                val contentType = response.header("Content-Type").orEmpty()
                if (contentType.startsWith("audio/") || contentType.startsWith("video/")) {
                    val ext = if (contentType.contains("audio")) "mp3" else "mp4"
                    val targetFile = tempManager.createTempFile(ext)
                    if (downloadMediaChunk(url, targetFile)) {
                        return targetFile
                    }
                    tempManager.cleanup(targetFile)
                    return null
                }

                val html = response.body?.string().orEmpty()
                val mediaUrl = extractMediaUrlFromHtml(html)
                if (!mediaUrl.isNullOrBlank()) {
                    val targetFile = tempManager.createTempFile("mp4")
                    if (downloadMediaChunk(mediaUrl, targetFile)) {
                        return targetFile
                    }
                    tempManager.cleanup(targetFile)
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed generic web media resolution for $url", e)
            null
        }
    }

    private fun extractMediaUrlFromHtml(html: String): String? {
        try {
            val unescaped = html.replace("\\/", "/").replace("\\u0026", "&").replace("\\\"", "\"")
            val doc = Jsoup.parse(html)
            // 1. og:video / og:video:secure_url
            val ogVideo = doc.select("meta[property=og:video:secure_url]").attr("content").takeIf { it.isNotBlank() }
                ?: doc.select("meta[property=og:video]").attr("content").takeIf { it.isNotBlank() }
                ?: doc.select("meta[name=twitter:player:stream]").attr("content").takeIf { it.isNotBlank() }

            if (!ogVideo.isNullOrBlank()) {
                return unescapeJsonString(ogVideo)
            }

            // 2. <video src="...">
            val videoSrc = doc.select("video source").attr("src").takeIf { it.isNotBlank() }
                ?: doc.select("video").attr("src").takeIf { it.isNotBlank() }

            if (!videoSrc.isNullOrBlank() && !videoSrc.startsWith("blob:")) {
                return unescapeJsonString(videoSrc)
            }

            // 3. Regex for JSON / embedded video_url in unescaped HTML
            val patterns = listOf(
                Regex("""(?:"video_url"|"playback_url"|"playable_url"|"playable_url_quality_hd")\s*:\s*"([^"]+)""""),
                Regex("""https://[a-zA-Z0-9.-]*(?:cdninstagram\.com|fbcdn\.net)/[^\s"']+\.mp4[^\s"']*"""),
                Regex("""https://[a-zA-Z0-9.-]*(?:cdninstagram\.com|fbcdn\.net)/v/t50[^\s"']*""")
            )

            for (pattern in patterns) {
                val match = pattern.find(unescaped)
                if (match != null) {
                    val raw = if (match.groupValues.size > 1) match.groupValues[1] else match.value
                    if (raw.isNotBlank() && raw.startsWith("http")) {
                        return unescapeJsonString(raw)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTML parsing error", e)
        }
        return null
    }

    suspend fun downloadMediaChunk(
        mediaUrl: String,
        targetFile: File,
        maxBytes: Long = MAX_CHUNK_BYTES,
        customUserAgent: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val isInstagram = mediaUrl.contains("cdninstagram.com") || mediaUrl.contains("fbcdn.net") || mediaUrl.contains("instagram.com")
        if (isInstagram) {
            // Instagram CDN rejects Range headers with 403/416; perform direct full chunk download with Referer
            return@withContext downloadWithoutRange(mediaUrl, targetFile, maxBytes, customUserAgent)
        }

        try {
            val hasRangeInUrl = mediaUrl.contains("range=") || mediaUrl.contains("bytestart=")
            val defaultUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            val reqBuilder = Request.Builder()
                .url(mediaUrl)
                .addHeader("User-Agent", customUserAgent ?: defaultUa)
                .addHeader("Accept", "*/*")

            if (!hasRangeInUrl) {
                reqBuilder.addHeader("Range", "bytes=0-${maxBytes - 1}")
            }

            val request = reqBuilder.build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    GlobalLog.append(Log.WARN, TAG, "Chunk download HTTP ${response.code}, trying direct download...")
                    return@use downloadWithoutRange(mediaUrl, targetFile, maxBytes, customUserAgent)
                }
                val body = response.body ?: return@use false
                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8192)
                        var totalRead = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            totalRead += read
                            if (totalRead >= maxBytes) break
                        }
                    }
                }
                val success = targetFile.exists() && targetFile.length() > 2048
                if (success) {
                    GlobalLog.append(Log.INFO, TAG, "Downloaded ${targetFile.length()} bytes to ${targetFile.name}")
                }
                success
            }
        } catch (e: Exception) {
            GlobalLog.append(Log.WARN, TAG, "Range download failed (${e.message}), trying direct download...")
            downloadWithoutRange(mediaUrl, targetFile, maxBytes, customUserAgent)
        }
    }

    private fun downloadWithoutRange(
        mediaUrl: String,
        targetFile: File,
        maxBytes: Long,
        customUserAgent: String? = null
    ): Boolean {
        return try {
            val isInstagram = mediaUrl.contains("cdninstagram.com") || mediaUrl.contains("fbcdn.net") || mediaUrl.contains("instagram.com")
            val defaultUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            val reqBuilder = Request.Builder()
                .url(mediaUrl)
                .addHeader("User-Agent", customUserAgent ?: defaultUa)
                .addHeader("Accept", "*/*")

            if (isInstagram) {
                reqBuilder.addHeader("Referer", "https://www.instagram.com/")
            }

            val request = reqBuilder.build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    GlobalLog.append(Log.ERROR, TAG, "Direct download failed with HTTP ${response.code}")
                    return false
                }
                val body = response.body ?: return false
                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8192)
                        var totalRead = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            totalRead += read
                            if (totalRead >= maxBytes) break
                        }
                    }
                }
                val success = targetFile.exists() && targetFile.length() > 2048
                if (success) {
                    GlobalLog.append(Log.INFO, TAG, "Direct download succeeded: ${targetFile.length()} bytes")
                }
                success
            }
        } catch (e: Exception) {
            GlobalLog.append(Log.ERROR, TAG, "Direct download exception: ${e.message}")
            false
        }
    }

    private fun isDirectMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mp3") || lower.endsWith(".m4a") ||
                lower.endsWith(".wav") || lower.endsWith(".webm") || lower.endsWith(".ogg") ||
                lower.endsWith(".aac") || lower.endsWith(".flac")
    }

    private fun unescapeJsonString(str: String): String {
        return str
            .replace(Regex("""\\u0026""", RegexOption.IGNORE_CASE), "&")
            .replace(Regex("""\\u002f""", RegexOption.IGNORE_CASE), "/")
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\\", "")
            .trim()
    }
}
