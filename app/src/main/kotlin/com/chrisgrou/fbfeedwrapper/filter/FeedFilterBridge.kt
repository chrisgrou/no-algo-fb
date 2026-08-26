package com.chrisgrou.fbfeedwrapper.filter

import android.webkit.JavascriptInterface
import org.json.JSONArray

/**
 * Exposes the current allowed-author list to the injected feed_filter.js asset via
 * window.NativeFilter.getAllowedAuthorsJson(). Read-only: there is no setter reachable
 * from JS running on facebook.com's pages, so the page content itself can't alter the
 * filter list through this bridge.
 */
class FeedFilterBridge {

    @Volatile
    var allowedAuthors: Set<String> = emptySet()

    @JavascriptInterface
    fun getAllowedAuthorsJson(): String = JSONArray(allowedAuthors).toString()
}
