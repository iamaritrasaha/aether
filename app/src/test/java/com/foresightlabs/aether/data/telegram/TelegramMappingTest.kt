package com.foresightlabs.aether.data.telegram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.foresightlabs.aether.data.telegram.ChatOrdering
import com.foresightlabs.aether.data.telegram.TelegramMappers
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.MessageType
import org.drinkless.tdlib.TdApi

class TelegramMappingTest {
  @Test
  fun chatTypeMapsPrivateGroupChannelAndSaved() {
    val me = 42L
    assertEquals(ChatType.DIRECT, TelegramMappers.mapChatType(TdApi.ChatTypePrivate(7), me))
    assertEquals(ChatType.SAVED_MESSAGES, TelegramMappers.mapChatType(TdApi.ChatTypePrivate(me), me))
    assertEquals(ChatType.GROUP, TelegramMappers.mapChatType(TdApi.ChatTypeBasicGroup(1), me))
    assertEquals(ChatType.GROUP, TelegramMappers.mapChatType(TdApi.ChatTypeSupergroup(1, false), me))
    assertEquals(ChatType.CHANNEL, TelegramMappers.mapChatType(TdApi.ChatTypeSupergroup(1, true), me))
  }

  @Test
  fun chatOrderingUsesUnsignedOrder() {
    val high = -1L // unsigned max
    val low = 1L
    assertTrue(ChatOrdering.unsignedGreater(high, low))
    assertEquals(-1, ChatOrdering.compare(high, low))
  }

  @Test
  fun mainPositionPrefersMainList() {
    val main = TdApi.ChatPosition(TdApi.ChatListMain(), 99L, true, null)
    val archive = TdApi.ChatPosition(TdApi.ChatListArchive(), 500L, false, null)
    val mapped = ChatOrdering.mainPosition(arrayOf(archive, main))
    assertEquals(99L, mapped?.order)
    assertTrue(mapped?.isPinned == true)
  }

  // --- pin state and ordering come from TdApi.ChatPosition, not a local flag ----

  private fun chat(id: Long, positions: Array<TdApi.ChatPosition>) = TdApi.Chat().apply {
    this.id = id
    title = "Chat $id"
    type = TdApi.ChatTypePrivate(id)
    this.positions = positions
  }

  @Test
  fun aChatPinnedInTheMainListMapsToPinnedWithThatOrder() {
    val pinned = TdApi.ChatPosition(TdApi.ChatListMain(), -100L, true, null)
    val chat = TelegramMappers.mapChat(chat(1L, arrayOf(pinned)), myUserId = 999L, users = emptyMap())
    assertTrue(chat.isPinned)
    assertEquals(-100L, chat.order)
  }

  @Test
  fun aChatOnlyPinnedInAnotherListIsNotPinnedInTheMainList() {
    // Pinned in the archive, ordinary (unpinned) in the main list -- the main
    // list is the source of truth for Home, so this must not read as pinned.
    val archivePinned = TdApi.ChatPosition(TdApi.ChatListArchive(), -50L, true, null)
    val mainUnpinned = TdApi.ChatPosition(TdApi.ChatListMain(), 42L, false, null)
    val chat = TelegramMappers.mapChat(
      chat(2L, arrayOf(archivePinned, mainUnpinned)),
      myUserId = 999L,
      users = emptyMap()
    )
    assertTrue(!chat.isPinned)
    assertEquals(42L, chat.order)
  }

  @Test
  fun anUnpinnedChatWithNoMainListPositionIsNotPinned() {
    val chat = TelegramMappers.mapChat(chat(3L, arrayOf()), myUserId = 999L, users = emptyMap())
    assertTrue(!chat.isPinned)
    assertEquals(0L, chat.order)
  }

