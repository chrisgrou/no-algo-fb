package com.chrisgrou.fbfeedwrapper.settings

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

private const val PREFS_NAME = "tab_visibility"
private const val KEY_HIDDEN_TABS = "hidden_tabs"
private const val KEY_TAB_ORDER = "tab_order"

/**
 * Which of Facebook's own top tab-bar icons (Home, Watch, Marketplace, ...) the user
 * chose to hide and what order they should appear in, plus the set tab_visibility.js
 * actually found on the current page (discoveredTabs) — read from the live DOM rather
 * than a hardcoded list, since the bar's contents can differ across accounts/rollouts.
 * Hiding a tab keeps its layout space (visibility:hidden, not display:none — see
 * tab_visibility.js) so nav_override.js can anchor our own Settings entry point in
 * that freed slot instead of ever overlaying a still-visible native icon.
 *
 * tabOrder is stored as just the labels the user has explicitly rearranged, in their
 * chosen order — not a full, always-complete list. tab_visibility.js merges it with
 * whatever it actually discovers on the page (any tab not mentioned keeps its natural
 * position, appended after the ones that are), the same "explicit override, sensible
 * fallback for the rest" shape hiddenTabs already has.
 */
class TabPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hiddenTabs = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN_TABS, emptySet())!!.toSet())
    val hiddenTabs: StateFlow<Set<String>> = _hiddenTabs.asStateFlow()

    private val _tabOrder = MutableStateFlow(loadOrder())
    val tabOrder: StateFlow<List<String>> = _tabOrder.asStateFlow()

    // Compose-only, refreshed whenever tab_visibility.js reports what's on screen right
    // now — not persisted, it's only ever a snapshot of the currently loaded page.
    private val _discoveredTabs = MutableStateFlow<List<String>>(emptyList())
    val discoveredTabs: StateFlow<List<String>> = _discoveredTabs.asStateFlow()

    private fun loadOrder(): List<String> {
        val raw = prefs.getString(KEY_TAB_ORDER, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun setTabHidden(label: String, hidden: Boolean) {
        val updated = if (hidden) _hiddenTabs.value + label else _hiddenTabs.value - label
        prefs.edit().putStringSet(KEY_HIDDEN_TABS, updated).apply()
        _hiddenTabs.value = updated
    }

    // The list Settings actually displays and reorders — the saved order filtered down
    // to labels still on the page, with any newly-discovered one appended at the end,
    // so a stale saved entry (an icon Facebook no longer shows) never leaves a gap or
    // a phantom row.
    fun displayOrder(discovered: List<String>): List<String> {
        val known = _tabOrder.value.filter { it in discovered }
        val missing = discovered.filter { it !in known }
        return known + missing
    }

    fun moveTab(discovered: List<String>, label: String, delta: Int) {
        val current = displayOrder(discovered).toMutableList()
        val index = current.indexOf(label)
        val target = index + delta
        if (index < 0 || target !in current.indices) return
        val tmp = current[index]
        current[index] = current[target]
        current[target] = tmp
        prefs.edit().putString(KEY_TAB_ORDER, JSONArray(current).toString()).apply()
        _tabOrder.value = current
    }

    @JavascriptInterface
    fun getHiddenTabs(): String = JSONArray(prefs.getStringSet(KEY_HIDDEN_TABS, emptySet())!!.toList()).toString()

    @JavascriptInterface
    fun getTabOrder(): String = JSONArray(_tabOrder.value).toString()

    @JavascriptInterface
    fun reportTabs(labelsJson: String) {
        val array = runCatching { JSONArray(labelsJson) }.getOrNull() ?: return
        val labels = (0 until array.length()).map { array.getString(it) }
        if (labels != _discoveredTabs.value) _discoveredTabs.value = labels
    }
}
