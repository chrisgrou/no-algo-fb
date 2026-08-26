package com.chrisgrou.fbfeedwrapper.debug

/**
 * Grabs the HTML of whatever is actually visible on screen right now — samples
 * elementFromPoint down the viewport, walks a few levels up each hit for context,
 * dedups — rather than the whole page. Lets us read the real feed markup and fix
 * feed_filter.js's selectors without a desktop DevTools connection.
 */
const val DUMP_VIEWPORT_HTML_JS = """
(function () {
  var vw = window.innerWidth, vh = window.innerHeight;
  var seen = new Set();
  var blocks = [];
  for (var y = 40; y < vh; y += 100) {
    var el = document.elementFromPoint(Math.floor(vw / 2), y);
    if (!el) continue;
    var node = el;
    for (var i = 0; i < 4 && node.parentElement; i++) node = node.parentElement;
    if (seen.has(node)) continue;
    seen.add(node);
    blocks.push(node);
  }
  var html = blocks.map(function (b) { return b.outerHTML; }).join('\n\n<!-- ===== -->\n\n');
  return html.length > 60000 ? html.substring(0, 60000) + '\n...[truncated]' : html;
})();
"""
