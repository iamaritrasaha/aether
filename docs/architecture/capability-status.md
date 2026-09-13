# Aether capability status

What Aether actually does today, per capability. A capability counts as implemented
only when its real TDLib, media or OS operation runs — never because a control exists.

Verification is tracked in four separate columns because they mean different things:

- **Implemented** — the real operation runs against Telegram
- **Unit tested** — covered by an automated test in this repository
- **Emulator verified** — exercised on a running Android instance
- **Physically verified** — exercised against two real Telegram accounts on hardware

**No row below is marked Emulator verified.** This machine's only emulator image is
x86_64 while the TDLib and ntgcalls native libraries Aether ships are arm64-only, so
the app cannot exercise its real native call/media path there; the emulator is used
only for UI/navigation smoke checks, never as evidence of a native-dependent
capability.

**Calling is the one section with real physical hardware history** (Samsung
SM-M145F, Android 15, arm64-v8a) against a second, separate Telegram account — see
the Calling section below for exactly what was and was not verified, and at which
commit. Every other section remains genuinely untested on hardware; "Implemented:
yes" there means the real code path compiles, links and runs against a real
dependency in a unit test — **STRUCTURALLY VERIFIED**, not **PHYSICALLY VERIFIED**.
A physically verified result is only ever claimed for the specific commit it was
observed at, never assumed to still hold after later changes until re-run.

## Chat list

| Capability | TDLib operation | Implemented | Unit tested | Emulator | Physical |
| --- | --- | --- | --- | --- | --- |
| Main chat list, ordering | `LoadChats`, `ChatPosition` | yes | yes | no | no |
| Archive membership | `ChatPosition` / `ChatListArchive` | yes | yes | no | no |
| Unread & mention badges | `Chat.unreadCount` | yes | yes | no | no |
| Draft preview | `Chat.draftMessage` | yes | yes | no | no |
| Mute state | `Chat.notificationSettings` | yes | yes | no | no |
| Pinned state | `ChatPosition.isPinned` | yes | yes | no | no |
| Typing preview | `UpdateChatAction` | yes | yes | no | no |
| Delivery / read state | `lastReadOutboxMessageId` | yes | yes | no | no |
| Presence | `UserStatus` | yes | yes | no | no |
| Pin / unpin chat | `ToggleChatIsPinned` | yes | yes | no | no |
| Mark read / unread | `ViewMessages`, `ToggleChatIsMarkedAsUnread` | yes | yes | no | no |
| Mute / unmute | `SetChatNotificationSettings` | yes | yes | no | no |
| Archive / unarchive | `AddChatToList` | yes | yes | no | no |
| Clear history | `DeleteChatHistory(false,false)` | yes | yes | no | no |
| Delete conversation | `DeleteChatHistory(true,false)` | yes | yes | no | no |
| Delete for everyone | `DeleteChatHistory(true,true)` | yes | yes | no | no |
| Leave chat | `LeaveChat` | yes | yes | no | no |
| Block / unblock | `SetMessageSenderBlockList` | yes | yes | no | no |
| **Chat folders** | `GetChatFolder`, `ChatListFolder` | **yes** | **yes** | no | no |

**Chat folders**: `GetChatFolder` / `ChatListFolder` are wired into `ChatsViewModel` and surfaced as folder tabs on `HomeScreen` when multiple folders exist. Switching folders filters the active chat list accordingly, while `ChatFolder.Main` provides the full unified chat list.

## Messaging

