package com.foresightlabs.aether.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.foresightlabs.aether.domain.model.CallHistoryItem
import com.foresightlabs.aether.domain.model.CallHistoryUiState
import com.foresightlabs.aether.domain.model.CallOutcome
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.components.AetherAvatar
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.design.AetherBackButton
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.AetherIconButton
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import com.foresightlabs.aether.ui.design.rememberAetherFloatingHeaderScrollFraction
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.OnlineGreen
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily

@Composable
fun CallsScreen(
    historyState: CallHistoryUiState,
    onLoadNextPage: () -> Unit,
    onRefresh: () -> Unit,
    onInitiateCall: (Long) -> Unit,
    onNavigateToConversation: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingCallUserId by remember { mutableStateOf<Long?>(null) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val userId = pendingCallUserId
        if (isGranted && userId != null) {
            onInitiateCall(userId)
        }
        pendingCallUserId = null
    }

    val requestCallWithPermission: (Long) -> Unit = { userId ->
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            onInitiateCall(userId)
        } else {
            pendingCallUserId = userId
            showPermissionRationale = true
        }
    }

    val frostState = rememberAetherFrostState()
    val listState = rememberLazyListState()
    val headerScrollFraction = rememberAetherFloatingHeaderScrollFraction(listState)

    Box(modifier = modifier.fillMaxSize()) {
        AetherAtmosphericBackground(
            modifier = Modifier.fillMaxSize(),
            heroFraction = 1f,
            frostState = frostState
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = aetherFloatingHeaderContentTopPadding())
            ) {
                when (historyState) {
                    is CallHistoryUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AetherAccent.current)
                        }
                    }

                    is CallHistoryUiState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                        ) {
                            Text(
                                text = "Couldn't load calls",
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = historyState.message,
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.5.sp,
                                color = AetherEmber.Colors.TextTertiary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Box(
                                modifier = Modifier
                                    .clip(AetherEmber.Shapes.Pill)
                                    .background(AetherAccent.current)
                                    .clickable { onRefresh() }
                                    .padding(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Retry",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    is CallHistoryUiState.Empty -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(AetherAccent.subtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    tint = AetherAccent.current,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No calls yet",
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Your Telegram call history will appear here.",
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.5.sp,
                                color = AetherEmber.Colors.TextTertiary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is CallHistoryUiState.Content -> {
                        val shouldLoadMore by remember {
                            derivedStateOf {
                                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                                lastVisibleItem != null && lastVisibleItem.index >= historyState.items.size - 3
                            }
                        }

                        LaunchedEffect(shouldLoadMore) {
                            if (shouldLoadMore && historyState.hasMore && !historyState.isLoadingMore) {
                                onLoadNextPage()
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            itemsIndexed(
                                items = historyState.items,
                                key = { index, item -> "${item.id}_$index" }
                            ) { _, item ->
                                CallHistoryRow(
                                    item = item,
                                    onItemClick = { onNavigateToConversation(item.chatId) },
                                    onCallClick = { requestCallWithPermission(item.userId) }
                                )
                            }

                            if (historyState.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = AetherAccent.current,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AetherFloatingHeader(
            title = "Calls",
            modifier = Modifier.align(Alignment.TopCenter),
            scrollFraction = headerScrollFraction,
            frostState = frostState,
            navigation = {
                AetherBackButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("calls_back_button")
                )
            },
            actions = {
                AetherIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    onClick = onRefresh,
                    modifier = Modifier.testTag("calls_refresh_button")
                )
            }
        )

        // Permission Rationale Modal
        if (showPermissionRationale) {
            AlertDialog(
                onDismissRequest = {
                    showPermissionRationale = false
                    pendingCallUserId = null
                },
                title = {
                    Text(
                        text = "Allow microphone access",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        text = "Aether needs microphone access for voice calls.",
                        fontFamily = ManropeFontFamily,
                        color = Color(0xDDFFFFFF)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPermissionRationale = false
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    ) {
                        Text("Continue", color = AetherEmber.Colors.Accent, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPermissionRationale = false
                            pendingCallUserId = null
                        }
                    ) {
                        Text("Cancel", color = Color(0xAAFFFFFF))
                    }
                },
                containerColor = AetherEmber.Colors.SurfaceElevated,
                shape = AetherEmber.Shapes.L
            )
        }
    }
}

@Composable
private fun CallHistoryRow(
    item: CallHistoryItem,
    onItemClick: () -> Unit,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User Avatar
        AetherAvatar(
            initials = item.user?.avatarInitials ?: "?",
            gradient = item.user?.avatarGradient ?: listOf(Color(0xFF4DA3FF), Color(0xFF1D4ED8)),
            size = 48.dp,
            photoPath = item.user?.photoPath
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.user?.name ?: "Telegram Contact",
                fontFamily = ManropeFontFamily,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Direction / Outcome Icon
                val isMissed = item.outcome == CallOutcome.MISSED || item.outcome == CallOutcome.DECLINED
                val icon = when {
                    isMissed -> Icons.AutoMirrored.Filled.CallMissed
                    item.isOutgoing -> Icons.AutoMirrored.Filled.CallMade
                    else -> Icons.AutoMirrored.Filled.CallReceived
                }

                val tint = if (isMissed) Color(0xFFEF4444) else Color(0xAAFFFFFF)

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(15.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                val outcomeText = when (item.outcome) {
                    CallOutcome.MISSED -> "Missed"
                    CallOutcome.DECLINED -> "Declined"
                    CallOutcome.CANCELLED -> "Cancelled"
                    CallOutcome.FAILED -> "Failed"
                    CallOutcome.COMPLETED -> if (item.isOutgoing) "Outgoing" else "Incoming"
                }

                val fullText = if (item.formattedDuration.isNotBlank() && item.outcome == CallOutcome.COMPLETED) {
                    "$outcomeText • ${item.formattedDuration}"
                } else {
                    outcomeText
                }

                Text(
                    text = fullText,
                    fontFamily = ManropeFontFamily,
                    fontSize = 13.sp,
                    color = if (isMissed) Color(0xFFEF4444) else AetherEmber.Colors.TextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Timestamp & Call Button
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.formattedTimestamp,
                fontFamily = ManropeFontFamily,
                fontSize = 12.sp,
                color = AetherEmber.Colors.TextTertiary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF))
                    .clickable { onCallClick() }
                    .testTag("call_again_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = OnlineGreen,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
