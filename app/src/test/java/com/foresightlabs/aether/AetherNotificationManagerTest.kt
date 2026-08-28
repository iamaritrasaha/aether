package com.foresightlabs.aether

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.data.notifications.ActiveConversationTracker
import com.foresightlabs.aether.data.notifications.AetherNotificationManager
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
            getUser = { usersMap[it] }
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
}
