package com.foresightlabs.aether.ui.conversation

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay
import java.io.File

private const val TAG = "VideoNoteRecorder"

/**
 * Extracts actual media dimensions and duration from a recorded MP4 video note.
 * Derives the square length parameter (capped at 640px) per TDLib InputMessageVideoNote requirements.
 */
fun extractVideoNoteMetadata(file: File): Pair<Int, Int> {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 480
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 480
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val durationSec = ((durationMs + 500) / 1000).toInt().coerceIn(1, 60)
        val length = minOf(width, height).coerceIn(120, 640)
        Pair(length, durationSec)
    } catch (_: Throwable) {
        Pair(480, 1)
    } finally {
        try {
            retriever.release()
        } catch (_: Throwable) {}
    }
}

/** Represents whether the video note is currently in live camera mode or post-recording review. */
private sealed interface VideoNoteStage {
    data object Live : VideoNoteStage
    data class Review(val file: File, val durationSec: Int, val length: Int) : VideoNoteStage
}

/**
 * Video Note Curtain content: direct content of [AetherConversationCurtain]
 * when [CurtainState.VIDEO_NOTE] is active.
 *
 * Exactly ONE surface, ONE header, and ONE background.
 * No nested headers, duplicate dismiss icons, or detached dialogs.
 */
@Composable
fun VideoNoteCurtainContent(
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onSendVideoNote: (filePath: String, durationSec: Int, length: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalAetherColors.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPermission = results[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasAudioPermission = results[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("curtain_video_note_content")
    ) {
        // The ONE canonical header for Video Note in the Curtain
        AttachmentCurtainHeader(
            title = "Video note",
            onBack = onBack,
            onCancel = onCancel,
            backTestTag = "video_note_back",
            cancelTestTag = "video_note_cancel"
        )

        if (!hasCameraPermission || !hasAudioPermission) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Camera & Audio Access Needed",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aether needs camera and microphone access to record round video messages.",
                    fontFamily = ManropeFontFamily,
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(AetherEmber.Shapes.Pill)
                        .background(colors.accent)
                        .clickable {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            )
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .testTag("video_note_grant_permissions")
                ) {
                    Text(
                        text = "Grant Permissions",
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.surface
                    )
                }
            }
        } else {
            VideoNoteCameraContent(
                onDismiss = onCancel,
                onSendVideo = onSendVideoNote
            )
        }
    }
}

/**
 * Camera capture and review content for Video Note.
 * Pure content only -- renders NO additional headers, titles, or outer frames.
 */
