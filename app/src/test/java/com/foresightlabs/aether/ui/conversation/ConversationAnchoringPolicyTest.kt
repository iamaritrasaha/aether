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
}
