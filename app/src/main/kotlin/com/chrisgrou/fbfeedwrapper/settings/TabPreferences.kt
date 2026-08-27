package com.chrisgrou.fbfeedwrapper.settings

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

private const val PREFS_NAME = "tab_visibility"
private const val KEY_HIDDEN_TABS = "hidden_tabs"

/**
 * Which of Facebook's own top tab-bar icons (Home, Watch, Marketplace, ...) the user
 * chose to hide, plus the set tab_visibility.js actually found on the current page
 * (discoveredTabs) — read from the live DOM rather than a hardcoded list, since the
 * bar's contents can differ across accounts/rollouts. Hiding a tab keeps its layout
 * space (visibility:hidden, not display:none — see tab_visibility.js) so
 * nav_override.js can anchor our own Settings entry point in that freed slot instead
 * of ever overlaying a still-visible native icon.
 */
class TabPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hiddenTabs = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN_TABS, emptySet())!!.toSet())
    val hiddenTabs: StateFlow<Set<String>> = _hiddenTabs.asStateFlow()

    // Compose-only, refreshed whenever tab_visibility.js reports what's on screen right
    // now — not persisted, it's only ever a snapshot of the currently loaded page.
    private val _discoveredTabs = MutableStateFlow<List<String>>(emptyList())
    val discoveredTabs: StateFlow<List<String>> = _discoveredTabs.asStateFlow()

    fun setTabHidden(label: String, hidden: Boolean) {
        val updated = if (hidden) _hiddenTabs.value + label else _hiddenTabs.value - label
        prefs.edit().putStringSet(KEY_HIDDEN_TABS, updated).apply()
        _hiddenTabs.value = updated
    }

    @JavascriptInterface
    fun getHiddenTabs(): String = JSONArray(prefs.getStringSet(KEY_HIDDEN_TABS, emptySet())!!.toList()).toString()

    @JavascriptInterface
    fun reportTabs(labelsJson: String) {
        val array = runCatching { JSONArray(labelsJson) }.getOrNull() ?: return
        val labels = (0 until array.length()).map { array.getString(it) }
        if (labels != _discoveredTabs.value) _discoveredTabs.value = labels
    }
}
