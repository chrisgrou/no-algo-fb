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
    // Named *Callback, not the same as the @JavascriptInterface method below it feeds:
    // giving a constructor property and a member function the identical name compiles
    // fine, but a bare call of that name from inside the class then binds to the
    // member function itself (Kotlin prefers a real function over invoking a
    // same-named property through its invoke() convention) — a silent recursive
    // self-call, not the callback. That was live here: every onImageDataUrl(dataUrl)
    // below was calling itself, forever, via its own Handler.post, and never once
    // reaching MainActivity's real saveImageDataUrl. On-device evidence matched
    // exactly — a MediaLog flood of nothing but "received"/"posted callback running"
    // pairs, all the same argLength, and not one saveImageDataUrl/reportSaveResult
    // line despite the JS side confirming every send.
    private val onImageUrlCallback: (String) -> Unit,
    private val onImageDataUrlCallback: (String) -> Unit,
    private val onImageResolveFailedCallback: (String) -> Unit,
) {
    @JavascriptInterface
    fun onImageUrl(url: String) {
        // Logged right here, at the actual JavascriptInterface entry point, before
        // anything else — including the thread hop below — gets a chance to lose it.
        // See MediaLog's own comment for why this replaced trying to log back into the
        // page's own JS console: that path was confirmed silently dropping every entry
        // despite image_save.js seeing the bridge call itself succeed.
        MediaLog.log("onImageUrl received, length=${url.length}")
        // Same reasoning as NavigationBridge.requestOpenSettings(): this callback runs
        // on a background thread, but everything downstream (permission requests,
        // Toasts, Compose state) needs the main thread.
        Handler(Looper.getMainLooper()).post {
            MediaLog.log("onImageUrl posted callback running")
            onImageUrlCallback(url)
        }
    }

    @JavascriptInterface
    fun onImageDataUrl(dataUrl: String) {
        MediaLog.log("onImageDataUrl received, length=${dataUrl.length}")
        Handler(Looper.getMainLooper()).post {
            MediaLog.log("onImageDataUrl posted callback running")
            onImageDataUrlCallback(dataUrl)
        }
    }

    // A long-press that found nothing to save, or a blob:/data: resolve that failed
    // (most likely a blob: URL Facebook's own code had already revoked by the time
    // this ran — see image_save.js's own comment) — either way, a visible reason
    // instead of a long-press or a Save tap that silently does nothing at all.
    @JavascriptInterface
    fun onImageResolveFailed(reason: String) {
        MediaLog.log("onImageResolveFailed received: $reason")
        Handler(Looper.getMainLooper()).post { onImageResolveFailedCallback(reason) }
    }
}
