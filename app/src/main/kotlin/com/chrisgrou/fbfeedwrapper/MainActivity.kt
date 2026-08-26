package com.chrisgrou.fbfeedwrapper

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisgrou.fbfeedwrapper.filter.FeedFilterBridge
import com.chrisgrou.fbfeedwrapper.settings.SettingsScreen
import com.chrisgrou.fbfeedwrapper.settings.SettingsViewModel
import com.chrisgrou.fbfeedwrapper.update.UpdateDialogHost
import com.chrisgrou.fbfeedwrapper.update.UpdateViewModel

private const val FEED_URL = "https://m.facebook.com"
private const val WEBVIEW_STATE_KEY = "webview_state"
private const val REFRESH_FILTER_JS = "window.__ffwRefreshAllowed && window.__ffwRefreshAllowed();"

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var restoredState: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredState = savedInstanceState?.getBundle(WEBVIEW_STATE_KEY)

        setContent {
            App(
                restoredState = restoredState,
                onWebViewCreated = { webView = it },
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val state = Bundle()
        webView?.saveState(state)
        outState.putBundle(WEBVIEW_STATE_KEY, state)
    }
}

private enum class Screen { Feed, Settings }

@Composable
private fun App(
    restoredState: Bundle?,
    onWebViewCreated: (WebView) -> Unit,
) {
    var screen by remember { mutableStateOf(Screen.Feed) }

    when (screen) {
        Screen.Feed -> FbWebViewScreen(
            restoredState = restoredState,
            onWebViewCreated = onWebViewCreated,
            onOpenSettings = { screen = Screen.Settings },
        )
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Feed })
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FbWebViewScreen(
    restoredState: Bundle?,
    onWebViewCreated: (WebView) -> Unit,
    onOpenSettings: () -> Unit,
    updateViewModel: UpdateViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val filterBridge = remember { FeedFilterBridge() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val allowedPages by settingsViewModel.allowedPages.collectAsState()

    // Re-applies the filter in the already-loaded page whenever the user
    // edits the allowed-pages list in Settings.
    LaunchedEffect(allowedPages) {
        filterBridge.allowedAuthors = allowedPages
        webViewRef?.evaluateJavascript(REFRESH_FILTER_JS, null)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                // Lets `chrome://inspect` on a USB-connected desktop attach DevTools to
                // this WebView's live, authenticated session — the only practical way to
                // read m.facebook.com's real DOM and fix the feed_filter.js selectors.
                if (BuildConfig.DEBUG) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }

                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    // Persistent session: cookies are never cleared on close, so the
                    // user stays logged in across app restarts.
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    addJavascriptInterface(filterBridge, "NativeFilter")
                    webViewClient = FbWebViewClient()

                    onWebViewCreated(this)
                    webViewRef = this

                    if (restoredState != null) {
                        restoreState(restoredState)
                    } else {
                        loadUrl(FEED_URL)
                    }
                }
            },
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Ρυθμίσεις φιλτραρίσματος",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = updateViewModel::checkForUpdate) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = "Έλεγχος για ενημερώσεις",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        UpdateDialogHost(updateViewModel)
    }
}
