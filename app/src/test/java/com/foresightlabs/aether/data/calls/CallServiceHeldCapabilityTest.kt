package com.foresightlabs.aether.data.calls

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.foresightlabs.aether.AetherFeatureFlags
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * When calling is held (Play release: BuildConfig.CALLING_ENABLED=false), the
 * call foreground service must be unreachable through ANY intent path —
 * including stale notification actions left over from a previous internal
 * install. It answers every start by stopping, never by going foreground.
 * Runs only in the release unit-test variant; debug compiles calling in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallServiceHeldCapabilityTest {

    private lateinit var controller: ServiceController<CallService>

    @Before
    fun setUp() {
        assumeFalse(AetherFeatureFlags.CALLS_ENABLED)
        controller = Robolectric.buildService(CallService::class.java)
        controller.get().onCreate()
    }

    private fun startWith(action: String, callId: Int = 1): Int {
        val service = controller.get()
        val intent = Intent(ApplicationProvider.getApplicationContext(), CallService::class.java)
            .setAction(action)
            .putExtra(CallService.EXTRA_CALL_ID, callId)
            .putExtra(CallService.EXTRA_BACKEND, "TELEGRAM_BETA")
        return service.onStartCommand(intent, 0, startIdSeed++)
    }

    private var startIdSeed = 1

    @Test
    fun `start-call intent stops instead of going foreground`() {
        assertEquals(android.app.Service.START_NOT_STICKY, startWith(CallService.ACTION_START_CALL))
    }

    @Test
    fun `stale accept action from an old notification stops cleanly`() {
        assertEquals(android.app.Service.START_NOT_STICKY, startWith(CallService.ACTION_ACCEPT_CALL))
    }

    @Test
    fun `stale decline action stops cleanly`() {
        assertEquals(android.app.Service.START_NOT_STICKY, startWith(CallService.ACTION_DECLINE_CALL))
    }

    @Test
    fun `stale stop action stops cleanly`() {
        assertEquals(android.app.Service.START_NOT_STICKY, startWith(CallService.ACTION_STOP_CALL))
    }
}
