package com.foresightlabs.aether.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.BuildConfig
import com.foresightlabs.aether.MainActivity
import com.foresightlabs.aether.R
import com.foresightlabs.aether.domain.messaging.ConversationClass
import com.foresightlabs.aether.domain.messaging.ConversationFacts
import com.foresightlabs.aether.domain.messaging.TelegramIdentity
import com.foresightlabs.aether.domain.messaging.classifyConversation
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class AetherNotificationManager(
    private val context: Context,
    private val getChat: suspend (Long) -> TdApi.Chat?,
    private val getUser: suspend (Long) -> TdApi.User?,
    private val getMyUserId: () -> Long = { 0L }
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    // In-memory record of active notification groups: groupId -> GroupData
    private val activeGroups = ConcurrentHashMap<Int, GroupData>()

    data class ActiveItem(
        val notificationId: Int,
        val date: Int,
        val messageId: Long,
        val senderId: Long,
        val senderName: String,
        val text: String,
        val isOutgoing: Boolean,
        val isSilent: Boolean
    )

    data class GroupData(
        val groupId: Int,
        val chatId: Long,
        var notificationSoundId: Long,
        var totalCount: Int,
        val items: MutableMap<Int, ActiveItem> = mutableMapOf()
    )

    private fun hashChatId(chatId: Long): String = Integer.toHexString(chatId.hashCode())

    /**
     * What a chat is, for notification purposes, via the one canonical rule set in
     * [classifyConversation].
     *
     * Every lookup that fails yields [ConversationClass.UNKNOWN] rather than a
     * guess, and UNKNOWN is not deliverable -- so a chat or user TDLib could not
     * resolve still produces no notification, exactly as before.
     */
    suspend fun classifyChat(chatId: Long): ConversationClass {
        val chat = getChat(chatId)
        if (chat == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "NOTIFICATION_ELIGIBILITY chatHash=${hashChatId(chatId)} UNKNOWN reason=CHAT_NOT_FOUND")
            return ConversationClass.UNKNOWN
        }
        val counterpartUserId = when (val type = chat.type) {
            is TdApi.ChatTypePrivate -> type.userId
            is TdApi.ChatTypeSecret -> type.userId
            else -> null
        }
        if (counterpartUserId == null) {
            // Groups, supergroups and channels: secondary content by policy, and
            // no user lookup is meaningful for them.
            if (BuildConfig.DEBUG) Log.d(TAG, "NOTIFICATION_ELIGIBILITY chatHash=${hashChatId(chatId)} SECONDARY reason=NOT_ONE_TO_ONE")
            return ConversationClass.SECONDARY_TELEGRAM_CONTENT
        }
        // Checked before any user lookup: TDLib reports Telegram's service account
        // as a bot, so resolving the user first and classifying on that flag is
        // what used to hide login codes.
        if (counterpartUserId == TelegramIdentity.SERVICE_NOTIFICATIONS_USER_ID) {
            if (BuildConfig.DEBUG) Log.d(TAG, "NOTIFICATION_ELIGIBILITY chatHash=${hashChatId(chatId)} TELEGRAM_SERVICE")
            return ConversationClass.TELEGRAM_SERVICE
        }
        if (counterpartUserId == 0L) {
            if (BuildConfig.DEBUG) Log.d(TAG, "NOTIFICATION_ELIGIBILITY chatHash=${hashChatId(chatId)} UNKNOWN reason=NO_COUNTERPART_ID")
            return ConversationClass.UNKNOWN
        }
        val myId = getMyUserId()
        if (myId != 0L && counterpartUserId == myId) {
            if (BuildConfig.DEBUG) Log.d(TAG, "NOTIFICATION_ELIGIBILITY chatHash=${hashChatId(chatId)} SECONDARY reason=SAVED_MESSAGES")
            return ConversationClass.SECONDARY_TELEGRAM_CONTENT
        }
        val user = getUser(counterpartUserId)
        if (user == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "NOTIFICATION_ELIGIBILITY chatHash=${hashChatId(chatId)} UNKNOWN reason=USER_NOT_FOUND")
            return ConversationClass.UNKNOWN
        }
        val userType = user.type
        if (userType !is TdApi.UserTypeRegular && userType !is TdApi.UserTypeBot &&
            userType !is TdApi.UserTypeDeleted
        ) {
            // UserTypeUnknown, or a type this build has never been taught about.
            if (BuildConfig.DEBUG) Log.d(TAG, "NOTIFICATION_ELIGIBILITY chatHash=${hashChatId(chatId)} UNKNOWN reason=UNSUPPORTED_USER_TYPE")
            return ConversationClass.UNKNOWN
        }
        val result = classifyConversation(
            ConversationFacts(
                isOneToOne = true,
                counterpartUserId = counterpartUserId,
                isBot = userType is TdApi.UserTypeBot,
                isDeleted = userType is TdApi.UserTypeDeleted,
                isCounterpartKnown = true
            )
        )
        if (BuildConfig.DEBUG) Log.d(TAG, "NOTIFICATION_ELIGIBILITY chatHash=${hashChatId(chatId)} $result")
        return result
    }

    /**
     * Whether this chat is a 1:1 conversation with a real person.
     *
     * Telegram's service account is deliberately not one -- it is not a person --
     * but it is still delivered; see [isDeliverableChat].
     */
    suspend fun isPersonalHumanChat(chatId: Long): Boolean =
        classifyChat(chatId) == ConversationClass.PERSONAL_HUMAN

    /** Whether Aether posts an Android notification for this chat at all. */
    suspend fun isDeliverableChat(chatId: Long): Boolean = classifyChat(chatId).isDeliverable

    suspend fun onUpdateNotificationGroup(update: TdApi.UpdateNotificationGroup) {
        val groupId = update.notificationGroupId
        val chatId = update.chatId

        if (BuildConfig.DEBUG) {
            val addedIds = update.addedNotifications?.map { it.id } ?: emptyList()
            val removedIds = update.removedNotificationIds?.toList() ?: emptyList()
            Log.d(
                TAG,
                "TDLIB_NOTIFICATION_GROUP_UPDATE groupId=$groupId chatHash=${hashChatId(chatId)} addedIds=$addedIds removedIds=$removedIds totalCount=${update.totalCount} settingsChatIdHash=${hashChatId(update.notificationSettingsChatId)} soundId=${update.notificationSoundId} elapsedRealtime=${android.os.SystemClock.elapsedRealtime()}"
            )
        }
        NotificationTiming.markNotificationGroup(
            update.addedNotifications.orEmpty().mapNotNull { notification ->
                (notification.type as? TdApi.NotificationTypeNewMessage)?.message?.id
            }
        )

        // Aether product policy: Android notifications are personal 1:1 human conversations only
        if (!isDeliverableChat(chatId)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "ANDROID_NOTIFICATION_SUPPRESSED reason=NOT_DELIVERABLE_CONVERSATION chatHash=${hashChatId(chatId)} groupId=$groupId")
            cancelNotification(chatId, groupId, "NOT_DELIVERABLE_CONVERSATION")
            activeGroups.remove(groupId)
            updateSummaryNotification()
            return
        }

        // If the user is currently viewing this exact conversation in foreground, suppress it
        if (ActiveConversationTracker.shouldSuppressNotification(chatId)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "ANDROID_NOTIFICATION_SUPPRESSED reason=ACTIVE_FOREGROUND_CONVERSATION chatHash=${hashChatId(chatId)} groupId=$groupId")
            cancelNotification(chatId, groupId, "ACTIVE_FOREGROUND_CONVERSATION")
            activeGroups.remove(groupId)
            updateSummaryNotification()
            return
        }

        val group = activeGroups.getOrPut(groupId) {
            GroupData(
                groupId = groupId,
                chatId = chatId,
                notificationSoundId = update.notificationSoundId,
                totalCount = update.totalCount
            )
        }

        group.totalCount = update.totalCount
        group.notificationSoundId = update.notificationSoundId

        // Remove any dismissed / removed notifications
        update.removedNotificationIds?.forEach { removedId ->
            group.items.remove(removedId)
        }

        // Process added notifications
        update.addedNotifications?.forEach { notification ->
            val item = parseNotification(notification)
            if (item != null) {
                group.items[notification.id] = item
            }
        }

        if (group.items.isEmpty() || group.totalCount <= 0) {
            if (BuildConfig.DEBUG) Log.d(TAG, "ANDROID_NOTIFICATION_SUPPRESSED reason=EMPTY_GROUP_ITEMS chatHash=${hashChatId(chatId)} groupId=$groupId")
            cancelNotification(chatId, groupId, "EMPTY_GROUP_ITEMS")
            activeGroups.remove(groupId)
        } else {
            postGroupNotification(group)
        }

        updateSummaryNotification()
    }

    suspend fun onUpdateNotification(update: TdApi.UpdateNotification) {
        val groupId = update.notificationGroupId
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "TDLIB_NOTIFICATION_UPDATE notificationId=${update.notification.id} groupId=$groupId")
        }
        val group = activeGroups[groupId] ?: return

        if (!isDeliverableChat(group.chatId)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "ANDROID_NOTIFICATION_SUPPRESSED reason=NOT_DELIVERABLE_CONVERSATION chatHash=${hashChatId(group.chatId)} groupId=$groupId")
            cancelNotification(group.chatId, groupId, "NOT_DELIVERABLE_CONVERSATION")
            activeGroups.remove(groupId)
            updateSummaryNotification()
            return
        }

        if (ActiveConversationTracker.shouldSuppressNotification(group.chatId)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "ANDROID_NOTIFICATION_SUPPRESSED reason=ACTIVE_FOREGROUND_CONVERSATION chatHash=${hashChatId(group.chatId)} groupId=$groupId")
            cancelNotification(group.chatId, groupId, "ACTIVE_FOREGROUND_CONVERSATION")
            activeGroups.remove(groupId)
            updateSummaryNotification()
            return
        }

        val item = parseNotification(update.notification)
        if (item != null) {
            group.items[update.notification.id] = item
            postGroupNotification(group)
            updateSummaryNotification()
        }
    }

    suspend fun onUpdateActiveNotifications(update: TdApi.UpdateActiveNotifications) {
        val groups = update.groups ?: emptyArray()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "TDLIB_ACTIVE_NOTIFICATIONS_RECEIVED groupCount=${groups.size}")
        }

        val newGroupIds = groups.map { it.id }.toSet()

        // 1. Cancel and evict stale groups no longer active in TDLib
        val staleGroupIds = activeGroups.keys - newGroupIds
        for (staleGroupId in staleGroupIds) {
            val staleGroup = activeGroups.remove(staleGroupId)
            if (staleGroup != null) {
                cancelNotification(staleGroup.chatId, staleGroupId, "STALE_NOT_IN_TDLIB_ACTIVE_UPDATE")
            }
        }

        // 2. Reconcile active groups
        for (activeGroup in groups) {
            val groupId = activeGroup.id
            val chatId = activeGroup.chatId

            if (!isDeliverableChat(chatId)) {
                if (BuildConfig.DEBUG) Log.d(TAG, "ANDROID_NOTIFICATION_SUPPRESSED reason=NOT_DELIVERABLE_CONVERSATION chatHash=${hashChatId(chatId)} groupId=$groupId")
                cancelNotification(chatId, groupId, "NOT_DELIVERABLE_CONVERSATION")
                activeGroups.remove(groupId)
                continue
            }

            val group = activeGroups.getOrPut(groupId) {
                GroupData(
                    groupId = groupId,
                    chatId = chatId,
                    notificationSoundId = 0L,
                    totalCount = activeGroup.totalCount
                )
            }
            group.totalCount = activeGroup.totalCount

            val activeNotifIds = activeGroup.notifications?.map { it.id }?.toSet() ?: emptySet()
            group.items.keys.retainAll(activeNotifIds)

            activeGroup.notifications?.forEach { notification ->
                val item = parseNotification(notification)
                if (item != null) {
                    group.items[notification.id] = item
                }
            }

            if (group.items.isEmpty() || group.totalCount <= 0) {
                if (BuildConfig.DEBUG) Log.d(TAG, "ANDROID_NOTIFICATION_SUPPRESSED reason=EMPTY_GROUP_ITEMS chatHash=${hashChatId(chatId)} groupId=$groupId")
                cancelNotification(chatId, groupId, "EMPTY_GROUP_ITEMS")
                activeGroups.remove(groupId)
            } else if (ActiveConversationTracker.shouldSuppressNotification(chatId)) {
                if (BuildConfig.DEBUG) Log.d(TAG, "ANDROID_NOTIFICATION_SUPPRESSED reason=ACTIVE_FOREGROUND_CONVERSATION chatHash=${hashChatId(chatId)} groupId=$groupId")
                cancelNotification(chatId, groupId, "ACTIVE_FOREGROUND_CONVERSATION")
                activeGroups.remove(groupId)
            } else {
                postGroupNotification(group)
            }
        }
        updateSummaryNotification()
    }

    fun onConversationOpened(chatId: Long, topicId: Int? = null) {
        ActiveConversationTracker.setActiveConversation(chatId, topicId)
        // Find and cancel any active notification for this chat
        val groupsForChat = activeGroups.filterValues { it.chatId == chatId }
        groupsForChat.forEach { (groupId, _) ->
            cancelNotification(chatId, groupId, "CONVERSATION_OPENED")
            activeGroups.remove(groupId)
        }
        updateSummaryNotification()
    }

    fun onConversationClosed() {
        ActiveConversationTracker.setActiveConversation(null, null)
    }

    fun onNotificationDismissed(groupId: Int) {
        val group = activeGroups.remove(groupId)
        if (group != null) {
            cancelNotification(group.chatId, groupId, "NOTIFICATION_DISMISSED_BY_USER")
            updateSummaryNotification()
        }
    }

    fun clearAllNotifications() {
        activeGroups.clear()
        if (BuildConfig.DEBUG) Log.d(TAG, "ANDROID_NOTIFICATION_CANCEL_ALL")
        notificationManager.cancelAll()
    }

    private suspend fun parseNotification(notification: TdApi.Notification): ActiveItem? {
        val type = notification.type ?: return null
        return when (type) {
            is TdApi.NotificationTypeNewMessage -> {
                val msg = type.message ?: return null
                val text = NotificationContentMapper.mapMessageContent(msg.content, type.showPreview)
                val senderUser = (msg.senderId as? TdApi.MessageSenderUser)?.let { getUser(it.userId) }
                val senderName = senderUser?.let { "${it.firstName} ${it.lastName}".trim() }
                    ?.ifBlank { "User" } ?: "User"
                ActiveItem(
                    notificationId = notification.id,
                    date = notification.date,
                    messageId = msg.id,
                    senderId = (msg.senderId as? TdApi.MessageSenderUser)?.userId ?: 0L,
                    senderName = senderName,
                    text = text,
                    isOutgoing = msg.isOutgoing,
                    isSilent = notification.isSilent
                )
            }
            is TdApi.NotificationTypeNewPushMessage -> {
                val text = NotificationContentMapper.mapPushContent(type.content)
                val senderName = type.senderName.orEmpty().ifBlank { "User" }
                ActiveItem(
                    notificationId = notification.id,
                    date = notification.date,
                    messageId = type.messageId,
                    senderId = (type.senderId as? TdApi.MessageSenderUser)?.userId ?: 0L,
                    senderName = senderName,
                    text = text,
                    isOutgoing = type.isOutgoing,
                    isSilent = notification.isSilent
                )
            }
            is TdApi.NotificationTypeNewSecretChat -> {
                ActiveItem(
                    notificationId = notification.id,
                    date = notification.date,
                    messageId = 0L,
                    senderId = 0L,
                    senderName = "Secret Chat",
                    text = "New secret chat",
                    isOutgoing = false,
                    isSilent = notification.isSilent
                )
            }
            else -> null
        }
    }

    private suspend fun postGroupNotification(group: GroupData) {
        if (!notificationManager.areNotificationsEnabled()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "ANDROID_NOTIFICATIONS_DISABLED")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = systemNotificationManager?.getNotificationChannel(AetherApplication.CHANNEL_MESSAGES)
            if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "ANDROID_CHANNEL_DISABLED channelExists=${channel != null} importance=${channel?.importance}")
                }
                return
            }
        }

        val chatId = group.chatId
        val conversationClass = classifyChat(chatId)
        // Telegram's own account: delivered, but not treated as a conversation
        // with a person. It gets no reply affordance and no lock-screen preview.
        val isTelegramService = conversationClass == ConversationClass.TELEGRAM_SERVICE
        val chat = getChat(chatId)
        val chatTitle = chat?.title.orEmpty().ifBlank { "Telegram" }
        val isGroup = chat?.type is TdApi.ChatTypeBasicGroup || chat?.type is TdApi.ChatTypeSupergroup

        val allItems = group.items.values.sortedBy { it.date }
        if (allItems.isEmpty()) return
        // Reconciliation retains every active item in GroupData; only the recent
        // meaningful window is sent to MessagingStyle so expanded shade stays compact.
        val sortedItems = allItems.takeLast(MAX_PRESENTED_HISTORY)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "ANDROID_NOTIFICATION_BUILD groupId=${group.groupId} chatHash=${hashChatId(chatId)} itemCount=${sortedItems.size}")
        }

        val selfPerson = Person.Builder()
            .setName("You")
            .setKey("self")
            .build()

        val privateUser = (chat?.type as? TdApi.ChatTypePrivate)?.userId?.let { getUser(it) }
        val otherDisplayName = privateUser?.let { "${it.firstName} ${it.lastName}".trim() }
            ?.ifBlank { chatTitle } ?: chatTitle
        val otherPerson = Person.Builder()
            .setName(otherDisplayName)
            .setKey("private_user_${privateUser?.id ?: 0L}")
            .setIcon(
                NotificationAvatars.circularIcon(
                    photoPath = privateUser?.profilePhoto?.small?.local?.path,
                    displayName = otherDisplayName,
                    colorSeedId = privateUser?.id ?: 0L
                )
            )
            .build()
        // A long-lived conversation shortcut is what promotes a notification into
        // Android's Conversation section, and it is meant for someone you talk to.
        // Telegram's service account is not that: it cannot be replied to, so
        // publishing a launcher shortcut for it would offer a conversation that
        // does not exist. Personal chats get one; service does not.
        val shortcutId = if (isTelegramService) null else privateConversationShortcutId(chatId)
        if (shortcutId != null) {
            registerConversationShortcut(shortcutId, chatTitle, otherPerson, chat)
        }

        val messagingStyle = NotificationCompat.MessagingStyle(selfPerson)
            .setConversationTitle(if (isGroup) chatTitle else null)
            .setGroupConversation(isGroup)

        sortedItems.forEach { item ->
            val person = if (item.isOutgoing) {
                selfPerson
            } else if (privateUser != null && item.senderId == privateUser.id) {
                otherPerson
            } else {
                Person.Builder()
                    .setName(item.senderName)
                    .setKey("user_${item.senderId}")
                    .build()
            }
            val timestampMs = if (item.date > 0) item.date.toLong() * 1000L else System.currentTimeMillis()
            messagingStyle.addMessage(item.text, timestampMs, person)
        }

        val chatHashInt = abs(chatId.hashCode()) % 100000

        // Tap intent -> Open ConversationScreen for this chatId
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHAT_ID, chatId)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            chatHashInt * 10 + 1,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Delete intent -> user swiped away notification
        val maxNotificationId = sortedItems.maxOfOrNull { it.notificationId } ?: 0
        val deleteIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_NOTIFICATION_GROUP_ID, group.groupId)
            putExtra(EXTRA_MAX_NOTIFICATION_ID, maxNotificationId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            (abs(group.groupId.hashCode()) % 100000) * 10 + 2,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Reply action with RemoteInput
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply")
            .build()

        val latestMessageId = sortedItems.lastOrNull()?.messageId ?: 0L
        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_NOTIFICATION_GROUP_ID, group.groupId)
            putExtra(EXTRA_REPLY_TO_MESSAGE_ID, latestMessageId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            chatHashInt * 10 + 3,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_aether,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()

        // Mark as read action
        val messageIds = sortedItems.map { it.messageId }.filter { it != 0L }.toLongArray()
        val readIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_NOTIFICATION_GROUP_ID, group.groupId)
            putExtra(EXTRA_MESSAGE_IDS, messageIds)
        }
        val readPendingIntent = PendingIntent.getBroadcast(
            context,
            chatHashInt * 10 + 4,
            readIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val readAction = NotificationCompat.Action.Builder(
            0,
            "Mark as read",
            readPendingIntent
        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val isMuted = group.notificationSoundId == 0L
        val isAllSilent = sortedItems.all { it.isSilent }

        val builder = NotificationCompat.Builder(context, AetherApplication.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_aether)
            .setStyle(messagingStyle)
            .setContentIntent(tapPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .apply {
                if (shortcutId != null) {
                    setShortcutId(shortcutId)
                    setLocusId(LocusIdCompat(shortcutId))
                }
                // Direct Reply is offered only where a reply is actually valid.
                // Replying to Telegram's service account does nothing useful, so
                // the affordance is absent rather than present and inert.
                if (!isTelegramService) addAction(replyAction)
                addAction(readAction)
                if (isTelegramService) {
                    // Login codes and security notices must not be readable from a
                    // locked device. The full notification stays available once the
                    // device is unlocked; the public version the lock screen shows
                    // instead says only that something arrived.
                    setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    setPublicVersion(buildServicePublicVersion(tapPendingIntent))
                }
            }
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(if (isMuted || isAllSilent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setSilent(isMuted || isAllSilent)
            .setAutoCancel(true)

        if (useLegacyGrouping()) builder.setGroup(GROUP_KEY_MESSAGES)

        val tag = notificationTag(chatId)
        val id = group.groupId
        try {
            if (BuildConfig.DEBUG) Log.d(TAG, "ANDROID_NOTIFICATION_POST_ATTEMPT tag=$tag id=$id elapsedRealtime=${android.os.SystemClock.elapsedRealtime()}")
            notificationManager.notify(tag, id, builder.build())
            NotificationTiming.markPosted(sortedItems.map { it.messageId }, group.groupId)
            if (BuildConfig.DEBUG) Log.d(TAG, "ANDROID_NOTIFICATION_POSTED tag=$tag id=$id elapsedRealtime=${android.os.SystemClock.elapsedRealtime()}")
        } catch (e: SecurityException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "ANDROID_NOTIFICATION_POST_FAILED_SECURITY tag=$tag id=$id msg=${e.message} elapsedRealtime=${android.os.SystemClock.elapsedRealtime()}")
        }
    }

    private fun updateSummaryNotification() {
        // Android 12+ owns conversation organization once each notification has a
        // stable shortcut. The explicit summary is retained only for legacy shade
        // behavior where group children otherwise have no native organization.
        if (!useLegacyGrouping()) {
            notificationManager.cancel(TAG_SUMMARY, ID_SUMMARY)
            return
        }
        val unreadGroups = activeGroups.values.filter { it.items.isNotEmpty() }
        if (unreadGroups.size <= 1) {
            // No summary needed if 0 or 1 chat
            notificationManager.cancel(TAG_SUMMARY, ID_SUMMARY)
            return
        }

        if (!notificationManager.areNotificationsEnabled()) return

        val totalMessages = unreadGroups.sumOf { it.items.size }
        val totalChats = unreadGroups.size
        val summaryText = "$totalMessages new messages from $totalChats chats"

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            999999,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryNotification = NotificationCompat.Builder(context, AetherApplication.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_aether)
            .setContentTitle("Aether")
            .setContentText(summaryText)
            .setGroup(GROUP_KEY_MESSAGES)
            .setGroupSummary(true)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        try {
            notificationManager.notify(TAG_SUMMARY, ID_SUMMARY, summaryNotification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }
    }

    private fun cancelNotification(chatId: Long, groupId: Int, reason: String = "UNSPECIFIED") {
        val tag = notificationTag(chatId)
        if (BuildConfig.DEBUG) Log.d(TAG, "ANDROID_NOTIFICATION_CANCEL tag=$tag id=$groupId reason=$reason")
        notificationManager.cancel(tag, groupId)
    }

    /**
     * The lock-screen stand-in for a Telegram service notification.
     *
     * Carries no message text at all -- deliberately not a truncated or masked
     * version of it, because a login code is short enough that any partial
     * rendering risks showing the whole thing. Anyone looking at a locked screen
     * learns that Telegram sent a security message and nothing more.
     */
    private fun buildServicePublicVersion(tapPendingIntent: PendingIntent): android.app.Notification =
        NotificationCompat.Builder(context, AetherApplication.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_aether)
            .setContentTitle("Telegram")
            .setContentText(PUBLIC_SERVICE_MESSAGE_TEXT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(tapPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

    private fun notificationTag(chatId: Long): String = "aether_chat_$chatId"

    private fun useLegacyGrouping(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    private fun privateConversationShortcutId(chatId: Long): String = "private_chat_$chatId"

    private fun registerConversationShortcut(
        shortcutId: String,
        label: String,
        person: Person,
        chat: TdApi.Chat?
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHAT_ID, chat?.id ?: 0L)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(label)
            .setLongLived(true)
            .setIntent(intent)
            .setPerson(person)
            .setLocusId(LocusIdCompat(shortcutId))
            .build()
        runCatching { ShortcutManagerCompat.addDynamicShortcuts(context, listOf(shortcut)) }
    }

    companion object {
        private const val TAG = "AetherTd"

        const val GROUP_KEY_MESSAGES = "com.foresightlabs.aether.MESSAGES_GROUP"
        const val TAG_SUMMARY = "aether_messages_summary"
        const val ID_SUMMARY = 1000001

        const val EXTRA_CHAT_ID = "com.foresightlabs.aether.EXTRA_CHAT_ID"
        const val EXTRA_TOPIC_ID = "com.foresightlabs.aether.EXTRA_TOPIC_ID"
        const val EXTRA_NOTIFICATION_GROUP_ID = "com.foresightlabs.aether.EXTRA_NOTIFICATION_GROUP_ID"
        const val EXTRA_MAX_NOTIFICATION_ID = "com.foresightlabs.aether.EXTRA_MAX_NOTIFICATION_ID"
        const val EXTRA_MESSAGE_IDS = "com.foresightlabs.aether.EXTRA_MESSAGE_IDS"
        const val EXTRA_REPLY_TO_MESSAGE_ID = "com.foresightlabs.aether.EXTRA_REPLY_TO_MESSAGE_ID"

        const val ACTION_REPLY = "com.foresightlabs.aether.action.NOTIFICATION_REPLY"
        const val ACTION_MARK_READ = "com.foresightlabs.aether.action.NOTIFICATION_MARK_READ"
        const val ACTION_DISMISS = "com.foresightlabs.aether.action.NOTIFICATION_DISMISS"

        const val KEY_TEXT_REPLY = "key_text_reply"
        private const val MAX_PRESENTED_HISTORY = 4

        /** Generic lock-screen wording for Telegram service messages. Never a code. */
        const val PUBLIC_SERVICE_MESSAGE_TEXT = "Telegram security message"
    }
}
