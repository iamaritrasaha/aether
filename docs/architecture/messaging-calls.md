# Aether — messaging, chat management and calling

Aether is a Telegram client. Its interface is its own; its behaviour is Telegram's.
This document records how the two are kept from drifting apart.

The pinned TDLib revision is `89ebded9571b7bb589ec1bd05e585fffa4c580e2`, and the
generated `tdlib/src/main/java/org/drinkless/tdlib/TdApi.java` in this repository is
the only authority on what the API offers. Where online documentation and the pinned
generated API disagree, the pinned API wins.

## The rule everything else follows

**Aether never fakes a Telegram action.**

If a control is visible, tapping it performs the real server operation. A capability
Aether cannot genuinely carry out is not shown as a disabled-looking button that
acknowledges the tap — it is absent, or it states plainly why it cannot happen.

Two concrete consequences, both of which this codebase previously violated:

- A call reported itself connected on the strength of signalling alone. It no longer
  does; see *Calling* below.
- The attachment sheet offered Location and Contact, which produced a toast and sent
  nothing. Those options are gone until the corresponding `InputMessageContent` is
  actually sent.

## Layers

```
TelegramClient                 TDLib gateway: one send(), one update stream
    │
    ├── TelegramMappers        TdApi types → Aether domain models
    ├── ChatOrdering           chat list positions, ordering, archive membership
    │
    ▼
Domain policies                what the account may actually do
    ├── MessageActionPolicy    from TDLib messageProperties
    ├── ChatActionPolicy       from chat type, rights and list membership
    └── CallsRepository        signalling and media, kept separate
    │
    ▼
ViewModels                     ChatsViewModel, ConversationViewModel, CallsViewModel
    │
    ▼
Compose UI                     renders the policy's answer, and nothing more
```

### One path per operation

Each operation has exactly one route from UI to TDLib. Calling is the worked example:
it previously had three independent entry points — `ConversationViewModel`,
`ProfileScreen` via `AppNavigation`, and `CallsViewModel` — of which only one passed
through `CallsRepository`. All three now go through `CallsRepository.initiateCall`,
which is also where the media-availability check lives. A second path would have been
a second place for that check to be forgotten.

## Message capability policy

`MessageActionPolicy` is the only thing that decides what a message offers. Its input
is `MessageCapabilities`, mapped directly from TDLib's `messageProperties` via
`GetMessageProperties`.

Nothing is inferred locally. In particular `message.isOutgoing` is **not** a licence
to edit or to delete for everyone: the server's answer already accounts for the edit
window, the account's rights in the chat, content protection and the message's age,
none of which are derivable from the message alone. Before properties arrive, the
policy offers nothing — an empty menu is correct while the answer is in flight.

Multi-selection takes the intersection of what every selected message supports, and
drops the actions that only make sense against one message (reply, edit, pin, link,
info).

## Chat capability policy

`ChatActionPolicy` resolves chat-list actions from the chat's type, its Telegram
rights (`canBeDeletedOnlyForSelf`, `canBeDeletedForAllUsers`), its list membership and
its notification settings.

It distinguishes operations that are commonly conflated:

| Action | TDLib operation | Scope |
| --- | --- | --- |
| Clear history | `DeleteChatHistory(removeFromChatList = false, revoke = false)` | messages only; chat stays in the list |
| Delete conversation | `DeleteChatHistory(removeFromChatList = true, revoke = false)` | this account only |
| Delete for everyone | `DeleteChatHistory(removeFromChatList = true, revoke = true)` | both sides, irreversible |
| Leave | `LeaveChat` | groups, supergroups, channels |
| Close secret chat | ends the encrypted session | secret chats only |

Each destructive action carries a confirmation that states its real scope. "Clear
history" explicitly says the conversation stays in the list, because a label that
implies otherwise costs the user something they cannot get back.

**Chat pinning and message pinning are different things** and share no code.
Chat-list pinning is `ToggleChatIsPinned(chatList, chatId, isPinned)`; message pinning
is `PinChatMessage` / `UnpinChatMessage`. Neither ever produces an outgoing message.

## Chat list

