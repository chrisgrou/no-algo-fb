// Replaces Facebook's own Marketplace tab, in its always-present top tab bar, with a
// Settings entry point (Feature: nav override). Facebook's tab bar survives every
// in-page navigation Facebook itself does (pull-to-refresh included, unlike our own
// canGoBack-based floating icon, which pull-to-refresh permanently hides — see
// MainActivity), so anchoring Settings there instead is the reliable fix.
(function () {
  if (window.__ffwNavInstalled) return;
  window.__ffwNavInstalled = true;

  var GEAR = '⚙';

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

    // The glyph itself is usually rendered by an inner element carrying Facebook's
    // private icon font (a mask-image or a background-image sprite, or a single
    // codepoint mapped through that font) rather than plain readable text, so
    // swapping textContent alone tends to do nothing visible. Overwrite whichever
    // leaf element actually carries the icon: the deepest element with no element
    // children.
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

    var leaf = deepestLeaf(tab);
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
