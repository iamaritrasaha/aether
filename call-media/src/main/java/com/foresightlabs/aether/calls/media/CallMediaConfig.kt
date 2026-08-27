package com.foresightlabs.aether.calls.media

data class CallServerEndpoint(
    val id: Long,
    val ipAddress: String,
    val ipv6Address: String,
    val port: Int,
    val peerTag: ByteArray = ByteArray(0),
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
        if (!peerTag.contentEquals(other.peerTag)) return false
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
        result = 31 * result + peerTag.contentHashCode()
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
