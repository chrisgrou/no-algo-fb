package com.chrisgrou.fbfeedwrapper.nav

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Bridges the in-page nav-bar override (nav_override.js) back to Compose navigation.
 *
 * Settings access used to be a floating Compose icon shown only while `canGoBack` was
 * false — but Facebook's own pull-to-refresh triggers an internal navigation that flips
 * `canGoBack` and never flips it back, permanently hiding that icon even on the base
 * feed. Instead, Settings is now reachable through Facebook's own always-present tab
 * bar (its Marketplace tab, relabelled), which doesn't depend on that heuristic at all.
 */
class NavigationBridge(
    private val onOpenSettings: () -> Unit,
) {
    @JavascriptInterface
    fun requestOpenSettings() {
        // evaluateJavascript's JS callbacks (and this @JavascriptInterface call) run on
        // a background thread, but navigating the Compose UI must happen on the main
        // thread.
        Handler(Looper.getMainLooper()).post { onOpenSettings() }
    }
}
