package com.chrisgrou.fbfeedwrapper.scroll

import android.content.Context
import android.webkit.JavascriptInterface

private const val PREFS_NAME = "scroll_position"
private const val KEY_SCROLL_Y = "scroll_y"

/**
 * Persists the feed's scroll position (Feature 2) so it survives the process death
 * Android can trigger on app switching. WebView.saveState()/restoreState() (already
 * wired in MainActivity) covers navigation history, but not reliably the visual
 * scroll offset on a lazy-loaded, infinitely-scrolling page like this one — content
 * below the fold may not even exist in the DOM yet when the WebView is recreated.
 *
 * Plain SharedPreferences rather than DataStore: the JS bridge needs a synchronous
 * read (getSavedScrollY is called directly from injected JS and must return a value
 * immediately, not a Flow to collect), which is exactly what SharedPreferences gives
 * for a single small value.
 */
class ScrollPositionBridge(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @JavascriptInterface
    fun getSavedScrollY(): Float = prefs.getFloat(KEY_SCROLL_Y, 0f)

    @JavascriptInterface
    fun saveScrollY(y: Float) {
        prefs.edit().putFloat(KEY_SCROLL_Y, y).apply()
    }
}
