package com.foresightlabs.aether.ui.home
import kotlin.math.pow
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.foresightlabs.aether.ui.home.HomeSelectionDock
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.foresightlabs.aether.domain.daily.AetherDaily
import com.foresightlabs.aether.domain.daily.DailyLine
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ConnectionStatus
import com.foresightlabs.aether.domain.model.User
import com.foresightlabs.aether.domain.presence.ActiveNow
import com.foresightlabs.aether.domain.presence.ActiveNowState
import com.foresightlabs.aether.domain.presence.ActivePerson
import com.foresightlabs.aether.ui.design.AetherAtmosphericBackground
import com.foresightlabs.aether.ui.design.AetherAvatar
import com.foresightlabs.aether.ui.design.AetherSearchPill
import com.foresightlabs.aether.domain.chats.ChatAction
import com.foresightlabs.aether.ui.home.ChatActionSheet
import com.foresightlabs.aether.ui.common.ChatRow
import com.foresightlabs.aether.ui.design.AetherConnectionMote
import com.foresightlabs.aether.ui.design.AetherEmptyState
import com.foresightlabs.aether.ui.design.LocalSceneHeightCache
import com.foresightlabs.aether.ui.design.LocalSceneOwnsDock
import com.foresightlabs.aether.ui.design.LocalSceneTransitionProgress
import com.foresightlabs.aether.ui.design.edgePx
import com.foresightlabs.aether.ui.home.PresenceDensity
import com.foresightlabs.aether.ui.home.PresenceStripTokens
import com.foresightlabs.aether.ui.design.rememberAetherFrostState
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.ManropeFontFamily
import com.foresightlabs.aether.ui.weather.AetherWeatherVisuals
import com.foresightlabs.aether.ui.weather.buildWeatherHeroState
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Home is one composition in two regions.
 *
 * The upper region is atmosphere: the greeting, the day's line and the people who
 * are actually around, set directly on the Living Atmosphere with no panel of any
 * kind beneath them. The lower region is content: an opaque near-black (or
 * porcelain) surface carrying the conversations, joined to the atmosphere by a
 * single large radius.
 *
 * There is deliberately no draggable sheet, no stack of glass cards and no second
 * toolbar. Chrome on this screen is one quiet search field, one compose action and
 * the dock.
 */
