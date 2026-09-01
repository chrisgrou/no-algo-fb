// Feed display toggles (Feature: hide reactions / hide suggested groups / hide
// suggested people). All off by default and controlled from Settings — see
// FeedDisplayPreferences.
(function () {
  if (window.__ffwDisplayInstalled) return;
  window.__ffwDisplayInstalled = true;

  var HIDDEN_ATTR = 'data-ffw-display-hidden';
  // Classification is permanent once decided (an element either is or isn't a
  // reaction pill / suggested-groups block / suggested-people block); the CHECKED
  // attrs memoize that decision so a long infinite-scroll session only ever
  // re-classifies nodes newly added since the last pass — the same reasoning
  // feed_filter.js's DECIDED_ATTR documents. Whether a classified element is actually
  // hidden is separate and re-applied every pass instead, since that depends on the
  // current (togglable) preference, not on anything about the node itself.
  var REACTION_CHECKED_ATTR = 'data-ffw-reaction-checked';
  var REACTION_MARK_ATTR = 'data-ffw-is-reaction';
  var SUGGESTED_CHECKED_ATTR = 'data-ffw-suggested-checked';
  var SUGGESTED_MARK_ATTR = 'data-ffw-is-suggested';
  var PEOPLE_CHECKED_ATTR = 'data-ffw-people-checked';
  var PEOPLE_MARK_ATTR = 'data-ffw-is-people';
  var SCROLLER_SELECTOR = '[data-type="vscroller"]';

  if (!document.getElementById('ffw-display-style')) {
    var style = document.createElement('style');
    style.id = 'ffw-display-style';
    style.textContent = '[' + HIDDEN_ATTR + '="1"]{display:none !important;}';
    (document.head || document.documentElement).appendChild(style);
  }

  function prefs() {
    try {
      return {
        hideReactions: !!(window.NativeDisplay && window.NativeDisplay.getHideReactions()),
        hideSuggested: !!(window.NativeDisplay && window.NativeDisplay.getHideSuggested()),
        hidePeople: !!(window.NativeDisplay && window.NativeDisplay.getHidePeopleYouMayKnow()),
      };
    } catch (e) {
      return { hideReactions: false, hideSuggested: false, hidePeople: false };
    }
  }

  function setHidden(el, hidden) {
    if (hidden) el.setAttribute(HIDDEN_ATTR, '1');
    else el.removeAttribute(HIDDEN_ATTR);
  }

  // Matches the reaction-count pill's own accessible name — confirmed against two
  // separate on-device captures, not guessed, and it turns out to differ by level:
  // a comment's pill reads "2 reactions"/"1 Reaction", but a post's own pill (what
  // the "👍 1" summary above the Like/Comment/Share row actually is) reads "1
  // reacted. Tap to see comments and reactions" or "You and 333 others reacted. Tap
  // to see comments and reactions" instead — a completely different accessible-name
  // shape. The comment-level pattern alone was hiding comment reactions fine while
  // leaving every post's own reaction pill untouched, which is what was reported.
  var REACTIONS_RE = /^\d+\s+reactions?$/i;
  var REACTED_RE = /reacted\.\s*tap to see comments and reactions$/i;

  function applyReactions(enabled) {
    var newNodes = document.querySelectorAll('[aria-label]:not([' + REACTION_CHECKED_ATTR + '])');
    for (var i = 0; i < newNodes.length; i++) {
      var node = newNodes[i];
      node.setAttribute(REACTION_CHECKED_ATTR, '1');
      var label = (node.getAttribute('aria-label') || '').trim();
      if (REACTIONS_RE.test(label) || REACTED_RE.test(label)) node.setAttribute(REACTION_MARK_ATTR, '1');
    }
    var marked = document.querySelectorAll('[' + REACTION_MARK_ATTR + '="1"]');
    for (var j = 0; j < marked.length; j++) setHidden(marked[j], enabled);
  }

  // Facebook's own group-suggestion and people-suggestion carousels don't carry a
  // distinctive selector (unlike a post's data-tracking-duration-id), so this looks
  // for the block's exact heading text instead and climbs to whatever direct child of
  // the feed scroller contains it — the same "climb to the nearest scroller child"
  // shape feed_filter.js uses for a post's own wrapper. Shared by applySuggested() and
  // applyPeopleYouMayKnow() below, which only differ in the heading text they match
  // and which attrs they memoize under.
  function applyHeadingBlock(headingText, checkedAttr, markAttr, enabled) {
    var newLeaves = document.querySelectorAll('*:not([' + checkedAttr + '])');
    for (var i = 0; i < newLeaves.length; i++) {
      var el = newLeaves[i];
      el.setAttribute(checkedAttr, '1');
      if (el.children.length > 0) continue;
      var text = (el.textContent || '').trim();
      if (text !== headingText) continue;

      var scroller = el.closest(SCROLLER_SELECTOR);
      var node = el;
      var guard = 0;
      while (node.parentElement && node.parentElement !== scroller && guard++ < 12) {
        node = node.parentElement;
      }
      node.setAttribute(markAttr, '1');
    }
    var marked = document.querySelectorAll('[' + markAttr + '="1"]');
    for (var j = 0; j < marked.length; j++) setHidden(marked[j], enabled);
  }

  function applySuggested(enabled) {
    applyHeadingBlock('Suggested for you', SUGGESTED_CHECKED_ATTR, SUGGESTED_MARK_ATTR, enabled);
  }

  function applyPeopleYouMayKnow(enabled) {
    applyHeadingBlock('People you may know', PEOPLE_CHECKED_ATTR, PEOPLE_MARK_ATTR, enabled);
  }

  function apply() {
    var p = prefs();
    applyReactions(p.hideReactions);
    applySuggested(p.hideSuggested);
    applyPeopleYouMayKnow(p.hidePeople);
  }

  // Re-applies against the current preference values (not just newly-added DOM) so
  // flipping a toggle in Settings takes effect on the already-loaded feed without a
  // refresh — Settings calls this the same way it does __ffwRefreshAllowed.
  window.__ffwRefreshDisplay = apply;

  apply();

  var timer = null;
  var observer = new MutationObserver(function (mutations) {
    // Classification is memoized per node (see REACTION_CHECKED_ATTR above), which
    // would otherwise permanently skip re-checking a node whose aria-label just
    // changed — exactly the case this attribute observer exists to catch. Un-mark
    // it here so the debounced apply() below re-classifies it from scratch.
    for (var i = 0; i < mutations.length; i++) {
      var m = mutations[i];
      if (m.type === 'attributes' && m.target.nodeType === 1) {
        m.target.removeAttribute(REACTION_CHECKED_ATTR);
      }
    }
    if (timer) return;
    timer = setTimeout(function () {
      timer = null;
      apply();
    }, 300);
  });
  // attributeFilter: ['aria-label'], not attributes:true broadly — an earlier version
  // of a different script (nav_override.js) watched every attribute and measurably
  // slowed the cold-launch feed render. But childList alone missed real cases here:
  // the reaction pill's aria-label ("N reactions") isn't always present on the node
  // when it's first inserted — Facebook sets it slightly later, an attribute-only
  // change with no accompanying childList mutation — so those pills were never
  // reclassified and stayed unhidden. Scoping the filter to just aria-label keeps
  // this cheap while still catching that case.
  observer.observe(document.body, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['aria-label'],
  });
})();
