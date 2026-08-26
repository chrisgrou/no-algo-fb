package com.chrisgrou.fbfeedwrapper

import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Placeholder for future feed-filtering JS injection (Feature 1) and
 * scroll-position restore JS (Feature 2). Scaffold pass: no-op beyond
 * keeping navigation inside the WebView.
 */
class FbWebViewClient : WebViewClient() {

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
    }
}
