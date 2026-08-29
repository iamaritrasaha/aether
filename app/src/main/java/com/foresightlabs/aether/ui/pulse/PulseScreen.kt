package com.foresightlabs.aether.ui.pulse
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.foresightlabs.aether.domain.model.StoryPrivacy
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.domain.model.UserPulse
import com.foresightlabs.aether.ui.design.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.design.AetherAvatar
import com.foresightlabs.aether.ui.design.AetherElevation
import com.foresightlabs.aether.ui.design.AetherFrostState
import com.foresightlabs.aether.ui.design.AetherIconButton
import com.foresightlabs.aether.ui.design.AetherNavItem
import com.foresightlabs.aether.ui.design.AetherNavPill
import com.foresightlabs.aether.ui.design.AetherNavPillDefaults
import com.foresightlabs.aether.ui.design.AetherSurface
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.pulse.PulseViewerState
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.AetherType
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.LocalReducedMotion
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import java.io.File
import java.io.FileOutputStream

@Composable
fun PulseScreen(
    myPulse: UserPulse?,
    pulses: List<UserPulse>,
    canPostPulse: Boolean,
    currentUser: User?,
    viewerState: PulseViewerState?,
    isPosting: Boolean,
    postError: String?,
    onOpenViewer: (UserPulse, Int) -> Unit,
    onCloseViewer: () -> Unit,
    onStoryChanged: (UserPulse, Int) -> Unit,
    onSendReaction: (Long, Int, String) -> Unit,
    onSendReply: (Long, String) -> Unit,
    onPostPulse: (String, String, StoryPrivacy, () -> Unit) -> Unit,
    onDeletePulse: (Int) -> Unit,
    onNavigateToChats: () -> Unit,
    onNavigateToCalls: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val atmosphere = LocalAtmosphere.current
    val colors = LocalAetherColors.current
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            showCreateDialog = true
        }
    }

    val unseenPulses = remember(pulses) { pulses.filter { it.hasUnseen } }
    val seenPulses = remember(pulses) { pulses.filter { !it.hasUnseen } }
    val listState = rememberLazyListState()
    val frostState = rememberAetherFrostState()

    Box(modifier = modifier.fillMaxSize()) {
        AetherAtmosphericBackground(
            modifier = Modifier.fillMaxSize(),
            heroFraction = 1f,
            frostState = frostState
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("pulse_list"),
                    contentPadding = PaddingValues(
                        top = 0.dp,
                        bottom = AetherNavPillDefaults.Height + AetherEmber.Spacing.Space40
                    )
                ) {
                    item(key = "pulse_header") {
                        PulsePageHeader(
                            onAddClick = { if (canPostPulse) photoPickerLauncher.launch("image/*") }
                        )
                    }
                    // --- YOUR PULSE ---
                    item(key = "my_pulse_section") {
                        PulseSectionHeader(title = "Your Pulse")
                        AetherSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AetherEmber.Spacing.ScreenHorizontal)
                                .clickable {
                                    if (myPulse != null && myPulse.stories.isNotEmpty()) {
                                        onOpenViewer(myPulse, 0)
                                    } else if (canPostPulse) {
                                        photoPickerLauncher.launch("image/*")
                                    }
                                },
                            elevation = AetherElevation.Surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(AetherEmber.Spacing.Space16),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AetherEmber.Spacing.Space16)
                            ) {
                                Box(
                                    modifier = Modifier.size(56.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AetherAvatar(
                                        initials = currentUser?.avatarInitials ?: "A",
                                        gradient = currentUser?.avatarGradient
                                            ?: listOf(atmosphere.accent, atmosphere.shadow),
                                        size = 56.dp,
                                        hasUnseenPulse = myPulse != null && myPulse.stories.isNotEmpty(),
                                        photoPath = myPulse?.latestStory?.mediaUrl ?: currentUser?.photoPath
                                    )
                                    if (myPulse == null || myPulse.stories.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(atmosphere.accent)
                                                .border(2.dp, colors.surface, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Your Pulse",
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (myPulse != null && myPulse.stories.isNotEmpty()) {
                                            "${myPulse.stories.size} active ${if (myPulse.stories.size == 1) "moment" else "moments"}"
                                        } else {
                                            "Share a moment with your people"
                                        },
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 13.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space20))
                    }

                    // --- RECENT UPDATES (Unseen) ---
                    if (unseenPulses.isNotEmpty()) {
                        item(key = "unseen_header") {
                            PulseSectionHeader(title = "Recent Updates")
                        }
                        items(unseenPulses, key = { "unseen_${it.chatId}" }) { pulse ->
                            PulseRowItem(
                                pulse = pulse,
                                onClick = { onOpenViewer(pulse, pulse.stories.indexOfFirst { it.id > pulse.maxReadStoryId }.coerceAtLeast(0)) },
                                modifier = Modifier.padding(horizontal = AetherEmber.Spacing.ScreenHorizontal, vertical = 4.dp)
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space20))
                        }
                    }

                    // --- VIEWED UPDATES ---
                    if (seenPulses.isNotEmpty()) {
                        item(key = "seen_header") {
                            PulseSectionHeader(title = "Viewed Updates")
                        }
                        items(seenPulses, key = { "seen_${it.chatId}" }) { pulse ->
                            PulseRowItem(
                                pulse = pulse,
                                onClick = { onOpenViewer(pulse, 0) },
                                modifier = Modifier.padding(horizontal = AetherEmber.Spacing.ScreenHorizontal, vertical = 4.dp)
                            )
                        }
                    }

                    // --- EMPTY STATE ---
                    if (unseenPulses.isEmpty() && seenPulses.isEmpty() && (myPulse == null || myPulse.stories.isEmpty())) {
                        item(key = "empty_pulses") {
                            PulseEmptyState()
                        }
                    }
                }

            }
        }

        // Compact Bottom Dock
        AetherNavPill(
            items = listOf(
                AetherNavItem(
                    key = "chats",
                    icon = Icons.Default.ChatBubble,
                    contentDescription = "Chats",
                    onClick = onNavigateToChats
                ),
                AetherNavItem(
                    key = "pulse",
                    icon = Icons.Default.AutoAwesome,
                    contentDescription = "Pulse",
                    onClick = {}
                ),
                AetherNavItem(
                    key = "calls",
                    icon = Icons.Default.Call,
                    contentDescription = "Calls",
                    onClick = onNavigateToCalls
                ),
                AetherNavItem(
                    key = "settings",
                    icon = Icons.Default.Settings,
                    contentDescription = "Settings",
                    onClick = onNavigateToSettings
                )
            ),
            selectedKey = "pulse",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .testTag("pulse_dock"),
            frostState = frostState
        )

        // Create Pulse Dialog
        if (showCreateDialog && selectedImageUri != null) {
            CreatePulseDialog(
                imageUri = selectedImageUri!!,
                isPosting = isPosting,
                error = postError,
                onDismiss = {
                    showCreateDialog = false
                    selectedImageUri = null
                },
                onPost = { caption, privacy ->
                    val tempFile = copyUriToTempFile(context, selectedImageUri!!)
                    if (tempFile != null) {
                        onPostPulse(tempFile.absolutePath, caption, privacy) {
                            showCreateDialog = false
                            selectedImageUri = null
                        }
                    }
                }
            )
        }

        // Full-Screen Interactive Pulse Viewer Overlay
        AnimatedVisibility(
            visible = viewerState != null,
            enter = fadeIn(animationSpec = tween(200)) + slideInVertically(initialOffsetY = { it / 4 }),
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { it / 4 })
        ) {
            if (viewerState != null) {
                PulseViewerModal(
                    viewerState = viewerState,
                    allPulses = pulses,
                    onClose = onCloseViewer,
                    onStoryChanged = onStoryChanged,
                    onSendReaction = onSendReaction,
                    onSendReply = onSendReply,
                    onDeletePulse = onDeletePulse
                )
            }
        }
    }
}

