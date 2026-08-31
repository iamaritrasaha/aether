package com.foresightlabs.aether.domain.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telegram's out-of-band service notifications are surfaced, never dropped. */
class ServiceNoticeTest {

    @Test
    fun `an ordinary service notification is surfaced`() {
        val notice = buildServiceNotice("UPDATE_APP", "Please update Telegram.")
        assertEquals("Please update Telegram.", notice?.text)
        assertFalse(notice!!.requiresAuthKeyDropPrompt)
    }

    @Test
    fun `the auth key drop type is flagged rather than flattened`() {
        val notice = buildServiceNotice("AUTH_KEY_DROP_DUPLICATE", "Your session was terminated.")
        assertTrue(notice!!.requiresAuthKeyDropPrompt)
    }

    @Test
    fun `an empty or blank notice has nothing to show`() {
        assertNull(buildServiceNotice("UPDATE_APP", ""))
        assertNull(buildServiceNotice("UPDATE_APP", "   "))
        assertNull(buildServiceNotice("UPDATE_APP", null))
    }

    @Test
    fun `a missing type is not an auth key drop`() {
        assertFalse(buildServiceNotice(null, "Notice")!!.requiresAuthKeyDropPrompt)
    }
}
