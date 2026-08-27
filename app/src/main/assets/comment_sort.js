// Applies the user's preferred comment-sort order (Feature: comment sort) on every
// post/comments view instead of leaving it at Facebook's own default ("Most
// relevant") every single time. Facebook's dropdown trigger shows the label
// currently in effect ("Most relevant", "Newest", or "All comments"), so a trigger
// already showing the preferred label needs nothing done to it.
(function () {
  if (window.__ffwSortInstalled) return;
  window.__ffwSortInstalled = true;

  var LABELS = ['Most relevant', 'Newest', 'All comments'];
  var CHECKED_ATTR = 'data-ffw-sort-checked';

  function preferred() {
    try {
      return (window.NativeCommentSort && window.NativeCommentSort.getPreferredSort()) || 'Most relevant';
    } catch (e) {
      return 'Most relevant';
    }
  }

  function textOf(el) {
    return (el.textContent || '').replace(/\s+/g, ' ').trim();
  }

  // The dropdown trigger is a small role="button" whose entire visible text is one
  // of the three sort labels — scoping to role="button" (rather than every element)
  // keeps this cheap even on a long comments thread.
  function findTriggers() {
    var want = preferred();
    var out = [];
    var buttons = document.querySelectorAll('[role="button"]:not([' + CHECKED_ATTR + '])');
    for (var i = 0; i < buttons.length; i++) {
      var btn = buttons[i];
      var t = textOf(btn);
      if (LABELS.indexOf(t) !== -1) out.push({ el: btn, current: t, want: want });
    }
    return out;
  }

  // Facebook opens the sort menu as a new, separately-rendered layer (not a child of
  // the trigger), so the matching item has to be searched for after the click rather
  // than looked up inside the trigger itself. Retries because that layer renders
  // asynchronously.
  function clickMenuItemWhenReady(want, attemptsLeft) {
    if (attemptsLeft <= 0) return;
    var candidates = document.querySelectorAll('[role="menuitem"], [role="menuitemradio"], [role="option"]');
    for (var i = 0; i < candidates.length; i++) {
      if (textOf(candidates[i]) === want) {
        candidates[i].click();
        return;
      }
    }
    setTimeout(function () { clickMenuItemWhenReady(want, attemptsLeft - 1); }, 150);
  }

  function apply() {
    var triggers = findTriggers();
    for (var i = 0; i < triggers.length; i++) {
      var trigger = triggers[i];
      trigger.el.setAttribute(CHECKED_ATTR, '1');
      if (trigger.current === trigger.want) continue;
      trigger.el.click();
      clickMenuItemWhenReady(trigger.want, 10);
    }
  }

  apply();

  // New comment sections (opening a post, more of the feed loading in) appear via
  // Facebook's own DOM updates, not a page load — this only reads/clicks through
  // Facebook's normal event handling (never edits its DOM directly), so it carries
  // none of the risk that mutating its markup directly did (see nav_override.js).
  var applyTimer = null;
  var observer = new MutationObserver(function () {
    if (applyTimer) return;
    applyTimer = setTimeout(function () {
      applyTimer = null;
      apply();
    }, 300);
  });
  observer.observe(document.body, { childList: true, subtree: true });
})();
