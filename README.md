<h1 align="center">Aether</h1>

<p align="center">
  <strong>A quieter way to Telegram.</strong>
</p>

<p align="center">
  A personal messenger for Android, built by <strong>Foresight Labs</strong> on Telegram's official TDLib.
</p>

<p align="center">
  <sub>Flagship product · Closed testing · Android</sub>
</p>

<br>

<p align="center">
  <img src="docs/screenshots/aether-home.png" width="47%" alt="Aether Home">
  &nbsp;
  <img src="docs/screenshots/aether-conversation.png" width="47%" alt="Aether Conversation">
</p>

<br>

## Conversation, without the noise

Aether is an independent Telegram client built around one idea: **personal conversation should be the product**.

Telegram provides the account, network, synchronization, and protocol through TDLib. Aether builds its own experience above it — its own interaction model, motion, hierarchy, atmosphere, and visual language.

It is deliberately not a reproduction of every Telegram surface.

Aether is designed around people first.

<br>

## The Aether experience

<table>
<tr>
<td width="50%" valign="top">

### One continuous space

Conversation is treated as a scene rather than a stack of unrelated screens.

Attachments, forwarding, selection, reply/edit states, and other contextual actions emerge from one persistent rear surface: the **Curtain**.

</td>
<td width="50%" valign="top">

### Living atmosphere

Graphite, lavender light, living glass, and equation-driven geometry form a quiet environment around conversation.

When connectivity disappears, that environment becomes dormant: the geometry remains, but its light and neural motion fade away.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### Glass that belongs to the scene

Aether's glass has no artificial tint of its own.

> **The glass has no color. The environment behind the glass gives it color.**

</td>
<td width="50%" valign="top">

### Privacy where it matters

Permissions are requested contextually. Aether also provides a local App Lock with passcode and supported Android biometrics without replacing Telegram authentication.

</td>
</tr>
</table>

<br>

<p align="center">
  <img src="docs/screenshots/aether-attachments.png" width="47%" alt="Attachments in the Curtain">
  &nbsp;
  <img src="docs/screenshots/aether-forwarding.png" width="47%" alt="Forwarding in the Curtain">
</p>

<br>

## Built for personal messaging

Aether currently focuses on direct communication: replies, quotes, editing, forwarding, selection, search, photos and video, documents, voice and video notes, stickers, emoji, GIFs, contacts, static location and venue sharing, and Telegram stories through **Pulse**.

Groups, channels, and forum topics remain reachable because they are part of the Telegram account underneath Aether, but they are not the center of the product.

Some capabilities remain intentionally held while they are completed and validated, including real voice/video calling media transport, continuous live location, and device contact-book syncing.

Aether does not present unfinished functionality as finished functionality.

<br>

## Design language

Aether is dark by design.

`#090A0D` base · `#111318` graphite · `#181A21` raised graphite · `#747291` lavender · `#A5A3B7` mist

The interface favors spatial continuity over conventional page transitions, rounded mathematical forms over decorative noise, and restrained motion over constant animation.

<br>

## Underneath

Aether is built with **Kotlin**, **Jetpack Compose**, and Telegram's official **TDLib Java/JNI bindings**.

TDLib owns Telegram connectivity, local database state, synchronization, files, and protocol communication. Aether owns the application experience above it.

Release builds currently target **arm64-v8a** only.

See [TDLIB.md](TDLIB.md) for the pinned TDLib artifacts and [push-notifications.md](docs/architecture/push-notifications.md) for notification architecture.

<details>
<summary><strong>Build Aether</strong></summary>

<br>

Requirements:

- Android Studio
- JDK 11 or newer
- Android SDK 36
- Telegram API credentials from [my.telegram.org](https://my.telegram.org)
- the vendored TDLib artifacts described in [TDLIB.md](TDLIB.md)

Place local Telegram API values in the untracked `local.properties` file. Never commit credentials or signing material.

For FCM-backed background notifications, provide the untracked `app/google-services.json` and configure the corresponding Firebase credentials for the Telegram application.

```shell
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug
```

Release signing is configured outside the repository.

</details>

<br>

## Independence

Aether is an independent, unofficial Telegram client. It is not affiliated with, sponsored by, or endorsed by Telegram.

Telegram provides the platform Aether connects to. Foresight Labs does not operate Telegram infrastructure and does not alter Telegram's encryption model.

<br>

## Identity

**Aether** · Foresight Labs · Created by Aritra Saha  
`com.foresightlabs.aether` · Android · Closed testing

<p align="center">
  <br>
  <strong>Foresight Labs</strong><br>
  <sub>Focused software. Deliberate interaction.</sub>
</p>

<p align="center">
  <sub>© 2026 Aritra Saha / Foresight Labs. All rights reserved.</sub>
</p>
