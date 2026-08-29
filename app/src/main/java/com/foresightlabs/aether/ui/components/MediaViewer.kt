package com.foresightlabs.aether.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.foresightlabs.aether.domain.model.MediaItem
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Full-screen photo viewer.
 *
 * Works from either data source a message can have: a local file already on
 * disk ([MediaItem.hasLocalFile]), or a file TDLib still has to fetch. In the
 * latter case this requests the full-quality download at high priority the
 * moment it opens -- [onRequestDownload] -- rather than assuming the caller
 * already started one, and shows Telegram's minithumbnail behind a loading
 * shell in the meantime so the viewer is never a blank screen.
 */
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MediaViewer(
    mediaItem: MediaItem?,
    senderName: String,
    isVisible: Boolean,
    onClose: () -> Unit,
    onRequestDownload: (fileId: Int, isRetry: Boolean) -> Unit = { _, _ -> }
) {
    var currentItem by remember { mutableStateOf<MediaItem?>(null) }
    if (mediaItem != null) {
        currentItem = mediaItem
    }
    val activeItem = currentItem ?: return
    if (!isVisible && currentItem == null) return
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    androidx.activity.compose.BackHandler(enabled = isVisible) {
        onClose()
    }

    LaunchedEffect(activeItem.id, isVisible) {
        if (isVisible && com.foresightlabs.aether.BuildConfig.DEBUG) {
            android.util.Log.d("AetherTd", "MEDIA_VIEWER_STATE_CREATED id=${activeItem.id} fileId=${activeItem.fileId} hasLocalFile=${activeItem.hasLocalFile}")
        }
    }

    LaunchedEffect(activeItem.fileId, activeItem.downloadFailed, activeItem.hasLocalFile) {
        if (!activeItem.hasLocalFile && activeItem.fileId != 0 && !activeItem.downloadFailed) {
            if (com.foresightlabs.aether.BuildConfig.DEBUG) {
                android.util.Log.d("AetherTd", "MEDIA_VIEWER_DOWNLOAD_REQUEST fileId=${activeItem.fileId}")
            }
            onRequestDownload(activeItem.fileId, false)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.94f, animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.94f, animationSpec = tween(120)),
        modifier = Modifier.fillMaxSize().zIndex(10f)
    ) {
        val dragOffsetY = remember(activeItem.id) { Animatable(0f) }
        var chromeVisible by remember(activeItem.id) { mutableStateOf(true) }
        var scale by remember(activeItem.id) { mutableFloatStateOf(1f) }
        var panOffset by remember(activeItem.id) { mutableStateOf(Offset.Zero) }

        val parsedSource: Any? = remember(activeItem.url, activeItem.hasLocalFile) {
            val url = activeItem.url.trim()
            when {
                url.isBlank() -> null
                url.startsWith("content://") -> Uri.parse(url)
                url.startsWith("file://") -> {
                    val file = File(url.removePrefix("file://"))
                    if (file.exists() && file.length() > 0L) file else null
                }
                url.startsWith("/") -> {
                    val file = File(url)
                    if (file.exists() && file.length() > 0L) file else null
                }
                else -> {
                    val file = File(url)
                    if (file.exists() && file.length() > 0L) file else url
                }
            }
        }
        val hasValidLocalSource = parsedSource != null && activeItem.hasLocalFile

        LaunchedEffect(activeItem.id, parsedSource, hasValidLocalSource) {
            if (com.foresightlabs.aether.BuildConfig.DEBUG) {
                val sourceKind = when {
                    parsedSource is File -> "FILE_PATH"
                    parsedSource is Uri -> "CONTENT_URI"
                    parsedSource is String -> "TDLIB_REMOTE"
                    !activeItem.previewBase64.isNullOrBlank() -> "THUMBNAIL"
                    else -> "NONE"
                }
                android.util.Log.d("AetherTd", "MEDIA_VIEWER_SOURCE sourceKind=$sourceKind hasValidLocalSource=$hasValidLocalSource fileId=${activeItem.fileId}")
            }
        }

        val backdropAlpha = (1f - (abs(dragOffsetY.value) / 500f)).coerceIn(0.15f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF08090C).copy(alpha = backdropAlpha))
                .pointerInput(activeItem.id) {
                    detectTapGestures(
                        onTap = { chromeVisible = !chromeVisible },
                        onDoubleTap = {
                            coroutineScope.launch {
                                if (scale > 1f) {
                                    scale = 1f
                                    panOffset = Offset.Zero
                                    dragOffsetY.snapTo(0f)
                                } else {
                                    scale = 2.5f
                                }
                            }
                        }
                    )
                }
                .pointerInput(activeItem.id) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        if (newScale > 1.05f) {
                            panOffset = panOffset + pan
                        } else {
                            panOffset = Offset.Zero
                        }
                    }
                }
                .pointerInput(activeItem.id) {
                    detectVerticalDragGestures(
                        onDragStart = { },
                        onDragEnd = {
                            if (scale <= 1.05f) {
                                if (abs(dragOffsetY.value) > 130.dp.toPx()) {
                                    onClose()
                                } else {
                                    coroutineScope.launch {
                                        dragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (scale <= 1.05f) {
                                change.consume()
                                coroutineScope.launch {
                                    dragOffsetY.snapTo(dragOffsetY.value + dragAmount)
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val previewBitmap = remember(activeItem.previewBase64) {
                activeItem.previewBase64?.let { encoded ->
                    runCatching {
                        val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.getOrNull()
                }
            }

            val imageModifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = panOffset.x,
                    translationY = panOffset.y + dragOffsetY.value
                )

            if (hasValidLocalSource) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(parsedSource)
                        .crossfade(200)
                        .build(),
                    contentDescription = activeItem.caption.ifBlank { "Photo" },
                    contentScale = ContentScale.Fit,
                    modifier = imageModifier,
                    onSuccess = {
                        if (com.foresightlabs.aether.BuildConfig.DEBUG) {
                            android.util.Log.d("AetherTd", "MEDIA_VIEWER_READY fileId=${activeItem.fileId}")
                        }
                    },
                    onError = {
                        if (com.foresightlabs.aether.BuildConfig.DEBUG) {
                            android.util.Log.w("AetherTd", "MEDIA_VIEWER_LOAD_FAILED errorClass=${it.result.throwable.javaClass.simpleName} fileId=${activeItem.fileId}")
                        }
                    },
                    loading = {
                        MediaViewerShell(
                            previewBitmap = previewBitmap,
                            failed = false,
                            onRetry = { onRequestDownload(activeItem.fileId, true) }
                        )
                    },
                    error = {
                        MediaViewerShell(
                            previewBitmap = previewBitmap,
                            failed = true,
                            onRetry = { onRequestDownload(activeItem.fileId, true) }
                        )
                    }
                )
            } else {
                MediaViewerShell(
                    previewBitmap = previewBitmap,
                    failed = activeItem.downloadFailed,
                    onRetry = { onRequestDownload(activeItem.fileId, true) },
                    modifier = imageModifier
                )
            }

            // Top Floating Restrained Header Pill
            AnimatedVisibility(
                visible = chromeVisible && abs(dragOffsetY.value) < 40f,
                enter = fadeIn(animationSpec = tween(140)),
                exit = fadeOut(animationSpec = tween(100)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(AetherEmber.Shapes.Pill)
                            .background(Color(0x55121318))
                            .border(0.5.dp, Color(0x1FFFFFFF), AetherEmber.Shapes.Pill)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .clickable(onClick = onClose),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = senderName,
                                fontFamily = ManropeFontFamily,
                                color = Color(0xFFF2F1F4),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (activeItem.timestamp.isNotEmpty()) {
                                Text(
                                    text = activeItem.timestamp,
                                    fontFamily = ManropeFontFamily,
                                    color = Color(0x99FFFFFF),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Row(
                        modifier = Modifier
                            .clip(AetherEmber.Shapes.Pill)
                            .background(Color(0x55121318))
                            .border(0.5.dp, Color(0x1FFFFFFF), AetherEmber.Shapes.Pill)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { shareMediaItem(context, activeItem) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color(0xFFF2F1F4),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { saveMediaToDownloads(context, activeItem) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = Color(0xFFF2F1F4),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Caption Pill
            if (activeItem.caption.isNotBlank()) {
                AnimatedVisibility(
                    visible = chromeVisible && abs(dragOffsetY.value) < 40f,
                    enter = fadeIn(animationSpec = tween(140)),
                    exit = fadeOut(animationSpec = tween(100)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AetherEmber.Shapes.L)
                                .background(Color(0x66101116))
                                .border(0.5.dp, Color(0x1FFFFFFF), AetherEmber.Shapes.L)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = activeItem.caption,
                                fontFamily = ManropeFontFamily,
                                color = Color(0xFFF2F1F4),
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The viewer's own loading/failure state -- shown while a file is not yet on
 * disk, over Telegram's minithumbnail when there is one, or over the bare
 * atmospheric background when there is not.
 */
@Composable
private fun MediaViewerShell(
    previewBitmap: android.graphics.Bitmap?,
    failed: Boolean,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer(alpha = 0.65f)
            )
        }
        if (failed) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(AetherEmber.Shapes.Pill)
                    .background(Color(0x8014151B))
                    .border(0.5.dp, Color(0x22FFFFFF), AetherEmber.Shapes.Pill)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = Color(0xFFF2F1F4),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Couldn't load photo. Tap to retry",
                    fontFamily = ManropeFontFamily,
                    color = Color(0xFFF2F1F4),
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        } else {
            CircularProgressIndicator(
                color = Color(0x99FFFFFF),
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

private fun shareMediaItem(context: Context, mediaItem: MediaItem) {
    try {
        val path = mediaItem.url.removePrefix("file://")
        val file = File(path)
        val isVideo = mediaItem.url.endsWith(".mp4", ignoreCase = true) || mediaItem.url.endsWith(".mkv", ignoreCase = true)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/*" else "image/*"
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                putExtra(Intent.EXTRA_TEXT, mediaItem.url)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share media"))
    } catch (_: Exception) {
        Toast.makeText(context, "Couldn't share media", Toast.LENGTH_SHORT).show()
    }
}

private fun saveMediaToDownloads(context: Context, mediaItem: MediaItem) {
    try {
        val path = mediaItem.url.removePrefix("file://")
        val sourceFile = File(path)
        if (!sourceFile.exists()) {
            Toast.makeText(context, "Media file not available locally", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = sourceFile.name
        val isVideo = mediaItem.url.endsWith(".mp4", ignoreCase = true) || mediaItem.url.endsWith(".mkv", ignoreCase = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input -> input.copyTo(out) }
                }
                Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, fileName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            Toast.makeText(context, "Saved to ${destFile.absolutePath}", Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        Toast.makeText(context, "Failed to save media", Toast.LENGTH_SHORT).show()
    }
}
