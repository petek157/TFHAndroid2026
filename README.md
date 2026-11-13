# TFHAndroid2026

The Top Flight Health Android app: the client side of the iOS app
([MacroCalculator](https://github.com/petek157/MacroCalculator)), talking to the same API and
sign-in server ([TFHReact2024](https://github.com/petek157/TFHReact2024)).

Kotlin + Jetpack Compose, Android 8.0 (API 26) and up. App id `co.topflighthealth.app`.

## Build and run

Open this folder in Android Studio and press Run, choosing the `devDebug` variant. From a terminal:

```bash
./gradlew assembleDevDebug        # build the dev app
./gradlew installDevDebug         # install it on the running emulator or plugged-in phone
```

- `dev` flavor: talks to `devapi.topflighthealth.co` and the dev auth server. App name "TFH Dev".
- `prod` flavor: talks to `api.topflighthealth.co` and `auth.topflighthealth.co`.

## Branches

- `dev` is the default branch. Each piece of work gets its own branch off `dev` and is merged by
  Peter.
- `prod` will be the release branch once the app ships.

## Plan

v1 is the client flow, built in phases: sign-in and app shell, profile and dashboard, food
journal, pantry and macros, progress photos, then messaging and push. The coach portal and
Health Connect (glucose) are v2. See [AGENTS.md](AGENTS.md) for how to work in this repo.
