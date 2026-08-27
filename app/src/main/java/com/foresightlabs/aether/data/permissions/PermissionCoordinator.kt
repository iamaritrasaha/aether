package com.foresightlabs.aether.data.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PermissionStatus {
    GRANTED,
    DENIED,
    NEEDS_RATIONALE,
    PERMANENTLY_DENIED,
    NOT_APPLICABLE
}

data class SystemPermissionState(
    val notification: PermissionStatus = PermissionStatus.DENIED,
    val microphone: PermissionStatus = PermissionStatus.DENIED,
    val camera: PermissionStatus = PermissionStatus.DENIED,
    val contacts: PermissionStatus = PermissionStatus.DENIED,
    val location: PermissionStatus = PermissionStatus.DENIED
)

class PermissionCoordinator(context: Context) {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(refreshState())
    val state: StateFlow<SystemPermissionState> = _state.asStateFlow()

    fun refresh(): SystemPermissionState {
        val newState = refreshState()
        _state.value = newState
        return newState
    }

    private fun refreshState(): SystemPermissionState {
        val notificationStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            PermissionStatus.GRANTED
        }

        val microphoneStatus = checkPermission(Manifest.permission.RECORD_AUDIO)
        val cameraStatus = checkPermission(Manifest.permission.CAMERA)
        val contactsStatus = checkPermission(Manifest.permission.READ_CONTACTS)
        val locationStatus = checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

        return SystemPermissionState(
            notification = notificationStatus,
            microphone = microphoneStatus,
            camera = cameraStatus,
            contacts = contactsStatus,
            location = locationStatus
        )
    }

    fun checkPermission(permission: String): PermissionStatus {
        val result = ContextCompat.checkSelfPermission(appContext, permission)
        return if (result == PackageManager.PERMISSION_GRANTED) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.DENIED
        }
    }

    fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
