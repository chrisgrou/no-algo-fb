package com.chrisgrou.fbfeedwrapper.sync

/**
 * Where to start. m.facebook.com, not www: www served an "open in the app"
 * interstitial instead of the list, and scanning that returned nothing but
 * "Chrome"/"Firefox"/"Edge" browser-picker links.
 *
 * The user can navigate anywhere from here before scanning — Facebook moves these
 * lists around (the followed-pages list currently lives behind Pages → See all), and
 * a hardcoded URL that quietly lands somewhere else is exactly how the first attempt
 * failed.
 */
const val GROUPS_URL = "https://m.facebook.com/groups/"

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

  // These list rows are buttons/containers in Facebook's own component markup, not
  // plain links, so anything clickable counts as a candidate. A row reads as
  // "Tesla Owners Greece\n1 new post · Pinned", so the name is its first line.
  var CANDIDATE_SELECTOR = 'a[href], [role="link"], [role="button"], [data-action-id]';
  var CHROME_TEXT = [
    'home', 'groups', 'pages', 'discover', 'search', 'menu', 'follow', 'following',
    'see all', 'see more', 'create', 'settings', 'notifications', 'marketplace',
    'friends', 'messages', 'reels', 'chrome', 'firefox', 'edge', 'samsung',
    'use facebook app', 'your groups', 'liked pages', 'pages you may like',
  ];

  function firstLine(el) {
    var text = (el.innerText || el.textContent || '').trim();
    if (!text) return '';
    return text.split('\n')[0].trim().replace(/\s+/g, ' ');
  }

  function collect() {
    var out = [];
    var seen = {};
    var nodes = document.querySelectorAll(CANDIDATE_SELECTOR);
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      var text = firstLine(el);
      if (!text || text.length < 2 || text.length > 80) continue;
      if (CHROME_TEXT.indexOf(text.toLowerCase()) >= 0) continue;
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
