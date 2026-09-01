package com.chrisgrou.fbfeedwrapper.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A "what was I looking at" log, not a bookmark list: see PostHistoryPreferences' own
 * comment on why there's no real link to jump straight back to a specific post.
 * MainActivity.onPause() records one entry (the topmost visible post) each time the app
 * actually goes to the background; tapping an entry here is the best available recovery
 * — a Facebook search for that source's name — rather than a guaranteed hit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    historyPreferences: PostHistoryPreferences,
    onOpenSearch: (String) -> Unit,
) {
    val entries by historyPreferences.entries.collectAsState()
    val timeFormat = remember { SimpleDateFormat("d/M HH:mm", Locale.getDefault()) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ιστορικό posts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Πίσω")
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = historyPreferences::clear) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Εκκαθάριση ιστορικού")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    if (entries.isEmpty()) {
                        "Δεν υπάρχει ακόμα ιστορικό — κάθε φορά που η εφαρμογή πάει στο " +
                            "παρασκήνιο, καταγράφεται το post που έβλεπες τότε."
                    } else {
                        "Δεν υπάρχει άμεσος σύνδεσμος για συγκεκριμένο post — το Facebook δεν " +
                            "εκθέτει έναν τέτοιο στη σελίδα. Πατώντας μια εγγραφή, ανοίγει " +
                            "αναζήτηση στο Facebook για την πηγή της."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(entries) { entry ->
                ListItem(
                    headlineContent = { Text(entry.source) },
                    supportingContent = { Text(entry.snippet) },
                    trailingContent = {
                        Text(
                            timeFormat.format(Date(entry.atMillis)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSearch(entry.source) },
                )
            }
        }
    }
}
