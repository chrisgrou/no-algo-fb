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

  function currentY() {
    var sc = scroller();
    return sc.scrollTop || window.scrollY || 0;
  }

  function setY(y) {
    var sc = scroller();
    sc.scrollTop = y;
    window.scrollTo(0, y);
  }

  function log(msg) {
    if (window.__ffwLog) window.__ffwLog('scroll: ' + msg);
  }

  function reachable() {
    var sc = scroller();
    return sc.scrollHeight - sc.clientHeight;
  }

  // The offset we believe the user actually chose, as opposed to whatever the browser
  // currently reports. They diverge on resume — see restoreAfterResume() below.
  var lastGoodY = 0;
  // While set, incoming scroll events are neither trusted nor saved: they're the
  // page settling itself, not the user moving.
  var holdUntil = 0;

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
    if (Date.now() < holdUntil) return;
    lastGoodY = currentY();
    scheduleSave();
  }, { passive: true, capture: true });

  // A real finger on the screen ends any restore in progress: past that point the user
  // is choosing where to be, and fighting them would be worse than losing the offset.
  window.addEventListener('touchstart', function () {
    if (Date.now() < holdUntil) {
      holdUntil = 0;
      log('restore cancelled by touch at ' + Math.round(currentY()));
    }
  }, { passive: true, capture: true });

  var saved = 0;
  try {
    saved = parseFloat(window.NativeScroll.getSavedScrollY()) || 0;
  } catch (e) {}
  lastGoodY = saved;

  // Called natively from MainActivity.onResume(). The page is never reloaded across a
  // backgrounding (confirmed on-device: the resume log shows one "load type=navigate"
  // spanning several resumes), yet the feed still comes back at the top — because the
  // feed is a virtualized scroller. Frozen, it drops its off-screen rows; on resume its
  // scrollHeight briefly collapses and the browser clamps scrollTop to what's left,
  // i.e. 0. That clamp then fires a scroll event, which is how the saved offset used to
  // get overwritten with 0 as well, losing it for good.
  //
  // So: hold the saver off, and keep re-asserting the pre-background offset until the
  // rows stream back in far enough to reach it — the same "only jump once reachable
  // covers the target" rule the cold-start restore below already follows, just repeated,
  // since there's no telling which relayout the clamp lands on.
  window.__ffwRestoreScroll = function () {
    var target = lastGoodY;
    log('resume: target=' + Math.round(target) + ' at=' + Math.round(currentY()) +
      ' reachable=' + Math.round(reachable()));
    if (target <= 50) return;

    holdUntil = Date.now() + 4000;
    (function reassert() {
      if (Date.now() >= holdUntil) {
        log('resume: settled at ' + Math.round(currentY()) + ' target=' + Math.round(target));
        return;
      }
      if (reachable() >= target && Math.abs(currentY() - target) > 4) setY(target);
      setTimeout(reassert, 250);
    })();
  };

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
