package com.chrisgrou.fbfeedwrapper.media

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Bridges image_save.js's own long-press detection back to MainActivity.saveImage().
 *
 * Not WebView's native long-press/HitTestResult: an on-device test found it never
 * fired at all on a Facebook photo — Facebook's own photo viewer very likely calls
 * preventDefault() on touchstart for its own pinch/zoom/swipe gestures, which stops
 * Android's native long-press gesture detection from ever recognizing the touch as a
 * long press in the first place. image_save.js instead times the touch itself in JS
 * (capture-phase, passive listeners — see its own comment), so it works whether or not
 * the page's own handlers consume the same touch.
 */
class MediaBridge(
    private val onImageLongPress: (String) -> Unit,
) {
    @JavascriptInterface
    fun onImageLongPress(url: String) {
        // Same reasoning as NavigationBridge.requestOpenSettings(): this callback runs
        // on a background thread, but everything downstream (permission requests,
        // Toasts, Compose state) needs the main thread.
        Handler(Looper.getMainLooper()).post { onImageLongPress(url) }
    }
}
