# PROJECT_CONTENT.md — FB Feed Wrapper (Android)

## Στόχος
Android εφαρμογή που λειτουργεί σαν wrapper για το m.facebook.com, με σκοπό:
- Αποφυγή του tracking της επίσημης Facebook εφαρμογής (personal use)
- Φιλτράρισμα feed ώστε να εμφανίζονται μόνο posts από σελίδες/χρήστες σε λίστα που ορίζει ο χρήστης
- Διατήρηση scroll position / αποφυγή συχνού reload σε app switching
- Δυνατότητα σχολιασμού σε posts (κανονική λειτουργία μέσω WebView)
- Δυνατότητα αποθήκευσης media (εικόνες/βίντεο) τοπικά

## Αρχιτεκτονική
- **Πλατφόρμα:** Native Android, Kotlin
- **Πυρήνας:** WebView που φορτώνει m.facebook.com
- **Min SDK:** ~26 (Android 8.0), χωρίς ιδιαίτερο περιορισμό — γενικός στόχος συμβατότητας
- **Session:** Persistent cookies μέσω CookieManager (όχι clear on close) — ώστε να μη χρειάζεται login κάθε φορά

## Feature 1: Φιλτράρισμα Feed
- Injected JavaScript (MutationObserver) παρακολουθεί το DOM του feed
- Κάθε post ελέγχεται ως προς τον author/σελίδα προέλευσης
- Αν ο author δεν βρίσκεται στη λίστα "επιτρεπόμενων σελίδων", το post αποκρύπτεται πλήρως (collapse, όχι απλό visibility:hidden — να μην αφήνει κενό)
- Η λίστα είναι **βάσει ονομάτων σελίδων** (display name matching στο DOM)
- Ο χρήστης μπορεί να προσθέτει/αφαιρεί ονόματα μέσω native settings UI εντός της εφαρμογής
- Λίστα αποθηκεύεται τοπικά (SharedPreferences ή DataStore/SQLite — να αποφασιστεί στο coding stage)
- **Ρίσκο συντήρησης:** Το DOM/class names του Facebook αλλάζουν συχνά· το JS matching θα χρειάζεται περιοδική προσαρμογή

## Feature 2: Διατήρηση Scroll Position
- Πρόβλημα: Android σκοτώνει το process σε background/app switching, χάνοντας scroll θέση, WebView κάνει reload
- Λύση:
  - Injected JS καταγράφει `window.scrollY` περιοδικά (interval ή on-scroll) → αποθηκεύεται native-side
  - Σε resume: μόλις φορτώσει η σελίδα, JS κάνει scroll στην αποθηκευμένη θέση
  - `WebView.saveState()`/`restoreState()` σε `onSaveInstanceState` για navigation history
  - Σωστό lifecycle handling (launchMode, αποφυγή περιττών Activity recreations) για μείωση συχνότητας process kill
  - Δεν υπάρχει 100% εγγύηση (OS-level περιορισμός), αλλά μειώνεται δραστικά η συχνότητα

## Feature 3: Σχόλια
- Λειτουργεί κανονικά μέσω WebView (standard interaction με τη σελίδα) — δεν μπλοκάρεται από filtering JS

## Feature 4: Αποθήκευση Media
- `setDownloadListener` στο WebView για downloads
- Long-press context menu override για εικόνες/βίντεο
- Αποθήκευση στη Gallery μέσω MediaStore API

## Feature 5: Auto-sync Groups (optional/μελλοντικό)
- Injected JS διαβάζει τη λίστα "Your groups" από facebook.com/groups/ (ή m.facebook.com αντίστοιχο)
- Χρειάζεται auto-scroll μέχρι το τέλος της λίστας πριν το parsing (lazy-load/infinite scroll)
- Τα ονόματα groups προστίθενται αυτόματα στη λίστα επιτρεπόμενων πηγών (μαζί με χειροκίνητες σελίδες)
- Trigger: on-demand μέσω κουμπιού "Sync groups" στο settings UI (όχι σε κάθε άνοιγμα app)
- Ξεχωριστό DOM parsing logic από αυτό του feed/σελίδων· πιθανή ασυμφωνία σε format ονόματος μεταξύ λίστας groups και εμφάνισης post στο feed — θα χρειαστεί προσαρμογή/testing

## Γνωστοί Περιορισμοί / Ρίσκα
- Ενδεχόμενη παραβίαση Terms of Service του Facebook (scraping/DOM manipulation εκτός επίσημου API) — ρίσκο μπλοκαρίσματος λογαριασμού
- Όχι για δημοσίευση σε Play Store — αποκλειστικά personal use / sideload
- DOM instability: το Facebook αλλάζει layout/classes συχνά, θα χρειάζονται fixes

## Αποφάσεις μέχρι στιγμής
| Θέμα | Απόφαση |
|---|---|
| WebView target | m.facebook.com (mobile) |
| Φιλτράρισμα | Λίστα ονομάτων σελίδων, add/remove από χρήστη |
| Session | Persistent cookies |
| Hide method | Πλήρης απόκρυψη (collapse) |
| Min SDK | ~26, χωρίς αυστηρό περιορισμό |
| Scroll persistence | Ναι, απαιτείται |
| Comments | Ναι, native μέσω WebView |
| Media save | Ναι, μέσω MediaStore |
| Auto-sync groups | Ναι, ως Feature 5 (optional/μελλοντικό) |

## Επόμενα Βήματα (στο Claude Code)
1. Αρχικό project scaffold (Kotlin, single Activity + WebView)
2. Injected JS για filtering (MutationObserver + author detection)
3. Settings UI για τη λίστα σελίδων
4. Scroll position persistence mechanism
5. Media download handling
6. Testing σε πραγματικές συνθήκες (DOM selectors θα χρειαστούν επαλήθευση, καθώς το πραγματικό HTML του m.facebook.com δεν είναι διαθέσιμο εκ των προτέρων)
