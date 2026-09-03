package com.chrisgrou.fbfeedwrapper.media

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Bridges image_save.js's own long-press detection back to MainActivity's saveImage
 * (a plain http(s) URL, fetched natively) / saveImageDataUrl (a data: URL — a blob:
 * source resolved to actual bytes inside the page's own JS first, since no native HTTP
 * client can dereference a blob: URL; see image_save.js's own comment on why).
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
    private val onImageUrl: (String) -> Unit,
    private val onImageDataUrl: (String) -> Unit,
    private val onImageResolveFailed: (String) -> Unit,
) {
    @JavascriptInterface
    fun onImageUrl(url: String) {
        // Same reasoning as NavigationBridge.requestOpenSettings(): this callback runs
        // on a background thread, but everything downstream (permission requests,
        // Toasts, Compose state) needs the main thread.
        Handler(Looper.getMainLooper()).post { onImageUrl(url) }
    }

    @JavascriptInterface
    fun onImageDataUrl(dataUrl: String) {
        Handler(Looper.getMainLooper()).post { onImageDataUrl(dataUrl) }
    }

    // A long-press that found nothing to save, or a blob:/data: resolve that failed
    // (most likely a blob: URL Facebook's own code had already revoked by the time
    // this ran — see image_save.js's own comment) — either way, a visible reason
    // instead of a long-press or a Save tap that silently does nothing at all.
    @JavascriptInterface
    fun onImageResolveFailed(reason: String) {
        Handler(Looper.getMainLooper()).post { onImageResolveFailed(reason) }
    }
}