@Composable
fun HomeScreen(
    chats: List<Chat>,
    currentUser: User?,
    connection: ConnectionStatus,
    isLoading: Boolean,
    onChatClick: (Chat) -> Unit,
    onChatAction: (Chat, ChatAction) -> Unit = { _, _ -> },
    onNavigateToCalls: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNewMessageClick: () -> Unit,
    onNavigateToPulse: () -> Unit = {},
    folders: List<com.foresightlabs.aether.domain.model.ChatFolder> = listOf(com.foresightlabs.aether.domain.model.ChatFolder.Main),
    selectedFolder: com.foresightlabs.aether.domain.model.ChatFolder = com.foresightlabs.aether.domain.model.ChatFolder.Main,
    onSelectFolder: (com.foresightlabs.aether.domain.model.ChatFolder) -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
    onEditFolder: (Int, String) -> Unit = { _, _ -> },
    onDeleteFolder: (Int) -> Unit = {},
    onReorderFolders: (List<Int>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    val density = LocalDensity.current
    val frostState = rememberAetherFrostState()

    var searchQuery by remember { mutableStateOf("") }
    var showLocationPicker by remember { mutableStateOf(false) }
    var actionSheetChat by remember { mutableStateOf<Chat?>(null) }
    var selectedChatIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionActive = selectedChatIds.isNotEmpty()

    BackHandler(enabled = isSelectionActive) {
        selectedChatIds = emptySet()
    }

    // The hero is measured, not guessed, so the conversations behind it always
    // start clear of its lower edge whatever the greeting and strip come to.
    var heroHeightPx by remember { mutableIntStateOf(0) }

    val themeState = com.foresightlabs.aether.ui.theme.LocalAppThemeState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val listState = rememberLazyListState()

    // Real presence only. Never a fabricated dot.
    val activeNow = remember(chats) { ActiveNow.from(chats) }

    // Stable for the whole local day, offline, no external quote source.
    val dailyLine = rememberAetherDaily()

    // Primary Home feed shows 1:1 personal conversations only.
    val visibleChats = remember(chats, searchQuery) {
        chats.filter { chat ->
            chat.isPersonalChat && matchesQuery(chat, searchQuery)
        }
    }

    val atmosphere = LocalAtmosphere.current
    val weatherState = remember(atmosphere) {
        buildWeatherHeroState(atmosphere, atmosphere.palette)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // A short display trades strip size, then the strip itself, before it ever
        // trades away the conversation list.
        val presenceDensity: PresenceDensity? = when {
            activeNow == ActiveNowState.Empty -> null
            maxHeight < 600.dp -> null
            maxHeight < 720.dp -> PresenceDensity.COMPACT
            else -> PresenceDensity.COMFORTABLE
        }
        val greetingSize = if (maxHeight < 720.dp || maxWidth < 370.dp) 33.sp else 38.sp
        val heroHeightDp = with(density) { heroHeightPx.toDp() }

        // Null outside the persistent scene, in which case this screen sizes
        // itself exactly as it always did (standalone previews, screenshots,
        // tests). Inside the scene it is the shared Home ↔ Conversation morph:
        // 0 at rest here, moving toward 1 as a conversation opens over it.
        val sceneProgress = LocalSceneTransitionProgress.current
        val heightCache = LocalSceneHeightCache.current
        // "At rest" is judged loosely rather than at exactly 0 so a settling
        // spring's last fractional step does not flip this back to measuring
        // mode and jitter the layout on the final frame.
        val atRest = sceneProgress == null || sceneProgress < 0.02f
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val morphedHeroPx = if (!atRest && heightCache != null) {
            heightCache.edgePx(sceneProgress!!, containerHeightPx)
        } else null
        // Home's contents recede in a carefully staggered sequence as the panel
        // expands into the conversation canvas, rather than disappearing abruptly
        // all together. Every element derives its visual state continuously from
        // the same shared transition progress, so reversing or predictive back
        // seamlessly restores the entire hero without abrupt visibility flips.
        val p = sceneProgress?.coerceIn(0f, 1f) ?: 0f

        // 1. Surrounding chat rows soften early so the tapped chat's identity
        // can guide the eye into the arriving header.
        val chatsContentAlpha = if (sceneProgress == null) 1f else (1f - p / 0.38f).coerceIn(0f, 1f)
        val chatsTranslationY = if (sceneProgress == null) 0f else with(density) { (10.dp.toPx() * (p / 0.38f).coerceIn(0f, 1f)) }

        // 2. Top search & contextual controls begin receding first.
        val topControlsAlpha = if (sceneProgress == null) 1f else (1f - p / 0.45f).coerceIn(0f, 1f)
        val topControlsTranslationY = if (sceneProgress == null) 0f else with(density) { (-10.dp.toPx() * (p / 0.45f).coerceIn(0f, 1f)) }

        // 3. Greeting ("Good evening", Daily line, Quiet weather line) stays clearly
        // visible through the early movement, then moves gently upward and fades
        // smoothly all the way to p=0.82 without any hard visibility cutoffs.
        val greetingAlpha = if (sceneProgress == null) 1f else {
            val normalized = (p / 0.82f).coerceIn(0f, 1f)
            (1f - normalized.toDouble().pow(1.15).toFloat()).coerceIn(0f, 1f)
        }
        val greetingTranslationY = if (sceneProgress == null) 0f else with(density) {
            (-22.dp.toPx() * (p / 0.80f).coerceIn(0f, 1f))
        }
        val greetingScale = if (sceneProgress == null) 1f else {
            1f - 0.04f * (p / 0.80f).coerceIn(0f, 1f)
        }

        // 4. Presence strip (Active now) recedes gracefully between 0.05 and 0.60.
        val presenceProgress = ((p - 0.05f) / 0.55f).coerceIn(0f, 1f)
        val presenceAlpha = if (sceneProgress == null) 1f else (1f - presenceProgress)
        val presenceTranslationY = if (sceneProgress == null) 0f else with(density) {
            (-14.dp.toPx() * presenceProgress)
        }
        val presenceScale = if (sceneProgress == null) 1f else (1f - 0.04f * presenceProgress)

        // --- The rear layer: the conversations ---------------------------
        // The dark chat world is the back of the screen, not a sheet laid onto
        // it. It fills the window and needs no corner rounding of its own — the
        // hero in front is what shapes the boundary between them, and this is
        // the same surface that collapses into the conversation's composer dock.
        Box(
            modifier = Modifier
                .fillMaxSize()
                // The scene above the navigation graph already paints this surface
                // and keeps it mounted across the transition, so painting it again
                // here would be a second copy that fades independently.
                .then(
                    if (LocalSceneOwnsDock.current) Modifier
                    else Modifier.background(colors.background)
                )
                .testTag("conversations_surface")
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = chatsContentAlpha
                        translationY = chatsTranslationY
                    }
                    .testTag("conversation_list"),
                contentPadding = PaddingValues(
                    // Clears the hero in front of it, plus the overlap the hero
                    // hides, so the first conversation is never pressed against
                    // the curve.
                    top = heroHeightDp + ChatsRevealGap,
                    bottom = if (isSelectionActive) 80.dp else AetherEmber.Spacing.Space32
                )
            ) {
                items(visibleChats, key = { it.id }) { chat ->
                    ChatRow(
                        chat = chat,
                        onClick = {
                            if (isSelectionActive) {
                                selectedChatIds = if (chat.id in selectedChatIds) selectedChatIds - chat.id else selectedChatIds + chat.id
                            } else {
                                onChatClick(chat)
                            }
                        },
                        onLongPress = {
                            selectedChatIds = if (chat.id in selectedChatIds) selectedChatIds - chat.id else selectedChatIds + chat.id
                        },
                        isSelected = chat.id in selectedChatIds,
                        isSelectionActive = isSelectionActive,
                        sharedAvatar = true
                    )
                }

                if (visibleChats.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxHeight(0.6f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            HomeEmptyState(
                                isLoading = isLoading,
                                hasAnyPersonalChats = chats.any { it.isPersonalChat },
                                query = searchQuery
                            )
                        }
                    }
                }
            }

            // Selection Action Dock at the bottom of the black conversations layer
            AnimatedVisibility(
                visible = isSelectionActive,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 220)
                ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 180)
                ) + fadeOut(animationSpec = tween(durationMillis = 140)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val selectedChatsList = remember(chats, selectedChatIds) {
                    chats.filter { it.id in selectedChatIds }
                }
                HomeSelectionDock(
                    selectedChats = selectedChatsList,
                    onClearSelection = { selectedChatIds = emptySet() },
                    onChatAction = onChatAction
                )
            }
        }

        // --- The foreground layer: the hero -------------------------------
        // A panel lying on top of that dark world. It owns the curve: its lower
        // corners are rounded, so the conversations behind it appear a little
        // sooner at the edges than they do at the centre.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .then(
                    if (morphedHeroPx != null) {
                        // Mid-morph: an explicit height interpolated from the
                        // shared rest-height pair, so this panel and Conversation's
                        // canvas — reading the same two numbers and the same
                        // progress — are always the same rectangle, not two
                        // independently animated shapes that merely resemble it.
                        Modifier.height(with(density) { morphedHeroPx.toDp() })
                    } else {
                        // At rest: measured from its own content, exactly as
                        // before, and the result is banked for the next morph.
                        Modifier.onSizeChanged {
                            heroHeightPx = it.height
                            heightCache?.homeRestPx = it.height.toFloat()
                        }
                    }
                )
                .clip(
                    RoundedCornerShape(
                        bottomStart = HeroCornerRadius,
                        bottomEnd = HeroCornerRadius
                    )
                )
                .testTag("home_hero")
        ) {
                AetherAtmosphericBackground(
                    modifier = Modifier.matchParentSize(),
                    heroFraction = 1f,
                    enableAmbientMotion = true,
                    frostState = frostState
                ) {}

                    // Real weather still shapes this screen; it simply does so
                    // quietly, behind the greeting, instead of taking the stage.
                    AetherWeatherVisuals(
                        condition = weatherState.condition,
                        timeBand = weatherState.timeBand,
                        revealProgress = HeroWeatherPresence,
                        weatherState = weatherState,
                        modifier = Modifier.matchParentSize()
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = topControlsAlpha
                                    translationY = topControlsTranslationY
                                }
                        ) {
                            HomeHeroControls(
                                connection = connection,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                onNavigateToSettings = onNavigateToSettings
                            )
                        }

                        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space20))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = greetingAlpha
                                    translationY = greetingTranslationY
                                    scaleX = greetingScale
                                    scaleY = greetingScale
                                }
                        ) {
                            HomeGreeting(
                                currentUser = currentUser,
                                daily = dailyLine,
                                greetingSize = greetingSize,
                                weatherLine = weatherState.quietLine(),
                                onWeatherClick = { showLocationPicker = true }
                            )
                        }

                        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space24))

                        if (presenceDensity != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = presenceAlpha
                                        translationY = presenceTranslationY
                                        scaleX = presenceScale
                                        scaleY = presenceScale
                                    }
                            ) {
                                PresenceSection(
                                    state = activeNow,
                                    density = presenceDensity,
                                    onPersonClick = { onChatClick(it.chat) },
                                    onNewClick = onNewMessageClick
                                )
                            }
                        }

                        // The hero's own lower edge, deep enough that its rounded
                        // corners cut through empty atmosphere rather than through
                        // the last row of faces.
                        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space24))
                    }
        }

        ChatActionSheet(
            chat = actionSheetChat,
            onDismiss = { actionSheetChat = null },
            onAction = { action ->
                actionSheetChat?.let { onChatAction(it, action) }
            }
        )

        if (showLocationPicker) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000))
                    .clickable { showLocationPicker = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                com.foresightlabs.aether.ui.weather.AetherLocationPickerSheet(
                    currentMode = themeState.weatherLocationMode,
                    currentManualLocation = themeState.manualWeatherLocation,
                    onSelectAutomatic = {
                        themeState.clearManualWeatherLocation()
                        scope.launch {
                            themeState.weatherReading = com.foresightlabs.aether.ui.theme.AtmosphereWeatherService.read(
                                context = context,
                                locationMode = com.foresightlabs.aether.data.preferences.WeatherLocationMode.AUTOMATIC,
                                manualLocation = null,
                                forceRefresh = true
                            )
                        }
                    },
                    onSelectLocation = { location ->
                        themeState.setAndPersistManualWeatherLocation(location)
                        scope.launch {
                            themeState.weatherReading = com.foresightlabs.aether.ui.theme.AtmosphereWeatherService.read(
                                context = context,
                                locationMode = com.foresightlabs.aether.data.preferences.WeatherLocationMode.MANUAL,
                                manualLocation = location,
                                forceRefresh = true
                            )
                        }
                    },
                    onDismiss = { showLocationPicker = false }
                )
            }
        }
    }
}

