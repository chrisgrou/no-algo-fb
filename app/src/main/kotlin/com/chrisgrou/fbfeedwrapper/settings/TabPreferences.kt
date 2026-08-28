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
 * chose to hide and what order everything — those icons plus our own Settings icon —
 * should appear in, plus the set tab_visibility.js actually found on the current page
 * (discoveredTabs) — read from the live DOM rather than a hardcoded list, since the
 * bar's contents can differ across accounts/rollouts. Hiding a tab keeps its layout
 * space (visibility:hidden, not display:none — see tab_visibility.js) so its space can
 * be reclaimed by the redistribution tab_visibility.js's relayout() does.
 *
 * The Settings icon is represented in the order by [SETTINGS_SENTINEL], a plain string
 * rather than a real discovered tab — it always gets its own slot in the bar (see
 * relayout()), never by overlaying a still-visible native icon the way earlier
 * versions of this feature did.
 */
class TabPreferences(context: Context) {

    companion object {
        // Stands in for our own Settings icon wherever it sits in the order — not a
        // real Facebook tab, so tab_visibility.js never looks it up by aria-label; it
        // simply reserves a slot in the layout math at this position. The literal
        // value must match SETTINGS_SENTINEL in tab_visibility.js exactly.
        const val SETTINGS_SENTINEL = "__ffw_settings__"
    }

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

    // The full list Settings displays and drag-reorders: the saved order filtered down
    // to what's still relevant — discovered tabs plus the Settings sentinel, which is
    // always relevant — with anything newly relevant (a tab just discovered, or the
    // sentinel the first time this runs) appended at the end.
    fun displayOrder(discovered: List<String>): List<String> {
        val relevant = discovered + SETTINGS_SENTINEL
        val known = _tabOrder.value.filter { it in relevant }
        val missing = relevant.filter { it !in known }
        return known + missing
    }

    fun setOrder(order: List<String>) {
        prefs.edit().putString(KEY_TAB_ORDER, JSONArray(order).toString()).apply()
        _tabOrder.value = order
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
