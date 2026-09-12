package com.foresightlabs.aether.data.calls

import com.foresightlabs.aether.calls.media.CallMediaConfig
import com.foresightlabs.aether.calls.media.CallProtocolInfo
import com.foresightlabs.aether.calls.media.CallServerEndpoint
import org.drinkless.tdlib.TdApi

object TgCallsAdapter {

    /**
     * The call protocol Aether asks TDLib to negotiate, built from what the
     * linked media engine actually supports ([supported], from
     * `NTgCalls.getProtocol()`) rather than a guessed layer/version list.
     * Falls back to conservative, TDLib-documented defaults only when
     * [supported] is null -- no transport loaded at all, a path
     * `isCallMediaAvailable` should already keep call placement/acceptance
     * from reaching.
     */
    fun buildCallProtocol(supported: CallProtocolInfo?): TdApi.CallProtocol {
        return if (supported != null) {
            TdApi.CallProtocol(
                supported.udpP2p,
                supported.udpReflector,
                supported.minLayer,
                supported.maxLayer,
                supported.libraryVersions.toTypedArray()
            )
        } else {
            TdApi.CallProtocol(true, true, 65, 92, arrayOf("2.9.5"))
        }
    }

    /**
     * @param videoCaptureEnabled whether the camera may be opened for this call.
     *   Not the same as `call.isVideo` -- see [com.foresightlabs.aether.domain.calls.TelegramCallMediaEngine.start].
     */
    fun buildMediaConfig(
        call: TdApi.Call,
        ready: TdApi.CallStateReady,
        videoCaptureEnabled: Boolean = false
    ): CallMediaConfig {
        val serverList = ready.servers?.map { server ->
            // null, never a synthesized ByteArray(0): see CallServerEndpoint.peerTag's
            // doc for why an empty-but-present array is not a safe stand-in for
            // absence at the native layer.
            var peerTag: ByteArray? = null
            var isTcp = false
            var username = ""
            var password = ""
            var supportsTurn = false
            var supportsStun = false

            when (val serverType = server.type) {
                is TdApi.CallServerTypeTelegramReflector -> {
                    peerTag = serverType.peerTag
                    isTcp = serverType.isTcp
                }
                is TdApi.CallServerTypeWebrtc -> {
                    // peerTag stays null: this is a STUN/TURN server, never
                    // a Telegram reflector, and must reach ntgcalls that way.
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
            videoCaptureEnabled = videoCaptureEnabled,
            encryptionKey = ready.encryptionKey ?: ByteArray(0),
            allowP2p = ready.allowP2p,
            servers = serverList,
            configJson = ready.config.orEmpty(),
            customParameters = ready.customParameters.orEmpty(),
            protocol = protocol
        )
    }
}
