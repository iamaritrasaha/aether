package com.foresightlabs.aether

import com.foresightlabs.aether.data.telegram.ServiceMessages
import com.foresightlabs.aether.data.telegram.TelegramMappers
import com.foresightlabs.aether.domain.model.MessageType
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The capability sweep, expressed as a test.
 *
 * Walks every `MessageContent` subclass in the pinned API by reflection and requires
 * that each one is *classified* — as normal content or as a system event — and that
 * none produces blank or misleading presentation. The point is that a content type
 * cannot be accidentally forgotten: adding one to TDLib without teaching Aether about
 * it fails here rather than showing up as an empty bubble at runtime.
 */
class ContentClassificationSweepTest {

    /** Every concrete MessageContent subclass the pinned TdApi declares. */
    private fun allContentTypes(): List<Class<*>> =
        TdApi::class.java.declaredClasses
            .filter { TdApi.MessageContent::class.java.isAssignableFrom(it) }
            .filter { it != TdApi.MessageContent::class.java }
            .sortedBy { it.simpleName }

    private fun instantiate(type: Class<*>): TdApi.MessageContent? = runCatching {
        type.getDeclaredConstructor().newInstance() as TdApi.MessageContent
    }.getOrNull()

    @Test
    fun thePinnedApiStillDeclaresTheContentTypesThisBuildWasWrittenAgainst() {
        // A sanity floor: if this drops sharply the pinned revision changed under us.
        assertTrue(
            "Expected a large MessageContent hierarchy, found ${allContentTypes().size}",
            allContentTypes().size >= 90
        )
    }

    @Test
    fun everyContentTypeProducesANonBlankClassification() {
        val blank = mutableListOf<String>()
        for (type in allContentTypes()) {
            // A default-constructed MessageText carries no text, and an empty text
            // message is legitimately empty — that is content, not a gap.
            if (type == TdApi.MessageText::class.java) continue
            val content = instantiate(type) ?: continue
            val (text, _) = TelegramMappers.mapContent(content)
            if (text.isBlank()) blank += type.simpleName
        }
        assertTrue("These content types map to blank text: $blank", blank.isEmpty())
    }

    @Test
    fun noContentTypeIsSilentlyPresentedAsOrdinaryTextItIsNot() {
        // Only genuine text-bearing content may claim MessageType.TEXT.
        val textLike = setOf("MessageText", "MessageAnimatedEmoji")
        val offenders = mutableListOf<String>()
        for (type in allContentTypes()) {
            val content = instantiate(type) ?: continue
            val (_, mapped) = TelegramMappers.mapContent(content)
            if (mapped == MessageType.TEXT && type.simpleName !in textLike) {
                offenders += type.simpleName
            }
        }
        assertTrue("These are presented as plain text but are not: $offenders", offenders.isEmpty())
    }

    @Test
    fun everySystemEventIsClassifiedAsOneAndDescribed() {
        val undescribed = mutableListOf<String>()
        for (type in allContentTypes()) {
            val content = instantiate(type) ?: continue
            if (!ServiceMessages.isServiceEvent(content)) continue
            if (ServiceMessages.describe(content, "Sam").isBlank()) {
                undescribed += type.simpleName
            }
        }
        assertTrue("System events with no wording: $undescribed", undescribed.isEmpty())
    }

    @Test
    fun aSystemEventNeverMapsToAMessageBubbleType() {
        val offenders = mutableListOf<String>()
        for (type in allContentTypes()) {
            val content = instantiate(type) ?: continue
            if (!ServiceMessages.isServiceEvent(content)) continue
            val (_, mapped) = TelegramMappers.mapContent(content)
            if (mapped != MessageType.SERVICE) offenders += type.simpleName
        }
        assertTrue("System events rendered as messages: $offenders", offenders.isEmpty())
    }

    // --- the specific types this pass classified ------------------------------

    @Test
    fun aVideoChatMessageIsASystemEvent() {
        val call = TdApi.MessageGroupCall(1L, true, false, false, 0, emptyArray())
        assertTrue(ServiceMessages.isServiceEvent(call))
        assertTrue(ServiceMessages.describe(call, "Sam").contains("Voice chat"))
    }

    @Test
    fun pollAndChecklistEventsAreSystemEventsNotMessages() {
        val added = TdApi.MessagePollOptionAdded(
            1L, "a", TdApi.FormattedText("Tea", emptyArray())
        )
        assertTrue(ServiceMessages.isServiceEvent(added))
        assertTrue(ServiceMessages.describe(added, "Sam").contains("Tea"))

        val done = TdApi.MessageChecklistTasksDone(1L, intArrayOf(1), intArrayOf())
        assertTrue(ServiceMessages.isServiceEvent(done))
        assertEquals("Sam completed a task", ServiceMessages.describe(done, "Sam"))
    }

    @Test
    fun anAnimatedEmojiIsPresentedAsItsEmoji() {
        val content = TdApi.MessageAnimatedEmoji(null, "🎉")
        val presentation = TelegramMappers.mapPresentation(content, 1L, resolvePath = { null })
        assertEquals(MessageType.TEXT, presentation.type)
        assertEquals("🎉", presentation.text)
    }

    @Test
    fun aChecklistReportsItsRealProgress() {
        val checklist = TdApi.MessageChecklist(
            TdApi.Checklist(
                TdApi.FormattedText("Launch", emptyArray()),
                arrayOf(
                    TdApi.ChecklistTask(1, TdApi.FormattedText("a", emptyArray()), TdApi.MessageSenderUser(1L), 0),
                    TdApi.ChecklistTask(2, TdApi.FormattedText("b", emptyArray()), null, 0)
                ),
                false, false, false, false
            )
        )
        val presentation = TelegramMappers.mapPresentation(checklist, 1L, resolvePath = { null })
        assertEquals("Launch", presentation.text)
        assertEquals("1 of 2 done", presentation.fileSize)
    }

    @Test
    fun genuinelyUnsupportedContentSaysSoRatherThanPretending() {
        val (text, type) = TelegramMappers.mapContent(TdApi.MessageUnsupported())
        assertNotEquals(MessageType.TEXT, type)
        assertFalse(text.isBlank())
    }
}
