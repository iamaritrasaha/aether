package com.foresightlabs.aether.data.notifications

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * What the notification layer does when a push arrives in a process that has
 * just been started by that push, and TDLib cannot answer for the chat or the
 * sender yet.
 *
 * This is the one situation the live-process path never exercises: with the app
 * running, chats and users are already in memory, so every lookup succeeds. A
 * cold process has neither, and if the local database cannot answer offline the
 * lookups come back empty -- for exactly the message the push was sent to
 * announce.
 */
@RunWith(AndroidJUnit4::class)
class ColdProcessNotificationTest {

    private lateinit var context: Context
    private val chats = mutableMapOf<Long, TdApi.Chat>()
    private val users = mutableMapOf<Long, TdApi.User>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ActiveConversationTracker.setAppForeground(false)
        ActiveConversationTracker.setActiveConversation(null, null)
        chats.clear()
        users.clear()
    }

    private fun manager(myUserId: Long = 99999L) = AetherNotificationManager(
        context = context,
        getChat = { chats[it] },
        getUser = { users[it] },
        getMyUserId = { myUserId }
    )

    private fun posted(): List<Notification> {
        val shadow = shadowOf(
            context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        )
        return shadow.allNotifications
    }

    /**
     * A push-generated notification: TDLib produces NotificationTypeNewPushMessage
     * from the payload itself, which carries the sender's name and the message
     * content without needing anything from the local database.
     */
    private fun pushGroupUpdate(
        chatId: Long,
        senderUserId: Long,
        senderName: String,
        groupId: Int = 501
    ) = TdApi.UpdateNotificationGroup().apply {
        notificationGroupId = groupId
        type = TdApi.NotificationGroupTypeMessages()
        this.chatId = chatId
        notificationSettingsChatId = chatId
        notificationSoundId = 1L
        totalCount = 1
        addedNotifications = arrayOf(
            TdApi.Notification().apply {
                id = 1
                date = 1700000000
                isSilent = false
                type = TdApi.NotificationTypeNewPushMessage().apply {
                    messageId = 4242L
                    this.senderId = TdApi.MessageSenderUser().apply { userId = senderUserId }
                    this.senderName = senderName
                    isOutgoing = false
                    content = TdApi.PushMessageContentText("Are you around?", false)
                }
            }
        )
        removedNotificationIds = intArrayOf()
    }

    @Test
    fun a_personal_message_is_still_posted_when_the_chat_record_cannot_be_read() = runBlocking {
        // Neither the chat nor the user is in the local database: the process was
        // started by this push and TDLib has nothing cached for them.
        manager().onUpdateNotificationGroup(pushGroupUpdate(chatId = 12345L, senderUserId = 12345L, senderName = "Mira"))

        val notifications = posted()
        assertEquals("A real personal message must not be dropped", 1, notifications.size)
        val extras = notifications.single().extras
        assertEquals("Mira", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
    }

    @Test
    fun an_unreadable_chat_does_not_cancel_a_notification_that_is_already_showing() = runBlocking {
        val manager = manager()
        val chatId = 12345L
        chats[chatId] = TdApi.Chat().apply {
            id = chatId
            type = TdApi.ChatTypePrivate().apply { userId = chatId }
            title = "Mira"
        }
        users[chatId] = TdApi.User().apply {
            id = chatId
            firstName = "Mira"
            type = TdApi.UserTypeRegular()
        }
        manager.onUpdateNotificationGroup(pushGroupUpdate(chatId, chatId, "Mira"))
        assertEquals(1, posted().size)

        // The record becomes unreadable (a cold process handling a follow-up
        // update whose lookup fails). Not knowing must not mean cancelling.
        chats.remove(chatId)
        users.remove(chatId)
        manager.onUpdateNotificationGroup(
            pushGroupUpdate(chatId, chatId, "Mira", groupId = 501).apply {
                addedNotifications = arrayOf(
                    TdApi.Notification().apply {
                        id = 2
                        date = 1700000100
                        isSilent = false
                        type = TdApi.NotificationTypeNewPushMessage().apply {
                            messageId = 4243L
                            senderId = TdApi.MessageSenderUser().apply { userId = chatId }
                            senderName = "Mira"
                            isOutgoing = false
                            content = TdApi.PushMessageContentText("Still there?", false)
                        }
                    }
                )
            }
        )

        assertTrue("An unresolved lookup must never cancel a live notification", posted().isNotEmpty())
    }

    @Test
    fun a_group_chat_is_still_not_delivered_when_its_record_cannot_be_read() = runBlocking {
        // A supergroup id, in Telegram's own chat-id encoding. Aether's
        // people-first policy must hold even without the chat record.
        manager().onUpdateNotificationGroup(
            pushGroupUpdate(chatId = -1001234567890L, senderUserId = 12345L, senderName = "Mira")
        )

        assertTrue("Groups and channels stay secondary content", posted().isEmpty())
    }

    @Test
    fun saved_messages_are_still_not_delivered_when_the_record_cannot_be_read() = runBlocking {
        manager(myUserId = 55555L).onUpdateNotificationGroup(
            pushGroupUpdate(chatId = 55555L, senderUserId = 55555L, senderName = "Me")
        )

        assertTrue(posted().isEmpty())
    }

    @Test
    fun a_login_code_keeps_its_lock_screen_privacy_without_the_chat_record() = runBlocking {
        manager().onUpdateNotificationGroup(
            pushGroupUpdate(chatId = 777000L, senderUserId = 777000L, senderName = "Telegram")
        )

        val notification = posted().single()
        assertEquals(
            "Telegram service messages must stay off the lock screen",
            Notification.VISIBILITY_PRIVATE,
            notification.visibility
        )
        val publicVersion = notification.publicVersion
        assertTrue("A public stand-in must be provided", publicVersion != null)
        assertEquals(
            AetherNotificationManager.PUBLIC_SERVICE_MESSAGE_TEXT,
            publicVersion!!.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        )
        assertTrue(
            "The code itself must never appear in the public version",
            publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?.contains("Are you around?") != true
        )
    }

    @Test
    fun a_bot_is_still_filtered_when_its_record_can_be_read() = runBlocking {
        // The identifier fallback cannot see a bot flag, so this guards the
        // other direction: when the record *is* available, the full rule set
        // still applies and secondary content stays out of the shade.
        val botId = 2001L
        chats[botId] = TdApi.Chat().apply {
            id = botId
            type = TdApi.ChatTypePrivate().apply { userId = botId }
            title = "HelperBot"
        }
        users[botId] = TdApi.User().apply {
            id = botId
            firstName = "HelperBot"
            type = TdApi.UserTypeBot()
        }

        manager().onUpdateNotificationGroup(pushGroupUpdate(botId, botId, "HelperBot"))

        assertTrue("A resolvable bot stays secondary content", posted().isEmpty())
    }

    @Test
    fun a_message_whose_sender_cannot_be_resolved_is_still_posted() = runBlocking {
        val chatId = 12345L
        // The chat resolves, the counterpart does not -- a partially warm cache.
        chats[chatId] = TdApi.Chat().apply {
            id = chatId
            type = TdApi.ChatTypePrivate().apply { userId = chatId }
            title = "Mira"
        }

        manager().onUpdateNotificationGroup(pushGroupUpdate(chatId, chatId, "Mira"))

        assertEquals(1, posted().size)
    }
}
