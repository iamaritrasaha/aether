# Aether capability status

What Aether actually does today, per capability. A capability counts as implemented
only when its real TDLib, media or OS operation runs — never because a control exists.

Verification is tracked in four separate columns because they mean different things:

- **Implemented** — the real operation runs against Telegram
- **Unit tested** — covered by an automated test in this repository
- **Emulator verified** — exercised on a running Android instance
- **Physically verified** — exercised against two real Telegram accounts on hardware

**No row below is marked Emulator verified or Physically verified.** This machine's
only emulator image is x86_64 while the TDLib native library Aether ships is
arm64-only, so the app cannot be booted here; and no two-account hardware run was
performed. Neither is claimed anywhere in this documen## Chat list

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
| Document send & render | yes | yes | no | no | SAF; real name, size, extension |
| Voice note record & send | yes | yes | no | no | contextual `RECORD_AUDIO` |
| Voice note render | yes | yes | no | no | real duration, unpacked 5-bit waveform |
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

| Capability | Implemented | Unit tested | Emulator | Physical | Notes |
| --- | --- | --- | --- | --- | --- |
| Signalling | yes | yes | no | no | `CreateCall`, `AcceptCall`, `DiscardCall` |
| Call state tracking | yes | yes | no | no | `UpdateCall` + `CallStateReady` handoff |
| Call history | yes | yes | no | no | `SearchCallMessages`; survives restart |
| Audio routing | yes | yes | no | no | `AudioManager.setCommunicationDevice` (API 31+) and legacy earpiece / speaker / Bluetooth SCO fallbacks |
| Mute | yes | yes | no | no | UI state flow and hardware microphone mute coordination |
| Foreground service | yes | yes | no | no | `CallService` with microphone service type |
| **Official tgcalls** | **no** | no | no | no | Upstream `TelegramMessenger/tgcalls` requires full WebRTC compilation/GN toolchain |
| **Telegram media transport** | **no** | no | no | no | No fake `CONNECTED` state; honest `MediaConnectionState.UNAVAILABLE` is reported |
| **Bidirectional audio** | **no** | no | no | no | Blocked on official tgcalls WebRTC build |
| **Video calling** | **no** | no | no | no | not offered |
| **Group calls** | **no** | no | no | no | not offered |

`MediaConnectionState.UNAVAILABLE` is strictly emitted when official Telegram media transport is absent. No timer or local scaffold may emit `CONNECTED` without a real tgcalls media stream.
