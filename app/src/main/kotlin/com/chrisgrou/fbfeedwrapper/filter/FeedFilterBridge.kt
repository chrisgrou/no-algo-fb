package com.chrisgrou.fbfeedwrapper.filter

import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

data class FilterStats(val rowsMatched: Int, val authorsResolved: Int, val hiddenCount: Int)

/**
 * Bridges native and the injected feed_filter.js asset via window.NativeFilter:
 * - getAllowedAuthorsJson(): read-only, exposes the current allow-list to JS. There is
 *   no setter reachable from JS running on facebook.com's pages.
 * - reportStats(...): write-only from JS's side, lets the script report what it found
 *   so an on-screen debug overlay can show live counts without a desktop devtools
 *   connection (see MainActivity's debug overlay, BuildConfig.DEBUG only).
 */
class FeedFilterBridge {

    @Volatile
    var allowedAuthors: Set<String> = emptySet()

    private val _stats = MutableStateFlow<FilterStats?>(null)
    val stats: StateFlow<FilterStats?> = _stats

    @JavascriptInterface
    fun getAllowedAuthorsJson(): String = JSONArray(allowedAuthors).toString()

    @JavascriptInterface
    fun reportStats(rowsMatched: Int, authorsResolved: Int, hiddenCount: Int) {
        _stats.value = FilterStats(rowsMatched, authorsResolved, hiddenCount)
    }
}