Ordering, badges, drafts, mute state, pinned state and archive membership all come
from TDLib. `ChatOrdering` reads `ChatPosition` for the main list and for archive
membership; Aether never invents an order or filters its own list to imitate an
archive. Drafts are stored server-side through `SetChatDraftMessage`, so text typed in
Aether and left unsent appears in Telegram's other clients.

## Calling: signalling is not audio

`TDLibCallState` and `MediaConnectionState` are deliberately separate types.

TDLib reaching `CallStateReady` means the two sides have agreed on servers and an
encryption key. It is **not** evidence that a single audio packet has flowed. Only
`MediaConnectionState.CONNECTED` means audio is running, and only the media transport
may report it.

The duration timer starts from `MediaConnectionState.CONNECTED`, never from
`CallStateReady`. Final call durations in history come from Telegram's own
`MessageCall`.

`MediaConnectionState.UNAVAILABLE` exists for the case where no media transport is
present in the build. In that state Aether refuses to place or accept the call and
says why, rather than ringing the other device for a conversation neither side could
hear.

Call history is `SearchCallMessages` against Telegram, so it survives restart because
it was never stored locally.

## Layer geometry and hit testing

Aether composes by sliding whole layers past one another. Every such layer carries
real controls, so `ui/design/AetherReveal.kt` enforces one invariant:

> A tap may only ever activate something the user can actually see at that coordinate.

`Modifier.aetherReveal` applies its vertical displacement as a **layout** offset, not
a draw-time `translationY`, so hit-test and semantics bounds follow the pixels. Once
opacity falls below `AetherReveal.InteractiveThreshold` the subtree is made inert:
pointer events are consumed on the `Initial` pass so descendant `clickable` nodes
never fire, and semantics are cleared so TalkBack cannot land on an invisible control.

A draw-only transform must never be used on an interactive moving container.
`SheetGhostHitTest` holds this invariant at every sheet anchor, including mid-settle.

## Permissions

Runtime permissions are requested contextually, at the moment the feature is used,
never at startup. Photos use the Android photo picker and documents use SAF, so no
broad storage permission is held. Calling adds `RECORD_AUDIO` only; Telegram calling
is internet VoIP and Aether holds none of `CALL_PHONE`, `READ_PHONE_STATE`,
`PROCESS_OUTGOING_CALLS` or `SYSTEM_ALERT_WINDOW`.

## Formatted text

`AetherText` carries a message's text together with the spans Telegram attached to
it. Offsets are UTF-16 code units, matching TDLib exactly — converting to code points
or characters silently breaks every message containing an emoji.

Entity types Aether has no representation for are **dropped**, not approximated: an
unmapped span renders as ordinary text, whereas guessing a style would misrepresent
what the sender wrote. Going the other way, only entities Aether itself created are
sent back; `Url`, `Mention`, `Hashtag` and friends are classified by the server, and
echoing them would assert a classification Aether never made.

Spoilers are revealed per reader, in composition only. Revealing is a reading choice,
never an edit, so it never touches the message on the server.

## Service messages

`ServiceMessages.isServiceEvent` is the single authority on whether a `MessageContent`
is a system event, and it is **exhaustive over the pinned API** rather than a list of
the types someone happened to encounter. A system event Aether has no bespoke wording
for still renders as a system event, described from its real type. Nothing that
happened *to* a chat can be drawn as something a person said.

## Search

In-chat search runs against `SearchChatMessages`, not over loaded rows, so a match a
thousand messages back is found. Paging uses TDLib's own `nextFromMessageId`; the
position label reports the server's total rather than the number paged in, so the
total does not appear to grow as the user steps through it.

Global search queries three endpoints — `SearchChats`, `SearchContacts` and
`SearchMessages` — concurrently and keeps their results in separate categories. Each
category clears its own loading flag, which is what lets "no results" stay honest:
it is claimed only once every source has answered.

Jumping to a message that is not loaded fetches the window around it with a negative
`GetChatHistory` offset, so the target lands mid-viewport rather than at the top.

## Known limitations

These are absent rather than faked. See the capability table in
`docs/architecture/capability-status.md`.
