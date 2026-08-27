package com.chrisgrou.fbfeedwrapper.settings

import android.content.Context
import android.webkit.JavascriptInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "comment_sort"
private const val KEY_SORT = "preferred_sort"

/** Facebook's own labels for its per-post comment-sort dropdown (Feature: comment
 *  sort). Matched against verbatim in comment_sort.js, so these have to be exactly
 *  what Facebook renders (English UI, confirmed on-device), not a translation. */
const val COMMENT_SORT_MOST_RELEVANT = "Most relevant"
const val COMMENT_SORT_NEWEST = "Newest"
const val COMMENT_SORT_ALL = "All comments"

val COMMENT_SORT_OPTIONS = listOf(COMMENT_SORT_MOST_RELEVANT, COMMENT_SORT_NEWEST, COMMENT_SORT_ALL)

/**
 * Persists the user's preferred comment-sort order and hands it to comment_sort.js,
 * which applies it on every post the user opens instead of leaving it at Facebook's
 * own default ("Most relevant") every time.
 *
 * Plain SharedPreferences rather than DataStore, same reasoning as
 * ScrollPositionBridge: the JS bridge needs a synchronous read. A StateFlow alongside
 * it is what lets the Settings screen show/edit the current choice reactively.
 */
class CommentSortPreference(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _preferredSort = MutableStateFlow(prefs.getString(KEY_SORT, COMMENT_SORT_MOST_RELEVANT)!!)
    val preferredSort: StateFlow<String> = _preferredSort.asStateFlow()

    fun setPreferredSort(sort: String) {
        prefs.edit().putString(KEY_SORT, sort).apply()
        _preferredSort.value = sort
    }

    @JavascriptInterface
    fun getPreferredSort(): String = prefs.getString(KEY_SORT, COMMENT_SORT_MOST_RELEVANT)!!
}
