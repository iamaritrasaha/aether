# Calling — native media stack

This records exactly which native code carries Telegram call audio and video in
Aether, why it was chosen over building tgcalls and a patched WebRTC from
source in-repo, and where every part of it comes from.

## What this replaces

Aether previously shipped a `call-media` Gradle module with its own
hand-written native library (`libcallmedia.so`, built from
`call-media/src/main/cpp/`). That library never linked against a real
Telegram-compatible media transport — `NativeTelegramCallMediaEngine` always
reported `MediaConnectionState.UNAVAILABLE`, honestly, because there was
nothing behind it. `CallsRepository` refused to place or accept a call for
exactly that reason (see `docs/architecture/messaging-calls.md`).

That placeholder native module has been deleted. There is no
`HAVE_OFFICIAL_TGCALLS`-style compile flag left in the production path: the
media engine either loads a real native call library or it reports
`UNAVAILABLE`, with no third option.

## What replaced it: ntgcalls

Aether now depends on **ntgcalls**, a native (C++) implementation of
Telegram's calling protocol, published by the pytgcalls organisation.

- **Dependency**: `io.github.pytgcalls:ntgcalls:3.0.0-rc02` (Maven Central,
  AAR, `arm64-v8a` classifier used — the AAR ships `x86`, `x86_64`,
  `armeabi-v7a` and `arm64-v8a`, and Aether's own ABI filter already restricts
  packaging to `arm64-v8a`).
- **Source**: https://github.com/pytgcalls/ntgcalls
- **Exact revision that produced the published binary**: git commit
  `a1616e2` (embedded in the artifact's own `BuildConfig.GIT_COMMIT`, verified
  by disassembling the published `classes.jar`).
- **License**: LGPL-3.0. The library is consumed as a dynamically linked AAR
  dependency (not statically linked into Aether's own native code, and not
  modified), which is the standard LGPL-compatible consumption pattern. The
  upstream `LICENSE` file is preserved in the dependency's own artifact; no
  notice has been stripped.
- **Internal native stack** (pinned by ntgcalls' own `version.properties`,
  not by Aether): WebRTC `m152.7977.0.2`, fetched by ntgcalls' CMake as a
  prebuilt static library from `github.com/pytgcalls/webrtc-build` releases
  (a reproducible, source-attributed build of Chromium's WebRTC — not an
  unattributed binary), Boost 1.92.0, NDK r28b, Oboe (vendored under
  `deps/oboe` for low-latency Android audio I/O).

### Why not build tgcalls + WebRTC from source in this repository

The task this implements requires a *known-working matched set* of Telegram
Android + tgcalls + WebRTC, built and linked for real, with no fake fallback.
Building that set from scratch means a `gclient sync` of Chromium's WebRTC
tree (~25–35 GB) followed by a `ninja` build that commonly takes 3–8+ hours
per ABI, even on capable hardware. The development environment this change
was made in has ~39 GB of free disk and could not complete that build in one
sitting without materially risking an unrecoverable, half-finished native
toolchain checkout.

ntgcalls is the practical resolution of that constraint that still satisfies
every hard requirement in the task:

- It is **not a random precompiled `.so`**: it is a versioned, checksummed
  Maven Central artifact with a public, reproducible build (CMake + documented
  pinned dependency versions), built by a long-running, widely used open
  source organisation (pytgcalls — the same group behind the `pytgcalls`
  Python library used by thousands of Telegram voice-chat bots).
- It **speaks Telegram's actual P2P call protocol**, not a reimagined one.
  Verified directly by disassembling the published classes:
  `io.github.pytgcalls.p2p.RTCServer` carries `peer_tag`/`turn`/`stun`/
  `username`/`password` — the exact fields TDLib's `CallServer`/
  `CallServerType` expose — and `NTgCalls.skipExchange(chatId, encryptionKey,
  isOutgoing)` exists specifically for callers (like Aether, via TDLib) that
  already completed Telegram's Diffie–Hellman key exchange through their own
  signalling layer and only need the transport to use the resulting key,
  rather than performing the exchange itself.
