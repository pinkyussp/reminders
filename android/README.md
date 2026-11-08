[![Android build](https://github.com/pinkyussp/reminders/actions/workflows/android-build.yml/badge.svg)](https://github.com/pinkyussp/reminders/actions/workflows/android-build.yml)

# Dates for Android

Birthday and anniversary reminders. The same app as the web version, built as a native Android app.

## Install

1. Open the latest release on GitHub and download `Dates-1.0.N.apk`.
2. Open the file on your phone and allow installs from that source if asked.
3. Install. Later releases install over this one and keep your dates.

## Updates

Every push to `main` builds a new signed APK and publishes it as a release. Settings → About shows the installed version, and "What's new" appears once after each update.

## Build

The build runs on GitHub Actions (`.github/workflows/build.yml`). It needs four repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. The signing key must never be committed, and every update must use the same key.

To build on a computer with Android Studio, open the folder and run `gradle :app:assembleDebug`.

## License

MIT. See LICENSE.
