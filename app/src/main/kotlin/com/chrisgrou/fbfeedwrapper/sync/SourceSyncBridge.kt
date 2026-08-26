package com.chrisgrou.fbfeedwrapper.sync

import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/** One thing the sync script found that might be a group or page the user follows. */
data class SyncCandidate(val name: String, val href: String)

data class SyncProgress(val scrollRounds: Int, val found: Int)

/**
 * Bridges the auto-scroll/extract script (see [AUTO_SYNC_JS]) back to native. The
 * script drives itself asynchronously — scrolling, waiting for lazy-loaded content,
 * repeating — so it reports through here rather than returning a value.
 */
class SourceSyncBridge {

    private val _progress = MutableStateFlow(SyncProgress(0, 0))
    val progress: StateFlow<SyncProgress> = _progress

    /** Non-null once a pass finishes. Reset with [clearResult] before the next pass. */
    private val _result = MutableStateFlow<List<SyncCandidate>?>(null)
    val result: StateFlow<List<SyncCandidate>?> = _result

    @JavascriptInterface
    fun onProgress(scrollRounds: Int, found: Int) {
        _progress.value = SyncProgress(scrollRounds, found)
    }

    @JavascriptInterface
    fun onComplete(json: String) {
        val parsed = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                SyncCandidate(item.optString("name"), item.optString("href"))
            }
        }.getOrDefault(emptyList())
        _result.value = parsed
    }

    fun clearResult() {
        _result.value = null
        _progress.value = SyncProgress(0, 0)
    }
}
