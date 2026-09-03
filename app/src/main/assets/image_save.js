// Long-press-to-save for images. Not relying on Android WebView's own native
// long-press detection: an on-device test found it never fired at all on a Facebook
// photo, most likely because Facebook's own photo viewer calls preventDefault() on
// touchstart for its pinch/zoom/swipe gestures — which stops the platform's gesture
// detector from ever recognizing the touch as a long press to begin with, regardless
// of what a native View.OnLongClickListener is set up to do. This detects the hold
// itself in JS instead, on capture-phase passive listeners so it sees every touch
// before the page's own handlers can consume it, and works the same either way.
(function () {
  if (window.__ffwImageSaveInstalled) return;
  window.__ffwImageSaveInstalled = true;

  var LONG_PRESS_MS = 500;
  // Cancels the hold if the finger actually moves — a scroll or swipe starting on top
  // of an image shouldn't be mistaken for someone holding still on it.
  var MOVE_TOLERANCE = 12;

  var timer = null;
  var startX = 0;
  var startY = 0;

  // Not just <img>: a first version of this only checked tagName === 'IMG' and found
  // nothing at all on the full-screen photo viewer (reported as long-press doing
  // literally nothing there) — that view most likely paints the photo as a CSS
  // background-image on a div instead, for its own pinch/zoom layering. Checking
  // every element in z-order at the point (not just the topmost) covers both cases,
  // since either an <img> or a background-image can sit under an interactive overlay
  // that would otherwise be the only thing elementFromPoint alone returns.
  function backgroundImageUrl(el) {
    var bg = getComputedStyle(el).backgroundImage;
    var match = bg && /url\(["']?([^"')]+)["']?\)/.exec(bg);
    return match ? match[1] : null;
  }

  function imageUrlAt(x, y) {
    var stack = document.elementsFromPoint ? document.elementsFromPoint(x, y) : [document.elementFromPoint(x, y)];
    for (var i = 0; i < stack.length; i++) {
      var el = stack[i];
      if (!el) continue;
      if (el.tagName === 'IMG' && el.src) return el.src;
      var bgUrl = backgroundImageUrl(el);
      if (bgUrl) return bgUrl;
    }
    return null;
  }

  // The image's own src isn't always a fetchable http(s) URL: an on-device test of the
  // "..." menu's own Save button (WebView.setDownloadListener, in MainActivity) failed
  // with "Expected URL scheme 'http' or 'https'" — the photo viewer had handed it a
  // blob: URL instead, which only means anything inside the page's own JS context that
  // created it (via URL.createObjectURL); no native HTTP client can dereference one.
  // Resolving it here, where that context still exists, and sending the actual bytes
  // as a data: URL is the only way to save it at all. http(s) URLs skip straight past
  // this and go to Kotlin as a plain URL — no reason to pull image bytes through this
  // page's JS and re-encode them as base64 when the native side can just fetch them
  // directly, and doing that always would multiply memory use for every save.
  function sendForSave(url) {
    if (!window.NativeMedia) return;
    if (url.indexOf('blob:') === 0 || url.indexOf('data:') === 0) {
      resolveAndSend(url);
    } else {
      window.NativeMedia.onImageUrl(url);
    }
  }

  // A blob: URL can go stale by the time this runs: Facebook's own code created it for
  // its own one-shot internal use (its "Save" button's own download flow) and, on the
  // setDownloadListener path in particular, likely already revoked it
  // (URL.revokeObjectURL) right after using it, before our native onDownloadStart
  // callback even fires and asks this function to re-fetch the same URL. A first
  // version had no failure reporting at all here — fetch() rejecting just silently did
  // nothing, indistinguishable from this function never having run in the first place.
  // Now it always tells the native side one way or the other.
  function resolveAndSend(url) {
    fetch(url)
      .then(function (res) { return res.blob(); })
      .then(function (blob) {
        var reader = new FileReader();
        reader.onloadend = function () {
          // reader.result is itself a data: URL ("data:image/jpeg;base64,...."),
          // already exactly what MediaDownloader.saveImageDataUrl expects.
          window.NativeMedia && window.NativeMedia.onImageDataUrl(String(reader.result));
        };
        reader.onerror = function () {
          window.NativeMedia && window.NativeMedia.onImageResolveFailed('FileReader error');
        };
        reader.readAsDataURL(blob);
      })
      .catch(function (err) {
        window.NativeMedia && window.NativeMedia.onImageResolveFailed(String((err && err.message) || err));
      });
  }

  // Exposed for MainActivity's setDownloadListener to call into when Facebook's own
  // "Save" button (the "..." menu) hands it a blob:/data: URL too — same problem,
  // same fix, just triggered from the native side instead of a long-press.
  window.__ffwResolveImageForSave = resolveAndSend;

  function cancel() {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  }

  document.addEventListener('touchstart', function (e) {
    if (e.touches.length !== 1) {
      cancel();
      return;
    }
    var touch = e.touches[0];
    startX = touch.clientX;
    startY = touch.clientY;
    cancel();
    timer = setTimeout(function () {
      timer = null;
      var url = imageUrlAt(startX, startY);
      // Reported once as "long-press does literally nothing": with no image found,
      // this used to do nothing at all, which looked identical to the hold timer never
      // having fired in the first place. A visible "not found" now rules that out —
      // if this still never appears, the timer/touch handling itself isn't the issue.
      if (url) {
        sendForSave(url);
      } else if (window.NativeMedia) {
        window.NativeMedia.onImageResolveFailed('Δεν βρέθηκε εικόνα σε αυτό το σημείο');
      }
    }, LONG_PRESS_MS);
  }, { capture: true, passive: true });

  document.addEventListener('touchmove', function (e) {
    if (!timer || e.touches.length !== 1) return;
    var touch = e.touches[0];
    if (Math.abs(touch.clientX - startX) > MOVE_TOLERANCE || Math.abs(touch.clientY - startY) > MOVE_TOLERANCE) {
      cancel();
    }
  }, { capture: true, passive: true });

  document.addEventListener('touchend', cancel, { capture: true, passive: true });
  document.addEventListener('touchcancel', cancel, { capture: true, passive: true });
})();
