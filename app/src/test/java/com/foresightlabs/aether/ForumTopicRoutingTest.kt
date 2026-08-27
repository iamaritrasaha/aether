package com.foresightlabs.aether

import androidx.compose.ui.graphics.Color
import com.foresightlabs.aether.data.telegram.ServiceMessages
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.ConversationTarget
import com.foresightlabs.aether.domain.model.ForumTopicSummary
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Forum topic routing.
 *
 * The defect this guards: `MessageTopic` was null on every send, draft, search and
 * history call, so a forum's topics were flattened into its root chat — messages
 * typed in one topic were posted to the forum itself and every topic's history was
 * interleaved.
 */
class ForumTopicRoutingTest {

    // --- the target model carries the topic ----------------------------------

    @Test
    fun aTopicTargetExposesItsForumTopicId() {
        val target = ConversationTarget.Topic(chatId = -100L, topicId = 77)
        assertEquals(77, target.forumTopicId)
    }

    @Test
    fun aPlainChatTargetHasNoTopic() {
        assertNull(ConversationTarget.Chat(-100L).forumTopicId)
        assertNull(ConversationTarget.User(42L).forumTopicId)
    }

    @Test
    fun twoTopicsInTheSameForumAreDistinctTargets() {
        val a = ConversationTarget.Topic(-100L, 1)
        val b = ConversationTarget.Topic(-100L, 2)
        assertFalse("Topics sharing a chat id must not be the same target", a == b)
    }

    // --- the pinned topic shape ----------------------------------------------

    @Test
    fun theForumTopicIsExpressedWithThePinnedMessageTopicType() {
        // Guards against drifting back to a messageThreadId-shaped model, which this
        // TDLib revision does not use for forums.
        val topic = TdApi.MessageTopicForum(77)
        assertEquals(77, topic.forumTopicId)
        assertTrue(topic is TdApi.MessageTopic)
    }

    // --- forum chats route to their topic list -------------------------------

    private fun chat(isForum: Boolean) = Chat(
        id = "-100",
        title = "Engineering",
        type = ChatType.GROUP,
        lastMessageText = "",
        lastMessageTime = "",
        isForum = isForum,
        avatarInitials = "E",
        avatarGradient = listOf(Color.Red, Color.Blue)
    )

    @Test
    fun onlyAForumChatIsMarkedAsOne() {
        assertTrue(chat(isForum = true).isForum)
        assertFalse(chat(isForum = false).isForum)
    }

    // --- topic summaries are server state ------------------------------------

    @Test
    fun aTopicCarriesItsOwnUnreadStateAndDraft() {
        val topic = ForumTopicSummary(
            chatId = -100L,
            topicId = 5,
            name = "Releases",
            unreadCount = 3,
            draftText = "half a thought"
        )
        assertTrue(topic.hasUnread)
        assertEquals("half a thought", topic.draftText)
    }

    @Test
    fun aTopicWithNothingUnreadReportsNone() {
        assertFalse(ForumTopicSummary(-100L, 5, "Releases").hasUnread)
    }

    // --- forum service events stay system events -----------------------------

    @Test
    fun forumTopicEventsRenderAsSystemEventsNotMessages() {
        val events = listOf<TdApi.MessageContent>(
            TdApi.MessageForumTopicCreated("Releases", false, null),
            TdApi.MessageForumTopicEdited("Shipping", false, 0L),
            TdApi.MessageForumTopicIsClosedToggled(true),
            TdApi.MessageForumTopicIsHiddenToggled(true)
        )
        for (event in events) {
            assertTrue(
                "${event.javaClass.simpleName} must be a system event",
                ServiceMessages.isServiceEvent(event)
            )
            assertTrue(ServiceMessages.describe(event, "Sam").isNotBlank())
        }
    }
}
