package com.devxrohan.smusic.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.devxrohan.smusic.R
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.constants.AccountChannelHandleKey
import com.devxrohan.smusic.constants.AccountEmailKey
import com.devxrohan.smusic.constants.AccountNameKey
import com.devxrohan.smusic.constants.AccountPhotoUrlKey
import com.devxrohan.smusic.constants.InnerTubeCookieKey
import com.devxrohan.smusic.constants.VisitorDataKey
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.utils.parseCookieString
import com.devxrohan.smusic.ui.component.IconButton
import com.devxrohan.smusic.ui.utils.backToMain
import com.devxrohan.smusic.utils.rememberPreference
import com.devxrohan.smusic.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.utils.dataStore
import kotlinx.coroutines.withContext

private const val YOUTUBE_MUSIC_URL = "https://music.youtube.com/"
private const val MAX_RETRY_ATTEMPTS = 3
private const val RETRY_DELAY_MS = 1000L

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeLoginScreen(navController: NavController, onScreenReady: () -> Unit = {}) {
    androidx.compose.runtime.SideEffect { onScreenReady() }
    val coroutineScope = rememberCoroutineScope()
    var visitorData by rememberPreference(VisitorDataKey, "")
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var accountName by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
    var accountPhotoUrl by rememberPreference(AccountPhotoUrlKey, "")

    val context = androidx.compose.ui.platform.LocalContext.current
    val database = LocalDatabase.current

    var webView: WebView? = null
    var isLoadingAccountInfo by remember { mutableStateOf(false) }
    var isAccountHandled by remember { mutableStateOf(false) }

    suspend fun fetchAccountInfoWithRetry(retryCount: Int = 0) {
        if (isAccountHandled) return
        try {
            val result = withContext(Dispatchers.IO) {
                YouTube.accountInfo()
            }
            result.onSuccess { accountInfo ->
                val name = accountInfo.name.takeIf { it.isNotBlank() } ?: ""
                val email = accountInfo.email?.takeIf { it.isNotBlank() } ?: ""
                val handle = accountInfo.channelHandle?.takeIf { it.isNotBlank() } ?: ""
                val photoUrl = accountInfo.photoUrl?.takeIf { it.startsWith("https://") } ?: ""

                withContext(Dispatchers.Main) {
                    if (isAccountHandled) return@withContext
                    if (name.isNotEmpty()) {
                        isAccountHandled = true
                        // Persist the whole profile in one atomic write that survives this screen leaving
                        // composition. The old per-field setters ran in this screen's coroutine scope, and
                        // finishLoginNavigation() (instant when started from onboarding) cancelled it before
                        // the last field (the profile photo) was written.
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                            context.dataStore.edit { prefs ->
                                prefs[AccountNameKey] = name
                                prefs[AccountEmailKey] = email
                                prefs[AccountChannelHandleKey] = handle
                                prefs[AccountPhotoUrlKey] = photoUrl
                            }
                        }
                        com.devxrohan.smusic.utils.dataStoreCache[AccountNameKey.name] = name
                        com.devxrohan.smusic.utils.dataStoreCache[AccountEmailKey.name] = email
                        com.devxrohan.smusic.utils.dataStoreCache[AccountChannelHandleKey.name] = handle
                        com.devxrohan.smusic.utils.dataStoreCache[AccountPhotoUrlKey.name] = photoUrl
                        isLoadingAccountInfo = false

                        // Sync the retrieved account profile into SMusic's local name/email store
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            val namePrefManager = com.devxrohan.smusic.ui.component.NamePreferenceManager(context)
                            if (email.isNotBlank()) {
                                namePrefManager.saveAccountEmail(email)
                                namePrefManager.rememberGoogleLoginEmail(email)
                            }
                            if (name.isNotBlank()) {
                                namePrefManager.saveUserName(name)
                            }
                        }

                        finishLoginNavigation(navController)
                    } else {
                        if (retryCount < MAX_RETRY_ATTEMPTS) {
                            delay(RETRY_DELAY_MS)
                            fetchAccountInfoWithRetry(retryCount + 1)
                        } else {
                            isAccountHandled = true
                            isLoadingAccountInfo = false
                            finishLoginNavigation(navController)
                        }
                    }
                }
            }.onFailure { exception ->
                if (retryCount < MAX_RETRY_ATTEMPTS) {
                    delay(RETRY_DELAY_MS)
                    fetchAccountInfoWithRetry(retryCount + 1)
                } else {
                    reportException(exception)
                    withContext(Dispatchers.Main) {
                        if (!isAccountHandled) {
                            isAccountHandled = true
                            isLoadingAccountInfo = false
                            finishLoginNavigation(navController)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            reportException(e)
            withContext(Dispatchers.Main) {
                if (!isAccountHandled) {
                    isAccountHandled = true
                    isLoadingAccountInfo = false
                    finishLoginNavigation(navController)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isLoadingAccountInfo) {
                            stringResource(R.string.login) + " - Loading..."
                        } else {
                            stringResource(R.string.login)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (url != null && url.startsWith(YOUTUBE_MUSIC_URL)) {
                                val youTubeCookieString = CookieManager.getInstance().getCookie(url)
                                val parsedCookies = parseCookieString(youTubeCookieString)

                                if ("SAPISID" in parsedCookies) {
                                    if (!isLoadingAccountInfo) {
                                        isLoadingAccountInfo = true
                                        // Connecting merges the account into the existing profile; nothing local is cleared.
                                        coroutineScope.launch(Dispatchers.IO) {
                                            withContext(Dispatchers.Main) {
                                                // Activate the exact cookie captured from WebView before
                                                // querying accountInfo; otherwise the request can run
                                                // against a stale/empty in-memory YouTube session.
                                                innerTubeCookie = youTubeCookieString
                                                YouTube.cookie = youTubeCookieString
                                                CookieManager.getInstance().flush()
                                                delay(300)
                                                fetchAccountInfoWithRetry()
                                            }
                                        }
                                    }
                                    loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                                } else if (!isLoadingAccountInfo) {
                                    innerTubeCookie = ""
                                }
                            }
                        }
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    // If no SMusic session is saved, do not inherit a stale in-memory app cookie.
                    if (innerTubeCookie.isBlank()) YouTube.cookie = null
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onRetrieveVisitorData(newVisitorData: String?) {
                                if (innerTubeCookie.isEmpty() && !isLoadingAccountInfo) {
                                    visitorData = ""
                                    return
                                }
                                if (!newVisitorData.isNullOrBlank()) {
                                    visitorData = newVisitorData
                                }
                            }
                        },
                        "Android",
                    )
                    webView = this
                    loadUrl(
                        "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=$YOUTUBE_MUSIC_URL",
                    )
                }
            },
        )
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}


/**
 * Remove both the WebView login route and its Sign in/Cancel entry screen from
 * the back stack after a valid YouTube session has been captured.
 */
private fun finishLoginNavigation(navController: NavController) {
    navController.navigate("home") {
        popUpTo(navController.graph.id) {
            inclusive = false
        }
        launchSingleTop = true
    }
}
