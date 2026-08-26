package com.foresightlabs.aether.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.daily.AetherDaily
import com.foresightlabs.aether.domain.daily.DailyLine
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatFilterCategory
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.domain.presence.ActiveNow
import com.foresightlabs.aether.domain.presence.ActiveNowState
import com.foresightlabs.aether.domain.presence.ActivePerson
import com.foresightlabs.aether.ui.components.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.components.AetherAvatar
import com.foresightlabs.aether.ui.components.AetherSearchPill
import com.foresightlabs.aether.ui.components.ChatRow
import com.foresightlabs.aether.ui.design.AetherAccent
import com.foresightlabs.aether.ui.design.AetherChip
import com.foresightlabs.aether.ui.design.AetherEmptyState
import com.foresightlabs.aether.ui.design.AetherIconButton
import com.foresightlabs.aether.ui.design.AetherNavItem
import com.foresightlabs.aether.ui.design.AetherNavPill
import com.foresightlabs.aether.ui.design.AetherNavPillDefaults
import com.foresightlabs.aether.ui.design.AetherSheet
import com.foresightlabs.aether.ui.design.AetherSheetDefaults
import com.foresightlabs.aether.ui.design.HomeLayout
import com.foresightlabs.aether.ui.design.PresenceDensity
import com.foresightlabs.aether.ui.design.PresenceStripTokens
import com.foresightlabs.aether.ui.design.SheetAnchor
import com.foresightlabs.aether.ui.design.SheetAnchors
import com.foresightlabs.aether.ui.design.rememberAetherSheetState
import com.foresightlabs.aether.ui.design.aetherFrostSource
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.theme.SpaceGroteskFontFamily
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Phase 1 — the Living Home.
 *
 * Three layers, in depth order:
 *  1. Living Atmosphere, spatially stable behind everything
 *  2. the personal hero: greeting, Aether Daily, Active Now
 *  3. the foreground conversations sheet, which physically moves
 *
 * The dock is fixed near the safe bottom and belongs to none of them.
 */