| Capability | TDLib operation | Implemented | Unit tested | Emulator | Physical |
| --- | --- | --- | --- | --- | --- |
| Send text | `SendMessage` / `InputMessageText` | yes | yes | no | no |
| Reply | `InputMessageReplyToMessage` | yes | yes | no | no |
| Reply quotes | `InputMessageReplyToMessage` + `InputTextQuote` | yes | yes | no | no |
| Edit text & caption | `EditMessageText` / `EditMessageCaption` | yes | yes | no | no |
| Delete, scoped | `DeleteMessages(revoke)` | yes | yes | no | no |
| Forward (single & multi) | `ForwardMessages` + target picker | yes | yes | no | no |
| Forward options (`sendCopy`, `removeCaption`) | `ForwardMessages(sendCopy, removeCaption)` | yes | yes | no | no |
| Copy | capability-gated | yes | yes | no | no |
| Reactions — add / remove | `AddMessageReaction`, `RemoveMessageReaction` | yes | yes | no | no |
| Reactions — counts, chosen state | `UpdateMessageInteractionInfo` | yes | yes | no | no |
| Pin / unpin message | `PinChatMessage`, `UnpinChatMessage` | yes | yes | no | no |
| Unpin all messages | `UnpinAllChatMessages` | yes | yes | no | no |
| Pinned banner & stack | `SearchChatMessages(FilterPinned)` | yes | yes | no | no |
| In-chat search | `SearchChatMessages` | yes | yes | no | no |
| Jump to message | `GetChatHistory` window | yes | yes | no | no |
| Global search — chats, people, messages | `SearchChats`, `SearchContacts`, `SearchMessages` | yes | yes | no | no |
| Multi-select | capability intersection | yes | yes | no | no |
| Failed-send retry | `ResendMessages` | yes | yes | no | no |
| Read state | `ViewMessages` | yes | yes | no | no |
| Chat actions (typing, recording, uploading) | `SendChatAction` + `ChatActionCancel` | yes | yes | no | no |
| Drafts, server-side | `SetChatDraftMessage` | yes | yes | no | no |
| Message capability policy | `GetMessageProperties` | yes | yes | no | no |
| Text entities | `TextEntityType*` → `AetherText` | yes | yes | no | no |
| Spoilers (text) | `TextEntityTypeSpoiler` | yes | yes | no | no |
| Composer formatting | entity insertion & markdown parsing | yes | yes | no | no |
| Message info sheet | real metadata inspection | yes | yes | no | no |
| Scheduled / silent send | `MessageSchedulingState` / `sendCopy` | yes | yes | no | no |
| Scheduled message dispatch | `EditMessageSchedulingState` | yes | yes | no | no |
| Forum topics | `GetForumTopics`, topic-bound messages | yes | yes | no | no |

**Message edit completeness**: `TelegramClient.editMessage` inspects message content type and dispatches `TdApi.EditMessageCaption` for media items (`Photo`, `Video`, `Animation`, `Document`, `Audio`, `VoiceNote`) and `TdApi.EditMessageText` for text messages.

**Multi-message forward**: `TelegramClient.forwardMessages` supports `LongArray` message IDs with `sendCopy` (attribution stripping) and `removeCaption` flags. `ConversationScreen` provides forward target selection and option toggles across single and multi-selected messages.

**Scheduled messages**: Supports scheduled dispatch (`MessageSchedulingStateSendAtDate`), send when online (`MessageSchedulingStateSendWhenOnline`), silent delivery, schedule inspection (`getScheduledMessages`), immediate send (`sendScheduledMessageNow`), and rescheduling (`rescheduleMessage`).

**Forum topics**: Forum supergroups open into dedicated topic lists (`Destinations.forumTopics`) with topic creation, renaming, closing, and pin toggling. Conversations support topic scoping (`Destinations.conversationTopic`) using `MessageTopic` parameterization on all sends and history fetches.

## Media

