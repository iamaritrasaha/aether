package com.foresightlabs.aether.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay
import java.io.File

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

@Composable
fun VideoNoteRecorderSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSendVideoNote: (filePath: String, durationSec: Int, length: Int) -> Unit
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

    LaunchedEffect(isVisible) {
        if (isVisible && (!hasCameraPermission || !hasAudioPermission)) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    if (!isVisible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .testTag("video_note_recorder_overlay"),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AetherEmber.Shapes.RisingSheet)
                    .background(colors.surface)
                    .border(1.dp, colors.border, AetherEmber.Shapes.RisingSheet)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* stay inside */ }
                    .padding(20.dp)
                    .navigationBarsPadding()
                    .testTag("video_note_recorder_sheet")
            ) {
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
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aether needs camera and microphone access to record round video messages.",
                            fontFamily = ManropeFontFamily,
                            fontSize = 14.sp,
                            color = colors.textSecondary
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
                    VideoNoteCameraCapture(
                        onDismiss = onDismiss,
                        onSendVideo = onSendVideoNote
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoNoteCameraCapture(
    onDismiss: () -> Unit,
    onSendVideo: (filePath: String, durationSec: Int, length: Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalAetherColors.current

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableIntStateOf(0) }
    var currentRecording by remember { mutableStateOf<Recording?>(null) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

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
                cameraProvider = future.get()
            }, ContextCompat.getMainExecutor(context))
        } catch (_: Throwable) {}
    }

    // Controlled Camera Binding: Bound strictly when cameraProvider, lifecycleOwner, or lensFacing changes.
    // UI recompositions (pulse animation, timer ticks) DO NOT rebind CameraX.
    LaunchedEffect(cameraProvider, lifecycleOwner, lensFacing) {
        val provider = cameraProvider ?: return@LaunchedEffect
        try {
            provider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD))
                .build()
            val capture = VideoCapture.withOutput(recorder)
            videoCapture = capture

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture
            )
        } catch (_: Throwable) {}
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
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Video Message",
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Circular Camera Preview
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .border(
                    3.dp,
                    if (isRecording) Color(0xFFEF4444).copy(alpha = pulseAlpha) else colors.accent,
                    CircleShape
                )
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { previewView },
                update = { /* No-op: Camera binding is handled in LaunchedEffect to avoid rebind loops */ },
                modifier = Modifier.fillMaxSize()
            )

            // Duration Pill overlay
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clip(AetherEmber.Shapes.Pill)
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val mins = recordingDuration / 60
                    val secs = recordingDuration % 60
                    val durationStr = String.format("%02d:%02d / 01:00", mins, secs)
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

        Spacer(modifier = Modifier.height(20.dp))

        // Action Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flip Camera (Only active when NOT recording)
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
                enabled = !isRecording,
                modifier = Modifier.size(48.dp).testTag("flip_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Flip Camera",
                    tint = if (!isRecording) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(26.dp)
                )
            }

            // Record / Stop Button
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) Color(0xFFEF4444) else colors.accent)
                    .clickable {
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
                                            // Output validation: check error, file existence, and non-empty content
                                            if (!event.hasError() && outputFile.exists() && outputFile.length() > 0) {
                                                val (derivedLength, measuredDuration) = extractVideoNoteMetadata(outputFile)
                                                val finalDuration = if (measuredDuration > 0) measuredDuration else recordingDuration.coerceAtLeast(1)
                                                onSendVideo(
                                                    outputFile.absolutePath,
                                                    finalDuration,
                                                    derivedLength
                                                )
                                                onDismiss()
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

            // Cancel / Delete Button
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
}
