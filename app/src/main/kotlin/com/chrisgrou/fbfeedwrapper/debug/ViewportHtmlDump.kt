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

/** Writes [html] to a cache file and opens the system share sheet for it, so it can be
 *  sent anywhere (email, messaging, "save to files") without the clipboard's size limits. */
fun shareHtmlDump(context: Context, html: String) {
    val dir = File(context.cacheDir, "debug").apply { mkdirs() }
    val file = File(dir, "feed_dump_${System.currentTimeMillis()}.html")
    file.writeText(html)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/html"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Αποστολή feed HTML dump").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
