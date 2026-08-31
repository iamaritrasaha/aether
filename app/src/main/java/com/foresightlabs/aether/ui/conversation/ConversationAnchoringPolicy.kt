package com.foresightlabs.aether.ui.conversation

/** Pure policy for deciding whether a normal composer focus may follow latest. */
fun shouldAnchorComposerToLatest(
    composerFocused: Boolean,
    isReplying: Boolean,
    isEditing: Boolean,
    isSearching: Boolean,
    hasJumpTarget: Boolean
): Boolean = composerFocused && !isReplying && !isEditing && !isSearching && !hasJumpTarget

/**
 * Whether a genuine draft mutation -- the user actually typing, not merely
 * focusing the field, and not a programmatic draft restore -- should settle
 * the Conversation to the latest messages.
 *
 * [contextAllows] is [shouldAnchorComposerToLatest]'s context gate (not
 * replying, not editing, not searching, no pending jump): those states
 * deliberately refer to another message location and must never be
 * overridden by typing. [alreadySettledThisSession] makes this fire once per
 * composing session rather than on every keystroke -- the caller sets it
 * after acting on `true` and clears it when the session ends (reply/edit
 * target changes, or the draft returns to empty). [alreadyNearLatest] avoids
 * a redundant scroll when there is nothing to settle to.
 */
fun shouldSettleOnComposerActivity(
    contextAllows: Boolean,
    alreadySettledThisSession: Boolean,
    alreadyNearLatest: Boolean
): Boolean = contextAllows && !alreadySettledThisSession && !alreadyNearLatest
