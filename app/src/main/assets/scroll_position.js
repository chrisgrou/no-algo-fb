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

  var saveTimer = null;
  function scheduleSave() {
    if (saveTimer) return;
    saveTimer = setTimeout(function () {
      saveTimer = null;
      window.NativeScroll && window.NativeScroll.saveScrollY(currentY());
    }, 400);
  }

  // capture:true so this catches scroll events on the internal vscroller element too:
  // 'scroll' doesn't bubble, but a capturing listener on window still sees it on the
  // way down to the actual target.
  window.addEventListener('scroll', scheduleSave, { passive: true, capture: true });

  var saved = 0;
  try {
    saved = parseFloat(window.NativeScroll.getSavedScrollY()) || 0;
  } catch (e) {}

  if (saved > 50) {
    var attempts = 0;
    (function tryRestore() {
      attempts++;
      setY(saved);
      var sc = scroller();
      var reachable = sc.scrollHeight - sc.clientHeight;
      if (attempts < 15 && reachable < saved) {
        setTimeout(tryRestore, 400);
      }
    })();
  }
})();