| Capability | Implemented | Unit tested | Emulator | Physical | Notes |
| --- | --- | --- | --- | --- | --- |
| Photo send | yes | yes | no | no | Android photo picker |
| Photo / video render | yes | yes | no | no | largest downloaded size |
| Video send | yes | yes | no | no | `InputMessageVideo` |
| Album send | yes | yes | no | no | one `SendMessageAlbum`, capped at 10 |
| Album render | yes | yes | no | no | grouped cluster, 2/3/4-up layouts |
| Document receive & open | yes | yes | no | **yes (device, synthetic)** | Since `ff18944` a received document's CONTENT file is mapped and indexed (lookup-only, refreshed on download completion); the chip is tappable: open via FileProvider `content://` + `ACTION_VIEW` + `FLAG_GRANT_READ`, or request the download first. `DocumentOpenDeviceTest` (SM-P610): FileProvider URI readable, MIME derivation correct per extension, ACTION_VIEW resolution never throws, unresolvable MIME degrades gracefully. No real Telegram document existed on the test account to consume -- real-media pass remains a one-tap manual check. |
| Voice note record & send | yes | yes | no | no | contextual `RECORD_AUDIO` (record mic on-device was deliberately NOT exercised autonomously: it captures room audio) |
| Voice note / audio playback | yes | yes | no | **yes (device, synthetic)** | Since `2949f66`+`26e422d` voice notes and audio messages actually play: content file mapped+indexed (audio indexes the audio, never the cover), tap-to-download-then-autoplay, one screen-scoped ExoPlayer (AudioPlaybackController), real position/speed, error/failure states, released on leaving. `AudioPlaybackDeviceTest` (SM-P610): a real WAV prepared and played on the device's real audio stack -- position advances, replace/release clean; **the publish-deadlock bug (UI could never show playing) was caught by this test's Robolectric twin and fixed**. Audibility needs human ears: HUMAN AUDIBILITY UNVERIFIED. No real voice note existed on the test account to consume. |
| Audio file send & render | yes | yes | no | no | `MessageType.AUDIO`, performer, title, duration, cover |
| Static sticker render | yes | yes | no | no | WebP and emoji fallback |
| Static sticker send | yes | yes | no | no | `InputMessageSticker` |
| GIF / animation render | yes | yes | no | no | GIF thumbnail, badge, duration |
| GIF / animation send | yes | yes | no | no | `InputMessageAnimation` |
| Contact send & render | yes | yes | no | no | manual entry, explicit consent copy |
| Static location send & render | yes | yes | no | no | last known fix, confirmed before send |
| Download | yes | yes | no | no | `DownloadFile` + `UpdateFile` |
| **Animated stickers (.tgs)** | **yes** | **yes** | no | no | `TgsDecompressor` GZIP decompression to Lottie vector json with continuous looping |
| **Video stickers (.webm)** | **yes** | **yes** | no | no | Media3 ExoPlayer looping muted playback, transparent background, reduced-motion fallback |
| **Video notes** | **yes** | **yes** | no | no | CameraX circular recording UI, 60s timer, MP4 output, `InputMessageVideoNote`, circular playback |
| **Venue send & render** | **yes** | **yes** | no | no | `InputMessageVenue`, `MessageVenue`, title/address rendering |
| **Live location** | **yes** | **yes** | no | no | `InputMessageLocation`, continuous GPS tracking loop via `LiveLocationCoordinator`, `LiveLocationService`, stop button |
| **Sticker picker** | **yes** | **yes** | no | no | Installed packs, recents, favorites, WebM/TGS renderers, direct send |
| **Media replacement** | **yes** | **yes** | no | no | `EditMessageMedia` gated by `canEditMedia` and saveable media type |

## Service messages

| Capability | Implemented | Unit tested | Emulator | Physical |
| --- | --- | --- | --- | --- |
| Service-event classification (exhaustive over pinned API) | yes | yes | no | no |
| Pin, joins, leaves, title, photo, chat creation | yes | yes | no | no |
| Auto-delete changes | yes | yes | no | no |
| Video chat started / ended / scheduled | yes | yes | no | no |
| Forum topic created / edited / closed / hidden | yes | yes | no | no |
| Expired self-destructing media | yes | yes | no | no |
| Group upgrades and migrations | yes | yes | no | no |
| Unknown service types | yes | yes | no | no |

## Secret chats

| Capability | Implemented | Unit tested | Emulator | Physical |
| --- | --- | --- | --- | --- |
| `ChatTypeSecret` identification | yes | yes | no | no |
| E2EE wording confined to secret chats | yes | yes | no | no |
| Close secret chat | yes | yes | no | no |
| Forward restriction follows `MessageProperties` | yes | yes | no | no |
| Auto-delete / TTL tracking | yes | yes | no | no |

