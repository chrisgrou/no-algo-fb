// Scroll-position persistence (Feature 2). Injected alongside feed_filter.js on every
// page load. Saves the feed's scroll offset as the user scrolls, then restores it once
// on the next load — covering the process death Android can trigger on app switching,
// which WebView.saveState()/restoreState() (wired in MainActivity) doesn't reliably
// cover for a lazy-loaded, infinitely-scrolling page like this one.
//
// No 100% guarantee (an OS-level limitation, not something JS can work around): if the
// saved offset is deep enough that the content there hasn't lazy-loaded yet, setting
// scrollTop to it does nothing until that content exists. So restoring retries for a
// few seconds — each attempt itself triggers more lazy-loading — rather than assuming
// one attempt is enough.
(function () {
  if (window.__ffwScrollInstalled) return;
  window.__ffwScrollInstalled = true;

  // The feed's own scrolling element, when the page uses one (see feed_filter.js's
  // markup notes); otherwise the page scrolls the normal way.
  function scroller() {
    return document.querySelector('[data-type="vscroller"]') || document.scrollingElement || document.body;
  }

  // Whichever of the vscroller and the window is actually carrying the scroll offset
  // right now — not assumed to always be the same one. An on-device trace caught the
  // vscroller reporting scrollTop=0 while window.scrollY correctly held the real
  // position (that DIV wasn't a real overflow container in that state), so this reads
  // both and trusts whichever is actually nonzero rather than favoring one by default.
  function currentY() {
    var sc = scroller();
    return Math.max(sc.scrollTop || 0, window.scrollY || 0);
  }

  function setY(y) {
    var sc = scroller();
    sc.scrollTop = y;
    window.scrollTo(0, y);
  }

  function log(msg) {
    if (window.__ffwLog) window.__ffwLog('scroll: ' + msg);
  }

  // The offset we believe the user actually chose, as opposed to whatever the browser
  // currently reports.
  var lastGoodY = 0;

  var saveTimer = null;
  function scheduleSave() {
    if (saveTimer) return;
    saveTimer = setTimeout(function () {
      saveTimer = null;
      window.NativeScroll && window.NativeScroll.saveScrollY(lastGoodY);
    }, 400);
  }

  // capture:true so this catches scroll events on the internal vscroller element too:
  // 'scroll' doesn't bubble, but a capturing listener on window still sees it on the
  // way down to the actual target.
  window.addEventListener('scroll', function () {
    lastGoodY = currentY();
    scheduleSave();
  }, { passive: true, capture: true });

  var saved = 0;
  try {
    saved = parseFloat(window.NativeScroll.getSavedScrollY()) || 0;
  } catch (e) {}
  lastGoodY = saved;

  // Restoring the pre-background scroll offset on resume was tried and abandoned: the
  // feed's virtualized scroller genuinely discards far-off content while frozen, and
  // an on-device trace of the "keep re-asserting the target as content streams back
  // in" approach showed it climbing 0 -> 598 -> 1284 -> 4057 -> ... -> 19153 over a
  // full 22 real seconds, visibly, without ever fully reaching the target — because
  // reconstructing that much content genuinely takes that long, no matter how the
  // correction is paced. There's no way to make that instant from here; the honest
  // answer for a page whose own virtualization threw away that much state is that
  // it's gone until reloaded, not that it can be smoothly animated back. Rather than
  // replace one bad experience (landing at the top) with a worse, jumpier one, resume
  // now leaves the browser's natural post-resume position alone entirely, and only
  // resume_guard.js's (invisible) visibility masking remains.

  // Temporary kill switch (Settings → toggles) for isolating whether this fix has
  // anything to do with a separate, still-unsolved bug (Facebook's own top tab bar
  // not reappearing on scroll-up) — see DebugToggles. Defaults to on (the fix stays
  // active) if the bridge isn't reachable for any reason.
  var fixEnabled = true;
  try {
    fixEnabled = !window.NativeFlags || window.NativeFlags.getScrollRestoreFixEnabled() !== false;
  } catch (e) {}

  // Reported bug: the feed stayed blank for 5-10s on launch. The pre-fix version
  // called setY(saved) unconditionally on every attempt, including while
  // reachable < saved (the lazy-loaded content hasn't streamed in that far yet) —
  // the browser clamps scrollTop to whatever IS currently scrollable, so that pinned
  // the viewport at the bottom edge of the little content that had loaded (mostly
  // blank space) instead of leaving it at the top where the story bar and first
  // posts already render. Repeating that every 400ms for up to 15 attempts is
  // exactly a multi-second "no posts visible" window. Only jump once reachable
  // actually covers the saved offset — until then, leave the browser at its natural
  // scrollTop 0 (a working, visible feed) rather than fighting the still-loading page.
  if (saved > 50) {
    var attempts = 0;
    (function tryRestore() {
      attempts++;
      var sc = scroller();
      var reachable = sc.scrollHeight - sc.clientHeight;
      if (!fixEnabled) {
        setY(saved);
        if (attempts < 15 && reachable < saved) setTimeout(tryRestore, 400);
        return;
      }
      if (reachable >= saved) {
        setY(saved);
        return;
      }
      if (attempts < 15) {
        setTimeout(tryRestore, 400);
      }
    })();
  }
})();
