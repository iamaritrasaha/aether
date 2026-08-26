# Aether

Aether is a living, people-first interface for your conversations. It is an independent third-party Telegram client for Android, built with Kotlin, Jetpack Compose, and the official Telegram Database Library (TDLib). Its atmospheric interface adapts to the time of day and, when the user permits approximate location access, to local weather conditions from Open-Meteo.

Aether is currently distributed through closed testing. It is not the official Telegram application and is not affiliated with or endorsed by Telegram.

## Privacy-conscious weather adaptation

Weather uses your approximate location only when needed. Aether does not continuously track or store your location. It requests coarse location only and never precise location, and it reads a last-known approximate fix rather than starting continuous updates.

Approximate coordinates are sent to Open-Meteo to look up current conditions; Aether does not claim that location never leaves the device. If location access is denied, no fix is available, or the weather service cannot be reached, the interface says so and falls back to time-only atmospheric styling.

## Build requirements

- Android Studio with JDK 11 or newer
- Android SDK 36
- A Telegram API application registered at [my.telegram.org](https://my.telegram.org)
- The vendored TDLib Java/JNI artifacts described in [TDLIB.md](TDLIB.md)

Place local Telegram API values in the untracked `local.properties` file. Never commit credentials or signing material.

Common development checks:

```shell
./gradlew test
./gradlew assembleDebug
```

Rendering Home for visual inspection (writes PNGs to `app/build/reports/aether-screenshots/`):

```shell
./gradlew :app:testDebugUnitTest --tests '*HomeScreenshotTest*'
```

Release builds package `arm64-v8a` only; see [TDLIB.md](TDLIB.md).

Release signing is intentionally configured outside the repository.

## Identity and licensing

- Application name: Aether
- Application ID: `com.foresightlabs.aether`
- TDLib: Boost Software License 1.0
- Manrope and Space Grotesk fonts: SIL Open Font License 1.1

See the license texts bundled under `app/src/main/assets/licenses/` and the TDLib details in [TDLIB.md](TDLIB.md).

The floating-bar backdrop blur uses Haze 1.2.2, licensed under Apache License 2.0.

© 2026 Aritra Saha / Foresight Labs. All rights reserved.
