# ARM64 physical-device smoke test — Phase 1 (Living Home)

Everything in this file is **unverified**. Phase 1 was built and inspected on the JVM
only: this machine's sole emulator image is `x86_64` while Aether packages `arm64-v8a`
TDLib binaries, so the app has never been executed against a real Telegram session
during this work.

Run this on a physical `arm64-v8a` device, signed into a real Telegram account, before
Phase 2 is treated as safe to start.

**Device / build**

- Device, Android version, screen size and density: ______
- Gesture navigation or 3-button navigation: ______
- Build: `./gradlew :app:installDebug`
- Confirm the ABI actually loaded: no "Aether can't run on this device" screen at launch

---

## 1. Sheet dragging and fling physics

- [ ] Slow drag upward: the sheet tracks the finger 1:1 with no lag or rubber-banding
- [ ] Slow drag downward: same, in the opposite direction
- [ ] Release mid-travel with no velocity: settles to the **nearest** anchor
- [ ] Slow flick up from Resting: advances exactly one anchor, to Expanded
- [ ] Slow flick down from Expanded: advances exactly one anchor, to Resting
- [ ] Hard fling up from Peek: does not skip past Expanded or overshoot past the top
- [ ] Hard fling down from Expanded: stops at Peek, never below it
- [ ] Settle looks like mass, not a linear tween; no visible bounce
- [ ] Grab the sheet mid-settle: the animation is cancelled and the finger takes over
- [ ] Top corner radius interpolates ~34dp → ~22dp and never becomes a square rectangle
- [ ] Atmosphere behind the sheet stays spatially fixed — it must not shrink,
      stretch or accordion as the sheet moves
- [ ] Hero parallax and fade read as "something is covering it", not as a slide-away
- [ ] With **Remove animations** on in Developer options / Accessibility, anchor changes
      jump instantly and the typing indicator stops pulsing

## 2. Nested-scroll handoff, both directions

- [ ] From Resting, drag up over the **conversation list**: the sheet expands first,
      and only once fully expanded does the list begin to scroll
- [ ] Continue the same unbroken gesture: handoff happens mid-drag with no stutter,
      no dropped frames and no jump
- [ ] With the list scrolled down, drag down: the list returns to its top **first**,
      then continued downward gesture pulls the sheet down
- [ ] The handoff point is not "sticky" — no dead zone where neither moves
- [ ] Fling the list hard downward from mid-list: it settles at the top without
      yanking the sheet down with it
- [ ] Fling the list hard upward while the sheet is at Peek: sheet expands, then list
      continues; velocity is not lost at the boundary
- [ ] Drag from the sheet **handle** rather than the list: moves the sheet only

## 3. Horizontal filters vs vertical drag

- [ ] Swipe the People/Groups/Channels/Unread row horizontally: chips scroll and the
      sheet does **not** move vertically
- [ ] Diagonal swipe starting on the chip row: one axis wins cleanly, no jitter
- [ ] Vertical drag starting **on** the chip row: moves the sheet, not the chips
- [ ] Same checks on the Active Now strip (horizontal) against vertical hero drag
- [ ] Tapping a chip while the sheet is mid-settle selects the chip without
      interrupting the settle oddly

## 4. Gesture navigation and system insets

- [ ] Gesture nav: the dock sits clear of the home indicator, not under it
- [ ] 3-button nav: the dock sits clear of the nav bar
- [ ] The dock is reachable at every sheet anchor and never scrolls with the list
- [ ] Back gesture from the left/right edge does not fight the sheet drag
- [ ] Status bar area: hero content clears the notch / punch-hole / status bar
- [ ] Rotate to landscape: anchors re-derive, nothing is clipped, no crash
- [ ] Split-screen / freeform (if supported): anchors re-derive sanely
- [ ] Display size (Settings → Display → Display size) at largest: layout still holds

## 5. Search and keyboard expansion

- [ ] Tap the search field: the sheet expands **and** the field takes focus
- [ ] Tap the dock's search slot: same, from any anchor
- [ ] Keyboard opens without covering the field or the first results
- [ ] `adjustResize` actually resizes — results remain scrollable with the IME up
- [ ] Type a query: results filter live against real chats
- [ ] Clear button empties the query and restores the full list
- [ ] Dismiss the keyboard: layout returns without the sheet jumping anchors
- [ ] Back press with the keyboard up dismisses the IME first, not the screen

## 6. Real TDLib chats

- [ ] Chat list populates from the real account; counts match official Telegram
- [ ] Ordering matches Telegram's own main-list order, pinned chats included
- [ ] No chats missing and none duplicated after a full sync
- [ ] Unread counts and badges match the official client exactly
- [ ] Avatars load (real photos, not just initials) and do not flicker on scroll
- [ ] Live updates arrive: new message, read receipt, typing, title/photo change
- [ ] Opening a conversation, sending text and replying still work
- [ ] People / Groups / Channels / Unread filters classify every chat correctly
- [ ] Scroll a large history: no jank, no unbounded memory growth
- [ ] Airplane mode: connection pill shows a truthful state; recovery on reconnect
- [ ] Force-quit and relaunch: session persists, no re-authentication

## 7. Real presence

