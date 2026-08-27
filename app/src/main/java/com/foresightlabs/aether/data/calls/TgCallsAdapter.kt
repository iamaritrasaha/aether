package com.foresightlabs.aether.data.calls

import org.drinkless.tdlib.TdApi

data class TgCallsServer(
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
        if (other !is TgCallsServer) return false
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

data class TgCallsConfig(
    val callId: Int,
    val isOutgoing: Boolean,
    val encryptionKey: ByteArray,
    val allowP2p: Boolean,
    val servers: List<TgCallsServer>,
    val configJson: String,
    val customParameters: String,
    val maxLayer: Int,
    val minLayer: Int,
    val versions: List<String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TgCallsConfig) return false
        if (callId != other.callId) return false
        if (isOutgoing != other.isOutgoing) return false
        if (!encryptionKey.contentEquals(other.encryptionKey)) return false
        if (allowP2p != other.allowP2p) return false
        if (servers != other.servers) return false
        if (configJson != other.configJson) return false
        if (customParameters != other.customParameters) return false
        if (maxLayer != other.maxLayer) return false
        if (minLayer != other.minLayer) return false
        if (versions != other.versions) return false
        return true
    }

    override fun hashCode(): Int {
        var result = callId
        result = 31 * result + isOutgoing.hashCode()
        result = 31 * result + encryptionKey.contentHashCode()
        result = 31 * result + allowP2p.hashCode()
        result = 31 * result + servers.hashCode()
        result = 31 * result + configJson.hashCode()
        result = 31 * result + customParameters.hashCode()
        result = 31 * result + maxLayer
        result = 31 * result + minLayer
        result = 31 * result + versions.hashCode()
        return result
    }
}

object TgCallsAdapter {

    fun buildConfig(
        call: TdApi.Call,
        ready: TdApi.CallStateReady
    ): TgCallsConfig {
        val serverList = ready.servers?.map { server ->
            var peerTag = ByteArray(0)
            var isTcp = false
            var username = ""
            var password = ""
            var supportsTurn = false
            var supportsStun = false

            when (val serverType = server.type) {
                is TdApi.CallServerTypeTelegramReflector -> {
                    peerTag = serverType.peerTag ?: ByteArray(0)
                    isTcp = serverType.isTcp
                }
                is TdApi.CallServerTypeWebrtc -> {
                    username = serverType.username.orEmpty()
                    password = serverType.password.orEmpty()
                    supportsTurn = serverType.supportsTurn
                    supportsStun = serverType.supportsStun
                }
            }

            TgCallsServer(
                id = server.id,
                ipAddress = server.ipAddress.orEmpty(),
                ipv6Address = server.ipv6Address.orEmpty(),
                port = server.port,
                peerTag = peerTag,
                isTcp = isTcp,
                username = username,
                password = password,
                supportsTurn = supportsTurn,
                supportsStun = supportsStun
            )
        }.orEmpty()

        return TgCallsConfig(
            callId = call.id,
            isOutgoing = call.isOutgoing,
            encryptionKey = ready.encryptionKey ?: ByteArray(0),
            allowP2p = ready.allowP2p,
            servers = serverList,
            configJson = ready.config.orEmpty(),
            customParameters = ready.customParameters.orEmpty(),
            maxLayer = ready.protocol?.maxLayer ?: 92,
            minLayer = ready.protocol?.minLayer ?: 65,
            versions = ready.protocol?.libraryVersions?.toList() ?: listOf("1.0.0")
        )
    }
}
