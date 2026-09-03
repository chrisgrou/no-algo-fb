package com.chrisgrou.fbfeedwrapper.settings

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "feed_display"
private const val KEY_FILTERING_ENABLED = "filtering_enabled"
private const val KEY_HIDE_REACTIONS = "hide_reactions"
private const val KEY_HIDE_SUGGESTED = "hide_suggested"
private const val KEY_HIDE_PEOPLE_YOU_MAY_KNOW = "hide_people_you_may_know"
private const val KEY_HIDE_CREATORS = "hide_creators"
private const val KEY_SHOW_SCROLL_TOP = "show_scroll_top_button"
private const val KEY_SHOW_POST_NAV = "show_post_nav_buttons"
private const val KEY_BUTTON_SIZE = "floating_button_size"
const val MIN_BUTTON_SIZE = 44
const val MAX_BUTTON_SIZE = 88
const val DEFAULT_BUTTON_SIZE = 52

/**
 * User-facing display toggles under Settings → Βελτιώσεις, read by feed_display.js,
 * scroll_to_top.js and post_nav.js: whether to hide the reaction-count pill under each
 * post/comment ("👍 2"), whether to hide Facebook's own "Suggested for you"
 * group-suggestion cards in the feed, whether the floating return-to-top and
 * previous/next-post buttons show, and how big they are. The first two are off by
 * default — they hide real Facebook UI the user may still want, unlike Feature 1's
 * group/page filtering, which starts from an explicit allow-list. The floating
 * buttons default on, since they're purely additive UI of our own rather than
 * something hidden from Facebook's page.
 */
class FeedDisplayPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Feature 1's own on/off switch, separate from the allow-list itself: turning this
    // off shows every post regardless of what's in the list, without having to clear
    // it (an empty list already meant "show everything" — this just makes that a
    // reachable state without losing whatever's been built up).
    private val _filteringEnabled = MutableStateFlow(prefs.getBoolean(KEY_FILTERING_ENABLED, true))
    val filteringEnabled: StateFlow<Boolean> = _filteringEnabled.asStateFlow()

    private val _hideReactions = MutableStateFlow(prefs.getBoolean(KEY_HIDE_REACTIONS, false))
    val hideReactions: StateFlow<Boolean> = _hideReactions.asStateFlow()

    private val _hideSuggested = MutableStateFlow(prefs.getBoolean(KEY_HIDE_SUGGESTED, false))
    val hideSuggested: StateFlow<Boolean> = _hideSuggested.asStateFlow()

    private val _hidePeopleYouMayKnow = MutableStateFlow(prefs.getBoolean(KEY_HIDE_PEOPLE_YOU_MAY_KNOW, false))
    val hidePeopleYouMayKnow: StateFlow<Boolean> = _hidePeopleYouMayKnow.asStateFlow()

    private val _hideCreators = MutableStateFlow(prefs.getBoolean(KEY_HIDE_CREATORS, false))
    val hideCreators: StateFlow<Boolean> = _hideCreators.asStateFlow()

    private val _showScrollTopButton = MutableStateFlow(prefs.getBoolean(KEY_SHOW_SCROLL_TOP, true))
    val showScrollTopButton: StateFlow<Boolean> = _showScrollTopButton.asStateFlow()

    private val _showPostNavButtons = MutableStateFlow(prefs.getBoolean(KEY_SHOW_POST_NAV, true))
    val showPostNavButtons: StateFlow<Boolean> = _showPostNavButtons.asStateFlow()

    private val _buttonSize = MutableStateFlow(prefs.getInt(KEY_BUTTON_SIZE, DEFAULT_BUTTON_SIZE))
    val buttonSize: StateFlow<Int> = _buttonSize.asStateFlow()

    fun setFilteringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILTERING_ENABLED, enabled).apply()
        _filteringEnabled.value = enabled
    }

    fun setHideReactions(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_REACTIONS, enabled).apply()
        _hideReactions.value = enabled
    }

    fun setHideSuggested(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_SUGGESTED, enabled).apply()
        _hideSuggested.value = enabled
    }

    fun setHidePeopleYouMayKnow(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_PEOPLE_YOU_MAY_KNOW, enabled).apply()
        _hidePeopleYouMayKnow.value = enabled
    }

    fun setHideCreators(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_CREATORS, enabled).apply()
        _hideCreators.value = enabled
    }

    fun setShowScrollTopButton(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SCROLL_TOP, enabled).apply()
        _showScrollTopButton.value = enabled
    }

    fun setShowPostNavButtons(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_POST_NAV, enabled).apply()
        _showPostNavButtons.value = enabled
    }

    fun setButtonSize(size: Int) {
        val clamped = size.coerceIn(MIN_BUTTON_SIZE, MAX_BUTTON_SIZE)
        prefs.edit().putInt(KEY_BUTTON_SIZE, clamped).apply()
        _buttonSize.value = clamped
    }

    @JavascriptInterface
    fun getFilteringEnabled(): Boolean = prefs.getBoolean(KEY_FILTERING_ENABLED, true)

    @JavascriptInterface
    fun getHideReactions(): Boolean = prefs.getBoolean(KEY_HIDE_REACTIONS, false)

    @JavascriptInterface
    fun getHideSuggested(): Boolean = prefs.getBoolean(KEY_HIDE_SUGGESTED, false)

    @JavascriptInterface
    fun getHidePeopleYouMayKnow(): Boolean = prefs.getBoolean(KEY_HIDE_PEOPLE_YOU_MAY_KNOW, false)

    @JavascriptInterface
    fun getHideCreators(): Boolean = prefs.getBoolean(KEY_HIDE_CREATORS, false)

    @JavascriptInterface
    fun getShowScrollTopButton(): Boolean = prefs.getBoolean(KEY_SHOW_SCROLL_TOP, true)

    @JavascriptInterface
    fun getShowPostNavButtons(): Boolean = prefs.getBoolean(KEY_SHOW_POST_NAV, true)

    @JavascriptInterface
    fun getButtonSize(): Int = prefs.getInt(KEY_BUTTON_SIZE, DEFAULT_BUTTON_SIZE)
}
