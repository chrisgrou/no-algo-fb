package com.chrisgrou.fbfeedwrapper.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val ALLOWED_PAGES_KEY = stringSetPreferencesKey("allowed_pages")

/** Persists the user's list of allowed page/author display names (Feature 1). */
class AllowedPagesRepository(private val context: Context) {

    val allowedPages: Flow<Set<String>> =
        context.settingsDataStore.data.map { prefs -> prefs[ALLOWED_PAGES_KEY] ?: emptySet() }

    suspend fun addPage(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            prefs[ALLOWED_PAGES_KEY] = (prefs[ALLOWED_PAGES_KEY] ?: emptySet()) + trimmed
        }
    }

    suspend fun removePage(name: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[ALLOWED_PAGES_KEY] = (prefs[ALLOWED_PAGES_KEY] ?: emptySet()) - name
        }
    }
}