@Composable
private fun VideoNoteCameraContent(
    onDismiss: () -> Unit,
    onSendVideo: (filePath: String, durationSec: Int, length: Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalAetherColors.current

    var stage by remember { mutableStateOf<VideoNoteStage>(VideoNoteStage.Live) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableIntStateOf(0) }
    var currentRecording by remember { mutableStateOf<Recording?>(null) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraUnavailableReason by remember { mutableStateOf<String?>(null) }
    var hasMultipleCameras by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Resolve CameraProvider once
    LaunchedEffect(context) {
        try {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    val provider = future.get()
                    cameraProvider = provider
                    val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    val hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    hasMultipleCameras = hasFront && hasBack

                    // If requested front camera is not available on device, automatically fall back to back
                    if (!hasFront && hasBack) {
                        lensFacing = CameraSelector.LENS_FACING_BACK
                    } else if (!hasFront && !hasBack) {
                        cameraUnavailableReason = "No camera sensor found on device"
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to get ProcessCameraProvider", e)
                    cameraUnavailableReason = "Camera provider unavailable: ${e.message}"
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize CameraProvider", e)
            cameraUnavailableReason = "Camera initialization failed: ${e.message}"
        }
    }

    // Camera Binding with QualitySelector fallback to prevent crash on devices without SD profile
    LaunchedEffect(cameraProvider, lifecycleOwner, lensFacing) {
        val provider = cameraProvider ?: return@LaunchedEffect
        try {
            provider.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            if (!provider.hasCamera(cameraSelector)) {
                cameraUnavailableReason = "Selected camera lens unavailable"
                return@LaunchedEffect
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // QualitySelector with robust FallbackStrategy: checks SD -> HD -> LOWEST.
            // Avoids crash when hardware does not support SD profile (e.g. on emulators or specialized sensors).
            val qualitySelector = QualitySelector.fromOrderedList(
                listOf(Quality.SD, Quality.HD, Quality.LOWEST),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )

            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            val capture = VideoCapture.withOutput(recorder)
            videoCapture = capture

            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture
            )
            cameraUnavailableReason = null
        } catch (e: Throwable) {
            Log.e(TAG, "Error binding camera to lifecycle", e)
            cameraUnavailableReason = "Device camera blocked or unavailable: ${e.message}"
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Timer loop during recording (max 60 seconds)
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording && recordingDuration < 60) {
                delay(1000)
                recordingDuration += 1
            }
            if (recordingDuration >= 60 && isRecording) {
                currentRecording?.stop()
                isRecording = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            currentRecording?.stop()
            recordedFile?.let { file ->
                if (file.exists() && isRecording) {
                    file.delete()
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Circular Viewport (Camera Preview or Recorded Video Frame)
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .border(
                    3.dp,
                    if (isRecording) Color(0xFFEF4444).copy(alpha = pulseAlpha) else colors.accent,
                    CircleShape
                )
                .background(Color.Black)
                .testTag("video_note_preview_circle"),
            contentAlignment = Alignment.Center
        ) {
            when (val currentStage = stage) {
                is VideoNoteStage.Live -> {
                    if (cameraUnavailableReason != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = "Camera Unavailable",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Camera Unavailable",
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cameraUnavailableReason ?: "Device camera blocked",
                                fontFamily = ManropeFontFamily,
                                fontSize = 10.5.sp,
                                color = Color(0xFF9A9AA2),
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    } else {
                        AndroidView(
                            factory = { previewView },
                            update = { /* Camera binding handled in LaunchedEffect */ },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Duration Pill overlay during recording
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .clip(AetherEmber.Shapes.Pill)
                                .background(Color(0xCC000000))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("video_note_duration_pill")
                        ) {
                            val mins = recordingDuration / 60
                            val secs = recordingDuration % 60
                            val durationStr = String.format(java.util.Locale.US, "%02d:%02d / 01:00", mins, secs)
                            Text(
                                text = durationStr,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }
                is VideoNoteStage.Review -> {
                    // Review stage: displays recorded thumbnail inside circular frame
                    AsyncImage(
                        model = currentStage.file,
                        contentDescription = "Recorded video note preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Review duration pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .clip(AetherEmber.Shapes.Pill)
                            .background(Color(0xCC000000))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("video_note_review_duration")
                    ) {
                        val mins = currentStage.durationSec / 60
                        val secs = currentStage.durationSec % 60
                        Text(
                            text = String.format(java.util.Locale.US, "%02d:%02d", mins, secs),
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Controls depending on Live vs Review stage
        when (val currentStage = stage) {
            is VideoNoteStage.Live -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flip Camera (Only visible/active when NOT recording and device has multiple cameras)
                    IconButton(
                        onClick = {
                            if (!isRecording) {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                    CameraSelector.LENS_FACING_BACK
                                } else {
                                    CameraSelector.LENS_FACING_FRONT
                                }
                            }
                        },
                        enabled = !isRecording && hasMultipleCameras && cameraUnavailableReason == null,
                        modifier = Modifier.size(48.dp).testTag("flip_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Flip Camera",
                            tint = if (!isRecording && hasMultipleCameras && cameraUnavailableReason == null) {
                                colors.textPrimary
                            } else {
                                colors.textSecondary.copy(alpha = 0.3f)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Record / Stop Button
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) Color(0xFFEF4444) else colors.accent)
                            .clickable(enabled = cameraUnavailableReason == null || isRecording) {
                                if (isRecording) {
                                    currentRecording?.stop()
                                    isRecording = false
                                } else {
                                    val cap = videoCapture ?: return@clickable
                                    val outputDir = File(context.cacheDir, "video_notes").apply { mkdirs() }
                                    val outputFile = File(outputDir, "vnote_${System.currentTimeMillis()}.mp4")
                                    recordedFile = outputFile

                                    val outputOptions = FileOutputOptions.Builder(outputFile).build()
                                    val recording = cap.output
                                        .prepareRecording(context, outputOptions)
                                        .apply {
                                            if (ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED
                                            ) {
                                                withAudioEnabled()
                                            }
                                        }
                                        .start(ContextCompat.getMainExecutor(context)) { event ->
                                            when (event) {
                                                is VideoRecordEvent.Start -> {
                                                    isRecording = true
                                                }
                                                is VideoRecordEvent.Finalize -> {
                                                    isRecording = false
                                                    if (!event.hasError() && outputFile.exists() && outputFile.length() > 0) {
                                                        val (derivedLength, measuredDuration) = extractVideoNoteMetadata(outputFile)
                                                        val finalDuration = if (measuredDuration > 0) measuredDuration else recordingDuration.coerceAtLeast(1)
                                                        // Transitions to Review state per specifications
                                                        stage = VideoNoteStage.Review(outputFile, finalDuration, derivedLength)
                                                    } else {
                                                        outputFile.delete()
                                                        Toast.makeText(context, "Video recording failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    currentRecording = recording
                                }
                            }
                            .testTag("record_video_note_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = if (isRecording) "Stop" else "Record",
                            tint = colors.surface,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Discard / Cancel Button
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                currentRecording?.stop()
                                isRecording = false
                                recordedFile?.delete()
                            }
                            onDismiss()
                        },
                        modifier = Modifier.size(48.dp).testTag("cancel_video_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Cancel",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            is VideoNoteStage.Review -> {
                // Review Controls: Retake / Discard / Send
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Retake Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(AetherEmber.Shapes.M)
                            .clickable {
                                currentStage.file.delete()
                                stage = VideoNoteStage.Live
                                recordingDuration = 0
                            }
                            .padding(8.dp)
                            .testTag("video_note_retake_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceHighlight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retake",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Retake",
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary
                        )
                    }

                    // Discard Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(AetherEmber.Shapes.M)
                            .clickable {
                                currentStage.file.delete()
                                onDismiss()
                            }
                            .padding(8.dp)
                            .testTag("video_note_discard_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Discard",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Discard",
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEF4444)
                        )
                    }

                    // Send Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(AetherEmber.Shapes.M)
                            .clickable {
                                onSendVideo(
                                    currentStage.file.absolutePath,
                                    currentStage.durationSec,
                                    currentStage.length
                                )
                                onDismiss()
                            }
                            .padding(8.dp)
                            .testTag("video_note_send_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = colors.surface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Send",
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}
