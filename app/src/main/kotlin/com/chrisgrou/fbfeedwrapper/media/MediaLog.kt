package com.chrisgrou.fbfeedwrapper.media

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A native-side event log for the image-save path, read directly by
 * MainActivity.captureDebugDump() — no WebView round trip involved at all.
 *
 * MainActivity previously tried pushing native events into the page's own shared JS
 * log via webView.evaluateJavascript(), the same channel image_save.js itself logs
 * into. On-device evidence ruled that out: image_save.js confirmed
 * "NativeMedia.onImageDataUrl call returned normally" (the bridge call really does
 * reach Kotlin, no exception crossing back) on every attempt, yet not one of the
 * corresponding native-side log lines ever showed up in a capture. Something about
 * that evaluateJavascript path — thread timing, a stale WebView reference, or
 * something else entirely — was silently losing every entry. A plain in-memory list,
 * written directly wherever these events actually happen and read back directly (no
 * JS, no WebView, no thread hop needed to write a line), can't have that problem.
 */
object MediaLog {

    private const val MAX_ENTRIES = 100
    private val entries = mutableListOf<String>()
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(message: String) {
        entries.add("${format.format(Date())} ${Thread.currentThread().name}: $message")
        if (entries.size > MAX_ENTRIES) entries.removeAt(0)
    }

    @Synchronized
    fun dump(): String = entries.joinToString("\n")
}
