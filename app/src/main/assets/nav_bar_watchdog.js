// Forces Facebook's own top tab bar back to visible when it's stuck hidden (Feature:
// nav bar watchdog). An on-device diagnostic capture found the exact bug: at
// window.scrollY === 0 (the very top of the feed, right after returning from a post),
// the tab bar's [role="tablist"] computed display was "none" — not from an inline
// style (its own inline style only sets height/width), but from a CSS class Facebook
// itself applies to hide it on scroll-down and is supposed to remove on scroll back
// up. That reset evidently doesn't always happen, particularly after navigating back
// from a subpage rather than a plain scroll gesture, leaving the bar hidden even at
// the top with nothing back-scrollable left to trigger it. This works around the
// stuck state with a single, safe style write (an inline `display` override, `!important`
// so it beats the stuck class) — never a structural DOM edit (no removeChild/replaceWith/
// textContent), which is what actually broke Facebook's own React tree when an earlier
// version of this feature (nav_override.js) tried to relabel the tab bar directly.
(function () {
  if (window.__ffwNavWatchdogInstalled) return;
  window.__ffwNavWatchdogInstalled = true;

  // Small tolerance: this only forces the bar visible near the top, matching exactly
  // the condition the capture found (stuck hidden despite nothing left to scroll up
  // to) — never overriding Facebook's own legitimate hide-on-scroll-down behavior
  // further down the feed.
  var TOP_THRESHOLD = 4;

  function tablist() {
    return document.querySelector('[role="tablist"]');
  }

  function check() {
    var tl = tablist();
    if (!tl) return;
    if (window.scrollY > TOP_THRESHOLD) return;
    var rect = tl.getBoundingClientRect();
    if (rect.height > 0) return;
    tl.style.setProperty('display', 'flex', 'important');
  }

  check();
  window.addEventListener('scroll', check, { passive: true, capture: true });

  var checkTimer = null;
  var observer = new MutationObserver(function () {
    if (checkTimer) return;
    checkTimer = setTimeout(function () {
      checkTimer = null;
      check();
    }, 200);
  });
  observer.observe(document.body, { childList: true, subtree: true });
})();
