package com.chrisgrou.fbfeedwrapper.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(val versionCode: Int, val apkUrl: String, val releaseUrl: String, val releaseNotes: String?)

/**
 * Checks the repo's "latest" GitHub Release (a fixed tag CI replaces on every push,
 * see .github/workflows/android-build.yml) for a build newer than the one currently
 * installed. The release's APK asset is named "fbfeedwrapper-<versionCode>.apk", so
 * the version number is read straight from the asset filename rather than needing a
 * separate API call.
 */
object UpdateChecker {

    private val client = OkHttpClient()

    suspend fun checkForUpdate(repo: String, currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/tags/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(response.body?.string().orEmpty())
            val assets = json.optJSONArray("assets") ?: return@withContext null
            if (assets.length() == 0) return@withContext null
            val asset = assets.getJSONObject(0)
            val remoteVersionCode = Regex("(\\d+)").find(asset.optString("name")).let { it?.value?.toIntOrNull() }
                ?: return@withContext null
            if (remoteVersionCode <= currentVersionCode) return@withContext null
            UpdateInfo(
                versionCode = remoteVersionCode,
                apkUrl = asset.optString("browser_download_url"),
                releaseUrl = json.optString("html_url"),
                releaseNotes = json.optString("body").trim().ifBlank { null },
            )
        }
    }
}
