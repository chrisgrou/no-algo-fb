// Floating "back to top" button, bottom-right (Feature: scroll to top). Shown once
// the user has scrolled down meaningfully, hidden near the top — purely additive,
// same approach as nav_override.js's overlay: an independent element of our own,
// never a mutation of Facebook's own DOM. Togglable from Settings → Βελτιώσεις (see
// FeedDisplayPreferences.showScrollTopButton), same on/off-in-Settings shape as the
// hide-reactions/hide-suggested toggles in feed_display.js.
(function () {
  if (window.__ffwScrollTopInstalled) return;
  window.__ffwScrollTopInstalled = true;

  var SHOW_THRESHOLD = 600;
  var UP_ARROW_SVG_PATH = 'M12 4l-8 8h5v8h6v-8h5z';

  function scroller() {
    return document.querySelector('[data-type="vscroller"]') || document.scrollingElement || document.body;
  }

  function currentY() {
    var sc = scroller();
    return sc.scrollTop || window.scrollY || 0;
  }

  var button = null;

  function ensureButton() {
    if (button) return button;
    button = document.createElement('div');
    button.id = '__ffwScrollTopButton';
    button.setAttribute('role', 'button');
    button.setAttribute('aria-label', 'Επιστροφή στην κορυφή');
    button.style.position = 'fixed';
    button.style.right = '16px';
    button.style.bottom = '16px';
    button.style.width = '44px';
    button.style.height = '44px';
    button.style.borderRadius = '50%';
    button.style.display = 'none';
    button.style.alignItems = 'center';
    button.style.justifyContent = 'center';
    button.style.background = 'rgba(24,25,26,0.45)';
    button.style.color = '#ffffff';
    button.style.zIndex = '999999';
    button.style.boxShadow = '0 2px 6px rgba(0,0,0,0.25)';

    var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('width', '24');
    svg.setAttribute('height', '24');
    svg.style.fill = 'currentColor';
    var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', UP_ARROW_SVG_PATH);
    svg.appendChild(path);
    button.appendChild(svg);

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
    var visible = enabled() && currentY() > SHOW_THRESHOLD;
    var el = ensureButton();
    el.style.display = visible ? 'flex' : 'none';
  }

  // Re-applies the enabled/disabled preference immediately when the user flips the
  // Settings toggle, same shape as feed_display.js's __ffwRefreshDisplay.
  window.__ffwRefreshScrollTop = update;

  update();

  window.addEventListener('scroll', update, { passive: true, capture: true });
})();
