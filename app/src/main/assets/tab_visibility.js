// Lets the user hide any icon in Facebook's own top tab bar and reorder everything —
// those icons plus our own Settings icon — (Feature: tab visibility). Discovers
// whatever tabs are actually on the current page (aria-label per [role="tab"]) rather
// than assuming a fixed list, and reports them to Kotlin so Settings can show real
// checkboxes/drag-reorder rows for them.
(function () {
  if (window.__ffwTabVisibilityInstalled) return;
  window.__ffwTabVisibilityInstalled = true;

  var HIDDEN_ATTR = 'data-ffw-tab-hidden';
  // Stands in for our own Settings icon wherever it sits in the order — not a real
  // Facebook tab, so it never matches a DOM element. relayout() below reserves it a
  // slot in the layout math the same as any real tab; nav_override.js then reads that
  // slot's position back out of window.__ffwSettingsSlotFrac. Must match the identical
  // literal in TabPreferences.SETTINGS_SENTINEL on the Kotlin side.
  var SETTINGS_SENTINEL = '__ffw_settings__';

  if (!document.getElementById('ffw-tab-visibility-style')) {
    var style = document.createElement('style');
    style.id = 'ffw-tab-visibility-style';
    // visibility:hidden, not display:none — a hidden tab keeps its own layout space so
    // the bar doesn't reflow mid-pass; relayout() below explicitly reclaims that space
    // itself instead of relying on removing it from flow (a display:none tab collapses
    // to a 0x0 rect, which would break the width/margin-left math relayout depends on).
    style.textContent =
      '[' + HIDDEN_ATTR + '="1"]{visibility:hidden !important;pointer-events:none !important;}';
    (document.head || document.documentElement).appendChild(style);
  }

  function tabs() {
    return document.querySelectorAll('[role="tablist"] [role="tab"]');
  }

  function labelOf(tab) {
    return (tab.getAttribute('aria-label') || '').trim();
  }

  var lastReported = null;

  function reportDiscovered(list) {
    var key = list.join('');
    if (key === lastReported) return;
    lastReported = key;
    try {
      window.NativeTabs && window.NativeTabs.reportTabs(JSON.stringify(list));
    } catch (e) {
      // No bridge (e.g. loaded outside the app) — nothing to report to.
    }
  }

  function hiddenSet() {
    try {
      var raw = window.NativeTabs ? window.NativeTabs.getHiddenTabs() : '[]';
      var arr = JSON.parse(raw);
      var set = {};
      for (var i = 0; i < arr.length; i++) set[arr[i]] = true;
      return set;
    } catch (e) {
      return {};
    }
  }

  // The saved order is just whichever labels (and possibly the Settings sentinel) the
  // user has explicitly rearranged, not necessarily everything currently on the page —
  // this walks it first, then appends anything discovered but not mentioned (in its
  // own natural DOM order), and finally the sentinel itself if it was never placed at
  // all (first run: it defaults to the end of the bar). Returns a list mixing real tab
  // elements and the literal SETTINGS_SENTINEL string.
  function combinedOrder(els) {
    var saved = [];
    try {
      saved = window.NativeTabs ? JSON.parse(window.NativeTabs.getTabOrder()) : [];
    } catch (e) {
      saved = [];
    }
    var byLabel = {};
    for (var i = 0; i < els.length; i++) {
      var label = labelOf(els[i]);
      if (label && !byLabel[label]) byLabel[label] = els[i];
    }
    var result = [];
    var used = {};
    var sawSentinel = false;
    for (var i = 0; i < saved.length; i++) {
      if (saved[i] === SETTINGS_SENTINEL) {
        if (!sawSentinel) {
          result.push(SETTINGS_SENTINEL);
          sawSentinel = true;
        }
        continue;
      }
      var t = byLabel[saved[i]];
      if (t && !used[saved[i]]) {
        result.push(t);
        used[saved[i]] = true;
      }
    }
    for (var i = 0; i < els.length; i++) {
      var label = labelOf(els[i]);
      if (label && !used[label]) {
        result.push(els[i]);
        used[label] = true;
      }
    }
    if (!sawSentinel) result.push(SETTINGS_SENTINEL);
    return result;
  }

  // Facebook lays these tabs out with an explicit per-tab pixel width and a cumulative
  // margin-left (not flex-grow) — an on-device capture confirmed it: each tab carries
  // e.g. style="width:74px; margin-left:374px", 374 being the sum of the widths of
  // every tab before it. Since margin-left alone determines each tab's horizontal
  // position (nothing here depends on the tab's actual DOM index), walking the tabs in
  // a chosen visual order and assigning each one's width/margin-left along that walk
  // reorders them on screen without ever touching Facebook's own DOM structure (no
  // reordering, no removeChild/insertBefore — just the same category of inline-style-
  // only write nav_bar_watchdog.js already established is safe). The Settings icon
  // always gets its own slot in the same walk — it's never anchored on a specific
  // native tab's rect the way earlier versions of this feature did, which needed
  // nav_override.js and this script to agree on exactly which tab that was; instead
  // this records the reserved slot's position as a *fraction* of the tab bar's width
  // (window.__ffwSettingsSlotFrac), which nav_override.js turns back into real pixels
  // against the bar's own live rect on every scroll/resize, decoupled from whenever
  // this script's own (debounced, mutation-triggered) pass last ran.
  function relayout(tablist, els, ordered, hidden) {
    var slots = [];
    for (var i = 0; i < ordered.length; i++) {
      var t = ordered[i];
      if (t === SETTINGS_SENTINEL || !hidden[labelOf(t)]) slots.push(t);
    }
    var tablistRect = tablist.getBoundingClientRect();
    if (!tablistRect.width) return;
    var each = tablistRect.width / slots.length;

    var cumulative = 0;
    var settingsFrac = null;
    for (var i = 0; i < ordered.length; i++) {
      var t = ordered[i];
      var isSlot = slots.indexOf(t) !== -1;
      if (t === SETTINGS_SENTINEL) {
        if (isSlot) {
          settingsFrac = { leftFrac: cumulative / tablistRect.width, widthFrac: each / tablistRect.width };
          cumulative += each;
        }
        continue;
      }
      if (!isSlot) {
        t.style.width = '0px';
        t.style.marginLeft = '0px';
        continue;
      }
      var w = Math.round(each);
      t.style.width = w + 'px';
      t.style.marginLeft = Math.round(cumulative) + 'px';
      cumulative += w;
    }
    window.__ffwSettingsSlotFrac = settingsFrac;
  }

  function apply() {
    var els = tabs();
    if (!els.length) return;
    var tablist = document.querySelector('[role="tablist"]');
    var labels = [];
    var hidden = hiddenSet();
    for (var i = 0; i < els.length; i++) {
      var label = labelOf(els[i]);
      if (!label) continue;
      labels.push(label);
      if (hidden[label]) els[i].setAttribute(HIDDEN_ATTR, '1');
      else els[i].removeAttribute(HIDDEN_ATTR);
    }
    if (tablist) relayout(tablist, els, combinedOrder(els), hidden);
    reportDiscovered(labels);
    // nav_override.js positions its Settings overlay from window.__ffwSettingsSlotFrac,
    // which this just updated — re-sync it now rather than waiting for its own next
    // scroll/resize/observer tick, so toggling or reordering in Settings relocates the
    // overlay right away instead of on a delay.
    window.__ffwSyncNavOverlay && window.__ffwSyncNavOverlay();
  }

  // Re-applies against the current hidden-set/order whenever the user flips a
  // checkbox or drags a row in Settings — the same shape as feed_display.js's
  // __ffwRefreshDisplay.
  window.__ffwRefreshTabs = apply;

  apply();

  var timer = null;
  var observer = new MutationObserver(function () {
    if (timer) return;
    timer = setTimeout(function () {
      timer = null;
      apply();
    }, 200);
  });
  // childList only, not attributes — see nav_override.js/feed_display.js for why:
  // broad attribute observation measurably slowed cold-launch feed render elsewhere in
  // this app, and the tab bar's own tabs appearing/changing always shows up as a
  // childList change somewhere on the way.
  observer.observe(document.body, { childList: true, subtree: true });
})();