@Composable
private fun PulsePageHeader(onAddClick: () -> Unit) {
    val colors = LocalAetherColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = AetherEmber.Spacing.ScreenHorizontal)
            .padding(top = AetherEmber.Spacing.Space24, bottom = AetherEmber.Spacing.Space16),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Pulse",
                style = AetherType.ScreenTitle,
                color = colors.atmosphereTextPrimary
            )
            Text(
                text = "Stories from your people",
                style = AetherType.Caption,
                color = colors.atmosphereTextSecondary
            )
        }

        AetherIconButton(
            icon = Icons.Default.Add,
            contentDescription = "Add to Pulse",
            onClick = onAddClick,
            modifier = Modifier.testTag("pulse_add_button")
        )
    }
}

@Composable
private fun PulseSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontFamily = ManropeFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
        color = Color(0xD9FFFFFF),
        modifier = Modifier.padding(horizontal = AetherEmber.Spacing.ScreenHorizontal, vertical = 8.dp)
    )
}

@Composable
private fun PulseEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color(0xE6FFFFFF),
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No recent Pulses",
            fontFamily = ManropeFontFamily,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Stories shared by your contacts will appear here.",
            fontFamily = ManropeFontFamily,
            fontSize = 13.5.sp,
            color = Color(0xC0FFFFFF),
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun PulseRowItem(
    pulse: UserPulse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    AetherSurface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = AetherElevation.Surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AetherEmber.Spacing.Space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AetherEmber.Spacing.Space12)
        ) {
            AetherAvatar(
                initials = pulse.avatarInitials,
                gradient = pulse.avatarGradient,
                size = 50.dp,
                isOnline = pulse.isOnline,
                hasUnseenPulse = pulse.hasUnseen,
                photoPath = pulse.latestStory?.mediaUrl ?: pulse.photoPath
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pulse.name,
                    fontFamily = ManropeFontFamily,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${pulse.stories.size} ${if (pulse.stories.size == 1) "moment" else "moments"}",
                    fontFamily = ManropeFontFamily,
                    fontSize = 12.5.sp,
                    color = if (pulse.hasUnseen) colors.textSecondary else colors.textTertiary
                )
            }
        }
    }
}

