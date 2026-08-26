# FB Feed Wrapper

Android app (Kotlin, Jetpack Compose) that wraps m.facebook.com in a WebView with a
persistent login session. This is a **personal-use, sideload-only** project — it is
not intended for the Play Store. See [PROJECT_CONTENT.md](PROJECT_CONTENT.md) for the
full feature roadmap (feed filtering, scroll-position persistence, media downloads,
group auto-sync).

## Build

```
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Status

This is the initial scaffold: a single Activity loading `m.facebook.com` in a WebView
with persistent cookies. Feed filtering, scroll-position persistence, and media
download handling are not implemented yet.
