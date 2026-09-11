# Aether

<p align="center">
  <strong>A quieter way to Telegram.</strong><br>
  <sub>Foresight Labs' flagship personal messenger for Android.</sub>
</p>

<p align="center">
  Built around people, conversation, atmosphere, and restraint — with Telegram's official TDLib underneath.
</p>

<p align="center">
  <img src="docs/screenshots/aether-home.png" width="49%" alt="Aether Home">
  <img src="docs/screenshots/aether-conversation.png" width="49%" alt="Aether Conversation">
</p>
<p align="center">
  <img src="docs/screenshots/aether-attachments.png" width="49%" alt="Attachments in the Aether Curtain">
  <img src="docs/screenshots/aether-forwarding.png" width="49%" alt="Forwarding in the Aether Curtain">
</p>

> **Aether is the flagship product of Foresight Labs.**  
> It is an independent Android messenger built on Telegram's official TDLib, designed around a simpler idea: personal conversation should feel calm, immediate, and alive.

Currently distributed through closed testing.

---

## The idea

Telegram is an extraordinarily capable communications platform. It supports private messaging, groups, communities, channels, bots, forums, public content, discovery, and much more.

Aether does not try to reproduce every one of those surfaces.

It starts from a smaller question:

> **What if the conversation itself were the product?**

Aether is Foresight Labs' answer: a focused personal communication environment that uses Telegram for identity, transport, synchronization, and protocol — while building its own interaction model, hierarchy, motion, visual language, and product priorities above it.

This is not "Telegram with a different theme."

It is a distinct messenger built on top of Telegram.

---

## Product philosophy

### People first

Aether is designed around personal conversations rather than turning the inbox into a general-purpose information feed.

### Intentional scope

A capability existing in Telegram does not automatically mean it belongs in Aether's primary UI. Features are included when they strengthen direct communication, not merely to satisfy a comparison chart.

### One continuous conversation space

Contextual actions are designed to emerge from the same scene instead of feeling like unrelated panels stacked over each other.

Composing, attachments, forwarding, selection, reply/edit states, and other bottom interactions are expressed through Aether's persistent **Curtain** — one continuous rear surface behind the conversation foreground.

### Contextual permissions

Aether asks for access when a feature actually needs it, not simply because the app has launched.

### Calm by design

Graphite surfaces, lavender atmospheric light, mathematical geometry, restrained motion, and living glass are used to create depth without visual noise.

Aether's material rule is simple:

> **The glass has no color. The environment behind the glass gives it color.**

### Telegram underneath. Aether above.

TDLib owns Telegram connectivity. Aether owns the product experience.

---

## A living interface

Aether's interface is intended to feel less like a collection of screens and more like one connected environment.

The Home scene carries a dark atmospheric field with lavender light and equation-driven geometry. In Conversation, that same environment expands into the full scene rather than becoming a separate wallpaper.

The geometry is designed as **living mathematics**: smooth orbital and parametric forms, intersections, and signal paths derived from equations rather than arbitrary decorative lines. Signals can travel through those structures like neural impulses.

When connectivity disappears, the environment becomes dormant — the geometry remains, but its lavender energy drains into graphite-grey and its firing stops, like the lights of the network have gone out.

---

## Current experience

Aether is in active development. This section describes the product as it exists today, not a promise of future parity with Telegram.

### Personal messaging

- Direct conversations
- Replies and quotes
- Message editing
- Forwarding
- Selection and contextual message actions
- Search within conversations
- Global search
- Media and documents
- Voice notes and video notes
- Stickers, emoji, and GIFs
- Static location and venue sharing
- Contacts

### Aether surfaces

- Atmospheric Home and Conversation scenes
- Time-aware visual expression
- Optional weather-aware atmosphere using approximate location
- Message-aware living glass
- Persistent Conversation Curtain
- Per-chat appearance
- Pulse, Aether's presentation of Telegram stories
- Local App Lock with passcode and supported Android biometrics

### Telegram scope

Your Telegram groups, channels, and forum topics remain reachable. Aether does not erase the broader Telegram account underneath it.

They are simply not the product's primary surface.

Public discovery, bot-centric experiences, broadcast-first interaction, and other broad platform layers are deliberately secondary to Aether's personal-messenger focus.

---

## Features intentionally held

Some capabilities are present only as architecture or behind disabled feature flags while they are being completed and validated.

That currently includes areas such as:

- real Telegram voice/video calling media transport
- continuous live-location sharing
- device contact-book syncing

Aether does not expose an unfinished capability as though it were complete.

For example, voice-call signalling and call architecture may exist internally, but calling is not considered complete until real bidirectional media transport is implemented and physically validated.

---

## App Lock

Aether includes a local App Lock designed to protect the application UI without changing or replacing Telegram authentication.

