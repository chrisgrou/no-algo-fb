// Image saving: two independent triggers feeding one save path.
//
// 1. Facebook's own "..." menu Save button — handled below by intercepting the click
//    on the hidden download anchor Facebook's JS creates for it (see the comment where
//    that listener is installed for how an on-device DOM capture found it and why
//    intercepting the click, not the resulting download, is what actually works).
// 2. A long-press on an image, for when there's no such button to begin with. Not
//    relying on Android WebView's own native long-press detection: an on-device test
//    found it never fired at all on a Facebook photo, most likely because Facebook's
//    own photo viewer calls preventDefault() on touchstart for its pinch/zoom/swipe
//    gestures — which stops the platform's gesture detector from ever recognizing the
//    touch as a long press to begin with, regardless of what a native
//    View.OnLongClickListener is set up to do. This detects the hold itself in JS
//    instead, on capture-phase passive listeners so it sees every touch before the
//    page's own handlers can consume it, and works the same either way.
(function () {
  if (window.__ffwImageSaveInstalled) return;
  window.__ffwImageSaveInstalled = true;

  // Reported twice now as producing literally no feedback on either the long-press or
  // the "..." menu's Save button, even after both were given guaranteed-to-fire native
  // Toasts for every path including failure. That points at something upstream of any
  // of that code ever running — this script not installing, the touch never reaching
  // it, or setDownloadListener never firing at all — none of which a Toast from deep
  // inside this file could ever reveal. Logging into the same shared timeline
  // resume_guard.js already exposes through the debug dump (window.__ffwLog) means a
  // capture taken right after reproducing it shows what actually happened, or shows
  // this file never got this far at all, instead of guessing again.
  function log(msg) {
    if (window.__ffwLog) window.__ffwLog('image_save: ' + msg);
  }
  log('installed, NativeMedia=' + !!window.NativeMedia);

  // Every attempt at fetch()-ing a blob: URL back — even one intercepted at the exact
  // moment of the click, before the browser ever treated it as a download — failed
  // instantly with "Failed to fetch" on-device. That rules out a revocation race (there
  // was no time for one) and points at fetch() simply not being able to reach a blob:
  // URL at all in this WebView, regardless of timing. The actual fix: capture the real
  // JS Blob object Facebook's own code hands to URL.createObjectURL() at the moment it
  // does, keyed by the URL string it returns — then resolving a blob: URL later is a
  // plain map lookup and a FileReader read of an object already in hand, no network-ish
  // fetch involved at all. Patched as early as this script itself installs, long before
  // any user action could trigger Facebook's own Save flow, so nothing created after
  // this point is missed.
  var blobRegistry = {};
  // The most recently created image blob — a fallback guess for "the photo currently
  // being viewed" when the long-press point-based DOM search below comes up empty. The
  // download-anchor blob this registry was built for proved the fix works at all (the
  // "..." Save button now reads it straight from here); if the full-screen viewer's own
  // <img> is *also* blob-backed, as seems likely given how blob-heavy this flow already
  // is, this catches it without needing to find that element in the DOM at all — sidestepping
  // whatever's stopping elementsFromPoint from seeing it (still unexplained: every
  // long-press trace so far found only shallow, unrelated elements at the touch point,
  // consistently, regardless of where on screen it was).
  var lastImageBlobUrl = null;
  var nativeCreateObjectURL = URL.createObjectURL;
  URL.createObjectURL = function (obj) {
    var url = nativeCreateObjectURL.call(URL, obj);
    try {
      blobRegistry[url] = obj;
      if (obj && typeof obj.type === 'string' && obj.type.indexOf('image/') === 0) {
        lastImageBlobUrl = url;
        log('createObjectURL image blob, size=' + obj.size + ' type=' + obj.type + ' url=' + url.substring(0, 60));
      }
    } catch (e) {}
    return url;
  };

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

  // Logs what was actually AT the touch point when neither check above matched —
  // an on-device trace found a real case of this (a full-screen photo view, reported
  // as long-press doing nothing there) where the debug dump's own separate HTML
  // capture couldn't help explain it: by the time that capture ran, the screen had
  // already changed back to something else, showing a different DOM entirely.
  // Logging the stack right here, in the moment the touch itself happened, doesn't
  // have that problem — it's already in the shared timeline before anything else can
  // change.
  function describeStack(stack) {
    var parts = [];
    for (var i = 0; i < Math.min(stack.length, 6); i++) {
      var el = stack[i];
      if (!el) continue;
      var desc = el.tagName;
      if (el.id) desc += '#' + el.id;
      if (el.className && typeof el.className === 'string') desc += '.' + el.className.split(' ').join('.');
      parts.push(desc);
    }
    return parts.join(' > ');
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
    // Broad fallback: some element on screen right now is showing a blob: image (the
    // "..." menu's own Save button proved one exists whenever a photo is open — see
    // MainActivity's setDownloadListener log) even if it isn't exactly under this
    // touch point, e.g. because of how a pinch/zoom transform or an invisible gesture
    // layer positions things. Picking the largest on-screen blob <img> is a reasonable
    // guess at "the photo being viewed" when the precise point-based search comes up
    // empty.
    var blobImgs = document.querySelectorAll('img[src^="blob:"]');
    var best = null;
    var bestArea = 0;
    for (var j = 0; j < blobImgs.length; j++) {
      var rect = blobImgs[j].getBoundingClientRect();
      var area = rect.width * rect.height;
      if (area > bestArea) {
        bestArea = area;
        best = blobImgs[j];
      }
    }
    if (best) return best.src;

    // Last resort: the most recent image blob created at all (see lastImageBlobUrl's
    // own comment), regardless of whether any element on screen currently references
    // it — only reached once both the point-based and on-screen-<img> searches above
    // have already come up empty.
    if (lastImageBlobUrl) {
      log('nothing matched at point or on screen; falling back to last image blob ' + lastImageBlobUrl.substring(0, 60));
      return lastImageBlobUrl;
    }

    log('nothing matched at point; stack=[' + describeStack(stack) + '] blobImgCount=' + blobImgs.length);
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
    log('sendForSave ' + url.substring(0, 60));
    if (!window.NativeMedia) {
      log('NativeMedia missing, cannot send');
      return;
    }
    if (url.indexOf('blob:') === 0 || url.indexOf('data:') === 0) {
      resolveAndSend(url);
    } else {
      window.NativeMedia.onImageUrl(url);
    }
  }

  function readBlobAndSend(blob) {
    log('reading blob, size=' + blob.size + ' type=' + blob.type);
    var reader = new FileReader();
    reader.onloadend = function () {
      log('resolveAndSend sending data url, length=' + (reader.result ? String(reader.result).length : 0));
      // reader.result is itself a data: URL ("data:image/jpeg;base64,...."), already
      // exactly what MediaDownloader.saveImageDataUrl expects.
      window.NativeMedia && window.NativeMedia.onImageDataUrl(String(reader.result));
    };
    reader.onerror = function () {
      log('resolveAndSend FileReader error');
      window.NativeMedia && window.NativeMedia.onImageResolveFailed('FileReader error');
    };
    reader.readAsDataURL(blob);
  }

  // The registry (see its own comment above) is checked first: every on-device trace
  // of fetch()-ing a blob: URL back failed instantly, timing-race or not, so that's now
  // only a fallback for the (should be rare) case of a blob: URL this page created
  // before this script got a chance to patch URL.createObjectURL. A first version had
  // no failure reporting at all here — fetch() rejecting just silently did nothing,
  // indistinguishable from this function never having run in the first place. Now it
  // always tells the native side one way or the other.
  function resolveAndSend(url) {
    log('resolveAndSend ' + url.substring(0, 60) + ' inRegistry=' + (url in blobRegistry));
    var cached = blobRegistry[url];
    if (cached) {
      readBlobAndSend(cached);
      return;
    }
    fetch(url)
      .then(function (res) { return res.blob(); })
      .then(readBlobAndSend)
      .catch(function (err) {
        var reason = String((err && err.message) || err);
        log('resolveAndSend fetch failed: ' + reason);
        window.NativeMedia && window.NativeMedia.onImageResolveFailed(reason);
      });
  }

  // Exposed for MainActivity's setDownloadListener to call into as a last-resort catch
  // — see the click interceptor below for why it's no longer the primary path.
  window.__ffwResolveImageForSave = resolveAndSend;

  // The actual fix for the "..." menu's Save button: an on-device capture of the live
  // DOM, taken while its own menu was still open, found exactly how Facebook triggers
  // it — a hidden anchor it creates on demand: <a style="display:none" download="…"
  // href="blob:…"></a>, presumably clicked programmatically (anchor.click()) to start
  // the browser's native download. By the time WebView's onDownloadStart notices that
  // and this page's own JS gets asked (via __ffwResolveImageForSave above) to fetch
  // the same blob: URL again, it's already unusable — every on-device trace failed in
  // 2-3ms, far too fast to be a real timing race, pointing at WebView releasing
  // whatever let a fresh fetch() resolve it as soon as it hands the URL off to its own
  // download pipeline. A synthetic click still dispatches as a real, trusted click
  // event through the normal DOM flow, so catching it here — before the browser ever
  // treats it as a download at all — reaches the blob while it's still definitely
  // good, and preventDefault() stops the native download from starting a second,
  // doomed attempt via setDownloadListener.
  document.addEventListener('click', function (e) {
    var target = e.target;
    if (!target || target.tagName !== 'A' || !target.hasAttribute('download')) return;
    var href = target.getAttribute('href') || '';
    if (href.indexOf('blob:') !== 0 && href.indexOf('data:') !== 0) return;
    log('intercepted download anchor click: ' + href.substring(0, 60));
    e.preventDefault();
    e.stopPropagation();
    resolveAndSend(href);
  }, true);

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
      log('long-press fired at ' + Math.round(startX) + ',' + Math.round(startY) + ' -> ' + (url ? url.substring(0, 60) : 'nothing found'));
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
