package com.chrisgrou.fbfeedwrapper.settings

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "debug_toggles"
private const val KEY_FEED_SCOPE = "feed_scope_enabled"
private const val KEY_SCROLL_RESTORE_FIX = "scroll_restore_fix_enabled"
private const val KEY_STATS_BANNER = "stats_banner_enabled"
private const val KEY_DEBUG_BUTTON = "debug_button_enabled"
private const val KEY_TOP_BAR_MOD = "top_bar_mod_enabled"

/**
 * User-facing on/off switches for debug-only behavior, gathered under Settings →
 * Debug: kill switches for fixes/features (feed_filter.js's isFeedPage() guard,
 * scroll_position.js's gated restore, and nav_override.js/tab_visibility.js's whole
 * top-bar icon hide/reorder/Settings-entry feature) kept around so each can be ruled
 * in or out against a real-device bug by testing instead of guessing, or turned off
 * outright while it's still rough; the other two are purely about the debug UI itself
 * — the on-screen stats banner, and the floating capture button — neither of which is
 * everyone's cup of tea to always have up.
 */
class DebugToggles(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _feedScopeEnabled = MutableStateFlow(prefs.getBoolean(KEY_FEED_SCOPE, true))
    val feedScopeEnabled: StateFlow<Boolean> = _feedScopeEnabled.asStateFlow()

    private val _scrollRestoreFixEnabled = MutableStateFlow(prefs.getBoolean(KEY_SCROLL_RESTORE_FIX, true))
    val scrollRestoreFixEnabled: StateFlow<Boolean> = _scrollRestoreFixEnabled.asStateFlow()

    // Compose-only (no JS bridge involved), unlike the others.
    private val _statsBannerEnabled = MutableStateFlow(prefs.getBoolean(KEY_STATS_BANNER, true))
    val statsBannerEnabled: StateFlow<Boolean> = _statsBannerEnabled.asStateFlow()

    private val _debugButtonEnabled = MutableStateFlow(prefs.getBoolean(KEY_DEBUG_BUTTON, true))
    val debugButtonEnabled: StateFlow<Boolean> = _debugButtonEnabled.asStateFlow()

    // Off by default: the top-bar icon hide/reorder/Settings-entry feature
    // (nav_override.js + tab_visibility.js) is still rough. Doesn't touch
    // nav_bar_watchdog.js's own fix for Facebook's separate stuck-hidden-bar bug —
    // that one's unrelated and stays on regardless.
    private val _topBarModEnabled = MutableStateFlow(prefs.getBoolean(KEY_TOP_BAR_MOD, false))
    val topBarModEnabled: StateFlow<Boolean> = _topBarModEnabled.asStateFlow()

    fun setFeedScopeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FEED_SCOPE, enabled).apply()
        _feedScopeEnabled.value = enabled
    }

    fun setScrollRestoreFixEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCROLL_RESTORE_FIX, enabled).apply()
        _scrollRestoreFixEnabled.value = enabled
    }

    fun setStatsBannerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STATS_BANNER, enabled).apply()
        _statsBannerEnabled.value = enabled
    }

    fun setDebugButtonEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_BUTTON, enabled).apply()
        _debugButtonEnabled.value = enabled
    }

    fun setTopBarModEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TOP_BAR_MOD, enabled).apply()
        _topBarModEnabled.value = enabled
    }

    @JavascriptInterface
    fun getFeedScopeEnabled(): Boolean = prefs.getBoolean(KEY_FEED_SCOPE, true)

    @JavascriptInterface
    fun getScrollRestoreFixEnabled(): Boolean = prefs.getBoolean(KEY_SCROLL_RESTORE_FIX, true)

    @JavascriptInterface
    fun getTopBarModEnabled(): Boolean = prefs.getBoolean(KEY_TOP_BAR_MOD, false)
}
