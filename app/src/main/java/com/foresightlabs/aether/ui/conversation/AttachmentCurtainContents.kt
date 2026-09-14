package com.foresightlabs.aether.ui.conversation

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AttachmentCurtains"

/**
 * Common header for child attachment flows in the Curtain.
 *
 * Back returns to [CurtainState.ATTACHMENTS], while Cancel dismisses directly to [CurtainState.COMPOSER].
 */
@Composable
internal fun AttachmentCurtainHeader(
    title: String,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    backTestTag: String,
    cancelTestTag: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .testTag(backTestTag)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to attachments",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }

        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .size(36.dp)
                .testTag(cancelTestTag)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel",
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Action card for triggering system picker or action within the Curtain.
 */
@Composable
internal fun AttachmentActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AetherEmber.Shapes.M)
            .background(colors.surfaceHighlight)
            .border(1.dp, colors.border, AetherEmber.Shapes.M)
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = ManropeFontFamily,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontFamily = ManropeFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }
    }
}

/** Item representing a photo or video in the Aether-native Gallery Curtain. */
data class GalleryMediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val durationMs: Long,
    val dateAdded: Long
)

/**
 * Native Aether Gallery Curtain content.
 *
 * Provides:
 * - Direct thumbnail grid of recent images and videos
 * - Image/video distinction with duration badges
 * - Multi-select with ordered badge numbering (1, 2, 3...)
 * - "Browse photos" system picker button for targeted selection without broad library access
 * - Contextual media permission requests (Android 13+ READ_MEDIA_IMAGES/VIDEO, Android 14+ selected-media, Android 12- READ_EXTERNAL_STORAGE)
 */
