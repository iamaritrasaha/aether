package com.foresightlabs.aether.data.calls.aether

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Identity mapping between Telegram and Aether calling -- kept EXPLICIT and
 * out of UI code, because the two namespaces are genuinely different things:
 *
 * - A Telegram user is an account in the user's contact universe. They may or
 *   may not be an Aether-calling user at all.
 * - An Aether calling identity is whatever the Aether call service registered
 *   for a person's installation.
 *
 * The dev mapping is deliberately dumb (an explicit registry the service
 * owns; the app queries it). Production replaces the DIRECTORY, not the call
 * sites: nothing in the UI may assume a Telegram user IS an Aether user.
 */
data class AetherCallingIdentity(
    val aetherId: String,
    val displayName: String,
    val telegramUserId: Long?
)

/**
 * HTTP client for the development Aether Call Service (see call-service/README).
 *
 * Endpoints (all JSON, no auth in dev beyond being on the LAN):
 * - POST /register           {aetherId, displayName, telegramUserId}
 * - GET  /capabilities?aetherId=...
 * - POST /call/invite        {from, to, isVideo}  -> {roomId, token, url}
 * - GET  /call/incoming?aetherId=...              -> {invite} | {none}
 * - POST /call/accept        {inviteId}           -> {roomId, token, url, e2eeKey}
 * - POST /call/decline       {inviteId}
 * - POST /call/complete      {roomId}
 *
 * The LiveKit API secret lives only in the service; the app receives
 * short-lived participant TOKENS, never credentials.
 */
class AetherCallServiceClient(private val baseUrlProvider: () -> String) {

    constructor(baseUrl: String) : this({ baseUrl })

    private fun post(path: String, body: JSONObject): JSONObject? =
        request(path, method = "POST", body = body)

    private fun get(path: String): JSONObject? = request(path, method = "GET")

    private fun request(path: String, method: String, body: JSONObject? = null): JSONObject? {
        return try {
            val connection = URL(baseUrlProvider() + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 2500
            connection.readTimeout = if (method == "GET" && path.contains("incoming")) 7_000 else 4000
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = connection.responseCode
            val payload = if (code in 200..299) {
                connection.inputStream.use { stream -> stream.bufferedReader().readText() }
            } else {
                // Error bodies (e.g. "recipient not registered") are surfaced
                // as JSON rather than collapsed into "unreachable".
                connection.errorStream?.use { stream -> stream.bufferedReader().readText() } ?: "{}"
            }
            connection.disconnect()
            if (payload.isBlank()) JSONObject() else JSONObject(payload)
        } catch (t: Throwable) {
            // The dev service being down is an expected state (nobody runs
            // it); surface as unavailability, never a crash. Visible at WARN:
            // silent failure here once hid a whole evening of debugging (the
            // cleartext-HTTP block produced zero log lines at DEBUG level).
            Log.w(TAG, "call service ${method.lowercase()} $path unavailable: ${t.javaClass.simpleName}: ${t.message ?: ""}")
            null
        }
    }

    fun register(aetherId: String, displayName: String, telegramUserId: Long?): Boolean =
        post("/register", JSONObject().apply {
            put("aetherId", aetherId)
            put("displayName", displayName)
            if (telegramUserId != null) put("telegramUserId", telegramUserId)
        }) != null

    /** Whether [aetherId] is registered and callable. Null = unknown (service unreachable). */
    fun capabilities(aetherId: String): Boolean? =
        get("/capabilities?aetherId=${java.net.URLEncoder.encode(aetherId, "UTF-8")}")
            ?.optBoolean("callable")

    /** Finds the Aether identity registered for a Telegram user, if any. */
    fun lookupByTelegramUser(telegramUserId: Long): AetherCallingIdentity? {
        val result = get("/lookup?telegramUserId=$telegramUserId") ?: return null
        val aetherId = result.optString("aetherId").takeIf { it.isNotBlank() } ?: return null
        return AetherCallingIdentity(
            aetherId = aetherId,
            displayName = result.optString("displayName", "Aether user"),
            telegramUserId = telegramUserId
        )
    }

    data class Invite(val inviteId: String, val from: AetherCallingIdentity, val isVideo: Boolean)

    /** Starts a call: creates the room and returns THIS side's join credentials. */
    fun invite(from: AetherCallingIdentity, to: AetherCallingIdentity, isVideo: Boolean): RoomJoin? =
        post(
            "/call/invite",
            JSONObject().apply {
                put("from", from.aetherId)
                put("to", to.aetherId)
                put("isVideo", isVideo)
            }
        )?.optJSONObject("join")?.toRoomJoin()

    /** Long-ish poll for an incoming invite addressed to [aetherId]. */
    fun incoming(aetherId: String): Invite? {
        val result = get("/call/incoming?aetherId=${java.net.URLEncoder.encode(aetherId, "UTF-8")}") ?: return null
        val invite = result.optJSONObject("invite") ?: return null
        return Invite(
            inviteId = invite.optString("inviteId"),
            from = AetherCallingIdentity(
                aetherId = invite.optString("from"),
                displayName = invite.optString("fromName", "Aether user"),
                telegramUserId = if (invite.has("telegramUserId")) invite.optLong("telegramUserId") else null
            ),
            isVideo = invite.optBoolean("isVideo")
        )
    }

    /** Callee accept: returns the room join credentials (+ the DEV e2ee key). */
    fun accept(inviteId: String): RoomJoin? =
        post("/call/accept", JSONObject().put("inviteId", inviteId))
            ?.optJSONObject("join")?.toRoomJoin()

    fun decline(inviteId: String): Boolean =
        post("/call/decline", JSONObject().put("inviteId", inviteId)) != null

    fun complete(roomId: String): Boolean =
        post("/call/complete", JSONObject().put("roomId", roomId)) != null

    private fun JSONObject.toRoomJoin(): RoomJoin = RoomJoin(
        roomId = optString("roomId"),
        url = optString("url"),
        token = optString("token"),
        e2eeKeyBase64 = optString("e2eeKey").takeIf { it.isNotBlank() }
    )

    private companion object {
        const val TAG = "AetherCallService"
    }
}

/** Credentials to join one LiveKit room. Tokens are short-lived and per-participant. */
data class RoomJoin(
    val roomId: String,
    val url: String,
    val token: String,
    /** DEV-ONLY: the shared dev media key handed out by the service. See [AetherCallSecurity]. */
    val e2eeKeyBase64: String?
)

/** One registered Aether-calling user, for the directory surface. */
data class AetherDirectoryEntry(val aetherId: String, val displayName: String, val telegramUserId: Long?) {
    companion object {
        fun fromJson(obj: JSONObject): AetherDirectoryEntry = AetherDirectoryEntry(
            aetherId = obj.optString("aetherId"),
            displayName = obj.optString("displayName"),
            telegramUserId = if (obj.has("telegramUserId")) obj.optLong("telegramUserId") else null
        )
    }
}

/** Directory helpers used by the chooser to decide Aether availability. */
fun JSONArray.toDirectoryEntries(): List<AetherDirectoryEntry> =
    (0 until length()).map { AetherDirectoryEntry.fromJson(getJSONObject(it)) }
