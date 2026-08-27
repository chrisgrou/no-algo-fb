package com.chrisgrou.fbfeedwrapper.settings

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "feed_display"
private const val KEY_HIDE_REACTIONS = "hide_reactions"
private const val KEY_HIDE_SUGGESTED = "hide_suggested"
private const val KEY_SHOW_SCROLL_TOP = "show_scroll_top_button"

/**
 * User-facing display toggles under Settings → Βελτιώσεις, read by feed_display.js
 * and scroll_to_top.js: whether to hide the reaction-count pill under each
 * post/comment ("👍 2"), whether to hide Facebook's own "Suggested for you"
 * group-suggestion cards in the feed, and whether the floating return-to-top button
 * shows at all. The first two are off by default — they hide real Facebook UI the
 * user may still want, unlike Feature 1's group/page filtering, which starts from an
 * explicit allow-list. The return-to-top button defaults on, since it's purely
 * additive UI of our own rather than something hidden from Facebook's page.
 */
class FeedDisplayPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hideReactions = MutableStateFlow(prefs.getBoolean(KEY_HIDE_REACTIONS, false))
    val hideReactions: StateFlow<Boolean> = _hideReactions.asStateFlow()

    private val _hideSuggested = MutableStateFlow(prefs.getBoolean(KEY_HIDE_SUGGESTED, false))
    val hideSuggested: StateFlow<Boolean> = _hideSuggested.asStateFlow()

    private val _showScrollTopButton = MutableStateFlow(prefs.getBoolean(KEY_SHOW_SCROLL_TOP, true))
    val showScrollTopButton: StateFlow<Boolean> = _showScrollTopButton.asStateFlow()

    fun setHideReactions(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_REACTIONS, enabled).apply()
        _hideReactions.value = enabled
    }

    fun setHideSuggested(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_SUGGESTED, enabled).apply()
        _hideSuggested.value = enabled
    }

    fun setShowScrollTopButton(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SCROLL_TOP, enabled).apply()
        _showScrollTopButton.value = enabled
    }

    @JavascriptInterface
    fun getHideReactions(): Boolean = prefs.getBoolean(KEY_HIDE_REACTIONS, false)

    @JavascriptInterface
    fun getHideSuggested(): Boolean = prefs.getBoolean(KEY_HIDE_SUGGESTED, false)

    @JavascriptInterface
    fun getShowScrollTopButton(): Boolean = prefs.getBoolean(KEY_SHOW_SCROLL_TOP, true)
}
