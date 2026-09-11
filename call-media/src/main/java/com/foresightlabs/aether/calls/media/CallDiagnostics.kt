package com.foresightlabs.aether.calls.media

/**
 * The observable stages a call passes through, in order.
 *
 * These exist so a failure during connection can be located precisely -- the
 * last stage reached before a crash names the subsystem to look at, rather than
 * leaving "it crashes when the call connects" as the whole diagnosis.
 */
enum class CallStage {
    /** TDLib reports CallStateReady: servers and key agreed. Nothing has flowed. */
    TDLIB_READY,

    /** The native media session object exists for this call id. */
    MEDIA_SESSION_CREATED,

    /** Transport connection to the reflector/peer has been asked for. */
    P2P_CONNECTING,

    /** Microphone/speaker sources are being configured. */
    AUDIO_INITIALIZING,

    /** Camera capture is being configured. Voice calls never reach this. */
    VIDEO_INITIALIZING,

    /** The native engine itself reports a connected media path. */
    MEDIA_CONNECTED,

    /** The call UI has taken the connected state. */
    UI_ACTIVE,

    /** A video renderer began consuming frames. */
    RENDERER_ATTACHED,

    /** A video renderer stopped consuming frames. */
    RENDERER_DETACHED,

    /** The ongoing-call foreground service started. */
    SERVICE_STARTED,

    /** The ongoing-call foreground service stopped, or was never startable. */
    SERVICE_STOPPED,

    /** The session is being torn down. */
    TEARDOWN,

    /** Something failed. Always accompanied by a sanitised reason. */
    FAILED
}

/**
 * Structured, deliberately non-sensitive call diagnostics.
 *
 * Nothing here accepts a payload, a key, or free-form caller text: the only
 * inputs are a session generation, a [CallStage], and typed primitives. That is
 * the point -- there is no call site shape that could accidentally log call
 * secrets, signalling bytes, or message content, because the API cannot carry
 * them. Throwable reporting is reduced to the exception's class and a sanitised
 * message (see [sanitise]), never a payload dump.
 */
object CallDiagnostics {

    const val TAG: String = "AetherCall"

    /** Where formatted lines go. Replaceable so tests can observe without Android. */
    @Volatile
    var sink: (String) -> Unit = { line ->
        try {
            android.util.Log.i(TAG, line)
        } catch (_: Throwable) {
            // Unit-test JVM has no android.util.Log; diagnostics must never
            // be the reason anything fails.
        }
    }

    fun format(generation: Long, stage: CallStage, detail: String? = null): String {
        val suffix = detail?.takeIf { it.isNotBlank() }?.let { " ${sanitise(it)}" }.orEmpty()
        return "gen=$generation stage=${stage.name}$suffix"
    }

    fun stage(generation: Long, stage: CallStage, detail: String? = null) {
        sink(format(generation, stage, detail))
    }

    /** Reports a failure as the exception's type plus a sanitised message. */
    fun failure(generation: Long, stage: CallStage, error: Throwable?) {
        val type = error?.javaClass?.name ?: "unknown"
        val message = error?.message?.let { sanitise(it) }.orEmpty()
        sink(format(generation, CallStage.FAILED, "at=${stage.name} type=$type msg=$message"))
    }

    /**
     * Strips anything that could carry a secret out of a detail string.
     *
     * Long unbroken alphanumeric runs are exactly what an encryption key,
     * peer tag or signalling blob looks like when someone eventually pastes one
     * into an exception message, so they are replaced rather than trusted. The
     * result is also length-capped: a diagnostic line is a landmark, not a dump.
     */
    fun sanitise(raw: String): String {
        val redacted = SECRET_SHAPED.replace(raw) { "<redacted:${it.value.length}>" }
        val collapsed = redacted.replace('\n', ' ').trim()
        return if (collapsed.length <= MAX_DETAIL) collapsed else collapsed.take(MAX_DETAIL) + "…"
    }

    private const val MAX_DETAIL = 160

    /** 24+ unbroken base64/hex-ish characters: key-shaped, never a human sentence. */
    private val SECRET_SHAPED = Regex("[A-Za-z0-9+/=_-]{24,}")
}
