package com.chrisgrou.fbfeedwrapper.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val allowedPages by viewModel.allowedPages.collectAsState()
    var newPageName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Επιτρεπόμενες σελίδες") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Πίσω")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Μόνο posts από σελίδες/χρήστες σε αυτή τη λίστα θα εμφανίζονται στο feed. " +
                    "Άδεια λίστα = εμφανίζονται όλα.",
                modifier = Modifier.padding(16.dp),
            )

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = newPageName,
                    onValueChange = { newPageName = it },
                    label = { Text("Όνομα σελίδας") },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    if (newPageName.isNotBlank()) {
                        viewModel.addPage(newPageName)
                        newPageName = ""
                    }
                }) {
                    Text("Προσθήκη")
                }
            }

            LazyColumn {
                items(allowedPages.sorted()) { name ->
                    ListItem(
                        headlineContent = { Text(name) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.removePage(name) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Αφαίρεση")
                            }
                        },
                    )
                }
            }
        }
    }
}
