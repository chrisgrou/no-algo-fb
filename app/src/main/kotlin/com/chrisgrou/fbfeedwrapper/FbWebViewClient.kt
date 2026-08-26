package com.chrisgrou.fbfeedwrapper

import android.webkit.WebView
import android.webkit.WebViewClient

private const val FEED_FILTER_ASSET = "feed_filter.js"

/**
 * Injects the feed-filtering script (Feature 1) after every page load. Scroll-position
 * restore JS (Feature 2) will hook in here too once implemented.
 */
class FbWebViewClient(
    private val onHistoryChanged: (WebView) -> Unit = {},
) : WebViewClient() {

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        val script = view.context.assets.open(FEED_FILTER_ASSET).bufferedReader().use { it.readText() }
        view.evaluateJavascript(script, null)
        onHistoryChanged(view)
    }

    // Facebook navigates within m.facebook.com mostly via pushState (opening a post,
    // a profile, etc.) rather than full page loads, so this — not onPageFinished
    // alone — is what keeps canGoBack() accurate for the system back gesture.
    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        onHistoryChanged(view)
    }
}
