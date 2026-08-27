package com.chrisgrou.fbfeedwrapper.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.chrisgrou.fbfeedwrapper.update.UpdateDialogHost
import com.chrisgrou.fbfeedwrapper.update.UpdateState
import com.chrisgrou.fbfeedwrapper.update.UpdateViewModel

/**
 * The app's single settings hub: the front screen shows only the gear icon that opens
 * this, and everything else — update checks, importing sources, and the allow-list —
 * is organized here instead of scattered as separate icons over the feed. The
 * allow-list itself lives on its own screen (AllowedSourcesScreen) rather than being
 * inlined here, since it can grow arbitrarily long and isn't a fixed-size setting like
 * the rows above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenAllowedSources: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenTabIcons: () -> Unit,
    displayPreferences: FeedDisplayPreferences,
    settingsViewModel: SettingsViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel(),
) {
    val allowedPages by settingsViewModel.allowedPages.collectAsState()
    val updateState by updateViewModel.state.collectAsState()
    val hideReactions by displayPreferences.hideReactions.collectAsState()
    val hideSuggested by displayPreferences.hideSuggested.collectAsState()

    // Without this, the system back gesture has nothing registered to intercept it on
    // this screen and falls through to the default (exit the app) instead of stepping
    // back to the feed.
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ρυθμίσεις") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Πίσω")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Έλεγχος για ενημερώσεις") },
                    supportingContent = {
                        Text(if (updateState is UpdateState.Checking) "Έλεγχος..." else "Νέα έκδοση από το GitHub")
                    },
                    leadingContent = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = updateViewModel::checkForUpdate),
                )
                ListItem(
                    headlineContent = { Text("Εισαγωγή από τις ομάδες & σελίδες μου") },
                    supportingContent = { Text("Σάρωση των ομάδων/σελίδων που ακολουθείς στο Facebook") },
                    leadingContent = { Icon(Icons.Filled.CloudSync, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSync),
                )
                ListItem(
                    headlineContent = { Text("Επιτρεπόμενες πηγές") },
                    supportingContent = { Text("${allowedPages.size} ομάδες/σελίδες") },
                    leadingContent = { Icon(Icons.Filled.List, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAllowedSources),
                )
                ListItem(
                    headlineContent = { Text("Εικονίδια πάνω μπάρας") },
                    supportingContent = { Text("Επιλογή ποια εικονίδια της μπάρας του Facebook εμφανίζονται") },
                    leadingContent = { Icon(Icons.Filled.Tab, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenTabIcons),
                )
                ListItem(
                    headlineContent = { Text("Απόκρυψη reactions") },
                    supportingContent = { Text("Κρύβει τον αριθμό reactions κάτω από posts και σχόλια") },
                    trailingContent = {
                        Switch(
                            checked = hideReactions,
                            onCheckedChange = displayPreferences::setHideReactions,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    headlineContent = { Text("Απόκρυψη \"Suggested for you\"") },
                    supportingContent = { Text("Κρύβει τις προτεινόμενες ομάδες μέσα στο feed") },
                    trailingContent = {
                        Switch(
                            checked = hideSuggested,
                            onCheckedChange = displayPreferences::setHideSuggested,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    headlineContent = { Text("Debug") },
                    supportingContent = { Text("Toggles δοκιμής, στατιστικά φίλτρου, αποστολή dump") },
                    leadingContent = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDebug),
                )
            }
        }
    }

    UpdateDialogHost(updateViewModel)
}

/**
 * Debug-only tools, split out from the main Settings hub so they don't clutter it for
 * everyday use: kill switches for two fixes (see DebugToggles) kept around so either
 * can be ruled in or out against a real-device bug by testing instead of guessing,
 * whether to show the on-screen filter-stats banner, and whether to show the floating
 * debug-capture button over the feed (it needs to float, not live as a button here —
 * it captures whatever screen the bug is actually on, which this screen isn't).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    debugToggles: DebugToggles,
) {
    val feedScopeEnabled by debugToggles.feedScopeEnabled.collectAsState()
    val scrollRestoreFixEnabled by debugToggles.scrollRestoreFixEnabled.collectAsState()
    val statsBannerEnabled by debugToggles.statsBannerEnabled.collectAsState()
    val debugButtonEnabled by debugToggles.debugButtonEnabled.collectAsState()

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Πίσω")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Εμφάνιση floating debug κουμπιού") },
                    supportingContent = { Text("Αποστολή feed dump (στατιστικά φίλτρου, μπάρα, ορατό HTML) από οπουδήποτε") },
                    trailingContent = {
                        Switch(
                            checked = debugButtonEnabled,
                            onCheckedChange = debugToggles::setDebugButtonEnabled,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    headlineContent = { Text("Στατιστικά φίλτρου στην οθόνη") },
                    supportingContent = { Text("posts/src/hid/ok/leak/gapfix/allow, κάτω αριστερά στο feed") },
                    trailingContent = {
                        Switch(
                            checked = statsBannerEnabled,
                            onCheckedChange = debugToggles::setStatsBannerEnabled,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Toggles δοκιμής",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
                ListItem(
                    headlineContent = { Text("Φιλτράρισμα μόνο στο βασικό feed") },
                    supportingContent = { Text("Απενεργοποίησέ το για δοκιμή αν υποψιάζεσαι ότι προκαλεί άλλο πρόβλημα") },
                    trailingContent = {
                        Switch(
                            checked = feedScopeEnabled,
                            onCheckedChange = debugToggles::setFeedScopeEnabled,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    headlineContent = { Text("Διόρθωση καθυστέρησης scroll στην εκκίνηση") },
                    supportingContent = { Text("Απενεργοποίησέ το για δοκιμή αν υποψιάζεσαι ότι προκαλεί άλλο πρόβλημα") },
                    trailingContent = {
                        Switch(
                            checked = scrollRestoreFixEnabled,
                            onCheckedChange = debugToggles::setScrollRestoreFixEnabled,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Checkboxes for whichever icons Facebook's own top tab bar actually has on the
 * current page (Home, Watch, Marketplace, Notifications, Menu, ...) — the list comes
 * from tab_visibility.js reporting the live aria-labels it found, not a hardcoded
 * guess, since the bar's contents can differ across accounts/rollouts. Checking one
 * hides it (keeping its layout slot as empty space) and, if it's the first one
 * hidden, also relocates our own Settings entry point there instead of ever
 * overlaying a still-visible native icon — see nav_override.js/tab_visibility.js.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabIconsScreen(
    onBack: () -> Unit,
    tabPreferences: TabPreferences,
) {
    val discoveredTabs by tabPreferences.discoveredTabs.collectAsState()
    val hiddenTabs by tabPreferences.hiddenTabs.collectAsState()

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Εικονίδια πάνω μπάρας") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Πίσω")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    "Επίλεξε ποια εικονίδια της πάνω μπάρας του Facebook θέλεις να κρύβονται. " +
                        "Η θέση τους παραμένει κενή αντί να καταλαμβάνεται από κάποιο άλλο — το " +
                        "δικό μας εικονίδιο ρυθμίσεων εμφανίζεται εκεί μόλις κρύψεις το πρώτο.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                if (discoveredTabs.isEmpty()) {
                    Text(
                        "Δεν βρέθηκαν ακόμα εικονίδια — άνοιξε το feed για λίγο και ξαναγύρισε εδώ.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            items(discoveredTabs) { label ->
                val hidden = hiddenTabs.contains(label)
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = {
                        Checkbox(
                            checked = hidden,
                            onCheckedChange = { tabPreferences.setTabHidden(label, it) },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { tabPreferences.setTabHidden(label, !hidden) },
                )
            }
        }
    }
}

/**
 * The allow-list itself, split out from SettingsScreen so the fixed-size hub (update
 * check, sync entry point) isn't buried under a list of arbitrary, growing length.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowedSourcesScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val allowedPages by settingsViewModel.allowedPages.collectAsState()
    var newPageName by remember { mutableStateOf("") }
    var editingName by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Επιτρεπόμενες πηγές") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Πίσω")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    "Μόνο posts από ομάδες/σελίδες σε αυτή τη λίστα θα εμφανίζονται στο feed, " +
                        "ανεξάρτητα από το ποιος τα δημοσίευσε. Άδεια λίστα = εμφανίζονται όλα.\n\n" +
                        "Γράψε το όνομα ακριβώς όπως εμφανίζεται στην πρώτη γραμμή του post. " +
                        "Πάτα σε μια καταχώρηση για να την επεξεργαστείς.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = newPageName,
                        onValueChange = { newPageName = it },
                        label = { Text("Όνομα ομάδας ή σελίδας") },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        if (newPageName.isNotBlank()) {
                            settingsViewModel.addPage(newPageName)
                            newPageName = ""
                        }
                    }) {
                        Text("Προσθήκη")
                    }
                }
            }

            items(allowedPages.sorted()) { name ->
                ListItem(
                    headlineContent = { Text(name) },
                    trailingContent = {
                        IconButton(onClick = { settingsViewModel.removePage(name) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Αφαίρεση")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingName = name },
                )
            }
        }
    }

    val editing = editingName
    if (editing != null) {
        EditSourceDialog(
            currentName = editing,
            onDismiss = { editingName = null },
            onConfirm = { newName ->
                settingsViewModel.editPage(editing, newName)
                editingName = null
            },
        )
    }
}

@Composable
private fun EditSourceDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(currentName) { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Επεξεργασία πηγής") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Όνομα ομάδας ή σελίδας") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text("Αποθήκευση") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Άκυρο") } },
    )
}
