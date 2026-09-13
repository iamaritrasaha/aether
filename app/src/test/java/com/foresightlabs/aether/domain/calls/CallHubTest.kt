package com.foresightlabs.aether.domain.calls

import com.foresightlabs.aether.domain.model.ActiveCall
import com.foresightlabs.aether.domain.model.CallStateEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The one-canonical-call rule across both backends: Aether (LiveKit) and
 * Telegram (Beta) may never present two calls at once, the newer
 * registration wins, the loser is presented as terminal and named for ITS
 * OWN backend to tear down, and a single-backend flow passes through
 * verbatim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallHubTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun telegramCall(id: Int = 11) = ActiveCall(
        callId = id, userId = 7L, user = null, isOutgoing = true,
        state = CallStateEnum.READY, backend = CallBackend.TELEGRAM_BETA
    )

    private fun aetherCall(id: String = "room-a") = ActiveCall(
        callId = id.hashCode(), userId = 9L, user = null, isOutgoing = true,
        state = CallStateEnum.READY, backend = CallBackend.AETHER
    )

    @Test
    fun singleBackendPassesThroughVerbatim() {
        val telegram = MutableStateFlow<ActiveCall?>(telegramCall())
        val hub = CallHub(telegram, kotlinx.coroutines.CoroutineScope(dispatcher))

        assertNull(hub.aetherCall.value)
        assertEquals(telegramCall(), hub.activeCall.value)
        assertEquals(CallBackend.TELEGRAM_BETA, hub.activeCall.value!!.backend)

        telegram.value = null
        assertNull(hub.activeCall.value)
    }

    @Test
    fun newerAetherCallPreemptsLiveTelegramCall() {
        val telegram = MutableStateFlow<ActiveCall?>(telegramCall())
        val hub = CallHub(telegram, kotlinx.coroutines.CoroutineScope(dispatcher))

        hub.setAetherCall(aetherCall())

        // The visible call is the Aether one; the Telegram side is presented
        // as terminal and the Telegram backend is named for teardown.
        assertEquals(CallBackend.AETHER, hub.activeCall.value!!.backend)
        assertEquals(CallStateEnum.DISCARDED, hub.telegramCall.value!!.state)
        assertEquals(CallStateEnum.READY, hub.aetherCall.value!!.state)
        assertEquals(CallBackend.TELEGRAM_BETA, hub.preemptedBackend.value)
    }

    @Test
    fun newerTelegramCallPreemptsLiveAetherCall() {
        val telegram = MutableStateFlow<ActiveCall?>(null)
        val hub = CallHub(telegram, kotlinx.coroutines.CoroutineScope(dispatcher))
        hub.setAetherCall(aetherCall())

        telegram.value = telegramCall()

        assertEquals(CallBackend.TELEGRAM_BETA, hub.activeCall.value!!.backend)
        assertEquals(CallStateEnum.DISCARDED, hub.aetherCall.value!!.state)
        assertEquals(CallBackend.AETHER, hub.preemptedBackend.value)
    }

    @Test
    fun preemptionClearsWhenTheLoserFinishes() {
        val telegram = MutableStateFlow<ActiveCall?>(telegramCall())
        val hub = CallHub(telegram, kotlinx.coroutines.CoroutineScope(dispatcher))
        hub.setAetherCall(aetherCall())
        assertEquals(CallBackend.TELEGRAM_BETA, hub.preemptedBackend.value)

        // The displaced Telegram backend ends its own call.
        telegram.value = null
        assertNull(hub.preemptedBackend.value)
        assertEquals(CallBackend.AETHER, hub.activeCall.value!!.backend)
    }

    @Test
    fun reRegisteringTheSameCallDoesNotStealTheSlot() {
        val telegram = MutableStateFlow<ActiveCall?>(telegramCall())
        val hub = CallHub(telegram, kotlinx.coroutines.CoroutineScope(dispatcher))
        hub.setAetherCall(aetherCall())
        assertEquals(CallBackend.AETHER, hub.activeCall.value!!.backend)

        // The Telegram flow re-emits the same call id (a TDLib update): the
        // Aether call must stay the winner -- a re-delivered update must not
        // look like a newer call.
        telegram.value = telegramCall().copy(state = CallStateEnum.READY)
        assertEquals(CallBackend.AETHER, hub.activeCall.value!!.backend)
        assertEquals(CallBackend.TELEGRAM_BETA, hub.preemptedBackend.value)
    }

    @Test
    fun backendLabelsAreHonest() {
        assertFalse(CallBackend.AETHER.isBeta)
        assertTrue(CallBackend.TELEGRAM_BETA.isBeta)
        assertTrue("The Beta suffix is mandatory in the product label", CallBackend.TELEGRAM_BETA.label.contains("Beta"))
    }

    @Test
    fun activeCallCarriesItsBackend() {
        assertEquals(CallBackend.TELEGRAM_BETA, telegramCall().backend)
        assertEquals(CallBackend.AETHER, aetherCall().backend)
        // Default constructor (pre-existing callers) is the Telegram backend:
        // the existing implementation is the only producer today.
        assertEquals(
            CallBackend.TELEGRAM_BETA,
            ActiveCall(callId = 1, userId = 1L, user = null).backend
        )
    }
}