- [ ] Active Now lists only people Telegram genuinely reports as online right now
- [ ] Cross-check each avatar against the official client
- [ ] Groups, channels, bots, deleted accounts and Saved Messages never appear
- [ ] A contact going online/offline updates the strip live
- [ ] With every contact offline or privacy-limited, the row switches to
      **"Recently active"** — and no green dot or glow ring is drawn
- [ ] The words "Active now" never appear over approximate presence
- [ ] With no usable presence data at all, the row hides rather than inventing content
- [ ] Tapping an avatar opens that person's conversation

## 8. Automatic coarse-location weather

- [ ] First launch does **not** request location
- [ ] Appearance → Time + Weather requests **approximate** location only; the system
      dialog must not offer or imply precise location
- [ ] Grant it: a real condition resolves and the atmosphere modulates
- [ ] The condition matches actual local weather
- [ ] Refresh re-reads and clears any manual override
- [ ] Result is cached ~30 min; repeated visits do not re-request or re-fetch
- [ ] No continuous location updates while the app sits idle (check battery/location
      usage in Settings after ~30 minutes of foreground use)
- [ ] Privacy copy on screen matches what the code does, including that approximate
      coordinates are sent to Open-Meteo

## 9. Time-only weather fallback

- [ ] Deny location: UI states the reason and falls back to a time-only palette
- [ ] Location off system-wide: same, with a truthful message
- [ ] Airplane mode / no network: "Weather service unreachable", time-only palette
- [ ] Location granted but no fix available yet: truthful "no approximate location"
- [ ] In every fallback, no weather condition is displayed as if it were real
- [ ] Revoking permission from system settings mid-session degrades gracefully

## 10. Atmosphere-derived accents

Check at each of the five bands — force them via Appearance → Atmosphere palette, then
confirm at least one naturally on the clock:

- [ ] Dawn, Day, Golden Hour, Evening, Night each produce a distinct accent
- [ ] Day and Night read cool; Golden Hour reads warm. No orange in Day.
- [ ] Selected filter chip, unread badges, timestamps, typing indicator, search cursor,
      dock's selected slot and avatar rings all follow the same accent
- [ ] Nothing anywhere in the app stays ember-orange when the atmosphere is cool —
      including Conversation, Profile, Settings, Calls, Search and Auth
- [ ] Crossing a real band boundary (16:30 / 19:30 / 22:30 / 05:00 / 08:00) transitions
      over ~2.5s, smoothly, without a flash or a hard cut
- [ ] Leave the app open across a boundary: the palette changes on its own
- [ ] Appearance → "Follow atmosphere" vs a pinned accent behaves as labelled
- [ ] Foreground text stays readable over every atmosphere at every sheet position

## 11. Small-screen layout

Ideally on a genuinely small device; otherwise Display size = largest to approximate.

- [ ] Active Now is **present**, at compact density (smaller avatars, tighter spacing)
- [ ] No avatar is clipped by the sheet's top edge at any anchor
- [ ] Names are legible and truncate cleanly rather than wrapping or overlapping
- [ ] The strip still scrolls horizontally to reach further contacts
- [ ] At least one conversation row is usable at Resting; more when Expanded
- [ ] The strip hides only if there is genuinely no presence data
- [ ] Dock, chips and search remain fully tappable — nothing under 44dp

## 12. Font scaling

At system font sizes **small, default, large, largest**, and Aether's own
Appearance → Typography scale at 85% and 125% (and the two combined at their extremes):

- [ ] Greeting, date and Aether Daily scale and never clip
- [ ] Aether Daily wraps rather than truncating mid-thought
- [ ] Unread badges grow with their digits — no clipped numbers
- [ ] Filter chips grow and scroll; labels are never cut
- [ ] Conversation rows keep name and preview readable on one line each
- [ ] Sheet anchors re-derive as the hero grows; nothing overlaps
- [ ] Presence strip drops to compact density before it would ever be clipped
- [ ] Dock icons and labels stay within the pill

## 13. Accessibility pass

- [ ] TalkBack: every icon-only control announces a meaningful label
- [ ] TalkBack: the sheet exposes Expand / Balance / Collapse actions and they work
- [ ] TalkBack: Active Now avatars announce name plus "online now" or "recently active"
      matching what is drawn
- [ ] Focus order through hero → search → chips → list → dock is sensible
- [ ] All touch targets ≥ 44dp
- [ ] Text contrast holds over the brightest atmosphere (Dawn, Golden Hour)

## 14. Performance

- [ ] Fling a long chat list: no dropped frames (Developer options → Profile HWUI, or
      `adb shell dumpsys gfxinfo com.foresightlabs.aether`)
- [ ] Dragging the sheet does not spike jank; the list is not re-measured every frame
- [ ] Sitting idle on Home shows no continuous CPU/GPU work once transitions settle
- [ ] Memory stable after 10 minutes of scrolling and sheet interaction
- [ ] Battery: no measurable location or network drain while idle

---

## Known unverified beyond this checklist

- New-account registration with a genuinely unused phone number
- `AuthorizationStateWaitEmailAddress` / `WaitEmailCode`, which are truthfully reported
  as unsupported rather than implemented
- Everything in Phases 2–7, which are not started
