package com.foresightlabs.aether

import com.foresightlabs.aether.data.telegram.ServiceMessages
import com.foresightlabs.aether.data.telegram.TelegramMappers
import com.foresightlabs.aether.domain.model.MessageType
import com.foresightlabs.aether.domain.text.AetherEntity
import com.foresightlabs.aether.domain.text.AetherText
import com.foresightlabs.aether.domain.text.EntityAction
import com.foresightlabs.aether.domain.text.EntityActions
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Formatted text and system events.
 *
 * Both were previously flattened: entities were dropped so every message rendered
 * as plain text, and every system event Aether had not been taught about was drawn
 * as an ordinary chat bubble — so "changed the group photo" looked like something
 * a person had said.
 */
class TextEntityAndServiceTest {

    private fun entity(offset: Int, length: Int, type: TdApi.TextEntityType) =
        TdApi.TextEntity(offset, length, type)

    // --- entity mapping ------------------------------------------------------

    @Test
    fun everyStylingEntityAetherSupportsSurvivesTheMapping() {
        val formatted = TdApi.FormattedText(
            "bold italic under strike spoil code",
            arrayOf(
                entity(0, 4, TdApi.TextEntityTypeBold()),
                entity(5, 6, TdApi.TextEntityTypeItalic()),
                entity(12, 5, TdApi.TextEntityTypeUnderline()),
                entity(18, 6, TdApi.TextEntityTypeStrikethrough()),
                entity(25, 5, TdApi.TextEntityTypeSpoiler()),
                entity(31, 4, TdApi.TextEntityTypeCode())
            )
        )

        val mapped = TelegramMappers.mapFormattedText(formatted)

        assertEquals(6, mapped.entities.size)
        assertTrue(mapped.entities[0] is AetherEntity.Bold)
        assertTrue(mapped.entities[1] is AetherEntity.Italic)
        assertTrue(mapped.entities[2] is AetherEntity.Underline)
        assertTrue(mapped.entities[3] is AetherEntity.Strikethrough)
        assertTrue(mapped.entities[4] is AetherEntity.Spoiler)
        assertTrue(mapped.entities[5] is AetherEntity.Code)
        assertTrue(mapped.hasSpoiler)
    }

    @Test
    fun entityPayloadsAreCarriedAcrossNotJustTheirTypes() {
        val formatted = TdApi.FormattedText(
            "click here or ask sam",
            arrayOf(
                entity(6, 4, TdApi.TextEntityTypeTextUrl("https://foresight.example")),
                entity(18, 3, TdApi.TextEntityTypeMentionName(99L))
            )
        )

        val mapped = TelegramMappers.mapFormattedText(formatted)

        val link = mapped.entities.filterIsInstance<AetherEntity.TextUrl>().single()
        assertEquals("https://foresight.example", link.url)
        val mention = mapped.entities.filterIsInstance<AetherEntity.MentionName>().single()
        assertEquals(99L, mention.userId)
    }

    @Test
    fun aPreCodeBlockKeepsItsLanguage() {
        val formatted = TdApi.FormattedText(
            "val x = 1",
            arrayOf(entity(0, 9, TdApi.TextEntityTypePreCode("kotlin")))
        )
        val pre = TelegramMappers.mapFormattedText(formatted)
            .entities.filterIsInstance<AetherEntity.Pre>().single()
        assertEquals("kotlin", pre.language)
    }

    @Test
    fun anEntityTypeAetherCannotRepresentIsDroppedRatherThanApproximated() {
        val formatted = TdApi.FormattedText(
            "see 12 May",
            arrayOf(entity(4, 6, TdApi.TextEntityTypeDateTime()))
        )
        assertTrue(TelegramMappers.mapFormattedText(formatted).entities.isEmpty())
    }

    @Test
    fun aZeroLengthEntityIsDiscarded() {
        val formatted = TdApi.FormattedText("hi", arrayOf(entity(0, 0, TdApi.TextEntityTypeBold())))
        assertTrue(TelegramMappers.mapFormattedText(formatted).entities.isEmpty())
    }

    @Test
    fun offsetsAreUtf16CodeUnitsSoEmojiDoNotShiftSpans() {
        // "👋 bold" — the wave is two UTF-16 units, so bold starts at index 3.
        val text = "👋 bold"
        val formatted = TdApi.FormattedText(text, arrayOf(entity(3, 4, TdApi.TextEntityTypeBold())))
        val mapped = TelegramMappers.mapFormattedText(formatted)
        val bold = mapped.entities.single()
        assertEquals("bold", text.substring(bold.offset, bold.end))
    }

    @Test
    fun entitiesSurviveTheRoundTripBackIntoTdlib() {
        val source = AetherText(
            "bold link",
            listOf(
                AetherEntity.Bold(0, 4),
                AetherEntity.TextUrl(5, 4, "https://example.com")
            )
        )
        val round = TelegramMappers.mapFormattedText(
            TdApi.FormattedText(source.text, TelegramMappers.toTdEntities(source))
        )
        assertEquals(source.entities, round.entities)
    }

    @Test
    fun serverDetectedEntitiesAreNotSentBackAsAssertions() {
        // Aether did not classify these; the server did. Echoing them would be
        // claiming a classification Aether never made.
        val source = AetherText("visit example.com", listOf(AetherEntity.Url(6, 11)))
        assertEquals(0, TelegramMappers.toTdEntities(source).size)
    }