/** How much of the weather scene the calm hero carries. Present, never theatrical. */
private const val HeroWeatherPresence = 0.26f

/** The foreground hero owns the curve; the conversations behind it do not. */
private val HeroCornerRadius = 34.dp

/** Breathing room under the hero's edge before the first conversation. */
private val ChatsRevealGap = 22.dp

/**
 * The whole of Home's top chrome: a quiet search lens, and the compact Settings control.
 *
 * Deliberately not an app bar — no avatar, no settings duplicate, no filled
 * container. Starting a conversation is the first item in the strip below;
 * Settings navigates directly to the settings screen.
 */
@Composable
private fun HomeHeroControls(
    connection: ConnectionStatus,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AetherEmber.Spacing.Space20,
                end = AetherEmber.Spacing.Space12,
                top = AetherEmber.Spacing.Space8
            )
    ) {
        val compactMote = maxWidth < 380.dp
        val reservedMoteWidth = if (compactMote) 96.dp else 132.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AetherSearchPill(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Search",
                onAtmosphere = true,
                height = 40.dp,
                modifier = Modifier
                    .width(178.dp)
                    .testTag("home_top_search")
            )

            Spacer(modifier = Modifier.weight(1f))

            // The wrapper reserves the expanded center space, so Search and Settings
            // do not move when the mote grows into its status capsule. The mote's
            // visible glyph remains small while its semantic hit area remains 48dp.
            Box(
                modifier = Modifier.width(reservedMoteWidth),
                contentAlignment = Alignment.Center
            ) {
                AetherConnectionMote(
                    rawStatus = connection,
                    compact = compactMote,
                    modifier = Modifier.testTag("home_status_mote")
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            HomeSettingsButton(
                onClick = onNavigateToSettings
            )
        }
    }
}