@Composable
fun HomeScreen(
    chats: List<Chat>,
    currentUser: User?,
    connection: ConnectionStatus,
    isLoading: Boolean,
    onChatClick: (Chat) -> Unit,
    onNavigateToCalls: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNewMessageClick: () -> Unit,
    onNavigateToPulse: () -> Unit = {},
    dockSelectedKey: String = HOME_KEY,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val colors = LocalAetherColors.current
    val frostState = rememberAetherFrostState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ChatFilterCategory.PEOPLE) }
    var heroHeightPx by remember { mutableIntStateOf(0) }
    var heroCoreHeightPx by remember { mutableIntStateOf(0) }
    var requestSearchFocus by remember { mutableStateOf(false) }

    val sheetState = rememberAetherSheetState(SheetAnchor.RESTING)
    val listState = rememberLazyListState()

    // Real presence only. Never a fabricated dot.
    val activeNow = remember(chats) { ActiveNow.from(chats) }

    // Stable for the whole local day, offline, no external quote source.
    val dailyLine = rememberAetherDaily()

    val visibleChats = remember(chats, selectedCategory, searchQuery) {
        chats.filter { chat ->
            selectedCategory.matches(chat) && matchesQuery(chat, searchQuery)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val topInsetPx = with(density) { statusBarInset().toPx() }
        val preferredViewportPx = with(density) {
            (HomeLayout.PreferredChatViewport + AetherNavPillDefaults.Height).toPx()
        }
        val floorViewportPx = with(density) {
            (HomeLayout.MinimumChatViewport + AetherNavPillDefaults.Height).toPx()
        }
        val minAtmospherePx = with(density) { AetherSheetDefaults.MinAtmosphereReveal.toPx() }
        val relaxedExtraPx = with(density) { AetherSheetDefaults.RelaxedExtraAtmosphere.toPx() }

        // A narrow display must not cost the user real presence data. Pick the
        // densest strip that still leaves a usable conversation viewport, trading
        // list rows down to a floor before ever dropping the strip. Decided from the
        // hero core measurement only, so the choice cannot feed back into itself.
        val homeFit = HomeLayout.resolve(
            containerHeightPx = containerHeightPx,
            topInsetPx = topInsetPx,
            heroCoreHeightPx = heroCoreHeightPx.toFloat(),
            hasPresence = activeNow != ActiveNowState.Empty,
            comfortableAllowancePx = with(density) {
                PresenceStripTokens.verticalAllowance(PresenceDensity.COMFORTABLE).toPx()
            },
            compactAllowancePx = with(density) {
                PresenceStripTokens.verticalAllowance(PresenceDensity.COMPACT).toPx()
            },
            preferredViewportPx = preferredViewportPx,
            floorViewportPx = floorViewportPx
        )
        val presenceDensity = homeFit.presence
        val minChatViewportPx = homeFit.minViewportPx

        // Anchors come from what was actually measured — hero content, available
        // height and safe insets — never from a fixed screen fraction.
        val anchors = remember(
            containerHeightPx, heroHeightPx, topInsetPx,
            minChatViewportPx, minAtmospherePx, relaxedExtraPx
        ) {
            val measuredHeroBottomPx = if (heroHeightPx > 0) {
                topInsetPx + heroHeightPx.toFloat()
            } else {
                0f
            }
            SheetAnchors.derive(
                containerHeightPx = containerHeightPx,
                heroBottomPx = measuredHeroBottomPx,
                topInsetPx = topInsetPx,
                minChatViewportPx = minChatViewportPx,
                minAtmosphereRevealPx = minAtmospherePx,
                relaxedExtraPx = relaxedExtraPx
            )
        }
        LaunchedEffect(anchors) { sheetState.updateAnchors(anchors) }

        // The luminous region is sized from the sheet's relaxed anchor so the
        // atmosphere stays put behind the sheet instead of resizing with it.
        val heroFraction = if (containerHeightPx > 0f && anchors.isResolved) {
            ((anchors.peek + relaxedExtraPx) / containerHeightPx).coerceIn(0.3f, 1f)
        } else {
            0.7f
        }

        AetherAtmosphericBackground(
            modifier = Modifier.fillMaxSize(),
            heroFraction = heroFraction,
            frostState = frostState
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // --- Layer 2: personal hero -------------------------------------
                val expandProgress = sheetState.expandProgress
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aetherFrostSource(frostState)
                        .statusBarsPadding()
                        .onSizeChanged { heroHeightPx = it.height }
                        .graphicsLayer {
                            // Parallax: the hero drifts slightly as it is covered,
                            // and fades because something is physically over it.
                            translationY = -expandProgress * 28f
                            alpha = 1f - expandProgress * 0.85f
                        }
                ) {
                    // The core is measured on its own so the Active Now decision below
                    // cannot feed back into its own measurement and oscillate.
                    Column(modifier = Modifier.onSizeChanged { heroCoreHeightPx = it.height }) {
                        HomeTopBar(
                            currentUser = currentUser,
                            connection = connection,
                            onNewMessageClick = onNewMessageClick,
                            onNavigateToSettings = onNavigateToSettings
                        )

                        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space12))

                        HomeGreeting(currentUser = currentUser, daily = dailyLine)
                    }

                    if (presenceDensity != null) {
                        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space20))
                        PresenceSection(
                            state = activeNow,
                            density = presenceDensity,
                            onPersonClick = { onChatClick(it.chat) },
                            onNewClick = onNewMessageClick
                        )
                    }

                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space24))
                }

                // --- Layer 3: foreground conversations sheet ---------------------
                AetherSheet(
                    state = sheetState,
                    containerHeightPx = containerHeightPx,
                    label = "Conversations",
                    modifier = Modifier
                        .aetherFrostSource(frostState)
                        .testTag("conversations_sheet")
                ) {
                    AetherSearchPill(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search conversations",
                        requestFocus = requestSearchFocus,
                        onFocused = {
                            requestSearchFocus = false
                            // Search is spatially part of Home: it expands the sheet
                            // rather than navigating to a separate destination.
                            sheetState.animateTo(SheetAnchor.EXPANDED)
                        },
                        modifier = Modifier.padding(
                            start = AetherEmber.Spacing.Space16,
                            end = AetherEmber.Spacing.Space16,
                            top = AetherEmber.Spacing.Space8,
                            bottom = AetherEmber.Spacing.Space8
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(
                                start = AetherEmber.Spacing.Space16,
                                end = AetherEmber.Spacing.Space16,
                                top = AetherEmber.Spacing.Space8,
                                bottom = AetherEmber.Spacing.Space12
                            ),
                        horizontalArrangement = Arrangement.spacedBy(AetherEmber.Spacing.Space8)
                    ) {
                        ChatFilterCategory.entries.forEach { category ->
                            AetherChip(
                                label = category.label,
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                modifier = Modifier.testTag("filter_${category.name.lowercase()}")
                            )
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(sheetState.nestedScrollConnection())
                            .testTag("conversation_list"),
                        contentPadding = PaddingValues(
                            top = AetherEmber.Spacing.Space4,
                            bottom = with(density) {
                                (sheetState.bottomOverflow).toDp()
                            } + AetherNavPillDefaults.Height + AetherEmber.Spacing.Space40
                        )
                    ) {
                        items(visibleChats, key = { it.id }) { chat ->
                            ChatRow(chat = chat, onClick = { onChatClick(chat) })
                            HorizontalDivider(
                                color = colors.divider,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 78.dp, end = 16.dp)
                            )
                        }

                        if (visibleChats.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillParentMaxHeight(0.8f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    HomeEmptyState(
                                        isLoading = isLoading,
                                        hasAnyChats = chats.isNotEmpty(),
                                        query = searchQuery,
                                        category = selectedCategory
                                    )
                                }
                            }
                        }
                    }
                }

                // --- Dock: fixed near the safe bottom, outside the sheet ---------
                AetherNavPill(
                    items = listOf(
                        AetherNavItem(
                            key = HOME_KEY,
                            icon = Icons.Default.ChatBubble,
                            contentDescription = "Chats",
                            onClick = { sheetState.animateTo(SheetAnchor.RESTING) }
                        ),
                        AetherNavItem(
                            key = "pulse",
                            icon = Icons.Default.AutoAwesome,
                            contentDescription = "Pulse",
                            onClick = onNavigateToPulse
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
                    selectedKey = dockSelectedKey,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .testTag("home_dock"),
                    frostState = frostState
                )
            }
        }
    }
}

