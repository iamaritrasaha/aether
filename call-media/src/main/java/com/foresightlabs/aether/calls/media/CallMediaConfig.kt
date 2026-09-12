package com.foresightlabs.aether.calls.media

data class CallServerEndpoint(
    val id: Long,
    val ipAddress: String,
    val ipv6Address: String,
    val port: Int,
    /**
     * Absent (`null`) for a genuine STUN/TURN (`CallServerTypeWebrtc`)
     * server, present for a Telegram reflector (`CallServerTypeTelegramReflector`).
     *
     * This distinction is load-bearing, not cosmetic: ntgcalls' native
     * `RTCServer::to_rtc_servers()` (`ntgcalls/src/p2p/rtc_server.cpp` at
     * the pinned rc02 commit) branches on `if (server.peer_tag)` -- and its
     * JNI binding's `parseOptional` (`targets/android/app/src/main/jni/
     * utils.hpp.tpl`) converts a Java field to `std::nullopt` only when the
     * field is literally `null`; an empty-but-non-null `byte[]` still
     * becomes a *present* `std::optional<bytes::binary>`. A synthesized
     * `ByteArray(0)` default here was therefore silently misclassifying
     * every WebRTC STUN/TURN server as a Telegram reflector, which
     * `push_phone` in the same native function turns into a "phone"/
     * reflector RTC server entry (`login = "reflector"`, `password =
     * hex(peer_tag)`) instead of the real STUN/TURN entry -- reproduced on
     * physical hardware as ntgcalls' own `reflector_port.cpp`: "Allocation
     * can't be started without setting the peer tag." Never synthesize a
     * non-null placeholder for this field; absence must reach the native
     * layer as absence.
     */
    val peerTag: ByteArray? = null,
    val isTcp: Boolean = false,
    val username: String = "",
    val password: String = "",
    val supportsTurn: Boolean = false,
    val supportsStun: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CallServerEndpoint) return false
        if (id != other.id) return false
        if (ipAddress != other.ipAddress) return false
        if (ipv6Address != other.ipv6Address) return false
        if (port != other.port) return false
        if (peerTag != null) {
            if (other.peerTag == null || !peerTag.contentEquals(other.peerTag)) return false
        } else if (other.peerTag != null) {
            return false
        }
        if (isTcp != other.isTcp) return false
        if (username != other.username) return false
        if (password != other.password) return false
        if (supportsTurn != other.supportsTurn) return false
        if (supportsStun != other.supportsStun) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + ipAddress.hashCode()
        result = 31 * result + ipv6Address.hashCode()
        result = 31 * result + port
        result = 31 * result + (peerTag?.contentHashCode() ?: 0)
        result = 31 * result + isTcp.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + password.hashCode()
        result = 31 * result + supportsTurn.hashCode()
        result = 31 * result + supportsStun.hashCode()
        return result
    }
}

data class CallProtocolInfo(
    val minLayer: Int = 65,
    val maxLayer: Int = 92,
    val udpP2p: Boolean = true,
    val udpReflector: Boolean = true,
    val libraryVersions: List<String> = listOf("1.0.0")
)

/**
 * Immutable configuration handoff from TDLib CallStateReady to the native media engine.
 */
data class CallMediaConfig(
    val callId: Long,
    val isOutgoing: Boolean,
    /**
     * Whether the camera may be opened for this call. Not "this is a video
     * call": a video call whose camera permission was refused connects with
     * this false, and nothing in the camera or renderer path runs.
     */
    val videoCaptureEnabled: Boolean,
    val encryptionKey: ByteArray,
    val allowP2p: Boolean,
    val servers: List<CallServerEndpoint>,
    val configJson: String,
    val customParameters: String,
    val protocol: CallProtocolInfo
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CallMediaConfig) return false
        if (callId != other.callId) return false
        if (isOutgoing != other.isOutgoing) return false
        if (videoCaptureEnabled != other.videoCaptureEnabled) return false
        if (!encryptionKey.contentEquals(other.encryptionKey)) return false
        if (allowP2p != other.allowP2p) return false
        if (servers != other.servers) return false
        if (configJson != other.configJson) return false
        if (customParameters != other.customParameters) return false
        if (protocol != other.protocol) return false
        return true
    }

    override fun hashCode(): Int {
        var result = callId.hashCode()
        result = 31 * result + isOutgoing.hashCode()
        result = 31 * result + videoCaptureEnabled.hashCode()
        result = 31 * result + encryptionKey.contentHashCode()
        result = 31 * result + allowP2p.hashCode()
        result = 31 * result + servers.hashCode()
        result = 31 * result + configJson.hashCode()
        result = 31 * result + customParameters.hashCode()
        result = 31 * result + protocol.hashCode()
        return result
    }

    /**
     * Wipes encryption key memory to minimize key lifetime in RAM.
     */
    fun wipeSecrets() {
        encryptionKey.fill(0)
    }
}
