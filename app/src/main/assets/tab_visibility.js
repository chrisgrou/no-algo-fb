// Lets the user hide any icon in Facebook's own top tab bar (Feature: tab visibility),
// and marks a freed slot for nav_override.js to anchor our own Settings entry point in
// — never by overlaying a still-visible native icon. Discovers whatever tabs are
// actually on the current page (aria-label per [role="tab"]) rather than assuming a
// fixed list, and reports them to Kotlin so Settings can show real checkboxes for them.
(function () {
  if (window.__ffwTabVisibilityInstalled) return;
  window.__ffwTabVisibilityInstalled = true;

  var HIDDEN_ATTR = 'data-ffw-tab-hidden';
  // Set on exactly one tab — whichever one is currently reserved for nav_override.js's
  // Settings overlay — separately from HIDDEN_ATTR (every hidden tab carries that, not
  // just the anchor). Needed once reordering entered the picture: nav_override.js used
  // to just grab "the first data-ffw-tab-hidden in DOM order", which agreed with this
  // script's own choice of anchor only as long as visual order matched DOM order. A
  // custom order can now visually place a hidden tab ahead of others that are still
  // earlier in the DOM, so the two scripts need to agree on the SAME element by
  // querying this dedicated attribute instead of re-deriving it independently.
  var ANCHOR_ATTR = 'data-ffw-tab-anchor';

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

  // The saved order is just whichever labels the user has explicitly rearranged, not
  // necessarily every tab on the page — this walks it first, then appends anything
  // discovered but not mentioned (in its own natural DOM order) at the end, the same
  // "explicit override, sensible fallback for the rest" shape hiddenSet() already has.
  function orderedTabs(els) {
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
    for (var i = 0; i < saved.length; i++) {
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
    return result;
  }

  function isDefaultOrder(ordered, els) {
    if (ordered.length !== els.length) return false;
    for (var i = 0; i < els.length; i++) {
      if (ordered[i] !== els[i]) return false;
    }
    return true;
  }

  var ORIG_WIDTH_ATTR = 'data-ffw-tab-orig-width';
  var ORIG_MARGIN_ATTR = 'data-ffw-tab-orig-margin';

  // Facebook lays these tabs out with an explicit per-tab pixel width and a
  // cumulative margin-left (not flex-grow) — an on-device capture confirmed it: each
  // tab carries e.g. style="width:74px; margin-left:374px", 374 being the sum of the
  // widths of every tab before it. Since margin-left alone determines each tab's
  // horizontal position (nothing here depends on the tab's actual DOM index), walking
  // the tabs in a chosen visual order and assigning each one's width/margin-left along
  // that walk reorders them on screen without ever touching Facebook's own DOM
  // structure (no reordering, no removeChild/insertBefore — just the same category of
  // inline-style-only write nav_bar_watchdog.js already established is safe). The same
  // walk also reclaims space from every hidden tab beyond the one reserved anchor slot
  // (confirmed: hiding two tabs used to leave a two-tab-wide gap, not just an overlay
  // in the wrong place), so the bar always fills edge-to-edge regardless of how many
  // tabs are hidden or how they're ordered.
  function relayout(tablist, els, ordered, hidden) {
    for (var i = 0; i < els.length; i++) {
      var t = els[i];
      if (!t.hasAttribute(ORIG_WIDTH_ATTR)) {
        t.setAttribute(ORIG_WIDTH_ATTR, t.style.width || '');
        t.setAttribute(ORIG_MARGIN_ATTR, t.style.marginLeft || '');
      }
      t.removeAttribute(ANCHOR_ATTR);
    }

    var hiddenEls = [];
    for (var i = 0; i < ordered.length; i++) {
      if (hidden[labelOf(ordered[i])]) hiddenEls.push(ordered[i]);
    }

    if (hiddenEls.length === 0 && isDefaultOrder(ordered, els)) {
      for (var i = 0; i < els.length; i++) {
        els[i].style.width = els[i].getAttribute(ORIG_WIDTH_ATTR);
        els[i].style.marginLeft = els[i].getAttribute(ORIG_MARGIN_ATTR);
      }
      return;
    }

    // First hidden tab in visual order gets the one reserved, non-zero slot — see
    // ANCHOR_ATTR above for why nav_override.js needs this marked explicitly rather
    // than re-deriving "the first hidden tab" itself. Every other hidden tab collapses
    // to nothing, its width folded into the slots that remain.
    var anchor = hiddenEls.length > 0 ? hiddenEls[0] : null;
    if (anchor) anchor.setAttribute(ANCHOR_ATTR, '1');
    var slotWidth = tablist.getBoundingClientRect().width;
    if (!slotWidth) return;
    var slots = [];
    for (var i = 0; i < ordered.length; i++) {
      var t = ordered[i];
      if (t === anchor || !hidden[labelOf(t)]) slots.push(t);
    }
    var each = slotWidth / slots.length;

    var cumulative = 0;
    for (var i = 0; i < ordered.length; i++) {
      var t = ordered[i];
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
    if (tablist) relayout(tablist, els, orderedTabs(els), hidden);
    reportDiscovered(labels);
    // nav_override.js anchors its Settings overlay on whichever tab this just marked
    // as the anchor — re-sync it now rather than waiting for its own next scroll/
    // resize/observer tick, so toggling or reordering in Settings relocates the
    // overlay right away instead of on a delay.
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