private const val HOME_KEY = "chats"

@Composable
private fun HomeTopBar(
    currentUser: User?,
    connection: ConnectionStatus,
    onNewMessageClick: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = AetherEmber.Spacing.Space20,
                vertical = AetherEmber.Spacing.Space4
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color(0x55FFFFFF), CircleShape)
                .padding(2.dp)
                .clickable { onNavigateToSettings() }
                .testTag("current_user_avatar"),
            contentAlignment = Alignment.Center
        ) {
            AetherAvatar(
                initials = currentUser?.avatarInitials ?: "A",
                gradient = currentUser?.avatarGradient
                    ?: listOf(AetherAccent.current, AetherAccent.subtle),
                size = 34.dp,
                photoPath = currentUser?.photoPath
            )
        }

        // Truthful connection state only; nothing is shown when connected.
        if (connection != ConnectionStatus.READY && connection != ConnectionStatus.UNKNOWN) {
            Box(
                modifier = Modifier
                    .clip(AetherEmber.Shapes.Pill)
                    .background(AetherEmber.Colors.GlassPill)
                    .border(0.75.dp, AetherEmber.Colors.GlassPillBorder, AetherEmber.Shapes.Pill)
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

        Row(horizontalArrangement = Arrangement.spacedBy(AetherEmber.Spacing.Space8)) {
            AetherIconButton(
                icon = Icons.Default.Edit,
                contentDescription = "New conversation",
                onClick = onNewMessageClick,
                iconSize = 18.dp,
                modifier = Modifier.testTag("new_conversation_button")
            )
            AetherIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                onClick = onNavigateToSettings,
                modifier = Modifier.testTag("settings_button")
            )
        }
    }
}

@Composable
private fun HomeGreeting(currentUser: User?, daily: DailyLine) {
    Column(modifier = Modifier.padding(horizontal = AetherEmber.Spacing.Space20)) {
        Text(
            text = localDateLabel().uppercase(),
            fontFamily = ManropeFontFamily,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.0.sp,
            color = AetherEmber.Colors.AtmosphereTextSecondary
        )

        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space4))

        // Space Grotesk is reserved for identity moments like this one.
        Text(
            text = greetingFor(currentUser),
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            color = AetherEmber.Colors.AtmosphereTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space20))

        Text(
            text = "AETHER DAILY",
            fontFamily = ManropeFontFamily,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.0.sp,
            color = AetherEmber.Colors.AtmosphereTextSecondary
        )
        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space4))
        Text(
            text = daily.text,
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
            color = AetherEmber.Colors.AtmosphereTextPrimary,
            modifier = Modifier.testTag("aether_daily")
        )
    }
}

