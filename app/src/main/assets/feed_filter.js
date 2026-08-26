// Feed filtering (Feature 1). Injected by FbWebViewClient.onPageFinished on every
// page load. Hides feed posts whose author isn't in the user's allowed-pages list
// (read from native via window.NativeFilter.getAllowedAuthorsJson()).
//
// SELECTOR NOTES (verified on-device against the real m.facebook.com "Mbasic Lite"
// markup, which has no semantic HTML at all — everything is a generically-classed
// div in a component tree keyed by data-mcomponent/data-comp-id):
//   - Every feed unit (post or ad) carries an avatar element with
//     data-testid="post-profile-image-<n>" whose aria-label is "<Author> Profile
//     Picture" — this is the one reasonably stable anchor found.
//   - The scrollable feed itself is the single element with data-type="vscroller";
//     its direct children are the top-level "rows" (header, stories, composer, and
//     each post/ad). A post's row is found by walking up from its avatar element
//     until the parent is the vscroller itself, regardless of how deep the avatar
//     is nested inside that row (varies by post type: photo/video/link/shared post).
// This is still Facebook's private, frequently-changing internal markup — expect it
// to need re-verification (via the debug HTML-dump button, see MainActivity) if
// filtering silently stops working again.
(function () {
  if (window.__ffwInstalled) {
    window.__ffwRefreshAllowed && window.__ffwRefreshAllowed();
    return;
  }
  window.__ffwInstalled = true;

  var AVATAR_SELECTOR = '[data-testid^="post-profile-image-"]';
  var NAME_SUFFIX = / Profile Picture$/;

  function getAllowed() {
    try {
      return new Set(JSON.parse(window.NativeFilter.getAllowedAuthorsJson()));
    } catch (e) {
      return new Set();
    }
  }

  var allowed = getAllowed();

  function getScroller() {
    return document.querySelector('[data-type="vscroller"]');
  }

  // Walks up from a post's avatar element to the row that is a direct child of the
  // vscroller — i.e. the whole post card, however deeply the avatar sits inside it.
  function rowRootFor(el, scroller) {
    var node = el;
    while (node && node.parentElement && node.parentElement !== scroller) {
      node = node.parentElement;
    }
    return node && node.parentElement === scroller ? node : null;
  }

  function authorNameFor(avatarEl) {
    var label = avatarEl.getAttribute('aria-label') || '';
    var name = label.replace(NAME_SUFFIX, '');
    return name && name !== label ? name.trim() : null;
  }

  function applyFilter() {
    var scroller = getScroller();
    if (!scroller) return;

    var avatars = document.querySelectorAll(AVATAR_SELECTOR);
    var seenRows = new Set();
    var resolvedCount = 0;

    for (var i = 0; i < avatars.length; i++) {
      var row = rowRootFor(avatars[i], scroller);
      // Not a direct feed row (e.g. an avatar inside a shared/quoted post) — the
      // outer row's own avatar already decides visibility for the whole card.
      if (!row || seenRows.has(row)) continue;
      seenRows.add(row);

      var name = authorNameFor(avatars[i]);
      // Unknown author (aria-label didn't match the expected pattern): leave
      // visible rather than risk hiding real content on an unanticipated shape.
      if (!name) continue;
      resolvedCount++;

      // Empty allow-list: show everything until the user configures it.
      var isAllowed = allowed.size === 0 || allowed.has(name);
      // display:none (not visibility:hidden) so hidden posts collapse fully,
      // leaving no gap in the feed, per the project's filtering requirement.
      row.style.display = isAllowed ? '' : 'none';
    }

    // Visible in chrome://inspect's console, or via the debug HTML-dump button —
    // use this to tell whether AVATAR_SELECTOR still matches anything.
    console.log('[ffw] post rows matched:', seenRows.size, '| authors resolved:', resolvedCount);
  }

  window.__ffwRefreshAllowed = function () {
    allowed = getAllowed();
    applyFilter();
  };

  // The feed loads/scrolls in progressively; debounce so a burst of DOM mutations
  // triggers one re-filter pass instead of dozens.
  var pending = null;
  var observer = new MutationObserver(function () {
    if (pending) return;
    pending = requestAnimationFrame(function () {
      pending = null;
      applyFilter();
    });
  });
  observer.observe(document.body, { childList: true, subtree: true });

  applyFilter();
})();