It supports a local passcode and, where supported and enrolled on the device, Android biometric authentication.

The lock is an Aether privacy boundary:

- it does not log the Telegram account out
- it does not create another Telegram session
- it does not replace Telegram's own security model
- protected app content is intended to remain hidden while Aether is locked

---

## Privacy-conscious atmosphere

Weather adaptation uses approximate location only when needed.

Aether does not continuously track precise location for this feature. It requests coarse location and uses an approximate fix to look up current conditions through Open-Meteo.

Aether does **not** claim that location never leaves the device: approximate coordinates are sent to the weather service when weather adaptation is used.

If location access is denied, no fix is available, or the service cannot be reached, Aether falls back to time-based atmospheric styling.

---

## Security and privacy

Aether connects to Telegram through TDLib and does not alter Telegram's encryption model.

Whatever protection a chat has is provided by Telegram. Aether does not claim a separate encryption layer on top of it.

Foresight Labs does not operate Telegram infrastructure and does not run servers for Aether's Telegram traffic. Account data, messages, and media remain part of Telegram's system just as they do for another Telegram client.

Permissions are generally requested contextually:

- camera when camera functionality is actually used
- microphone for recording and other real audio functionality
- coarse location for location/weather features
- notification permission after sign-in where required by Android

Features held behind disabled flags should not request permissions simply because their internal architecture exists.

---

## Architecture

Aether is built primarily with:

- **Kotlin**
- **Jetpack Compose**
- **Telegram TDLib** through official Java/JNI bindings
- Aether's own design system and interaction architecture

The Telegram runtime, local database, synchronization, message history, file state, and protocol communication are provided through TDLib.

Aether's application layer builds its own navigation, state, interaction, atmospheric rendering, glass, Curtain architecture, privacy surfaces, and product behavior on top.

Details of the vendored TDLib artifacts are documented in [TDLIB.md](TDLIB.md).

---

## Design language

Aether is dark by design.

Its visual foundation is built around near-black rooms, graphite surfaces, mist typography, and restrained lavender light.

Core palette:

- `#090A0D` — base
- `#111318` — graphite
- `#181A21` — raised graphite
- `#747291` — lavender
- `#A5A3B7` — mist

The visual system favors spatial continuity over conventional page transitions. Conversation is treated as a scene, not simply another destination stacked on top of Home.

---

## Build

Requirements:

- Android Studio
- JDK 11 or newer
- Android SDK 36
- a Telegram API application from [my.telegram.org](https://my.telegram.org)
- the vendored TDLib Java/JNI artifacts described in [TDLIB.md](TDLIB.md)

Place local Telegram API credentials in the untracked `local.properties` file.

Never commit credentials or signing material.

Background push notifications additionally require a Firebase Android configuration for this `applicationId` (`app/google-services.json`, untracked) and the corresponding Firebase credentials configured for the Telegram application at my.telegram.org.

Telegram is the push sender and rejects device registration when that configuration is incomplete.

Without Firebase configuration, Aether can still build and run; notification delivery then depends on the active Telegram connection.

See [docs/architecture/push-notifications.md](docs/architecture/push-notifications.md).

### Common development checks

```shell
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug
```

For visual inspection of Home:

```shell
./gradlew :app:testDebugUnitTest --tests '*HomeScreenshotTest*'
```

Generated screenshots are written to:

```text
app/build/reports/aether-screenshots/
```

Release builds intentionally package **arm64-v8a only**. See [TDLIB.md](TDLIB.md).

Release signing is configured outside the repository.

---

## Independence

Aether is an independent, unofficial Telegram client.

It is not affiliated with, sponsored by, or endorsed by Telegram.

Telegram provides the platform and protocol Aether connects to. Foresight Labs neither owns nor represents Telegram technology, and Telegram has no ownership of Aether.

---

## Identity

- **Product:** Aether
- **Studio:** Foresight Labs
- **Creator:** Aritra Saha
- **Application ID:** `com.foresightlabs.aether`
- **Platform:** Android
- **Status:** Closed testing / active development

Aether is the flagship product of Foresight Labs and the clearest expression of the studio's approach to software: focused products, deliberate interaction, strong visual identity, and technology that stays out of the way of the person using it.

---

## Licensing

- TDLib — Boost Software License 1.0
- Manrope — SIL Open Font License 1.1
- Space Grotesk — SIL Open Font License 1.1
- Haze — Apache License 2.0

See bundled license texts under `app/src/main/assets/licenses/` and TDLib details in [TDLIB.md](TDLIB.md).

---

<p align="center">
  <strong>Foresight Labs</strong><br>
  <sub>Building software with a point of view.</sub>
</p>

© 2026 Aritra Saha / Foresight Labs. All rights reserved.
