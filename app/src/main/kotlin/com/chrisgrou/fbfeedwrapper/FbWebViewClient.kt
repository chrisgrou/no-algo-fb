package com.chrisgrou.fbfeedwrapper

import android.webkit.WebView
import android.webkit.WebViewClient

private const val FEED_FILTER_ASSET = "feed_filter.js"

/**
 * Injects the feed-filtering script (Feature 1) after every page load. Scroll-position
 * restore JS (Feature 2) will hook in here too once implemented.
 */
class FbWebViewClient : WebViewClient() {

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        val script = view.context.assets.open(FEED_FILTER_ASSET).bufferedReader().use { it.readText() }
        view.evaluateJavascript(script, null)
    }
}