/**
 * Mac-like restrained optical control for opening Settings from Home.
 *
 * Compact circular control, clean gear glyph, visually light, low-contrast
 * neutral backing, subtle depth border.
 * 48dp minimum touch target, 40dp visible circular lens, 20dp icon.
 */
@Composable
fun HomeSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick
            )
            .semantics { this.contentDescription = "Settings" }
            .testTag("home_settings_button"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .border(
                    width = 0.5.dp,
                    color = Color(0x18FFFFFF),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = colors.atmosphereTextPrimary.copy(alpha = 0.88f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * The dominant element on Home: who you are and what today is, set straight on the
 * atmosphere with nothing behind it.
 */
@Composable
private fun HomeGreeting(
    currentUser: User?,
    daily: DailyLine,
    greetingSize: androidx.compose.ui.unit.TextUnit,
    weatherLine: String?,
    onWeatherClick: () -> Unit
) {
    val colors = LocalAetherColors.current
    Column(modifier = Modifier.padding(horizontal = AetherEmber.Spacing.Space20)) {
        Text(
            text = greetingFor(currentUser),
            fontFamily = ManropeFontFamily,
            fontSize = greetingSize,
            // Tight enough that two lines read as one confident block.
            lineHeight = greetingSize * 1.04f,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1.2).sp,
            color = colors.atmosphereTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("home_greeting")
        )

        Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space12))

        Text(
            text = daily.text,
            fontFamily = ManropeFontFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = colors.atmosphereTextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("aether_daily")
        )

        if (weatherLine != null) {
            Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space4))
            Text(
                text = weatherLine,
                fontFamily = ManropeFontFamily,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = colors.atmosphereTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(AetherEmber.Shapes.Pill)
                    .clickable { onWeatherClick() }
                    .testTag("home_weather_line")
            )
        }
    }
}

