package com.chrisgrou.fbfeedwrapper.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Downloads the update APK into the app's private cache and hands it to the
 *  system package installer, which shows Android's own install-confirmation UI. */
object UpdateInstaller {

    private val client = OkHttpClient()

    suspend fun downloadApk(context: Context, url: String, onProgress: (Float) -> Unit = {}): Uri =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "update.apk")
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Empty response body")
                val total = body.contentLength()
                file.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8 * 1024)
                        var readSoFar = 0L
                        var n: Int
                        while (input.read(buffer).also { n = it } >= 0) {
                            if (n == 0) continue
                            out.write(buffer, 0, n)
                            readSoFar += n
                            if (total > 0) onProgress(readSoFar.toFloat() / total)
                        }
                    }
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

    fun launchInstall(context: Context, apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun canRequestInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()
}
