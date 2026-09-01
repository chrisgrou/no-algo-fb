// Post history (Feature): a lightweight "what was I looking at" log, not a bookmark.
// m.facebook.com's mobile markup has no real navigable link for an individual post —
// everything routes through role="link" elements with opaque internal action IDs (see
// feed_filter.js's own markup notes on this) — so there's no href worth storing that
// could jump straight back to it later. This instead remembers the source name and a
// text snippet of whichever post was topmost on screen right before the app went to the
// background, so Settings can show a short "recently seen" list purely for recall.
(function () {
  if (window.__ffwHistoryInstalled) return;
  window.__ffwHistoryInstalled = true;

  var POST_SELECTOR = '[data-tracking-duration-id]';
  var LINK_SELECTOR = '[role="link"]';
  var HIDDEN_ATTR = 'data-ffw-hidden';
  var SNIPPET_LEN = 160;

  // Same combined title+pathname check as feed_filter.js's isFeedPage() — see that
  // file's comment for why neither signal alone is reliable. A post's own permalink
  // page shouldn't be captured as "the post the user was looking at in the feed".
  function isFeedPage() {
    return document.title === 'Facebook' && (location.pathname === '/' || location.pathname === '');
  }

  function normalizeName(text) {
    return text.replace(/\s+/g, ' ').trim();
  }

  // The group/page/person the post is from — same rule as feed_filter.js's
  // sourceNameFor, minus its avatar fallback: a post whose header link is missing is
  // rare enough, and unimportant enough for a purely informational history entry, to
  // just skip rather than duplicate that fallback here too.
  function sourceNameFor(post) {
    var link = post.querySelector(LINK_SELECTOR);
    if (!link) return null;
    return normalizeName(link.textContent || '') || null;
  }

  // Called natively from MainActivity.onPause(), right before the app actually goes to
  // the background. Picks the topmost post that's both currently on screen (not just
  // in the DOM — the vscroller keeps a lot rendered above and below the viewport) and
  // not filtered out, since a hidden post was never what the user was looking at.
  window.__ffwCaptureHistory = function () {
    try {
      if (!isFeedPage()) return;
      var posts = document.querySelectorAll(POST_SELECTOR);
      for (var i = 0; i < posts.length; i++) {
        var post = posts[i];
        if (post.hasAttribute(HIDDEN_ATTR)) continue;
        // Nested container (a shared/quoted post inside another) — same rule
        // feed_filter.js uses to let the outermost post represent the whole card.
        if (post.parentElement && post.parentElement.closest(POST_SELECTOR)) continue;
        var rect = post.getBoundingClientRect();
        if (rect.bottom <= 0 || rect.top >= window.innerHeight) continue;

        var source = sourceNameFor(post);
        if (!source) continue;
        var snippet = (post.innerText || '').replace(/\s+/g, ' ').trim().substring(0, SNIPPET_LEN);
        if (!snippet) continue;

        window.NativeHistory && window.NativeHistory.addEntry(source, snippet);
        return;
      }
    } catch (e) {}
  };
})();