/**
 * The people genuinely around right now. One avatar, one name, one small presence
 * dot — no stacked rings, no decorative halo.
 */
@Composable
private fun PresenceSection(
    state: ActiveNowState,
    density: PresenceDensity,
    onPersonClick: (ActivePerson) -> Unit,
    onNewClick: () -> Unit
) {
    if (state == ActiveNowState.Empty) return

    val colors = LocalAetherColors.current
    val avatarSize = PresenceStripTokens.avatarSize(density)
    val itemSpacing = PresenceStripTokens.itemSpacing(density)
    val labelWidth = PresenceStripTokens.labelWidth(density)
    val labelSize = if (density == PresenceDensity.COMPACT) 11.5.sp else 12.sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The strip is the claim. Screen readers still hear which claim it is,
            // because "online now" and "recently active" are different facts.
            .semantics { contentDescription = state.label }
            .testTag("active_now_label")
    ) {
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
                        .testTag("active_now_new")
                        .clickable { onNewClick() }
                        .clearAndSetSemantics {
                            contentDescription = "Start a new conversation"
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .background(Color(0x24FFFFFF))
                            .border(1.dp, Color(0x33FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = colors.atmosphereTextPrimary,
                            modifier = Modifier.size(avatarSize * 0.40f)
                        )
                    }
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space8))
                    Text(
                        text = "Add",
                        fontFamily = ManropeFontFamily,
                        fontSize = labelSize,
                        fontWeight = FontWeight.Medium,
                        color = colors.atmosphereTextSecondary,
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
                        .testTag("active_now_person_${person.chat.id}")
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
                        showGlowingRim = false
                    )
                    Spacer(modifier = Modifier.height(AetherEmber.Spacing.Space8))
                    Text(
                        text = person.firstName,
                        fontFamily = ManropeFontFamily,
                        fontSize = labelSize,
                        fontWeight = FontWeight.Medium,
                        color = colors.atmosphereTextSecondary,
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
    hasAnyPersonalChats: Boolean,
    query: String
) {
    when {
        isLoading && !hasAnyPersonalChats -> AetherEmptyState(
            title = "Loading your conversations",
            detail = "Aether is syncing with Telegram."
        )
        query.isNotBlank() -> AetherEmptyState(
            title = "No conversations match “$query”",
            detail = "Try a different name or phrase."
        )
        else -> AetherEmptyState(
            title = "No conversations yet",
            detail = "Start a conversation and it will appear here."
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

private fun matchesQuery(chat: Chat, query: String): Boolean {
    if (query.isBlank()) return true
    return chat.title.contains(query, ignoreCase = true) ||
        chat.lastMessageText.contains(query, ignoreCase = true)
}

/**
 * The greeting is real: the local hour, and the user's own first name when Telegram
 * has told us one. Nothing is invented when it has not.
 */
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

/**
 * The weather reading as one unobtrusive line, or nothing at all when Aether does
 * not actually have a reading.
 */
private fun com.foresightlabs.aether.ui.weather.WeatherHeroState.quietLine(): String? {
    if (!isAvailable) return null
    val name = conditionName ?: return temperatureDisplay
    return "$temperatureDisplay · $name"
}
