package com.foresightlabs.aether.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatFilterCategory
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.components.AetherAvatar
import com.foresightlabs.aether.ui.components.AetherSearchPill
import com.foresightlabs.aether.ui.components.ChatRow
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

@Composable
fun ChatsScreen(
    chats: List<Chat>,
    currentUser: User?,
    connection: ConnectionStatus,
    isLoading: Boolean,
    onChatClick: (Chat) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToCalls: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNewMessageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ChatFilterCategory.ALL) }

    val filteredChats = chats.filter { chat ->
        val matchesCategory = when (selectedCategory) {
            ChatFilterCategory.ALL -> true
            ChatFilterCategory.DIRECT -> chat.type == ChatType.DIRECT || chat.type == ChatType.SAVED_MESSAGES
            ChatFilterCategory.GROUPS -> chat.type == ChatType.GROUP
            ChatFilterCategory.CHANNELS -> chat.type == ChatType.CHANNEL
            ChatFilterCategory.UNREAD -> chat.unreadCount > 0
        }
        val matchesSearch = if (searchQuery.isBlank()) true else {
            chat.title.contains(searchQuery, ignoreCase = true) ||
                    chat.lastMessageText.contains(searchQuery, ignoreCase = true)
        }
        matchesCategory && matchesSearch
    }

    // Frequent/recent contacts for the horizontal strip (real chats only)
    val recentContacts = remember(chats) {
        chats.filter { it.type == ChatType.DIRECT || it.type == ChatType.SAVED_MESSAGES }.take(10)
    }

    AetherAtmosphericBackground(
        modifier = modifier.fillMaxSize(),
        heroOnly = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // --- UPPER HERO REGION ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 14.dp)
            ) {
                // Top Action Bar with Profile, Connection Tag, Compose action, Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Avatar
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color(0x35FFFFFF), CircleShape)
                            .padding(2.dp)
                            .clickable { onNavigateToSettings() }
                            .testTag("current_user_avatar"),
                        contentAlignment = Alignment.Center
                    ) {
                        AetherAvatar(
                            initials = currentUser?.avatarInitials ?: "A",
                            gradient = currentUser?.avatarGradient ?: listOf(Color(0xFFFF9A4A), Color(0xFFFF7038)),
                            size = 34.dp,
                            photoPath = currentUser?.photoPath
                        )
                    }

                    // Connection status badge
                    if (connection != ConnectionStatus.READY && connection != ConnectionStatus.UNKNOWN) {
                        Box(
                            modifier = Modifier
                                .clip(AetherEmber.Shapes.Pill)
                                .background(Color(0x35000000))
                                .border(0.5.dp, Color(0x25FFFFFF), AetherEmber.Shapes.Pill)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = connection.label.uppercase(),
                                fontFamily = ManropeFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Right action slots: New Compose + Settings
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Relocated Compose / New Message Action Button in Top Hero
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0x28000000))
                                .border(1.dp, Color(0x24FFFFFF), CircleShape)
                                .clickable { onNewMessageClick() }
                                .testTag("top_compose_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "New Message",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Settings Icon
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0x28000000))
                                .border(1.dp, Color(0x24FFFFFF), CircleShape)
                                .clickable { onNavigateToSettings() }
                                .testTag("top_settings_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar Pill
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    AetherSearchPill(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search",
                        onMicClick = { onNavigateToSearch() }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Headline: "Let's Stay\nConnected"
                Text(
                    text = "Let's Stay\nConnected",
                    fontFamily = ManropeFontFamily,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 36.sp,
                    letterSpacing = (-0.6).sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 22.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Horizontal People Strip
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "+ Add" action button
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { onNewMessageClick() }
                                .testTag("hero_add_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x28000000))
                                    .border(1.5.dp, Color(0x55FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Chat",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add",
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    // Contacts list
                    items(recentContacts, key = { "recent_${it.id}" }) { contact ->
                        val firstName = contact.title.split(" ").firstOrNull() ?: contact.title
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onChatClick(contact) }
                        ) {
                            Box(
                                modifier = Modifier.size(54.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AetherAvatar(
                                    initials = contact.avatarInitials,
                                    gradient = contact.avatarGradient,
                                    size = 54.dp,
                                    isOnline = contact.directUser?.isOnline ?: false,
                                    chatType = contact.type,
                                    photoPath = contact.photoPath,
                                    showGlowingRim = true
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = firstName,
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // --- LOWER NEAR-BLACK RISING SHEET ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(AetherEmber.Shapes.RisingSheet)
                    .background(AetherEmber.Colors.Background)
                    .border(1.dp, Color(0x14FFFFFF), AetherEmber.Shapes.RisingSheet)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Glassy, Translucent, Geometric Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChatFilterCategory.entries.forEach { category ->
                            val isSelected = selectedCategory == category

                            val chipBg by animateColorAsState(
                                targetValue = if (isSelected) Color(0x35FF7038) else Color(0x15FFFFFF),
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                label = "chip_bg"
                            )
                            val chipBorderColor by animateColorAsState(
                                targetValue = if (isSelected) AetherEmber.Colors.BrightOrange else Color(0x22FFFFFF),
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                label = "chip_border"
                            )
                            val chipTextColor by animateColorAsState(
                                targetValue = if (isSelected) Color.White else Color(0xCCFFFFFF),
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                label = "chip_text"
                            )

                            Box(
                                modifier = Modifier
                                    .clip(AetherEmber.Shapes.Pill)
                                    .background(chipBg)
                                    .border(
                                        width = if (isSelected) 1.dp else 0.75.dp,
                                        color = chipBorderColor,
                                        shape = AetherEmber.Shapes.Pill
                                    )
                                    .clickable { selectedCategory = category }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                    .testTag("filter_tab_${category.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = category.label,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = chipTextColor
                                )
                            }
                        }
                    }

                    // Conversation List (Clean near-black rows)
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(filteredChats, key = { it.id }) { chat ->
                            ChatRow(
                                chat = chat,
                                onClick = { onChatClick(chat) }
                            )
                            HorizontalDivider(
                                color = AetherEmber.Colors.BorderSubtle,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 78.dp, end = 16.dp)
                            )
                        }

                        if (filteredChats.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when {
                                            isLoading -> "Loading conversations…"
                                            chats.isEmpty() -> "No conversations yet"
                                            else -> "No messages found"
                                        },
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 14.5.sp,
                                        color = AetherEmber.Colors.TextTertiary
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(96.dp))
                        }
                    }
                }

                // Sleek, Slim, Refined 52dp Floating Bottom Dock
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 40.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = AetherEmber.Shapes.Pill,
                                ambientColor = Color.Black.copy(alpha = 0.5f),
                                spotColor = Color.Black.copy(alpha = 0.8f)
                            )
                            .clip(AetherEmber.Shapes.Pill)
                            .background(Color(0xF0101012))
                            .border(0.75.dp, Color(0x22FFFFFF), AetherEmber.Shapes.Pill)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EmberDockItem(
                            icon = Icons.Default.ChatBubble,
                            contentDescription = "Chats",
                            isSelected = true,
                            onClick = { },
                            modifier = Modifier.weight(1f).testTag("bottom_nav_chats")
                        )

                        EmberDockItem(
                            icon = Icons.Default.Call,
                            contentDescription = "Calls",
                            isSelected = false,
                            onClick = onNavigateToCalls,
                            modifier = Modifier.weight(1f).testTag("bottom_nav_calls")
                        )

                        EmberDockItem(
                            icon = Icons.Default.Search,
                            contentDescription = "Search",
                            isSelected = false,
                            onClick = onNavigateToSearch,
                            modifier = Modifier.weight(1f).testTag("bottom_nav_search")
                        )

                        EmberDockItem(
                            icon = Icons.Default.Settings,
                            contentDescription = "Settings",
                            isSelected = false,
                            onClick = onNavigateToSettings,
                            modifier = Modifier.weight(1f).testTag("bottom_nav_settings")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmberDockItem(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) Color(0x28FFFFFF) else Color.Transparent,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "dock_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0x80FFFFFF),
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "dock_tint"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(AetherEmber.Shapes.Pill)
            .clickable { onClick() }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 36.dp)
                .clip(AetherEmber.Shapes.Pill)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
