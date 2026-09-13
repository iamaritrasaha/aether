package com.foresightlabs.aether.calls.media

/**
 * The frame-batch admission guard, pure so the stale-generation contract is
 * unit-testable without native state.
 *
 * A batch of decoded frames is converted and rendered only when ALL of these
 * hold:
 * - the session's renderer is enabled (a voice call never converts frames;
 *   a renderer that gave up on malformed frames does not resume either);
 * - the batch belongs to the CURRENTLY active call id -- a batch from an old
 *   generation (or a call that was replaced) must never render into a new
 *   call, which is the stale-frame rule;
 * - the device is the CAMERA (microphone/screen batches are not pixels here);
 * - there is at least one frame;
 * - the batch is newer than the conversion throttle window (newest-frame-
 *   wins: conversion cost is bounded regardless of arrival rate).
 */
object FrameBatchGuard {

    fun shouldProcess(
        rendererEnabled: Boolean,
        callIdMatchesActive: Boolean,
        isCameraDevice: Boolean,
        frameCount: Int,
        nowMs: Long,
        lastFrameAtMs: Long,
        minIntervalMs: Long
    ): Boolean =
        rendererEnabled &&
            callIdMatchesActive &&
            isCameraDevice &&
            frameCount > 0 &&
            (nowMs - lastFrameAtMs >= minIntervalMs || lastFrameAtMs == 0L)
}
