// Adds an "App Settings" tile to Facebook's own bookmarks/Menu grid (Feature:
// bookmarks nav) — a second, independent way to reach Settings that doesn't touch the
// top tab bar at all. Only active on https://www.facebook.com/bookmarks/
// (document.title === "Menu").
//
// Same overlay philosophy as nav_override.js: never inserts into Facebook's own
// [role="list"] grid — an earlier top-bar feature learned the hard way that mutating
// a React-owned tree can make a whole subtree vanish on the next re-render. Instead
// this paints an independent tile of our own, positioned to visually sit in the next
// open grid slot, computed from the same width/margin numbers Facebook's own tiles
// use (an on-device capture found the grid: two 208px-wide columns at margin-left
// 12px/228px, each row 77px tall, right-column tiles pulled up with
// margin-top:-77px to sit beside their left neighbor instead of below it — the same
// "declarative layout via margin" scheme the top tab bar uses).
(function () {
  if (window.__ffwBookmarksNavInstalled) return;
  window.__ffwBookmarksNavInstalled = true;

  var TILE_WIDTH = 208;
  var TILE_HEIGHT = 77;
  var CARD_HEIGHT = 69;
  var CARD_TOP_INSET = 4;
  var COL_LEFT = 12;
  var COL_RIGHT = 228;

  var GEAR_SVG_PATH =
    'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.04-.7-1.63-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.44.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.63.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.04.7 1.63.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.63-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z';

  function isBookmarksPage() {
    return document.title === 'Menu' && location.pathname.indexOf('/bookmarks') !== -1;
  }

  function listContainer() {
    return document.querySelector('[role="list"]');
  }

  function gearSvg() {
    var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('width', '24');
    svg.setAttribute('height', '24');
    svg.style.fill = 'currentColor';
    svg.style.display = 'block';
    var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', GEAR_SVG_PATH);
    svg.appendChild(path);
    return svg;
  }

  var tile = null;

  // Icon-above-label, left-aligned — the same shape every native tile uses (an icon
  // badge near the top, the label directly under it), not the single-line
  // icon-beside-label row this used to be. The badge behind the gear (a plain
  // rounded-square fill) mirrors how every native tile's own icon is itself a small
  // colored graphic, not a bare glyph — a plain white gear floating with no badge was
  // what made this tile visually stick out as "not one of these" rather than blend
  // in as just another entry.
  function ensureTile() {
    if (tile) return tile;
    tile = document.createElement('div');
    tile.id = '__ffwBookmarksSettingsTile';
    tile.setAttribute('role', 'button');
    tile.setAttribute('aria-label', 'App Settings');
    tile.style.position = 'fixed';
    tile.style.display = 'none';
    tile.style.flexDirection = 'column';
    tile.style.alignItems = 'flex-start';
    tile.style.justifyContent = 'flex-start';
    tile.style.boxSizing = 'border-box';
    tile.style.paddingTop = '8px';
    tile.style.paddingLeft = '12px';
    tile.style.borderRadius = '8px';
    tile.style.color = '#f2f4f7';
    tile.style.zIndex = '999999';

    var badge = document.createElement('div');
    badge.style.width = '28px';
    badge.style.height = '28px';
    badge.style.flexShrink = '0';
    badge.style.borderRadius = '8px';
    badge.style.background = '#606770';
    badge.style.display = 'flex';
    badge.style.alignItems = 'center';
    badge.style.justifyContent = 'center';
    var svg = gearSvg();
    svg.setAttribute('width', '16');
    svg.setAttribute('height', '16');
    svg.style.color = '#ffffff';
    badge.appendChild(svg);
    tile.appendChild(badge);

    var label = document.createElement('span');
    label.textContent = 'App Settings';
    label.style.fontSize = '15px';
    label.style.marginTop = '7px';
    tile.appendChild(label);

    tile.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      window.NativeNav && window.NativeNav.requestOpenSettings();
    });
    document.body.appendChild(tile);
    return tile;
  }

  // The card's rounded background isn't set directly on the element itself — like the
  // post cards' own "bg-s*" classes, it's drawn via a ::before pseudo-element
  // consuming CSS custom properties this dump's inline <style> didn't carry (the rule
  // lives in an external stylesheet). Reading getComputedStyle's resolved pixel
  // values off a live neighboring tile — including its ::before, not just the element
  // itself — gets the real rendered color regardless of how it's implemented, the
  // same reasoning nav_override.js's backgroundBehind() already relies on. No border
  // is drawn at all: native tiles' own border is barely visible (~5% white), and a
  // hardcoded fallback for it was what made this tile visibly outlined next to ones
  // that aren't.
  function sampleCard() {
    var listItem = document.querySelector('[role="list"] > [role="listitem"]');
    return listItem ? listItem.querySelector('.nb') : null;
  }

  function resolvedColor(el, pseudo, prop) {
    if (!el) return null;
    var value = getComputedStyle(el, pseudo)[prop];
    if (value && value !== 'rgba(0, 0, 0, 0)' && value !== 'transparent' && value !== 'none') return value;
    return null;
  }

  function styleTileLike(el, sample) {
    var bg = resolvedColor(sample, '::before', 'backgroundColor') || resolvedColor(sample, null, 'backgroundColor');
    el.style.background = bg || '#3a3b3c';
  }

  // Positions the tile over whatever grid slot comes right after Facebook's own last
  // real tile — never a slot one of its own tiles already occupies. row/col derive
  // straight from how many [role="listitem"] children the list already has, the same
  // arithmetic Facebook's own layout uses (odd count so far → next slot is the left
  // column of a new row; even → the right column of the current last row).
  function sync() {
    if (!isBookmarksPage()) {
      if (tile) tile.style.display = 'none';
      return;
    }
    var list = listContainer();
    if (!list) {
      if (tile) tile.style.display = 'none';
      return;
    }
    var items = list.querySelectorAll(':scope > [role="listitem"]');
    var count = items.length;
    if (count === 0) {
      if (tile) tile.style.display = 'none';
      return;
    }
    var col = count % 2;
    var row = Math.floor(count / 2);

    // A brand new row beyond Facebook's own current rows needs the list container's
    // own reserved height to grow to match — otherwise real content right after it
    // (the "Settings & privacy" section) would render where our tile visually sits,
    // since this tile is position:fixed and doesn't participate in document flow the
    // way a real extra row would. Only a single, additive style write (like
    // nav_bar_watchdog.js's own inline style override), never touching the list's
    // children.
    if (col === 0) {
      var needed = (row + 1) * TILE_HEIGHT;
      var current = list.getBoundingClientRect().height;
      if (current < needed) list.style.height = needed + 'px';
    }

    var listRect = list.getBoundingClientRect();
    if (listRect.width === 0) {
      if (tile) tile.style.display = 'none';
      return;
    }

    var el = ensureTile();
    styleTileLike(el, sampleCard());
    el.style.left = (listRect.left + (col === 0 ? COL_LEFT : COL_RIGHT)) + 'px';
    el.style.top = (listRect.top + row * TILE_HEIGHT + CARD_TOP_INSET) + 'px';
    el.style.width = TILE_WIDTH + 'px';
    el.style.height = CARD_HEIGHT + 'px';
    el.style.display = 'flex';
  }

  sync();

  window.addEventListener('scroll', sync, { passive: true, capture: true });
  window.addEventListener('resize', sync);

  var timer = null;
  var observer = new MutationObserver(function () {
    if (timer) return;
    timer = setTimeout(function () {
      timer = null;
      sync();
    }, 150);
  });
  observer.observe(document.body, { childList: true, subtree: true });
})();
