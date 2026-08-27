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
performed. Neither is claimed anywhere in this document.

---

## Chat list

| Capability | TDLib operation | Implemented | Unit tested | Emulator | Physical |
| --- | --- | --- | --- | --- | --- |
| Main chat list, ordering | `LoadChats`, `ChatPosition` | yes | yes | no | no |
| Archive membership | `ChatPosition` / `ChatListArchive` | yes | no | no | no |
| Unread & mention badges | `Chat.unreadCount` | yes | no | no | no |
| Draft preview | `Chat.draftMessage` | yes | yes | no | no |
| Mute state | `Chat.notificationSettings` | yes | no | no | no |
| Pinned state | `ChatPosition.isPinned` | yes | yes | no | no |
| Typing preview | `UpdateChatAction` | yes | yes | no | no |
| Delivery / read state | `lastReadOutboxMessageId` | yes | yes | no | no |
| Presence | `UserStatus` | yes | yes | no | no |
| Pin / unpin chat | `ToggleChatIsPinned` | yes | policy | no | no |
| Mark read / unread | `ViewMessages`, `ToggleChatIsMarkedAsUnread` | yes | policy | no | no |
| Mute / unmute | `SetChatNotificationSettings` | yes | policy | no | no |
| Archive / unarchive | `AddChatToList` | yes | policy | no | no |
| Clear history | `DeleteChatHistory(false,false)` | yes | policy | no | no |
| Delete conversation | `DeleteChatHistory(true,false)` | yes | policy | no | no |
| Delete for everyone | `DeleteChatHistory(true,true)` | yes | policy | no | no |
| Leave chat | `LeaveChat` | yes | policy | no | no |
| Block / unblock | `SetMessageSenderBlockList` | yes | policy | no | no |
| **Chat folders** | — | **no** | — | — | — |

"policy" means the action's *availability* is covered by `ChatActionPolicyTest`, and
it dispatches to a single real TDLib call, but no test drives the round trip through
a TDLib double.

**Chat folders**: `GetChatFolder` / `ChatListFolder` exist in the pinned API. Aether
exposes no folder surface, so they are not implemented. Chats belonging to a folder
still appear correctly in the main list — folder membership is an additional
`ChatPosition`, not a replacement, so nothing about them is broken by the omission.

## Messaging

| Capability | TDLib operation | Implemented | Unit tested | Emulator | Physical |
| --- | --- | --- | --- | --- | --- |
| Send text | `SendMessage` / `InputMessageText` | yes | yes | no | no |
| Reply | `InputMessageReplyToMessage` | yes | yes | no | no |
| Edit text | `EditMessageText` | yes | yes | no | no |
| Delete, scoped | `DeleteMessages(revoke)` | yes | yes | no | no |
| Forward | `ForwardMessages` + target picker | yes | no | no | no |
| Copy | capability-gated | yes | yes | no | no |
| Reactions — add / remove | `AddMessageReaction`, `RemoveMessageReaction` | yes | yes | no | no |
| Reactions — counts, chosen state | `UpdateMessageInteractionInfo` | yes | yes | no | no |
| Pin / unpin message | `PinChatMessage`, `UnpinChatMessage` | yes | no | no | no |
| Pinned banner & stack | `SearchChatMessages(FilterPinned)` | yes | no | no | no |
| In-chat search | `SearchChatMessages` | yes | yes | no | no |
| Jump to message | `GetChatHistory` window | yes | no | no | no |
| Global search — chats, people, messages | `SearchChats`, `SearchContacts`, `SearchMessages` | yes | no | no | no |
| Multi-select | capability intersection | yes | yes | no | no |
| Failed-send retry | `ResendMessages` | yes | yes | no | no |
| Read state | `ViewMessages` | yes | yes | no | no |
| Chat actions (typing, recording, uploading) | `SendChatAction` + `ChatActionCancel` | yes | no | no | no |
| Drafts, server-side | `SetChatDraftMessage` | yes | no | no | no |
| Message capability policy | `GetMessageProperties` | yes | yes | no | no |
| Text entities | `TextEntityType*` → `AetherText` | yes | yes | no | no |
| Spoilers (text) | `TextEntityTypeSpoiler` | yes | yes | no | no |
| **Composer formatting** | — | **no** | — | — | — |
| **Reply quotes** | — | **no** | — | — | — |
| **Message info sheet** | — | **no** | — | — | — |
| **Forward options** (copy, hide sender) | — | **no** | — | — | — |
| **Scheduled / silent send** | — | **no** | — | — | — |
| **Forum topics** | — | **no** | — | — | — |

