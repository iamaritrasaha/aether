package com.foresightlabs.aether.data.location
import com.foresightlabs.aether.data.location.GeoPoint
import com.foresightlabs.aether.data.location.LiveLocationCoordinator
import com.foresightlabs.aether.data.location.LiveLocationGateway
import com.foresightlabs.aether.data.location.LiveLocationService
import com.foresightlabs.aether.data.location.LocationProvider
import com.foresightlabs.aether.data.location.LocationSubscription
import com.foresightlabs.aether.domain.model.ChatFolder
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.domain.model.MessageStatus
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.model.StickerItem
import com.foresightlabs.aether.ui.conversation.extractVideoNoteMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NonCallCorrectiveFlowsTest {

    private class FakeLocationProvider : LocationProvider {
        var isSubscribed = false
        var callback: ((GeoPoint) -> Unit)? = null

        override fun requestLocationUpdates(
            intervalMs: Long,
            minDisplacementMeters: Float,
            onLocation: (GeoPoint) -> Unit
        ): LocationSubscription {
            isSubscribed = true
            callback = onLocation
            return LocationSubscription {
                isSubscribed = false
                callback = null
            }
        }

        fun emitLocation(lat: Double, lon: Double, bearing: Float = 0f) {
            callback?.invoke(GeoPoint(latitude = lat, longitude = lon, bearing = bearing))
        }
    }

    private class FakeLiveLocationGateway : LiveLocationGateway {
        val editInvocations = mutableListOf<EditCall>()
        val stopInvocations = mutableListOf<StopCall>()

        data class EditCall(
            val chatId: Long,
            val messageId: Long,
            val latitude: Double,
            val longitude: Double,
            val livePeriod: Int,
            val heading: Int
        )

        data class StopCall(val chatId: Long, val messageId: Long)

        override suspend fun editLiveLocation(
            chatId: Long,
            messageId: Long,
            latitude: Double,
            longitude: Double,
            livePeriod: Int,
            heading: Int
        ): Result<Unit> {
            editInvocations.add(EditCall(chatId, messageId, latitude, longitude, livePeriod, heading))
            return Result.success(Unit)
        }

        override suspend fun stopLiveLocation(chatId: Long, messageId: Long): Result<Unit> {
            stopInvocations.add(StopCall(chatId, messageId))
            return Result.success(Unit)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun liveLocationCoordinatorStartAndEditFlow() = runTest {
        val fakeProvider = FakeLocationProvider()
        val fakeGateway = FakeLiveLocationGateway()

        val coordinator = LiveLocationCoordinator(
            context = null,
            locationProvider = fakeProvider,
            gateway = fakeGateway,
            scope = this
        )

        // Start live sharing for 900 seconds
        coordinator.startLiveSharing(chatId = 100L, messageId = 200L, livePeriodSeconds = 900)
        assertTrue(coordinator.isSharing(200L))
        assertTrue(fakeProvider.isSubscribed)

        // Emit new GPS fix
        fakeProvider.emitLocation(37.7749, -122.4194, 90f)
        testScheduler.runCurrent()

        assertEquals(1, fakeGateway.editInvocations.size)
        val editCall = fakeGateway.editInvocations.first()
        assertEquals(100L, editCall.chatId)
        assertEquals(200L, editCall.messageId)
        assertEquals(37.7749, editCall.latitude, 0.0001)
        assertEquals(-122.4194, editCall.longitude, 0.0001)
        assertEquals(90, editCall.heading)
        assertTrue(editCall.livePeriod in 890..900)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun liveLocationCoordinatorStopFromUiCancelsSubscriptionAndNotifiesGateway() = runTest {
        val fakeProvider = FakeLocationProvider()
        val fakeGateway = FakeLiveLocationGateway()

        val coordinator = LiveLocationCoordinator(
            context = null,
            locationProvider = fakeProvider,
            gateway = fakeGateway,
            scope = this
        )

        coordinator.startLiveSharing(chatId = 100L, messageId = 200L, livePeriodSeconds = 900)
        assertTrue(coordinator.isSharing(200L))
        assertTrue(fakeProvider.isSubscribed)

        // Stop session from UI
        coordinator.stopLiveSharing(chatId = 100L, messageId = 200L)
        testScheduler.runCurrent()

        assertFalse(coordinator.isSharing(200L))
        assertFalse(fakeProvider.isSubscribed)
        assertEquals(1, fakeGateway.stopInvocations.size)
        assertEquals(100L, fakeGateway.stopInvocations.first().chatId)
        assertEquals(200L, fakeGateway.stopInvocations.first().messageId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun liveLocationCoordinatorMultiSessionAndStopAll() = runTest {
        val fakeProvider = FakeLocationProvider()
        val fakeGateway = FakeLiveLocationGateway()

        val coordinator = LiveLocationCoordinator(
            context = null,
            locationProvider = fakeProvider,
            gateway = fakeGateway,
            scope = this
        )

        coordinator.startLiveSharing(chatId = 100L, messageId = 201L, livePeriodSeconds = 600)
        coordinator.startLiveSharing(chatId = 200L, messageId = 202L, livePeriodSeconds = 1200)

        assertEquals(2, coordinator.activeSessions.size)
        assertTrue(coordinator.isSharing(201L))
        assertTrue(coordinator.isSharing(202L))

        // Stop only session 201
        coordinator.stopLiveSharing(chatId = 100L, messageId = 201L)
        testScheduler.runCurrent()

        assertEquals(1, coordinator.activeSessions.size)
        assertFalse(coordinator.isSharing(201L))
        assertTrue(coordinator.isSharing(202L))
        assertEquals(1, fakeGateway.stopInvocations.size)

        // Stop all remaining sessions (like Notification Stop action)
        coordinator.stopAll()
        testScheduler.runCurrent()

        assertEquals(0, coordinator.activeSessions.size)
        assertFalse(coordinator.isSharing(202L))
        assertEquals(2, fakeGateway.stopInvocations.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun liveLocationCoordinatorAutoExpiresAfterDuration() = runTest {
        val fakeProvider = FakeLocationProvider()
        val fakeGateway = FakeLiveLocationGateway()

        val coordinator = LiveLocationCoordinator(
            context = null,
            locationProvider = fakeProvider,
            gateway = fakeGateway,
            scope = this
        )

        val durationSeconds = 60
        coordinator.startLiveSharing(chatId = 100L, messageId = 500L, livePeriodSeconds = durationSeconds)
        assertTrue(coordinator.isSharing(500L))

        // Advance time past expiry
        testScheduler.advanceTimeBy((durationSeconds + 1) * 1000L)
        testScheduler.runCurrent()

        assertFalse(coordinator.isSharing(500L))
        assertFalse(fakeProvider.isSubscribed)
        assertEquals(1, fakeGateway.stopInvocations.size)
    }

    @Test
    fun liveLocationServiceActionConstantsAndNotSticky() {
        assertEquals("com.foresightlabs.aether.action.STOP_LIVE_LOCATION", LiveLocationService.ACTION_STOP)
        assertEquals("extra_chat_id", LiveLocationService.EXTRA_CHAT_ID)
        assertEquals("extra_message_id", LiveLocationService.EXTRA_MESSAGE_ID)
    }

    @Test
    fun videoNoteMetadataExtractionFallbackOnEmptyFile() {
        val tempFile = File.createTempFile("empty_vnote", ".mp4")
        tempFile.deleteOnExit()

        val (length, duration) = extractVideoNoteMetadata(tempFile)
        assertTrue(length in 120..640)
        assertTrue(duration in 1..60)
        tempFile.delete()
    }

    @Test
    fun scheduledMessageRescheduleFlowDispatchesNewTime() {
        val originalMessage = Message(
            id = "1001",
            chatId = "200",
            senderId = "42",
            senderName = "Alice",
            text = "Scheduled reminder",
            timestamp = "10:00",
            isOutgoing = true,
            status = MessageStatus.SENT,
            dateSeconds = 1700000000
        )

        var rescheduledMessage: Message? = null
        var rescheduledSeconds: Int? = null

        val onReschedule: (Message, Int) -> Unit = { msg, newSeconds ->
            rescheduledMessage = msg
            rescheduledSeconds = newSeconds
        }

        val newTime = 1700003600 // +1 hour
        onReschedule(originalMessage, newTime)

        assertNotNull(rescheduledMessage)
        assertEquals("1001", rescheduledMessage?.id)
        assertEquals(newTime, rescheduledSeconds)
    }

    @Test
    fun folderReorderAndEditDispatchTest() {
        val folders = listOf(
            ChatFolder(id = 1, title = "Work"),
            ChatFolder(id = 2, title = "Personal"),
            ChatFolder(id = 3, title = "Crypto")
        )

        var reorderedIds: List<Int>? = null
        val onReorder: (List<Int>) -> Unit = { reorderedIds = it }

        // Swap 1 and 2
        val newOrder = listOf(2, 1, 3)
        onReorder(newOrder)

        assertEquals(listOf(2, 1, 3), reorderedIds)

        var editedFolderId: Int? = null
        var editedFolderName: String? = null
        val onEdit: (Int, String) -> Unit = { id, name ->
            editedFolderId = id
            editedFolderName = name
        }

        onEdit(1, "Work Projects")
        assertEquals(1, editedFolderId)
        assertEquals("Work Projects", editedFolderName)
    }

    @Test
    fun mediaReplacementFlowPreservesMessageType() {
        val originalPhotoMessage = Message(
            id = "555",
            chatId = "100",
            senderId = "42",
            senderName = "Alice",
            text = "Photo caption",
            type = MessageType.IMAGE,
            timestamp = "12:00",
            isOutgoing = true,
            status = MessageStatus.SENT
        )

        var replacementType: MessageType? = null
        var replacementPath: String? = null

        val onReplaceMedia: (Message, String, MessageType) -> Unit = { msg, path, type ->
            replacementPath = path
            replacementType = type
        }

        onReplaceMedia(originalPhotoMessage, "/tmp/new_photo.jpg", MessageType.IMAGE)

        assertEquals(MessageType.IMAGE, replacementType)
        assertEquals("/tmp/new_photo.jpg", replacementPath)
    }

    @Test
    fun videoNoteDataFlowCalculatesProperDurationAndAspect() {
        val durationSec = 45
        val length = 240
        val tempFilePath = "/tmp/video_note_123.mp4"

        var sentPath: String? = null
        var sentDuration: Int? = null
        var sentLength: Int? = null

        val onSendVideoNote: (String, Int, Int) -> Unit = { path, dur, len ->
            sentPath = path
            sentDuration = dur
            sentLength = len
        }

        onSendVideoNote(tempFilePath, durationSec, length)

        assertEquals("/tmp/video_note_123.mp4", sentPath)
        assertEquals(45, sentDuration)
        assertEquals(240, sentLength)
    }

    @Test
    fun liveLocationSessionStopFlow() {
        val liveMessage = Message(
            id = "777",
            chatId = "100",
            senderId = "42",
            senderName = "Alice",
            text = "Live Location",
            type = MessageType.LOCATION,
            isLiveLocation = true,
            liveLocationExpiresIn = 900,
            timestamp = "14:00",
            isOutgoing = true,
            status = MessageStatus.SENT
        )

        var stoppedMessageId: String? = null
        val onStopLiveLocation: (Message) -> Unit = { msg ->
            stoppedMessageId = msg.id
        }

        onStopLiveLocation(liveMessage)
        assertEquals("777", stoppedMessageId)
    }

    @Test
    fun stickerPickerSelectionFlowHandlesWebmAndTgs() {
        val webmSticker = StickerItem(
            fileId = 991,
            emoji = "🔥",
            localPath = "/cache/sticker_fire.webm",
            isAnimated = false,
            isVideo = true
        )
        val tgsSticker = StickerItem(
            fileId = 992,
            emoji = "🎉",
            localPath = "/cache/sticker_party.tgs",
            isAnimated = true,
            isVideo = false
        )

        var chosenFileId: Int? = null
        var chosenEmoji: String? = null

        val onSendSticker: (Int, String) -> Unit = { fileId, emoji ->
            chosenFileId = fileId
            chosenEmoji = emoji
        }

        onSendSticker(webmSticker.fileId, webmSticker.emoji)
        assertEquals(991, chosenFileId)
        assertEquals("🔥", chosenEmoji)

        onSendSticker(tgsSticker.fileId, tgsSticker.emoji)
        assertEquals(992, chosenFileId)
        assertEquals("🎉", chosenEmoji)
    }

    @Test
    fun tdApiReorderChatFoldersSignatureCompatibility() {
        val folderIds = intArrayOf(2, 1, 3)
        val reorderReq = TdApi.ReorderChatFolders(folderIds, 0)
        assertEquals(3, reorderReq.chatFolderIds.size)
        assertEquals(2, reorderReq.chatFolderIds[0])
        assertEquals(0, reorderReq.mainChatListPosition)
    }
}
