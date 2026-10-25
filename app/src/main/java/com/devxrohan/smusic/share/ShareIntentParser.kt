package com.devxrohan.smusic.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Patterns

object ShareIntentParser {

    private val URL_REGEX = Regex("https?://\\S+")

    fun parse(context: Context, intent: Intent): List<SharedContent> {
        val action = intent.action ?: return emptyList()
        val mimeType = intent.type
        val sourcePackage = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
            ?: intent.`package`

        val results = mutableListOf<SharedContent>()

        when (action) {
            Intent.ACTION_SEND -> {
                // Check stream URI first (video/audio files)
                val streamUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }

                val clipUri: Uri? = intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                val primaryUri = streamUri ?: clipUri

                if (primaryUri != null) {
                    tryTakePersistablePermission(context, primaryUri)
                    val contentType = classifyUri(context, primaryUri, mimeType)
                    results.add(
                        SharedContent(
                            type = contentType,
                            uri = primaryUri,
                            mimeType = mimeType ?: context.contentResolver.getType(primaryUri),
                            sourcePackage = sourcePackage
                        )
                    )
                } else {
                    // Check text / URL
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                        ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

                    if (!sharedText.isNullOrBlank()) {
                        val extractedUrl = extractUrl(sharedText)
                        if (extractedUrl != null) {
                            results.add(
                                SharedContent(
                                    type = SharedContentType.URL,
                                    text = extractedUrl,
                                    mimeType = "text/plain",
                                    sourcePackage = sourcePackage
                                )
                            )
                        } else {
                            results.add(
                                SharedContent(
                                    type = SharedContentType.TEXT,
                                    text = sharedText,
                                    mimeType = "text/plain",
                                    sourcePackage = sourcePackage
                                )
                            )
                        }
                    }
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val streamUris: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }

                if (!streamUris.isNullOrEmpty()) {
                    for (uri in streamUris) {
                        tryTakePersistablePermission(context, uri)
                        val contentType = classifyUri(context, uri, mimeType)
                        results.add(
                            SharedContent(
                                type = contentType,
                                uri = uri,
                                mimeType = mimeType ?: context.contentResolver.getType(uri),
                                sourcePackage = sourcePackage
                            )
                        )
                    }
                } else if (intent.clipData != null) {
                    val count = intent.clipData?.itemCount ?: 0
                    for (i in 0 until count) {
                        val itemUri = intent.clipData?.getItemAt(i)?.uri
                        if (itemUri != null) {
                            tryTakePersistablePermission(context, itemUri)
                            val contentType = classifyUri(context, itemUri, mimeType)
                            results.add(
                                SharedContent(
                                    type = contentType,
                                    uri = itemUri,
                                    mimeType = mimeType ?: context.contentResolver.getType(itemUri),
                                    sourcePackage = sourcePackage
                                )
                            )
                        }
                    }
                }
            }
        }

        return results
    }

    private fun classifyUri(context: Context, uri: Uri, explicitMime: String?): SharedContentType {
        val resolvedMime = explicitMime ?: context.contentResolver.getType(uri) ?: ""
        return when {
            resolvedMime.startsWith("video/", ignoreCase = true) -> SharedContentType.VIDEO
            resolvedMime.startsWith("audio/", ignoreCase = true) -> SharedContentType.AUDIO
            else -> {
                val path = uri.path?.lowercase() ?: ""
                when {
                    path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm") ||
                            path.endsWith(".mov") || path.endsWith(".avi") || path.endsWith(".3gp") -> SharedContentType.VIDEO
                    path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".wav") ||
                            path.endsWith(".ogg") || path.endsWith(".opus") || path.endsWith(".aac") ||
                            path.endsWith(".flac") -> SharedContentType.AUDIO
                    else -> SharedContentType.VIDEO // Default media assumption for intent streams
                }
            }
        }
    }

    private fun tryTakePersistablePermission(context: Context, uri: Uri) {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Non-persistable or transient grant, safe to ignore
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val match = URL_REGEX.find(text)
        return match?.value ?: if (Patterns.WEB_URL.matcher(text).matches()) text else null
    }
}
