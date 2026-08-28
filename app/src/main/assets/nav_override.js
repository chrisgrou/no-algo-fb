// Puts a Settings entry point in Facebook's own top tab bar (Feature: nav override).
// Never writes to Facebook's own DOM — an earlier version mutated the Marketplace tab
// directly (replaceWith on its icon, overwriting a leaf's textContent) and the
// *entire* tab bar started disappearing after a pull-to-refresh, consistent with
// React's reconciler choking on a removeChild/replaceChild it didn't expect. This
// version only ever reads layout (getBoundingClientRect/getComputedStyle) and paints
// an independent, opaque button of our own on top, synced to a position tab_visibility.js
// computes.
//
// Where it sits is never derived from a specific native tab's own rect — an earlier
// version anchored on "whichever tab is currently hidden", which needed this script
// and tab_visibility.js to agree on exactly which one that was, and broke once
// reordering could place a hidden tab visually ahead of others still earlier in the
// DOM. Instead tab_visibility.js's own relayout() always reserves the Settings icon
// its own slot (never overlaying a still-visible native icon) and records that slot's
// position as a *fraction* of the tab bar's width in window.__ffwSettingsSlotFrac;
// this script turns that back into real pixels against the bar's own live rect on
// every scroll/resize, so it stays correctly positioned between tab_visibility.js's
// own (debounced, mutation-triggered) passes.
//
// The tab bar itself can still go missing (Facebook's own stuck-hidden bug — see
// nav_bar_watchdog.js, injected alongside this and loaded first, which unsticks it);
// this script just tracks whatever position/visibility the bar currently has and
// doesn't need to know why it changed.
(function () {
  if (window.__ffwNavInstalled) return;
  window.__ffwNavInstalled = true;

  var GEAR_SVG_PATH =
    'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.04-.7-1.63-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.44.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.63.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.04.7 1.63.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.63-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z';

  function gearSvg() {
    var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('width', '24');
    svg.setAttribute('height', '24');
    svg.style.fill = 'currentColor';
    svg.style.display = 'block';
    var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', GEAR_SVG_PATH);
    svg.appendChild(path);
    return svg;
  }

  // Facebook's pull-to-refresh spinner isn't revealed by scrolling — it's a sibling
  // element whose own margin-top animates from a resting -36px up toward 0px as the
  // user drags, with no scroll event firing at all (an on-device capture caught it
  // resting at exactly "-36px"). Our overlay only re-syncs on scroll/resize/DOM
  // mutation, so during that drag it stays glued to its last position while the real
  // tab bar (and the spinner now appearing beside/under it) move — which is exactly
  // why the overlay was seen floating over, and hiding, the spinner. Hiding the
  // overlay for the duration of an active pull avoids covering it at all.
  function pullToRefreshActive() {
    var el = document.querySelector('.pull-to-refresh-spinner-container');
    if (!el) return false;
    var mt = parseFloat(el.style.marginTop);
    return !isNaN(mt) && mt > -30;
  }

  var overlay = null;

  function ensureOverlay() {
    if (overlay) return overlay;
    overlay = document.createElement('div');
    overlay.id = '__ffwSettingsOverlay';
    overlay.setAttribute('role', 'button');
    overlay.setAttribute('aria-label', 'Ρυθμίσεις');
    overlay.style.position = 'fixed';
    overlay.style.display = 'flex';
    overlay.style.alignItems = 'center';
    overlay.style.justifyContent = 'center';
    overlay.style.zIndex = '999999';
    overlay.style.color = '#ffffff';
    overlay.style.background = '#242526';
    overlay.appendChild(gearSvg());
    overlay.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      window.NativeNav && window.NativeNav.requestOpenSettings();
    });
    document.body.appendChild(overlay);
    return overlay;
  }

  // Facebook's own background behind the tab bar isn't a single fixed color (theme,
  // dark/light mode), so read it off a live ancestor rather than hardcoding it —
  // otherwise the overlay would sit on top as a visibly mismatched patch instead of
  // blending in like a real tab.
  function backgroundBehind(node) {
    var current = node.parentElement;
    while (current && current !== document.body) {
      var bg = getComputedStyle(current).backgroundColor;
      if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') return bg;
      current = current.parentElement;
    }
    return '#242526';
  }

  // Draws our own bottom divider rather than relying on any native tab's — the
  // Settings icon never sits on top of a real tab element anymore, so there's nothing
  // underneath to "peek through". Reads the tab bar's own live computed border-bottom
  // instead of a hardcoded color, same reasoning as backgroundBehind() above; if the
  // bar itself doesn't carry one (e.g. it's drawn some other way not reachable via
  // getComputedStyle, such as a box-shadow), this simply draws nothing rather than
  // guessing a color that might not match.
  function dividerBorder(tablist) {
    var cs = getComputedStyle(tablist);
    if (parseFloat(cs.borderBottomWidth) > 0 && cs.borderBottomColor && cs.borderBottomColor !== 'rgba(0, 0, 0, 0)') {
      return cs.borderBottomWidth + ' ' + cs.borderBottomStyle + ' ' + cs.borderBottomColor;
    }
    return '';
  }

  // Turns tab_visibility.js's fractional slot (window.__ffwSettingsSlotFrac) back into
  // real pixels against the tab bar's current rect — recomputed on every call, so this
  // stays correct across scroll/resize/reflow without needing tab_visibility.js's own
  // (debounced, mutation-triggered) pass to have just run.
  function sync() {
    if (pullToRefreshActive()) {
      if (overlay) overlay.style.display = 'none';
      return;
    }
    var tablist = document.querySelector('[role="tablist"]');
    var frac = window.__ffwSettingsSlotFrac;
    if (!tablist || !frac) {
      if (overlay) overlay.style.display = 'none';
      return;
    }
    var rect = tablist.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) {
      if (overlay) overlay.style.display = 'none';
      return;
    }
    var el = ensureOverlay();
    el.style.left = (rect.left + frac.leftFrac * rect.width) + 'px';
    el.style.top = rect.top + 'px';
    el.style.width = (frac.widthFrac * rect.width) + 'px';
    el.style.height = rect.height + 'px';
    el.style.background = backgroundBehind(tablist);
    el.style.borderBottom = dividerBorder(tablist);
    el.style.display = 'flex';
  }

  // Exposed so tab_visibility.js can trigger an immediate re-sync right after the user
  // hides/shows or reorders a tab in Settings, instead of waiting for the next
  // scroll/resize or the debounced MutationObserver below to happen to fire.
  window.__ffwSyncNavOverlay = sync;

  sync();

  // capture:true so this also sees scrolling on an internal scroller, not just the
  // window itself (scroll doesn't bubble, but a capturing listener still sees it on
  // the way down to the real target).
  window.addEventListener('scroll', sync, { passive: true, capture: true });
  window.addEventListener('resize', sync);

  // childList only, not attributes: an earlier version also observed attribute
  // mutations, and Facebook's initial feed load streams in constant inline-style
  // updates across a huge subtree — that fired this on nearly every one of them and
  // measurably slowed the cold-launch feed render. The tab bar appearing, moving, or
  // being removed always shows up as a childList change somewhere on the way; scroll
  // and resize already cover pure reflow.
  var syncTimer = null;
  var observer = new MutationObserver(function () {
    if (syncTimer) return;
    syncTimer = setTimeout(function () {
      syncTimer = null;
      sync();
    }, 150);
  });
  observer.observe(document.body, { childList: true, subtree: true });

  // The pull-to-refresh drag itself never fires a scroll event (see
  // pullToRefreshActive() above) — only this element's own style attribute changes as
  // the user drags and as it springs back afterwards. Watching it directly, scoped to
  // this one element rather than document-wide, is what actually catches the gesture
  // in time to hide the overlay during it and show it again right after — the
  // childList-only observer above wouldn't see either edge of this reliably.
  var pullSpinner = document.querySelector('.pull-to-refresh-spinner-container');
  if (pullSpinner) {
    var pullObserver = new MutationObserver(sync);
    pullObserver.observe(pullSpinner, { attributes: true, attributeFilter: ['style'] });
  }
})();
