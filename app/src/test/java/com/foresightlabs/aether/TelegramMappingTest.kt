package com.foresightlabs.aether

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
}
