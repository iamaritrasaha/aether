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

- **Dependency**: Patched release `call-media/libs/ntgcalls-3.0.0-rc02-aetherfix2-arm64.aar`,
  carrying **five** independent local patches against the same pinned upstream
  commit (`call-media/third-party/ntgcalls/patches/`, all required, none
  supersedes another):
  1. `retain-jni-zero-entry-points.patch` — retains all 191 WebRTC `jni_zero`
     JNI entry points (fixes the `Java_J_N_MM6G5xGU` `UnsatisfiedLinkError`;
     see `docs/architecture/ntgcalls-jni-forensics.md`).
  2. `android-callback-classloader.patch` — resolves generated JNI callback
     classes (`ConnectionInfo` and 30 others) via WebRTC's class-loader-safe
     `GetClass` instead of a raw `FindClass`, fixing a `ClassNotFoundException`
     crash when a native-created callback thread tried to construct one (see
     ["The real media-connection-failure root cause"](#the-real-media-connection-failure-root-cause-physically-proven)
     below for the full proof chain and physical verification).
  3. `oboe-stream-restart-robustness.patch` — backported verbatim from upstream
     `v3.0.0-rc03`: fixes Oboe stream-error/teardown races (Android microphone
     and speaker device layer; every voice call runs on this code).
  4. `android-native-logcat-diagnostics.patch` — routes WebRTC/ntgcalls
     `RTC_LOG` to logcat (`AetherCallNative`; previously everything was
     swallowed on Android) and adds throttled outgoing/incoming RTP/RTCP
     packet counters (`AetherDiagnostics`). Diagnostics only; no behaviour
     change.
  5. `reconnect-connected-callback.patch` — re-emits `Connected` on recovery
     from a transient ICE drop (the `!was_connected` guard previously skipped
     the callback forever after the first connect, leaving a call screen
     stuck on reconnecting while media had actually recovered).
  - **Current AAR SHA-256** (`SHA256SUMS` is the source of truth for this):
    `b81d8552b129ec251159cb65ea7761f337b96ea8308cc6794940b572d3091523`
  - **Current `libntgcalls.so` SHA-256**: `ca3843d1794cdb1dc055c4070336dc764fb12ee4db401cf16e8ba8cb166d768f`
  - Upstream base: `io.github.pytgcalls:ntgcalls:3.0.0-rc02` at commit `a1616e280947452d86ac28d140fd1250d31e6959`.
  - Upstream patches, license and checksums are located in `call-media/third-party/ntgcalls/`
    (`README.md` there documents all five patches' rationale, exact source call
    chains, and full reproduction steps).
- **Source**: https://github.com/pytgcalls/ntgcalls
- **Exact revision that produced the base binary**: git commit
  `a1616e2` (embedded in the artifact's own `BuildConfig.GIT_COMMIT`, verified
  by disassembling the published `classes.jar`).
- **License**: LGPL-3.0. The library is consumed as a dynamically linked AAR
  dependency (not statically linked into Aether's own native code). The
  upstream `LICENSE` file is preserved in `call-media/third-party/ntgcalls/LICENSE`; no
  notice has been stripped.
- **Internal native stack**: WebRTC `m152.7977.0.2`, fetched by ntgcalls' CMake as a
  prebuilt static library from `github.com/pytgcalls/webrtc-build` releases
  (a reproducible, source-attributed build of Chromium's WebRTC — not an
  unattributed binary), Boost 1.92.0, NDK r28b, Oboe (vendored under
  `deps/oboe` for low-latency Android audio I/O).
- **ELF & Page Alignment**: All ELF segments in `libntgcalls.so` are built with
  `PT_LOAD` segment alignment `0x4000` (16 KB), satisfying Android 15's 16 KB page-size requirement.

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

## Stream sources initialization and failure safety

ntgcalls expects media capture sources (microphone and optionally camera) to be configured via `setStreamSources` before initiating P2P transport negotiation (`connectP2p`). Initiating P2P first creates a race condition where transport callbacks can arrive while the session has no audio source.

In `NativeTelegramCallMediaEngine`:
- Capture sources are resolved prior to calling `connectP2p`.
- If microphone resolution or capture registration fails, `startCall` halts immediately and emits `MediaConnectionState.FAILED` without invoking `connectP2p`.
- For video calls, if camera registration fails, the engine safely degrades to audio-only and retries `setStreamSources` with the microphone alone before proceeding to P2P negotiation.
- Frame callbacks and rendering are decoupled: malformed or unrenderable frames degrade gracefully to audio without terminating the underlying call.

## The indefinite "Connecting…" defect and its fix

A call could reach `CallStage.TDLIB_READY`, start its native media session, and
then sit on the call screen showing "Connecting…" forever — no crash, no
progress. Root-caused as two compounding defects in the *app-level*
orchestration layer (`DefaultCallsRepository` / `CallStatePresenter`), not in
ntgcalls, WebRTC, or the JNI boundary, all of which were independently audited
and found correct for this symptom (native artifact checksums and JNI symbol
retention intact; `AudioDescription.input` proven unread by the Android Oboe
capture path so it cannot gate connection; `configJson` vs `customParameters`
mapping matches both TDLib's own field documentation and ntgcalls'
`connectP2p` signature; protocol/server field mapping matches the documented
table above).

1. **`handleMediaStateChange`'s `FAILED` branch never told TDLib to discard
   the call.** When the native media engine reported `FAILED` (an ICE
   failure, a native `TIMEOUT`, or any other connect failure), Aether stopped
   the local media engine but left TDLib's own call signalling sitting at
   `CallStateReady` indefinitely — nothing ever called
   `TelegramClient.discardCall`. Compare the sibling `UNAVAILABLE` branch,
   which always did. Fixed by discarding the call from the `FAILED` branch
   too.
2. **`CallStatePresenter` had no case for a stopped media engine while
   signalling was still `READY`.** Its `READY` branch mapped every media
   state other than `CONNECTED`/`RECONNECTING` to `CONNECTING` — including
   `STOPPED`, the state a call lands in immediately after (1) stops it. That
   turned "media engine already gave up" back into "still connecting" for
   the UI, for as long as the TDLib discard from (1) took to land (or forever,
   before (1) was fixed). Fixed by mapping `STOPPED` to `ENDED` explicitly.

Together, these meant *any* media-layer connect failure was invisible to the
user — indistinguishable from a call that was still legitimately trying to
connect. Neither defect required a physical two-device call to prove: both
are pure state-machine gaps, verified via `CallStatePresenterTest` and code
inspection of `DefaultCallsRepository`.

### Connection watchdog

Even with both of the above fixed, a scenario where the native engine never
calls `onConnectionChange` at all (rather than calling it with `FAILED`) was
still unbounded. `DefaultCallsRepository` now runs a generation-scoped
watchdog for 20 seconds from the moment a media session starts: if
`MediaConnectionState.CONNECTED` has not been reached by then, the watchdog
calls the new `TelegramCallMediaEngine.failConnectTimeout()` (tears the
native session down and ends in `FAILED`, not `STOPPED`, so the presenter
fix above applies immediately) and discards the call at TDLib. The watchdog
is cancelled the moment `CONNECTED` is reached, on any terminal media state,
and on TDLib-side call teardown, and is keyed to the same `callGeneration`
counter every other stale-callback guard in this file uses, so a timer left
over from an earlier call can never act on the one that replaced it.

### Signalling diagnostics

The outgoing (`mediaEngine.outgoingSignalingData` → `TelegramClient.
sendCallSignalingData`) and incoming (`TelegramClient.callSignalingDataFlow`
→ `mediaEngine.submitIncomingSignalingData`) signalling collectors in
`DefaultCallsRepository` now log a sanitised, sequence-numbered
`CallDiagnostics` line per packet (`signal=out|in seq=N bytes=N
tdlib=ok|fail`), so a future stall can be pinpointed to "no packets ever
left" vs "packets left but none arrived" vs "packets arrived but native never
connected" from a `logcat -s AetherCall` capture alone, without needing to
log payload content.

## The real media-connection-failure root cause (physically proven)

Physical-device testing (Samsung SM-M145F, Android 15, two consenting-peer
voice call attempts) found the actual reason media never reached CONNECTED,
separate from the presentation-layer defect above.

### 1. `AudioDescription.input` must be valid JSON — fixed

`NativeTelegramCallMediaEngine`'s voice-call path was skipping
`NTgCalls.getMediaDevices()` entirely and substituting
`AudioDescription(MediaSource.DEVICE, 48000, 1, "", false)` — an **empty**
`input` string — to avoid a suspected crash (see #2 below). This is wrong:
ntgcalls' native `BaseDeviceModule` constructor
(`ntgcalls/src/media/devices/base_device_module.cpp`, fetched and read at the
pinned `a1616e2` commit) does:

```cpp
device_metadata_ = json::parse(desc->input);
is_microphone = device_metadata_["is_microphone"];
```

An empty string is not valid JSON, so this throws
`MediaDeviceError("Invalid device metadata")` — reproduced verbatim on
physical hardware (`gen=1 stage=FAILED at=AUDIO_INITIALIZING
type=io.github.pytgcalls.exceptions.MediaDeviceErrorException msg=Invalid
device metadata`), every single voice call, immediately after
`P2P_CONNECTING`. Before this session's presentation-layer fix, this
immediate failure is exactly what a user would see as an indefinite
"Connecting…" screen (media stopped itself, signalling was never told, and
`STOPPED` read back as `CONNECTING`) — the two defects compounded into the
originally reported symptom.

**Fixed** by giving WebRTC's Android layer a real application `Context` (see
#2) and calling `NTgCalls.getMediaDevices()` unconditionally again, using the
real enumerated microphone's `metadata` JSON instead of a synthesized empty
string. Verified on-device: the retest log shows `AUDIO_INITIALIZING
devices=1` (successful enumeration) and, for the first time this session,
real outgoing signalling packets reaching TDLib (`P2P_CONNECTING signal=out
seq=1 bytes=283 tdlib=ok`, `seq=2 bytes=4013 tdlib=ok`).

### 2. Missing `PeerConnectionFactory.initialize()` — fixed

`NTgCalls.getMediaDevices()`'s Java half
(`JavaVideoCapturerModule.getDevices()`) calls
`Camera2Enumerator.isSupported(ApplicationContextProvider.getApplicationContext())`.
Aether never called `org.webrtc.PeerConnectionFactory.initialize(...)` —
the standard, required WebRTC-Android step that gives
`ApplicationContextProvider` its Context — so that call returned `null`,
and `Context.getSystemService` on a null Context is a fatal
`NullPointerException` that JNI escalates to a full ART process abort
(`JNI DETECTED ERROR IN APPLICATION`). Reproduced on physical hardware on a
video call (accidental first test call), full stack trace rooted at
`Camera2Enumerator.isSupported` → `JavaVideoCapturerModule.getDevices()` →
`NTgCalls.getMediaDevices()` → `NativeTelegramCallMediaEngine.
applyStreamSources`.

ntgcalls' own `JNI_OnLoad` (`targets/android/app/src/main/jni/jni_onload.cpp`)
only calls `webrtc::InitAndroid`/`webrtc::JVM::Initialize` — native-side JNI
plumbing. It never calls the Java-side `PeerConnectionFactory.initialize`;
that remains the embedding app's responsibility, as in every WebRTC Android
integration.

**Fixed** in `NativeTelegramCallMediaEngine.setContext()`: calls
`PeerConnectionFactory.initialize(...)` once, with `setNativeLibraryLoader {
true }` (WebRTC's default loader looks for a separate
`jingle_peerconnection_so` library that this build does not ship — WebRTC is
statically linked into `libntgcalls.so`, already loaded via
`NTgCalls.ping()`).

### 3. Native connection-callback JNI classloader crash — FIXED

With both of the above fixed, a physical retest voice call progressed
further than ever previously observed: real signalling flowed (`seq=1`,
`seq=2` reaching TDLib successfully), then the process crashed again, fatally.
This is the **historical regression evidence** for the crash the
`android-callback-classloader.patch` below fixes — the exact trace captured
before the fix existed:

```
JNI DETECTED ERROR IN APPLICATION: JNI GetMethodID called with pending
exception java.lang.ClassNotFoundException: Didn't find class
"io.github.pytgcalls.ConnectionInfo" on path: DexPathList[[directory "."],
nativeLibraryDirectories=[...]]
```

The empty `DexPathList[[directory "."]]` (no dex elements at all, versus the
app's real multi-dex `DexPathList`) is the signature of a JNI `FindClass`
call made from a **native-created thread with no Java-attached classloader
context** — a classic Android JNI pitfall. `wrtc/src/interfaces/
native_connection.cpp` (fetched at the pinned commit) posts connection-state
work to WebRTC's own internal `network_thread()`/`signaling_thread()` —
`rtc::Thread` instances created by WebRTC's native threading, never passed
through Java — which is consistent with this failure mode: such a thread's
JNI environment cannot resolve app-space classes like
`io.github.pytgcalls.ConnectionInfo` via a plain `FindClass` unless the
native code explicitly cached a `ClassLoader` reference obtained from a
correctly-attached thread and used `ClassLoader.loadClass()` instead.

**Root cause, proven against WebRTC's own source, not guessed**:
`wrtc::utils::GetJNIEnv()` does call `webrtc::AttachCurrentThreadIfNeeded()` —
the calling thread genuinely is JVM-attached. The defect is JNI *class-loader*
context, a distinct and separately-documented Android pitfall: `FindClass`
resolves against the ClassLoader of the nearest Java stack frame on the
calling thread, and a thread merely attached from native code has none, so it
silently falls back to the system/bootstrap ClassLoader — which cannot see
any app-packaged class. `sdk/android/native_api/jni/class_loader.h` (fetched
at the pinned WebRTC commit `6f37672d358475cd17544121a12494da454d85fb`)
documents and solves exactly this with `webrtc::GetClass`, and
`sdk/android/native_api/base/init.cc` confirms `webrtc::InitAndroid` (which
ntgcalls' own `JNI_OnLoad` already calls) already calls `InitClassLoader`
internally — the correct ClassLoader was already cached and ready; ntgcalls'
own generated JNI binding template (`targets/android/app/src/main/jni/
utils.hpp.tpl`) simply never used it, calling raw `env->FindClass` instead.

**The fix**: `android-callback-classloader.patch` changes that template's one
`findClass(JNIEnv*, const char*)` helper — the single choke point every
generated struct/enum `parseJ<Name>` conversion in the file routes through,
`ConnectionInfo` included — from `env->FindClass(name)` to
`webrtc::GetClass(env, name)`. Generic by construction: it covers all 31
generated callback DTOs (`ConnectionInfo`, `MediaState`, `CallInfo`, `Frame`,
etc.), not just the one that happened to crash first, and patches the
*generator template*, so every future `cmake` configure regenerates the
fixed binding automatically.

**Physically verified fixed**, not merely believed fixed: two consecutive
answered voice calls on the same physical Samsung SM-M145F (Android 15) that
previously crashed with the trace above instead ran to a clean native
`CONNECTING` → `TIMEOUT` → `FAILED` → TDLib-discard sequence with zero
crashes, real bidirectional signalling proven end-to-end (see
["The real media-connection-failure root cause"](#the-real-media-connection-failure-root-cause-physically-proven)
below), and the same regression check repeated on an accidental video call
(camera path exercises the same WebRTC-Android-context dependency as fix #2
above) with the same clean result. Full artifact provenance for the rebuilt
`.so` carrying this patch is in `call-media/third-party/ntgcalls/README.md`.

## Verification & quality gates

The calling stack is verified across multiple levels:
- **Static JNI proof**: 191/191 `Java_J_N_*` symbols matched between `webrtc.jar` and `libntgcalls.so` dynamic symbol table, re-verified after the classloader patch rebuild (patch 1 not regressed by patch 2). 49/49 `Java_io_github_pytgcalls_*` entry points present.
- **ELF 16 KB page alignment**: Verified via `readelf -l` (all `PT_LOAD` segments aligned to `0x4000`).
- **Isolated JNI regression probe**: Standalone off-repo probe (`com.probe.stock` vs `com.probe.fixed`) executed on physical `arm64-v8a` hardware (Android 15), reproducing `UnsatisfiedLinkError` on stock rc02 and verifying successful codec creation on fixed AAR. In-app smoke check is covered by `ZeroTelegramJniRegressionProbeTest`.
- **Contract tests**: Complete unit test suite in `CallMediaModuleTest` validating stream source failure boundaries, video degradation, audio fallback, native-session cleanup on startup failures, and (added alongside the WebRTC-init hardening below) that a failed WebRTC Android-context initialization never latches as success, blocks native session creation, and can retry on a later call.
- **Fail-closed WebRTC initialization**: `NativeTelegramCallMediaEngine` only marks WebRTC's Android context ready once `PeerConnectionFactory.initialize(...)` has itself returned without throwing; a failure is logged and leaves the flag false so a later attempt retries, and `startCall`/`isMediaTransportAvailable` require this to succeed before `createP2pCall`, `NTgCalls.getMediaDevices()`, or any other native session call is reached — "the `.so` loaded" is no longer treated as "the transport is usable".
- **Physical device verification (2026-09-12, Samsung SM-M145F, Android 15)**: two consecutive answered outgoing voice calls plus one accidental video call, all against the current AAR (`7b63506d...`), with zero crashes of any kind (no `MM6G5xGU` `UnsatisfiedLinkError`, no `Invalid device metadata`, no `ConnectionInfo` `ClassNotFoundException`, no `JNI DETECTED ERROR`), real bidirectional signalling proven end-to-end, and a clean native `CONNECTING` → `TIMEOUT` → `FAILED` resolution (see the ICE/P2P timeout investigation below — media never reached `CONNECTED` in these tests, which remains open separately from the crashes above).

## Voice audio pipeline trace (pinned source, verified gate-by-gate)

Traced end-to-end against the pinned ntgcalls commit and the WebRTC sources it
bundles (`m152.7977.0.2`, commit `6f37672d358475cd17544121a12494da454d85fb`).

### OUTGOING

1. Android microphone -> `OboeDeviceModule` capture
   (`ntgcalls/src/media/devices/oboe_device_module.cpp`; VOICE_COMMUNICATION
   input preset, usage, 48 kHz mono I16). The stream does NOT open at
   `setStreamSources` time: `StreamManager::start()` (which opens every reader
   and writer) runs only on the FIRST `ConnectionState::Connected`
   (`ntgcalls/src/instances/call_interface.cpp`). Native CONNECTED therefore
   also proves the Oboe devices opened without throwing.
2. Oboe callback -> `BaseReader`/`AudioMixer` -> `AudioStreamer::sendData`
   (increments the engine's capture frame accumulator -- what
   `NTgCalls.time(callId, CAPTURE)` returns) -> `LocalAudioSinkAdapter`
   (`audio_sink_` on `NativeConnection`).
3. `OutgoingAudioChannel` (`wrtc/src/interfaces/media/channels/
   outgoing_audio_channel.cpp`) wires the adapter into a stock WebRTC
   `BaseChannel` (`ChannelManager::create_voice_channel`), sets
   `SetRtpTransport(dtls_srtp_transport_)`, negotiates Opus (ptime 60, inband
   FEC) via `SetLocalContent`/`SetRemoteContent`, then enables send
   (`channel_->Enable(true)` + `SetAudioSend(ssrc, true, nullptr, sink_)`).
4. Encoder -> RTP packetizer -> `RtpSenderEgress` -> `RtpPacketSenderProxy`
   -> `RtpTransportControllerSend`'s `PacingController` -> `PacketRouter`
   back to the egress -> `MediaChannel::SendRtp` -> `RtpTransport::
   SendRtpPacket` -> stock `DtlsSrtpTransport` (SRTP protect) ->
   `DtlsTransportInternalImpl` -> `P2PTransportChannel` (Telegram reflectors
   via `ReflectorRelayPortFactory`, plus STUN/TURN) -> network.

### INCOMING

1. Network -> `P2PTransportChannel` -> `DtlsTransportInternalImpl` ->
   `WrappedDtlsSrtpTransport::OnRtpPacketReceived` (SRTP unprotect; failures
   are counted and logged) -> `DemuxPacket` into the per-SSRC
   `IncomingAudioChannel`'s receive channel AND `call_->Receiver()`.
2. NetEQ decode -> `RemoteAudioSink` -> `AudioReceiver` (increments the
   playback accumulator -- `NTgCalls.time(callId, PLAYBACK)`) -> speaker
   writer (`AudioMixer` -> Oboe playback) -> Android speaker.

Incoming channels exist only when `StreamManager::optimize_sources` saw a
PLAYBACK writer (`writers_.contains(Microphone)`) -- which is exactly why
Aether configures PLAYBACK sources before `connectP2p` (see
`NativeTelegramCallMediaEngine.applyPlaybackSources`).

### Send-activation gates (why silence can happen)

| Gate | Where | Proven by |
| --- | --- | --- |
| `was_ever_writable_` | `BaseChannel` (`pc/channel.cc` `IsReadyToSendMedia_w`) | same writability that produces native CONNECTED |
| `SetSend`/stream start | `BaseChannel::UpdateMediaSendRecvState_w` | ntgcalls' explicit `Enable(true)` + `SetAudioSend(true)` |
| pacer paused | `PacingController` (starts `paused_(false)` in m152) | only `OnNetworkAvailability(false)` pauses it; ntgcalls never calls that |
| GoogCC creation | `RtpTransportControllerSend::MaybeCreateControllers` | needs `OnNetworkAvailability(true)`; absence degrades bandwidth estimation, never blocks egress |

## The WebRTC network-availability hypothesis: DISPROVEN for this pin

The known failure mode ("CONNECTED but `PacedSender` paused because
`SignalNetworkState` never reaches Up", reported upstream against ntgcalls
**2.1.0**) does not exist in the BaseChannel-based 3.0.0-rc02 architecture
Aether pins. In stock WebRTC m152 the propagation is fully wired inside the
classes ntgcalls instantiates:

    DtlsSrtpTransport::SetDtlsTransports          (ntgcalls calls this)
      -> RtpTransport::SetRtpPacketTransport
         subscribes ReadyToSend + WritableState on the DTLS transport
      -> RtpTransport::MaybeSignalReadyToSend    (rtcp_mux enabled)
      -> BaseChannel::ConnectToRtpTransport_n    (subscribed via SetRtpTransport)
      -> WebRtcVoiceSendChannel::OnReadyToSend
      -> Call::SignalChannelNetworkState(AUDIO, kNetworkUp)
      -> Call::UpdateAggregateNetworkState
      -> RtpTransportControllerSend::OnNetworkAvailability(true)
      -> pacer_.Resume() (+ MaybeCreateControllers)

`BaseChannel::SetRtpTransport` additionally calls
`media_send_channel()->OnReadyToSend(rtp_transport_->IsReadyToSend())`
immediately, covering the DTLS-already-writable ordering; and the m152
`PacingController` constructor starts with `paused_(false)`, so even a fully
missing signal would not stop the pacer. Accordingly, NO
`OnNetworkAvailability` patch was added to the vendored artifact, and the
diagnostics (below) exist to catch any *other* cause of silence on hardware.

## Media-activity diagnostics (what proves media, not just transport)

`NativeTelegramCallMediaEngine` polls the engine's own frame accumulators
every 5 s while a call is live (`MediaActivityMonitor` -> `CallDiagnostics`
`MEDIA_ACTIVITY` lines, `logcat -s AetherCall`):

- `capture=<s>(+<d>s)` — microphone media-time handed to the WebRTC send
  path. Growing => capture -> encoder input alive. Zero while CONNECTED =>
  capture-side failure.
- `playback=<s>(+<d>s)` — decoded remote media-time delivered to the speaker
  writer. Growing => remote RTP arrives, decrypts and decodes. Zero =>
  remote silent OR our receive path broken (distinguish via the
  `AetherDiagnostics incoming RTP` counters from ntgcalls patch 4).
- `muted`/`videoPaused`/`videoStopped` — the engine's own `MediaState`.
- `remote_source ssrc=... state=... device=...` — remote stream negotiation
  events (`onRemoteSourceChange`); `stream_end` — stream EOF
  (`onStreamEnd`).

RTP-level evidence (outgoing/incoming packet counts at the SRTP transport)
comes from the vendored ntgcalls' logcat diagnostics patch: `adb logcat -s
AetherCall AetherCallNative` (see `tools/capture-call-diagnostics`).

Physical two-way audio has NOT yet been observed with these diagnostics in
place; the instrumentation exists precisely to classify the next physical
call in one pass.
