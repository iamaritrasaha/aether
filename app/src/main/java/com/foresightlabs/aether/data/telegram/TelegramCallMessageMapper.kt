package com.foresightlabs.aether.data.telegram

import com.foresightlabs.aether.domain.model.CallHistoryItem
import com.foresightlabs.aether.domain.model.CallOutcome
import com.foresightlabs.aether.domain.model.User
import org.drinkless.tdlib.TdApi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TelegramCallMessageMapper {

    fun mapToCallHistoryItem(
        message: TdApi.Message,
        user: User?
    ): CallHistoryItem? {
        val callContent = message.content as? TdApi.MessageCall ?: return null
        val duration = callContent.duration
        val isOutgoing = message.isOutgoing
        val outcome = mapOutcome(callContent.discardReason, isOutgoing, duration)

        val targetUserId = user?.id?.toLongOrNull() ?: message.chatId

        return CallHistoryItem(
            id = message.id.toString(),
            messageId = message.id,
            chatId = message.chatId,
            userId = targetUserId,
            user = user,
            isOutgoing = isOutgoing,
            isVideo = callContent.isVideo,
            outcome = outcome,
            durationSeconds = duration,
            timestampSeconds = message.date,
            formattedTimestamp = formatCallHistoryTimestamp(message.date),
            formattedDuration = formatDuration(duration)
        )
    }

    fun mapOutcome(discardReason: TdApi.CallDiscardReason?, isOutgoing: Boolean, duration: Int): CallOutcome {
        return when (discardReason) {
            is TdApi.CallDiscardReasonMissed -> if (isOutgoing) CallOutcome.CANCELLED else CallOutcome.MISSED
            is TdApi.CallDiscardReasonDeclined -> CallOutcome.DECLINED
            is TdApi.CallDiscardReasonDisconnected -> if (duration > 0) CallOutcome.COMPLETED else CallOutcome.FAILED
            is TdApi.CallDiscardReasonHungUp -> CallOutcome.COMPLETED
            is TdApi.CallDiscardReasonUpgradeToGroupCall -> CallOutcome.COMPLETED
            is TdApi.CallDiscardReasonEmpty, null -> if (duration > 0) CallOutcome.COMPLETED else if (isOutgoing) CallOutcome.CANCELLED else CallOutcome.MISSED
            else -> if (duration > 0) CallOutcome.COMPLETED else CallOutcome.MISSED
        }
    }

    fun formatCallMessagePresentation(call: TdApi.MessageCall, isOutgoing: Boolean): String {
        val callType = if (call.isVideo) "video call" else "voice call"
        val durationSec = call.duration
        val durationText = formatDuration(durationSec)

        val outcome = mapOutcome(call.discardReason, isOutgoing, durationSec)

        return when (outcome) {
            CallOutcome.MISSED -> "☎ Missed $callType"
            CallOutcome.CANCELLED -> "☎ Cancelled $callType"
            CallOutcome.DECLINED -> "☎ Declined $callType"
            CallOutcome.FAILED -> "☎ Failed $callType"
            CallOutcome.COMPLETED -> {
                val dir = if (isOutgoing) "Outgoing" else "Incoming"
                if (durationText.isNotBlank()) {
                    "☎ $dir $callType • $durationText"
                } else {
                    "☎ $dir $callType"
                }
            }
        }
    }

    fun formatDuration(durationSeconds: Int): String {
        if (durationSeconds <= 0) return ""
        val hrs = durationSeconds / 3600
        val mins = (durationSeconds % 3600) / 60
        val secs = durationSeconds % 60

        return when {
            hrs > 0 -> if (mins > 0) "${hrs} hr ${mins} min" else "${hrs} hr"
            mins > 0 -> if (secs > 0) "${mins} min ${secs} sec" else "${mins} min"
            else -> "${secs} sec"
        }
    }

    fun formatCallHistoryTimestamp(unixSeconds: Int): String {
        if (unixSeconds <= 0) return ""
        val date = Date(unixSeconds * 1000L)
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { time = date }

        val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)

        val isToday = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)

        val isYesterday = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) == 1

        return when {
            isToday -> "Today, $timeStr"
            isYesterday -> "Yesterday, $timeStr"
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) ->
                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
            else ->
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
        }
    }
}
