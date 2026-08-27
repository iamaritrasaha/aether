package com.foresightlabs.aether.data.calls

import com.foresightlabs.aether.calls.media.CallMediaConfig
import com.foresightlabs.aether.calls.media.CallProtocolInfo
import com.foresightlabs.aether.calls.media.CallServerEndpoint
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
)

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
)

object TgCallsAdapter {

    fun buildMediaConfig(
        call: TdApi.Call,
        ready: TdApi.CallStateReady
    ): CallMediaConfig {
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

            CallServerEndpoint(
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

        val protocol = CallProtocolInfo(
            minLayer = ready.protocol?.minLayer ?: 65,
            maxLayer = ready.protocol?.maxLayer ?: 92,
            udpP2p = ready.protocol?.udpP2p ?: true,
            udpReflector = ready.protocol?.udpReflector ?: true,
            libraryVersions = ready.protocol?.libraryVersions?.toList() ?: listOf("1.0.0")
        )

        return CallMediaConfig(
            callId = call.id.toLong(),
            isOutgoing = call.isOutgoing,
            encryptionKey = ready.encryptionKey ?: ByteArray(0),
            allowP2p = ready.allowP2p,
            servers = serverList,
            configJson = ready.config.orEmpty(),
            customParameters = ready.customParameters.orEmpty(),
            protocol = protocol
        )
    }

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
