package com.foresightlabs.aether.data.notifications
import com.foresightlabs.aether.data.notifications.ActiveConversationTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActiveConversationTrackerTest {

    @Before
    fun setUp() {
        ActiveConversationTracker.setAppForeground(false)
        ActiveConversationTracker.setActiveConversation(null, null)
        ActiveConversationTracker.setPendingNavigationChatId(null)
    }

    @Test
    fun testBackgroundDoesNotSuppress() {
        ActiveConversationTracker.setAppForeground(false)
        ActiveConversationTracker.setActiveConversation(100L, null)

        // When in background, notifications are never suppressed
        assertFalse(ActiveConversationTracker.shouldSuppressNotification(100L))
        assertFalse(ActiveConversationTracker.shouldSuppressNotification(200L))
    }

    @Test
    fun testForegroundSameChatSuppresses() {
        ActiveConversationTracker.setAppForeground(true)
        ActiveConversationTracker.setActiveConversation(100L, null)

        // Active chat is 100L, so incoming message for 100L must be suppressed
        assertTrue(ActiveConversationTracker.shouldSuppressNotification(100L))

        // Different chat 200L must NOT be suppressed
        assertFalse(ActiveConversationTracker.shouldSuppressNotification(200L))
    }

    @Test
    fun testForegroundTopicLevelSuppression() {
        ActiveConversationTracker.setAppForeground(true)
        ActiveConversationTracker.setActiveConversation(100L, topicId = 5)

        // Same chat and same topic -> suppressed
        assertTrue(ActiveConversationTracker.shouldSuppressNotification(100L, topicId = 5))

        // Same chat but different topic -> NOT suppressed
        assertFalse(ActiveConversationTracker.shouldSuppressNotification(100L, topicId = 10))

        // Different chat -> NOT suppressed
        assertFalse(ActiveConversationTracker.shouldSuppressNotification(200L, topicId = 5))
    }

    @Test
    fun testPendingNavigationLifecycle() {
        assertNull(ActiveConversationTracker.pendingNavigationChatId.value)

        ActiveConversationTracker.setPendingNavigationChatId(555L)
        assertEquals(555L, ActiveConversationTracker.pendingNavigationChatId.value)

        val consumed = ActiveConversationTracker.consumePendingNavigationChatId()
        assertEquals(555L, consumed)
        assertNull(ActiveConversationTracker.pendingNavigationChatId.value)
    }
}
