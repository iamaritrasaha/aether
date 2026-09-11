package com.foresightlabs.aether.domain.calls

import com.foresightlabs.aether.domain.messaging.ConversationClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallEligibilityTest {

    @Test
    fun personalHumanWithMediaIsEligible() {
        assertTrue(CallEligibility.isEligible(ConversationClass.PERSONAL_HUMAN, isMediaTransportAvailable = true))
    }

    @Test
    fun personalHumanWithoutMediaIsNotEligible() {
        assertFalse(CallEligibility.isEligible(ConversationClass.PERSONAL_HUMAN, isMediaTransportAvailable = false))
    }

    @Test
    fun telegramServiceAccountCannotBeCalledEvenWithMediaAvailable() {
        assertFalse(CallEligibility.isEligible(ConversationClass.TELEGRAM_SERVICE, isMediaTransportAvailable = true))
    }

    @Test
    fun botsGroupsChannelsForumsCannotBeCalled() {
        assertFalse(CallEligibility.isEligible(ConversationClass.SECONDARY_TELEGRAM_CONTENT, isMediaTransportAvailable = true))
    }

    @Test
    fun unresolvedCounterpartCannotBeCalled() {
        assertFalse(CallEligibility.isEligible(ConversationClass.UNKNOWN, isMediaTransportAvailable = true))
    }
}
