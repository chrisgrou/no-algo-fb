package com.chrisgrou.fbfeedwrapper.debug

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Grabs the HTML of whatever is actually visible on screen right now — samples
 * elementFromPoint down the viewport, walks a few levels up each hit for context,
 * dedups — rather than the whole page. Lets us read the real feed markup and fix
 * feed_filter.js's selectors without a desktop DevTools connection.
 *
 * No hard truncation here: the result goes to a file (see [shareHtmlDump]), not the
 * clipboard, so there's no practical size limit to guard against — only a generous
 * safety cap against a pathological capture.
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
  var SAFETY_CAP = 2000000;
  return html.length > SAFETY_CAP ? html.substring(0, SAFETY_CAP) + '\n...[truncated]' : html;
})();
"""

/**
 * Per-post report of what the filter actually sees and does: the avatar's aria-label,
 * the author name derived from it, whether that name is in the allow-list, and the
 * rects of both the avatar and the row we picked. Four hiding mechanisms in a row have
 * reported success while posts stayed on screen, so this replaces guessing about the
 * cause with the ground truth of what the running filter reads for each post.
 *
 * Prepended to the HTML capture rather than shipped separately so one shared file
 * carries both the decisions and the markup they were made from.
 */
const val DUMP_FILTER_REPORT_JS = """
(function () {
  var AV = '[data-testid^="post-profile-image-"]';
  var SC = '[data-type="vscroller"]';
  var allowed = [];
  try { allowed = JSON.parse(window.NativeFilter.getAllowedAuthorsJson()); } catch (e) {}
  var lines = ['ALLOW-LIST: ' + JSON.stringify(allowed), ''];
  var avatars = document.querySelectorAll(AV);
  lines.push('AVATARS FOUND: ' + avatars.length);
  lines.push('SCROLLERS FOUND: ' + document.querySelectorAll(SC).length);
  lines.push('');
  for (var i = 0; i < avatars.length; i++) {
    var a = avatars[i];
    var label = a.getAttribute('aria-label') || '(none)';
    var name = label.replace(/ Profile Picture${'$'}/, '');
    var scroller = a.closest(SC);
    var row = null;
    if (scroller) {
      var n = a;
      while (n && n.parentElement && n.parentElement !== scroller) n = n.parentElement;
      row = (n && n.parentElement === scroller) ? n : null;
    }
    var ar = a.getBoundingClientRect();
    var rr = row ? row.getBoundingClientRect() : null;
    lines.push('[' + i + '] testid=' + a.getAttribute('data-testid'));
    lines.push('    aria-label = "' + label + '"');
    lines.push('    derived    = "' + name + '"  allowed=' + (allowed.indexOf(name) >= 0));
    lines.push('    avatar rect: top=' + Math.round(ar.top) + ' w=' + Math.round(ar.width) + ' h=' + Math.round(ar.height));
    lines.push('    row found  : ' + (row ? 'yes' : 'NO') +
      (rr ? (' rect top=' + Math.round(rr.top) + ' w=' + Math.round(rr.width) + ' h=' + Math.round(rr.height)) : '') +
      (row ? (' hiddenAttr=' + row.getAttribute('data-ffw-hidden') + ' display=' + getComputedStyle(row).display) : ''));
    // The text near the avatar is what the user actually reads as the post's source;
    // if it differs from the aria-label, that mismatch is the bug.
    var header = a.parentElement && a.parentElement.parentElement;
    if (header) lines.push('    header text: "' + (header.innerText || '').replace(/\s+/g, ' ').substring(0, 120) + '"');
    lines.push('');
  }
  return lines.join('\n');
})();
"""

/** Writes the debug capture to a cache file and opens the system share sheet for it, so
 *  it can be sent anywhere (email, messaging, "save to files") without the clipboard's
 *  size limits. */
fun shareHtmlDump(context: Context, report: String, html: String) {
    val dir = File(context.cacheDir, "debug").apply { mkdirs() }
    val file = File(dir, "feed_dump_${System.currentTimeMillis()}.txt")
    file.writeText("===== FILTER REPORT =====\n\n$report\n\n===== VIEWPORT HTML =====\n\n$html")

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Αποστολή feed debug dump").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
