package com.chrisgrou.fbfeedwrapper.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrisgrou.fbfeedwrapper.update.UpdateDialogHost
import com.chrisgrou.fbfeedwrapper.update.UpdateState
import com.chrisgrou.fbfeedwrapper.update.UpdateViewModel

/**
 * The app's single settings hub: the front screen shows only the gear icon that opens
 * this, and everything else is organized here instead of scattered as separate icons
 * over the feed. The allow-list and everything else beyond the core filtering feature
 * — hide-reactions, hide-suggested, the top-bar icon picker, the floating buttons —
 * live under "Βελτιώσεις" (Enhancements) instead of cluttering this hub directly, so
 * new optional extras have one obvious place to land as they're added. The update
 * check sits last: it's the row people touch least often, not the one they need to
 * find fastest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEnhancements: () -> Unit,
    onOpenDebug: () -> Unit,
    updateViewModel: UpdateViewModel = viewModel(),
) {
    val updateState by updateViewModel.state.collectAsState()

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
                    headlineContent = { Text("Βελτιώσεις") },
                    supportingContent = { Text("Επιτρεπόμενες πηγές, extra κουμπιά, εμφάνιση/απόκρυψη στοιχείων") },
                    leadingContent = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenEnhancements),
                )
                ListItem(
                    headlineContent = { Text("Debug") },
                    supportingContent = { Text("Πειραματικά features, toggles δοκιμής, αποστολή dump") },
                    leadingContent = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDebug),
                )
                ListItem(
                    headlineContent = { Text("Έλεγχος για ενημερώσεις") },
                    supportingContent = {
                        Text(if (updateState is UpdateState.Checking) "Έλεγχος..." else "Έλεγχος για νεότερη έκδοση στο GitHub")
                    },
                    leadingContent = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = updateViewModel::checkForUpdate),
                )
            }
        }
    }

    UpdateDialogHost(updateViewModel)
}

/**
 * Every optional, non-core-filtering extra lands here: the allow-list itself (its own
 * sub-screen, since it can grow arbitrarily long and isn't a fixed-size setting),
 * hide-reactions, hide-suggested, the floating return-to-top/prev-next buttons and
 * their shared size slider. The top-bar icon picker lives in Debug instead, next to
 * the toggle that turns the feature it configures on and off — it's still rough
 * enough that it doesn't belong alongside settled features like these.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancementsScreen(
    onBack: () -> Unit,
    onOpenAllowedSources: () -> Unit,
    displayPreferences: FeedDisplayPreferences,
) {
    val hideReactions by displayPreferences.hideReactions.collectAsState()
    val hideSuggested by displayPreferences.hideSuggested.collectAsState()
    val hidePeopleYouMayKnow by displayPreferences.hidePeopleYouMayKnow.collectAsState()
    val showScrollTopButton by displayPreferences.showScrollTopButton.collectAsState()
    val showPostNavButtons by displayPreferences.showPostNavButtons.collectAsState()
    val buttonSize by displayPreferences.buttonSize.collectAsState()

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Βελτιώσεις") },
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
                    headlineContent = { Text("Φιλτράρισμα") },
                    supportingContent = { Text("Ποιες ομάδες, σελίδες ή άτομα επιτρέπονται στο feed") },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAllowedSources),
                )
                ListItem(
                    headlineContent = { Text("Απόκρυψη reactions") },
                    supportingContent = { Text("Κρύβει τον αριθμό reactions κάτω από κάθε post και σχόλιο") },
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
                    supportingContent = { Text("Κρύβει τις προτεινόμενες ομάδες/σελίδες μέσα στο feed") },
                    trailingContent = {
                        Switch(
                            checked = hideSuggested,
                            onCheckedChange = displayPreferences::setHideSuggested,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    headlineContent = { Text("Απόκρυψη \"People you may know\"") },
                    supportingContent = { Text("Κρύβει τους προτεινόμενους φίλους μέσα στο feed") },
                    trailingContent = {
                        Switch(
                            checked = hidePeopleYouMayKnow,
                            onCheckedChange = displayPreferences::setHidePeopleYouMayKnow,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    headlineContent = { Text("Κουμπί επιστροφής στην κορυφή") },
                    supportingContent = { Text("Γυρνάει στην αρχή του feed") },
                    trailingContent = {
                        Switch(
                            checked = showScrollTopButton,
                            onCheckedChange = displayPreferences::setShowScrollTopButton,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    headlineContent = { Text("Κουμπιά προηγούμενο/επόμενο post") },
                    supportingContent = { Text("Πλοήγηση στο προηγούμενο ή το επόμενο post του feed") },
                    trailingContent = {
                        Switch(
                            checked = showPostNavButtons,
                            onCheckedChange = displayPreferences::setShowPostNavButtons,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    headlineContent = { Text("Μέγεθος κουμπιών") },
                    supportingContent = {
                        Column {
                            Text("Επιστροφή στην κορυφή & προηγούμενο/επόμενο post ($buttonSize dp)")
                            Slider(
                                value = buttonSize.toFloat(),
                                onValueChange = { displayPreferences.setButtonSize(it.toInt()) },
                                valueRange = MIN_BUTTON_SIZE.toFloat()..MAX_BUTTON_SIZE.toFloat(),
                                steps = (MAX_BUTTON_SIZE - MIN_BUTTON_SIZE) / 4 - 1,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
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
    onOpenTabIcons: () -> Unit,
    debugToggles: DebugToggles,
) {
    val feedScopeEnabled by debugToggles.feedScopeEnabled.collectAsState()
    val scrollRestoreFixEnabled by debugToggles.scrollRestoreFixEnabled.collectAsState()
    val statsBannerEnabled by debugToggles.statsBannerEnabled.collectAsState()
    val debugButtonEnabled by debugToggles.debugButtonEnabled.collectAsState()
    val topBarModEnabled by debugToggles.topBarModEnabled.collectAsState()

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
                    headlineContent = { Text("Εικονίδια πάνω μπάρας") },
                    supportingContent = {
                        Text(
                            "Δικό μας εικονίδιο Ρυθμίσεων + κρύψιμο/σειρά εικονιδίων στη μπάρα " +
                                "του Facebook. Πάτα για να ρυθμίσεις ποια εικονίδια φαίνονται — το " +
                                "διακόπτης ενεργοποιεί ολόκληρο το feature, ακόμα σε δοκιμή, " +
                                "απενεργοποιημένο από προεπιλογή.",
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Tab, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = topBarModEnabled,
                            onCheckedChange = debugToggles::setTopBarModEnabled,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenTabIcons),
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
 * Checkboxes and a drag handle for whichever icons Facebook's own top tab bar
 * actually has on the current page (Home, Watch, Marketplace, Notifications, Menu,
 * ...), plus a row standing in for our own Settings icon — the tab list comes from
 * tab_visibility.js reporting the live aria-labels it found, not a hardcoded guess,
 * since the bar's contents can differ across accounts/rollouts. Checking a native tab
 * hides it (its layout slot is reclaimed by the tabs around it); the Settings row has
 * no checkbox since it always needs to stay reachable somewhere, but drags exactly
 * like any other row. Dragging any row (by its handle) reorders
 * tabPreferences.tabOrder, which tab_visibility.js reads back to lay the real tab bar
 * — and this Settings row's own reserved slot — out in the same order (pure
 * width/margin-left rewrite, not real DOM reordering — see relayout() there for why
 * that's safe).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabIconsScreen(
    onBack: () -> Unit,
    tabPreferences: TabPreferences,
) {
    val discoveredTabs by tabPreferences.discoveredTabs.collectAsState()
    val hiddenTabs by tabPreferences.hiddenTabs.collectAsState()
    tabPreferences.tabOrder.collectAsState() // recompute baseOrder when this changes
    val baseOrder = tabPreferences.displayOrder(discoveredTabs)

    // Local, mutable copy driving the drag animation; re-seeded whenever the
    // underlying preference/discovery data actually changes (not on every
    // recomposition), so a drag in progress isn't reset mid-gesture by an unrelated
    // recomposition.
    var items by remember { mutableStateOf(baseOrder) }
    LaunchedEffect(baseOrder) { items = baseOrder }

    var draggedIndex by remember { mutableStateOf(-1) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Int, Int>() }

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
                    "Επίλεξε ποια εικονίδια της πάνω μπάρας του Facebook θέλεις να κρύβονται, ή " +
                        "σύρε τα (από τη λαβή δεξιά) για να αλλάξεις σειρά — μαζί με το δικό μας " +
                        "εικονίδιο ρυθμίσεων, που πάντα καταλαμβάνει τη δική του θέση χωρίς ποτέ να " +
                        "καλύπτει κάποιο ορατό εικονίδιο.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                if (items.size <= 1) {
                    Text(
                        "Δεν βρέθηκαν ακόμα εικονίδια — άνοιξε το feed για λίγο και ξαναγύρισε εδώ.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            itemsIndexed(items, key = { _, label -> label }) { index, label ->
                val isSettings = label == TabPreferences.SETTINGS_SENTINEL
                val hidden = hiddenTabs.contains(label)
                val offsetY = if (draggedIndex == index) dragOffsetY else 0f
                ListItem(
                    headlineContent = { Text(if (isSettings) "Ρυθμίσεις (δικό μας εικονίδιο)" else label) },
                    leadingContent = {
                        if (isSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = null)
                        } else {
                            Checkbox(
                                checked = hidden,
                                onCheckedChange = { tabPreferences.setTabHidden(label, it) },
                            )
                        }
                    },
                    trailingContent = {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Σύρε για αλλαγή σειράς",
                            modifier = Modifier.pointerInput(label) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggedIndex = index
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        draggedIndex = -1
                                        dragOffsetY = 0f
                                        tabPreferences.setOrder(items)
                                    },
                                    onDragCancel = {
                                        draggedIndex = -1
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val from = draggedIndex
                                        val rowHeight = itemHeights[from]
                                        if (rowHeight != null && rowHeight > 0) {
                                            dragOffsetY += dragAmount.y
                                            if (dragOffsetY > rowHeight / 2 && from < items.lastIndex) {
                                                items = items.toMutableList().apply { add(from + 1, removeAt(from)) }
                                                draggedIndex = from + 1
                                                dragOffsetY -= rowHeight
                                            } else if (dragOffsetY < -rowHeight / 2 && from > 0) {
                                                items = items.toMutableList().apply { add(from - 1, removeAt(from)) }
                                                draggedIndex = from - 1
                                                dragOffsetY += rowHeight
                                            }
                                        }
                                    },
                                )
                            },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { itemHeights[index] = it.size.height }
                        .graphicsLayer { translationY = offsetY },
                )
            }
        }
    }
}

/**
 * The allow-list itself, plus the entry point for scanning it in from Facebook's own
 * groups/pages lists (SourceSyncScreen) — the two used to be separate rows in the main
 * Settings hub, but importing only ever makes sense as a way of filling out this exact
 * list, so it lives here now as an action on this screen instead. Split out from
 * EnhancementsScreen so the fixed-size list of toggles up there isn't buried under a
 * list of arbitrary, growing length.
 *
 * Despite the name, an entry here isn't limited to a group or page: feed_filter.js
 * matches whatever text sits in a post's own first link, which for a friend's post
 * that isn't shared through a group is that friend's own name — so adding a person's
 * name works exactly the same way. The copy on this screen says so explicitly rather
 * than leaving "sources" to imply groups/pages only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowedSourcesScreen(
    onBack: () -> Unit,
    onOpenSync: () -> Unit,
    displayPreferences: FeedDisplayPreferences,
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val allowedPages by settingsViewModel.allowedPages.collectAsState()
    val filteringEnabled by displayPreferences.filteringEnabled.collectAsState()
    var newPageName by remember { mutableStateOf("") }
    var editingName by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Φιλτράρισμα") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Πίσω")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSync) {
                        Icon(Icons.Filled.CloudSync, contentDescription = "Εισαγωγή από ομάδες, σελίδες ή φίλους")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Ενεργοποίηση φιλτραρίσματος") },
                    supportingContent = {
                        Text(
                            if (filteringEnabled) {
                                "Μόνο posts από την παρακάτω λίστα εμφανίζονται στο feed"
                            } else {
                                "Ανενεργό — εμφανίζονται όλα τα posts του feed"
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = filteringEnabled,
                            onCheckedChange = displayPreferences::setFilteringEnabled,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (filteringEnabled) {
                item {
                    Text(
                        "Δεν είναι μόνο ομάδες και σελίδες: μπορείς να προσθέσεις και το όνομα " +
                            "ενός συγκεκριμένου φίλου/ατόμου που ακολουθείς, με τον ίδιο ακριβώς " +
                            "τρόπο. Πρόσθεσε μία-μία παρακάτω, ή πάτα το εικονίδιο πάνω δεξιά για " +
                            "να σαρώσεις κατευθείαν από μια λίστα του Facebook (ομάδες, σελίδες ή " +
                            "φίλοι).",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newPageName,
                            onValueChange = { newPageName = it },
                            label = { Text("Όνομα ομάδας, σελίδας ή ατόμου") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                if (newPageName.isNotBlank()) {
                                    settingsViewModel.addPage(newPageName)
                                    newPageName = ""
                                }
                            },
                            enabled = newPageName.isNotBlank(),
                        ) {
                            Text("Προσθήκη")
                        }
                    }
                    if (allowedPages.isNotEmpty()) {
                        Text(
                            "${allowedPages.size} πηγές — πάτα σε μία για να την επεξεργαστείς",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                        )
                    } else {
                        Text(
                            "Δεν έχεις προσθέσει καμία πηγή ακόμα.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                items(allowedPages.sorted()) { name ->
                    ListItem(
                        headlineContent = { Text(name) },
                        leadingContent = { Icon(Icons.Filled.Groups, contentDescription = null) },
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
                label = { Text("Όνομα ομάδας, σελίδας ή ατόμου") },
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
