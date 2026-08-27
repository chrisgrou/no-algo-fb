package com.chrisgrou.fbfeedwrapper

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

private val INJECTED_ASSETS = listOf("feed_filter.js", "scroll_position.js")

/**
 * Injects the feed-filtering (Feature 1) and scroll-position (Feature 2) scripts
 * after every page load, keeps facebook.com navigation inside the WebView, and
 * routes everything else — an article link, a YouTube video, a shared website — out
 * to the user's own browser/app instead.
 */
class FbWebViewClient(
    private val onHistoryChanged: (WebView) -> Unit = {},
) : WebViewClient() {

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        for (asset in INJECTED_ASSETS) {
            val script = view.context.assets.open(asset).bufferedReader().use { it.readText() }
            view.evaluateJavascript(script, null)
        }
        onHistoryChanged(view)
    }

    // Facebook navigates within m.facebook.com mostly via pushState (opening a post,
    // a profile, etc.) rather than full page loads, so this — not onPageFinished
    // alone — is what keeps canGoBack() accurate for the system back gesture.
    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        onHistoryChanged(view)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        val uri = request.url
        val host = uri.host?.lowercase() ?: return false

        // Every outbound link on facebook.com gets wrapped in a tracking redirect —
        // l.facebook.com/l.php?u=<real destination>&h=<hash> — so the click itself is
        // logged before the user ever leaves. Unwrap it: the external browser/app
        // should land on the real destination directly, not bounce through Facebook
        // first (the whole point of a "no tracking" wrapper).
        if (host == "l.facebook.com" || host == "lm.facebook.com") {
            val target = uri.getQueryParameter("u")?.let(Uri::parse) ?: uri
            return openExternally(view.context, target)
        }

        val isFacebook = host == "facebook.com" || host.endsWith(".facebook.com")
        if (isFacebook) return false

        return openExternally(view.context, uri)
    }

    private fun openExternally(context: Context, uri: Uri): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        // No app can handle it (a malformed or unrecognized scheme) — better to leave
        // it unhandled here than to silently swallow the tap.
        false
    }
}
