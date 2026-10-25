package com.devxrohan.smusic.ui.sharedmusic

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.devxrohan.smusic.MainActivity
import com.devxrohan.smusic.share.ShareIntentParser
import com.devxrohan.smusic.share.SharedContent
import com.devxrohan.smusic.ui.theme.SMusicTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point for "Find Music" in Android's share sheet — the activity the user asked for:
 * sharing an Instagram Reel / YouTube Short video via Share -> SMusic -> Find Music.
 * Registered for ACTION_SEND/ACTION_SEND_MULTIPLE with video, audio, and plain-text MIME types in the
 * manifest. Ported logic from SMusic' SharedMusicIdentificationActivity.kt, but delegates
 * recognition to SMusic's own Shazam pipeline via [SharedMusicViewModel] instead of SMusic'
 * provider/usecase abstraction (see ACCOUNT_INTEGRATION_REPORT.md).
 */
@AndroidEntryPoint
class SharedMusicIdentificationActivity : ComponentActivity() {

    private val viewModel: SharedMusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            SMusicTheme {
                SharedMusicScreen(
                    viewModel = viewModel,
                    onOpenInSMusic = { query ->
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("navigate_to", "search/${Uri.encode(query)}")
                        }
                        startActivity(mainIntent)
                        finish()
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val sharedContents = ShareIntentParser.parse(this, intent)
        val initialContent: SharedContent? = sharedContents.firstOrNull()
        if (initialContent != null) {
            viewModel.processSharedContent(initialContent)
        }
    }
}