@Composable
private fun PresenceSection(
    state: ActiveNowState,
    density: PresenceDensity,
    onPersonClick: (ActivePerson) -> Unit,
    onNewClick: () -> Unit
) {
    if (state == ActiveNowState.Empty) return

    // Every value below comes from the canonical strip tokens; the compact variant
    // takes smaller steps on the same grid rather than inventing its own spacing.
    val avatarSize = PresenceStripTokens.avatarSize(density)
    val itemSpacing = PresenceStripTokens.itemSpacing(density)
    val labelWidth = PresenceStripTokens.labelWidth(density)
    val labelSize = if (density == PresenceDensity.COMPACT) 12.sp else 12.5.sp

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            // "Recently active" and "Active now" are different claims and stay
            // visually and textually distinct.
            text = state.label.uppercase(),
            fontFamily = ManropeFontFamily,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = AetherEmber.Colors.AtmosphereTextSecondary,
            modifier = Modifier
                .padding(horizontal = AetherEmber.Spacing.Space20)
                .testTag("active_now_label")
        )

        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space8))

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("active_now_strip"),
            contentPadding = PaddingValues(horizontal = AetherEmber.Spacing.Space20),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item(key = "new") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(labelWidth)
                        .clickable { onNewClick() }
                        .clearAndSetSemantics {
                            contentDescription = "Start a new conversation"
                        }
                        .testTag("active_now_new")
                ) {
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .background(AetherEmber.Colors.GlassPill)
                            .border(1.5.dp, Color(0x60FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(avatarSize * 0.42f)
                        )
                    }
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space4))
                    Text(
                        text = "New",
                        fontFamily = ManropeFontFamily,
                        fontSize = labelSize,
                        fontWeight = FontWeight.SemiBold,
                        color = AetherEmber.Colors.AtmosphereTextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            items(state.people, key = { "person_${it.id}" }) { person ->
                val descriptionSuffix = when (state) {
                    is ActiveNowState.Online -> "online now"
                    is ActiveNowState.RecentlyActive -> "recently active"
                    ActiveNowState.Empty -> ""
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(labelWidth)
                        .clickable { onPersonClick(person) }
                        .clearAndSetSemantics {
                            contentDescription = "${person.name}, $descriptionSuffix"
                        }
                ) {
                    AetherAvatar(
                        initials = person.chat.avatarInitials,
                        gradient = person.chat.avatarGradient,
                        size = avatarSize,
                        // Only an exact TDLib online status lights the dot.
                        isOnline = state is ActiveNowState.Online,
                        hasUnseenPulse = person.hasUnseenPulse,
                        chatType = person.chat.type,
                        photoPath = person.chat.photoPath,
                        showGlowingRim = state is ActiveNowState.Online
                    )
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space4))
                    Text(
                        text = person.firstName,
                        fontFamily = ManropeFontFamily,
                        fontSize = labelSize,
                        fontWeight = FontWeight.SemiBold,
                        color = AetherEmber.Colors.AtmosphereTextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeEmptyState(
    isLoading: Boolean,
    hasAnyChats: Boolean,
    query: String,
    category: ChatFilterCategory
) {
    when {
        isLoading && !hasAnyChats -> AetherEmptyState(
            title = "Loading your conversations",
            detail = "Aether is syncing with Telegram."
        )
        query.isNotBlank() -> AetherEmptyState(
            title = "No conversations match “$query”",
            detail = "Try a different name or phrase."
        )
        !hasAnyChats -> AetherEmptyState(
            title = "No conversations yet",
            detail = "Start one and it will appear here."
        )
        else -> AetherEmptyState(
            title = "Nothing in ${category.label}",
            detail = when (category) {
                ChatFilterCategory.PEOPLE -> "Your direct conversations will show up here."
                ChatFilterCategory.GROUPS -> "You are not in any groups yet."
                ChatFilterCategory.CHANNELS -> "You are not following any channels yet."
                ChatFilterCategory.UNREAD -> "You are all caught up."
            }
        )
    }
}

@Composable
private fun rememberAetherDaily(): DailyLine {
    // Recomputed when the local calendar day rolls over.
    val epochDay = AetherDaily.localEpochDay()
    var day by remember { mutableStateOf(epochDay) }
    val inspecting = LocalInspectionMode.current
    LaunchedEffect(day, inspecting) {
        if (inspecting) return@LaunchedEffect
        kotlinx.coroutines.delay(AetherDaily.millisUntilLocalMidnight())
        day = AetherDaily.localEpochDay()
    }
    return remember(day) { AetherDaily.lineForEpochDay(day) }
}

@Composable
private fun statusBarInset() = WindowInsets.statusBars
    .asPaddingValues()
    .calculateTopPadding()

private fun matchesQuery(chat: Chat, query: String): Boolean {
    if (query.isBlank()) return true
    return chat.title.contains(query, ignoreCase = true) ||
        chat.lastMessageText.contains(query, ignoreCase = true)
}

private fun greetingFor(user: User?): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Still up"
    }
    val firstName = user?.name?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    return if (firstName == null) greeting else "$greeting,\n$firstName"
}

private fun localDateLabel(now: Date = Date()): String =
    SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
