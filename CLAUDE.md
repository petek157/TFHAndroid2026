# TFHAndroid2026: notes for agents (Claude, Cursor)

The Android version of the Top Flight Health client app. Peter is new to Android, so explain
Android-specific steps in plain words when you hand work back.

## Sibling repos (same parent folder, separate git repos)

- `../MacroCalculator`: the iOS app (SwiftUI). **The reference for behavior.** When building a
  screen, read the matching iOS view and view model first and match what it does and the API
  calls it makes.
- `../TFHReact2024`: the web app and the API/auth backend (SST v3). Its `dev` branch deploys the
  dev stage (`devapi.topflighthealth.co`).
- Don't change the sibling repos as part of Android work unless the task says so. Backend changes
  go on their own branch in TFHReact2024.

## Rules

- Branch off `dev` for every piece of work; Peter merges. Never push to `dev` or `prod` directly.
- The app id `co.topflighthealth.app` is permanent once on Google Play. Never change it.
- Sign-in uses AppAuth with client id `android-client` and redirect
  `co.topflighthealth.app:/callback`. Both must match
  `TFHReact2024/packages/functions/src/auth/constants.ts`, which only accepts exact matches.
- The server is the source of truth for membership (`GET users/subscription-status`), as on iOS.
- **No in-app purchases.** The app sells nothing; non-members are told to join on the website.
  Check Google Play's current payment rules before adding any link or button to an outside
  checkout.
- Tokens live only in `TokenStore` (encrypted with an Android Keystore key) and are excluded from
  backups. Never log tokens.
- Never commit signing keys, `keystore.properties`, `google-services.json` or `local.properties`.

## Layout

```
app/src/main/java/co/topflighthealth/app/
  TfhApplication.kt   builds AppContainer (auth + api), the app's long-lived objects
  MainActivity.kt
  auth/               AppAuth sign-in, token refresh, TokenStore, JWT claims
  api/                Retrofit TfhApi (endpoints + models), OkHttp bearer token and 401 refresh
  ui/                 AppViewModel (session, user, membership), TfhApp (tabs), theme
  ui/screens/         one file per screen or group of screens
```

Patterns: Compose + Material 3, one ViewModel per screen exposing a `StateFlow`, suspend
functions on `TfhApi`. Brand colors are in `ui/theme/Theme.kt`, taken from the iOS asset catalog.
Library versions live in `gradle/libs.versions.toml`.

## Commands

```bash
./gradlew assembleDevDebug     # build; run this before every commit
./gradlew installDevDebug      # install on emulator or phone
./gradlew lintDevDebug         # Android lint
```

Java comes from Android Studio (`JAVA_HOME` points at its bundled JBR in `~/.zshrc`).

## Testing on this Mac

Peter's Mac is a 2019 Intel MacBook Pro with 6 cores. Use the `Pixel_API_35` emulator (Android 15),
started with software graphics; it boots in about 2 minutes:

```bash
$ANDROID_HOME/emulator/emulator -avd Pixel_API_35 -no-boot-anim -no-audio -gpu swiftshader_indirect
```

The API 37 emulator and `-gpu host` both stall at boot on this Mac. Quit the iOS Simulator first:
its `mediaanalysisd` process can take most of the CPU and stall the emulator. A real phone over
USB is faster still.

The dev build signs in against Peter's `sst dev` session of TFHReact2024, so that has to be
running.
