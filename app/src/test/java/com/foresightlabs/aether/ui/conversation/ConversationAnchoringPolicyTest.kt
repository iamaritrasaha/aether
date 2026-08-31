package com.foresightlabs.aether.ui.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationAnchoringPolicyTest {
    @Test
    fun normalComposerFocusFollowsLatest() {
        assertTrue(shouldAnchorComposerToLatest(true, false, false, false, false))
    }

    @Test
    fun replyEditSearchAndJumpPreserveContext() {
        assertFalse(shouldAnchorComposerToLatest(true, true, false, false, false))
        assertFalse(shouldAnchorComposerToLatest(true, false, true, false, false))
        assertFalse(shouldAnchorComposerToLatest(true, false, false, true, false))
        assertFalse(shouldAnchorComposerToLatest(true, false, false, false, true))
    }

    // --- shouldSettleOnComposerActivity: the "start typing settles to latest" trigger ---

    @Test
    fun firstTypedCharacterWhileReadingOldHistoryRequestsSettle() {
        // Scenario 1: normal composition, nothing settled yet this session, not
        // already at latest.
        assertTrue(
            shouldSettleOnComposerActivity(
                contextAllows = true,
                alreadySettledThisSession = false,
                alreadyNearLatest = false
            )
        )
    }

    @Test
    fun subsequentCharactersDoNotRepeatTheRequest() {
        // Scenario 2: same session, already settled once -- no repeat.
        assertFalse(
            shouldSettleOnComposerActivity(
                contextAllows = true,
                alreadySettledThisSession = true,
                alreadyNearLatest = false
            )
        )
    }

    @Test
    fun alreadyAtLatestPlusTypingDoesNothingRedundant() {
        // Scenario 3: nothing to settle to.
        assertFalse(
            shouldSettleOnComposerActivity(
                contextAllows = true,
                alreadySettledThisSession = false,
                alreadyNearLatest = true
            )
        )
    }

    @Test
    fun replyToOldMessagePreservesReplyContext() {
        // Scenario 4: reply/edit/search/jump all flow through contextAllows,
        // computed by shouldAnchorComposerToLatest -- false while replying.
        val contextAllows = shouldAnchorComposerToLatest(
            composerFocused = true,
            isReplying = true,
            isEditing = false,
            isSearching = false,
            hasJumpTarget = false
        )
        assertFalse(
            shouldSettleOnComposerActivity(
                contextAllows = contextAllows,
                alreadySettledThisSession = false,
                alreadyNearLatest = false
            )
        )
    }

    @Test
    fun editingOldMessagePreservesEditContext() {
        // Scenario 5.
        val contextAllows = shouldAnchorComposerToLatest(
            composerFocused = true,
            isReplying = false,
            isEditing = true,
            isSearching = false,
            hasJumpTarget = false
        )
        assertFalse(
            shouldSettleOnComposerActivity(
                contextAllows = contextAllows,
                alreadySettledThisSession = false,
                alreadyNearLatest = false
            )
        )
    }

    @Test
    fun explicitSearchOrJumpStateIsNotOverriddenByTyping() {
        // Scenario 6.
        val searching = shouldAnchorComposerToLatest(true, false, false, true, false)
        val jumping = shouldAnchorComposerToLatest(true, false, false, false, true)
        assertFalse(shouldSettleOnComposerActivity(searching, false, false))
        assertFalse(shouldSettleOnComposerActivity(jumping, false, false))
    }

    @Test
    fun newComposingSessionAfterLatestMessageChangeCanSettleAgain() {
        // Scenario 9: an incoming (or just-sent) message resets the session --
        // the caller clears alreadySettledThisSession when latestMessageId
        // changes, so the next typed character can settle again.
        val settledLastSession = true
        val sessionResetByNewLatestMessage = false
        assertFalse(shouldSettleOnComposerActivity(true, settledLastSession, false))
        assertTrue(shouldSettleOnComposerActivity(true, sessionResetByNewLatestMessage, false))
    }
}
