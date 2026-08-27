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
import com.chrisgrou.fbfeedwrapper.settings.DebugScreen
import com.chrisgrou.fbfeedwrapper.settings.DebugToggles
import com.chrisgrou.fbfeedwrapper.settings.FeedDisplayPreferences
import com.chrisgrou.fbfeedwrapper.settings.SettingsScreen
import com.chrisgrou.fbfeedwrapper.settings.SettingsViewModel
import com.chrisgrou.fbfeedwrapper.settings.TabIconsScreen
import com.chrisgrou.fbfeedwrapper.settings.TabPreferences
import com.chrisgrou.fbfeedwrapper.sync.SourceSyncScreen
import com.chrisgrou.fbfeedwrapper.update.UpdateViewModel
import org.json.JSONTokener

private const val FEED_URL = "https://m.facebook.com"
private const val WEBVIEW_STATE_KEY = "webview_state"
private const val REFRESH_FILTER_JS = "window.__ffwRefreshAllowed && window.__ffwRefreshAllowed();"
private const val REFRESH_DISPLAY_JS = "window.__ffwRefreshDisplay && window.__ffwRefreshDisplay();"
private const val REFRESH_TABS_JS = "window.__ffwRefreshTabs && window.__ffwRefreshTabs();"

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
                onDebugDump = ::captureDebugDump,
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val state = Bundle()
        webView?.saveState(state)
        outState.putBundle(WEBVIEW_STATE_KEY, state)
    }

    // Held here (Activity-scoped), not in FbWebViewScreen's own composable state, so
    // it's still callable from the Debug menu inside Settings — a different screen,
    // where FbWebViewScreen (and any local WebView reference it held) isn't part of
    // the composition. Compose doesn't destroy the underlying WebView just because
    // AndroidView left composition (nothing here calls WebView.destroy()), so this
    // reference and evaluateJavascript on it both stay valid across screens — the
    // same assumption onSaveInstanceState above already relies on.
    private fun captureDebugDump() {
        val web = webView ?: return
        web.evaluateJavascript(DUMP_FILTER_REPORT_JS) { reportResult ->
            val filterReport = runCatching { JSONTokener(reportResult).nextValue() as String }
                .getOrNull().orEmpty()
            web.evaluateJavascript(DUMP_NAV_REPORT_JS) { navResult ->
                val navReport = runCatching { JSONTokener(navResult).nextValue() as String }
                    .getOrNull().orEmpty()
                val report = listOf(filterReport, navReport).filter { it.isNotBlank() }.joinToString("\n\n")
                web.evaluateJavascript(DUMP_VIEWPORT_HTML_JS) { htmlResult ->
                    val html = runCatching { JSONTokener(htmlResult).nextValue() as String }
                        .getOrNull().orEmpty()
                    if (report.isBlank() && html.isBlank()) {
                        Toast.makeText(this, "Δεν βρέθηκε περιεχόμενο", Toast.LENGTH_SHORT).show()
                        return@evaluateJavascript
                    }
                    shareHtmlDump(this, report, html)
                }
            }
        }
    }
}

private enum class Screen { Feed, Settings, Sync, AllowedSources, Debug, TabIcons }

