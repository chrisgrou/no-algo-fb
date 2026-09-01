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

  // How far down the page can actually be scrolled right now. Not just the vscroller's
  // own scrollHeight - clientHeight: an on-device trace caught scroller() resolving to
  // a DIV whose scrollHeight and clientHeight were within 2px of each other (35818 vs
  // 35816) — a block sized to fit all of its own content, not a real overflow
  // container — while window.scrollY sat at the correct, already-restored offset the
  // whole time. That vscroller-only reading called the page "unreachable" when it very
  // much wasn't, which is what let __ffwRestoreScroll below drag a perfectly good
  // position down to 0. Taking the max of both readings means an actually-scrollable
  // vscroller (the case this was originally written for) still works, while a page
  // that scrolls at the document level instead — this trace's actual case — isn't
  // penalized for the vscroller marker not being where the scrolling happens.
  function reachable() {
    var sc = scroller();
    var viaScroller = sc.scrollHeight - sc.clientHeight;
    var viaDocument = document.documentElement.scrollHeight - window.innerHeight;
    return Math.max(viaScroller, viaDocument, 0);
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
  // A first on-device trace showed why passively waiting for reachable to catch up
  // isn't enough: currentY was still 5525 (correct!) the instant this ran, but
  // reachable was only 2 — nothing had streamed back in yet — so the "only jump once
  // reachable covers the target" rule (right for the cold-start restore below, which
  // must avoid landing on blank space past the loaded content) did nothing, and the
  // browser's own clamp dragged scrollTop to 0 while this sat there waiting. Rows
  // stream back in gradually, not all at once, so the fix is to actively track that
  // climb: on every tick, pin scrollTop to whatever's the deepest position currently
  // safe (min(target, reachable)) instead of only acting once reachable is already
  // past target. That keeps the visible content pinned near the target throughout the
  // reflow instead of leaving a window for the clamp to win.
  // Describes which element is actually being read/written, and how — the "did setY
  // even do anything" question a bare number can't answer on its own. sc !== window's
  // own scrolling element is exactly the case that would make sc.scrollTop = y a no-op
  // (e.g. a non-scrolling wrapper with overflow:visible, where scrollTop always reads
  // back 0 regardless of what's assigned): only the window.scrollTo half of setY would
  // be doing anything real in that case.
  function scrollerDebug() {
    var sc = scroller();
    return sc.tagName + (sc.id ? '#' + sc.id : '') + ' scrollTop=' + Math.round(sc.scrollTop) +
      ' scrollHeight=' + Math.round(sc.scrollHeight) + ' clientHeight=' + Math.round(sc.clientHeight) +
      ' isScrollingElement=' + (sc === document.scrollingElement) + ' windowScrollY=' + Math.round(window.scrollY);
  }

  window.__ffwRestoreScroll = function () {
    var target = lastGoodY;
    log('resume: target=' + Math.round(target) + ' at=' + Math.round(currentY()) +
      ' reachable=' + Math.round(reachable()) + ' | ' + scrollerDebug());
    if (target <= 50) return;

    holdUntil = Date.now() + 6000;
    var tick = 0;
    (function reassert() {
      tick++;
      // Checked first, before anything below can call setY: an on-device trace caught
      // the browser already sitting exactly on target the instant this ran (the page
      // was never actually disturbed), and a stale reachable() reading below that
      // target dragged a perfectly good position down anyway. Nothing to fix if
      // there's nothing wrong — leave it alone rather than risk moving it.
      if (Math.abs(currentY() - target) <= 4) {
        holdUntil = Date.now();
        log('resume: already at target (' + Math.round(currentY()) + '), nothing to do');
        return;
      }
      if (Date.now() >= holdUntil) {
        log('resume: settled at ' + Math.round(currentY()) + ' target=' + Math.round(target) +
          ' | ' + scrollerDebug());
        return;
      }
      var safe = Math.min(target, reachable());
      if (safe > 0 && Math.abs(currentY() - safe) > 4) setY(safe);
      // Every ~640ms (8 ticks * 80ms), not every tick — the 6s hold is ~75 ticks, and
      // the shared log only keeps the last entries, so per-tick logging would push
      // everything before it (including the "resume:" start line) out before this even
      // finishes.
      if (tick % 8 === 0) {
        log('resume: tick target=' + Math.round(target) + ' at=' + Math.round(currentY()) +
          ' safe=' + Math.round(safe) + ' | ' + scrollerDebug());
      }
      setTimeout(reassert, 80);
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
