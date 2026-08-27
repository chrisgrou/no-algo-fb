package com.chrisgrou.fbfeedwrapper.nav

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Bridges the in-page nav-bar overlay (nav_override.js) back to Compose navigation.
 *
 * Settings is also still reachable as a floating icon on the base feed (see
 * MainActivity) — this is a second entry point, anchored over Facebook's own
 * always-present tab bar (its Marketplace tab), which survives navigating away from
 * and back to the feed without depending on the floating icon's own visibility
 * heuristics.
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
