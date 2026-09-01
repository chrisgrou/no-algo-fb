package com.chrisgrou.fbfeedwrapper.sync

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Lets the user browse to whichever list they want imported — their groups, their
 * followed pages, their friends — and scan it on demand. The allow-list this feeds
 * isn't actually limited to groups/pages (feed_filter.js matches a post's own first
 * link text either way), so a friends list scans in exactly the same way.
 *
 * An earlier version drove itself through two hardcoded URLs and reported five browser
 * names: www.facebook.com had served an "open in the app" interstitial rather than the
 * list, and the scan dutifully scraped that. Facebook also moves these lists around
 * (followed pages currently sit behind Pages → See all). Letting the user land on the
 * real list first removes the guesswork entirely, and scanning is explicit so what gets
 * captured is never a surprise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceSyncScreen(
    onCancel: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val bridge = remember { SourceSyncBridge() }
    val collected = remember { mutableStateListOf<SyncCandidate>() }
    val selected = remember { mutableStateListOf<String>() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }

    val progress by bridge.progress.collectAsState()
    val result by bridge.result.collectAsState()

    LaunchedEffect(result) {
        val pass = result ?: return@LaunchedEffect
        pass.forEach { candidate ->
            if (collected.none { it.name == candidate.name }) collected.add(candidate)
        }
        bridge.clearResult()
        scanning = false
    }

    // Back goes through the page's own history first, so browsing to a list and
    // stepping back doesn't drop the user out of the sync flow.
    BackHandler(enabled = !reviewing) {
        val web = webViewRef
        if (web != null && web.canGoBack()) web.goBack() else onCancel()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (reviewing) "Βρέθηκαν ${collected.size}" else "Εισαγωγή πηγών")
                },
                navigationIcon = {
                    IconButton(onClick = { if (reviewing) reviewing = false else onCancel() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Πίσω")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (reviewing) {
                ResultList(
                    candidates = collected,
                    selected = selected,
                    onToggle = { name ->
                        if (selected.contains(name)) selected.remove(name) else selected.add(name)
                    },
                    onConfirm = { onConfirm(selected.toList()) },
                )
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true

                            // Shares the app's cookie jar, so these pages load logged in.
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            addJavascriptInterface(bridge, "NativeSync")
                            webViewClient = WebViewClient()
                            webViewRef = this
                            loadUrl(GROUPS_URL)
                        }
                    },
                )

                SyncControls(
                    scanning = scanning,
                    scanRounds = progress.scrollRounds,
                    scanFound = progress.found,
                    collectedCount = collected.size,
                    onScan = {
                        scanning = true
                        webViewRef?.evaluateJavascript(AUTO_SYNC_JS, null)
                    },
                    onReview = {
                        // Pre-tick everything: on a real list page nearly all of it is
                        // wanted, and unticking the odd stray is less work than ticking
                        // fifty groups by hand.
                        selected.clear()
                        selected.addAll(collected.map { it.name })
                        reviewing = true
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun SyncControls(
    scanning: Boolean,
    scanRounds: Int,
    scanFound: Int,
    collectedCount: Int,
    onScan: () -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(12.dp),
    ) {
        Text(
            if (scanning) {
                "Σάρωση... scroll $scanRounds · βρέθηκαν $scanFound"
            } else {
                "Πήγαινε στη λίστα με τις ομάδες, τις σελίδες ή τους φίλους σου και πάτα " +
                    "σάρωση — δουλεύει σε οποιαδήποτε τέτοια λίστα του Facebook. " +
                    "Μαζεμένα μέχρι τώρα: $collectedCount"
            },
            color = Color.White,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (scanning) {
                CircularProgressIndicator(color = Color.White)
            } else {
                TextButton(onClick = onScan) { Text("Σάρωση αυτής της σελίδας") }
                TextButton(onClick = onReview, enabled = collectedCount > 0) {
                    Text("Τέλος ($collectedCount)")
                }
            }
        }
    }
}

@Composable
private fun ResultList(
    candidates: List<SyncCandidate>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Ομάδες, σελίδες και άτομα είναι όλα έγκυρα — απλά ξεδιάλεξε ό,τι δεν είναι " +
                "καθόλου πηγή (η σάρωση πιάνει και στοιχεία μενού).",
            modifier = Modifier.padding(16.dp),
        )
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text("Προσθήκη ${selected.size} επιλεγμένων")
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(candidates) { candidate ->
                ListItem(
                    headlineContent = { Text(candidate.name) },
                    supportingContent = {
                        if (candidate.href.isNotBlank()) {
                            Text(candidate.href, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    leadingContent = {
                        Checkbox(
                            checked = selected.contains(candidate.name),
                            onCheckedChange = { onToggle(candidate.name) },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(candidate.name) },
                )
            }
        }
    }
}
