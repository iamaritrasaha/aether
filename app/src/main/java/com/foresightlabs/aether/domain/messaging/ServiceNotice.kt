package com.foresightlabs.aether.domain.messaging

/**
 * A service notification Telegram's server pushed directly at the client, as
 * `TdApi.UpdateServiceNotification` on the pinned revision.
 *
 * These are not chat messages. TDLib delivers them out of band and documents that
 * the application must show the content to the user, so they cannot be routed
 * through the notification pipeline (which is driven by TDLib's own notification
 * updates for actual messages) and they must not simply be dropped -- account
 * warnings and security notices arrive this way.
 */
data class ServiceNotice(
    val text: String,
    /**
     * Whether TDLib flagged this as the auth-key-drop case.
     *
     * The pinned API documents `type` beginning with `"AUTH_KEY_DROP_"` as
     * requiring a two-button prompt whose second button destroys all local data
     * via TDLib's `Destroy`. Aether surfaces the notice itself but does not offer
     * that destructive action: wiring an irreversible local-data wipe to a
     * server-supplied string is not something to add without a way to exercise it
     * end to end. The flag is carried so the distinction is visible rather than
     * silently flattened.
     */
    val requiresAuthKeyDropPrompt: Boolean
)

/**
 * Maps a service notification's TDLib `type` string and already-rendered content
 * text into a [ServiceNotice], or null when there is nothing to show.
 *
 * Split out as a pure function so the AUTH_KEY_DROP_ prefix rule and the
 * empty-content case are testable without TDLib.
 */
fun buildServiceNotice(type: String?, text: String?): ServiceNotice? {
    val body = text?.trim().orEmpty()
    if (body.isEmpty()) return null
    return ServiceNotice(
        text = body,
        requiresAuthKeyDropPrompt = type.orEmpty().startsWith(AUTH_KEY_DROP_PREFIX)
    )
}

/** TDLib's documented marker for the destroy-local-data notification type. */
const val AUTH_KEY_DROP_PREFIX = "AUTH_KEY_DROP_"