**Composer formatting** (§11): rendering entities is done; *creating* them is not.
The composer sends plain text, so no entity is fabricated — text typed as `**bold**`
is sent literally rather than being silently converted.

**Reply quotes** (§15): replies use `InputMessageReplyToMessage` with a null quote.
Quoting a selected span needs `InputTextQuote` with offsets taken from a text
selection in the bubble, which Aether has no selection affordance for yet. Replies
are real replies; they simply never carry a quote.

**Forward options** (§16): forwarding sends one message to one chosen chat with
sender attribution intact. `sendCopy` and `removeCaption` exist in the pinned API and
are not exposed, so no option is shown that does not work.

**Forum topics** (§30): `MessageTopic` is passed as null everywhere. In a forum
supergroup this means messages are read and sent against the chat rather than a
topic. Forum *service events* render correctly as system events. This is the largest
remaining gap and is called out as such rather than partially exposed.

## Media

| Capability | Implemented | Unit tested | Emulator | Physical | Notes |
| --- | --- | --- | --- | --- | --- |
| Photo send | yes | no | no | no | Android photo picker |
| Photo / video render | yes | yes | no | no | largest downloaded size |
| Video send | yes | no | no | no | `InputMessageVideo` |
| Album send | yes | no | no | no | one `SendMessageAlbum`, capped at 10 |
| Album render | yes | yes | no | no | grouped cluster, 2/3/4-up layouts |
| Document send & render | yes | yes | no | no | SAF; real name, size, extension |
| Voice note record & send | yes | no | no | no | contextual `RECORD_AUDIO` |
| Voice note render | yes | yes | no | no | real duration, unpacked 5-bit waveform |
| Sticker render (static) | yes | yes | no | no | WebP only |
| GIF / animation render | yes | no | no | no | still frame; not played |
| Contact send | yes | no | no | no | manual entry, explicit consent copy |
| Static location send | yes | no | no | no | last known fix, confirmed before send |
| Contact / location / venue render | yes | no | no | no | — |
| Download | yes | no | no | no | `DownloadFile` + `UpdateFile` |
| **Animated / video stickers** | **no** | — | — | — | `.tgs` and `.webm` show their emoji |
| **GIF send** | **no** | — | — | — | — |
| **Audio files as a distinct type** | **no** | — | — | — | routed through documents |
| **Video notes** | **no** | — | — | — | hidden, per §6 |
| **Venue send** | **no** | — | — | — | no venue search source |
| **Live location** | **no** | — | — | — | hidden, per §25 |
| **Sticker picker** | **no** | — | — | — | — |

**Animated stickers**: rendering `.tgs` needs a Lottie pipeline and `.webm` needs a
video decoder; neither ships. Those stickers present their emoji rather than a blank
bubble.

**GIF rendering** shows the still thumbnail. Nothing claims to be playing.

**Video notes and live location** are hidden entirely rather than shown as
placeholders, as the brief requires.

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

An unknown service type renders as a neutral system event derived from its real TDLib
type. It can never reach a text bubble — `ServiceMessages.isServiceEvent` is
exhaustive over the pinned API rather than a list of types someone happened to hit.

## Secret chats

| Capability | Implemented | Unit tested | Emulator | Physical |
| --- | --- | --- | --- | --- |
| `ChatTypeSecret` identification | yes | yes | no | no |
| E2EE wording confined to secret chats | yes | yes | no | no |
| Close secret chat | yes | yes | no | no |
| Forward restriction follows `MessageProperties` | yes | yes | no | no |
| **Self-destruct timer UI** | **no** | — | — | — |

Ordinary cloud chats are labelled "Telegram Cloud Chat" and described as protected by
MTProto. They are never described as end-to-end encrypted.

## Calling

Untouched this pass, as instructed.

| Capability | Implemented | Notes |
| --- | --- | --- |
| Signalling | yes | `CreateCall`, `AcceptCall`, `DiscardCall` |
| Call state tracking | yes | `UpdateCall` |
| Call history | yes | `SearchCallMessages`; survives restart |
| Audio focus, routing, restore | yes | earpiece / speaker / Bluetooth SCO |
| Foreground service | yes | microphone service type |
| **Bidirectional audio** | **no** | no media transport in this build |
| **Video calling** | **no** | not offered |
| **Group calls** | **no** | not offered |

`MediaConnectionState.UNAVAILABLE` remains the honest state when no transport is
present: the call is refused and the user is told why. No fake `CONNECTED` state has
been reintroduced.