@Composable
private fun CreatePulseDialog(
    imageUri: Uri,
    isPosting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPost: (String, StoryPrivacy) -> Unit
) {
    val colors = LocalAetherColors.current
    val atmosphere = LocalAtmosphere.current
    var caption by remember { mutableStateOf("") }
    var selectedPrivacy by remember { mutableStateOf(StoryPrivacy.EVERYONE) }

    Dialog(
        onDismissRequest = { if (!isPosting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New Pulse",
                        fontFamily = ManropeFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    AetherIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        onClick = onDismiss,
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Image Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(AetherEmber.Shapes.L)
                        .background(colors.surfaceElevated)
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Pulse Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Caption Input
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = { Text("Add a caption...", color = colors.textTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AetherEmber.Shapes.M,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = atmosphere.accent,
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Privacy selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StoryPrivacy.entries.forEach { privacy ->
                        val isSelected = privacy == selectedPrivacy
                        Box(
                            modifier = Modifier
                                .clip(AetherEmber.Shapes.Pill)
                                .background(if (isSelected) atmosphere.accent.copy(alpha = 0.25f) else colors.surface)
                                .border(
                                    1.dp,
                                    if (isSelected) atmosphere.accent else colors.borderSubtle,
                                    AetherEmber.Shapes.Pill
                                )
                                .clickable { selectedPrivacy = privacy }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = privacy.label,
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else colors.textSecondary
                            )
                        }
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        fontFamily = ManropeFontFamily,
                        fontSize = 12.sp,
                        color = AetherEmber.Colors.Error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Share Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(AetherEmber.Shapes.Pill)
                        .background(if (isPosting) atmosphere.accent.copy(alpha = 0.5f) else atmosphere.accent)
                        .clickable(enabled = !isPosting) { onPost(caption, selectedPrivacy) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "Share to Pulse",
                            fontFamily = ManropeFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseViewerModal(
    viewerState: PulseViewerState,
    allPulses: List<UserPulse>,
    onClose: () -> Unit,
    onStoryChanged: (UserPulse, Int) -> Unit,
    onSendReaction: (Long, Int, String) -> Unit,
    onSendReply: (Long, String) -> Unit,
    onDeletePulse: (Int) -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val currentPulse = viewerState.pulse
    val stories = currentPulse.stories
    var currentStoryIndex by remember(currentPulse) { mutableIntStateOf(viewerState.initialStoryIndex) }
    val currentStory = stories.getOrNull(currentStoryIndex) ?: return

    var isPaused by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    val progress = remember { Animatable(0f) }

    val pulseIndex = allPulses.indexOfFirst { it.chatId == currentPulse.chatId }

    fun goToNextStory() {
        if (currentStoryIndex < stories.size - 1) {
            val next = currentStoryIndex + 1
            currentStoryIndex = next
            onStoryChanged(currentPulse, next)
        } else if (pulseIndex != -1 && pulseIndex < allPulses.size - 1) {
            val nextPulse = allPulses[pulseIndex + 1]
            onStoryChanged(nextPulse, 0)
        } else {
            onClose()
        }
    }

    fun goToPrevStory() {
        if (currentStoryIndex > 0) {
            val prev = currentStoryIndex - 1
            currentStoryIndex = prev
            onStoryChanged(currentPulse, prev)
        } else if (pulseIndex > 0) {
            val prevPulse = allPulses[pulseIndex - 1]
            onStoryChanged(prevPulse, (prevPulse.stories.size - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(currentStoryIndex, currentPulse.chatId, isPaused) {
        if (isPaused) return@LaunchedEffect
        progress.snapTo(0f)
        val duration = if (reducedMotion) 100 else 5000
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = duration, easing = LinearEasing)
        )
        goToNextStory()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPaused = true
                        tryAwaitRelease()
                        isPaused = false
                    },
                    onTap = { offset ->
                        val width = size.width
                        if (offset.x > width * 0.4f) {
                            goToNextStory()
                        } else {
                            goToPrevStory()
                        }
                    }
                )
            }
    ) {
        // Media Image / Gradient
        if (!currentStory.mediaUrl.isNullOrBlank()) {
            AsyncImage(
                model = currentStory.mediaUrl,
                contentDescription = "Story Media",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                currentPulse.avatarGradient.firstOrNull() ?: Color(0xFF1E293B),
                                Color(0xFF0A0F1D)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentStory.caption.ifBlank { "Pulse" },
                    fontFamily = ManropeFontFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }

        // Top Progress Bars & Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Segmented progress bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stories.forEachIndexed { index, _ ->
                    val segmentProgress = when {
                        index < currentStoryIndex -> 1f
                        index == currentStoryIndex -> progress.value
                        else -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(segmentProgress)
                                .height(2.5.dp)
                                .background(Color.White)
                        )
                    }
                }
            }

            // User Info Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AetherAvatar(
                        initials = currentPulse.avatarInitials,
                        gradient = currentPulse.avatarGradient,
                        size = 36.dp,
                        photoPath = currentPulse.photoPath
                    )
                    Column {
                        Text(
                            text = currentPulse.name,
                            fontFamily = ManropeFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (currentStory.isForCloseFriends) {
                            Text(
                                text = "Close Friends",
                                fontFamily = ManropeFontFamily,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (currentPulse.isMine) {
                        AetherIconButton(
                            icon = Icons.Default.Delete,
                            contentDescription = "Delete Pulse",
                            onClick = { onDeletePulse(currentStory.id); goToNextStory() },
                            tint = Color.White,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                    }
                    AetherIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        onClick = onClose,
                        tint = Color.White,
                        size = 36.dp,
                        iconSize = 18.dp
                    )
                }
            }
        }

        // Bottom Caption & Interactions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (currentStory.caption.isNotBlank() && currentStory.mediaUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AetherEmber.Shapes.M)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = currentStory.caption,
                        fontFamily = ManropeFontFamily,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Quick Reactions
            if (!currentPulse.isMine) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("🔥", "❤️", "😂", "👏", "😮", "😢").forEach { emoji ->
                        val isSelected = currentStory.reactionEmoji == emoji
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White.copy(alpha = 0.3f) else Color(0x35000000))
                                .clickable { onSendReaction(currentPulse.chatId, currentStory.id, emoji) }
                                .padding(8.dp)
                        ) {
                            Text(text = emoji, fontSize = 20.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Reply Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { Text("Reply to ${currentPulse.name}...", color = Color(0x99FFFFFF)) },
                        modifier = Modifier.weight(1f),
                        shape = AetherEmber.Shapes.Pill,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            focusedContainerColor = Color(0x40000000),
                            unfocusedContainerColor = Color(0x40000000)
                        ),
                        singleLine = true
                    )
                    AetherIconButton(
                        icon = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Reply",
                        onClick = {
                            if (replyText.isNotBlank()) {
                                onSendReply(currentPulse.chatId, replyText)
                                replyText = ""
                            }
                        },
                        tint = Color.White,
                        background = Color(0x60000000)
                    )
                }
            }
        }
    }
}

private fun copyUriToTempFile(context: android.content.Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = File.createTempFile("pulse_", ".jpg", context.cacheDir)
        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }
        tempFile
    } catch (_: Exception) {
        null
    }
}
