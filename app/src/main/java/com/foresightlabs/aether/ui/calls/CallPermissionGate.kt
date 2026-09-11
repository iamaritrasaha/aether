package com.foresightlabs.aether.ui.calls

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.foresightlabs.aether.domain.calls.CallPermissions

/**
 * Starts a call only once the OS permissions that call needs are actually held.
 *
 * This exists because the permissions were previously requested when *accepting*
 * a call but not when *placing* one, and the consequence was not a silent call:
 * Android kills the process outright when a microphone foreground service starts
 * without `RECORD_AUDIO`, which is how a successfully connected call became a
 * crash. A call is now never placed before the grant exists.
 *
 * Asking here, at the tap, is also the contextual moment Aether requires --
 * never at Conversation open, and never for the camera on a voice call.
 *
 * @param onStart invoked with the call kind once the call may proceed.
 * @return `start(isVideo)`, to be called from a button.
 */
@Composable
fun rememberCallStarter(
    onStart: (isVideo: Boolean) -> Unit
): (Boolean) -> Unit {
    val context = LocalContext.current
    // Which kind of call the in-flight permission request belongs to, so the
    // grant result resumes the call the user actually asked for.
    val pendingIsVideo = remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.filterValues { it }.keys
        // A refused camera downgrades a video call to audio rather than
        // cancelling it; a refused microphone means there is no call to place.
        if (CallPermissions.callAllowed(granted)) {
            onStart(pendingIsVideo.value)
        }
    }

    return { isVideo ->
        val required = CallPermissions.required(isVideo)
        val missing = required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onStart(isVideo)
        } else {
            pendingIsVideo.value = isVideo
            launcher.launch(missing.toTypedArray())
        }
    }
}
