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

  if (!document.getElementById('ffw-style')) {
    var style = document.createElement('style');
    style.id = 'ffw-style';
    style.textContent = '[' + HIDDEN_ATTR + '="1"]{display:none !important;}';
    (document.head || document.documentElement).appendChild(style);
  }

  function getAllowed() {
    try {
      return new Set(JSON.parse(window.NativeFilter.getAllowedAuthorsJson()));
    } catch (e) {
      return new Set();
    }
  }

  var allowed = getAllowed();

  // The group/page the post comes from. The first link in the post header is that
  // source; the avatar's aria-label is only a fallback for shapes with no link.
  function sourceNameFor(post) {
    var link = post.querySelector(LINK_SELECTOR);
    if (link) {
      var text = (link.textContent || '').trim();
      if (text) return text;
    }
    var avatar = post.querySelector(AVATAR_SELECTOR);
    if (!avatar) return null;
    var label = avatar.getAttribute('aria-label') || '';
    var name = label.replace(NAME_SUFFIX, '');
    return name && name !== label ? name.trim() : null;
  }

  function occupiesSpace(el) {
    var rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  }

  // Climbs only if hiding the post container somehow didn't collapse it, and never
  // past the scroller, so a mis-climb can't blank the whole feed.
  function hide(post, scroller) {
    post.setAttribute(HIDDEN_ATTR, '1');
    if (!occupiesSpace(post)) return true;

    var node = post.parentElement;
    var guard = 0;
    while (node && node !== scroller && node !== document.body && guard++ < 10) {
      if (node.querySelectorAll(POST_SELECTOR).length > 1) break; // holds other posts
      node.setAttribute(HIDDEN_ATTR, '1');
      if (!occupiesSpace(post)) return true;
      node = node.parentElement;
    }
    return false;
  }

  function applyFilter() {
    var posts = document.querySelectorAll(POST_SELECTOR);
    var considered = 0;
    var resolved = 0;
    var hiddenCount = 0;
    var verifiedHidden = 0;
    var unresolvedVisible = 0;

    for (var i = 0; i < posts.length; i++) {
      var post = posts[i];
      // Nested container (a shared/quoted post inside another): the outermost one
      // decides for the whole card.
      if (post.parentElement && post.parentElement.closest(POST_SELECTOR)) continue;
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

      // Empty allow-list: show everything until the user configures it.
      if (allowed.size === 0 || allowed.has(name)) {
        post.removeAttribute(HIDDEN_ATTR);
      } else {
        hiddenCount++;
        if (hide(post, post.closest(SCROLLER_SELECTOR))) verifiedHidden++;
      }
    }

    console.log('[ffw] posts:', considered, '| sources:', resolved, '| hidden:', hiddenCount,
      '| verified:', verifiedHidden, '| unresolved-visible:', unresolvedVisible);
    window.NativeFilter && window.NativeFilter.reportStats &&
      window.NativeFilter.reportStats(considered, resolved, hiddenCount, verifiedHidden, unresolvedVisible);
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
