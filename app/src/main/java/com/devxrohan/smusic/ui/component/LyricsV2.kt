/*
 * SMusic Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.devxrohan.smusic.ui.component

import android.annotation.SuppressLint
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import com.devxrohan.smusic.constants.AppFont
import com.devxrohan.smusic.constants.AppFontKey
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.LyricsLineSpacingKey
import com.devxrohan.smusic.constants.LyricsScrollKey
import com.devxrohan.smusic.constants.LyricsTextSizeKey
import com.devxrohan.smusic.constants.AutoTranslateKey
import com.devxrohan.smusic.constants.ReplaceOriginalLyricsWithTranslationKey
import com.devxrohan.smusic.constants.TranslateLanguageKey
import com.devxrohan.smusic.constants.TranslateModeKey
import com.devxrohan.smusic.lyrics.LyricsTranslationHelper
import com.devxrohan.smusic.constants.AiProviderKey
import com.devxrohan.smusic.constants.OpenRouterApiKey
import com.devxrohan.smusic.constants.OpenRouterBaseUrlKey
import com.devxrohan.smusic.constants.OpenRouterModelKey
import com.devxrohan.smusic.constants.DeeplApiKey
import com.devxrohan.smusic.constants.PlayerBackgroundStyle
import com.devxrohan.smusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.devxrohan.smusic.lyrics.LyricsEntry
import com.devxrohan.smusic.lyrics.SMusicLyricsUtils.findCurrentLineIndex
import com.devxrohan.smusic.lyrics.SMusicLyricsUtils.isTtml
import com.devxrohan.smusic.lyrics.SMusicLyricsUtils.parseLyrics
import com.devxrohan.smusic.lyrics.SMusicLyricsUtils.parseTtml
import com.devxrohan.smusic.lyrics.WordTimestamp
import com.devxrohan.smusic.ui.component.shimmer.ShimmerHost
import com.devxrohan.smusic.ui.component.shimmer.TextPlaceholder
import com.devxrohan.smusic.ui.utils.smoothFadingEdge
import com.devxrohan.smusic.utils.rememberEnumPreference
import com.devxrohan.smusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs


// ──────────────────────────────────────────────────────────────────────
// Constants
// ──────────────────────────────────────────────────────────────────────

/** Seconds to wait before auto-scroll resumes after manual scroll. */
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L

/** Apple-Music-style easing for smooth deceleration. */
private val V2Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)

/** Liquid fill easing: fast attack, very smooth deceleration (Apple Music-like). */
private val LiquidFillEasing = CubicBezierEasing(0.0f, 0.0f, 0.15f, 1.0f)

/** Sentinel entry prepended so auto-scroll has headroom above the first line. */
private val HEAD_LYRICS_ENTRY = LyricsEntry(time = 0L, text = "")


// ──────────────────────────────────────────────────────────────────────
// Main Composable
// ──────────────────────────────────────────────────────────────────────


