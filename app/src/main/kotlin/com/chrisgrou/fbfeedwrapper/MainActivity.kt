package com.chrisgrou.fbfeedwrapper

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
import androidx.compose.material.icons.filled.Settings
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
import com.chrisgrou.fbfeedwrapper.settings.DebugToggles
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
    val context = LocalContext.current
    val debugToggles = remember { DebugToggles(context) }

    when (screen) {
        Screen.Feed -> FbWebViewScreen(
            restoredState = restoredState,
            onWebViewCreated = onWebViewCreated,
            onOpenSettings = { screen = Screen.Settings },
            settingsViewModel = settingsViewModel,
            debugToggles = debugToggles,
        )
        Screen.Settings -> SettingsScreen(
            onBack = { screen = Screen.Feed },
            onOpenSync = { screen = Screen.Sync },
            onOpenAllowedSources = { screen = Screen.AllowedSources },
            settingsViewModel = settingsViewModel,
            updateViewModel = updateViewModel,
            debugToggles = debugToggles,
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
    debugToggles: DebugToggles,
) {
    val context = LocalContext.current
    val filterBridge = remember { FeedFilterBridge() }
    val scrollBridge = remember { ScrollPositionBridge(context) }
    val navBridge = remember { NavigationBridge(onOpenSettings = onOpenSettings) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    // Gates the floating Settings icon. Not canGoBack: this client evidently pushes
    // "screens" (opening a post, its Replies, ...) via its own internal mechanism
    // rather than always through the browser's URL/history APIs — an on-device
    // capture showed WebView.canGoBack() getting stuck true after returning from a
    // post, permanently hiding the icon. document.title changes reliably per screen
    // ("Facebook" on the main feed, "Replies"/a post's own title elsewhere — see
    // feed_filter.js's isFeedPage(), which hit the same problem with the URL and was
    // fixed the same way), so track that instead via WebChromeClient.onReceivedTitle.
    var isBaseFeed by remember { mutableStateOf(true) }
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
                    addJavascriptInterface(debugToggles, "NativeFlags")
                    addJavascriptInterface(navBridge, "NativeNav")
                    webViewClient = FbWebViewClient(onHistoryChanged = { view ->
                        canGoBack = view.canGoBack()
                    })
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView, title: String?) {
                            isBaseFeed = title == "Facebook"
                        }
                    }

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

        // Debug-only developer tool, not a user-facing option — unlike the Settings
        // icon below, it's kept reachable on every screen (not just !canGoBack), since
        // diagnosing a bug (e.g. the Replies pagination issue) often means capturing
        // from exactly the subpage where it happens.
        if (BuildConfig.DEBUG) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                IconButton(onClick = {
                    val web = webViewRef ?: return@IconButton
                    web.evaluateJavascript(DUMP_FILTER_REPORT_JS) { reportResult ->
                        val filterReport = runCatching { JSONTokener(reportResult).nextValue() as String }
                            .getOrNull().orEmpty()
                        web.evaluateJavascript(DUMP_NAV_REPORT_JS) { navResult ->
                            val navReport = runCatching { JSONTokener(navResult).nextValue() as String }
                                .getOrNull().orEmpty()
                            val report = listOf(filterReport, navReport)
                                .filter { it.isNotBlank() }.joinToString("\n\n")
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

        // Only shown at the top level of the feed, not while the user has navigated
        // into a photo/video/post/comments view: those have their own close, share,
        // and reaction controls at every edge of the screen (confirmed by an on-device
        // screenshot — our top-right icons sat directly on the photo viewer's own
        // controls there), and there is no corner that's safe across every such view.
        // isBaseFeed is exactly "have we navigated away from the feed", so it doubles
        // as the signal for this.
        //
        // This — a floating icon — is deliberately back to the original design:
        // anchoring Settings to Facebook's own Marketplace tab (nav_override.js,
        // since removed) went through three different approaches, each surfacing a
        // new failure mode (a mutation that broke Facebook's own React reconciler on
        // refresh; an overlay that depended on Facebook's own scroll-triggered
        // show/hide behavior for its top tab bar, which sometimes never came back).
        // That's Facebook's own client behavior to chase, not something reliably
        // fixable from outside it — this known-working baseline is worth more than
        // another attempt.
        if (isBaseFeed) {
            // Only the settings icon on the front screen — updating, syncing, and
            // editing the allow-list all live inside Settings now.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = if (BuildConfig.DEBUG) 56.dp else 8.dp, end = 8.dp),
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Ρυθμίσεις",
                        tint = MaterialTheme.colorScheme.primary,
                    )
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
