# Aether

Aether is an independent third-party Telegram client for Android, built with Kotlin, Jetpack Compose, and the official Telegram Database Library (TDLib). Its dynamic atmospheric interface adapts to the time of day and, when the user permits approximate location access, local weather conditions from Open-Meteo.

Aether is currently distributed through closed testing. It is not the official Telegram application and is not affiliated with or endorsed by Telegram.

## Privacy-conscious weather adaptation

Weather adaptation is optional and on demand. Aether requests approximate location only, does not request precise location, does not continuously track location, and does not store location history. If location or weather data is unavailable, the interface gracefully uses time-only atmospheric styling.

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

Release signing is intentionally configured outside the repository.

## Identity and licensing

- Application name: Aether
- Application ID: `com.foresightlabs.aether`
- TDLib: Boost Software License 1.0
- Manrope and Space Grotesk fonts: SIL Open Font License 1.1

See the license texts bundled under `app/src/main/assets/licenses/` and the TDLib details in [TDLIB.md](TDLIB.md).

© 2026 Aritra Saha / Foresight Labs. All rights reserved.