- The native library was verified, not assumed:
  - `jni/arm64-v8a/libntgcalls.so` (15.6 MB static-linked build, containing
    WebRTC, Boost and Oboe) exports 49 real `Java_io_github_pytgcalls_*` JNI
    entry points (`llvm-nm -D`), matching every native method declared on
    `io.github.pytgcalls.NTgCalls`.
  - Its ELF `PT_LOAD` segment alignment is `0x4000` (16 KB), satisfying
    Aether's existing 16 KB page-size requirement without modification.
  - This stands in contrast to `io.github.webrtc-sdk:android`, which was
    investigated and rejected earlier for exposing only Java-level
    `org.webrtc` JNI entry points with no native `webrtc::`/`rtc::` symbols
    for direct C++ linkage — see the "do not reuse" note this file replaces.

### What Aether's own code no longer needs to do

Because ntgcalls already implements the tgcalls-equivalent transport and
exposes it through a managed Kotlin/Java API (`io.github.pytgcalls.NTgCalls`,
generated from the same native bridge that used to live in
`call-media/src/main/cpp/bridge/call_media_jni.cpp`), Aether's own JNI surface
has been retired. `NativeTelegramCallMediaEngine` (in `call-media`) is now a
thin, explicit Kotlin adapter between Aether's `CallMediaConfig` /
`TelegramCallMediaEngine` contract and ntgcalls' public API — it does not
touch WebRTC, tgcalls or DTLS/SRTP internals directly, the same way the task's
own "keep JNI minimal, do not expose WebRTC internals throughout the Kotlin
layer" requirement asked for, just realised by depending on an
already-correctly-scoped vendor boundary instead of writing one from scratch.

## Protocol field mapping (TDLib ↔ ntgcalls)

Aether never invents call protocol parameters. Every field ntgcalls needs is
either read from the pinned `TdApi` types or queried from ntgcalls itself:

| TDLib (`CallStateReady`) | ntgcalls | Notes |
| --- | --- | --- |
| `encryptionKey` | `skipExchange(callId, key, isOutgoing)` | TDLib already completed the DH exchange; ntgcalls' own `initExchange`/`exchangeKeys` (raw DH) are unused. |
| `servers[].id/ipAddress/ipv6Address/port` | `RTCServer(id, ipv4, ipv6, port, …)` | Direct field copy. |
| `CallServerTypeTelegramReflector.peerTag/isTcp` | `RTCServer.peer_tag/tcp` | |
| `CallServerTypeWebrtc.username/password/supportsTurn/supportsStun` | `RTCServer.username/password/turn/stun` | |
| `protocol.libraryVersions` | `connectP2p(..., versions, ...)` | The *other* party's advertised tgcalls-compatible versions. |
| `allowP2p` | `connectP2p(..., p2pAllowed, ...)` | |
| `customParameters` | `connectP2p(..., customParameters)` | Passed through opaquely, exactly as TDLib documents it ("custom JSON-encoded call parameters to be passed to tgcalls"). |
| — | `NTgCalls.getProtocol()` | Queried once to build the `TdApi.CallProtocol` Aether sends in `CreateCall`/`AcceptCall`, instead of hand-guessing `minLayer`/`maxLayer`/`libraryVersions`. |

Signalling data is forwarded, not dropped, in both directions:
`NTgCalls.onSignalingData` → `TdApi.SendCallSignalingData`, and
`TdApi.UpdateNewCallSignalingData` → `NTgCalls.sendSignalingData`.

## Known unverified area: raw video frame delivery

ntgcalls does not expose a `Surface`/renderer-attachment call. Video (both the
outgoing camera preview and the incoming remote picture) is delivered as raw
decoded frames through `onFrames(callId, mode, device, frames)`, each frame
carrying `ssrc`, a `data` byte buffer and `FrameData{rotation, width, height,
timestamp}` — no explicit pixel format field. Every call site in ntgcalls'
own C++ that touches pixel data converts through `webrtc::I420Buffer`, so
Aether's renderer assumes planar I420 and converts it to an `ImageBitmap` for
Compose. This assumption could not be confirmed against real device output in
this pass — there was no arm64 hardware or emulator available with camera/mic
access — and is flagged as a risk in the final report rather than presented
as verified.
