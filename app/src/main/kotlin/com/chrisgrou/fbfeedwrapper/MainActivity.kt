package com.chrisgrou.fbfeedwrapper

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisgrou.fbfeedwrapper.debug.DUMP_FILTER_REPORT_JS
import com.chrisgrou.fbfeedwrapper.debug.DUMP_NAV_REPORT_JS
import com.chrisgrou.fbfeedwrapper.debug.DUMP_VIEWPORT_HTML_JS
import com.chrisgrou.fbfeedwrapper.debug.shareHtmlDump
import com.chrisgrou.fbfeedwrapper.filter.FeedFilterBridge
import com.chrisgrou.fbfeedwrapper.nav.NavigationBridge
import com.chrisgrou.fbfeedwrapper.scroll.ScrollPositionBridge
import com.chrisgrou.fbfeedwrapper.settings.AllowedSourcesScreen
import com.chrisgrou.fbfeedwrapper.settings.SettingsScreen
import com.chrisgrou.fbfeedwrapper.settings.SettingsViewModel
import com.chrisgrou.fbfeedwrapper.sync.SourceSyncScreen
import com.chrisgrou.fbfeedwrapper.update.UpdateViewModel
import org.json.JSONTokener

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

private enum class Screen { Feed, Settings, Sync, AllowedSources }

@Composable
private fun App(
    restoredState: Bundle?,
    onWebViewCreated: (WebView) -> Unit,
) {
    var screen by remember { mutableStateOf(Screen.Feed) }
    // Activity-scoped, so results (list edits, sync imports, an update check) land
    // wherever the app navigates back to afterwards.
    val settingsViewModel: SettingsViewModel = viewModel()
    val updateViewModel: UpdateViewModel = viewModel()

    when (screen) {
        Screen.Feed -> FbWebViewScreen(
            restoredState = restoredState,
            onWebViewCreated = onWebViewCreated,
            onOpenSettings = { screen = Screen.Settings },
            settingsViewModel = settingsViewModel,
        )
        Screen.Settings -> SettingsScreen(
            onBack = { screen = Screen.Feed },
            onOpenSync = { screen = Screen.Sync },
            onOpenAllowedSources = { screen = Screen.AllowedSources },
            settingsViewModel = settingsViewModel,
            updateViewModel = updateViewModel,
        )
        Screen.Sync -> SourceSyncScreen(
            onCancel = { screen = Screen.Settings },
            onConfirm = { names ->
                settingsViewModel.addPages(names)
                screen = Screen.Settings
            },
        )
        Screen.AllowedSources -> AllowedSourcesScreen(
            onBack = { screen = Screen.Settings },
            settingsViewModel = settingsViewModel,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FbWebViewScreen(
    restoredState: Bundle?,
    onWebViewCreated: (WebView) -> Unit,
    onOpenSettings: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val filterBridge = remember { FeedFilterBridge() }
    val scrollBridge = remember { ScrollPositionBridge(context) }
    val navBridge = remember { NavigationBridge(onOpenSettings = onOpenSettings) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    val allowedPages by settingsViewModel.allowedPages.collectAsState()
    val filterStats by filterBridge.stats.collectAsState()

    // Re-applies the filter in the already-loaded page whenever the user
    // edits the allowed-pages list in Settings.
    LaunchedEffect(allowedPages) {
        filterBridge.allowedAuthors = allowedPages
        webViewRef?.evaluateJavascript(REFRESH_FILTER_JS, null)
    }

    // Without this, the system back gesture has nothing registered to intercept it, so
    // it falls straight through to the default (exit the app) instead of stepping back
    // through the page the user was just on — opening a post, going back, and getting
    // dumped on the Android home screen instead of the feed.
    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
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
                    addJavascriptInterface(scrollBridge, "NativeScroll")
                    addJavascriptInterface(navBridge, "NativeNav")
                    webViewClient = FbWebViewClient(onHistoryChanged = { view ->
                        canGoBack = view.canGoBack()
                    })

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

        // Settings is reached through Facebook's own tab bar now (its Marketplace tab,
        // relabelled — see nav_override.js/NavigationBridge), not a floating overlay:
        // that tab bar survives Facebook's own pull-to-refresh navigation, whereas the
        // old canGoBack-based overlay icon didn't (pull-to-refresh flipped canGoBack
        // and never flipped it back, permanently hiding the icon even on the base feed).
        //
        // The debug capture icon below is still gated on canGoBack, since it's only
        // useful at the top level of the feed: a photo/video/post/comments view has its
        // own close, share, and reaction controls at every edge of the screen (confirmed
        // by an on-device screenshot — this icon sat directly on the photo viewer's own
        // controls there), and there is no corner that's safe across every such view.
        if (!canGoBack) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                // Debug-only developer tool, not a user-facing option: it needs the
                // live feed WebView, which only exists on this screen, so it stays
                // here rather than moving into Settings with everything else.
                if (BuildConfig.DEBUG) {
                    IconButton(onClick = {
                        val web = webViewRef ?: return@IconButton
                        web.evaluateJavascript(DUMP_FILTER_REPORT_JS) { reportResult ->
                            val filterReport = runCatching { JSONTokener(reportResult).nextValue() as String }
                                .getOrNull().orEmpty()
                            web.evaluateJavascript(DUMP_NAV_REPORT_JS) { navResult ->
                                val navReport = runCatching { JSONTokener(navResult).nextValue() as String }
                                    .getOrNull().orEmpty()
                                val report = listOf(filterReport, navReport).filter { it.isNotBlank() }
                                    .joinToString("\n\n")
                                web.evaluateJavascript(DUMP_VIEWPORT_HTML_JS) { htmlResult ->
                                    val html = runCatching { JSONTokener(htmlResult).nextValue() as String }
                                        .getOrNull().orEmpty()
                                    if (report.isBlank() && html.isBlank()) {
                                        Toast.makeText(context, "Δεν βρέθηκε περιεχόμενο", Toast.LENGTH_SHORT).show()
                                        return@evaluateJavascript
                                    }
                                    shareHtmlDump(context, report, html)
                                }
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Debug: αποστολή ορατού HTML",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Debug-only: live filter counts, since console.log (see feed_filter.js)
            // isn't visible without a desktop chrome://inspect connection.
            if (BuildConfig.DEBUG) {
                Text(
                    text = filterStats?.let {
                        "posts=${it.rowsMatched} src=${it.authorsResolved} hid=${it.hiddenCount} " +
                            "ok=${it.verifiedHidden} leak=${it.unresolvedVisible} gapfix=${it.gapsCollapsed} " +
                            "allow=${allowedPages.size}"
                    } ?: "filter: no data yet",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(8.dp),
                )
            }
        }
    }
}
