package com.chrisgrou.fbfeedwrapper.settings

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "feed_display"
private const val KEY_HIDE_REACTIONS = "hide_reactions"
private const val KEY_HIDE_SUGGESTED = "hide_suggested"

/**
 * User-facing display toggles, read by feed_display.js: whether to hide the
 * reaction-count pill under each post/comment ("👍 2") and whether to hide Facebook's
 * own "Suggested for you" group-suggestion cards in the feed. Off by default — these
 * hide real Facebook UI the user may still want, unlike Feature 1's group/page
 * filtering, which starts from an explicit allow-list.
 */
class FeedDisplayPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hideReactions = MutableStateFlow(prefs.getBoolean(KEY_HIDE_REACTIONS, false))
    val hideReactions: StateFlow<Boolean> = _hideReactions.asStateFlow()

    private val _hideSuggested = MutableStateFlow(prefs.getBoolean(KEY_HIDE_SUGGESTED, false))
    val hideSuggested: StateFlow<Boolean> = _hideSuggested.asStateFlow()

    fun setHideReactions(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_REACTIONS, enabled).apply()
        _hideReactions.value = enabled
    }

    fun setHideSuggested(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_SUGGESTED, enabled).apply()
        _hideSuggested.value = enabled
    }

    @JavascriptInterface
    fun getHideReactions(): Boolean = prefs.getBoolean(KEY_HIDE_REACTIONS, false)

    @JavascriptInterface
    fun getHideSuggested(): Boolean = prefs.getBoolean(KEY_HIDE_SUGGESTED, false)
}
