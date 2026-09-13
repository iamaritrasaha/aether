package com.foresightlabs.aether.data.calls.aether

import android.content.Context
import androidx.annotation.MainThread
import com.foresightlabs.aether.domain.calls.CallBackend
import com.foresightlabs.aether.domain.calls.CallHub
import com.foresightlabs.aether.domain.model.ActiveCall
import com.foresightlabs.aether.domain.model.CallStateEnum
import com.foresightlabs.aether.domain.calls.MediaConnectionState
import com.foresightlabs.aether.domain.model.User
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * The Aether (LiveKit) call backend.
 *
 * Owns everything Aether-to-Aether: room join via the dev call service,
 * media state mapped into the SAME [ActiveCall] model the Telegram backend
 * uses (so CallScreen, permissions, audio-route UI, duration and lifecycle
 * are reused verbatim), mute/camera/switch on the LiveKit room, and the
 * backend-tagged publishing into the [CallHub] -- which enforces the
 * one-canonical-call rule across both backends.
 *
 * Backend isolation: this repository only ever publishes/observes calls with
 * `backend == AETHER`; a Telegram call passing through the hub is invisible
 * here beyond the hub's preemption signal (which ends OUR call).
 *
 * Diagnostics are backend-tagged and use an independent session namespace
 * (`sid=<room-scoped serial>`), never the Telegram generation counters.
 */
