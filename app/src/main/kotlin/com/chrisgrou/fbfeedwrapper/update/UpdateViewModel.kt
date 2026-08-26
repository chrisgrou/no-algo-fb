package com.chrisgrou.fbfeedwrapper.update

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisgrou.fbfeedwrapper.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class Error(val message: String, val needsInstallPermission: Boolean = false) : UpdateState()
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    fun checkForUpdate() {
        viewModelScope.launch {
            _state.value = UpdateState.Checking
            val result = runCatching { UpdateChecker.checkForUpdate(BuildConfig.GITHUB_REPO, BuildConfig.VERSION_CODE) }
                .getOrNull()
            _state.value = if (result != null) UpdateState.Available(result) else UpdateState.UpToDate
        }
    }

    fun downloadAndInstall(info: UpdateInfo) {
        val context = getApplication<Application>()
        if (!UpdateInstaller.canRequestInstall(context)) {
            _state.value = UpdateState.Error(
                "Χρειάζεται άδεια εγκατάστασης εφαρμογών από άγνωστες πηγές.",
                needsInstallPermission = true,
            )
            return
        }
        viewModelScope.launch {
            _state.value = UpdateState.Downloading(0f)
            val uri = runCatching {
                UpdateInstaller.downloadApk(context, info.apkUrl) { progress ->
                    _state.value = UpdateState.Downloading(progress)
                }
            }.getOrElse {
                _state.value = UpdateState.Error(it.message ?: "Αποτυχία λήψης")
                return@launch
            }
            UpdateInstaller.launchInstall(context, uri)
            _state.value = UpdateState.Idle
        }
    }

    fun openInstallPermissionSettings() {
        val context = getApplication<Application>()
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }
}
