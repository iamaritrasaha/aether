package com.foresightlabs.aether.ui.conversation
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.model.Message
import com.foresightlabs.aether.ui.design.AetherGlass
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScheduledMessagesSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onLoadScheduled: suspend () -> List<Message>,
    onSendNow: (Message) -> Unit,
    onReschedule: (Message, Int) -> Unit,
    onDelete: (Message) -> Unit
) {
    val colors = LocalAetherColors.current
    val scope = rememberCoroutineScope()
    var scheduledMessages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            isLoading = true
            scheduledMessages = onLoadScheduled()
            isLoading = false
        }
    }

    if (isVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                AetherGlass(
                    frostState = null,
                    shape = AetherEmber.Shapes.RisingSheet,
                    elevation = 12.dp,
                    emphasis = 0.25f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* keep taps inside sheet */ }
                        .padding(top = 16.dp)
                        .navigationBarsPadding()
                        .testTag("scheduled_messages_sheet")
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scheduled Messages",
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

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = colors.accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        } else if (scheduledMessages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No scheduled messages in this chat",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 14.sp,
                                    color = colors.textSecondary
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(scheduledMessages, key = { it.id }) { message ->
                                    ScheduledMessageCard(
                                        message = message,
                                        onSendNow = {
                                            onSendNow(message)
                                            scheduledMessages = scheduledMessages.filter { it.id != message.id }
                                        },
                                        onReschedule = { newEpochSeconds ->
                                            onReschedule(message, newEpochSeconds)
                                            // Refresh list after reschedule
                                            isLoading = true
                                            scope.launch {
                                                delay(300)
                                                scheduledMessages = onLoadScheduled()
                                                isLoading = false
                                            }
                                        },
                                        onDelete = {
                                            onDelete(message)
                                            scheduledMessages = scheduledMessages.filter { it.id != message.id }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledMessageCard(
    message: Message,
    onSendNow: () -> Unit,
    onReschedule: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAetherColors.current
    var showRescheduleDialog by remember { mutableStateOf(false) }

    val scheduledTimeFormatted = remember(message.dateSeconds) {
        if (message.dateSeconds > 0) {
            val date = Date(message.dateSeconds * 1000L)
            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(date)
        } else {
            "When online"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = scheduledTimeFormatted,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Reschedule Button
                IconButton(
                    onClick = { showRescheduleDialog = true },
                    modifier = Modifier.size(28.dp).testTag("reschedule_btn_${message.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Reschedule",
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                // Send Now Button
                IconButton(
                    onClick = onSendNow,
                    modifier = Modifier.size(28.dp).testTag("send_now_btn_${message.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send now",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp).testTag("delete_scheduled_btn_${message.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = message.text.ifBlank { "[${message.type}]" },
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            color = colors.textPrimary,
            maxLines = 3
        )
    }

    if (showRescheduleDialog) {
        RescheduleDialog(
            currentDateSeconds = message.dateSeconds,
            onDismiss = { showRescheduleDialog = false },
            onConfirm = { newSeconds ->
                onReschedule(newSeconds)
                showRescheduleDialog = false
            }
        )
    }
}

@Composable
private fun RescheduleDialog(
    currentDateSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val colors = LocalAetherColors.current
    val nowSeconds = (System.currentTimeMillis() / 1000).toInt()

    val options = listOf(
        "In 10 minutes" to (nowSeconds + 600),
        "In 1 hour" to (nowSeconds + 3600),
        "In 3 hours" to (nowSeconds + 10800),
        "Tomorrow (in 24h)" to (nowSeconds + 86400),
        "In 3 days" to (nowSeconds + 86400 * 3),
        "When user comes online" to 0
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        AetherGlass(
            frostState = null,
            shape = RoundedCornerShape(18.dp),
            elevation = 10.dp,
            emphasis = 0.2f,
            modifier = Modifier
                .width(320.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* keep taps inside */ }
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Reschedule Message",
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (label, epochSec) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceElevated)
                                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                                .clickable { onConfirm(epochSec) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontFamily = ManropeFontFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = ManropeFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
