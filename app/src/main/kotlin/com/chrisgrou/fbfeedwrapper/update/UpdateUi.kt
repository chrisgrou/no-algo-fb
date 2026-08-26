package com.chrisgrou.fbfeedwrapper.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** Hosts the update-check dialogs; call [UpdateViewModel.checkForUpdate] from
 *  wherever the app exposes a "check for updates" action. */
@Composable
fun UpdateDialogHost(updateViewModel: UpdateViewModel = viewModel()) {
    val state by updateViewModel.state.collectAsState()

    when (val s = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = updateViewModel::dismiss,
            title = { Text("Νέα έκδοση διαθέσιμη") },
            text = {
                Column {
                    Text("Βρέθηκε νέα έκδοση (build ${s.info.versionCode}). Λήψη και εγκατάσταση;")
                    if (!s.info.releaseNotes.isNullOrBlank()) {
                        Text(
                            "Τι άλλαξε:",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        Text(s.info.releaseNotes)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { updateViewModel.downloadAndInstall(s.info) }) { Text("Λήψη & εγκατάσταση") } },
            dismissButton = { TextButton(onClick = updateViewModel::dismiss) { Text("Άκυρο") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Λήψη ενημέρωσης...") },
            text = {
                Column {
                    if (s.progress > 0f) {
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {},
        )
        is UpdateState.Error -> AlertDialog(
            onDismissRequest = updateViewModel::dismiss,
            title = { Text("Σφάλμα ενημέρωσης") },
            text = { Text(s.message) },
            confirmButton = {
                if (s.needsInstallPermission) {
                    TextButton(onClick = updateViewModel::openInstallPermissionSettings) { Text("Ρυθμίσεις") }
                } else {
                    TextButton(onClick = updateViewModel::dismiss) { Text("OK") }
                }
            },
            dismissButton = { TextButton(onClick = updateViewModel::dismiss) { Text("Άκυρο") } },
        )
        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = updateViewModel::dismiss,
            title = { Text("Ενημερωμένη έκδοση") },
            text = { Text("Έχεις ήδη την τελευταία έκδοση.") },
            confirmButton = { TextButton(onClick = updateViewModel::dismiss) { Text("OK") } },
        )
        UpdateState.Checking, UpdateState.Idle -> Unit
    }
}