  @Test
  fun pinnedChatsSortAheadOfUnpinnedChatsByTheirTdlibOrder() {
    // TDLib assigns pinned chats an order high enough to sort above every
    // unpinned chat; Aether does not special-case isPinned when sorting -- it
    // trusts the single order value TDLib already pinned-adjusted.
    val pinned = TelegramMappers.mapChat(
      chat(1L, arrayOf(TdApi.ChatPosition(TdApi.ChatListMain(), -1L, true, null))),
      myUserId = 999L,
      users = emptyMap()
    )
    val unpinned = TelegramMappers.mapChat(
      chat(2L, arrayOf(TdApi.ChatPosition(TdApi.ChatListMain(), 1_000_000L, false, null))),
      myUserId = 999L,
      users = emptyMap()
    )

    val sorted = listOf(unpinned, pinned).sortedWith { a, b -> ChatOrdering.compare(a.order, b.order) }
    assertEquals(listOf(pinned.id, unpinned.id), sorted.map { it.id })
  }

  @Test
  fun unpinningRestoresTheChatToItsOrdinaryOrderPosition() {
    // Same chat, before and after an unpin: the position TDLib reports afterward
    // is what decides where it lands -- Aether never keeps its own placement.
    val whilePinned = TelegramMappers.mapChat(
      chat(1L, arrayOf(TdApi.ChatPosition(TdApi.ChatListMain(), -1L, true, null))),
      myUserId = 999L,
      users = emptyMap()
    )
    val afterUnpin = TelegramMappers.mapChat(
      chat(1L, arrayOf(TdApi.ChatPosition(TdApi.ChatListMain(), 500L, false, null))),
      myUserId = 999L,
      users = emptyMap()
    )
    val other = TelegramMappers.mapChat(
      chat(2L, arrayOf(TdApi.ChatPosition(TdApi.ChatListMain(), 800L, false, null))),
      myUserId = 999L,
      users = emptyMap()
    )

    assertTrue(whilePinned.isPinned)
    assertTrue(!afterUnpin.isPinned)

    val sorted = listOf(afterUnpin, other).sortedWith { a, b -> ChatOrdering.compare(a.order, b.order) }
    assertEquals(listOf(other.id, afterUnpin.id), sorted.map { it.id })
  }

  @Test
  fun authStatesMap() {
    assertTrue(TelegramMappers.mapAuthState(TdApi.AuthorizationStateWaitPhoneNumber()) is AuthUiState.Phone)
    assertTrue(TelegramMappers.mapAuthState(TdApi.AuthorizationStateReady()) is AuthUiState.Ready)
    val code = TdApi.AuthorizationStateWaitCode(
      TdApi.AuthenticationCodeInfo("+15551212", TdApi.AuthenticationCodeTypeSms(5), null, 60)
    )
    val mapped = TelegramMappers.mapAuthState(code) as AuthUiState.Code
    assertEquals(5, mapped.codeLength)
  }

  @Test
  fun emailAndRegistrationStatesKeepPinnedFields() {
    val email = TelegramMappers.mapAuthState(TdApi.AuthorizationStateWaitEmailAddress(true, false))
      as AuthUiState.EmailAddress
    assertTrue(email.allowAppleId)
    assertTrue(!email.allowGoogleId)

    val emailCode = TelegramMappers.mapAuthState(
      TdApi.AuthorizationStateWaitEmailCode(
        false,
        true,
        TdApi.EmailAddressAuthenticationCodeInfo("m•••@example.com", 7),
        TdApi.EmailAddressResetStateAvailable(0)
      )
    ) as AuthUiState.EmailCode
    assertEquals("m•••@example.com", emailCode.addressPattern)
    assertEquals(7, emailCode.codeLength)
    assertTrue(emailCode.canReset)

    val registration = TelegramMappers.mapAuthState(
      TdApi.AuthorizationStateWaitRegistration(
        TdApi.TermsOfService(TdApi.FormattedText("supplied terms", emptyArray()), 16, true)
      )
    ) as AuthUiState.Registration
    assertEquals("supplied terms", registration.termsOfServiceText)
    assertEquals(16, registration.minAge)
    assertTrue(registration.showPopup)
  }

