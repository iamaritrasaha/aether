# Calling — manual validation checklist

This implementation pass is structurally complete, compiles, packages the real
native transport, and passes its automated tests. Physical history, precisely:

- `a7476ab` (SM-M145F, Android 15, second Telegram test account): signalling
  end to end with zero loss across 3 calls; every previously-crashing boundary
  (JNI symbol retention, WebRTC Android-context init, real microphone metadata,
  native-callback classloader) confirmed fixed with zero crashes.
- `54272c8`-era (SM-M145F): the reflector peer-tag fix confirmed native
  `CONNECTED` (not just `CONNECTING`/`TIMEOUT`) on hardware.
- Current head: `:call-media:connectedDebugAndroidTest` 9/9 PASS (voice native
  suite + video suite: camera metadata contract, facing resolution, CAPTURE
  mic+camera + PLAYBACK speaker+EXTERNAL video config, clean teardown) and
  `:app:connectedDebugAndroidTest` 3/3 PASS — on BOTH Samsung SM-P610
  (Android 13) and SM-M145F (Android 15), plus the x86_64 AVD.

**Two-way physical audio has still never been verified.** Every checkbox below
remains unchecked until a real two-account run. During that run, the DEBUG-only
call inspector on the call screen shows live media evidence (capture/playback
counters, remote source presence, route, camera facing), and
`tools/call-diagnostics-summary` prints a per-generation connected duration and
an A–F flow-class verdict from the captured log. See
`docs/architecture/calling-native-stack.md` for what is actually running under
the hood, including the one assumption (video pixel format) that is
source-proven but not yet device-proven.

Do not treat a green run of `testDebugUnitTest` / `assembleDebug` /
`lintDebug` as evidence that any of the rows below are true. They are not
evidence of physical interoperability with official Telegram — only a real
device run, of the kind described above, is.

## Setup

- Two devices, each running a build of this app signed in to a **different**
  Telegram account, and/or one device running official Telegram (Android/iOS/
  Desktop) alongside one running this app.
- Both accounts already contacts of each other (calling a non-contact follows
  the same path, but start with the simple case first).
- `AetherFeatureFlags.CALLS_ENABLED` must be `true` (it is, as of this pass).

## VOICE OUTGOING — Aether → official Telegram

- [ ] Placing the call rings the official Telegram device
- [ ] Accepting on official Telegram reaches a connected state on both sides
- [ ] Audio flows Aether → official Telegram (speak on Aether, heard on official)
- [ ] Audio flows official Telegram → Aether (speak on official, heard on Aether)
- [ ] Encryption-key emoji (if surfaced) match on both sides
- [ ] Ending from the Aether side ends the call on both sides
- [ ] Ending from the official Telegram side ends the call on the Aether side

## VOICE INCOMING — official Telegram → Aether

- [ ] Aether rings when official Telegram places the call
- [ ] Accepting on Aether reaches a connected state on both sides
- [ ] Two-way audio (both directions, as above)
- [ ] Declining on Aether is reflected as declined on official Telegram
- [ ] Missing the call (letting it ring out) shows correctly in both call histories

## VIDEO OUTGOING — Aether → official Telegram

- [ ] Placing a video call rings the official Telegram device as a video call
  (not a voice call)
- [ ] Accepting shows the local camera's picture on official Telegram
- [ ] Two-way audio, as above
- [ ] Local preview on Aether looks correct (not upside down, not badly
  discolored — if colors are visibly wrong, that's the I420-format assumption
  in `docs/architecture/calling-native-stack.md` being incorrect for this
  device, not a crash)

## VIDEO INCOMING — official Telegram → Aether

- [ ] Aether distinguishes an incoming video call from an incoming voice call
  before accepting
- [ ] Accepting shows official Telegram's remote video on Aether
- [ ] Local preview also renders on Aether

## Cross-cutting, run against whichever direction is easiest to set up

- [ ] Ringing state is visually distinct from connecting and from connected
- [ ] Microphone mute (from Aether) is audible as silence on the other side;
  unmuting restores audio
- [ ] Speaker toggle actually changes where audio plays on the Aether device
- [ ] Earpiece routing works when speaker is off
- [ ] Bluetooth headset routing, if a paired device is available
- [ ] Camera enable/disable during an active video call: disabling shows no
  video (not a frozen frame) on the other side; re-enabling resumes it
- [ ] Switch camera (front/back) actually swaps which camera is sending —
  since `8aeb0db` facing is resolved from ntgcalls' authoritative
  `{id, is_front}` enumerator metadata (the same enumerator that opens the
  device), with `LENS_FACING` as fallback; `VideoMediaNativeSmokeTest`
  proves front+back resolve on-device, but a real switch mid-call is still
  unverified
- [ ] Ending the call from either side works
- [ ] Backgrounding the Aether app during an active call keeps audio running
  (voice) and the call does not silently die
- [ ] Locking the screen during an active call keeps audio running
- [ ] Switching networks mid-call (Wi-Fi → mobile data, or vice versa)
  reconnects rather than permanently dropping the call
- [ ] A genuine network loss (airplane mode for a few seconds) shows a
  reconnecting state, not a false "connected" or an unrecoverable hang
- [ ] Rotating the device during a video call does not crash and keeps both
  video feeds displayed sensibly
- [ ] Double-tapping Accept, or Accept then End in quick succession, does not
  produce a stuck or duplicated call
- [ ] Placing a second call while one is already active is refused or handled
  sensibly, not silently dropped or double-started

## What to report back

For each unchecked or failed row: which device/OS/Telegram-client version was
on the other end, what was expected, what actually happened, and (if
possible) the Logcat around the failure filtered to `CallsRepository`,
`CallMediaEngine`, and `NTGCALLS`. None of the diagnostic logging in this
implementation includes encryption keys, auth data, or message content — see
"Failure logging" below.

## Failure logging already in place

Structured, non-sensitive diagnostics are already logged at the points where
manual testing will need them: native engine connection-state transitions,
native-engine errors, and TDLib call-state transitions in
`DefaultCallsRepository`/`TelegramClient`. None of it logs encryption keys,
signalling payload contents, or Telegram credentials.


## Automated precondition (new since 03cab8b)

- On-device native smoke suite (no call needed):
  `adb shell pm grant com.foresightlabs.aether.calls.media android.permission.RECORD_AUDIO`
  is unnecessary — `:call-media` installs its test APK with `-g` (auto-grant);
  run `./gradlew :call-media:connectedDebugAndroidTest` and expect 9/9 PASS
  (load/ping, WebRTC context, protocol, device enumeration, real
  session+sources+teardown, callback registration — plus the video suite:
  camera metadata contract, front/back resolution, native session accepting
  CAPTURE with camera + PLAYBACK with the EXTERNAL video slot, `videoStopped`
  false, clean teardown).

## Physical run procedure (current tooling)

1. `tools/capture-call-diagnostics` — starts a clean, tag-filtered capture
   into git-ignored `diagnostics/`.
2. Place the call under test (outgoing first, then incoming).
3. After the call: `tools/call-diagnostics-summary <log-file>` prints the
   connection timeline, CAPTURE/PLAYBACK progression, RTP send/receive
   counters, remote-source events, routing evidence and errors — the failure
   class (capture-dead / playback-dead / incoming-blocked / RTP-level) is
   readable from that summary without manually scanning logcat.
