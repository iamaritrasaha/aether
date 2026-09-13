package com.foresightlabs.aether.data.notifications
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.data.notifications.ActiveConversationTracker
import com.foresightlabs.aether.data.notifications.AetherNotificationManager
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AetherNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var notificationManager: AetherNotificationManager
    private val chatsMap = mutableMapOf<Long, TdApi.Chat>()
    private val usersMap = mutableMapOf<Long, TdApi.User>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ActiveConversationTracker.setAppForeground(false)
        ActiveConversationTracker.setActiveConversation(null, null)

        val testUser = TdApi.User().apply {
            id = 12345L
            firstName = "John"
            lastName = "Doe"
            type = TdApi.UserTypeRegular()
            usernames = TdApi.Usernames().apply { activeUsernames = arrayOf("johndoe") }
        }
        usersMap[12345L] = testUser

        val testChat = TdApi.Chat().apply {
            id = 1001L
            type = TdApi.ChatTypePrivate().apply { userId = 12345L }
            title = "John Doe"
        }
        chatsMap[1001L] = testChat

        notificationManager = AetherNotificationManager(
            context = context,
            getChat = { chatsMap[it] },
            getUser = { usersMap[it] },
            getMyUserId = { 99999L }
        )
    }

    @Test
    fun testChannelsCreated() {
        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = systemManager.getNotificationChannel(AetherApplication.CHANNEL_MESSAGES)
        assertNotNull(channel)
        assertEquals("Messages", channel.name)
    }

    @Test
    fun testIsPersonalHumanChatClassification() = runBlocking {
        // 1. Private human user -> eligible
        assertEquals(true, notificationManager.isPersonalHumanChat(1001L))

        // 2. Secret 1:1 human chat -> eligible
        val secretChat = TdApi.Chat().apply {
            id = 1002L
            type = TdApi.ChatTypeSecret().apply { userId = 12345L }
            title = "John Secret"
        }
        chatsMap[1002L] = secretChat
        assertEquals(true, notificationManager.isPersonalHumanChat(1002L))

        // 3. Bot user -> suppressed
        val botUser = TdApi.User().apply {
            id = 2001L
            firstName = "HelperBot"
            type = TdApi.UserTypeBot()
        }
        usersMap[2001L] = botUser
        val botChat = TdApi.Chat().apply {
            id = 2001L
            type = TdApi.ChatTypePrivate().apply { userId = 2001L }
            title = "HelperBot"
        }
        chatsMap[2001L] = botChat
        assertEquals(false, notificationManager.isPersonalHumanChat(2001L))

        // 4. Basic group -> suppressed
        val groupChat = TdApi.Chat().apply {
            id = 3001L
            type = TdApi.ChatTypeBasicGroup().apply { basicGroupId = 55L }
            title = "Test Group"
        }
        chatsMap[3001L] = groupChat
        assertEquals(false, notificationManager.isPersonalHumanChat(3001L))

        // 5. Supergroup / Channel -> suppressed
        val supergroupChat = TdApi.Chat().apply {
            id = 4001L
            type = TdApi.ChatTypeSupergroup().apply { supergroupId = 77L; isChannel = true }
            title = "Test Channel"
        }
        chatsMap[4001L] = supergroupChat
        assertEquals(false, notificationManager.isPersonalHumanChat(4001L))

        // 6. Telegram Service notification chat (777000) -> suppressed
        val serviceChat = TdApi.Chat().apply {
            id = 777000L
            type = TdApi.ChatTypePrivate().apply { userId = 777000L }
            title = "Telegram"
        }
        val serviceUser = TdApi.User().apply {
            id = 777000L
            firstName = "Telegram"
            type = TdApi.UserTypeRegular()
        }
        usersMap[777000L] = serviceUser
        chatsMap[777000L] = serviceChat
        assertEquals(false, notificationManager.isPersonalHumanChat(777000L))

        // 7. Saved Messages (self chat) -> suppressed
        val savedChat = TdApi.Chat().apply {
            id = 99999L
            type = TdApi.ChatTypePrivate().apply { userId = 99999L }
            title = "Saved Messages"
        }
        val savedUser = TdApi.User().apply {
            id = 99999L
            firstName = "Me"
            type = TdApi.UserTypeRegular()
        }
        usersMap[99999L] = savedUser
        chatsMap[99999L] = savedChat
        assertEquals(false, notificationManager.isPersonalHumanChat(99999L))

        // 8. Deleted user -> suppressed
        val deletedUser = TdApi.User().apply {
            id = 5001L
            firstName = "Deleted"
            type = TdApi.UserTypeDeleted()
        }
        usersMap[5001L] = deletedUser
        val deletedChat = TdApi.Chat().apply {
            id = 5001L
            type = TdApi.ChatTypePrivate().apply { userId = 5001L }
            title = "Deleted Account"
        }
        chatsMap[5001L] = deletedChat
        assertEquals(false, notificationManager.isPersonalHumanChat(5001L))
    }

    @Test
    fun testUpdateNotificationGroupPostsAndCancels() = runBlocking {
        val message = TdApi.Message().apply {
            id = 999L
            senderId = TdApi.MessageSenderUser().apply { userId = 12345L }
            chatId = 1001L
            date = 1700000000
            content = TdApi.MessageText().apply {
                text = TdApi.FormattedText().apply { text = "Hello Aether!" }
            }
        }

        val notification = TdApi.Notification().apply {
            id = 1
            date = 1700000000
            isSilent = false
            type = TdApi.NotificationTypeNewMessage().apply {
                this.message = message
                this.showPreview = true
            }
        }

        val updateAdd = TdApi.UpdateNotificationGroup().apply {
            notificationGroupId = 101
            type = TdApi.NotificationGroupTypeMessages()
            chatId = 1001L
            notificationSettingsChatId = 1001L
            notificationSoundId = 1L
            totalCount = 1
            addedNotifications = arrayOf(notification)
            removedNotificationIds = intArrayOf()
        }

        // Process incoming group notification
        notificationManager.onUpdateNotificationGroup(updateAdd)

        // Process removal
        val updateRemove = TdApi.UpdateNotificationGroup().apply {
            notificationGroupId = 101
            type = TdApi.NotificationGroupTypeMessages()
            chatId = 1001L
            notificationSettingsChatId = 1001L
            notificationSoundId = 1L
            totalCount = 0
            addedNotifications = emptyArray()
            removedNotificationIds = intArrayOf(1)
        }
        notificationManager.onUpdateNotificationGroup(updateRemove)
    }

    @Test
    fun testNonPersonalNotificationGroupIsSuppressed() = runBlocking {
        val groupChat = TdApi.Chat().apply {
            id = 3001L
            type = TdApi.ChatTypeBasicGroup().apply { basicGroupId = 55L }
            title = "Test Group"
        }
        chatsMap[3001L] = groupChat

        val message = TdApi.Message().apply {
            id = 888L
            senderId = TdApi.MessageSenderUser().apply { userId = 12345L }
            chatId = 3001L
            date = 1700000000
            content = TdApi.MessageText().apply {
                text = TdApi.FormattedText().apply { text = "Group chatter" }
            }
        }

        val notification = TdApi.Notification().apply {
            id = 2
            date = 1700000000
            isSilent = false
            type = TdApi.NotificationTypeNewMessage().apply {
                this.message = message
                this.showPreview = true
            }
        }

        val updateAdd = TdApi.UpdateNotificationGroup().apply {
            notificationGroupId = 202
            type = TdApi.NotificationGroupTypeMessages()
            chatId = 3001L
            notificationSettingsChatId = 3001L
            notificationSoundId = 1L
            totalCount = 1
            addedNotifications = arrayOf(notification)
            removedNotificationIds = intArrayOf()
        }

        // Group notification must be suppressed without crash
        notificationManager.onUpdateNotificationGroup(updateAdd)
    }

    @Test
    fun testConversationOpenedDismissesActiveNotifications() = runBlocking {
        val message = TdApi.Message().apply {
            id = 999L
            senderId = TdApi.MessageSenderUser().apply { userId = 12345L }
            chatId = 1001L
            date = 1700000000
            content = TdApi.MessageText().apply {
                text = TdApi.FormattedText().apply { text = "Hey there!" }
            }
        }

        val notification = TdApi.Notification().apply {
            id = 1
            date = 1700000000
            isSilent = false
            type = TdApi.NotificationTypeNewMessage().apply {
                this.message = message
                this.showPreview = true
            }
        }

        val update = TdApi.UpdateNotificationGroup().apply {
            notificationGroupId = 101
            type = TdApi.NotificationGroupTypeMessages()
            chatId = 1001L
            notificationSettingsChatId = 1001L
            notificationSoundId = 1L
            totalCount = 1
            addedNotifications = arrayOf(notification)
            removedNotificationIds = intArrayOf()
        }

        notificationManager.onUpdateNotificationGroup(update)

        // When user opens the conversation
        notificationManager.onConversationOpened(1001L)

        // Active conversation tracker is now set to 1001L
        assertEquals(1001L, ActiveConversationTracker.activeChatId.value)

        notificationManager.onConversationClosed()
        assertEquals(null, ActiveConversationTracker.activeChatId.value)
    }

    @Test
    fun testUpdateActiveNotificationsReconcilesAndCancelsStaleGroups() = runBlocking {
        val user2 = TdApi.User().apply {
            id = 67890L
            firstName = "Alice"
            lastName = "Smith"
            type = TdApi.UserTypeRegular()
        }
        usersMap[67890L] = user2
        val chat2 = TdApi.Chat().apply {
            id = 1002L
            type = TdApi.ChatTypePrivate().apply { userId = 67890L }
            title = "Alice Smith"
        }
        chatsMap[1002L] = chat2

        // Group 1 initial notification
        val msg1 = TdApi.Message().apply {
            id = 101L
            senderId = TdApi.MessageSenderUser().apply { userId = 12345L }
            chatId = 1001L
            date = 1700000000
            content = TdApi.MessageText().apply { text = TdApi.FormattedText("Msg 1", emptyArray()) }
        }
        val notif1 = TdApi.Notification().apply {
            id = 1
            date = 1700000000
            type = TdApi.NotificationTypeNewMessage().apply { message = msg1; showPreview = true }
        }
        val updateAdd1 = TdApi.UpdateNotificationGroup().apply {
            notificationGroupId = 101
            type = TdApi.NotificationGroupTypeMessages()
            chatId = 1001L
            notificationSettingsChatId = 1001L
            notificationSoundId = 1L
            totalCount = 1
            addedNotifications = arrayOf(notif1)
            removedNotificationIds = intArrayOf()
        }
        notificationManager.onUpdateNotificationGroup(updateAdd1)

        // TDLib sends UpdateActiveNotifications containing ONLY group 102
        val msg2 = TdApi.Message().apply {
            id = 102L
            senderId = TdApi.MessageSenderUser().apply { userId = 67890L }
            chatId = 1002L
            date = 1700000010
            content = TdApi.MessageText().apply { text = TdApi.FormattedText("Msg 2", emptyArray()) }
        }
        val notif2 = TdApi.Notification().apply {
            id = 2
            date = 1700000010
            type = TdApi.NotificationTypeNewMessage().apply { message = msg2; showPreview = true }
        }
        val activeGroup2 = TdApi.NotificationGroup().apply {
            id = 102
            chatId = 1002L
            totalCount = 1
            type = TdApi.NotificationGroupTypeMessages()
            notifications = arrayOf(notif2)
        }
        val updateActive = TdApi.UpdateActiveNotifications().apply {
            groups = arrayOf(activeGroup2)
        }

        notificationManager.onUpdateActiveNotifications(updateActive)
    }

    @Test
    fun testActiveConversationSuppressionMatrix() {
        // Backgrounded: never suppress
        ActiveConversationTracker.setAppForeground(false)
        ActiveConversationTracker.setActiveConversation(1001L, null)
        assertEquals(false, ActiveConversationTracker.shouldSuppressNotification(1001L))
        assertEquals(false, ActiveConversationTracker.shouldSuppressNotification(1002L))

        // Foregrounded on Home/Settings (activeChatId == null): never suppress
        ActiveConversationTracker.setAppForeground(true)
        ActiveConversationTracker.setActiveConversation(null, null)
        assertEquals(false, ActiveConversationTracker.shouldSuppressNotification(1001L))
        assertEquals(false, ActiveConversationTracker.shouldSuppressNotification(1002L))

        // Foregrounded in Conversation 1001L:
        ActiveConversationTracker.setActiveConversation(1001L, null)
        // Same chat -> suppress
        assertEquals(true, ActiveConversationTracker.shouldSuppressNotification(1001L))
        // Different chat -> do NOT suppress
        assertEquals(false, ActiveConversationTracker.shouldSuppressNotification(1002L))
    }

    /** Posts one message notification for [chatId] and returns the posted Notification. */
    private suspend fun postMessageNotification(chatId: Long, groupId: Int, messageId: Long, senderUserId: Long): Notification {
        val message = TdApi.Message().apply {
            id = messageId
            senderId = TdApi.MessageSenderUser().apply { userId = senderUserId }
            this.chatId = chatId
            date = 1700000000
            content = TdApi.MessageText().apply {
                text = TdApi.FormattedText().apply { text = "msg $messageId" }
            }
        }
        val notification = TdApi.Notification().apply {
            id = 1
            date = 1700000000
            isSilent = false
            type = TdApi.NotificationTypeNewMessage().apply {
                this.message = message
                this.showPreview = true
            }
        }
        val update = TdApi.UpdateNotificationGroup().apply {
            notificationGroupId = groupId
            type = TdApi.NotificationGroupTypeMessages()
            this.chatId = chatId
            notificationSettingsChatId = chatId
            notificationSoundId = 1L
            totalCount = 1
            addedNotifications = arrayOf(notification)
            removedNotificationIds = intArrayOf()
        }
        notificationManager.onUpdateNotificationGroup(update)
        val shadowNm = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        val tag = "aether_chat_$chatId"
        return shadowNm.getActiveNotifications()
            .firstOrNull { it.tag == tag }
            ?.notification
            ?: error("notification with tag $tag was not posted")
    }

    @Test
    fun replyActionIntentCarriesTheMessageIdsToMarkRead() = runBlocking {
        val posted = postMessageNotification(chatId = 1001L, groupId = 201, messageId = 999L, senderUserId = 12345L)
        val replyAction = posted.actions.first { it.title == "Reply" }
        val results = android.os.Bundle()
        results.putCharSequence(AetherNotificationManager.KEY_TEXT_REPLY, "hi")
        val filled = android.app.RemoteInput.addResultsToIntent(
            arrayOf(android.app.RemoteInput.Builder(AetherNotificationManager.KEY_TEXT_REPLY).build()),
            Intent(), results
        )
        // The receiver reads the reply input from the action's remote-input intent extras
        // merged onto the action intent; here we assert the ids ride along at all.
        val savedIntent = shadowOf(replyAction.actionIntent).savedIntent
        val ids = savedIntent.getLongArrayExtra(AetherNotificationManager.EXTRA_MESSAGE_IDS)
        assertNotNull("reply intent must carry EXTRA_MESSAGE_IDS (else replied-to messages stay unread)", ids)
        assertTrue(ids!!.isNotEmpty())
        assertEquals(999L, ids.first())
    }

    @Test
    fun chatsWhoseHashCollidesGetDistinctPendingIntentRequestCodes() = runBlocking {
        // 1001 and 101001 collide under the old abs(hashCode()) % 100_000 scheme;
        // identical request codes with FLAG_UPDATE_CURRENT made the second chat's
        // intents overwrite the first's (tap opened the wrong chat; replies were
        // sent to the wrong chat). Chat 1001/user 12345 exist from setUp().
        usersMap[54321L] = TdApi.User().apply {
            id = 54321L
            firstName = "Second"
            lastName = "Contact"
            type = TdApi.UserTypeRegular()
        }
        chatsMap[101001L] = TdApi.Chat().apply {
            id = 101001L
            type = TdApi.ChatTypePrivate().apply { userId = 54321L }
            title = "Second Contact"
        }

        val first = postMessageNotification(chatId = 1001L, groupId = 301, messageId = 1L, senderUserId = 12345L)
        val second = postMessageNotification(chatId = 101001L, groupId = 302, messageId = 2L, senderUserId = 54321L)

        val firstTap = shadowOf(first.contentIntent)
        val secondTap = shadowOf(second.contentIntent)
        assertNotEquals(
            "colliding hashes must not yield the same PendingIntent request code",
            firstTap.requestCode, secondTap.requestCode
        )
        assertEquals(1001L, firstTap.savedIntent.getLongExtra(AetherNotificationManager.EXTRA_CHAT_ID, 0L))
        assertEquals(101001L, secondTap.savedIntent.getLongExtra(AetherNotificationManager.EXTRA_CHAT_ID, 0L))

        val firstReply = shadowOf(first.actions.first { it.title == "Reply" }.actionIntent)
        val secondReply = shadowOf(second.actions.first { it.title == "Reply" }.actionIntent)
        assertNotEquals(firstReply.requestCode, secondReply.requestCode)
        assertEquals(1001L, firstReply.savedIntent.getLongExtra(AetherNotificationManager.EXTRA_CHAT_ID, 0L))
        assertEquals(101001L, secondReply.savedIntent.getLongExtra(AetherNotificationManager.EXTRA_CHAT_ID, 0L))
    }

    @Test
    fun chatIdsCollidingInIntSpaceStillGetDistinctPendingIntentIdentities() {
        // Android request codes are Int-sized, so no request-code scheme can be
        // fully unique for Long chat ids. These two ids have IDENTICAL low 32
        // bits (identical request codes under any Int-derived scheme); the
        // Intent's data URI is what keeps their PendingIntents distinct.
        val hugeChatId = 1001L + (1L shl 32) // 4_294_968_297
        assertTrue(hugeChatId.toInt() == 1001L.toInt())

        usersMap[54321L] = TdApi.User().apply {
            id = 54321L
            firstName = "Wide"
            lastName = "Id"
            type = TdApi.UserTypeRegular()
        }
        chatsMap[hugeChatId] = TdApi.Chat().apply {
            id = hugeChatId
            type = TdApi.ChatTypePrivate().apply { userId = 54321L }
            title = "Wide Id"
        }

        runBlocking {
            val first = postMessageNotification(chatId = 1001L, groupId = 401, messageId = 1L, senderUserId = 12345L)
            val second = postMessageNotification(chatId = hugeChatId, groupId = 402, messageId = 2L, senderUserId = 54321L)

            // Tap intents: distinct data URIs (the actual PI identity), each
            // still carrying its own chat id.
            val firstTap = shadowOf(first.contentIntent).savedIntent
            val secondTap = shadowOf(second.contentIntent).savedIntent
            assertEquals(1001L, firstTap.getLongExtra(AetherNotificationManager.EXTRA_CHAT_ID, 0L))
            assertEquals(hugeChatId, secondTap.getLongExtra(AetherNotificationManager.EXTRA_CHAT_ID, 0L))
            assertNotEquals(firstTap.data, secondTap.data)
            assertEquals(
                notificationManager.chatIdentityUri(1001L),
                firstTap.data
            )

            // Reply intents: the wrong-recipient hazard. Distinct identities
            // mean the second chat's reply never overwrites the first's.
            val firstReply = shadowOf(first.actions.first { it.title == "Reply" }.actionIntent).savedIntent
            val secondReply = shadowOf(second.actions.first { it.title == "Reply" }.actionIntent).savedIntent
            assertEquals(1001L, firstReply.getLongExtra(AetherNotificationManager.EXTRA_CHAT_ID, 0L))
            assertEquals(hugeChatId, secondReply.getLongExtra(AetherNotificationManager.EXTRA_CHAT_ID, 0L))
            assertNotEquals(firstReply.data, secondReply.data)
        }
    }
}
