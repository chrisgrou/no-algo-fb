package com.chrisgrou.fbfeedwrapper.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Saves an image from m.facebook.com straight into the device's own Photos/Gallery
 * (Pictures/SaneBook, via MediaStore) rather than the app's own private storage — the
 * whole point of "save this photo" is to have it somewhere the user can actually find
 * it afterward, outside the app.
 *
 * Two entry points, for the two kinds of source image_save.js can hand over:
 * - saveImage(): a plain http(s) URL, fetched natively.
 * - saveImageDataUrl(): a data: URL already carrying the actual bytes — what a blob:
 *   source becomes once image_save.js resolves it inside the page's own JS context
 *   (see that file's own comment on why a blob: URL can't be fetched from here at
 *   all: it's a page-local reference, not a network resource).
 */
object MediaDownloader {

    private val client = OkHttpClient()

    suspend fun saveImage(context: Context, url: String): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val cookie = CookieManager.getInstance().getCookie(url)
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Referer", "https://m.facebook.com/")
                // Matches the actual WebView's own default UA rather than OkHttp's —
                // some CDNs 403 a request whose User-Agent looks unlike a real browser,
                // and this request otherwise looks exactly like that: a bare OkHttp
                // client with none of the headers a real page load would carry.
                .header("User-Agent", WebSettings.getDefaultUserAgent(context))
            if (!cookie.isNullOrEmpty()) requestBuilder.header("Cookie", cookie)

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Empty response body")
                val mimeType = body.contentType()?.let { "${it.type}/${it.subtype}" } ?: "image/jpeg"
                writeToMediaStore(context, mimeType) { out -> body.byteStream().copyTo(out) }
            }
        }
    }

    // "data:image/jpeg;base64,<payload>" — a data: URL's own MIME type is right there in
    // the header, no response to read one from.
    suspend fun saveImageDataUrl(context: Context, dataUrl: String): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val comma = dataUrl.indexOf(',')
            if (!dataUrl.startsWith("data:") || comma < 0) error("Not a data: URL")
            val header = dataUrl.substring(5, comma)
            if (!header.endsWith(";base64")) error("Unsupported data: URL encoding: $header")
            val mimeType = header.removeSuffix(";base64").ifBlank { "image/jpeg" }
            val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
            writeToMediaStore(context, mimeType) { out -> out.write(bytes) }
        }
    }

    private fun writeToMediaStore(context: Context, mimeType: String, writeBody: (java.io.OutputStream) -> Unit): Uri {
        val extension = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("gif") -> "gif"
            else -> "jpg"
        }
        val fileName = "sanebook_${System.currentTimeMillis()}.$extension"

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            // Below API 29 (no scoped storage), RELATIVE_PATH isn't a real column — the
            // framework resolves a default location for a bare insert instead.
            // IS_PENDING marks the row "not ready yet" so it doesn't show up
            // half-written in the Gallery while still being written; cleared below once
            // the copy finishes.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SaneBook")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore.insert returned null")

        resolver.openOutputStream(uri)?.use(writeBody) ?: error("Could not open output stream for $uri")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }

        return uri
    }
}
