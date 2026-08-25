package com.foresightlabs.aether.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.components.AetherSearchPill
import com.foresightlabs.aether.ui.components.ChatRow
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

@Composable
fun SearchScreen(
    results: List<Chat>,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onChatClick: (Chat) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Chats") }
    val filters = listOf("Chats")

    AetherAtmosphericBackground(
        modifier = modifier.fillMaxSize(),
        heroOnly = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Search Input Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x28000000))
                        .border(1.dp, Color(0x20FFFFFF), CircleShape)
                        .clickable { onBack() }
                        .testTag("search_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    AetherSearchPill(
                        value = query,
                        onValueChange = {
                            query = it
                            onQueryChange(it)
                        },
                        placeholder = "Search messages, chats, contacts…",
                        onClearClick = {
                            query = ""
                            onQueryChange("")
                        }
                    )
                }
            }

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val bg = if (isSelected) AetherEmber.Colors.Accent else AetherEmber.Colors.SurfaceElevated
                    val textColor = if (isSelected) Color.White else AetherEmber.Colors.TextSecondary

                    Box(
                        modifier = Modifier
                            .clip(AetherEmber.Shapes.Pill)
                            .background(bg)
                            .border(0.5.dp, if (isSelected) Color.Transparent else AetherEmber.Colors.Border, AetherEmber.Shapes.Pill)
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter,
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }

            // --- LOWER RESULTS CONTAINER ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(AetherEmber.Shapes.RisingSheet)
                    .background(AetherEmber.Colors.Background)
                    .border(1.dp, Color(0x14FFFFFF), AetherEmber.Shapes.RisingSheet)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (query.isBlank()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Search conversations loaded in Aether",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 14.sp,
                                    color = AetherEmber.Colors.TextTertiary
                                )
                            }
                        }
                    } else {
                        if (results.isNotEmpty()) {
                            item {
                                Text(
                                    text = "MATCHING CONVERSATIONS",
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AetherEmber.Colors.TextTertiary,
                                    letterSpacing = 1.2.sp,
                                    modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)
                                )
                            }

                            items(results, key = { it.id }) { chat ->
                                ChatRow(
                                    chat = chat,
                                    onClick = { onChatClick(chat) }
                                )
                                HorizontalDivider(
                                    color = AetherEmber.Colors.BorderSubtle,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 78.dp)
                                )
                            }
                        } else {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No results for \"$query\"",
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 14.5.sp,
                                        color = AetherEmber.Colors.TextTertiary
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
