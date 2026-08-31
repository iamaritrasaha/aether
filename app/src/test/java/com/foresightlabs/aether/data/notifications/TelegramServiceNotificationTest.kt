package com.foresightlabs.aether.data.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.messaging.ConversationClass
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Telegram's service account must reach the user, and must do so without leaking a
 * login code to anyone holding the locked device or offering a reply that goes
 * nowhere.
 *
 * The regression these guard: service messages were previously suppressed
 * outright, because "not a human conversation" and "not worth delivering" were the
 * same boolean.
 */
@RunWith(AndroidJUnit4::class)
class TelegramServiceNotificationTest {

    private lateinit var context: Context
    private lateinit var manager: AetherNotificationManager
    private val chatsMap = mutableMapOf<Long, TdApi.Chat>()
    private val usersMap = mutableMapOf<Long, TdApi.User>()

    private val serviceChatId = 777000L
    private val personChatId = 1001L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ActiveConversationTracker.setAppForeground(false)
        ActiveConversationTracker.setActiveConversation(null, null)
        systemManager().cancelAll()

        // Telegram's service account, reported by TDLib as a bot -- which is
        // exactly why it used to be filtered out.
        usersMap[777000L] = TdApi.User().apply {
            id = 777000L
            firstName = "Telegram"
            type = TdApi.UserTypeBot()
        }
        chatsMap[serviceChatId] = TdApi.Chat().apply {
            id = serviceChatId
            type = TdApi.ChatTypePrivate().apply { userId = 777000L }
            title = "Telegram"
        }

        usersMap[12345L] = TdApi.User().apply {
            id = 12345L
            firstName = "John"
            lastName = "Doe"
            type = TdApi.UserTypeRegular()
        }
        chatsMap[personChatId] = TdApi.Chat().apply {
            id = personChatId
            type = TdApi.ChatTypePrivate().apply { userId = 12345L }
            title = "John Doe"
        }

        manager = AetherNotificationManager(
            context = context,
            getChat = { chatsMap[it] },
            getUser = { usersMap[it] },
            getMyUserId = { 99999L }
        )
    }

    private fun systemManager() =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun postMessage(chatId: Long, groupId: Int, senderUserId: Long, text: String) = runBlocking {
        val message = TdApi.Message().apply {
            id = 900L + groupId
            senderId = TdApi.MessageSenderUser().apply { userId = senderUserId }
            this.chatId = chatId
            date = 1700000000
            content = TdApi.MessageText().apply {
                this.text = TdApi.FormattedText().apply { this.text = text }
            }
        }
        val notification = TdApi.Notification().apply {
            id = groupId
            date = 1700000000
            isSilent = false
            type = TdApi.NotificationTypeNewMessage().apply {
                this.message = message
                this.showPreview = true
            }
        }
        manager.onUpdateNotificationGroup(
            TdApi.UpdateNotificationGroup().apply {
                notificationGroupId = groupId
                type = TdApi.NotificationGroupTypeMessages()
                this.chatId = chatId
                notificationSettingsChatId = chatId
                notificationSoundId = 1L
                totalCount = 1
                addedNotifications = arrayOf(notification)
                removedNotificationIds = intArrayOf()
            }
        )
    }

    private fun posted(groupId: Int): Notification? =
        systemManager().activeNotifications.firstOrNull { it.id == groupId }?.notification

    // --- classification -------------------------------------------------------

    @Test
    fun `the service account classifies as telegram service, not as a bot`() = runBlocking {
        assertEquals(ConversationClass.TELEGRAM_SERVICE, manager.classifyChat(serviceChatId))
        assertFalse("It is not a person", manager.isPersonalHumanChat(serviceChatId))
        assertTrue("But it must still be delivered", manager.isDeliverableChat(serviceChatId))
    }

    @Test
    fun `an unresolvable chat still fails closed`() = runBlocking {
        assertEquals(ConversationClass.UNKNOWN, manager.classifyChat(424242L))
        assertFalse(manager.isDeliverableChat(424242L))
    }

    // --- delivery -------------------------------------------------------------

    @Test
    fun `a service message is actually posted`() {
        postMessage(serviceChatId, groupId = 501, senderUserId = 777000L, text = "Login code: 24680")
        assertNotNull("Telegram service messages must not be filtered out", posted(501))
    }

    @Test
    fun `a service notification carries no direct reply action`() {
        postMessage(serviceChatId, groupId = 502, senderUserId = 777000L, text = "Login code: 24680")
        val actions = posted(502)?.actions.orEmpty()
        val hasRemoteInput = actions.any { !it.remoteInputs.isNullOrEmpty() }
        assertFalse("Replying to Telegram's service account does nothing", hasRemoteInput)
    }

    @Test
    fun `a personal message does carry a direct reply action`() {
        postMessage(personChatId, groupId = 503, senderUserId = 12345L, text = "hey")
        val actions = posted(503)?.actions.orEmpty()
        assertTrue(
            "Ordinary conversations keep Direct Reply",
            actions.any { !it.remoteInputs.isNullOrEmpty() }
        )
    }

    @Test
    fun `a service notification is private with a code-free public version`() {
        postMessage(serviceChatId, groupId = 504, senderUserId = 777000L, text = "Login code: 24680")
        val notification = posted(504)
        assertNotNull(notification)
        assertEquals(Notification.VISIBILITY_PRIVATE, notification!!.visibility)

        val public = notification.publicVersion
        assertNotNull("A lock screen needs something safe to show instead", public)
        val publicText = public!!.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertEquals(AetherNotificationManager.PUBLIC_SERVICE_MESSAGE_TEXT, publicText)
        assertFalse("The code must never reach the lock screen", publicText.contains("24680"))
    }

    @Test
    fun `a service notification publishes no conversation shortcut`() {
        postMessage(serviceChatId, groupId = 505, senderUserId = 777000L, text = "Login code: 24680")
        assertNull(
            "A shortcut would offer a conversation that cannot be held",
            posted(505)?.shortcutId
        )
    }

    @Test
    fun `a personal notification keeps its stable conversation shortcut`() {
        postMessage(personChatId, groupId = 506, senderUserId = 12345L, text = "hey")
        assertEquals("private_chat_$personChatId", posted(506)?.shortcutId)
    }

    @Test
    fun `separate chats stay separate notifications`() {
        postMessage(personChatId, groupId = 507, senderUserId = 12345L, text = "hey")
        postMessage(serviceChatId, groupId = 508, senderUserId = 777000L, text = "Login code: 13579")
        assertNotNull(posted(507))
        assertNotNull(posted(508))
    }
}
