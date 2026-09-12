package com.foresightlabs.aether.domain.calls

import com.foresightlabs.aether.domain.model.CallStateEnum

/**
 * What Aether actually tells the user about a call, at any given moment.
 *
 * TDLib's [CallStateEnum] is signalling only -- it reaches READY once both
 * sides have exchanged keys, with no idea whether audio can actually flow.
 * [MediaConnectionState] is the real media transport's own account of
 * whether a path exists. [ACTIVE] is reachable ONLY when both agree: never
 * from signalling alone. That is the whole point of keeping this mapping in
 * one place rather than inlined at a call site that could take the shortcut.
 */
enum class CallPresentationState {
    IDLE,
    OUTGOING_REQUEST,
    RINGING,
    CONNECTING,
    ACTIVE,
    RECONNECTING,
    ENDED,
    FAILED
}

object CallStatePresenter {
    fun present(
        signalling: CallStateEnum,
        media: MediaConnectionState,
        isOutgoing: Boolean,
        mediaEverConnected: Boolean = false
    ): CallPresentationState {
        // Checked first and unconditionally: a media failure ends the call
        // regardless of what signalling still believes.
        if (media == MediaConnectionState.FAILED || media == MediaConnectionState.UNAVAILABLE) {
            return CallPresentationState.FAILED
        }
        return when (signalling) {
            CallStateEnum.ERROR -> CallPresentationState.FAILED
            CallStateEnum.DISCARDED, CallStateEnum.HANGING_UP -> CallPresentationState.ENDED
            CallStateEnum.PENDING, CallStateEnum.EXCHANGING_KEYS ->
                if (isOutgoing) CallPresentationState.OUTGOING_REQUEST else CallPresentationState.RINGING
            CallStateEnum.READY -> when (media) {
                // Reachable only when the real media transport has itself
                // reported a connected path -- never inferred from
                // signalling reaching READY alone.
                MediaConnectionState.CONNECTED -> CallPresentationState.ACTIVE
                MediaConnectionState.RECONNECTING -> CallPresentationState.RECONNECTING
                // Native reports plain CONNECTING for a transient drop after
                // media was already up once; with the sticky ever-connected
                // flag that presents as RECONNECTING, not a misleading first
                // "Connecting".
                MediaConnectionState.CONNECTING ->
                    if (mediaEverConnected) CallPresentationState.RECONNECTING else CallPresentationState.CONNECTING
                // The media engine has already stopped itself -- there is
                // nothing left to connect, regardless of whether TDLib's own
                // discard of the call has landed yet. Falling into CONNECTING
                // here (the old behaviour) is exactly what turned a media
                // engine that had already given up into a call screen stuck
                // on "Connecting..." forever.
                MediaConnectionState.STOPPED -> CallPresentationState.ENDED
                else -> CallPresentationState.CONNECTING
            }
        }
    }

    /** Whether an elapsed-time counter may run: only once real media is [CallPresentationState.ACTIVE]. */
    fun durationShouldRun(state: CallPresentationState): Boolean = state == CallPresentationState.ACTIVE
}