@Composable
fun GalleryCurtainContent(
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onOpenPicker: () -> Unit,
    onSendSelectedMedia: (List<Uri>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalAetherColors.current

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    var hasMediaPermission by remember {
        mutableStateOf(
            checkMediaPermissionsGranted(context)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasMediaPermission = checkMediaPermissionsGranted(context)
    }

    var mediaItems by remember { mutableStateOf<List<GalleryMediaItem>>(emptyList()) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Load recent media from MediaStore when permission is granted
    LaunchedEffect(hasMediaPermission) {
        if (hasMediaPermission) {
            isLoading = true
            mediaItems = withContext(Dispatchers.IO) {
                queryRecentGalleryMedia(context)
            }
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("curtain_gallery_content")
    ) {
        AttachmentCurtainHeader(
            title = "Gallery",
            onBack = onBack,
            onCancel = onCancel,
            backTestTag = "gallery_back",
            cancelTestTag = "gallery_cancel"
        )

        if (!hasMediaPermission) {
            // Contextual permission prompt with system picker alternative
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Photo & Video Library",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Allow access to view recent photos and videos directly in Aether, or browse individual items using the system photo picker.",
                    fontFamily = ManropeFontFamily,
                    fontSize = 12.5.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .clip(AetherEmber.Shapes.Pill)
                        .background(colors.accent)
                        .clickable {
                            permissionLauncher.launch(requiredPermissions)
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .testTag("gallery_grant_permission")
                ) {
                    Text(
                        text = "Allow Media Access",
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.surface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                AttachmentActionRow(
                    title = "Browse photos",
                    subtitle = "Pick from device without granting full access",
                    icon = Icons.Default.PhotoLibrary,
                    testTag = "gallery_open_picker",
                    onClick = onOpenPicker
                )
            }
        } else {
            // MediaStore Grid
            if (mediaItems.isEmpty() && !isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No recent photos or videos found.",
                        fontFamily = ManropeFontFamily,
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    AttachmentActionRow(
                        title = "Browse photos",
                        subtitle = "Choose using system photo picker",
                        icon = Icons.Default.PhotoLibrary,
                        testTag = "gallery_open_picker",
                        onClick = onOpenPicker
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .testTag("gallery_media_grid")
                ) {
                    items(mediaItems, key = { it.id }) { item ->
                        val isSelected = selectedUris.contains(item.uri)
                        val selectionIndex = if (isSelected) selectedUris.indexOf(item.uri) + 1 else 0

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.5.dp,
                                    color = if (isSelected) colors.accent else colors.border,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedUris = if (isSelected) {
                                        selectedUris.filter { it != item.uri }
                                    } else {
                                        selectedUris + item.uri
                                    }
                                }
                                .testTag("gallery_item_${item.id}")
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = if (item.isVideo) "Video thumbnail" else "Photo thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Video badge (duration + camera icon)
                            if (item.isVideo) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                        .clip(AetherEmber.Shapes.Pill)
                                        .background(Color(0xCC000000))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Videocam,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        val secs = (item.durationMs / 1000).toInt()
                                        val minStr = secs / 60
                                        val secStr = secs % 60
                                        Text(
                                            text = String.format(java.util.Locale.US, "%d:%02d", minStr, secStr),
                                            fontFamily = ManropeFontFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // Selection badge (Numbered 1, 2, 3...)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) colors.accent else Color(0x66000000))
                                    .border(1.dp, if (isSelected) colors.accent else Color.White.copy(alpha = 0.8f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Text(
                                        text = selectionIndex.toString(),
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.surface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Bar: Browse photos & Send (N)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(AetherEmber.Shapes.M)
                            .background(colors.surfaceHighlight)
                            .clickable(onClick = onOpenPicker)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("gallery_open_picker")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Browse photos",
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                    }

                    if (selectedUris.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(AetherEmber.Shapes.Pill)
                                .background(colors.accent)
                                .clickable {
                                    onSendSelectedMedia(selectedUris)
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                                .testTag("gallery_send_selected")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Send (${selectedUris.size})",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.surface
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = colors.surface,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** Helper to check whether relevant media read permissions are granted. */
private fun checkMediaPermissionsGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED ||
            (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

/** Queries recent MediaStore images and videos, merged and sorted by dateAdded. */
private fun queryRecentGalleryMedia(context: Context): List<GalleryMediaItem> {
    val items = mutableListOf<GalleryMediaItem>()
    try {
        val resolver = context.contentResolver

        // Images
        val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val imageProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        resolver.query(
            imageUri,
            imageProjection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            var count = 0
            while (cursor.moveToNext() && count < 40) {
                val id = cursor.getLong(idColumn)
                val date = cursor.getLong(dateColumn)
                val contentUri = ContentUris.withAppendedId(imageUri, id)
                items.add(GalleryMediaItem(id, contentUri, isVideo = false, durationMs = 0L, dateAdded = date))
                count++
            }
        }

        // Videos
        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED
        )
        resolver.query(
            videoUri,
            videoProjection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val durColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            var count = 0
            while (cursor.moveToNext() && count < 20) {
                val id = cursor.getLong(idColumn)
                val dur = cursor.getLong(durColumn)
                val date = cursor.getLong(dateColumn)
                val contentUri = ContentUris.withAppendedId(videoUri, id)
                items.add(GalleryMediaItem(id, contentUri, isVideo = true, durationMs = dur, dateAdded = date))
                count++
            }
        }
    } catch (e: Throwable) {
        Log.e(TAG, "Failed to query MediaStore", e)
    }

    return items.sortedByDescending { it.dateAdded }
}

/** Item representing an audio track in the Music Curtain. */
data class MusicTrackItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val durationMs: Long
)

/**
 * Files Curtain content: direct content of [AetherConversationCurtain]
 * when [CurtainState.FILES] is active.
 *
 * Uses scoped storage access via Storage Access Framework (ACTION_OPEN_DOCUMENT).
 * NEVER requests broad, dangerous storage permissions.
 */
@Composable
fun FilesCurtainContent(
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onBrowseFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("curtain_files_content")
    ) {
        AttachmentCurtainHeader(
            title = "Files",
            onBack = onBack,
            onCancel = onCancel,
            backTestTag = "files_back",
            cancelTestTag = "files_cancel"
        )
        Text(
            text = "Send documents, archives, or any file format without quality compression.",
            fontFamily = ManropeFontFamily,
            fontSize = 12.5.sp,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(14.dp))
        AttachmentActionRow(
            title = "Browse files",
            subtitle = "Choose from internal storage or cloud documents",
            icon = Icons.Default.FolderOpen,
            testTag = "files_browse_button",
            onClick = onBrowseFiles
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * Music / Audio Curtain content: direct content of [AetherConversationCurtain]
 * when [CurtainState.MUSIC] is active.
 *
 * Distinct from Voice Notes and Video Notes. Preserves track metadata (title, artist, duration).
 */
@Composable
fun MusicCurtainContent(
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onBrowseAudio: () -> Unit,
    onSendTrack: (uri: Uri, title: String, artist: String, durationSec: Int) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalAetherColors.current

    val hasAudioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    var tracks by remember { mutableStateOf<List<MusicTrackItem>>(emptyList()) }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            tracks = withContext(Dispatchers.IO) {
                queryRecentAudioTracks(context)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("curtain_music_content")
    ) {
        AttachmentCurtainHeader(
            title = "Music & Audio",
            onBack = onBack,
            onCancel = onCancel,
            backTestTag = "music_back",
            cancelTestTag = "music_cancel"
        )
        Text(
            text = "Send audio tracks or music files with title, artist and duration metadata.",
            fontFamily = ManropeFontFamily,
            fontSize = 12.5.sp,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))

        AttachmentActionRow(
            title = "Browse audio",
            subtitle = "Select songs or audio recordings using system picker",
            icon = Icons.Default.Headphones,
            testTag = "music_browse_button",
            onClick = onBrowseAudio
        )

        // If local tracks are available from MediaStore, list them
        if (tracks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Recent tracks",
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AetherEmber.Shapes.M)
                            .background(colors.surfaceHighlight)
                            .clickable {
                                onSendTrack(
                                    track.uri,
                                    track.title,
                                    track.artist,
                                    (track.durationMs / 1000).toInt().coerceAtLeast(1)
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("music_track_${track.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.5.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val secs = (track.durationMs / 1000).toInt()
                        Text(
                            text = String.format(java.util.Locale.US, "%d:%02d", secs / 60, secs % 60),
                            fontFamily = ManropeFontFamily,
                            fontSize = 11.sp,
                            color = colors.textTertiary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** Queries recent audio tracks from MediaStore. */
private fun queryRecentAudioTracks(context: Context): List<MusicTrackItem> {
    val items = mutableListOf<MusicTrackItem>()
    try {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 1000"
        context.contentResolver.query(
            uri,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            var count = 0
            while (cursor.moveToNext() && count < 25) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Audio Track"
                val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                val duration = cursor.getLong(durCol)
                val trackUri = ContentUris.withAppendedId(uri, id)
                items.add(MusicTrackItem(id, trackUri, title, artist, duration))
                count++
            }
        }
    } catch (e: Throwable) {
        Log.e(TAG, "Failed to query audio MediaStore", e)
    }
    return items
}
