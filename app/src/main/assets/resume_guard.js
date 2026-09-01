// Keeps the feed from being thrown away when the app comes back from the background
// (the "returns to top and refreshes far too eagerly" bug).
//
// Three separate things can reset the feed on resume, and only the last one is
// Facebook's own doing:
//   1. Android recreating the Activity (a config change, or process death) — the new
//      WebView reloads the page from its restored history, which is a fresh feed at
//      the top. Handled natively: AndroidManifest's widened configChanges, and
//      MainActivity holding one WebView for the whole Activity instead of building a
//      new one every time the feed re-enters composition.
//   2. Facebook's own JS timers continuing to run while we're backgrounded, so the
//      feed has already re-fetched itself by the time the user returns. Handled
//      natively too: MainActivity pauses/resumes the WebView and its timers.
//   3. Facebook's client noticing the page became visible again and re-rendering the
//      feed for it. That's this file: never let the page find out it was hidden.
//
// So document.hidden/visibilityState are pinned to "visible" and the visibility and
// page-transition events are swallowed before any Facebook listener sees them. Every
// one of them is still recorded first (window.__ffwResumeLog, surfaced in the debug
// dump) so a capture taken right after a resume shows what actually fired — and
// whether suppressing it was enough — instead of us guessing again.
//
// What this file does NOT fix: the feed's own virtualized scroller discards far-off
// rendered content while frozen regardless of any of the above, so a long-enough
// backgrounding can still lose the visible scroll position even though none of 1-3
// happened. scroll_position.js tried actively re-scrolling back to it on resume and
// abandoned that (see its own comment) — reconstructing that much content visibly
// takes many real seconds no matter how it's paced, which read as worse than just
// leaving the browser's natural post-resume position alone.
(function () {
  if (window.__ffwResumeGuardInstalled) return;
  window.__ffwResumeGuardInstalled = true;

  var LOG_CAP = 80;
  var log = [];
  window.__ffwResumeLog = log;

  function record(entry) {
    log.push(new Date().toISOString().substring(11, 23) + ' ' + entry);
    if (log.length > LOG_CAP) log.shift();
  }

  // Shared with scroll_position.js so a resume shows its scroll bookkeeping inline
  // with the events that triggered it, in one ordered timeline.
  window.__ffwLog = record;

  // Read fresh on every event rather than captured once, so flipping the Debug kill
  // switch takes effect without a reload — and so the log keeps recording either way.
  function enabled() {
    try {
      return !window.NativeFlags || window.NativeFlags.getResumeGuardEnabled() !== false;
    } catch (e) {
      return true;
    }
  }

  // Whether this page load was a reload tells us which of the three mechanisms above
  // we're actually fighting: "reload" or "navigate" here right after a resume means
  // the page really was thrown away (1 or 2), while "navigate" from a launch hours ago
  // with a resume logged in between means the DOM survived and any reset came from
  // Facebook's own re-render (3).
  try {
    var nav = performance.getEntriesByType('navigation')[0];
    record('load type=' + (nav ? nav.type : 'unknown') + ' enabled=' + enabled());
  } catch (e) {
    record('load type=unavailable enabled=' + enabled());
  }

  // Pinned rather than left alone: a Facebook listener that we fail to intercept can
  // still ask the document directly on its next timer tick, and would see "hidden".
  function pin(prop, value) {
    try {
      Object.defineProperty(document, prop, {
        configurable: true,
        get: function () { return value; },
      });
    } catch (e) {
      record('pin failed: ' + prop);
    }
  }

  if (enabled()) {
    pin('hidden', false);
    pin('webkitHidden', false);
    pin('visibilityState', 'visible');
    pin('webkitVisibilityState', 'visible');
  }

  // Deliberately no 'blur'/'focus' here: those share a name with the events every
  // text input fires, and a capturing window listener sees those too — swallowing
  // them would break typing a comment.
  var BLOCKED = [
    'visibilitychange',
    'webkitvisibilitychange',
    'pagehide',
    'pageshow',
    'freeze',
    'resume',
  ];

  function intercept(event) {
    record('event ' + event.type + ' visibility=' + document.visibilityState);
    if (!enabled()) return;
    event.stopImmediatePropagation();
    event.stopPropagation();
  }

  // Capture phase on window, which the DOM walks before anything registered on
  // document — where visibilitychange is actually dispatched, and where Facebook's
  // own listener for it lives. That ordering is the whole reason this works despite
  // being injected long after Facebook's scripts ran: we can't get ahead of a
  // same-node listener registered earlier, but we don't have to.
  for (var i = 0; i < BLOCKED.length; i++) {
    window.addEventListener(BLOCKED[i], intercept, true);
  }

  // ...and stop new listeners for those events from being registered at all, which
  // covers the window-targeted ones (pageshow/pagehide/freeze/resume) that a capturing
  // window listener can't get ahead of. Only the blocked names are affected; every
  // other addEventListener call passes straight through untouched.
  var nativeAdd = EventTarget.prototype.addEventListener;
  EventTarget.prototype.addEventListener = function (type, listener, options) {
    if (enabled() && listener !== intercept && BLOCKED.indexOf(type) >= 0) {
      record('blocked listener for ' + type);
      return;
    }
    return nativeAdd.call(this, type, listener, options);
  };
})();
