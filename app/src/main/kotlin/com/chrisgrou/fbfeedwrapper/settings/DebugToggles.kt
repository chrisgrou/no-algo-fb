package com.chrisgrou.fbfeedwrapper.settings

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "debug_toggles"
private const val KEY_FEED_SCOPE = "feed_scope_enabled"
private const val KEY_SCROLL_RESTORE_FIX = "scroll_restore_fix_enabled"

/**
 * Temporary, user-facing on/off switches for two recent fixes — feed_filter.js's
 * isFeedPage() guard and scroll_position.js's gated restore — while chasing a third,
 * still-unsolved bug (Facebook's own top tab bar not reappearing on scroll-up).
 * Neither fix touches that tab bar's code path, but disabling either here reverts
 * just that one fix's behavior without a full code revert, so they can be ruled in
 * or out by testing on a real device instead of guessing. Remove once that's settled.
 */
class DebugToggles(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _feedScopeEnabled = MutableStateFlow(prefs.getBoolean(KEY_FEED_SCOPE, true))
    val feedScopeEnabled: StateFlow<Boolean> = _feedScopeEnabled.asStateFlow()

    private val _scrollRestoreFixEnabled = MutableStateFlow(prefs.getBoolean(KEY_SCROLL_RESTORE_FIX, true))
    val scrollRestoreFixEnabled: StateFlow<Boolean> = _scrollRestoreFixEnabled.asStateFlow()

    fun setFeedScopeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FEED_SCOPE, enabled).apply()
        _feedScopeEnabled.value = enabled
    }

    fun setScrollRestoreFixEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCROLL_RESTORE_FIX, enabled).apply()
        _scrollRestoreFixEnabled.value = enabled
    }

    @JavascriptInterface
    fun getFeedScopeEnabled(): Boolean = prefs.getBoolean(KEY_FEED_SCOPE, true)

    @JavascriptInterface
    fun getScrollRestoreFixEnabled(): Boolean = prefs.getBoolean(KEY_SCROLL_RESTORE_FIX, true)
}
