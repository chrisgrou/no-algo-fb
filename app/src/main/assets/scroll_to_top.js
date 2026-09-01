// Floating "back to top" button, bottom-center (Feature: scroll to top). Shown once
// the user has scrolled down meaningfully on the main feed, hidden near the top and
// on any other screen (a post, Replies, ...) — purely additive, same approach as
// nav_override.js's overlay: an independent element of our own, never a mutation of
// Facebook's own DOM. Togglable from Settings → Βελτιώσεις (see
// FeedDisplayPreferences.showScrollTopButton), same on/off-in-Settings shape as the
// hide-reactions/hide-suggested toggles in feed_display.js.
(function () {
  if (window.__ffwScrollTopInstalled) return;
  window.__ffwScrollTopInstalled = true;

  var SHOW_THRESHOLD = 600;

  function scroller() {
    return document.querySelector('[data-type="vscroller"]') || document.scrollingElement || document.body;
  }

  function currentY() {
    var sc = scroller();
    return sc.scrollTop || window.scrollY || 0;
  }

  // Same check feed_filter.js's own isFeedPage() uses — see the long comment there for
  // why BOTH document.title and location.pathname are required: title alone missed a
  // post's own permalink page (story.php), which keeps title "Facebook" but changes
  // the path; pathname alone misses the "Replies" screen, which keeps the path "/"
  // but changes the title. Only requiring both to agree correctly excludes either.
  function isFeedPage() {
    return document.title === 'Facebook' && (location.pathname === '/' || location.pathname === '');
  }

  function buttonSize() {
    try {
      var size = window.NativeDisplay ? window.NativeDisplay.getButtonSize() : 52;
      return size > 0 ? size : 52;
    } catch (e) {
      return 52;
    }
  }

  var button = null;

  function ensureButton() {
    if (button) return button;
    button = document.createElement('div');
    button.id = '__ffwScrollTopButton';
    button.setAttribute('role', 'button');
    button.setAttribute('aria-label', 'Επιστροφή στην κορυφή');
    button.style.position = 'fixed';
    button.style.left = '50%';
    button.style.bottom = '16px';
    button.style.transform = 'translateX(-50%)';
    button.style.borderRadius = '50%';
    button.style.display = 'none';
    button.style.alignItems = 'center';
    button.style.justifyContent = 'center';
    button.style.background = 'rgba(24,25,26,0.35)';
    // Slightly yellow rather than plain white, matching post_nav.js's prev/next
    // glyphs, so it stands out against the semi-transparent dark background.
    button.style.color = '#ffd966';
    button.style.fontWeight = 'bold';
    button.style.lineHeight = '1';
    button.style.zIndex = '999999';
    button.style.boxShadow = '0 2px 6px rgba(0,0,0,0.2)';

    // Same "›" glyph the post_nav.js prev/next buttons use, just rotated to point up
    // instead of right, so all three floating buttons read as one consistent set.
    var glyph = document.createElement('span');
    glyph.textContent = '›';
    glyph.style.display = 'inline-block';
    glyph.style.transform = 'rotate(-90deg)';
    button.appendChild(glyph);

    button.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      var sc = scroller();
      if (sc.scrollTo) sc.scrollTo({ top: 0, behavior: 'smooth' });
      else sc.scrollTop = 0;
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });

    document.body.appendChild(button);
    return button;
  }

  function enabled() {
    try {
      return !window.NativeDisplay || window.NativeDisplay.getShowScrollTopButton();
    } catch (e) {
      return true;
    }
  }

  function update() {
    var visible = isFeedPage() && enabled() && currentY() > SHOW_THRESHOLD;
    var el = ensureButton();
    var size = buttonSize();
    el.style.width = size + 'px';
    el.style.height = size + 'px';
    el.style.fontSize = Math.round(size * 0.46) + 'px';
    el.style.display = visible ? 'flex' : 'none';
  }

  // Re-applies the enabled/disabled preference immediately when the user flips the
  // Settings toggle, same shape as feed_display.js's __ffwRefreshDisplay.
  window.__ffwRefreshScrollTop = update;

  update();

  window.addEventListener('scroll', update, { passive: true, capture: true });

  // document.title changes when opening/leaving a post, but that isn't a scroll or a
  // DOM mutation this script would otherwise see — a lightweight poll is simpler and
  // cheaper here than wiring a MutationObserver onto <title> for something that only
  // needs to be noticed within a second or so of it happening.
  setInterval(update, 500);
})();
