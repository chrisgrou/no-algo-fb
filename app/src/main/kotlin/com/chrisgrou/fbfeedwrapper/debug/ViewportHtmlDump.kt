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
  var POST = '[data-tracking-duration-id]';
  var LINK = '[role="link"]';
  var allowed = [];
  try { allowed = JSON.parse(window.NativeFilter.getAllowedAuthorsJson()); } catch (e) {}
  var lines = ['ALLOW-LIST: ' + JSON.stringify(allowed), ''];
  var posts = document.querySelectorAll(POST);
  lines.push('POST CONTAINERS FOUND: ' + posts.length);
  lines.push('AVATARS FOUND: ' + document.querySelectorAll(AV).length);
  lines.push('');
  // Iterates containers, not avatars: posts whose avatar lacks the testid (group
  // posts, some ads) are precisely the ones that used to be skipped entirely.
  for (var i = 0; i < posts.length; i++) {
    var post = posts[i];
    var nested = post.parentElement && post.parentElement.closest(POST) ? ' NESTED' : '';
    var link = post.querySelector(LINK);
    var source = link ? (link.textContent || '').replace(/\s+/g, ' ').trim() : '';
    var a = post.querySelector(AV);
    var label = a ? (a.getAttribute('aria-label') || '(none)') : '(no avatar w/ testid)';
    var pr = post.getBoundingClientRect();

    lines.push('[' + i + ']' + nested + ' trackingId=' + post.getAttribute('data-tracking-duration-id'));
    lines.push('    source (first role=link) = "' + source + '"  allowed=' + (allowed.indexOf(source) >= 0));
    lines.push('    avatar aria-label        = "' + label + '"');
    lines.push('    post rect: top=' + Math.round(pr.top) + ' w=' + Math.round(pr.width) + ' h=' + Math.round(pr.height) +
      ' hiddenAttr=' + post.getAttribute('data-ffw-hidden') + ' display=' + getComputedStyle(post).display);
    lines.push('    post text: "' + (post.innerText || '').replace(/\s+/g, ' ').substring(0, 160) + '"');
    lines.push('');
  }
  return lines.join('\n');
})();
"""

/**
 * Diagnostic for the top nav bar (still-unsolved: it sometimes doesn't reappear on
 * scroll-up after Facebook hides it) plus the two page-identity signals the rest of
 * the app now depends on (document.title, drives both feed_filter.js's isFeedPage()
 * and the floating Settings icon's visibility) — so a capture right when the bar is
 * missing shows both "is it really gone" and "does the app agree about what page
 * this is" in one file, instead of needing several separate captures.
 */
const val DUMP_NAV_REPORT_JS = """
(function () {
  var lines = ['===== NAV BAR DIAGNOSTIC ====='];
  lines.push('document.title = "' + document.title + '"');
  lines.push('location.href = "' + location.href + '"');
  lines.push('window.scrollY = ' + window.scrollY);
  lines.push('');

  var tablists = document.querySelectorAll('[role="tablist"]');
  lines.push('[role="tablist"] COUNT: ' + tablists.length);
  for (var k = 0; k < tablists.length; k++) {
    var tl = tablists[k];
    var tlRect = tl.getBoundingClientRect();
    var tlStyle = getComputedStyle(tl);
    lines.push('  [' + k + '] rect: top=' + Math.round(tlRect.top) + ' w=' + Math.round(tlRect.width) +
      ' h=' + Math.round(tlRect.height) + ' display=' + tlStyle.display +
      ' visibility=' + tlStyle.visibility + ' opacity=' + tlStyle.opacity);
    lines.push('  inline style="' + (tl.getAttribute('style') || '') + '"');
    var p = tl.parentElement;
    var depth = 0;
    while (p && depth < 3) {
      var pr = p.getBoundingClientRect();
      var ps = getComputedStyle(p);
      lines.push('  ancestor[' + depth + '] tag=' + p.tagName + ' rect: h=' + Math.round(pr.height) +
        ' display=' + ps.display + ' style="' + (p.getAttribute('style') || '') + '"');
      p = p.parentElement;
      depth++;
    }
  }
  lines.push('');

  var tabs = document.querySelectorAll('[role="tab"]');
  lines.push('[role="tab"] COUNT: ' + tabs.length);
  for (var i = 0; i < tabs.length; i++) {
    var t = tabs[i];
    var tRect = t.getBoundingClientRect();
    lines.push('  [' + i + '] aria-label="' + (t.getAttribute('aria-label') || '') + '" tag=' + t.tagName +
      ' rect: top=' + Math.round(tRect.top) + ' w=' + Math.round(tRect.width) + ' h=' + Math.round(tRect.height));
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
