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
  fun messageContentMapsTextAndUnsupported() {
    val text = TdApi.MessageText(TdApi.FormattedText("hello", emptyArray()), null, null)
    assertEquals("hello" to MessageType.TEXT, TelegramMappers.mapContent(text))
    val photo = TdApi.MessagePhoto()
    val mapped = TelegramMappers.mapContent(photo)
    assertEquals(MessageType.UNSUPPORTED, mapped.second)
  }

  @Test
  fun errorMessagesAreReadable() {
    val message = com.foresightlabs.aether.data.telegram.TdErrors.userMessage(
      TdApi.Error(400, "PHONE_CODE_INVALID")
    )
    assertTrue(message.contains("incorrect", ignoreCase = true))
  }
}
