# BarfChalo for Android ❄

Android app for **[BarfChalo](https://sahilfarooqui.github.io/barfchalo/)**, the free snow road-trip planner for India.

The app opens the live website, so snow forecasts, routes, fuel prices and cars are always up to date. It doesn't need an app update when the site changes.

**Download:** [latest APK](https://github.com/Sahilfarooqui/barfchalo-android/releases/latest). Open it on your phone, allow "install unknown apps" if asked, and install it. Needs Android 7.0 or newer.

## What the app adds
- **🎯 My location:** uses your phone's GPS. Android asks for permission the first time.
- **WhatsApp, Booking.com, Airbnb and Google Maps links:** open in those apps, or in your browser.
- **📤 Share:** uses the normal Android share sheet. **Copy link** copies to the clipboard.
- **No internet screen:** comes back on its own when you're online again.
- **Trip links:** links starting with `https://sahilfarooqui.github.io/barfchalo/...` can open in the app.
- **Snowy splash screen and launcher icon.**

## Build
Needs JDK 17 and the Android SDK (platform 34, build-tools 34).
```bash
./gradlew assembleRelease bundleRelease   # needs keys/keystore.properties (not in git)
```
Package id: `com.barfchalo.app`
