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

  var ORIG_WIDTH_ATTR = 'data-ffw-tab-orig-width';
  var ORIG_MARGIN_ATTR = 'data-ffw-tab-orig-margin';

  // Facebook lays these tabs out with an explicit per-tab pixel width and a
  // cumulative margin-left (not flex-grow) — an on-device capture confirmed it: each
  // tab carries e.g. style="width:74px; margin-left:374px", 374 being the sum of the
  // widths of every tab before it. The visibility:hidden rule above correctly reserves
  // ONE slot (for nav_override.js's Settings overlay to sit on), but doesn't reclaim
  // space for anything hidden *beyond* that — those extra hidden tabs just keep their
  // own width, leaving a matching gap at the right edge of the bar (confirmed: hiding
  // two tabs left a two-tab-wide gap, not just an overlay in the wrong place). This
  // redistributes the same width+margin-left numbers Facebook itself uses across
  // whichever tabs remain visible (plus the one reserved anchor slot), so the bar
  // always fills edge-to-edge regardless of how many tabs are hidden.
  function relayout(tablist, els, hidden) {
    for (var i = 0; i < els.length; i++) {
      var t = els[i];
      if (!t.hasAttribute(ORIG_WIDTH_ATTR)) {
        t.setAttribute(ORIG_WIDTH_ATTR, t.style.width || '');
        t.setAttribute(ORIG_MARGIN_ATTR, t.style.marginLeft || '');
      }
    }

    var hiddenEls = [];
    for (var i = 0; i < els.length; i++) {
      if (hidden[labelOf(els[i])]) hiddenEls.push(els[i]);
    }

    if (hiddenEls.length === 0) {
      for (var i = 0; i < els.length; i++) {
        els[i].style.width = els[i].getAttribute(ORIG_WIDTH_ATTR);
        els[i].style.marginLeft = els[i].getAttribute(ORIG_MARGIN_ATTR);
      }
      return;
    }

    // First hidden tab in DOM order is the one nav_override.js anchors the Settings
    // overlay on (see findFreedTab() there) — it needs a real, non-zero slot. Every
    // other hidden tab collapses to nothing, its width folded into the slots that
    // remain.
    var anchor = hiddenEls[0];
    var slotWidth = tablist.getBoundingClientRect().width;
    if (!slotWidth) return;
    var slots = [];
    for (var i = 0; i < els.length; i++) {
      var t = els[i];
      if (t === anchor || !hidden[labelOf(t)]) slots.push(t);
    }
    var each = slotWidth / slots.length;

    var cumulative = 0;
    for (var i = 0; i < els.length; i++) {
      var t = els[i];
      if (slots.indexOf(t) === -1) {
        t.style.width = '0px';
        t.style.marginLeft = '0px';
        continue;
      }
      var w = Math.round(each);
      t.style.width = w + 'px';
      t.style.marginLeft = Math.round(cumulative) + 'px';
      cumulative += w;
    }
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
    if (tablist) relayout(tablist, els, hidden);
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
