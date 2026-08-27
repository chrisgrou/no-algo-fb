// Replaces Facebook's own Marketplace tab, in its always-present top tab bar, with a
// Settings entry point (Feature: nav override). Facebook's tab bar survives every
// in-page navigation Facebook itself does (pull-to-refresh included, unlike our own
// canGoBack-based floating icon, which pull-to-refresh permanently hides — see
// MainActivity), so anchoring Settings there instead is the reliable fix.
(function () {
  if (window.__ffwNavInstalled) return;
  window.__ffwNavInstalled = true;

  var GEAR = '⚙';

  // A real Material "settings" gear glyph, drawn with currentColor so it inherits
  // whatever color the tab bar's other (active/inactive) icons use — the Unicode ⚙
  // character alone rendered as a thin, mismatched glyph next to Facebook's filled
  // icon set, which is why this is a full replacement rather than a text swap.
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

  function relabel(tab) {
    if (tab.__ffwRelabelled) return;
    tab.__ffwRelabelled = true;
    tab.setAttribute('aria-label', 'Ρυθμίσεις');

    // The icon is rendered one of a few ways depending on Facebook's build: an inline
    // <svg>, an <img> (sprite/data-uri), or a leaf element using a private icon font
    // via mask-image/background-image/a mapped codepoint. Handle the SVG/img cases by
    // swapping in a real gear icon of our own; fall back to a text glyph otherwise.
    var iconNode = tab.querySelector('svg, img');
    if (iconNode) {
      iconNode.replaceWith(gearSvg());
    } else {
      // An on-device capture found the real markup: the icon glyph itself sits in a
      // `.native-text` element (an MComponent "ServerTextArea"), same as the tab's
      // notification-count badge does — but the badge's copy carries an extra
      // `ref-key` class ours doesn't. Picking "the deepest leaf" without that
      // distinction landed on the (usually empty/hidden) badge span instead of the
      // actually-visible icon glyph, leaving the real icon untouched.
      var candidates = tab.querySelectorAll('.native-text');
      var target = null;
      for (var i = 0; i < candidates.length; i++) {
        if (!candidates[i].classList.contains('ref-key')) {
          target = candidates[i];
          break;
        }
      }

      // Fall back to the old "deepest leaf anywhere in the tab" heuristic if that
      // more specific markup isn't present (a future Facebook build, a differently
      // laid out tab) rather than doing nothing.
      if (!target) {
        (function () {
          function deepestLeaf(el) {
            var kids = el.children;
            if (!kids || kids.length === 0) return el;
            var best = el;
            for (var i = 0; i < kids.length; i++) {
              var candidate = deepestLeaf(kids[i]);
              if (candidate !== el) best = candidate;
            }
            return best;
          }
          target = deepestLeaf(tab);
        })();
      }

      var leaf = target.querySelector('span') || target;
      leaf.style.backgroundImage = 'none';
      leaf.style.maskImage = 'none';
      leaf.style.webkitMaskImage = 'none';
      leaf.textContent = GEAR;
      // Reset the font so the private icon font doesn't remap this Unicode codepoint
      // to an unrelated glyph from its own private-use-area mapping.
      leaf.style.fontFamily = 'sans-serif';
      leaf.style.fontSize = '20px';
      leaf.style.display = 'flex';
      leaf.style.alignItems = 'center';
      leaf.style.justifyContent = 'center';
    }

    // Capture phase + stopImmediatePropagation so this runs and wins before any of
    // Facebook's own listeners on the tab (or its ancestors) navigate to Marketplace.
    tab.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopImmediatePropagation();
      window.NativeNav && window.NativeNav.requestOpenSettings();
    }, true);
  }

  function apply() {
    var tab = findMarketplaceTab();
    if (tab) relabel(tab);
  }

  apply();

  // The tab bar can re-render (e.g. after Facebook's own pull-to-refresh navigation),
  // dropping our relabelling — watch for that and reapply.
  var observer = new MutationObserver(function () {
    apply();
  });
  observer.observe(document.body, { childList: true, subtree: true });
})();
