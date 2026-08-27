// Lets the user hide any icon in Facebook's own top tab bar (Feature: tab visibility),
// and marks a freed slot for nav_override.js to anchor our own Settings entry point in
// — never by overlaying a still-visible native icon. Discovers whatever tabs are
// actually on the current page (aria-label per [role="tab"]) rather than assuming a
// fixed list, and reports them to Kotlin so Settings can show real checkboxes for them.
(function () {
  if (window.__ffwTabVisibilityInstalled) return;
  window.__ffwTabVisibilityInstalled = true;

  var HIDDEN_ATTR = 'data-ffw-tab-hidden';

  if (!document.getElementById('ffw-tab-visibility-style')) {
    var style = document.createElement('style');
    style.id = 'ffw-tab-visibility-style';
    // visibility:hidden, not display:none — a hidden tab keeps its own layout space so
    // the bar doesn't reflow, and so its rect stays usable for nav_override.js to place
    // our Settings overlay over (a display:none tab collapses to a 0x0 rect).
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
    var key = list.join('');
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

  function apply() {
    var els = tabs();
    if (!els.length) return;
    var labels = [];
    var hidden = hiddenSet();
    for (var i = 0; i < els.length; i++) {
      var label = labelOf(els[i]);
      if (!label) continue;
      labels.push(label);
      if (hidden[label]) els[i].setAttribute(HIDDEN_ATTR, '1');
      else els[i].removeAttribute(HIDDEN_ATTR);
    }
    reportDiscovered(labels);
    // nav_override.js anchors its Settings overlay on whichever tab this just marked
    // hidden — re-sync it now rather than waiting for its own next scroll/resize/
    // observer tick, so toggling a checkbox in Settings relocates the overlay right
    // away instead of on a delay.
    window.__ffwSyncNavOverlay && window.__ffwSyncNavOverlay();
  }

  // Re-applies against the current hidden-set whenever the user flips a checkbox in
  // Settings — the same shape as feed_display.js's __ffwRefreshDisplay.
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