@SuppressLint("UnusedBoxWithConstraintsScope", "LocalContextGetResourceValueCall",
    "StringFormatInvalid"
)
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LyricsV2(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    onSelectionChanged: (String) -> Unit = {},
    selectionRequest: Int = 0,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    // ── Preferences ──
    val (lyricsScroll) = rememberPreference(LyricsScrollKey, defaultValue = true)
    val (lyricsTextSize) = rememberPreference(LyricsTextSizeKey, defaultValue = 26f)
    val (lyricsLineSpacing) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)
    val (targetLanguage) = rememberPreference(TranslateLanguageKey, defaultValue = "auto")
    val (autoTranslate) = rememberPreference(AutoTranslateKey, defaultValue = false)
    val (aiProvider) = rememberPreference(AiProviderKey, defaultValue = "Google")
    val (openRouterApiKey) = rememberPreference(OpenRouterApiKey, defaultValue = "")
    val (deeplApiKey) = rememberPreference(DeeplApiKey, defaultValue = "")
    val (openRouterBaseUrl) = rememberPreference(OpenRouterBaseUrlKey, defaultValue = "https://api.puter.com/v1/chat/completions")
    val (openRouterModel) = rememberPreference(OpenRouterModelKey, defaultValue = "gpt-4o-mini")
    val (translateMode) = rememberPreference(TranslateModeKey, defaultValue = "Literal")
    val (replaceOriginalLyrics) = rememberPreference(ReplaceOriginalLyricsWithTranslationKey, defaultValue = false)
    val translationVersion by LyricsTranslationHelper.translationVersion.collectAsState()
    val (appFontKey) = rememberPreference(AppFontKey, defaultValue = AppFont.APPLE.key)
    val lyricsFontFamily = remember(appFontKey) { AppFont.fromKey(appFontKey).getFontFamily() }
    val playerBackground = PlayerBackgroundStyle.DEFAULT

    // ── Text colour derived from background style ──
    val textColor = if (playerBackground == PlayerBackgroundStyle.DEFAULT)
        MaterialTheme.colorScheme.onBackground
    else
        Color.White

    val inactiveAlpha = 0.35f

    // ── Selection mode state ──
    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    LaunchedEffect(selectionRequest) {
        if (selectionRequest > 0) isSelectionModeActive = true
    }
    var showMaxSelectionToast by remember { mutableStateOf(false) }
    val maxSelectionLimit = com.devxrohan.smusic.utils.LyricsShareRenderer.MAX_SELECTED_LINES
    var showShareDialog by remember { mutableStateOf(false) }
    var shareDialogData by remember {
        mutableStateOf<com.devxrohan.smusic.ui.component.ShareLyricsData?>(null)
    }
    // ── Lyrics data ──
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyrics = currentLyrics?.lyrics

    // ── Parse lyrics into entries ──

    val lyricsEntries: List<LyricsEntry> = remember(lyrics, player.duration) {
        if (lyrics == null || lyrics == LYRICS_NOT_FOUND) return@remember emptyList()
        val parsed = when {
            com.devxrohan.smusic.lyrics.SMusicLyricsUtils.isRecognizedTimedLyrics(lyrics!!) ->
                com.devxrohan.smusic.lyrics.SMusicLyricsUtils.parseAnyLyrics(lyrics!!)
            else -> lyrics!!.lines()
                .filter { it.isNotBlank() }
                .map { line -> LyricsEntry(time = -1L, text = line.trim()) }
        }.filter { it.text.isNotBlank() || it.sungUntilMs != null }

        // Untimed lyrics still advance as complete lines over the track duration,
        // but their text stays static because there are no real timing stamps.
        val allUntimed = parsed.isNotEmpty() && parsed.all { it.time < 0L }
        val timed = if (allUntimed) {
            val trackDuration = player.duration.takeIf { it > 0L }
                ?: (parsed.size * 2500L)
            parsed.mapIndexed { index, entry ->
                val startMs = trackDuration * index / parsed.size
                // Keep the line on the playback timeline, but do not invent
                // word timing. Only provider-supplied word timestamps may drive
                // the karaoke bounce/highlight animation.
                entry.copy(time = startMs)
            }
        } else parsed

        if (timed.isNotEmpty() && timed.first().time >= 0L) {
            listOf(HEAD_LYRICS_ENTRY) + timed
        } else timed
    }

    // A TTML document is not necessarily synced: lrc.red also returns
    // lrc:timing="None" documents containing plain <p> lyric lines.
    val isSynced = remember(lyricsEntries) {
        lyricsEntries.any { it.time >= 0L }
    }

    // Only preserve real provider word timings. Line-timed and untimed entries
    // stay as complete text lines; stretching a synthetic word across the gap
    // to the next timestamp made instrumental sections animate for too long.
    val entriesWithWords: List<LyricsEntry> = lyricsEntries
    LaunchedEffect(selectedIndices.toList(), entriesWithWords) {
        onSelectionChanged(
            selectedIndices.sorted()
                .mapNotNull { entriesWithWords.getOrNull(it)?.text }
                .joinToString("\n")
        )
    }


    DisposableEffect(entriesWithWords) {
        LyricsTranslationHelper.registerLyrics(entriesWithWords)
        onDispose {
            LyricsTranslationHelper.unregisterLyrics(entriesWithWords)
        }
    }

    LaunchedEffect(mediaMetadata?.id) {
        mediaMetadata?.id?.let { LyricsTranslationHelper.onSongChanged(it) }
    }

    // ── AI Lyrics Translation Sync ──
    LaunchedEffect(entriesWithWords, mediaMetadata?.id, targetLanguage, autoTranslate, aiProvider, deeplApiKey, openRouterApiKey, openRouterBaseUrl, openRouterModel, translateMode) {
        val songId = mediaMetadata?.id ?: return@LaunchedEffect
        if (entriesWithWords.isEmpty()) return@LaunchedEffect

        if (autoTranslate) {
            val hasLoaded = LyricsTranslationHelper.loadTranslationsFromCache(
                lyrics = entriesWithWords,
                context = context,
                songId = songId,
                targetLanguageCode = targetLanguage
            )

            // Auto-translate if enabled and not already translated or translating
            if (!hasLoaded && !LyricsTranslationHelper.isTranslating()) {
                val key = if (aiProvider == "DeepL") deeplApiKey else openRouterApiKey
                if (key.isNotBlank() || aiProvider == "Puter" || aiProvider == "Custom" || aiProvider == "Google") {
                    LyricsTranslationHelper.translateLyrics(
                        lyrics = entriesWithWords,
                        targetLanguageCode = targetLanguage,
                        apiKey = key,
                        baseUrl = openRouterBaseUrl,
                        model = openRouterModel,
                        mode = translateMode,
                        provider = aiProvider,
                        context = context,
                        songId = songId,
                        scope = scope
                    )
                }
            }
        }
    }

    // ── Romanization ──
    // ── Playback position tracking ──
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var currentLineIndex by remember { mutableIntStateOf(0) }

    // Frame-accurate position loop
    LaunchedEffect(entriesWithWords, isSynced) {
        if (!isSynced || entriesWithWords.isEmpty()) return@LaunchedEffect
        while (isActive) {
            val sliderPos = sliderPositionProvider()
            val pos = sliderPos ?: player.currentPosition

            currentPositionMs = pos
            currentLineIndex = findCurrentLineIndex(entriesWithWords, currentPositionMs, 0L)
            delay(16L) // ~60fps polling
        }
    }

    // ── Scroll State ──
    val listState = rememberLazyListState()
    var isManualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    // Detect manual scrolling
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isSelectionModeActive && source == NestedScrollSource.UserInput) {
                    isManualScrolling = true
                    lastManualScrollTime = System.currentTimeMillis()
                }
                return Offset.Zero
            }
        }
    }

    // Resume auto-scroll after timeout
    LaunchedEffect(isManualScrolling, lastManualScrollTime) {
        if (isManualScrolling) {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            isManualScrolling = false
        }
    }

    // Auto-scroll to active line
    LaunchedEffect(currentLineIndex, isManualScrolling, lyricsScroll) {
        if (!lyricsScroll || isManualScrolling || !isSynced) return@LaunchedEffect
        if (currentLineIndex < 0 || currentLineIndex >= entriesWithWords.size) return@LaunchedEffect

        val visibleInfo = listState.layoutInfo
        val viewportHeight = visibleInfo.viewportSize.height
        val targetOffset = (viewportHeight * 0.35f).toInt() // Center bias at 35% from top

        val distance = abs(currentLineIndex - (listState.firstVisibleItemIndex))
        if (distance > 15) {
            // Far jump — snap first, then settle
            listState.scrollToItem(
                (currentLineIndex - 2).coerceAtLeast(0),
                0
            )
        }
        listState.animateScrollToItem(
            index = currentLineIndex,
            scrollOffset = -targetOffset
        )
    }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedIndices.clear()
    }

    LaunchedEffect(showMaxSelectionToast) {
        if (showMaxSelectionToast) {
            Toast.makeText(
                context,
                context.getString(R.string.max_selection_limit, maxSelectionLimit),
                Toast.LENGTH_SHORT
            ).show()
            showMaxSelectionToast = false
        }
    }

    // ── Keep screen alive ──
    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── Render ──
    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
    ) {
        if (lyrics == LYRICS_NOT_FOUND) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            return@BoxWithConstraints
        }

        if (lyrics == null) {
            ShimmerHost {
                repeat(6) {
                    TextPlaceholder()
                }
            }
            return@BoxWithConstraints
        }

        if (entriesWithWords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            return@BoxWithConstraints
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .smoothFadingEdge(vertical = 80.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(
                items = entriesWithWords,
                key = { index, entry -> "${index}_${entry.time}" }
            ) { index, item ->
                if (item == HEAD_LYRICS_ENTRY) {
                    Spacer(modifier = Modifier.height(120.dp))
                    return@itemsIndexed
                }
                if (item.text.isBlank()) {
                    Spacer(modifier = Modifier.height(48.dp))
                    return@itemsIndexed
                }

                // ── Agent-based positioning ──
                // v1 or null -> Start, v2 -> End, others -> Center
                val textAlign = when (item.agent?.lowercase()) {
                    "v1", null -> TextAlign.Start
                    "v2" -> TextAlign.End
                    else -> TextAlign.Center
                }
                val horizontalAlignment = when (item.agent?.lowercase()) {
                    "v1", null -> Alignment.Start
                    "v2" -> Alignment.End
                    else -> Alignment.CenterHorizontally
                }

                val isActive = isSynced && index == currentLineIndex
                val isPast = isSynced && index < currentLineIndex
                val isFuture = isSynced && index > currentLineIndex

                // Distance-based alpha for non-active lines
                val distanceFromActive = if (isSynced) abs(index - currentLineIndex) else 0
                // For word-synced lines, each word handles its own alpha independently
                // so we use 1f for active lines to not double-dim
                val lineAlpha = when {
                    !isSynced -> 0.9f
                    isActive -> 1f
                    else -> (inactiveAlpha - (distanceFromActive - 1) * 0.03f)
                        .coerceIn(0.15f, inactiveAlpha)
                }
                // Word-synced lines: pass 1f alpha so individual words control their own
                val wordLineAlpha = if (item.words != null && isSynced) 1f else lineAlpha



                // Background vocal detection
                val hasBackgroundWords = item.words?.any { it.isBackground } == true
                val isAllBackground = item.words?.all { it.isBackground || it.text.isBlank() } == true

                val isSelected = selectedIndices.contains(index)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isSelected && isSelectionModeActive)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else
                                Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(
                            start = if (isAllBackground) 24.dp else 12.dp,
                            end = 12.dp,
                            top = if (index == 0 || (index == 1 && entriesWithWords[0] == HEAD_LYRICS_ENTRY)) 0.dp else (lyricsLineSpacing * 8).dp,
                            bottom = (lyricsLineSpacing * 8).dp,
                        )
                        .alpha(wordLineAlpha)
                        .combinedClickable(
                            enabled = true,
                            onClick = {
                                if (isSelectionModeActive) {
                                    if (isSelected) {
                                        selectedIndices.remove(index)
                                        if (selectedIndices.isEmpty()) {
                                            isSelectionModeActive = false
                                        }
                                    } else {
                                        if (selectedIndices.size < maxSelectionLimit) {
                                            selectedIndices.add(index)
                                        } else {
                                            showMaxSelectionToast = true
                                        }
                                    }
                                } else if (isSynced && item.time > 0) {
                                    player.seekTo(item.time)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionModeActive) {
                                    isSelectionModeActive = true
                                    selectedIndices.add(index)
                                } else if (!isSelected && selectedIndices.size < maxSelectionLimit) {
                                    selectedIndices.add(index)
                                } else if (!isSelected) {
                                    showMaxSelectionToast = true
                                }
                            }
                        ),
                    horizontalAlignment = horizontalAlignment,
                ) {
                    val translatedText by item.translatedTextFlow.collectAsState()
                    val showDirectTranslation = replaceOriginalLyrics && !translatedText.isNullOrBlank()


                    if (showDirectTranslation) {
                        // AI translation has no provider-supplied word timestamps.
                        // Keep it as one complete line instead of assigning fake
                        // word times that make translated words jump one by one.
                        Text(
                            text = translatedText!!,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = if (isAllBackground) (lyricsTextSize * 0.82f).sp else lyricsTextSize.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                            ),
                            color = textColor.copy(alpha = if (isActive) 1f else inactiveAlpha),
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (!item.words.isNullOrEmpty() && isSynced) {
                        // ── Word-synced original lyrics rendering ──
                        LyricsLineV2(
                            words = item.words,
                            isActive = isActive,
                            isPast = isPast,
                            currentPositionMs = currentPositionMs,
                            textColor = textColor,
                            inactiveAlpha = inactiveAlpha,
                            baseFontSize = lyricsTextSize,
                            isLineAllBackground = isAllBackground,
                            textAlign = textAlign,
                            lyricsFontFamily = lyricsFontFamily,
                        )
                    } else {
                        // ── Plain text rendering (no word-level timing) ──
                        // There are no per-word timestamps to bounce here, but the
                        // line should still visibly react when it becomes the
                        // active one instead of just snapping between font
                        // weight/alpha values. A gentle scale-and-fade "pop" gives
                        // it the same sense of motion as the karaoke line above,
                        // without inventing fake word timings.
                        val activeLineScale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isActive) 1f else 0.97f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                            ),
                            label = "plainLyricLineScale",
                        )
                        val activeLineAlpha by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isActive) 1f else inactiveAlpha,
                            animationSpec = androidx.compose.animation.core.tween(260),
                            label = "plainLyricLineAlpha",
                        )
                        Text(
                            text = if (showDirectTranslation) translatedText!! else item.text,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = if (isAllBackground) (lyricsTextSize * 0.82f).sp else lyricsTextSize.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal,
                                lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                            ),
                            color = textColor.copy(alpha = activeLineAlpha),
                            textAlign = textAlign,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = activeLineScale
                                    scaleY = activeLineScale
                                    transformOrigin = when (horizontalAlignment) {
                                        Alignment.Start -> androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                        Alignment.End -> androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                                        else -> androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                                    }
                                },
                        )
                    }

                    // ── Romanization ──
                    val romanizedText by item.romanizedTextFlow.collectAsState()
                    if (!showDirectTranslation && romanizedText != null) {
                        Text(
                            text = romanizedText!!,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = (lyricsTextSize * 0.55f).sp,
                                lineHeight = (lyricsTextSize * 0.75f).sp,
                                fontWeight = FontWeight.Normal,
                                fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal,
                                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.bodyMedium.fontFamily,
                            ),
                            color = textColor.copy(alpha = if (isActive) 0.75f else inactiveAlpha * 0.7f),
                            textAlign = textAlign,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = (lyricsTextSize * 0.3f).dp),
                        )
                    }

                    // ── AI Lyrics Translation ──
                    if (!showDirectTranslation && !translatedText.isNullOrBlank()) {
                        // Translation is displayed as a complete sentence. Its
                        // progress follows the source line, but it has no invented
                        // per-word bounce animation.
                        Text(
                            text = translatedText!!,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = (lyricsTextSize * 0.65f).sp,
                                lineHeight = (lyricsTextSize * 0.85f).sp,
                                fontWeight = FontWeight.Medium,
                                fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal,
                                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.bodyMedium.fontFamily,
                            ),
                            color = textColor.copy(alpha = if (isActive) 0.90f else inactiveAlpha * 0.75f),
                            textAlign = textAlign,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = (lyricsTextSize * 0.25f).dp),
                        )
                    }
                }
            }

            // Bottom spacer for overscroll
            item {
                Spacer(modifier = Modifier.height(300.dp))
            }
        }

        // ── Resume auto-scroll button ──
        if (isManualScrolling && isSynced) {
            androidx.compose.material3.FilledTonalButton(
                onClick = {
                    isManualScrolling = false
                    scope.launch {
                        val viewportHeight = listState.layoutInfo.viewportSize.height
                        listState.animateScrollToItem(
                            index = currentLineIndex,
                            scrollOffset = -(viewportHeight * 0.35f).toInt()
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.ui_resume),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (isSelectionModeActive) {
            mediaMetadata?.let { metadata ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    isSelectionModeActive = false
                                    selectedIndices.clear()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.close),
                                contentDescription = stringResource(R.string.cancel),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .background(
                                    color = if (selectedIndices.isNotEmpty())
                                        Color.White.copy(alpha = 0.9f)
                                    else
                                        Color.White.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clickable(enabled = selectedIndices.isNotEmpty()) {
                                    if (selectedIndices.isNotEmpty()) {
                                        val sortedIndices = selectedIndices.sorted()
                                        val selectedEntries = sortedIndices.mapNotNull { entriesWithWords.getOrNull(it) }
                                        val selectedLyricsText = selectedEntries.joinToString("\n") { it.text }

                                        if (selectedLyricsText.isNotBlank()) {
                                            shareDialogData = com.devxrohan.smusic.ui.component.ShareLyricsData(
                                                text = selectedLyricsText,
                                                entries = selectedEntries,
                                                songTitle = metadata.title ?: "",
                                                artists = metadata.artists.joinToString { it.name }
                                            )
                                            showShareDialog = true
                                        }
                                        isSelectionModeActive = false
                                        selectedIndices.clear()
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.share),
                                contentDescription = stringResource(R.string.share_selected),
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.share),
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }


        // ── Sharing the picked lines ─────────────────────────────────────────────
        if (showShareDialog) {
            shareDialogData?.let { data ->
                ShareLyricsDialog(
                    data = data,
                    mediaMetadata = mediaMetadata,
                    onDismiss = {
                        showShareDialog = false
                        shareDialogData = null
                    },
                )
            }
        }
    }
}


// ──────────────────────────────────────────────────────────────────────
// Line-level composable: renders words with fluid fill animation
// ──────────────────────────────────────────────────────────────────────

@Composable
internal fun LyricsMiniKaraokeLine(
    words: List<WordTimestamp>,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    if (words.isEmpty()) return
    LyricsLineV2(
        words = words,
        isActive = true,
        isPast = false,
        currentPositionMs = positionMs,
        textColor = Color.White,
        inactiveAlpha = 0.42f,
        baseFontSize = 15f,
        isLineAllBackground = words.all { it.isBackground || it.text.isBlank() },
        textAlign = TextAlign.Start,
        lyricsFontFamily = null,
        modifier = modifier,
        singleLine = true,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsLineV2(
    words: List<WordTimestamp>,
    isActive: Boolean,
    isPast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    baseFontSize: Float,
    isLineAllBackground: Boolean,
    textAlign: TextAlign,
    lyricsFontFamily: FontFamily?,
    modifier: Modifier = Modifier,
    // The compact strip above the seek bar has room for exactly one line.
    // FlowRow wraps to a second row once words stop fitting the available
    // width, which is correct for the full lyrics screen but not for that
    // strip. In single-line mode words lay out in a plain non-wrapping Row
    // and anything past the visible width is clipped away — like Spotify's
    // mini lyric line — instead of dropping to a second row underneath.
    singleLine: Boolean = false,
) {
    // FlowRow adds no gap between items on its own, and some lyrics providers
    // never send an explicit " " word token (or any baked-in space) between
    // consecutive words — those songs ended up with every word jammed flush
    // against the next one. A real arrangement gap guarantees a visible gap
    // between words on every song regardless of what the provider sent, so
    // explicit " " tokens below are now just skipped instead of rendered as
    // their own transparent item (rendering both would double the gap).
    val wordGap = (baseFontSize * 0.22f).dp
    val arrangement = when (textAlign) {
        TextAlign.Center -> Arrangement.spacedBy(wordGap, Alignment.CenterHorizontally)
        TextAlign.End -> Arrangement.spacedBy(wordGap, Alignment.End)
        else -> Arrangement.spacedBy(wordGap, Alignment.Start)
    }

    // Split words into main and background
    val mainWords = words.filter { !it.isBackground }
    val bgWords = words.filter { it.isBackground }

    // 1. Render main words First (if any)
    if (mainWords.isNotEmpty()) {
        val mainWordsContent: @Composable () -> Unit = {
            mainWords.forEachIndexed { wordIndex, word ->
                if (word.text == " ") {
                    return@forEachIndexed
                }
                if (word.text == "\n") {
                    if (!singleLine) Spacer(modifier = Modifier.fillMaxWidth())
                    return@forEachIndexed
                }

                AnimatedWordV2(
                    word = word,
                    wordIndex = wordIndex,
                    isLineActive = isActive,
                    isLinePast = isPast,
                    currentPositionMs = currentPositionMs,
                    textColor = textColor,
                    inactiveAlpha = inactiveAlpha,
                    fontSize = if (isLineAllBackground) baseFontSize * 0.82f else baseFontSize,
                    isBackground = isLineAllBackground,
                    lyricsFontFamily = lyricsFontFamily,
                )
            }
        }
        if (singleLine) {
            // A Row of individually animated word Texts cannot apply TextOverflow.Ellipsis
            // to the whole sentence. Measure the complete line first, then keep the longest
            // prefix that leaves room for a real ellipsis. This keeps word-level karaoke
            // animation while making timestamped lyrics truncate exactly like plain lyrics.
            BoxWithConstraints(
                modifier = modifier
                    .fillMaxWidth()
                    // Give the floating word a little vertical headroom. The
                    // parent strip intentionally does not clip this 36dp draw
                    // area, so the bounce remains visible on compact players.
                    .requiredHeight(36.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart,
            ) {
                val textMeasurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val availableWidthPx = with(density) { maxWidth.toPx() }
                val gapPx = with(density) { wordGap.toPx() }
                val measureStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = baseFontSize.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = (baseFontSize * 1.35f).sp,
                    fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                )
                val visibleCandidates = remember(mainWords, availableWidthPx, baseFontSize, measureStyle) {
                    val candidates = mainWords.filter { it.text != " " && it.text != "\n" }
                    val style = measureStyle
                    val fullText = candidates.joinToString("") { it.text }
                    val fullWidth = textMeasurer.measure(fullText, style = style).size.width +
                        (candidates.size - 1).coerceAtLeast(0) * gapPx
                    if (fullWidth <= availableWidthPx) {
                        candidates to false
                    } else {
                        var bestCount = 0
                        for (count in 1..candidates.size) {
                            val prefix = candidates.take(count).joinToString("") { it.text }
                            val measured = textMeasurer.measure("$prefix…", style = style).size.width +
                                (count.coerceAtLeast(1)) * gapPx
                            if (measured <= availableWidthPx) bestCount = count else break
                        }
                        candidates.take(bestCount) to true
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clipToBounds(),
                    horizontalArrangement = arrangement,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    visibleCandidates.first.forEachIndexed { wordIndex, word ->
                        AnimatedWordV2(
                            word = word,
                            wordIndex = wordIndex,
                            isLineActive = isActive,
                            isLinePast = isPast,
                            currentPositionMs = currentPositionMs,
                            textColor = textColor,
                            inactiveAlpha = inactiveAlpha,
                            fontSize = if (isLineAllBackground) baseFontSize * 0.82f else baseFontSize,
                            isBackground = isLineAllBackground,
                            lyricsFontFamily = lyricsFontFamily,
                        )
                    }
                    if (visibleCandidates.second) {
                        Text(
                            text = "…",
                            color = textColor,
                            fontSize = baseFontSize.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        } else {
            FlowRow(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = arrangement,
            ) { mainWordsContent() }
        }
    }

    // 2. Render background words explicitly on a NEW line, noticeably smaller
    //    (skipped in single-line mode: a second row has nowhere to go there)
    if (bgWords.isNotEmpty() && !singleLine) {
        val spacerHeight = if (mainWords.isNotEmpty()) 4.dp else 0.dp
        if (mainWords.isNotEmpty()) Spacer(modifier = Modifier.height(spacerHeight))

        FlowRow(
            modifier = Modifier.fillMaxWidth().alpha(0.85f), // Slightly dimmer overall
            horizontalArrangement = arrangement,
        ) {
            bgWords.forEachIndexed { wordIndex, word ->
                if (word.text == " ") {
                    return@forEachIndexed
                }

                AnimatedWordV2(
                    word = word,
                    wordIndex = wordIndex + mainWords.size,
                    isLineActive = isActive,
                    isLinePast = isPast,
                    currentPositionMs = currentPositionMs,
                    textColor = textColor,
                    inactiveAlpha = inactiveAlpha,
                    fontSize = baseFontSize * 0.65f, // ~65% size of main text
                    isBackground = true, // Force dimmer styling inside AnimatedWordV2
                    lyricsFontFamily = lyricsFontFamily,
                )
            }
        }
    }
}


// ──────────────────────────────────────────────────────────────────────
// Word-level composable: liquid fill sweep + glow + bounce
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedWordV2(
    word: WordTimestamp,
    wordIndex: Int,
    isLineActive: Boolean,
    isLinePast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    fontSize: Float,
    isBackground: Boolean,
    lyricsFontFamily: FontFamily?,
) {
    val wordStartMs = (word.startTime * 1000).toLong()
    val wordEndMs = (word.endTime * 1000).toLong()
    val wordDuration = (wordEndMs - wordStartMs).coerceAtLeast(1L)

    val isWordComplete = currentPositionMs >= wordEndMs
    val isWordActive = currentPositionMs in wordStartMs until wordEndMs

    // Perfect linear progress [0..1] that matches individual word timings
    val progress = when {
        isWordComplete -> 1f
        currentPositionMs <= wordStartMs -> 0f
        word.syllables.isEmpty() ->
            ((currentPositionMs - wordStartMs).toFloat() / wordDuration).coerceIn(0f, 1f)
        else -> {
            var revealedChars = 0f
            for (part in word.syllables) {
                val partStart = (part.startTime * 1000.0).toLong()
                val partEnd = (part.endTime * 1000.0).toLong().coerceAtLeast(partStart + 1L)
                revealedChars = when {
                    currentPositionMs < partStart -> part.startChar.toFloat()
                    currentPositionMs >= partEnd -> part.endChar.toFloat()
                    else -> {
                        val fraction = ((currentPositionMs - partStart).toFloat() /
                            (partEnd - partStart).toFloat()).coerceIn(0f, 1f)
                        part.startChar + (part.endChar - part.startChar) * fraction
                    }
                }
                if (currentPositionMs < partEnd) break
            }
            (revealedChars / word.text.length.coerceAtLeast(1)).coerceIn(0f, 1f)
        }
    }

    // ── Bounce and Float animation ──
    // Subtle scale up peaking halfway through the word. Exact timing sync!
    val sinProgress = kotlin.math.sin(progress * kotlin.math.PI).toFloat()
    val wordScale = 1f + (0.015f * sinProgress)

    // Float is only applied when the word is actively sung, making it pop from the line.
    // We use animateFloatAsState so that when it finishes (and drops to 0f), 
    // it smoothly decays back into place rather than a harsh mathematical snap.
    val targetFloat = if (isWordActive) -4f * sinProgress else 0f
    val floatOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetFloat,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (isWordActive) 50 else 120,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        )
    )

    // ── Glow intensity ──
    // "lines and words that are done animating shouldnt continue to glow"
    // Make glow build up faster: reach max intensity at 50% progress
    val glowProgress = (progress * 2f).coerceAtMost(1f)
    val glowAlpha = if (isWordActive) glowProgress * 0.45f else 0f
    val glowRadius = if (isWordActive) glowProgress * 12f else 0f

    val actualFontSize = if (isBackground) fontSize * 0.85f else fontSize
    val fontWeight = FontWeight.SemiBold // Consistent weight — no thin→bold jump

    // ── Two-layer rendering: dim base + liquid fill overlay ──
    Box(
        modifier = Modifier
            .graphicsLayer {
                translationY = floatOffset * density
                scaleX = wordScale
                scaleY = wordScale
            }
    ) {
        // Layer 1: Base text (always dimmed)
        Text(
            text = word.text,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = actualFontSize.sp,
                fontWeight = fontWeight,
                fontStyle = FontStyle.Normal,
                lineHeight = (actualFontSize * 1.35f).sp,
                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            color = textColor.copy(alpha = if (isBackground) inactiveAlpha * 0.7f else inactiveAlpha),
        )

        // Layer 2: Filled overlay with liquid sweep mask + glow
        if (isWordComplete || isWordActive || isLinePast) {
            Text(
                text = word.text,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = actualFontSize.sp,
                    fontWeight = fontWeight,
                    fontStyle = FontStyle.Normal,
                    lineHeight = (actualFontSize * 1.35f).sp,
                    fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                    shadow = if (glowAlpha > 0f) {
                        Shadow(
                            color = textColor.copy(alpha = glowAlpha),
                            offset = Offset.Zero,
                            blurRadius = glowRadius.coerceAtLeast(1f),
                        )
                    } else null,
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                color = textColor.copy(
                    alpha = if (isBackground) 0.75f else 1f
                ),
                modifier = if (isWordActive && !isWordComplete) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val edgeWidth = 8.dp.toPx()
                            val center = (size.width + edgeWidth * 2) * progress - edgeWidth
                            drawRect(
                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startX = center - edgeWidth,
                                    endX = center + edgeWidth,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                } else {
                    Modifier
                }
            )
        }
    }
}