@Composable
private fun App(
    restoredState: Bundle?,
    onWebViewCreated: (WebView) -> Unit,
    onDebugDump: () -> Unit,
) {
    var screen by remember { mutableStateOf(Screen.Feed) }
    // Activity-scoped, so results (list edits, sync imports, an update check) land
    // wherever the app navigates back to afterwards.
    val settingsViewModel: SettingsViewModel = viewModel()
    val updateViewModel: UpdateViewModel = viewModel()
    val context = LocalContext.current
    val debugToggles = remember { DebugToggles(context) }
    val displayPreferences = remember { FeedDisplayPreferences(context) }
    val tabPreferences = remember { TabPreferences(context) }

    when (screen) {
        Screen.Feed -> FbWebViewScreen(
            restoredState = restoredState,
            onWebViewCreated = onWebViewCreated,
            onOpenSettings = { screen = Screen.Settings },
            onDebugDump = onDebugDump,
            settingsViewModel = settingsViewModel,
            debugToggles = debugToggles,
            displayPreferences = displayPreferences,
            tabPreferences = tabPreferences,
        )
        Screen.Settings -> SettingsScreen(
            onBack = { screen = Screen.Feed },
            onOpenSync = { screen = Screen.Sync },
            onOpenAllowedSources = { screen = Screen.AllowedSources },
            onOpenDebug = { screen = Screen.Debug },
            onOpenTabIcons = { screen = Screen.TabIcons },
            settingsViewModel = settingsViewModel,
            updateViewModel = updateViewModel,
            displayPreferences = displayPreferences,
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
        Screen.Debug -> DebugScreen(
            onBack = { screen = Screen.Settings },
            debugToggles = debugToggles,
        )
        Screen.TabIcons -> TabIconsScreen(
            onBack = { screen = Screen.Settings },
            tabPreferences = tabPreferences,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FbWebViewScreen(
    restoredState: Bundle?,
    onWebViewCreated: (WebView) -> Unit,
    onOpenSettings: () -> Unit,
    onDebugDump: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(),
    debugToggles: DebugToggles,
    displayPreferences: FeedDisplayPreferences,
    tabPreferences: TabPreferences,
) {
    val context = LocalContext.current
    val filterBridge = remember { FeedFilterBridge() }
    val scrollBridge = remember { ScrollPositionBridge(context) }
    val navBridge = remember { NavigationBridge(onOpenSettings = onOpenSettings) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    // Gates the debug stats banner below. Not canGoBack: this client evidently pushes
    // "screens" (opening a post, its Replies, ...) via its own internal mechanism
    // rather than always through the browser's URL/history APIs — an on-device
    // capture showed WebView.canGoBack() getting stuck true after returning from a
    // post. document.title changes reliably per screen ("Facebook" on the main feed,
    // "Replies"/a post's own title elsewhere — see feed_filter.js's isFeedPage(),
    // which hit the same problem with the URL and was fixed the same way), so track
    // that instead via WebChromeClient.onReceivedTitle.
    var isBaseFeed by remember { mutableStateOf(true) }
    val allowedPages by settingsViewModel.allowedPages.collectAsState()
    val filterStats by filterBridge.stats.collectAsState()
    val statsBannerEnabled by debugToggles.statsBannerEnabled.collectAsState()
    val debugButtonEnabled by debugToggles.debugButtonEnabled.collectAsState()
    val hideReactions by displayPreferences.hideReactions.collectAsState()
    val hideSuggested by displayPreferences.hideSuggested.collectAsState()
    val hiddenTabs by tabPreferences.hiddenTabs.collectAsState()

    // Re-applies the filter in the already-loaded page whenever the user
    // edits the allowed-pages list in Settings.
    LaunchedEffect(allowedPages) {
        filterBridge.allowedAuthors = allowedPages
        webViewRef?.evaluateJavascript(REFRESH_FILTER_JS, null)
    }

    // Re-applies feed_display.js's hide/show state whenever the user flips a display
    // toggle in Settings — including the first composition after navigating back to
    // the feed from there, which is what actually picks up a change made while this
    // screen wasn't on screen to react to it live.
    LaunchedEffect(hideReactions, hideSuggested) {
        webViewRef?.evaluateJavascript(REFRESH_DISPLAY_JS, null)
    }

    // Re-applies tab_visibility.js's hide/show state (and relocates the Settings
    // overlay to the newly freed slot) whenever the user checks/unchecks a top-bar
    // icon in Settings — same shape as the two effects above.
    LaunchedEffect(hiddenTabs) {
        webViewRef?.evaluateJavascript(REFRESH_TABS_JS, null)
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
                    addJavascriptInterface(displayPreferences, "NativeDisplay")
                    addJavascriptInterface(tabPreferences, "NativeTabs")
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

        // The floating Settings icon moved out (reachable via nav_override.js's
        // overlay in Facebook's own top tab bar instead — see TabIconsScreen for
        // where the user picks which native icon's slot it uses). The debug-capture
        // icon stays floating, though — its whole point is capturing whatever screen
        // the bug is actually on (e.g. the Replies pagination issue), which a button
        // buried inside Settings can't do since navigating there leaves that screen.
        // Toggleable now (Settings → Debug) rather than always shown.
        if (BuildConfig.DEBUG && debugButtonEnabled) {
            IconButton(
                onClick = onDebugDump,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Debug: αποστολή ορατού HTML",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Debug-only: live filter counts, since console.log (see feed_filter.js)
        // isn't visible without a desktop chrome://inspect connection. Also
        // toggleable now (Settings → Debug), since it's a permanent on-screen
        // overlay that isn't everyone's cup of tea to always have up.
        if (isBaseFeed && BuildConfig.DEBUG && statsBannerEnabled) {
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
