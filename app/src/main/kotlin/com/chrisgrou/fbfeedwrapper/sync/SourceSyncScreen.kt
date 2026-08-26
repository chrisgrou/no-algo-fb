package com.chrisgrou.fbfeedwrapper.sync

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

private val SYNC_URLS = listOf(GROUPS_URL, PAGES_URL)

/**
 * Loads the user's groups and followed-pages lists, scrolling each to the end so the
 * lazy-loaded entries exist, then offers what it found for selection.
 *
 * The WebView is on screen rather than offscreen on purpose: these lists only load
 * more as they are scrolled, which needs real layout, and showing the pages scroll by
 * makes it obvious what the sync is actually doing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceSyncScreen(
    onCancel: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val bridge = remember { SourceSyncBridge() }
    var stage by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val collected = remember { mutableStateListOf<SyncCandidate>() }
    val selected = remember { mutableStateListOf<String>() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val progress by bridge.progress.collectAsState()
    val result by bridge.result.collectAsState()

    // Each pass reports once; fold it in, then either move to the next URL or stop.
    LaunchedEffect(result) {
        val pass = result ?: return@LaunchedEffect
        pass.forEach { candidate ->
            if (collected.none { it.name == candidate.name }) collected.add(candidate)
        }
        bridge.clearResult()
        if (stage + 1 < SYNC_URLS.size) {
            stage++
            webViewRef?.loadUrl(SYNC_URLS[stage])
        } else {
            finished = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (finished) "Βρέθηκαν ${collected.size}" else "Συγχρονισμός...") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Πίσω")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (finished) {
                ResultList(
                    candidates = collected,
                    selected = selected,
                    onToggle = { name ->
                        if (selected.contains(name)) selected.remove(name) else selected.add(name)
                    },
                    onConfirm = { onConfirm(selected.toList()) },
                )
            } else {
                // Kept in the tree while running: scrolling is what loads the entries.
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

                            // Shares the app's cookie jar, so these pages load logged in.
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            addJavascriptInterface(bridge, "NativeSync")
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    super.onPageFinished(view, url)
                                    view.evaluateJavascript(AUTO_SYNC_JS, null)
                                }
                            }
                            webViewRef = this
                            loadUrl(SYNC_URLS[0])
                        }
                    },
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(16.dp),
                ) {
                    Text(
                        "Σελίδα ${stage + 1} από ${SYNC_URLS.size} · scroll ${progress.scrollRounds} · " +
                            "βρέθηκαν ${progress.found}",
                        color = Color.White,
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color.White,
                    )
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
            "Διάλεξε ποιες θέλεις στη λίστα. Η λίστα περιέχει ό,τι έμοιαζε με σύνδεσμο " +
                "σε αυτές τις σελίδες, οπότε μπορεί να έχει και άσχετα στοιχεία μενού.",
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
