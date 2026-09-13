package com.foresightlabs.aether.domain.calls

/**
 * Which calling transport owns a call.
 *
 * Aether carries TWO backends under one call surface:
 *
 * - [AETHER] -- the primary, stable path: Aether-to-Aether over LiveKit,
 *   end-to-end encrypted media (development E2EE key distribution for now --
 *   see AetherCallSecurity for the exact boundary).
 * - [TELEGRAM_BETA] -- the existing TDLib + ntgcalls transport, talking to
 *   official Telegram clients. Kept fully functional and fully instrumented,
 *   but always surfaced to the user with its Beta label: its physical media
 *   validation is still open (two-way audio and video are physically
 *   unresolved; see docs/architecture/calling-native-stack.md).
 *
 * Every [com.foresightlabs.aether.domain.model.ActiveCall] knows its backend;
 * backend callbacks must never mutate a call owned by another backend (see
 * [CallHub], which owns the one-canonical-call rule across both).
 */
enum class CallBackend(
    /** Shown in user-facing labels: the Beta suffix is mandatory for [TELEGRAM_BETA]. */
    val isBeta: Boolean,
    val label: String
) {
    AETHER(isBeta = false, label = "Aether Call"),
    TELEGRAM_BETA(isBeta = true, label = "Telegram Call (Beta)")
}