  @Test
  fun pinnedAuthFunctionsExposeQrEmailRecoveryAndPasskeyContracts() {
    assertTrue(TdApi.RequestQrCodeAuthentication(longArrayOf()).otherUserIds.isEmpty())
    assertEquals("mail@example.com", TdApi.SetAuthenticationEmailAddress("mail@example.com").emailAddress)
    assertTrue(TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode("123456")).code is TdApi.EmailAddressAuthenticationCode)
    assertTrue(TdApi.RequestAuthenticationPasswordRecovery() is TdApi.RequestAuthenticationPasswordRecovery)
    assertTrue(TdApi.GetAuthenticationPasskeyParameters() is TdApi.GetAuthenticationPasskeyParameters)
  }

  @Test
  fun messageContentMapsTextAndUnsupported() {
    val text = TdApi.MessageText(TdApi.FormattedText("hello", emptyArray()), null, null)
    assertEquals("hello" to MessageType.TEXT, TelegramMappers.mapContent(text))
    val photo = TdApi.MessagePhoto()
    val mapped = TelegramMappers.mapContent(photo)
    assertEquals(MessageType.UNSUPPORTED, mapped.second)
  }

  @Test
  fun messageCallMapsToServiceEvent() {
    val callEnded = TdApi.MessageCall(12345L, false, TdApi.CallDiscardReasonHungUp(), 135)
    val (text, type) = TelegramMappers.mapContent(callEnded, isOutgoing = true)
    assertEquals(MessageType.CALL, type)
    assertTrue(text.contains("Outgoing voice call"))
    assertTrue(text.contains("2 min 15 sec"))

    val missedCall = TdApi.MessageCall(12346L, false, TdApi.CallDiscardReasonMissed(), 0)
    val (missedText, missedType) = TelegramMappers.mapContent(missedCall, isOutgoing = false)
    assertEquals(MessageType.CALL, missedType)
    assertTrue(missedText.contains("Missed voice call"))
  }

  @Test
  fun serviceMessagesMapToServiceType() {
    val pin = TdApi.MessagePinMessage()
    val (pinText, pinType) = TelegramMappers.mapContent(pin)
    assertEquals(MessageType.SERVICE, pinType)
    assertEquals("pinned a message", pinText)

    val join = TdApi.MessageChatAddMembers()
    val (joinText, joinType) = TelegramMappers.mapContent(join)
    assertEquals(MessageType.SERVICE, joinType)
    assertEquals("joined the chat", joinText)
  }

  @Test
  fun errorMessagesAreReadable() {
    val message = com.foresightlabs.aether.data.telegram.TdErrors.userMessage(
      TdApi.Error(400, "PHONE_CODE_INVALID")
    )
    assertTrue(message.contains("incorrect", ignoreCase = true))
  }

  // --- video messages remain VIDEO through the mapper, never coerced into IMAGE ---

  private fun tdFile(id: Int, localPath: String = "", downloaded: Boolean = false): TdApi.File =
    TdApi.File().apply {
      this.id = id
      local = TdApi.LocalFile().apply {
        path = localPath
        isDownloadingCompleted = downloaded
      }
      remote = TdApi.RemoteFile()
    }

  private fun tdVideo(
    thumbnailFileId: Int? = 11,
    videoFileId: Int = 22,
    duration: Int = 47,
    width: Int = 640,
    height: Int = 360
  ): TdApi.Video = TdApi.Video().apply {
    this.duration = duration
    this.width = width
    this.height = height
    thumbnail = thumbnailFileId?.let { id ->
      TdApi.Thumbnail().apply { file = tdFile(id) }
    }
    video = tdFile(videoFileId)
  }

  private fun tdPhoto(fileId: Int = 33): TdApi.Photo = TdApi.Photo().apply {
    sizes = arrayOf(TdApi.PhotoSize("x", tdFile(fileId), 640, 360, intArrayOf()))
  }

  @Test
  fun messageVideoMapsToDomainVideoType() {
    val content = TdApi.MessageVideo().apply { video = tdVideo() }
    val presentation = TelegramMappers.mapPresentation(content, messageId = 1L, resolvePath = { null })
    assertEquals(MessageType.VIDEO, presentation.type)
  }

  @Test
  fun messagePhotoMapsToDomainImageType() {
    val content = TdApi.MessagePhoto().apply { photo = tdPhoto() }
    val presentation = TelegramMappers.mapPresentation(content, messageId = 1L, resolvePath = { null })
    assertEquals(MessageType.IMAGE, presentation.type)
  }

  @Test
  fun videoIsNeverClassifiedAsPhoto() {
    val content = TdApi.MessageVideo().apply { video = tdVideo() }
    val presentation = TelegramMappers.mapPresentation(content, messageId = 1L, resolvePath = { null })
    assertTrue(presentation.type != MessageType.IMAGE)
  }

  @Test
  fun videoThumbnailPresenceDoesNotChangeTheMediaType() {
    val withThumbnail = TelegramMappers.mapPresentation(
      TdApi.MessageVideo().apply { video = tdVideo(thumbnailFileId = 11) },
      messageId = 1L,
      resolvePath = { null }
    )
    val withoutThumbnail = TelegramMappers.mapPresentation(
      TdApi.MessageVideo().apply { video = tdVideo(thumbnailFileId = null) },
      messageId = 2L,
      resolvePath = { null }
    )
    assertEquals(MessageType.VIDEO, withThumbnail.type)
    assertEquals(MessageType.VIDEO, withoutThumbnail.type)
  }

  @Test
  fun missingThumbnailDoesNotCrashAndStillProducesAPlayableVideoItem() {
    val content = TdApi.MessageVideo().apply { video = tdVideo(thumbnailFileId = null) }
    val presentation = TelegramMappers.mapPresentation(content, messageId = 1L, resolvePath = { null })

    assertEquals(1, presentation.mediaItems.size)
    val item = presentation.mediaItems.single()
    assertTrue(item.isVideo)
    assertEquals(22, item.videoFileId)
    // No thumbnail to load -- url is blank rather than pointing at the raw
    // video content, which is the exact coercion this mapper must not do.
    assertTrue(item.url.isBlank())
  }

  @Test
  fun remoteVideoContentIsNotEagerlyResolvedOnlyItsThumbnailIs() {
    // resolvePath is what triggers a download (see TelegramClient.resolveMediaPath);
    // the mapper must only ever call it for the thumbnail file, never for the
    // video's own content file, or every video in a conversation would start
    // downloading in full the moment its message loads.
    val resolvedFileIds = mutableListOf<Int>()
    val content = TdApi.MessageVideo().apply { video = tdVideo(thumbnailFileId = 11, videoFileId = 22) }

    TelegramMappers.mapPresentation(
      content,
      messageId = 1L,
      resolvePath = { file -> file?.id?.let { resolvedFileIds.add(it) }; null }
    )

    assertTrue(11 in resolvedFileIds)
    assertTrue(22 !in resolvedFileIds)
  }

  @Test
  fun downloadedVideoContentSurfacesAsItsLocalPath() {
    val content = TdApi.MessageVideo().apply {
      video = TdApi.Video().apply {
        duration = 10
        width = 100
        height = 100
        thumbnail = TdApi.Thumbnail().apply { file = tdFile(11) }
        video = tdFile(22, localPath = "/data/aether/video22.mp4", downloaded = true)
      }
    }
    val presentation = TelegramMappers.mapPresentation(content, messageId = 1L, resolvePath = { null })
    assertEquals("/data/aether/video22.mp4", presentation.mediaItems.single().videoLocalPath)
  }

  @Test
  fun aVideoNotYetDownloadedHasNoLocalPathButKeepsItsFileId() {
    val content = TdApi.MessageVideo().apply { video = tdVideo(videoFileId = 22) }
    val presentation = TelegramMappers.mapPresentation(content, messageId = 1L, resolvePath = { null })
    val item = presentation.mediaItems.single()
    assertEquals(22, item.videoFileId)
    assertTrue(item.videoLocalPath.isBlank())
  }

  @Test
  fun aVideoInsideAnAlbumKeepsItsOwnVideoTypeAlongsideAPhotoSibling() {
    val photoPresentation = TelegramMappers.mapPresentation(
      TdApi.MessagePhoto().apply { photo = tdPhoto(fileId = 33) },
      messageId = 1L,
      resolvePath = { null }
    )
    val videoPresentation = TelegramMappers.mapPresentation(
      TdApi.MessageVideo().apply { video = tdVideo() },
      messageId = 2L,
      resolvePath = { null }
    )
    // Album grouping (MessageGrouping.kt) only clusters already-mapped Messages
    // by their shared mediaAlbumId; it never re-derives or merges their types.
    assertEquals(MessageType.IMAGE, photoPresentation.type)
    assertEquals(MessageType.VIDEO, videoPresentation.type)
  }
}
