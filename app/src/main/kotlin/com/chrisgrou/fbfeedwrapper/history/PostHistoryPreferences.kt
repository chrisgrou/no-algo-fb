package com.chrisgrou.fbfeedwrapper.history

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "post_history"
private const val KEY_ENTRIES = "entries"
private const val MAX_ENTRIES = 20

data class HistoryEntry(val source: String, val snippet: String, val atMillis: Long)

/**
 * A lightweight "what was I looking at" log, not a bookmark: post_history.js can't
 * capture a navigable link to a specific post — m.facebook.com's mobile markup routes
 * everything through role="link" elements with opaque internal action IDs, no real href
 * (see that file's own comment) — so there's nothing to store that could jump straight
 * back to the exact post. This only remembers the source name and a text snippet of
 * whatever was on screen right before the app went to the background
 * (MainActivity.onPause), so Settings can show a short "recently seen" list purely for
 * recall; tapping an entry there opens a Facebook search for that source's name instead.
 */
class PostHistoryPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    @JavascriptInterface
    fun addEntry(source: String, snippet: String) {
        val current = _entries.value
        // Skip an exact repeat of the most recent entry: backgrounding/resuming
        // repeatedly without scrolling anywhere else would otherwise spam the list with
        // the same post over and over.
        if (current.firstOrNull()?.let { it.source == source && it.snippet == snippet } == true) return
        val updated = (listOf(HistoryEntry(source, snippet, System.currentTimeMillis())) + current)
            .take(MAX_ENTRIES)
        _entries.value = updated
        persist(updated)
    }

    fun clear() {
        _entries.value = emptyList()
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    private fun persist(list: List<HistoryEntry>) {
        val array = JSONArray()
        for (entry in list) {
            array.put(
                JSONObject()
                    .put("source", entry.source)
                    .put("snippet", entry.snippet)
                    .put("at", entry.atMillis),
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private fun load(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                HistoryEntry(obj.getString("source"), obj.getString("snippet"), obj.getLong("at"))
            }
        }.getOrNull() ?: emptyList()
    }
}