    // --- entity actions ------------------------------------------------------

    @Test
    fun aBareHostIsGivenASchemeBeforeItIsOpened() {
        assertEquals("https://example.com", EntityActions.normaliseUrl("example.com"))
        assertEquals("http://example.com", EntityActions.normaliseUrl("http://example.com"))
        assertEquals("tg://resolve", EntityActions.normaliseUrl("tg://resolve"))
    }

    @Test
    fun aMentionResolvesToTheUsernameWithoutItsAtSign() {
        val action = EntityActions.resolve(AetherEntity.Mention(0, 4), "@sam hello")
        assertEquals(EntityAction.OpenUsername("sam"), action)
    }

    @Test
    fun aSpanWithNoActionResolvesToNothingRatherThanSomethingArbitrary() {
        assertNull(EntityActions.resolve(AetherEntity.Bold(0, 4), "bold"))
        assertNull(EntityActions.resolve(AetherEntity.Spoiler(0, 4), "spoi"))
    }

    @Test
    fun anEntityRunningPastTheEndOfTheTextDoesNotCrashResolution() {
        assertEquals(
            EntityAction.OpenUsername("sam"),
            EntityActions.resolve(AetherEntity.Mention(0, 400), "@sam")
        )
    }

    // --- service events ------------------------------------------------------

    @Test
    fun aChatPhotoChangeIsASystemEventNotAMessage() {
        val content = TdApi.MessageChatChangePhoto()
        assertTrue(ServiceMessages.isServiceEvent(content))
        assertEquals("Sam changed the chat photo", ServiceMessages.describe(content, "Sam"))
    }

    @Test
    fun aForumTopicEventIsASystemEvent() {
        val created = TdApi.MessageForumTopicCreated("Releases", false, null)
        assertTrue(ServiceMessages.isServiceEvent(created))
        assertTrue(ServiceMessages.describe(created, "Sam").contains("Releases"))

        val closed = TdApi.MessageForumTopicIsClosedToggled(true)
        assertEquals("Topic closed", ServiceMessages.describe(closed, "Sam"))
        assertEquals("Topic reopened", ServiceMessages.describe(TdApi.MessageForumTopicIsClosedToggled(false), "Sam"))
    }

    @Test
    fun anAutoDeleteChangeStatesWhetherItWasTurnedOnOrOff() {
        val on = TdApi.MessageChatSetMessageAutoDeleteTime(86400, 1L)
        val off = TdApi.MessageChatSetMessageAutoDeleteTime(0, 1L)
        assertTrue(ServiceMessages.describe(on, "Sam").contains("1 day"))
        assertTrue(ServiceMessages.describe(off, "Sam").contains("turned off"))
    }

    @Test
    fun aVideoChatEndingReportsItsRealDuration() {
        val ended = TdApi.MessageVideoChatEnded(3600)
        assertTrue(ServiceMessages.describe(ended, "Sam").contains("1 h"))
    }

    @Test
    fun anExpiredPhotoSaysTheContentIsGone() {
        assertTrue(ServiceMessages.isServiceEvent(TdApi.MessageExpiredPhoto()))
        assertEquals("Photo expired", ServiceMessages.describe(TdApi.MessageExpiredPhoto(), "Sam"))
    }

    @Test
    fun anOrdinaryMessageIsNotASystemEvent() {
        assertFalse(ServiceMessages.isServiceEvent(TdApi.MessageText()))
        assertFalse(ServiceMessages.isServiceEvent(TdApi.MessagePhoto()))
        assertFalse(ServiceMessages.isServiceEvent(TdApi.MessageVoiceNote()))
        assertFalse(ServiceMessages.isServiceEvent(null))
    }

    @Test
    fun anUnknownContentTypeNeverBecomesATextBubble() {
        // MessageUnsupported stands in for any type this build predates.
        val (_, type) = TelegramMappers.mapContent(TdApi.MessageUnsupported())
        assertNotEquals(MessageType.TEXT, type)
    }

    @Test
    fun theFallbackDescriptionIsDerivedFromTheRealTypeAndReadsAsAnEvent() {
        val described = ServiceMessages.fallbackDescription(TdApi.MessageChatBoost(1))
        assertEquals("Chat boost", described)
    }

    @Test
    fun everyServiceEventProducesNonBlankWording() {
        val events = listOf<TdApi.MessageContent>(
            TdApi.MessagePinMessage(),
            TdApi.MessageChatDeletePhoto(),
            TdApi.MessageChatOwnerLeft(),
            TdApi.MessageScreenshotTaken(),
            TdApi.MessageContactRegistered(),
            TdApi.MessageVideoChatStarted(1),
            TdApi.MessageSupergroupChatCreate("Team"),
            TdApi.MessageChatBoost(3),
            TdApi.MessageExpiredVoiceNote()
        )
        for (event in events) {
            assertTrue("${event.javaClass.simpleName} is not classified", ServiceMessages.isServiceEvent(event))
            assertTrue(
                "${event.javaClass.simpleName} produced blank wording",
                ServiceMessages.describe(event, "Sam").isNotBlank()
            )
        }
    }
}
