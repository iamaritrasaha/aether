package com.foresightlabs.aether.domain.calls

import com.foresightlabs.aether.domain.model.ActiveCall
import com.foresightlabs.aether.domain.model.CallStateEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.atomic.AtomicLong

/**
 * The ONE canonical active call, across BOTH backends.
 *
 * Aether shows at most one call at a time no matter which transport owns it:
 * a LiveKit call and a Telegram call must never coexist -- they would fight
 * over the microphone, the camera and audio focus.
 *
 * Backends publish into the hub ([setTelegramCall] / [setAetherCall]); every
 * registration mints a monotonically increasing slot, and when two calls are
 * ever published simultaneously the NEWER registration wins while the older
 * one is presented as [CallStateEnum.DISCARDED] -- and named in
 * [preemptedBackend] so ITS OWN backend tears its media down (a backend never
 * reads the winner's call; backend isolation lives at that boundary).
 *
 * The Telegram side is observed here rather than rewritten: its repository
 * keeps constructing [ActiveCall]s tagged [CallBackend.TELEGRAM_BETA], and
 * the hub watches its flow. The Aether (LiveKit) side publishes explicitly.
 */
class CallHub(
    telegramState: StateFlow<ActiveCall?>,
    scope: CoroutineScope
) {

    private val _telegramCall = MutableStateFlow<ActiveCall?>(null)

    /** The Telegram backend's call as the hub sees it (may be preempted to DISCARDED). */
    val telegramCall: StateFlow<ActiveCall?> = _telegramCall.asStateFlow()

    private val _aetherCall = MutableStateFlow<ActiveCall?>(null)

    /** The Aether (LiveKit) backend's call, if any. Backend-owned; the hub only publishes it. */
    val aetherCall: StateFlow<ActiveCall?> = _aetherCall.asStateFlow()

    private val slotCounter = AtomicLong(0L)
    private val telegramSlot = AtomicLong(0L)
    private val aetherSlot = AtomicLong(0L)

    private val _preemptedBackend = MutableStateFlow<CallBackend?>(null)

    /**
     * Non-null while a backend's call was displaced by the other backend's
     * newer call: the displaced backend observes this and tears its own media
     * down. Cleared once that backend publishes no live call.
     */
    val preemptedBackend: StateFlow<CallBackend?> = _preemptedBackend.asStateFlow()

    init {
        telegramState
            .onEach { setTelegramCall(it) }
            .launchIn(scope)
    }

    fun setTelegramCall(call: ActiveCall?) {
        if (call != null && _telegramCall.value?.callId != call.callId) {
            telegramSlot.set(slotCounter.incrementAndGet())
        }
        _telegramCall.value = call
        recomputePreemption()
    }

    fun setAetherCall(call: ActiveCall?) {
        if (call != null && _aetherCall.value?.callId != call.callId) {
            aetherSlot.set(slotCounter.incrementAndGet())
        }
        _aetherCall.value = call
        recomputePreemption()
    }

    /**
     * How a backend un-minimizes ITS OWN call (Telegram: repository
     * setMinimized; Aether: its repository). Registered by app wiring, because
     * the hub is backend-agnostic and must never reach into a backend
     * directly.
     */
    private val resumeHandlers = mutableMapOf<CallBackend, () -> Unit>()

    fun registerResumeHandler(backend: CallBackend, handler: () -> Unit) {
        resumeHandlers[backend] = handler
    }

    /**
     * Reopens the active call's full-screen surface (the animated call icon's
     * action). Dispatches to whichever backend owns the current call; a no-op
     * when no call is live or no handler registered.
     */
    fun resumeActiveCall() {
        val call = activeCall.value ?: return
        resumeHandlers[call.backend]?.invoke()
    }

    private val minimizeHandlers = mutableMapOf<CallBackend, () -> Unit>()

    fun registerMinimizeHandler(backend: CallBackend, handler: () -> Unit) {
        minimizeHandlers[backend] = handler
    }

    /**
     * Back from the call screen: leaves the surface, keeps the call running.
     * Dispatches to the owning backend; a no-op with no active call.
     */
    fun minimizeActiveCall() {
        val call = activeCall.value ?: return
        minimizeHandlers[call.backend]?.invoke()
    }

    /** The one visible call, whichever backend owns it. */
    val activeCall: StateFlow<ActiveCall?> = combine(_telegramCall, _aetherCall) { telegram, aether ->
        resolveSingleActiveCall(telegram, aether)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * The merge rule, unit-testable: never two calls at once. The later
     * REGISTRATION wins and is returned verbatim; the loser is presented as
     * DISCARDED in its own backend flow (see [recomputePreemption]) so every
     * UI surface stops presenting it as live.
     */
    fun resolveSingleActiveCall(telegram: ActiveCall?, aether: ActiveCall?): ActiveCall? = when {
        telegram == null && aether == null -> null
        aether == null -> telegram
        telegram == null -> aether
        aetherSlot.get() > telegramSlot.get() -> aether
        else -> telegram
    }

    private fun recomputePreemption() {
        val t = _telegramCall.value
        val a = _aetherCall.value
        if (t == null || a == null) {
            _preemptedBackend.value = null
            return
        }
        val telegramLoses = aetherSlot.get() > telegramSlot.get()
        _preemptedBackend.value = if (telegramLoses) CallBackend.TELEGRAM_BETA else CallBackend.AETHER
        // Present the loser as terminal in ITS OWN flow so every surface
        // (and the losing backend itself) stops treating it as live.
        if (telegramLoses && t.state != CallStateEnum.DISCARDED) {
            _telegramCall.value = t.copy(state = CallStateEnum.DISCARDED)
        } else if (!telegramLoses && a.state != CallStateEnum.DISCARDED) {
            _aetherCall.value = a.copy(state = CallStateEnum.DISCARDED)
        }
    }
}
