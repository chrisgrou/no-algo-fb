package com.chrisgrou.fbfeedwrapper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisgrou.fbfeedwrapper.debug.DUMP_FILTER_REPORT_JS
import com.chrisgrou.fbfeedwrapper.debug.DUMP_NAV_REPORT_JS
import com.chrisgrou.fbfeedwrapper.debug.DUMP_VIEWPORT_HTML_JS
import com.chrisgrou.fbfeedwrapper.debug.shareHtmlDump
import com.chrisgrou.fbfeedwrapper.filter.FeedFilterBridge
import com.chrisgrou.fbfeedwrapper.history.HistoryScreen
import com.chrisgrou.fbfeedwrapper.history.PostHistoryPreferences
import com.chrisgrou.fbfeedwrapper.media.MediaBridge
import com.chrisgrou.fbfeedwrapper.media.MediaDownloader
import com.chrisgrou.fbfeedwrapper.media.MediaLog
import com.chrisgrou.fbfeedwrapper.nav.NavigationBridge
import com.chrisgrou.fbfeedwrapper.scroll.ScrollPositionBridge
import com.chrisgrou.fbfeedwrapper.settings.AllowedSourcesScreen
import com.chrisgrou.fbfeedwrapper.settings.DebugScreen
import com.chrisgrou.fbfeedwrapper.settings.DebugToggles
import com.chrisgrou.fbfeedwrapper.settings.EnhancementsScreen
import com.chrisgrou.fbfeedwrapper.settings.FeedDisplayPreferences
import com.chrisgrou.fbfeedwrapper.settings.SettingsScreen
import com.chrisgrou.fbfeedwrapper.settings.SettingsViewModel
import com.chrisgrou.fbfeedwrapper.settings.TabIconsScreen
import com.chrisgrou.fbfeedwrapper.settings.TabPreferences
import com.chrisgrou.fbfeedwrapper.sync.SourceSyncScreen
import com.chrisgrou.fbfeedwrapper.update.UpdateViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

