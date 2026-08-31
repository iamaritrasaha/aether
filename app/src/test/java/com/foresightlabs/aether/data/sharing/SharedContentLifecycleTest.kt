package com.foresightlabs.aether.data.sharing

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.domain.sharing.ShareRecipients
import com.foresightlabs.aether.domain.sharing.SharedContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * A share between arriving and being sent.
 *
 * The lifecycle claims worth holding to: an Activity recreation must not present
 * the same share twice, navigating from recipient selection into a conversation
 * must not lose it, and only the conversation it was addressed to may collect it.
 * Plus the two things that decide what can be done with it -- who may receive it,
 * and whether its bytes can actually be read.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SharedContentLifecycleTest {

    @Before
    fun setUp() {
        SharedContentInbox.reset()
    }

    // --- duplicate delivery -------------------------------------------------

    @Test
    fun theSameIntentRedeliveredIsAcceptedOnlyOnce() {
        fun share() = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "hello")
        }

        val first = SharedContentInbox.offer(
            SharedIntents.normalize(share()),
            SharedIntents.identityOf(share())
        )
        // Activity recreation hands the very same share back.
        val second = SharedContentInbox.offer(
            SharedIntents.normalize(share()),
            SharedIntents.identityOf(share())
        )

        assertTrue("The first arrival is the share", first)
        assertFalse("A redelivered share must not be presented again", second)
        assertEquals(SharedContent.Text("hello"), SharedContentInbox.pending.value)
    }

    @Test
    fun aGenuinelyNewShareIsAccepted() {
        val one = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "first")
        }
        val two = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "second")
        }

        SharedContentInbox.offer(SharedIntents.normalize(one), SharedIntents.identityOf(one))
        val accepted = SharedContentInbox.offer(SharedIntents.normalize(two), SharedIntents.identityOf(two))

        assertTrue(accepted)
        assertEquals(SharedContent.Text("second"), SharedContentInbox.pending.value)
    }

    @Test
    fun nothingDeliverableIsNeverAccepted() {
        assertFalse(SharedContentInbox.offer(null, "identity"))
        assertNull(SharedContentInbox.pending.value)
    }

    // --- surviving the way to the conversation ------------------------------

    @Test
    fun aShareSurvivesTheWayIntoTheConversationItWasAddressedTo() {
        SharedContentInbox.offer(SharedContent.Text("https://example.com"), "id")
        assertNotNull(SharedContentInbox.pending.value)

        SharedContentInbox.addressTo(chatId = 4242L)

        // Recipient selection is done with it; the conversation now holds it.
        assertNull(SharedContentInbox.pending.value)
        assertEquals(4242L, SharedContentInbox.delivery.value?.chatId)

        assertNull("Another conversation must not collect it", SharedContentInbox.consumeDelivery(99L))
        assertEquals(
            SharedContent.Text("https://example.com"),
            SharedContentInbox.consumeDelivery(4242L)
        )
    }

    @Test
    fun aCollectedShareIsNotPresentedAgain() {
        SharedContentInbox.offer(SharedContent.Text("once"), "id")
        SharedContentInbox.addressTo(7L)

        assertNotNull(SharedContentInbox.consumeDelivery(7L))
        assertNull("Re-entering the conversation must not re-offer it", SharedContentInbox.consumeDelivery(7L))
    }

    @Test
    fun dismissingRecipientSelectionAbandonsTheShare() {
        SharedContentInbox.offer(SharedContent.Text("never sent"), "id")
        SharedContentInbox.clear()

        assertNull(SharedContentInbox.pending.value)
        assertNull(SharedContentInbox.delivery.value)
    }

    // --- who may receive it -------------------------------------------------

    @Test
    fun onlyPersonalConversationsAreOfferedAsRecipients() {
        val person = chat("1", ChatType.DIRECT, user(id = "1"))
        val mutedGroup = chat("2", ChatType.GROUP, null)
        val channel = chat("3", ChatType.CHANNEL, null)
        val bot = chat("4", ChatType.DIRECT, user(id = "4", isBot = true))
        val telegramService = chat("777000", ChatType.DIRECT, user(id = "777000"))
        val readOnly = chat("5", ChatType.DIRECT, user(id = "5")).copy(canSendText = false)
        val savedMessages = chat("6", ChatType.SAVED_MESSAGES, null)

        val eligible = ShareRecipients.eligible(
            listOf(person, mutedGroup, channel, bot, telegramService, readOnly, savedMessages)
        )

        assertEquals(listOf("1"), eligible.map { it.id })
    }

    // --- reading the bytes --------------------------------------------------

    @Test
    fun aReadableUriBecomesARealFile() {
        val context = Robolectric.buildActivity(android.app.Activity::class.java).get()
        val uri = Uri.parse("content://test/photo")
        Shadows.shadowOf(context.contentResolver)
            .registerInputStream(uri, ByteArrayInputStream(ByteArray(64) { 7 }))

        val gateway = SharedUriGateway(context)
        val materialized = gateway.materialize(
            com.foresightlabs.aether.domain.sharing.SharedAttachment(
                uri = uri.toString(),
                kind = com.foresightlabs.aether.domain.sharing.SharedAttachmentKind.IMAGE,
                mimeType = "image/jpeg",
                name = "photo.jpg"
            )
        )

        assertNotNull(materialized)
        val file = java.io.File(materialized!!.path)
        assertTrue("The share must reach a real file, not a URI path", file.exists())
        assertEquals(64L, file.length())
        assertTrue(materialized.path.endsWith(".jpg"))
    }

    @Test
    fun anInaccessibleUriFailsWithoutThrowing() {
        val context = Robolectric.buildActivity(android.app.Activity::class.java).get()
        val gateway = SharedUriGateway(context)

        val materialized = gateway.materialize(
            com.foresightlabs.aether.domain.sharing.SharedAttachment(
                uri = "content://revoked/nothing-here",
                kind = com.foresightlabs.aether.domain.sharing.SharedAttachmentKind.FILE,
                mimeType = "application/pdf"
            )
        )

        assertNull("An unreadable share reports failure rather than crashing", materialized)
        // Asking for lasting access to a grant that has none must be survivable too.
        gateway.retainAccess(Uri.parse("content://revoked/nothing-here"))
    }

    // --- fixtures -----------------------------------------------------------

    private fun user(id: String, isBot: Boolean = false) = User(
        id = id,
        name = "Person $id",
        username = "person$id",
        avatarInitials = "P$id",
        avatarGradient = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)),
        phone = "+1 555 0$id",
        isBot = isBot
    )

    private fun chat(id: String, type: ChatType, directUser: User?) = Chat(
        id = id,
        title = "Chat $id",
        type = type,
        lastMessageText = "",
        lastMessageTime = "",
        avatarInitials = "C$id",
        avatarGradient = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)),
        directUser = directUser,
        blockableUserId = directUser?.id?.toLongOrNull()
    )
}
