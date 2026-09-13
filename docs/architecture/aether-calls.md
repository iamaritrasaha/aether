# Aether Calls — dual-backend calling architecture

Aether carries TWO call backends under ONE call surface:

| | Aether Call | Telegram Call (Beta) |
| --- | --- | --- |
| Transport | LiveKit (managed SDK, no hand-rolled WebRTC) | TDLib + patched ntgcalls |
| Reach | Aether ↔ Aether | Aether ↔ official Telegram |
| Status | **primary / stable path** | **Beta / under development** |
| Media E2EE | LiveKit frame encryption ON; **key distribution is development-scoped and NOT production-secure** (below) | Telegram's own call encryption |

Every `ActiveCall` carries its `backend`. Backend callbacks must never mutate
a call owned by another backend.

## Layers

```
Call UI (one CallScreen + chooser + animated return icon)
    ↓
CallHub  — the ONE canonical active call across both backends
    ↓                    ↓
AetherCallsRepository   DefaultCallsRepository (Telegram, untouched)
    ↓ (LiveKit SDK)      ↓ (TgCallsAdapter → NativeTelegramCallMediaEngine → ntgcalls)
Aether Call Service     TDLib signalling
```

- **CallHub** mints a slot per call registration; when two calls ever
  coexist, the newer wins, the loser is presented `DISCARDED` in its own
  flow, and `preemptedBackend` names the loser so ONLY IT tears down. The
  Telegram side's teardown is wired in `AetherApplication`; the Aether side
  observes the same signal in `AetherCallsRepository`.
- **One CallScreen, two transports**: the screen receives backend-supplied
  video content slots (Telegram: decoded I420 frames; Aether: LiveKit
  `TextureViewRenderer` sinks) and a small backend label. It never carries
  per-backend warnings after the chooser.

## Back behavior — no floating call UI

Back from the call screen **minimizes** (via `CallHub.minimizeActiveCall`):
the call keeps running behind its foreground-service notification, the
conversation returns to its normal composer, and nothing floats anywhere.
The owning conversation's call icon carries the animated perimeter (see
below) and **resumes** (`CallHub.resumeActiveCall`) instead of starting a
new call. A different conversation's icon stays normal.

## Animated active-call icon

`Modifier.activeCallPerimeter(active)`: a 2dp sweep-gradient ring with one
strong head and a faint tail travelling the perimeter (~3.4 s/turn). Colors
derive from the app accent (theme-following); implemented as one remembered
brush + canvas rotation — zero per-frame allocation, no bitmaps, no
coroutine loops. With reduced motion (animator duration scale 0) it becomes
a static accent ring.

## Identity

Telegram identity and Aether calling identity are deliberately separate.
`AetherInstallIdentity` gives each installation a stable random `aetherId`
(production: Keystore-protected). Whether a Telegram contact is an
Aether-calling user is a **directory lookup** at call time
(`/lookup?telegramUserId=`) — never an assumption in UI. A contact without
an Aether identity calls via Telegram directly; the chooser appears only
when BOTH systems can reach the recipient, with Aether marked Recommended
and Telegram clearly Beta.

## Dev environment (no LiveKit Cloud)

`call-service/` contains everything:

- **livekit-server** (static binary, config in `livekit-dev.yaml`, UDP
  50000–60000, TURN off for same-LAN dev).
- **call service** (`server.js`, Node + express + livekit-server-sdk):
  directory, invitations, per-participant 10-minute roomJoin JWTs, random
  per-call room names, and a per-invite DEV E2EE media key.
- `run-dev.sh` boots both; `README.md` has one-time setup (binary download,
  key generation, `.env`).

The Android app's `BuildConfig.AETHER_CALL_SERVICE_URL` is the LAN HTTP URL
of the service (override: `-PaetherCallServiceUrl=http://<ip>:8080`). The
LiveKit API secret is env-only in the service.

Incoming invites are polled while an app surface is up (dev has no push);
the loop backs off to 30 s while the service is unreachable, so devices
without the service never churn.

## E2EE — exact security status

LiveKit frame encryption is enabled (`BaseKeyProvider` +
`E2EEOptions`) with a per-invite key. **The key is handed to both
participants BY THE DEV CALL SERVICE over plain HTTP.** Therefore:

- media IS encrypted end-to-end in LiveKit terms;
- key distribution IS NOT secure: the service (and anyone who can reach it)
  knows every dev call's media key; the key is service-generated, not
  established between participants; participants are not authenticated
  against real identities.

Production requires: per-installation identity with Android-Keystore-
protected private material; authenticated per-call key establishment
between the two participants (service relays opaque material it cannot
read); unique per-call media keys never persisted. The boundary is isolated
in `AetherCallSecurity` / `AetherCallE2EE` and surfaced as the constant
`E2EE_STATUS = "dev-key-distribution (NOT production secure)"`. Do not
describe Aether Calls as production-E2EE until that boundary is replaced.

## Telegram Calls (Beta) — truthful status

The Telegram backend is preserved completely, including its diagnostics and
physical history (see `calling-native-stack.md`):

- Signalling: **PHYSICAL PASS** (end-to-end, zero loss, `a7476ab`).
- Transport: native `CONNECTED` reached on hardware (`54272c8`-era).
- Two-way audio: **PHYSICAL FAIL** (media clocks advanced on-device, both
  sides silent — unresolved, evidence preserved).
- Video: **PHYSICAL FAIL** (outgoing video call crashed in the vendor
  capturer's JNI boundary — `nativeOnFrame` UnsatisfiedLinkError, root cause
  identified; fix pending a native rebuild).

## Verification vocabulary

STRUCTURAL PASS (source/tests) · NATIVE PASS (real native code) · DEVICE
PASS (real device, local) · PHYSICAL PASS (real Telegram/Aether peer media
observed). Only device-to-device observation may be called PHYSICAL PASS.
