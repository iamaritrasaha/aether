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
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.design.AetherBackButton
import com.foresightlabs.aether.ui.design.AetherChip
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.aetherFrostSource
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.design.AetherSectionLabel
import com.foresightlabs.aether.ui.design.AetherEmptyState
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import com.foresightlabs.aether.ui.theme.LocalAetherColors
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
    val colors = LocalAetherColors.current
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Chats") }
    val filters = listOf("Chats")
    val frostState = rememberAetherFrostState()

    AetherAtmosphericBackground(
        modifier = modifier.fillMaxSize(),
        frostState = frostState,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
          Column(
            modifier = Modifier.fillMaxSize().aetherFrostSource(frostState)
                .padding(top = aetherFloatingHeaderContentTopPadding())
          ) {
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
                    AetherChip(label = filter, selected = isSelected, onClick = { selectedFilter = filter })
                }
            }

            // --- LOWER RESULTS CONTAINER ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(AetherEmber.Shapes.RisingSheet)
                    .background(colors.background)
                    .border(1.dp, colors.border, AetherEmber.Shapes.RisingSheet)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (query.isBlank()) {
                        item {
                            AetherEmptyState(title = "Search conversations loaded in Aether")
                        }
                    } else {
                        if (results.isNotEmpty()) {
                            item {
                                AetherSectionLabel("Matching conversations", modifier = Modifier.padding(top = 16.dp))
                            }

                            items(results, key = { it.id }) { chat ->
                                ChatRow(
                                    chat = chat,
                                    onClick = { onChatClick(chat) }
                                )
                                HorizontalDivider(
                                    color = colors.divider,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 78.dp)
                                )
                            }
                        } else {
                            item {
                            AetherEmptyState(title = "No results for \"$query\"")
                            }
                        }
                    }
                }
            }
          }

          AetherFloatingHeader(
              modifier = Modifier.align(Alignment.TopCenter),
              frostState = frostState
          ) {
              AetherBackButton(
                  onClick = onBack,
                  modifier = Modifier.testTag("search_back_button")
              )
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
        }
    }
}
