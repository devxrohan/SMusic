package com.devxrohan.smusic.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.AudioQuality
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

object SaveToStorageUtil {
    private const val TAG = "SaveToStorageUtil"
    private const val CHANNEL_ID = "smusic_storage_downloads"
    private const val CHANNEL_NAME = "Storage Downloads"

    // Downloads must outlive Compose bottom sheets/menus, which are dismissed immediately.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val exportGate = Semaphore(1)
    private val pending = ConcurrentHashMap<String, Deferred<Result<String>>>()
    private val baseHttpClient = OkHttpClient.Builder().build()
    private val httpClient get() = baseHttpClient.newBuilder().proxy(YouTube.streamProxy).build()

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager?.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows download progress when saving songs to device storage"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }
                notificationManager?.createNotificationChannel(channel)
            }
        }
    }

    private fun showProgressNotification(
        context: Context,
        notificationId: Int,
        title: String,
        progress: Int,
        subText: String? = null,
    ) {
        try {
            createNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.save_to_storage)
                .setContentTitle(title)
                .setContentText(if (progress in 0..100) "$progress%" else "Downloading…")
                .setProgress(100, progress.coerceIn(0, 100), progress < 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            if (!subText.isNullOrEmpty()) {
                builder.setSubText(subText)
            }

            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to update download progress notification")
        }
    }

    private fun showCompleteNotification(
        context: Context,
        notificationId: Int,
        title: String,
        success: Boolean,
        message: String,
    ) {
        try {
            createNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(if (success) R.drawable.save_to_storage else R.drawable.close)
                .setContentTitle(if (success) "Downloaded" else "Download failed")
                .setContentText(message)
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to show complete notification")
        }
    }

    suspend fun savePlaylistToMusicFolder(
        context: Context,
        playlistName: String,
        mediaList: List<MediaMetadata>,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val subFolder = playlistName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(50)
            val relativeSubPath = if (subFolder.isNotEmpty()) "SMusic/$subFolder" else "SMusic"
            var savedCount = 0
            val total = mediaList.size

            mediaList.forEachIndexed { index, mediaMetadata ->
                try {
                    val subText = "${index + 1}/$total"
                    val notificationId = 20000 + (mediaMetadata.id.hashCode() and 0x7FFF)
                    saveToFolder(
                        context = context,
                        mediaMetadata = mediaMetadata,
                        relativeFolder = relativeSubPath,
                        notificationId = notificationId,
                        subText = subText,
                    ).onSuccess {
                        savedCount++
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error saving song ${mediaMetadata.title} in playlist")
                }
            }
            savedCount
        }
    }

    /** Own downloads in an application scope so dismissing a menu cannot cancel them. */
    fun saveToMusicFolderInBackground(
        context: Context,
        mediaMetadata: MediaMetadata,
        onResult: (Result<String>) -> Unit = {},
    ) {
        val appContext = context.applicationContext
        backgroundScope.launch {
            val result = saveToMusicFolder(appContext, mediaMetadata)
            withContext(Dispatchers.Main.immediate) {
                onResult(result)
            }
        }
    }

    suspend fun saveToMusicFolder(
        context: Context,
        mediaMetadata: MediaMetadata,
    ): Result<String> = saveToFolder(
        context = context,
        mediaMetadata = mediaMetadata,
        relativeFolder = "SMusic",
        notificationId = 20000 + (mediaMetadata.id.hashCode() and 0x7FFF),
        subText = null,
    )

    suspend fun saveToFolder(
        context: Context,
        mediaMetadata: MediaMetadata,
        relativeFolder: String,
        notificationId: Int = 20000 + (mediaMetadata.id.hashCode() and 0x7FFF),
        subText: String? = null,
    ): Result<String> {
        val key = "$relativeFolder|${mediaMetadata.id}"
        val candidate = backgroundScope.async(start = CoroutineStart.LAZY) {
            exportGate.withPermit { performSave(context.applicationContext, mediaMetadata, relativeFolder, notificationId, subText) }
        }
        val active = pending.putIfAbsent(key, candidate)
        if (active != null) { candidate.cancel(); return active.await() }
        candidate.invokeOnCompletion { pending.remove(key, candidate) }
        candidate.start()
        return candidate.await()
    }

    private suspend fun performSave(
        context: Context,
        mediaMetadata: MediaMetadata,
        relativeFolder: String,
        notificationId: Int,
        subText: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        val workspace = File(context.cacheDir, "audio-export-${java.util.UUID.randomUUID()}")
        val source = File(workspace, "source.m4a")
        val remuxed = File(workspace, "remuxed.m4a")
        val tagged = File(workspace, "tagged.m4a")
        try {
            check(workspace.mkdirs()) { "Could not create export workspace" }
            showProgressNotification(context, notificationId, mediaMetadata.title, 0, subText)
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val playback = YTPlayerUtils.playerResponseForPlayback(mediaMetadata.id, audioQuality = AudioQuality.HIGH,
                connectivityManager = connectivity, avoidCodecs = setOf("opus", "vorbis")).getOrThrow()
            require(playback.format.mimeType.contains("mp4")) { "A compatible AAC download is unavailable" }
            val length = playback.format.contentLength?.takeIf { it > 0 }
            val url = if (length != null && !playback.streamUrl.contains("range="))
                "${playback.streamUrl}&range=0-${length - 1}" else playback.streamUrl
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "*/*").build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                val body = response.body ?: error("Download body is missing")
                val expected = length ?: body.contentLength().takeIf { it > 0 }
                if (expected == null) showProgressNotification(context, notificationId, mediaMetadata.title, -1, subText)
                var total = 0L
                var lastUpdate = 0L
                var lastPercent = -1
                body.byteStream().use { input -> source.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                        val now = android.os.SystemClock.elapsedRealtime()
                        val percent = expected?.let { ((total * 100) / it).toInt().coerceIn(0, 100) } ?: -1
                        if (percent != lastPercent && now - lastUpdate >= 500) {
                            showProgressNotification(context, notificationId, mediaMetadata.title, percent, subText)
                            lastUpdate = now
                            lastPercent = percent
                        }
                    }
                } }
                check(total > 0 && (expected == null || total == expected)) { "Audio download was incomplete" }
            }
            showProgressNotification(context, notificationId, mediaMetadata.title, 100, "Adding artwork")
            val artwork = AudioFileExport.artwork(context, mediaMetadata)
            AudioFileExport.remuxAac(source, remuxed)
            source.delete()
            val output = try {
                Mp4CoverWriter.write(remuxed, tagged, mediaMetadata.title,
                    mediaMetadata.artists.joinToString(", ") { it.name },
                    ExportArtworkPolicy.album(mediaMetadata.title, mediaMetadata.artists.joinToString(", ") { it.name }, mediaMetadata.album?.title), artwork)
                tagged
            } catch (error: Exception) {
                Timber.tag(TAG).w(error, "Could not embed export tags; keeping playable audio")
                remuxed
            }
            val title = mediaMetadata.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(180)
            val artist = mediaMetadata.artists.joinToString(", ") { it.name }.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
            val fileName = "$title - $artist.m4a"
            publish(context, mediaMetadata, output, fileName, relativeFolder)
            showCompleteNotification(context, notificationId, mediaMetadata.title, true,
                if (artwork != null && output == tagged) "${mediaMetadata.title} saved with cover art"
                else "${mediaMetadata.title} saved; cover art was unavailable")
            Result.success(fileName)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            Timber.tag(TAG).e(error, "Could not save audio to phone")
            showCompleteNotification(context, notificationId, mediaMetadata.title, false, "Failed to download: ${error.message}")
            Result.failure(error)
        } finally { workspace.deleteRecursively() }
    }

    private fun publish(context: Context, track: MediaMetadata, source: File, fileName: String, relativeFolder: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$relativeFolder")
                put(MediaStore.Audio.Media.TITLE, track.title)
                put(MediaStore.Audio.Media.ARTIST, track.artists.joinToString(", ") { it.name })
                put(MediaStore.Audio.Media.ALBUM, ExportArtworkPolicy.album(track.title, track.artists.joinToString(", ") { it.name }, track.album?.title))
                if (track.duration > 0) put(MediaStore.Audio.Media.DURATION, track.duration * 1000L)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create audio entry")
            try {
                resolver.openOutputStream(uri, "w")?.use { output -> source.inputStream().use { it.copyTo(output) } }
                    ?: error("Could not write audio file")
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                check(resolver.update(uri, values, null, null) > 0) { "Could not publish audio file" }
            } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
        } else {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), relativeFolder)
            check(folder.isDirectory || folder.mkdirs())
            val temporary = File.createTempFile("audio-", ".part", folder)
            val destination = File(folder, fileName)
            try {
                source.inputStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
                check(temporary.renameTo(destination)) { "Could not publish audio file" }
            } finally { temporary.delete() }
            MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf("audio/mp4"), null)
        }
    }
}
