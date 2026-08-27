// Puts a Settings entry point where Facebook's own Marketplace tab sits, in its
// top tab bar (Feature: nav override). Never writes to Facebook's own DOM — an
// earlier version mutated the Marketplace tab directly (replaceWith on its icon,
// overwriting a leaf's textContent) and the *entire* tab bar started disappearing
// after a pull-to-refresh, consistent with React's reconciler choking on a
// removeChild/replaceChild it didn't expect. This version only ever reads layout
// (getBoundingClientRect/getComputedStyle) and paints an independent, opaque button
// of our own on top of the tab (same position/size), synced to its real position.
//
// The tab bar itself can still go missing (Facebook's own stuck-hidden bug — see
// nav_bar_watchdog.js, injected alongside this and loaded first, which unsticks it);
// this script just tracks whatever position/visibility the tab currently has and
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

  function findMarketplaceTab() {
    var tabs = document.querySelectorAll('[role="tab"]');
    for (var i = 0; i < tabs.length; i++) {
      var label = (tabs[i].getAttribute('aria-label') || '').toLowerCase();
      if (label.indexOf('marketplace') !== -1) return tabs[i];
    }
    return null;
  }

  var overlay = null;
  var trackedTab = null;

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
  function backgroundBehind(tab) {
    var node = tab.parentElement;
    while (node && node !== document.body) {
      var bg = getComputedStyle(node).backgroundColor;
      if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') return bg;
      node = node.parentElement;
    }
    return '#242526';
  }

  // Mirrors the tab's own position/size/visibility onto the overlay every time this
  // runs, rather than once — the tab bar can reflow (rotation, keyboard, Facebook's
  // own re-renders) and can vanish outright (see nav_bar_watchdog.js). When that
  // happens the tab's rect collapses to nothing, so hiding the overlay in that case
  // keeps our button from floating in a stale position over content that's no longer
  // a tab bar.
  function sync() {
    var tab = findMarketplaceTab() || trackedTab;
    if (!tab || !document.body.contains(tab)) {
      if (overlay) overlay.style.display = 'none';
      trackedTab = null;
      return;
    }
    trackedTab = tab;
    var rect = tab.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) {
      if (overlay) overlay.style.display = 'none';
      return;
    }
    var el = ensureOverlay();
    el.style.left = rect.left + 'px';
    el.style.top = rect.top + 'px';
    el.style.width = rect.width + 'px';
    // -2px, not the full rect.height: an on-device capture found each tab carries a
    // thin (2px) divider strip of its own — a real child element, its own exact
    // color — that visually lines up with its neighbors to look like one continuous
    // bar under the whole row. Covering it with our own approximated background
    // color broke that line right under this tab. Leaving the bottom 2px of the real
    // tab uncovered lets its own genuine divider strip keep showing through instead.
    el.style.height = Math.max(0, rect.height - 2) + 'px';
    el.style.background = backgroundBehind(tab);
    el.style.display = 'flex';
  }

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
})();