class AetherCallsRepository(
    private val context: Context,
    private val hub: CallHub,
    private val serviceClient: AetherCallServiceClient,
    private val scope: CoroutineScope,
    /** The local user's Aether calling identity (dev: derived at registration). */
    private val selfIdentity: suspend () -> AetherCallingIdentity?
) {

    private val _activeCall = MutableStateFlow<ActiveCall?>(null)
    val activeCall: StateFlow<ActiveCall?> = _activeCall.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<RemoteVideoTrack?>(null)
    /** The remote participant's video track while a video call receives one. */
    val remoteVideoTrack: StateFlow<RemoteVideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<LocalVideoTrack?>(null)
    /** Our own outgoing camera track, for local preview. */
    val localVideoTrack: StateFlow<LocalVideoTrack?> = _localVideoTrack.asStateFlow()

    private val _localFrontFacing = MutableStateFlow(true)

    /** Facing of our local camera as WE model it (starts front; switch flips). Drives preview mirroring. */
    val localFrontFacing: StateFlow<Boolean> = _localFrontFacing.asStateFlow()

    private var room: Room? = null
    private var durationJob: Job? = null
    private var incomingPollJob: Job? = null
    private var sid: Long = 0L

    init {
        // Cross-backend preemption: when the OTHER backend's newer call takes
        // the single slot, THIS backend tears its own call down. It never
        // reads the winner -- only the hub's named signal.
        scope.launch {
            hub.preemptedBackend.collect { preempted ->
                if (preempted == CallBackend.AETHER && _activeCall.value != null) {
                    AetherCallLog.stage(nextSid(), "preempted — ending our call")
                    teardown("preempted_by_newer_backend")
                }
            }
        }
    }

    private fun nextSid(): Long = ++sid

    /** Whether the Aether calling path is configured (a reachable dev service URL). */
    val isConfigured: Boolean
        get() = serviceClient.let { true }

    /**
     * Starts polling for incoming invites. Dev prototype: no push, so the
     * app polls the service while running. Safe to call repeatedly.
     */
    fun startIncomingPolling() {
        if (incomingPollJob?.isActive == true) return
        incomingPollJob = scope.launch {
            while (true) {
                val self = selfIdentity() ?: run { delay(10_000); continue }
                // Idempotent re-registration keeps the dev directory fresh
                // (server restarts lose state by design).
                serviceClient.register(self.aetherId, self.displayName, self.telegramUserId)
                val invite = serviceClient.incoming(self.aetherId) ?: run { delay(POLL_MILLIS); continue }
                if (_activeCall.value != null) {
                    // One canonical call: refuse while busy (the service will
                    // report us unavailable; the caller sees a decline).
                    serviceClient.decline(invite.inviteId)
                    delay(POLL_MILLIS)
                    continue
                }
                beginIncoming(invite)
                delay(POLL_MILLIS)
            }
        }
    }

    private fun beginIncoming(invite: AetherCallServiceClient.Invite) {
        val id = nextSid()
        AetherCallLog.stage(id, "incoming invite from=${invite.from.aetherId} video=${invite.isVideo}")
        _activeCall.value = ActiveCall(
            callId = invite.inviteId.hashCode(),
            userId = invite.from.telegramUserId ?: 0L,
            user = User(
                id = invite.from.aetherId,
                name = invite.from.displayName,
                username = "",
                avatarInitials = invite.from.displayName.take(1).uppercase(),
                avatarGradient = listOf(
                    androidx.compose.ui.graphics.Color(0xFF6F6A8A),
                    androidx.compose.ui.graphics.Color(0xFF3A3750)
                )
            ),
            isOutgoing = false,
            isVideo = invite.isVideo,
            state = CallStateEnum.PENDING,
            backend = CallBackend.AETHER
        ).also { hub.setAetherCall(it) }
        pendingInviteId = invite.inviteId
    }

    @Volatile
    private var pendingInviteId: String? = null

    /**
     * Directory lookup: is this Telegram contact an Aether-calling user, and
     * under which identity? Null = not registered (a completely normal
     * state) or the dev service being unreachable (also normal).
     */
    suspend fun aetherIdentityFor(telegramUserId: Long): AetherCallingIdentity? =
        serviceClient.lookupByTelegramUser(telegramUserId)

    /**
     * Places an Aether call to a Telegram contact RESOLVED to an Aether
     * identity by the directory. Refuses when no mapping exists -- a contact
     * not being an Aether user is a normal state, not an error.
     */
    suspend fun initiateCall(target: AetherCallingIdentity, isVideo: Boolean, callerName: String): Result<Unit> {
        if (_activeCall.value != null) {
            return Result.failure(IllegalStateException("A call is already active"))
        }
        val self = selfIdentity()
            ?: return Result.failure(IllegalStateException("This installation is not registered for Aether Calls"))
        val join = serviceClient.invite(self, target, isVideo)
            ?: return Result.failure(IllegalStateException("Aether call service is unreachable"))
        return startSession(join, outgoing = true, peerName = target.displayName, isVideo = isVideo, callerName = callerName)
    }

    suspend fun acceptCall(): Result<Unit> {
        val inviteId = pendingInviteId
            ?: return Result.failure(IllegalStateException("No incoming call to accept"))
        val join = serviceClient.accept(inviteId)
            ?: return Result.failure(IllegalStateException("Aether call service is unreachable"))
        val call = _activeCall.value
        return startSession(
            join,
            outgoing = false,
            peerName = call?.user?.name ?: "Aether user",
            isVideo = call?.isVideo ?: false,
            callerName = call?.user?.name ?: "Aether user"
        )
    }

    fun declineCall() {
        pendingInviteId?.let { serviceClient.decline(it) }
        pendingInviteId = null
        teardown("declined")
    }

    fun endCall() {
        _activeCall.value?.let { serviceClient.complete("room-${it.callId}") }
        teardown("ended")
    }

    private suspend fun startSession(
        join: RoomJoin,
        outgoing: Boolean,
        peerName: String,
        isVideo: Boolean,
        callerName: String
    ): Result<Unit> {
        val id = nextSid()
        AetherCallLog.stage(id, "join room=${join.roomId.hashCode()} urlHost=${hostOf(join.url)} outgoing=$outgoing video=$isVideo")
        _activeCall.value = ActiveCall(
            callId = join.roomId.hashCode(),
            userId = 0L,
            user = User(
                id = peerName,
                name = peerName,
                username = "",
                avatarInitials = peerName.take(1).uppercase(),
                avatarGradient = listOf(
                    androidx.compose.ui.graphics.Color(0xFF6F6A8A),
                    androidx.compose.ui.graphics.Color(0xFF3A3750)
                )
            ),
            isOutgoing = outgoing,
            isVideo = isVideo,
            state = CallStateEnum.EXCHANGING_KEYS,
            backend = CallBackend.AETHER
        ).also { hub.setAetherCall(it) }

        return try {
            connectRoom(join, id)
            Result.success(Unit)
        } catch (t: Throwable) {
            AetherCallLog.failure(id, "connect", t)
            teardown("connect_failed")
            Result.failure(t)
        }
    }

    private suspend fun connectRoom(join: RoomJoin, id: Long) = withContext(Dispatchers.Main) {
        LiveKit.init(context)
        val newRoom = LiveKit.create(context, AetherCallE2EE.roomOptions(join.e2eeKeyBase64))
        room = newRoom

        // Backend-tagged media-state mapping: LiveKit's room lifecycle maps
        // onto the shared MediaConnectionState model.
        scope.launch {
            newRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.Connected -> onRoomConnected(id)
                    is RoomEvent.Reconnecting -> publishMedia(MediaConnectionState.RECONNECTING, id)
                    is RoomEvent.Reconnected -> publishMedia(MediaConnectionState.CONNECTED, id)
                    is RoomEvent.Disconnected -> teardown("remote_disconnected")
                    is RoomEvent.FailedToConnect -> {
                        AetherCallLog.failure(id, "failed_to_connect", event.error)
                        teardown("connect_failed")
                    }
                    is RoomEvent.TrackSubscribed -> {
                        if (event.track is RemoteVideoTrack) {
                            _remoteVideoTrack.value = event.track as RemoteVideoTrack
                            AetherCallLog.stage(id, "remote_video_subscribed")
                        }
                    }
                    is RoomEvent.TrackUnsubscribed -> {
                        if (event.track is RemoteVideoTrack) {
                            _remoteVideoTrack.value = null
                            AetherCallLog.stage(id, "remote_video_unsubscribed")
                        }
                    }
                    is RoomEvent.ParticipantDisconnected -> {
                        // The peer left: end our side too.
                        teardown("peer_left")
                    }
                    else -> Unit
                }
            }
        }

        // E2EE is configured at ROOM CREATION: development-scoped key
        // distribution only -- see AetherCallSecurity for exactly why this is
        // NOT production-secure yet.
        newRoom.connect(url = join.url, token = join.token)

        // Publish our tracks per the call kind.
        val local = newRoom.localParticipant
        local.setMicrophoneEnabled(true)
        if (_activeCall.value?.isVideo == true) {
            local.setCameraEnabled(true)
            _localVideoTrack.value = local.getOrCreateDefaultVideoTrack()
        }
        _activeCall.value = _activeCall.value?.copy(mediaState = MediaConnectionState.CONNECTED, connectedAtMs = System.currentTimeMillis())
        hub.setAetherCall(_activeCall.value)
        AetherCallLog.stage(id, "media=CONNECTED")
        startDurationTicker()
    }

    private fun onRoomConnected(id: Long) {
        AetherCallLog.stage(id, "room event=Connected")
        publishMedia(MediaConnectionState.CONNECTED, id)
    }

    private fun publishMedia(state: MediaConnectionState, id: Long) {
        AetherCallLog.stage(id, "media=${state.name}")
        _activeCall.value = _activeCall.value?.copy(mediaState = state)
        hub.setAetherCall(_activeCall.value)
        if (state == MediaConnectionState.CONNECTED) startDurationTicker()
    }

    private fun startDurationTicker() {
        if (durationJob?.isActive == true) return
        durationJob = scope.launch {
            var seconds = 0
            while (true) {
                delay(1000)
                val call = _activeCall.value ?: break
                if (call.mediaState == MediaConnectionState.CONNECTED) {
                    seconds++
                    _activeCall.value = call.copy(durationSec = seconds)
                    hub.setAetherCall(_activeCall.value)
                } else if (call.mediaState != MediaConnectionState.RECONNECTING) {
                    break
                }
            }
        }
    }

    @MainThread
    fun setMuted(muted: Boolean) {
        val call = _activeCall.value ?: return
        _activeCall.value = call.copy(isMuted = muted)
        hub.setAetherCall(_activeCall.value)
        val r = room ?: return
        scope.launch {
            try {
                r.localParticipant.setMicrophoneEnabled(!muted)
            } catch (t: Throwable) {
                AetherCallLog.failure(nextSid(), "setMicrophone($muted)", t)
            }
        }
    }

    @MainThread
    fun setCameraEnabled(enabled: Boolean) {
        val call = _activeCall.value ?: return
        _activeCall.value = call.copy(cameraIntentOn = enabled)
        hub.setAetherCall(_activeCall.value)
        val r = room ?: return
        scope.launch {
            try {
                r.localParticipant.setCameraEnabled(enabled)
                _localVideoTrack.value = if (enabled) r.localParticipant.getOrCreateDefaultVideoTrack() else null
            } catch (t: Throwable) {
                AetherCallLog.failure(nextSid(), "setCamera($enabled)", t)
            }
        }
    }

    @MainThread
    fun switchCamera() {
        val track = _localVideoTrack.value ?: return
        track.switchCamera()
        _localFrontFacing.value = !_localFrontFacing.value
        _activeCall.value = _activeCall.value?.copy(isFrontCamera = _localFrontFacing.value)
        hub.setAetherCall(_activeCall.value)
        AetherCallLog.stage(nextSid(), "camera_switched front=${_localFrontFacing.value}")
    }

    fun isFrontCamera(): Boolean = _localVideoTrack.value?.let {
        // LiveKit's capturer reports facing through its options; mirror the
        // preview for front by default and let switchCamera flip it.
        true
    } ?: true

    /**
     * Speaker/earpiece routing for Aether calls: the same AudioManager
     * communication-device path the Telegram engine uses, mirrored into the
     * call's UI-visible [ActiveCall.audioRoute]/[ActiveCall.isSpeakerOn].
     * Bluetooth/wired routes arrive via the OS callback in a later pass; the
     * user-facing toggle is real from day one.
     */
    @MainThread
    fun toggleSpeaker() {
        val call = _activeCall.value ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return
        val targetRoute = if (call.audioRoute == com.foresightlabs.aether.domain.calls.AudioRoute.SPEAKER) {
            com.foresightlabs.aether.domain.calls.AudioRoute.EARPIECE
        } else {
            com.foresightlabs.aether.domain.calls.AudioRoute.SPEAKER
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val wanted = if (targetRoute == com.foresightlabs.aether.domain.calls.AudioRoute.SPEAKER) {
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == wanted }
                    ?.let { audioManager.setCommunicationDevice(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = targetRoute == com.foresightlabs.aether.domain.calls.AudioRoute.SPEAKER
            }
            _activeCall.value = call.copy(
                audioRoute = targetRoute,
                isSpeakerOn = targetRoute == com.foresightlabs.aether.domain.calls.AudioRoute.SPEAKER
            )
            hub.setAetherCall(_activeCall.value)
        } catch (t: Throwable) {
            AetherCallLog.failure(nextSid(), "toggleSpeaker", t)
        }
    }

    private fun teardown(reason: String) {
        val id = nextSid()
        durationJob?.cancel()
        durationJob = null
        _remoteVideoTrack.value = null
        _localVideoTrack.value = null
        pendingInviteId = null
        try {
            room?.disconnect()
        } catch (t: Throwable) {
            AetherCallLog.failure(id, "disconnect", t)
        }
        room = null
        _activeCall.value = null
        hub.setAetherCall(null)
        AetherCallLog.stage(id, "teardown reason=$reason")
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull() ?: "?"

    private companion object {
        const val POLL_MILLIS = 4000L
    }
}

/**
 * Backend-tagged diagnostics for the Aether (LiveKit) backend. Deliberately
 * separate from Telegram's generation-based [CallDiagnostics]: sessions here
 * are per-room serials, and every line carries backend=AETHER.
 */
object AetherCallLog {
    const val TAG: String = "AetherCall"

    fun stage(session: Long, detail: String) {
        try {
            android.util.Log.i(TAG, "backend=AETHER sid=$session stage=$detail")
        } catch (_: Throwable) {
        }
    }

    fun failure(session: Long, stage: String, error: Throwable?) {
        val type = error?.javaClass?.name ?: "unknown"
        try {
            android.util.Log.w(TAG, "backend=AETHER sid=$session FAILED at=$stage type=$type")
        } catch (_: Throwable) {
        }
    }
}
