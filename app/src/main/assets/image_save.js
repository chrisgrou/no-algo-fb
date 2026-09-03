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

  function imageAt(x, y) {
    var stack = document.elementsFromPoint ? document.elementsFromPoint(x, y) : [document.elementFromPoint(x, y)];
    for (var i = 0; i < stack.length; i++) {
      if (stack[i] && stack[i].tagName === 'IMG' && stack[i].src) return stack[i];
    }
    return null;
  }

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
      var img = imageAt(startX, startY);
      if (img) window.NativeMedia && window.NativeMedia.onImageLongPress(img.src);
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
