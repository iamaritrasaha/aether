package com.foresightlabs.aether.ui.search
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
import com.foresightlabs.aether.ui.design.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.design.AetherSearchPill
import com.foresightlabs.aether.ui.common.ChatRow
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.design.AetherBackButton
import com.foresightlabs.aether.ui.design.AetherChip
import com.foresightlabs.aether.ui.design.AetherFloatingHeader
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.design.AetherSectionLabel
import com.foresightlabs.aether.ui.design.AetherEmptyState
import com.foresightlabs.aether.ui.design.aetherFloatingHeaderContentTopPadding
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextOverflow
import com.foresightlabs.aether.domain.search.GlobalMessageHit
import com.foresightlabs.aether.domain.search.GlobalSearchState
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

@Composable
fun SearchScreen(
    results: List<Chat>,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onChatClick: (Chat) -> Unit,
    modifier: Modifier = Modifier,
    state: GlobalSearchState = GlobalSearchState.Idle,
    onMessageClick: (GlobalMessageHit) -> Unit = {},
    onLoadMoreMessages: () -> Unit = {}
) {
    val colors = LocalAetherColors.current
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(SearchCategory.ALL) }
    // Categories are what Telegram actually answers separately, not display buckets.
    val filters = SearchCategory.entries
    val frostState = rememberAetherFrostState()

    Box(modifier = modifier.fillMaxSize()) {
        AetherAtmosphericBackground(
            modifier = Modifier.fillMaxSize(),
            frostState = frostState
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
              Column(
                modifier = Modifier.fillMaxSize()
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
                        AetherChip(
                            label = filter.label,
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            modifier = Modifier.testTag("search_filter_${filter.name.lowercase()}")
                        )
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
                                AetherEmptyState(title = "Search chats, people and messages")
                            }
                        } else {
                            val showChats = selectedFilter != SearchCategory.MESSAGES
                            val showPeople = selectedFilter != SearchCategory.MESSAGES
                            val showMessages = selectedFilter != SearchCategory.CHATS

                            if (showChats && state.chats.isNotEmpty()) {
                                item(key = "label_chats") {
                                    AetherSectionLabel(
                                        "Conversations",
                                        modifier = Modifier.padding(top = 16.dp)
                                    )
                                }
                                items(state.chats, key = { "chat_${it.id}" }) { chat ->
                                    ChatRow(chat = chat, onClick = { onChatClick(chat) })
                                    SearchDivider(colors.divider)
                                }
                            }

                            if (showPeople && state.contacts.isNotEmpty()) {
                                item(key = "label_people") {
                                    AetherSectionLabel(
                                        "People",
                                        modifier = Modifier.padding(top = 16.dp)
                                    )
                                }
                                items(state.contacts, key = { "person_${it.id}" }) { chat ->
                                    ChatRow(chat = chat, onClick = { onChatClick(chat) })
                                    SearchDivider(colors.divider)
                                }
                            }

                            if (showMessages && state.messages.isNotEmpty()) {
                                item(key = "label_messages") {
                                    AetherSectionLabel(
                                        if (state.messagesTotal > 0) {
                                            "Messages · ${state.messagesTotal}"
                                        } else {
                                            "Messages"
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    )
                                }
                                items(
                                    state.messages,
                                    key = { "msg_${it.message.chatId}_${it.message.id}" }
                                ) { hit ->
                                    MessageHitRow(hit = hit, onClick = { onMessageClick(hit) })
                                    SearchDivider(colors.divider)
                                }
                                if (state.hasMoreMessages) {
                                    item(key = "messages_more") {
                                        LaunchedEffect(state.messagesCursor) { onLoadMoreMessages() }
                                    }
                                }
                            }

                            if (state.isLoading && !state.hasAnyResult) {
                                item(key = "searching") {
                                    AetherEmptyState(title = "Searching…")
                                }
                            } else if (state.error != null && !state.hasAnyResult) {
                                item(key = "search_error") {
                                    AetherEmptyState(title = state.error!!)
                                }
                            } else if (state.isEmptyResult) {
                                item(key = "search_empty") {
                                    AetherEmptyState(title = "No results for \"$query\"")
                                }
                            }
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


/** The result categories Telegram answers separately. */
enum class SearchCategory(val label: String) {
    ALL("All"),
    CHATS("Chats"),
    MESSAGES("Messages")
}

@Composable
private fun SearchDivider(color: Color) {
    HorizontalDivider(
        color = color,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 78.dp)
    )
}

/**
 * One message found by global search.
 *
 * Shows the conversation it came from, because a matching line with no context is
 * not a usable result.
 */
@Composable
private fun MessageHitRow(hit: GlobalMessageHit, onClick: () -> Unit) {
    val colors = LocalAetherColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("search_message_${hit.message.id}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hit.chatTitle,
                fontFamily = ManropeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = hit.message.timestamp,
                fontFamily = ManropeFontFamily,
                fontSize = 11.sp,
                color = colors.textTertiary
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = hit.message.text,
            fontFamily = ManropeFontFamily,
            fontSize = 13.sp,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
