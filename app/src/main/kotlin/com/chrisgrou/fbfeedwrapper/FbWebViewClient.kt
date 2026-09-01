package com.chrisgrou.fbfeedwrapper

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

private val INJECTED_ASSETS = listOf(
    // First: the sooner it pins document.hidden and gets its listeners in, the smaller
    // the window in which Facebook could still see a visibility change.
    "resume_guard.js",
    "feed_filter.js",
    "post_history.js",
    "scroll_position.js",
    "nav_bar_watchdog.js",
    "tab_visibility.js",
    "nav_override.js",
    "feed_display.js",
    "scroll_to_top.js",
    "post_nav.js",
    "bookmarks_nav.js",
)

/**
 * Injects the resume-guard, feed-filtering (Feature 1), post-history, scroll-position
 * (Feature 2), nav-bar watchdog, tab-visibility, nav-bar override, feed-display,
 * scroll-to-top, post-nav and bookmarks-nav scripts after every page load, keeps
 * facebook.com navigation inside the WebView, and routes everything else — an article
 * link, a YouTube video, a shared website — out to the user's own browser/app instead.
 * tab_visibility.js
 * runs before nav_override.js so the freed-slot attribute it sets is already on the
 * DOM by the time nav_override.js's first sync() runs.
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
