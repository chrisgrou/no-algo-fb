package com.chrisgrou.fbfeedwrapper.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AllowedPagesRepository(application)

    val allowedPages: StateFlow<Set<String>> = repository.allowedPages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun addPage(name: String) {
        viewModelScope.launch { repository.addPage(name) }
    }

    fun removePage(name: String) {
        viewModelScope.launch { repository.removePage(name) }
    }
}
