package com.chrisgrou.fbfeedwrapper.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
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
 * Cookies are read straight from CookieManager — the same store WebView itself reads
 * from and keeps logged in across restarts (see MainActivity's persistent-session
 * setup) — and sent along with the request, since Facebook's CDN can 403 an
 * unauthenticated fetch for some image URLs.
 */
object MediaDownloader {

    private val client = OkHttpClient()

    suspend fun saveImage(context: Context, url: String): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val cookie = CookieManager.getInstance().getCookie(url)
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Referer", "https://m.facebook.com/")
            if (!cookie.isNullOrEmpty()) requestBuilder.header("Cookie", cookie)

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Empty response body")
                val mimeType = body.contentType()?.let { "${it.type}/${it.subtype}" } ?: "image/jpeg"
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
                    // Below API 29 (no scoped storage), RELATIVE_PATH isn't a real
                    // column — the framework resolves a default location for a bare
                    // insert instead. IS_PENDING marks the row "not ready yet" so it
                    // doesn't show up half-written in the Gallery while still
                    // downloading; cleared below once the copy finishes.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SaneBook")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore.insert returned null")

                resolver.openOutputStream(uri)?.use { out -> body.byteStream().copyTo(out) }
                    ?: error("Could not open output stream for $uri")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                }

                uri
            }
        }
    }
}
