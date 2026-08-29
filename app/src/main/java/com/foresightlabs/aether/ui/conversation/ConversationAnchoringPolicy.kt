package com.foresightlabs.aether.ui.conversation

/** Pure policy for deciding whether a normal composer focus may follow latest. */
fun shouldAnchorComposerToLatest(
    composerFocused: Boolean,
    isReplying: Boolean,
    isEditing: Boolean,
    isSearching: Boolean,
    hasJumpTarget: Boolean
): Boolean = composerFocused && !isReplying && !isEditing && !isSearching && !hasJumpTarget
