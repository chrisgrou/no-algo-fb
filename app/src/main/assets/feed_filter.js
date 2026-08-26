// Feed filtering (Feature 1). Injected by FbWebViewClient.onPageFinished on every
// page load. Hides feed posts whose author isn't in the user's allowed-pages list
// (read from native via window.NativeFilter.getAllowedAuthorsJson()).
//
// NOTE ON SELECTORS: m.facebook.com's DOM structure and class names are not public,
// change frequently, and were not available to inspect while writing this scaffold
// (see PROJECT_CONTENT.md "Ρίσκο συντήρησης"). POST_SELECTOR / AUTHOR_SELECTOR below
// are a best-effort starting point based on common mobile-FB markup patterns and will
// need verification/adjustment against the real page on a device.
(function () {
  if (window.__ffwInstalled) {
    window.__ffwRefreshAllowed && window.__ffwRefreshAllowed();
    return;
  }
  window.__ffwInstalled = true;

  var POST_SELECTOR = 'article, div[data-testid="post_message"], div[role="article"]';
  var AUTHOR_SELECTOR = 'h3 a, strong a, a[role="link"] strong';

  function getAllowed() {
    try {
      return new Set(JSON.parse(window.NativeFilter.getAllowedAuthorsJson()));
    } catch (e) {
      return new Set();
    }
  }

  var allowed = getAllowed();

  function authorNameFor(post) {
    var el = post.querySelector(AUTHOR_SELECTOR);
    return el ? el.textContent.trim() : null;
  }

  function applyFilterTo(root) {
    var posts = root.querySelectorAll
      ? root.querySelectorAll(POST_SELECTOR)
      : [];
    for (var i = 0; i < posts.length; i++) {
      var post = posts[i];
      var name = authorNameFor(post);
      // Unknown author (selector didn't match): leave visible rather than risk
      // hiding real content on a DOM shape we didn't anticipate.
      if (!name) continue;
      // Empty allow-list: show everything until the user configures it.
      var isAllowed = allowed.size === 0 || allowed.has(name);
      // display:none (not visibility:hidden) so hidden posts collapse fully,
      // leaving no gap in the feed, per the project's filtering requirement.
      post.style.display = isAllowed ? '' : 'none';
    }
  }

  function applyFilter() {
    applyFilterTo(document);
  }

  window.__ffwRefreshAllowed = function () {
    allowed = getAllowed();
    applyFilter();
  };

  var observer = new MutationObserver(function (mutations) {
    for (var i = 0; i < mutations.length; i++) {
      var added = mutations[i].addedNodes;
      for (var j = 0; j < added.length; j++) {
        var node = added[j];
        if (node.nodeType === 1) applyFilterTo(node);
      }
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });

  applyFilter();
})();
