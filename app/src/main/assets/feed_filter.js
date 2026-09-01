// Feed filtering (Feature 1). Injected by FbWebViewClient.onPageFinished on every
// page load. Hides feed posts whose SOURCE — the group or page the post appears from,
// not whoever wrote it — isn't in the user's allow-list.
//
// WHAT COUNTS AS THE SOURCE: in a group post the header reads "<Group>" on the first
// line and the individual poster ("Marshall Evans", "Anonymous participant") on the
// second. The user follows groups and pages, not people, so the first line is what
// the allow-list is matched against. Earlier versions derived the name from the
// avatar's aria-label, which on group posts names the person — that is why whitelisted
// groups kept disappearing while their posts' authors were never in the list.
//
// MARKUP NOTES (from on-device captures of m.facebook.com's "Mbasic Lite" markup,
// which has no semantic HTML — everything is a generically-classed div in a component
// tree keyed by data-mcomponent/data-comp-id):
//   - A whole post/ad is wrapped in an element carrying data-tracking-duration-id,
//     whose height covers header AND body (e.g. 916px = 63px header + 389px body +
//     footer). This is the thing to hide.
//   - The source name is the first [role="link"] in the post: the group or page name.
//   - Some avatars carry data-testid="post-profile-image-<n>", but group posts and
//     some ad formats do not. An on-device capture found 57 post containers against
//     only 32 such avatars, and the ~25 posts with no matching avatar were exactly
//     the ones that kept getting through: anchoring the scan on avatars never looked
//     at them. So the scan iterates post containers, and the avatar is only a
//     fallback source of the name.
//
// Hiding the header alone was what produced the reported "blank gaps above and below
// posts": the header collapsed, the body kept painting. Hiding the tracking-duration
// container takes the whole card.
(function () {
  if (window.__ffwInstalled) {
    window.__ffwRefreshAllowed && window.__ffwRefreshAllowed();
    return;
  }
  window.__ffwInstalled = true;

  var AVATAR_SELECTOR = '[data-testid^="post-profile-image-"]';
  var POST_SELECTOR = '[data-tracking-duration-id]';
  var SCROLLER_SELECTOR = '[data-type="vscroller"]';
  var LINK_SELECTOR = '[role="link"]';
  var NAME_SUFFIX = / Profile Picture$/;
  var HIDDEN_ATTR = 'data-ffw-hidden';
  // Separate from HIDDEN_ATTR because gap marks are recomputed from scratch on every
  // pass: an empty band can fill with lazy-loaded content later and must come back.
  var GAP_ATTR = 'data-ffw-gap';
  // A post already decided doesn't need re-deciding on every scroll-triggered pass —
  // only on a full re-check (the allow-list changed). Infinite scroll on a long feed
  // was re-running sourceNameFor/hide/collapse over every earlier post on every batch
  // of newly-loaded content, each involving getBoundingClientRect (forces layout) and
  // DOM queries; on a 100+ post feed that repeated, compounding cost is what made pull-
  // to-refresh and opening a post feel like they'd hung.
  var DECIDED_ATTR = 'data-ffw-decided';
  // A scroller child already confirmed to render real content: skips collapseGaps'
  // isVisuallyEmpty check (itself a layout read plus a media query) on every pass for
  // the bulk of the feed, which never becomes empty once it has rendered.
  var HAS_CONTENT_ATTR = 'data-ffw-has-content';

  if (!document.getElementById('ffw-style')) {
    var style = document.createElement('style');
    style.id = 'ffw-style';
    style.textContent =
      '[' + HIDDEN_ATTR + '="1"],[' + GAP_ATTR + '="1"]{display:none !important;}';
    (document.head || document.documentElement).appendChild(style);
  }

  // Restricts filtering to the main feed. Without this, opening a single post ran the
  // exact same scan over it — and a post permalink page's own comments/replies also
  // carry data-tracking-duration-id, so each comment was read as if it were a feed
  // post: its first [role="link"] is the commenter's own name, never a group/page in
  // the allow-list, so comments started getting hidden and their wrappers collapsed —
  // the reported blank gaps below comments on a post's own page. Filtering by
  // source only makes sense on the feed itself; a post the user deliberately opened
  // (and its comments) should never be touched.
  //
  // Checks BOTH document.title and location.pathname — neither alone is reliable, and
  // each catches a case the other misses. An on-device capture of the "Replies"
  // screen (opening a comment's own replies) showed pathname staying "/", the same as
  // the main feed, while document.title changed to "Replies" — title alone was
  // originally enough to tell that case apart. But a second capture, this time of a
  // post opened as its own permalink page (story.php, its comments visible below it),
  // showed the *opposite* mismatch: document.title stayed exactly "Facebook" (the
  // feed's own value) while location.pathname changed to "/story.php" — title alone
  // was NOT enough there, and the guard let filtering run on that page's comments,
  // hiding real commenters ("Mark Turchyn", "Matthew Olan", ...) as if they were feed
  // posts, since a commenter's own name is never in the group/page allow-list. This
  // client evidently pushes "screens" via more than one mechanism (WebLitePipe's own
  // screenID/topScreenURI concept for some transitions, a real navigation/URL change
  // for others), so only requiring both signals to agree correctly excludes every
  // screen seen doing either kind of transition.
  function isFeedPage() {
    return document.title === 'Facebook' && (location.pathname === '/' || location.pathname === '');
  }

  // Temporary kill switch (Settings → toggles) for isolating whether this guard has
  // anything to do with a separate, still-unsolved bug (Facebook's own top tab bar
  // not reappearing on scroll-up) — see DebugToggles. Defaults to on (the fix stays
  // active) if the bridge isn't reachable for any reason.
  function feedScopeGuardEnabled() {
    try {
      return !window.NativeFlags || window.NativeFlags.getFeedScopeEnabled() !== false;
    } catch (e) {
      return true;
    }
  }

  function getAllowed() {
    try {
      return new Set(JSON.parse(window.NativeFilter.getAllowedAuthorsJson()));
    } catch (e) {
      return new Set();
    }
  }

  // Feature 1's own on/off switch (Settings → Βελτιώσεις → "Ενεργοποίηση
  // φιλτραρίσματος"), separate from the allow-list itself — off means every post
  // shows, the same as an empty list already did, but reachable without clearing it.
  function filteringEnabled() {
    try {
      return !window.NativeDisplay || window.NativeDisplay.getFilteringEnabled() !== false;
    } catch (e) {
      return true;
    }
  }

  var allowed = getAllowed();

  // Collapses internal whitespace/newlines to a single space. A long source name that
  // wraps onto a second line in the DOM has a literal newline in its textContent (e.g.
  // "Parkside Greek Club - Εργαλεία και\nκατασκευές"), which .trim() alone leaves in
  // place — the allow-list entry the user typed never had that newline, so the two
  // never matched and the post was judged wrong regardless of what was typed.
  function normalizeName(text) {
    return text.replace(/\s+/g, ' ').trim();
  }

  // The group/page the post comes from. The first link in the post header is that
  // source; the avatar's aria-label is only a fallback for shapes with no link.
  function sourceNameFor(post) {
    var link = post.querySelector(LINK_SELECTOR);
    if (link) {
      var text = normalizeName(link.textContent || '');
      if (text) return text;
    }
    var avatar = post.querySelector(AVATAR_SELECTOR);
    if (!avatar) return null;
    var label = avatar.getAttribute('aria-label') || '';
    var name = normalizeName(label.replace(NAME_SUFFIX, ''));
    return name && name !== normalizeName(label) ? name : null;
  }

  function occupiesSpace(el) {
    var rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  }

  // Whether an element currently shows nothing: no rendered text (innerText already
  // ignores display:none subtrees) and no media still taking up space.
  function isVisuallyEmpty(el) {
    if ((el.innerText || '').trim().length > 0) return false;
    var media = el.querySelectorAll('img,video,canvas,svg');
    for (var i = 0; i < media.length; i++) {
      if (occupiesSpace(media[i])) return false;
    }
    return true;
  }

  // Collapses the wrappers left behind around a hidden post. Restricting this to
  // single-child wrappers wasn't enough — gaps survived — so the test is now what the
  // wrapper actually shows: an ancestor that renders nothing is one whose height is
  // pure empty space, whatever its child count. An ancestor still holding a visible
  // post has text, so the climb stops there, and it never passes the scroller.
  function collapseEmptyAncestors(post, scroller) {
    var node = post;
    var guard = 0;
    while (guard++ < 12) {
      var parent = node.parentElement;
      if (!parent || parent === scroller || parent === document.body) break;
      if (!isVisuallyEmpty(parent)) break;
      parent.setAttribute(HIDDEN_ATTR, '1');
      node = parent;
    }
  }

  // Mirror of [hide]: clears the post's mark and any wrapper marks above it, so a
  // source added to the allow-list later isn't left buried under a hidden wrapper.
  function unhide(post, scroller) {
    post.removeAttribute(HIDDEN_ATTR);

    var node = post;
    var guard = 0;
    while (guard++ < 10) {
      var parent = node.parentElement;
      if (!parent || parent === scroller || parent === document.body) break;
      if (!parent.hasAttribute(HIDDEN_ATTR)) break;
      parent.removeAttribute(HIDDEN_ATTR);
      node = parent;
    }
  }

  // Collapses empty bands sitting directly in the feed. These aren't ancestors of the
  // posts we hid — measuring showed two of them surviving the ancestor cleanup — but
  // siblings of them: spacers the framework sized for content that is no longer shown.
  //
  // Marks are cleared and recomputed every pass, so a band that later fills with
  // lazy-loaded content reappears instead of staying collapsed forever. Only direct
  // children of the scroller are considered: that is the granularity of a band between
  // posts, and going deeper risks collapsing deliberate spacing inside a visible post.
  function collapseGaps(scroller) {
    if (!scroller) return 0;

    var stale = scroller.querySelectorAll('[' + GAP_ATTR + '="1"]');
    for (var i = 0; i < stale.length; i++) stale[i].removeAttribute(GAP_ATTR);

    var collapsed = 0;
    for (var j = 0; j < scroller.children.length; j++) {
      var child = scroller.children[j];
      if (child.hasAttribute(HIDDEN_ATTR) || child.hasAttribute(HAS_CONTENT_ATTR)) continue;
      if (child.getBoundingClientRect().height < 40) continue;
      if (!isVisuallyEmpty(child)) {
        // Real content doesn't later become empty, so this child never needs
        // re-checking again — the expensive part of every subsequent pass.
        child.setAttribute(HAS_CONTENT_ATTR, '1');
        continue;
      }
      child.setAttribute(GAP_ATTR, '1');
      collapsed++;
    }
    return collapsed;
  }

  // force=true (only from __ffwRefreshAllowed) re-decides every post, since a changed
  // allow-list can flip a decision already marked DECIDED_ATTR. Everything else — new
  // content streaming in from a scroll or pull-to-refresh — only has new posts to
  // decide; the rest already carry their mark from a previous pass and are skipped
  // outright, without even reading their source name.
  function applyFilter(force) {
    if (feedScopeGuardEnabled() && !isFeedPage()) return;
    var posts = document.querySelectorAll(POST_SELECTOR);
    var considered = 0;
    var resolved = 0;
    var hiddenCount = 0;
    var verifiedHidden = 0;
    var unresolvedVisible = 0;
    var hiddenPosts = [];
    var scroller = null;

    // Pass 1 — decide undecided posts. The wrapper cleanup has to wait until this is
    // done, since a wrapper holding two unwanted posts only reads as empty once both
    // are marked.
    for (var i = 0; i < posts.length; i++) {
      var post = posts[i];
      // Nested container (a shared/quoted post inside another): the outermost one
      // decides for the whole card.
      if (post.parentElement && post.parentElement.closest(POST_SELECTOR)) continue;
      if (!scroller) scroller = post.closest(SCROLLER_SELECTOR);
      if (!force && post.hasAttribute(DECIDED_ATTR)) continue;
      considered++;

      var name = sourceNameFor(post);
      // Unknown source: leave visible rather than hide content on a shape we didn't
      // anticipate. Counted separately — a non-zero count here is what "some posts
      // still get through" looks like.
      if (!name) {
        if (occupiesSpace(post)) unresolvedVisible++;
        continue;
      }
      resolved++;
      post.setAttribute(DECIDED_ATTR, '1');

      // Empty allow-list: show everything until the user configures it. Same when
      // the feature's own toggle is off, regardless of what's actually in the list.
      if (!filteringEnabled() || allowed.size === 0 || allowed.has(name)) {
        unhide(post, post.closest(SCROLLER_SELECTOR));
      } else {
        hiddenCount++;
        post.setAttribute(HIDDEN_ATTR, '1');
        hiddenPosts.push(post);
      }
    }

    // Pass 2 — collapse what the hiding emptied out.
    for (var j = 0; j < hiddenPosts.length; j++) {
      collapseEmptyAncestors(hiddenPosts[j], hiddenPosts[j].closest(SCROLLER_SELECTOR));
      if (!occupiesSpace(hiddenPosts[j])) verifiedHidden++;
    }

    var gapsCollapsed = collapseGaps(scroller);
    console.log('[ffw] posts:', considered, '| sources:', resolved, '| hidden:', hiddenCount,
      '| verified:', verifiedHidden, '| unresolved-visible:', unresolvedVisible,
      '| gaps collapsed:', gapsCollapsed);
    window.NativeFilter && window.NativeFilter.reportStats &&
      window.NativeFilter.reportStats(
        considered, resolved, hiddenCount, verifiedHidden, unresolvedVisible, gapsCollapsed);
  }

  window.__ffwRefreshAllowed = function () {
    allowed = getAllowed();
    applyFilter(true);
  };

  // Hides newly-added unwanted posts synchronously, right in the mutation callback —
  // before the browser's next paint — rather than waiting for the debounced pass
  // below. An on-device screenshot caught an unwanted post at full size for a moment
  // during fast scrolling: new posts stream in constantly during infinite scroll, and
  // debouncing the *decision* along with the (expensive) collapse/gap cleanup meant a
  // post could paint once, full-size, before the deferred pass got to it. Only sets
  // HIDDEN_ATTR here — instant visually via the stylesheet rule — and deliberately
  // leaves DECIDED_ATTR unset, so the debounced applyFilter() pass still re-decides
  // and, for real, collapses these posts' now-empty wrappers; that part doesn't need
  // to be instant, only the hide does.
  function quickHideNewPosts(mutations) {
    if (feedScopeGuardEnabled() && !isFeedPage()) return;
    for (var m = 0; m < mutations.length; m++) {
      var added = mutations[m].addedNodes;
      for (var a = 0; a < added.length; a++) {
        var node = added[a];
        if (node.nodeType !== 1 || !node.querySelectorAll) continue;

        var candidates = node.matches && node.matches(POST_SELECTOR) ? [node] : [];
        var nested = node.querySelectorAll(POST_SELECTOR);
        for (var n = 0; n < nested.length; n++) candidates.push(nested[n]);

        for (var c = 0; c < candidates.length; c++) {
          var post = candidates[c];
          if (post.hasAttribute(HIDDEN_ATTR) || post.hasAttribute(DECIDED_ATTR)) continue;
          if (post.parentElement && post.parentElement.closest(POST_SELECTOR)) continue;
          var name = sourceNameFor(post);
          if (name && filteringEnabled() && allowed.size > 0 && !allowed.has(name)) {
            post.setAttribute(HIDDEN_ATTR, '1');
          }
        }
      }
    }
  }

  // The feed loads/scrolls in progressively; debounce so a burst of DOM mutations
  // triggers one re-filter pass instead of dozens.
  var pending = null;
  var observer = new MutationObserver(function (mutations) {
    quickHideNewPosts(mutations);
    if (pending) return;
    pending = requestAnimationFrame(function () {
      pending = null;
      applyFilter();
    });
  });
  observer.observe(document.body, { childList: true, subtree: true });

  applyFilter();
})();