private const val FEED_URL = "https://m.facebook.com"
private const val WEBVIEW_STATE_KEY = "webview_state"
private const val REFRESH_FILTER_JS = "window.__ffwRefreshAllowed && window.__ffwRefreshAllowed();"
private const val REFRESH_DISPLAY_JS = "window.__ffwRefreshDisplay && window.__ffwRefreshDisplay();"
private const val REFRESH_TABS_JS = "window.__ffwRefreshTabs && window.__ffwRefreshTabs();"
private const val REFRESH_SCROLL_TOP_JS = "window.__ffwRefreshScrollTop && window.__ffwRefreshScrollTop();"
private const val REFRESH_POST_NAV_JS = "window.__ffwRefreshPostNav && window.__ffwRefreshPostNav();"
private const val CAPTURE_HISTORY_JS = "window.__ffwCaptureHistory && window.__ffwCaptureHistory();"
private const val SEARCH_URL_PREFIX = "https://m.facebook.com/search/top/?q="

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var restoredState: Bundle? = null
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Set once on a cold start from a tapped Facebook link (see the manifest's VIEW
    // intent-filter) and read once by App() to decide the very first page loaded —
    // separate from pendingExternalUrl below, which is Compose state read reactively on
    // every subsequent link tap while the Activity is already alive.
    private var startUrl: String = FEED_URL
    // Backed by Compose state (not a plain var) so App()'s LaunchedEffect actually
    // re-runs when singleTask hands this Activity a new VIEW intent for a link tapped
    // while the app was already running — a plain var wouldn't trigger recomposition.
    private var pendingExternalUrl by mutableStateOf<String?>(null)

    // Only ever needed below API 29 (see the manifest's own maxSdkVersion note) — a
    // launcher has to be registered before the Activity reaches STARTED, so this lives
    // here as a property rather than being created on demand inside saveImage().
    private var pendingImageAction: (() -> Unit)? = null
    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingImageAction
            pendingImageAction = null
            if (granted) action?.invoke()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredState = savedInstanceState?.getBundle(WEBVIEW_STATE_KEY)
        // A restored WebView state already has its own last-loaded URL, which
        // restoreState() below wins with regardless — this only matters for a genuine
        // cold start (restoredState == null), where it decides what loadUrl() sees
        // instead of the feed root.
        startUrl = urlFromIntent(intent) ?: FEED_URL

        setContent {
            App(
                restoredState = restoredState,
                startUrl = startUrl,
                pendingExternalUrl = pendingExternalUrl,
                onPendingExternalUrlHandled = { pendingExternalUrl = null },
                onWebViewCreated = { webView = it },
                onDebugDump = ::captureDebugDump,
                onSaveImage = ::saveImage,
                onSaveImageDataUrl = ::saveImageDataUrl,
                onImageResolveFailed = ::onImageResolveFailed,
            )
        }
    }

    // singleTask (see the manifest) means a second tap on a Facebook link while this
    // Activity is already running hands the URL here instead of going through
    // onCreate again — the WebView (and everything else) stays exactly as it was.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        urlFromIntent(intent)?.let { pendingExternalUrl = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val state = Bundle()
        webView?.saveState(state)
        outState.putBundle(WEBVIEW_STATE_KEY, state)
    }

    // Freezing the page while we're backgrounded is the second half of "come back to
    // the feed exactly where you left it" (resume_guard.js is the first): left running,
    // Facebook's own polling keeps ticking off-screen and can re-fetch the feed out
    // from under the user, so returning shows fresh content at the top even though the
    // WebView was never recreated. Paused, its JS simply picks up mid-thought.
    //
    // Note these fire on real backgrounding only — moving between the feed and Settings
    // is Compose navigation inside one Activity, which never reaches onPause.
    override fun onPause() {
        super.onPause()
        // Before anything is paused: records the topmost visible post (see
        // post_history.js) so Settings → Ιστορικό has a "what was I looking at" entry
        // for this backgrounding to show, even though scroll position itself isn't
        // recoverable (see scroll_position.js's own comment on why that was abandoned).
        webView?.evaluateJavascript(CAPTURE_HISTORY_JS, null)
        webView?.onPause()
        webView?.pauseTimers()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        webView?.resumeTimers()
    }

    // The WebView now outlives every composition (see App()), so nothing else would
    // ever release it — and it holds the whole rendered feed plus its JS heap.
    override fun onDestroy() {
        webView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        webView = null
        activityScope.cancel()
        super.onDestroy()
    }

    // Only android.intent.action.VIEW carries a link tapped elsewhere — MAIN (the
    // launcher icon) has no data, and this returning null for it is what keeps a plain
    // app-icon tap loading the normal feed instead of trying to "open" nothing.
    private fun urlFromIntent(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.toString()

    // Shared by saveImage() and saveImageDataUrl() below: both need the same
    // below-API-29 permission gate before touching MediaStore at all, differing only
    // in which suspend call they actually make once it's granted.
    private fun withStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            MediaLog.log("withStoragePermission requesting permission, SDK_INT=${Build.VERSION.SDK_INT}")
            pendingImageAction = action
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        MediaLog.log("withStoragePermission proceeding directly, SDK_INT=${Build.VERSION.SDK_INT}")
        action()
    }

    // Reachable from image_save.js's own long-press detection and from
    // WebView.setDownloadListener, both times for a plain http(s) image URL — see
    // MediaBridge.onImageUrl / MediaDownloader.saveImage.
    private fun saveImage(url: String) = withStoragePermission {
        MediaLog.log("saveImage url=" + url.take(80))
        Toast.makeText(this, "Αποθήκευση εικόνας...", Toast.LENGTH_SHORT).show()
        activityScope.launch { reportSaveResult(MediaDownloader.saveImage(this@MainActivity, url)) }
    }

    // The blob:/data: counterpart: image_save.js resolves those inside the page's own
    // JS context first (see its own comment on why a blob: URL can't be fetched from
    // here at all) and hands over the actual bytes as a data: URL instead — see
    // MediaBridge.onImageDataUrl / MediaDownloader.saveImageDataUrl.
    private fun saveImageDataUrl(dataUrl: String) = withStoragePermission {
        MediaLog.log("saveImageDataUrl length=" + dataUrl.length)
        Toast.makeText(this, "Αποθήκευση εικόνας...", Toast.LENGTH_SHORT).show()
        activityScope.launch { reportSaveResult(MediaDownloader.saveImageDataUrl(this@MainActivity, dataUrl)) }
    }

    // A long-press that found no image, or a blob:/data: resolve that failed inside the
    // page's own JS — see MediaBridge.onImageResolveFailed / image_save.js's own
    // comment on why a blob: URL specifically can fail this way (most likely already
    // revoked by Facebook's own code by the time this runs).
    private fun onImageResolveFailed(reason: String) {
        Toast.makeText(this, "Αποτυχία αποθήκευσης εικόνας: $reason", Toast.LENGTH_LONG).show()
    }

    // The reason, not just pass/fail, until this has actually been confirmed working
    // on-device — a bare "failed" gives no way to tell a network/CDN problem (see
    // MediaDownloader's own Referer/Cookie/User-Agent handling) apart from a MediaStore
    // one, or from the blob: URL case above, without another whole debug round trip.
    //
    // Logged via MediaLog now, not evaluateJavascript back into the page's own console:
    // on-device evidence ruled that path out — image_save.js confirmed the bridge call
    // itself succeeding every time, yet not one of the corresponding native-side lines
    // it was supposed to produce ever showed up in a capture. See MediaLog's own
    // comment for the full reasoning. MediaLog.dump() is read directly by
    // captureDebugDump() below instead, no WebView round trip involved.
    private fun reportSaveResult(result: Result<Uri>) {
        if (result.isSuccess) {
            MediaLog.log("reportSaveResult success uri=${result.getOrNull()}")
            Toast.makeText(this, "Η εικόνα αποθηκεύτηκε στη Συλλογή", Toast.LENGTH_SHORT).show()
        } else {
            val error = result.exceptionOrNull()
            MediaLog.log("reportSaveResult failed ${error?.javaClass?.simpleName}: ${error?.message}")
            Toast.makeText(
                this,
                "Αποτυχία αποθήκευσης εικόνας: ${error?.message}",
                Toast.LENGTH_LONG,
            ).show()
        }
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
                // Read directly, not through the WebView at all — see MediaLog's own
                // comment on why the previous attempt at this (evaluateJavascript back
                // into the page's own console) turned out to be unreliable.
                val mediaLog = MediaLog.dump().let { if (it.isBlank()) "" else "===== MEDIA LOG =====\n\n$it" }
                val report = listOf(filterReport, navReport, mediaLog).filter { it.isNotBlank() }.joinToString("\n\n")
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

private enum class Screen { Feed, Settings, Sync, AllowedSources, Debug, TabIcons, Enhancements, History }

@Composable
private fun App(
    restoredState: Bundle?,
    startUrl: String,
    pendingExternalUrl: String?,
    onPendingExternalUrlHandled: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onDebugDump: () -> Unit,
    onSaveImage: (String) -> Unit,
    onSaveImageDataUrl: (String) -> Unit,
    onImageResolveFailed: (String) -> Unit,
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
    val historyPreferences = remember { PostHistoryPreferences(context) }

    val filterBridge = remember { FeedFilterBridge() }
    val scrollBridge = remember { ScrollPositionBridge(context) }
    val navBridge = remember { NavigationBridge(onOpenSettings = { screen = Screen.Settings }) }
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

    // Built once here, for as long as this Activity lives — deliberately not inside
    // FbWebViewScreen's AndroidView factory, where it used to be. A factory lambda runs
    // again every time its AndroidView re-enters composition, so opening Settings and
    // coming back built a second WebView and loadUrl'd the feed from scratch (leaking
    // the first, which nothing ever destroyed): the user lost their place for the same
    // reason a backgrounded-and-recreated Activity loses it. One instance, reattached
    // to whichever composition currently wants it, keeps the live DOM — scroll offset,
    // loaded posts and all — across both.
    val webView = remember {
        createFeedWebView(
            context = context,
            restoredState = restoredState,
            startUrl = startUrl,
            filterBridge = filterBridge,
            scrollBridge = scrollBridge,
            navBridge = navBridge,
            debugToggles = debugToggles,
            displayPreferences = displayPreferences,
            tabPreferences = tabPreferences,
            historyPreferences = historyPreferences,
            onHistoryChanged = { canGoBack = it.canGoBack() },
            onBaseFeedChanged = { isBaseFeed = it },
            onSaveImage = onSaveImage,
            onSaveImageDataUrl = onSaveImageDataUrl,
            onImageResolveFailed = onImageResolveFailed,
        )
    }
    LaunchedEffect(webView) { onWebViewCreated(webView) }

    // Handles a Facebook link tapped while the app was already running (singleTask
    // hands MainActivity.onNewIntent the new intent instead of recreating anything —
    // see there). A cold start's own first URL is startUrl above, loaded once by
    // createFeedWebView itself; this is only for every load after that.
    LaunchedEffect(pendingExternalUrl) {
        val url = pendingExternalUrl ?: return@LaunchedEffect
        webView.loadUrl(url)
        screen = Screen.Feed
        onPendingExternalUrlHandled()
    }

    when (screen) {
        Screen.Feed -> FbWebViewScreen(
            webView = webView,
            canGoBack = canGoBack,
            isBaseFeed = isBaseFeed,
            onDebugDump = onDebugDump,
            settingsViewModel = settingsViewModel,
            filterBridge = filterBridge,
            debugToggles = debugToggles,
            displayPreferences = displayPreferences,
            tabPreferences = tabPreferences,
        )
        Screen.Settings -> SettingsScreen(
            onBack = { screen = Screen.Feed },
            onOpenEnhancements = { screen = Screen.Enhancements },
            onOpenDebug = { screen = Screen.Debug },
            updateViewModel = updateViewModel,
        )
        Screen.Sync -> SourceSyncScreen(
            onCancel = { screen = Screen.AllowedSources },
            onConfirm = { names ->
                settingsViewModel.addPages(names)
                screen = Screen.AllowedSources
            },
        )
        Screen.AllowedSources -> AllowedSourcesScreen(
            onBack = { screen = Screen.Enhancements },
            onOpenSync = { screen = Screen.Sync },
            displayPreferences = displayPreferences,
            settingsViewModel = settingsViewModel,
        )
        Screen.Debug -> DebugScreen(
            onBack = { screen = Screen.Settings },
            onOpenTabIcons = { screen = Screen.TabIcons },
            debugToggles = debugToggles,
        )
        Screen.TabIcons -> TabIconsScreen(
            onBack = { screen = Screen.Debug },
            tabPreferences = tabPreferences,
        )
        Screen.Enhancements -> EnhancementsScreen(
            onBack = { screen = Screen.Settings },
            onOpenAllowedSources = { screen = Screen.AllowedSources },
            onOpenHistory = { screen = Screen.History },
            displayPreferences = displayPreferences,
        )
        Screen.History -> HistoryScreen(
            onBack = { screen = Screen.Enhancements },
            historyPreferences = historyPreferences,
            // No real permalink is ever available (see PostHistoryPreferences' own
            // comment on why) — a Facebook search for the source's name is the closest
            // this can get the user back, so this leaves Settings and drops straight
            // into the feed screen showing that search's results.
            onOpenSearch = { query ->
                webView.loadUrl(SEARCH_URL_PREFIX + Uri.encode(query))
                screen = Screen.Feed
            },
        )
    }
}

/**
 * Builds the single WebView the whole Activity shares. Split out of the composition so
 * it can't accidentally be tied to one — see App()'s remember{} for why that mattered.
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createFeedWebView(
    context: android.content.Context,
    restoredState: Bundle?,
    startUrl: String,
    filterBridge: FeedFilterBridge,
    scrollBridge: ScrollPositionBridge,
    navBridge: NavigationBridge,
    debugToggles: DebugToggles,
    displayPreferences: FeedDisplayPreferences,
    tabPreferences: TabPreferences,
    historyPreferences: PostHistoryPreferences,
    onHistoryChanged: (WebView) -> Unit,
    onBaseFeedChanged: (Boolean) -> Unit,
    onSaveImage: (String) -> Unit,
    onSaveImageDataUrl: (String) -> Unit,
    onImageResolveFailed: (String) -> Unit,
): WebView {
    // Lets `chrome://inspect` on a USB-connected desktop attach DevTools to this
    // WebView's live, authenticated session — the only practical way to read
    // m.facebook.com's real DOM and fix the feed_filter.js selectors.
    if (BuildConfig.DEBUG) {
        WebView.setWebContentsDebuggingEnabled(true)
    }

    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        // Persistent session: cookies are never cleared on close, so the user stays
        // logged in across app restarts.
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)

        addJavascriptInterface(filterBridge, "NativeFilter")
        addJavascriptInterface(scrollBridge, "NativeScroll")
        addJavascriptInterface(debugToggles, "NativeFlags")
        addJavascriptInterface(navBridge, "NativeNav")
        addJavascriptInterface(displayPreferences, "NativeDisplay")
        addJavascriptInterface(tabPreferences, "NativeTabs")
        addJavascriptInterface(historyPreferences, "NativeHistory")
        webViewClient = FbWebViewClient(onHistoryChanged = onHistoryChanged)
        webChromeClient = object : WebChromeClient() {
            // Title alone isn't enough — see feed_filter.js's isFeedPage() for why: a
            // post's own permalink page (story.php) keeps the title "Facebook" too, so
            // this also requires the path to still be the feed's own root.
            override fun onReceivedTitle(view: WebView, title: String?) {
                val path = runCatching { Uri.parse(view.url).path }.getOrNull()
                onBaseFeedChanged(title == "Facebook" && (path == "/" || path.isNullOrEmpty()))
            }
        }

        // Catches a real HTTP download Facebook's own UI triggers directly (an
        // unrenderable mimetype, or a Content-Disposition: attachment response) — a
        // secondary path alongside the long-press handler below. An on-device test of
        // the "..." menu's own Save button hit this and failed with "Expected URL
        // scheme 'http' or 'https'": the url here can itself be a blob: URL (how some
        // of Facebook's own "Save" buttons work internally), which no native HTTP
        // client can fetch — only the page's own JS, which is what created it, can.
        // __ffwResolveImageForSave (image_save.js) does exactly what it does for a
        // long-press on the same kind of source: fetch the blob and hand the actual
        // bytes back over NativeMedia.onImageDataUrl. Video isn't handled yet —
        // PROJECT_CONTENT.md's own roadmap keeps it as a separate, later step.
        setDownloadListener { url, _, _, mimetype, _ ->
            // Reported as producing no feedback at all even after every path below was
            // given a guaranteed Toast — logged first, before any of that logic, in
            // case onDownloadStart itself either never fires or throws before reaching
            // any of it. Same shared timeline image_save.js and resume_guard.js already
            // log into, surfaced in the debug dump.
            evaluateJavascript(
                "window.__ffwLog && window.__ffwLog('setDownloadListener: mimetype=' + " +
                    "${JSONObject.quote(mimetype ?: "null")} + ' url=' + ${JSONObject.quote(url.take(80))});",
                null,
            )
            // mimetype is a Java String crossing the SAM boundary — Kotlin infers it
            // as non-null here, but nothing stops the actual WebView engine from
            // handing over a real null underneath that; ?.startsWith(...) == true
            // (not mimetype?.startsWith(...) ?: false, which reads the same but hides
            // the intent less) treats that the same as "not an image" instead of
            // crashing this callback with an NPE the WebView engine would otherwise
            // swallow silently — exactly the kind of total silence this was meant to
            // stop happening.
            if (mimetype?.startsWith("image/") != true) {
                Toast.makeText(context, "Η αποθήκευση υποστηρίζει προς το παρόν μόνο εικόνες", Toast.LENGTH_SHORT).show()
            } else if (url.startsWith("blob:") || url.startsWith("data:")) {
                evaluateJavascript(
                    "window.__ffwResolveImageForSave && window.__ffwResolveImageForSave(${JSONObject.quote(url)});",
                    null,
                )
            } else {
                onSaveImage(url)
            }
        }

        // The reliable path: image_save.js's own JS long-press detection, not
        // WebView's native OnLongClickListener/HitTestResult — an on-device test found
        // that never fired at all on a Facebook photo (see MediaBridge's own comment
        // on why: Facebook's photo viewer almost certainly preventDefault()s
        // touchstart for its own pinch/zoom/swipe gestures, which stops the platform's
        // long-press gesture detection before it ever starts).
        addJavascriptInterface(
            MediaBridge(
                onImageUrl = onSaveImage,
                onImageDataUrl = onSaveImageDataUrl,
                onImageResolveFailed = onImageResolveFailed,
            ),
            "NativeMedia",
        )

        if (restoredState != null) {
            restoreState(restoredState)
        } else {
            loadUrl(startUrl)
        }
    }
}

@Composable
private fun FbWebViewScreen(
    webView: WebView,
    canGoBack: Boolean,
    isBaseFeed: Boolean,
    onDebugDump: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(),
    filterBridge: FeedFilterBridge,
    debugToggles: DebugToggles,
    displayPreferences: FeedDisplayPreferences,
    tabPreferences: TabPreferences,
) {
    val allowedPages by settingsViewModel.allowedPages.collectAsState()
    val filteringEnabled by displayPreferences.filteringEnabled.collectAsState()
    val filterStats by filterBridge.stats.collectAsState()
    val statsBannerEnabled by debugToggles.statsBannerEnabled.collectAsState()
    val debugButtonEnabled by debugToggles.debugButtonEnabled.collectAsState()
    val hideReactions by displayPreferences.hideReactions.collectAsState()
    val hideSuggested by displayPreferences.hideSuggested.collectAsState()
    val hidePeopleYouMayKnow by displayPreferences.hidePeopleYouMayKnow.collectAsState()
    val hideCreators by displayPreferences.hideCreators.collectAsState()
    val showScrollTopButton by displayPreferences.showScrollTopButton.collectAsState()
    val showPostNavButtons by displayPreferences.showPostNavButtons.collectAsState()
    val buttonSize by displayPreferences.buttonSize.collectAsState()
    val hiddenTabs by tabPreferences.hiddenTabs.collectAsState()
    val tabOrder by tabPreferences.tabOrder.collectAsState()
    val topBarModEnabled by debugToggles.topBarModEnabled.collectAsState()

    // Re-applies the filter in the already-loaded page whenever the user edits the
    // allowed-pages list, or flips the feature's own on/off toggle, in Settings.
    LaunchedEffect(allowedPages, filteringEnabled) {
        filterBridge.allowedAuthors = allowedPages
        webView.evaluateJavascript(REFRESH_FILTER_JS, null)
    }

    // Re-applies feed_display.js's hide/show state whenever the user flips a display
    // toggle in Settings — including the first composition after navigating back to
    // the feed from there, which is what actually picks up a change made while this
    // screen wasn't on screen to react to it live.
    LaunchedEffect(hideReactions, hideSuggested, hidePeopleYouMayKnow, hideCreators) {
        webView.evaluateJavascript(REFRESH_DISPLAY_JS, null)
    }

    // Re-applies tab_visibility.js's hide/show state and layout (and relocates the
    // Settings overlay to the newly freed slot) whenever the user checks/unchecks or
    // reorders a top-bar icon in Settings — same shape as the two effects above.
    LaunchedEffect(hiddenTabs, tabOrder, topBarModEnabled) {
        webView.evaluateJavascript(REFRESH_TABS_JS, null)
    }

    // Re-applies the return-to-top button's on/off preference the same way. Both
    // floating buttons also read their own size fresh from the same preference on
    // every update() pass, so a size change picks this up too without its own effect.
    LaunchedEffect(showScrollTopButton, buttonSize) {
        webView.evaluateJavascript(REFRESH_SCROLL_TOP_JS, null)
    }

    // Re-applies the previous/next-post buttons' on/off preference the same way.
    LaunchedEffect(showPostNavButtons, buttonSize) {
        webView.evaluateJavascript(REFRESH_POST_NAV_JS, null)
    }

    // Without this, the system back gesture has nothing registered to intercept it, so
    // it falls straight through to the default (exit the app) instead of stepping back
    // through the page the user was just on — opening a post, going back, and getting
    // dumped on the Android home screen instead of the feed.
    BackHandler(enabled = canGoBack) {
        webView.goBack()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            // Hands back the Activity's existing WebView rather than building one. It
            // may still be attached to the holder of a previous composition (Compose
            // detaches the holder, not the child), and a View can only have one parent,
            // so unhook it first.
            factory = { webView.also { view -> (view.parent as? ViewGroup)?.removeView(view) } },
        )

        // The floating Settings icon moved out (reachable via nav_override.js's
        // overlay in Facebook's own top tab bar instead — see TabIconsScreen for
        // where the user picks which native icon's slot it uses). The debug-capture
        // icon stays floating, though — its whole point is capturing whatever screen
        // the bug is actually on (e.g. the Replies pagination issue), which a button
        // buried inside Settings can't do since navigating there leaves that screen.
        // Toggleable now (Settings → Debug) rather than always shown. Offset down and
        // in from the corner — flush top-end sat right on top of Facebook's own
        // search/menu icons in that same corner.
        if (BuildConfig.DEBUG && debugButtonEnabled) {
            IconButton(
                onClick = onDebugDump,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).offset(x = (-40).dp, y = 48.dp),
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
