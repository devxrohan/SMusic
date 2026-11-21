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

/** Handle shared video audio through the Find Music entry in the Android share sheet. */
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
