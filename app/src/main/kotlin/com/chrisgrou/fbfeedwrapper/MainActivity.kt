package com.chrisgrou.fbfeedwrapper

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisgrou.fbfeedwrapper.update.UpdateDialogHost
import com.chrisgrou.fbfeedwrapper.update.UpdateViewModel

private const val FEED_URL = "https://m.facebook.com"
private const val WEBVIEW_STATE_KEY = "webview_state"

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var restoredState: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredState = savedInstanceState?.getBundle(WEBVIEW_STATE_KEY)

        setContent {
            FbWebViewScreen(
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FbWebViewScreen(
    restoredState: Bundle?,
    onWebViewCreated: (WebView) -> Unit,
    updateViewModel: UpdateViewModel = viewModel(),
) {
    Box(Modifier.fillMaxSize()) {
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

                    // Persistent session: cookies are never cleared on close, so the
                    // user stays logged in across app restarts.
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = FbWebViewClient()

                    onWebViewCreated(this)

                    if (restoredState != null) {
                        restoreState(restoredState)
                    } else {
                        loadUrl(FEED_URL)
                    }
                }
            },
        )

        IconButton(
            onClick = updateViewModel::checkForUpdate,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = "Έλεγχος για ενημερώσεις",
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        UpdateDialogHost(updateViewModel)
    }
}
