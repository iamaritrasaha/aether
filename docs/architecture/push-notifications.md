# Aether — background push notifications

Aether posts a notification when a message arrives while the app is not running.
Everything about *what* the notification says is Telegram's; everything about
*when the process is alive to say it* is Firebase's. This document records how
the two meet, and the one piece of the arrangement that does not live in this
repository at all.

## The path

```
Telegram server
  → FCM                              Telegram is the sender; Aether is the recipient
  → Google Play services
  → process created                  no Activity involved
  → AetherApplication.onCreate()     channels, TelegramClient, AetherNotificationManager
  → AetherFirebaseMessagingService.onMessageReceived()
  → PushPayloads                     the FCM data map, carried through verbatim
  → TelegramClient.processPushNotification()
  → PushDelivery                     routing, readiness, processing, waiting
  → TdApi.ProcessPushNotification    TDLib decrypts and decides what changed
  → UpdateNotificationGroup / UpdateNotification / UpdateActiveNotifications
  → NotificationWorkQueue            one update at a time, in arrival order
  → AetherNotificationManager
  → NotificationManager.notify()
```

Two properties of that path are load-bearing and are covered by tests rather than
by review:

**The callback does not return early.** Returning from `onMessageReceived` tells
Android the work is finished and the process may be frozen. TDLib answers `Ok`
once the updates a push caused have been *emitted*, which is not the same as
rendered — the rendering runs on its own coroutines. So the delivery waits for
that work to drain before returning. The whole delivery is bounded, so it waits
for as long as the work takes and never indefinitely.

**Notification updates are serialized.** TDLib emits its notification updates
back to back, and a push-woken process receives several within milliseconds.
Handling them concurrently lets two coroutines mutate one notification group's
state at once. They are queued and run in arrival order instead.

The one outcome that can genuinely outlast the callback's execution allowance is
TDLib's error 406 — the push did not carry enough and a live connection is
required to find out what changed. That is handed to a bounded WorkManager job
rather than waited on. It is the only continuation path a push has.

## Cold-process metadata

A process the push itself started has nothing cached. If TDLib's local database
cannot answer for the chat or the sender — plausible offline, and for a chat
that is new to this install — the notification is still built. Telegram encodes
what a chat is in its identifier (a private chat's id *is* the counterpart's
user id, and is positive; every group form is negative), and the push itself
carries the sender's name. That is enough to keep groups and channels secondary,
keep Saved Messages secondary, and keep Telegram's service account in its own
class with its lock-screen privacy intact.

What the identifier cannot say is whether the counterpart is a bot, so a bot's
message can reach a notification on this path where the full rule set would have
filed it as secondary content. That is the deliberate direction to be wrong in:
this is consulted only when a real message has already arrived and the
alternative is discarding it unseen. When the chat record *is* readable, the full
rule set applies unchanged.

A classification that cannot be made at all withholds the notification without
cancelling anything. Not knowing is never treated as a reason to clear what is
already on screen.

## What must be configured outside this repository

Telegram itself is the FCM sender. It cannot address a message to this
application without this project's Firebase credentials, and it will not accept a
device registration until it has them:

```
RegisterDevice → 400 APP_PUSH_APIKEY_MISSING
```

That error is Telegram's server answering, and no client change resolves it.
Telegram's push documentation states that an application key must be specified in
the application settings; the credential is attached to the Telegram application
behind the `TELEGRAM_API_ID` this build uses, on that application's configuration
page at [my.telegram.org](https://my.telegram.org).

Required, once, per Telegram application:

1. The Telegram application's platform must be one that receives push
   (Android/iOS). Platform is fixed when the `api_id` is created and cannot be
   changed afterwards.
2. A Firebase **service account JSON**, uploaded to that application's
   configuration page.
3. The service account must belong to the **same Firebase project** as the
   `app/google-services.json` this build ships. The device token is scoped to
   that project's sender, so credentials from any other project would produce
   registrations Telegram can address to nobody.
4. Firebase Cloud Messaging (v1) enabled for that project.

Until that is done, everything above still runs — it is simply never reached,
because Telegram never sends a push. Notifications continue to work normally
while the process is alive, since those come from TDLib's live connection rather
than from FCM.

Neither `app/google-services.json` nor `local.properties` is tracked here, and no
credential of any kind belongs in this repository.

## Confirming it works

Debug builds log the chain with safe markers only — stage, outcome, error class or
Telegram error symbol, field counts, timings. No token, credential, payload,
message text or login code is ever logged.

```shell
adb logcat -c && adb logcat -s AetherTd
```

A healthy registration:

```
FCM_TOKEN_AVAILABLE
REGISTER_DEVICE_REQUEST
REGISTER_DEVICE_SUCCESS
```

A healthy push, with the app not running:

```
FCM_ON_MESSAGE_RECEIVED
FCM_DATA_RECEIVED fieldCount=…
FCM_HANDOFF_TO_TDLIB
PROCESS_PUSH_STARTED
PUSH_PROCESS_OK
TDLIB_NOTIFICATION_GROUP_UPDATE …
ANDROID_NOTIFICATION_POSTED …
FCM_TDLIB_HANDOFF_COMPLETE outcome=PROCESSED durationMs=…
```

Test it by removing Aether from Recents and confirming the process is gone
(`adb shell pidof com.foresightlabs.aether` prints nothing), then sending a
message from another account. Do **not** test with Force stop: Android does not
deliver FCM messages to an application the user has force-stopped until the user
launches it again, and some manufacturer builds force-stop on a Recents swipe,
which has the same effect.