## Calling

`AetherFeatureFlags.CALLS_ENABLED = true`: calling is a live, enabled capability, not
held. `AetherFeatureFlags.APP_LOCK_ENABLED = false` (unrelated capability, held for
this milestone — see the top-level table in `README.md`).

| Capability | Implemented | Unit tested | Emulator | Physical | Notes |
| --- | --- | --- | --- | --- | --- |
| Signalling, both directions | yes | yes | no | **yes** | `CreateCall`, `AcceptCall`, `DiscardCall`, `SendCallSignalingData` / `UpdateNewCallSignalingData`. Verified at commit `a7476ab` with per-boundary diagnostics: every outgoing and incoming signalling packet traced end to end with zero loss across 3 real calls. |
| Call state tracking | yes | yes | no | **yes** | `UpdateCall` + `CallStateReady` handoff; verified at `a7476ab` alongside signalling. |
| Call history | yes | yes | no | no | `SearchCallMessages`; survives restart. Not specifically exercised during physical sessions. |
| Incoming-call ringer | yes | yes | no | no | Since `03cab8b`: MAX-importance `CATEGORY_CALL` ringing notification with Answer/Decline actions for a pending incoming call (no full-screen intent — policy), routed through `CallService` with stale-action call-id guards; posted/cancelled lifecycle unit-tested. Since `8aeb0db`: an Answer from the notification without `RECORD_AUDIO` does not accept (Android would kill the process at the microphone-typed FGS) — it opens the call screen, whose Answer runs the normal permission flow. |
| Audio routing | yes | yes | no | no | `AudioManager.setCommunicationDevice` (API 31+ incl. BLE devices) and legacy fallbacks; UI route state now derives from Android's ACTUAL selected device via `AudioDeviceCallback` (synced on device add/remove and on route requests, with result verification and diagnostics) and is synced into `ActiveCall.audioRoute` (`8aeb0db`), so the call screen labels Bluetooth/wired routes and flips from the real route, not the last toggle. TelegramClient's separate deprecated AudioManager path was removed (`8aeb0db`) — it raced the engine's routing and clobbered BT/wired. Route-type mapping unit-tested. Runtime route changes still need physical exercise. |
| Mute | yes | yes | no | no | `toggleMute` derives from the UI-visible `ActiveCall.isMuted` and drives engine mute/unmute + AudioManager mic mute with the same value (`8aeb0db`); the engine's process-wide muted flag resets on stop/fail so a stale mute cannot leak into the next call. Flow/state pairing unit-tested (`DefaultCallsRepositoryLifecycleTest`). Native mute path still needs a physical call. |
| Foreground service | yes | yes | no | **yes** | `CallService`, typed `microphone` or `microphone\|camera` per call; started/stopped cleanly across all `a7476ab` test calls. |
| Telegram media transport (ntgcalls) | yes | yes | no | **yes (on-device smoke)** | Real native library linked and packaged (see `docs/architecture/calling-native-stack.md`). Since `03cab8b` the vendored artifact carries five patches and `CallMediaNativeSmokeTest` (instrumented, `:call-media:connectedDebugAndroidTest`) proves on real arm64 hardware: native load + `ping`, WebRTC Android-context init, protocol contract, real mic/speaker enumeration with metadata, REAL native session with CAPTURE+PLAYBACK stream-source configuration and clean teardown, callback registration — 6/6 PASS on Samsung SM-P610 (Android 13). Note this is device-level verification of the native boundaries, NOT a real call. At commit `a7476ab`: the previously-crashing `MM6G5xGU` `UnsatisfiedLinkError`, `Invalid device metadata` failure, WebRTC-Android-context null crash, and `ConnectionInfo` `ClassNotFoundException` are all physically confirmed fixed — zero crashes across 3 voice calls + 1 video call. |
| Voice call media path | yes | yes | no | **partial** | `skipExchange` + `connectP2p` against TDLib's negotiated key and servers. Two fixes since `a7476ab`: (1) `CallServerEndpoint.peerTag` is now a genuine nullable field (was a synthesized `ByteArray(0)`, which made ntgcalls' native `RTCServer::to_rtc_servers()` misclassify every WebRTC/STUN/TURN server as a Telegram reflector) — physically confirmed on hardware to reach native `CONNECTED`, not just `CONNECTING`/`TIMEOUT`. (2) `applyPlaybackSources` now configures a PLAYBACK stream (real speaker device in the `.microphone` slot per `StreamManager::optimize_sources`) in addition to CAPTURE, and native `CONNECTED` now propagates into `ActiveCall.mediaState` so the call screen actually leaves "Connecting…". **`CONNECTED` proves the ICE/DTLS transport is writable, never that audio is actually flowing both ways — that still requires a dedicated physical retest, not yet completed against this change.** |
| Video call media path | yes | yes | no | **partial (device smoke)** | Camera capture + raw decoded-frame rendering; pixel-format assumption undocumented by the vendor (source-proven against the pinned revision, still unproven against live device output). **`8aeb0db` fixed a silent video killer**: camera selection passed ntgcalls' JSON metadata to `getCameraCharacteristics`, which always threw, so every "video" call was audio-only; selection now parses the enumerator's authoritative `{id, is_front}` metadata (see `CameraDeviceSelection`), with `LENS_FACING` as fallback. `VideoMediaNativeSmokeTest` (`e846e02`, instrumented): real enumeration carries the metadata contract, front+back resolve from real devices, a real native session accepts CAPTURE(mic+camera `VideoDescription`) + PLAYBACK(speaker+`EXTERNAL` video) and reports `videoStopped=false`, clean teardown — 9/9 PASS on SM-P610, SM-M145F and the AVD. Camera state is engine-reported (`onCameraStateChanged`) and mirrored into `ActiveCall.cameraIntentOn/isFrontCamera`; the call screen shows a camera-starting placeholder, mirrors the front preview, and requests `CAMERA` at the tap. `applyPlaybackSources` also configures an `EXTERNAL` PLAYBACK camera slot (per `StreamManager::handle_playback_config`/`setup_video_playback_callbacks`) so decoded remote frames reach `NTgCalls.onFrames` — the full local-capture + remote-decode + render chain is still not physically exercised end to end. |
| Group calls | no | no | no | no | not offered; ntgcalls supports it, Aether does not expose it |

