// Floating "previous / next post" buttons, bottom-left (Feature: post navigation).
// Jumps to whichever visible post — the same [data-tracking-duration-id] cards
// feed_filter.js already scans, skipping nested (shared/quoted) and hidden ones the
// same way that script does — sits just above or below the current one, aligning its
// own top edge with the top of the scroller. Purely additive, never touches
// Facebook's own DOM: an independent pair of elements of our own painted on top, same
// approach as scroll_to_top.js/nav_override.js. Togglable from Settings → Βελτιώσεις
// (see FeedDisplayPreferences.showPostNavButtons).
(function () {
  if (window.__ffwPostNavInstalled) return;
  window.__ffwPostNavInstalled = true;

  var POST_SELECTOR = '[data-tracking-duration-id]';
  var HIDDEN_ATTR = 'data-ffw-hidden';
  var EDGE_THRESHOLD = 4;

  function scroller() {
    return document.querySelector('[data-type="vscroller"]') || document.scrollingElement || document.body;
  }

  function visiblePosts() {
    var all = document.querySelectorAll(POST_SELECTOR);
    var out = [];
    for (var i = 0; i < all.length; i++) {
      var post = all[i];
      if (post.parentElement && post.parentElement.closest(POST_SELECTOR)) continue;
      if (post.hasAttribute(HIDDEN_ATTR)) continue;
      var rect = post.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) continue;
      out.push(post);
    }
    return out;
  }

  function scrollToPost(post) {
    var sc = scroller();
    var delta = post.getBoundingClientRect().top - sc.getBoundingClientRect().top;
    var target = sc.scrollTop + delta;
    if (sc.scrollTo) sc.scrollTo({ top: target, behavior: 'smooth' });
    else sc.scrollTop = target;
  }

  // First post below the current viewport top — the post "at the top" right now
  // already has top <= EDGE_THRESHOLD, so this naturally skips it and lands on the
  // next one, whether the current one is fully in view or only partway scrolled past.
  function nextPost() {
    var posts = visiblePosts();
    for (var i = 0; i < posts.length; i++) {
      if (posts[i].getBoundingClientRect().top > EDGE_THRESHOLD) return posts[i];
    }
    return null;
  }

  // Mirror of nextPost(): the last post whose top has already scrolled above the
  // viewport.
  function prevPost() {
    var posts = visiblePosts();
    for (var i = posts.length - 1; i >= 0; i--) {
      if (posts[i].getBoundingClientRect().top < -EDGE_THRESHOLD) return posts[i];
    }
    return null;
  }

  var prevButton = null;
  var nextButton = null;

  function makeButton(id, label, ariaLabel, left) {
    var btn = document.createElement('div');
    btn.id = id;
    btn.setAttribute('role', 'button');
    btn.setAttribute('aria-label', ariaLabel);
    btn.style.position = 'fixed';
    btn.style.left = left;
    btn.style.bottom = '16px';
    btn.style.width = '44px';
    btn.style.height = '44px';
    btn.style.borderRadius = '50%';
    btn.style.display = 'none';
    btn.style.alignItems = 'center';
    btn.style.justifyContent = 'center';
    btn.style.background = 'rgba(24,25,26,0.45)';
    btn.style.color = '#ffffff';
    btn.style.fontSize = '20px';
    btn.style.fontWeight = 'bold';
    btn.style.lineHeight = '1';
    btn.style.zIndex = '999999';
    btn.style.boxShadow = '0 2px 6px rgba(0,0,0,0.25)';
    btn.textContent = label;
    document.body.appendChild(btn);
    return btn;
  }

  function ensureButtons() {
    if (!prevButton) {
      prevButton = makeButton('__ffwPrevPostButton', '‹', 'Προηγούμενο post', '16px');
      prevButton.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        var p = prevPost();
        if (p) scrollToPost(p);
      });
    }
    if (!nextButton) {
      // 68px = prevButton's left (16) + its width (44) + an 8px gap.
      nextButton = makeButton('__ffwNextPostButton', '›', 'Επόμενο post', '68px');
      nextButton.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        var n = nextPost();
        if (n) scrollToPost(n);
      });
    }
  }

  function enabled() {
    try {
      return !window.NativeDisplay || window.NativeDisplay.getShowPostNavButtons();
    } catch (e) {
      return true;
    }
  }

  function update() {
    ensureButtons();
    var on = enabled();
    prevButton.style.display = on && prevPost() ? 'flex' : 'none';
    nextButton.style.display = on && nextPost() ? 'flex' : 'none';
  }

  // Re-applies the enabled/disabled preference immediately when the user flips the
  // Settings toggle, same shape as scroll_to_top.js's __ffwRefreshScrollTop.
  window.__ffwRefreshPostNav = update;

  // Scanning every post's rect on each raw scroll event would run dozens of times per
  // fling on a long feed; batching to one check per animation frame keeps this cheap
  // the same way feed_filter.js's own scroll/mutation handling already is.
  var raf = null;
  function scheduleUpdate() {
    if (raf) return;
    raf = requestAnimationFrame(function () {
      raf = null;
      update();
    });
  }

  update();

  window.addEventListener('scroll', scheduleUpdate, { passive: true, capture: true });

  var timer = null;
  var observer = new MutationObserver(function () {
    if (timer) return;
    timer = setTimeout(function () {
      timer = null;
      update();
    }, 200);
  });
  observer.observe(document.body, { childList: true, subtree: true });
})();
