package com.chrisgrou.fbfeedwrapper.sync

/** Where the user's groups and followed pages are listed. */
const val GROUPS_URL = "https://www.facebook.com/groups/"
const val PAGES_URL = "https://www.facebook.com/pages/"

/**
 * Scrolls a list page to its end — these lists lazy-load, so the names only exist in
 * the DOM once scrolled past — then reports every link-ish element it found, with its
 * href when there is one.
 *
 * Extraction is deliberately broad rather than clever: the markup of these list pages
 * hasn't been inspected, and a narrow guess would silently return nothing (the same
 * failure mode that cost several rounds on the feed filter). Everything found is shown
 * to the user for selection instead of being trusted, so a sloppy match is visible and
 * dismissable rather than quietly polluting the allow-list.
 */
const val AUTO_SYNC_JS = """
(function () {
  if (window.__ffwSyncRunning) return;
  window.__ffwSyncRunning = true;

  var STABLE_ROUNDS_NEEDED = 3;
  var MAX_ROUNDS = 60;
  var lastHeight = -1;
  var stableRounds = 0;
  var rounds = 0;

  function scroller() {
    return document.querySelector('[data-type="vscroller"]');
  }

  function currentHeight() {
    var sc = scroller();
    return Math.max(document.body.scrollHeight || 0, sc ? (sc.scrollHeight || 0) : 0);
  }

  function collect() {
    var out = [];
    var seen = {};
    var nodes = document.querySelectorAll('a[href], [role="link"]');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      var text = (el.textContent || '').trim().replace(/\s+/g, ' ');
      if (!text || text.length < 2 || text.length > 80) continue;
      if (seen[text]) continue;
      seen[text] = 1;
      out.push({ name: text, href: el.getAttribute('href') || '' });
    }
    return out;
  }

  function step() {
    var sc = scroller();
    if (sc) sc.scrollTop = sc.scrollHeight;
    window.scrollTo(0, document.body.scrollHeight);
    rounds++;

    var h = currentHeight();
    if (h === lastHeight) stableRounds++; else stableRounds = 0;
    lastHeight = h;

    var found = collect();
    if (window.NativeSync) window.NativeSync.onProgress(rounds, found.length);

    if (stableRounds >= STABLE_ROUNDS_NEEDED || rounds >= MAX_ROUNDS) {
      window.__ffwSyncRunning = false;
      if (window.NativeSync) window.NativeSync.onComplete(JSON.stringify(found));
      return;
    }
    setTimeout(step, 800);
  }

  step();
})();
"""
