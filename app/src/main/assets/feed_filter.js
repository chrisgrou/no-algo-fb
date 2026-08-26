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
//     each post/ad).
//
// HIDING STRATEGY: two earlier attempts (row.style.display, then a data-attribute
// plus a stylesheet rule) both reported the right rows as hidden while nothing
// actually disappeared — this framework re-renders rows and drops whatever we write
// onto the element. So the primary mechanism is now a single purely declarative CSS
// rule using :has(), which keeps working no matter how often the framework rebuilds
// a row, since nothing of ours has to survive on the element itself. The per-element
// attribute is still set as a fallback for WebViews without :has() support.
(function () {
  if (window.__ffwInstalled) {
    window.__ffwRefreshAllowed && window.__ffwRefreshAllowed();
    return;
  }
  window.__ffwInstalled = true;

  var AVATAR_SELECTOR = '[data-testid^="post-profile-image-"]';
  var NAME_SUFFIX = / Profile Picture$/;
  var HIDDEN_ATTR = 'data-ffw-hidden';
  var HAS_SUPPORT = (function () {
    try {
      return CSS.supports('selector(:has(*))');
    } catch (e) {
      return false;
    }
  })();

  function styleEl() {
    var el = document.getElementById('ffw-style');
    if (!el) {
      el = document.createElement('style');
      el.id = 'ffw-style';
      (document.head || document.documentElement).appendChild(el);
    }
    return el;
  }

  function cssString(s) {
    return s.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  }

  function getAllowed() {
    try {
      return JSON.parse(window.NativeFilter.getAllowedAuthorsJson());
    } catch (e) {
      return [];
    }
  }

  var allowedList = getAllowed();
  var allowed = new Set(allowedList);

  // "Every vscroller child that contains a post avatar, except those containing an
  // avatar of an allowed author." Purely declarative, so re-renders can't undo it.
  function buildRule() {
    if (!allowedList.length) return '[' + HIDDEN_ATTR + '="1"]{display:none !important;}';
    var rule = '[data-type="vscroller"] > *:has(' + AVATAR_SELECTOR + ')';
    for (var i = 0; i < allowedList.length; i++) {
      rule += ':not(:has([aria-label="' + cssString(allowedList[i]) + ' Profile Picture"]))';
    }
    return rule + '{display:none !important;}' +
      '[' + HIDDEN_ATTR + '="1"]{display:none !important;}';
  }

  function refreshRule() {
    styleEl().textContent = buildRule();
  }

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
    var hiddenCount = 0;
    var hiddenRows = [];

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
      if (isAllowed) {
        row.removeAttribute(HIDDEN_ATTR);
      } else {
        hiddenCount++;
        hiddenRows.push(row);
        row.setAttribute(HIDDEN_ATTR, '1');
      }
    }

    // Diagnostics: of the rows we decided to hide, how many are actually rendered as
    // display:none right now? A hiddenCount far above verifiedHidden means the page
    // is winning the fight over those elements, and the mechanism — not the
    // selectors — is what still needs work.
    var verifiedHidden = 0;
    for (var j = 0; j < hiddenRows.length; j++) {
      if (getComputedStyle(hiddenRows[j]).display === 'none') verifiedHidden++;
    }

    console.log('[ffw] rows:', seenRows.size, '| authors:', resolvedCount,
      '| hidden:', hiddenCount, '| verified:', verifiedHidden, '| :has():', HAS_SUPPORT);
    window.NativeFilter && window.NativeFilter.reportStats &&
      window.NativeFilter.reportStats(seenRows.size, resolvedCount, hiddenCount, verifiedHidden, HAS_SUPPORT);
  }

  window.__ffwRefreshAllowed = function () {
    allowedList = getAllowed();
    allowed = new Set(allowedList);
    refreshRule();
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

  refreshRule();
  applyFilter();
})();
