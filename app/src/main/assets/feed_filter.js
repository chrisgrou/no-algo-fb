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
// HIDING STRATEGY: three attempts failed before this one. The decisive measurement
// was the third: every row we'd picked reported computed display:none (59 of 59) and
// yet all those posts stayed fully visible. So the CSS was always being applied fine
// — the element we called "the row" simply isn't what paints the post.
//
// Rather than keep guessing which ancestor is the real post card, hiding is now
// self-correcting and verified against layout, not style: mark the candidate, then
// measure whether the avatar still occupies space, and if it does, climb one parent
// at a time until it genuinely doesn't. The climb stops at the scroller, so a
// mis-climb can never blank the whole feed. The declarative :has() rule is kept as a
// first line of defence since it survives row re-renders on its own.
(function () {
  if (window.__ffwInstalled) {
    window.__ffwRefreshAllowed && window.__ffwRefreshAllowed();
    return;
  }
  window.__ffwInstalled = true;

  var AVATAR_SELECTOR = '[data-testid^="post-profile-image-"]';
  var SCROLLER_SELECTOR = '[data-type="vscroller"]';
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
    var rule = SCROLLER_SELECTOR + ' > *:has(' + AVATAR_SELECTOR + ')';
    for (var i = 0; i < allowedList.length; i++) {
      rule += ':not(:has([aria-label="' + cssString(allowedList[i]) + ' Profile Picture"]))';
    }
    return rule + '{display:none !important;}' +
      '[' + HIDDEN_ATTR + '="1"]{display:none !important;}';
  }

  function refreshRule() {
    styleEl().textContent = buildRule();
  }

  // Each avatar's own nearest scroller ancestor, rather than the document's first
  // one: nothing guarantees the page keeps a single feed container.
  function scrollerFor(el) {
    return el.closest(SCROLLER_SELECTOR);
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

  // Whether an element still occupies space on screen. This — not the row's computed
  // style — is the only trustworthy check: a previous attempt had every row we picked
  // reporting display:none while the posts stayed fully visible, i.e. the row we
  // picked was not what actually paints.
  function occupiesSpace(el) {
    var rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  }

  // Guards the climb: an ancestor that also contains an allowed author's post must
  // never be hidden, or one unwanted post would take a wanted one down with it.
  function containsAllowedAvatar(node) {
    if (allowed.size === 0) return false;
    var list = node.querySelectorAll(AVATAR_SELECTOR);
    for (var k = 0; k < list.length; k++) {
      var n = authorNameFor(list[k]);
      if (n && allowed.has(n)) return true;
    }
    return false;
  }

  // Hides the post by climbing from its avatar until the avatar genuinely stops
  // occupying space, instead of trusting any one ancestor to be "the row". Stops at
  // the scroller, and at any ancestor holding allowed content, so a mis-climb can
  // never blank the whole feed or a post the user asked to see.
  function hideFrom(avatar, row, scroller) {
    if (row && !containsAllowedAvatar(row)) {
      row.setAttribute(HIDDEN_ATTR, '1');
      if (!occupiesSpace(avatar)) return true;
    }
    var node = avatar;
    var guard = 0;
    while (node && node !== scroller && node !== document.body && guard++ < 25) {
      if (containsAllowedAvatar(node)) return false;
      node.setAttribute(HIDDEN_ATTR, '1');
      if (!occupiesSpace(avatar)) return true;
      node = node.parentElement;
    }
    return false;
  }

  function unhideFrom(avatar, row, scroller) {
    if (row) row.removeAttribute(HIDDEN_ATTR);
    var node = avatar;
    var guard = 0;
    while (node && node !== scroller && node !== document.body && guard++ < 25) {
      node.removeAttribute(HIDDEN_ATTR);
      node = node.parentElement;
    }
  }

  function applyFilter() {
    var avatars = document.querySelectorAll(AVATAR_SELECTOR);
    var seenRows = new Set();
    var resolvedCount = 0;
    var hiddenCount = 0;
    var verifiedHidden = 0;

    for (var i = 0; i < avatars.length; i++) {
      var avatar = avatars[i];
      var scroller = scrollerFor(avatar);
      if (!scroller) continue;

      var row = rowRootFor(avatar, scroller);
      // Dedupe by row where we found one (e.g. an avatar inside a shared/quoted post
      // shouldn't re-decide its container's visibility).
      if (row) {
        if (seenRows.has(row)) continue;
        seenRows.add(row);
      }

      var name = authorNameFor(avatar);
      // Unknown author (aria-label didn't match the expected pattern): leave
      // visible rather than risk hiding real content on an unanticipated shape.
      if (!name) continue;
      resolvedCount++;

      // Empty allow-list: show everything until the user configures it.
      if (allowed.size === 0 || allowed.has(name)) {
        unhideFrom(avatar, row, scroller);
      } else {
        hiddenCount++;
        if (hideFrom(avatar, row, scroller)) verifiedHidden++;
      }
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