**No row has been re-verified as a real two-account call against the current
commit** — the current artifacts (five-patch `aetherfix2` AAR, camera-metadata
mapping fix, media-state truth model, debug inspector) have been exercised by the
on-device instrumented suites and JVM tests only: `:call-media:connectedDebugAndroidTest`
9/9 PASS (native voice suite + new video suite) and `:app:connectedDebugAndroidTest`
3/3 PASS on BOTH Samsung SM-P610 (Android 13) and Samsung SM-M145F (Android 15),
plus the x86_64 AVD. The older `a7476ab`/`54272c8` physical history was on the
SM-M145F against a second, separate Telegram test account. Two-way physical audio
remains the open gate: one outgoing call with `tools/capture-call-diagnostics`
running classifies the media path via `MEDIA_ACTIVITY` counters and the native RTP
counters; `tools/call-diagnostics-summary` parses the result and now prints a
per-generation connected duration and an A–F flow-class verdict. The DEBUG-only
call inspector (`CallDebugInspector`) shows the same evidence live on the call
screen during the physical session.
`MediaConnectionState.UNAVAILABLE` is strictly emitted when the media transport is
absent or fails to load; no timer or local scaffold may emit `CONNECTED` without the
real native engine reporting a connected state. See
`docs/qa/calling-manual-test-checklist.md` for the outstanding physical validation
(reaching `CONNECTED`, two-way audio, mute, routing, second call, incoming call).
