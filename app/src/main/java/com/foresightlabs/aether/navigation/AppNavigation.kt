package com.foresightlabs.aether.navigation

import android.app.Application
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.launch
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.data.contacts.DefaultContactsRepository
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.ui.auth.AuthViewModel
import com.foresightlabs.aether.ui.chats.ChatsViewModel
import com.foresightlabs.aether.ui.forum.ForumTopicsViewModel
import com.foresightlabs.aether.ui.screens.ForumTopicsScreen
import com.foresightlabs.aether.ui.contacts.ContactsViewModel
import com.foresightlabs.aether.ui.conversation.ConversationViewModel
import androidx.compose.runtime.rememberCoroutineScope
import com.foresightlabs.aether.ui.calls.CallsViewModel
import com.foresightlabs.aether.ui.screens.AppearanceScreen
import com.foresightlabs.aether.ui.screens.AuthScreen
import com.foresightlabs.aether.ui.screens.CallsScreen
import com.foresightlabs.aether.ui.screens.ChatAppearanceScreen
import com.foresightlabs.aether.ui.screens.ContactsScreen
import com.foresightlabs.aether.ui.screens.FullCallScreen
import com.foresightlabs.aether.ui.screens.HomeScreen
import com.foresightlabs.aether.ui.screens.OngoingCallBar
import com.foresightlabs.aether.ui.screens.ConversationScreen
import com.foresightlabs.aether.ui.screens.ProfileScreen
import com.foresightlabs.aether.ui.screens.PulseScreen
import com.foresightlabs.aether.ui.screens.SearchScreen
import com.foresightlabs.aether.ui.screens.SettingsScreen
import com.foresightlabs.aether.ui.search.SearchViewModel
import com.foresightlabs.aether.ui.settings.SettingsViewModel
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.LocalAppearanceRepository
import com.foresightlabs.aether.ui.theme.LocalAtmosphere
import com.foresightlabs.aether.ui.theme.WeatherReading
import com.foresightlabs.aether.ui.theme.buildAtmosphere
import com.foresightlabs.aether.data.preferences.ChatBubbleStyle

object Destinations {
    const val CHATS = "chats"
    const val PULSE = "pulse"
    const val CONTACTS = "contacts"
    const val CONVERSATION_CHAT = "conversation/chat/{chatId}"
    const val CONVERSATION_USER = "conversation/user/{userId}"
    const val CONVERSATION = "conversation/chat/{chatId}"
    const val PROFILE = "profile/{chatId}"
    const val SEARCH = "search"
    const val CALLS = "calls"
    const val SETTINGS = "settings"
    const val APPEARANCE = "appearance"
    const val CHAT_APPEARANCE = "chat-appearance/{chatId}"
    const val FORUM_TOPICS = "forum/{chatId}"
    const val CONVERSATION_TOPIC = "conversation/topic/{chatId}/{topicId}"

    fun conversation(chatId: String) = "conversation/chat/$chatId"
    fun forumTopics(chatId: String) = "forum/$chatId"
    fun conversationTopic(chatId: Long, topicId: Int) = "conversation/topic/$chatId/$topicId"
    fun conversationWithUser(userId: String) = "conversation/user/$userId"
    fun profile(chatId: String) = "profile/$chatId"
    fun chatAppearance(chatId: Long) = "chat-appearance/$chatId"
}

@Composable
fun AetherApp(
    navController: NavHostController = rememberNavController()
) {
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val busy by authViewModel.busy.collectAsStateWithLifecycle()
    val error by authViewModel.error.collectAsStateWithLifecycle()

    if (authState !is AuthUiState.Ready) {
        AuthScreen(
            state = authState,
            busy = busy,
            error = error,
            onSubmitPhone = authViewModel::submitPhone,
            onSubmitCode = authViewModel::submitCode,
            onSubmitPassword = authViewModel::submitPassword,
            onRegister = authViewModel::register,
            onResendCode = authViewModel::resendCode
        )
        return
    }

    val chatsViewModel: ChatsViewModel = viewModel()
    val chats by chatsViewModel.chats.collectAsStateWithLifecycle()
    val currentUser by chatsViewModel.currentUser.collectAsStateWithLifecycle()
    val connection by chatsViewModel.connection.collectAsStateWithLifecycle()
    val loadingChats by chatsViewModel.isLoading.collectAsStateWithLifecycle()
    val colors = LocalAetherColors.current
    val application = LocalContext.current.applicationContext as Application

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 720.dp

        if (isTablet) {
            var selectedChatId by remember { mutableStateOf(chats.firstOrNull()?.id) }
            val activeChat = chats.firstOrNull { it.id == selectedChatId } ?: chats.firstOrNull()
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                    HomeScreen(
                        chats = chats,
                        currentUser = currentUser,
                        connection = connection,
                        isLoading = loadingChats,
                        onChatClick = { chat ->
                            // A forum opens as its topic list; only a plain chat
                            // opens straight into a conversation.
                            if (chat.isForum) {
                                navController.navigate(Destinations.forumTopics(chat.id))
                            } else {
                                selectedChatId = chat.id
                            }
                        },
                        onNavigateToCalls = { navController.navigate(Destinations.CALLS) },
                        onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) },
                        onNewMessageClick = { navController.navigate(Destinations.SEARCH) },
                        onChatAction = chatsViewModel::perform,
                        onNavigateToPulse = { navController.navigate(Destinations.PULSE) }
                    )
                }
                VerticalDivider(color = colors.border, thickness = 0.5.dp)
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (activeChat != null) {
                        ConversationRoute(
                            application = application,
                            target = com.foresightlabs.aether.domain.model.ConversationTarget.Chat(activeChat.id.toLongOrNull() ?: return@Box),
                            onBack = { },
                            onNavigateToProfile = { navController.navigate(Destinations.profile(activeChat.id)) },
                            onNavigateToChatAppearance = { navController.navigate(Destinations.chatAppearance(it)) }
                        )
                    }
                }
            }
        } else {
            NavHost(
                navController = navController,
                startDestination = Destinations.CHATS,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(
                    route = Destinations.CHATS,
                    enterTransition = { fadeIn(animationSpec = tween(220)) },
                    exitTransition = { fadeOut(animationSpec = tween(180)) }
                ) {
                    HomeScreen(
                        chats = chats,
                        currentUser = currentUser,
                        connection = connection,
                        isLoading = loadingChats,
                        onChatClick = { chat -> navController.navigate(destinationFor(chat)) },
                        onNavigateToCalls = { navController.navigate(Destinations.CALLS) },
                        onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) },
                        onNewMessageClick = { navController.navigate(Destinations.CONTACTS) },
                        onChatAction = chatsViewModel::perform,
                        onNavigateToPulse = { navController.navigate(Destinations.PULSE) }
                    )
                }

                composable(
                    route = Destinations.CONTACTS,
                    enterTransition = { fadeIn(animationSpec = tween(220)) },
                    exitTransition = { fadeOut(animationSpec = tween(180)) }
                ) {
                    val aetherApp = application as? AetherApplication
                    val contactsRepository = aetherApp?.contactsRepository
                        ?: DefaultContactsRepository(
                            context = application.applicationContext,
                            telegram = (application as AetherApplication).telegram
                        )
                    val contactsViewModel: ContactsViewModel = viewModel(
                        factory = ContactsViewModel.Factory(contactsRepository)
                    )
                    val contactsList by contactsViewModel.contacts.collectAsStateWithLifecycle()
                    val isLoadingContacts by contactsViewModel.isLoading.collectAsStateWithLifecycle()
                    val hasDeviceContactsLoaded by contactsViewModel.hasDeviceContactsLoaded.collectAsStateWithLifecycle()

                    ContactsScreen(
                        contacts = contactsList,
                        isLoading = isLoadingContacts,
                        hasDeviceContactsLoaded = hasDeviceContactsLoaded,
                        onContactClick = { user ->
                            navController.navigate(Destinations.conversationWithUser(user.id))
                        },
                        onBack = { navController.popBackStack() },
                        onRequestDeviceSync = contactsViewModel::onUserApprovedDeviceSync
                    )
                }

                composable(
                    route = Destinations.PULSE,
                    enterTransition = { fadeIn(animationSpec = tween(220)) },
                    exitTransition = { fadeOut(animationSpec = tween(180)) }
                ) {
                    val pulseViewModel: com.foresightlabs.aether.ui.pulse.PulseViewModel = viewModel()
                    val myPulse by pulseViewModel.myPulse.collectAsStateWithLifecycle()
                    val pulses by pulseViewModel.pulses.collectAsStateWithLifecycle()
                    val canPostPulse by pulseViewModel.canPostPulse.collectAsStateWithLifecycle()
                    val viewerState by pulseViewModel.viewerState.collectAsStateWithLifecycle()
                    val isPosting by pulseViewModel.isPosting.collectAsStateWithLifecycle()
                    val postError by pulseViewModel.postError.collectAsStateWithLifecycle()

                    com.foresightlabs.aether.ui.screens.PulseScreen(
                        myPulse = myPulse,
                        pulses = pulses,
                        canPostPulse = canPostPulse,
                        currentUser = currentUser,
                        viewerState = viewerState,
                        isPosting = isPosting,
                        postError = postError,
                        onOpenViewer = pulseViewModel::openViewer,
                        onCloseViewer = pulseViewModel::closeViewer,
                        onStoryChanged = pulseViewModel::onStoryChanged,
                        onSendReaction = pulseViewModel::sendReaction,
                        onSendReply = pulseViewModel::sendReply,
                        onPostPulse = pulseViewModel::postPulse,
                        onDeletePulse = pulseViewModel::deletePulse,
                        onNavigateToChats = { navController.navigate(Destinations.CHATS) { popUpTo(Destinations.CHATS) { inclusive = true } } },
                        onNavigateToCalls = { navController.navigate(Destinations.CALLS) },
                        onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) }
                    )
                }

                composable(
                    route = Destinations.CONVERSATION_CHAT,
                    arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
                    enterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    }
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
                    val id = chatId.toLongOrNull() ?: return@composable
                        ConversationRoute(
                        application = application,
                        target = com.foresightlabs.aether.domain.model.ConversationTarget.Chat(id),
                        onBack = { navController.popBackStack() },
                        onNavigateToProfile = { navController.navigate(Destinations.profile(chatId)) },
                        onNavigateToChatAppearance = { navController.navigate(Destinations.chatAppearance(it)) }
                    )
                }

                composable(
                    route = Destinations.FORUM_TOPICS,
                    arguments = listOf(navArgument("chatId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
                    val id = chatId.toLongOrNull() ?: return@composable
                    val topicsViewModel: ForumTopicsViewModel = viewModel(
                        key = "forum-$id",
                        factory = ForumTopicsViewModel.Factory(application, id)
                    )
                    val topics by topicsViewModel.topics.collectAsStateWithLifecycle()
                    val topicsLoading by topicsViewModel.isLoading.collectAsStateWithLifecycle()
                    ForumTopicsScreen(
                        title = topicsViewModel.chat?.title.orEmpty().ifBlank { "Forum" },
                        topics = topics,
                        isLoading = topicsLoading,
                        onTopicClick = { topic ->
                            navController.navigate(
                                Destinations.conversationTopic(id, topic.topicId)
                            )
                        },
                        // Topic administration is reached from the topic itself once
                        // rights are resolved; a long press does nothing yet rather
                        // than opening a menu of actions that might not apply.
                        onTopicLongPress = {},
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Destinations.CONVERSATION_TOPIC,
                    arguments = listOf(
                        navArgument("chatId") { type = NavType.StringType },
                        navArgument("topicId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString("chatId")?.toLongOrNull()
                        ?: return@composable
                    val topicId = backStackEntry.arguments?.getString("topicId")?.toIntOrNull()
                        ?: return@composable
                    ConversationRoute(
                        application = application,
                        target = com.foresightlabs.aether.domain.model.ConversationTarget.Topic(
                            chatId = chatId,
                            topicId = topicId
                        ),
                        onBack = { navController.popBackStack() },
                        onNavigateToProfile = {
                            navController.navigate(Destinations.profile(chatId.toString()))
                        },
                        onNavigateToChatAppearance = {
                            navController.navigate(Destinations.chatAppearance(it))
                        }
                    )
                }

                composable(
                    route = Destinations.CONVERSATION_USER,
                    arguments = listOf(navArgument("userId") { type = NavType.StringType }),
                    enterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    }
                ) { backStackEntry ->
                    val userId = backStackEntry.arguments?.getString("userId").orEmpty()
                    val id = userId.toLongOrNull() ?: return@composable
                        ConversationRoute(
                        application = application,
                        target = com.foresightlabs.aether.domain.model.ConversationTarget.User(id),
                        onBack = { navController.popBackStack() },
                        onNavigateToProfile = { /* profile navigation */ },
                        onNavigateToChatAppearance = { navController.navigate(Destinations.chatAppearance(it)) }
                    )
                }

                composable(
                    route = Destinations.PROFILE,
                    arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
                    enterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    }
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString("chatId")
                    val chat = chats.firstOrNull { it.id == chatId }
                    val profileCalls = (application as AetherApplication).callsRepository
                    val coroutineScope = rememberCoroutineScope()
                    var videoNotice by remember { mutableStateOf<String?>(null) }
                    if (chat != null) {
                        ProfileScreen(
                            chat = chat,
                            onBack = { navController.popBackStack() },
                            onNavigateToConversation = { navController.popBackStack() },
                            onStartVoiceCall = {
                                val targetUserId = chat.directUser?.id?.toLongOrNull() ?: chat.id.toLongOrNull() ?: 0L
                                if (targetUserId != 0L) {
                                    coroutineScope.launch {
                                        profileCalls.initiateCall(targetUserId)
                                            .exceptionOrNull()?.message
                                            ?.let { videoNotice = it }
                                    }
                                }
                            },
                            onStartVideoCall = {
                                videoNotice = "Video calling isn't available in Aether yet."
                            }
                        )
                        if (videoNotice != null) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { videoNotice = null },
                                title = { androidx.compose.material3.Text("Call unavailable", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White) },
                                text = { androidx.compose.material3.Text(videoNotice!!, color = Color(0xDDFFFFFF)) },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(onClick = { videoNotice = null }) {
                                        androidx.compose.material3.Text("OK", color = Color.White)
                                    }
                                },
                                containerColor = com.foresightlabs.aether.ui.theme.AetherEmber.Colors.SurfaceElevated,
                                shape = com.foresightlabs.aether.ui.theme.AetherEmber.Shapes.L
                            )
                        }
                    }
                }

                composable(
                    route = Destinations.SEARCH,
                    enterTransition = { fadeIn(animationSpec = tween(200)) },
                    exitTransition = { fadeOut(animationSpec = tween(150)) }
                ) {
                    val searchViewModel: SearchViewModel = viewModel()
                    val results by searchViewModel.results.collectAsStateWithLifecycle()
                    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
                    SearchScreen(
                        results = results,
                        state = searchState,
                        onQueryChange = searchViewModel::query,
                        onBack = { navController.popBackStack() },
                        onChatClick = { chat -> navController.navigate(destinationFor(chat)) },
                        onMessageClick = { hit ->
                            navController.navigate(Destinations.conversation(hit.message.chatId))
                        },
                        onLoadMoreMessages = searchViewModel::loadMoreMessages
                    )
                }

                composable(
                    route = Destinations.CALLS,
                    enterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    }
                ) {
                    val callsRepository = (application as AetherApplication).callsRepository
                    val callsViewModel: CallsViewModel = viewModel(
                        factory = CallsViewModel.Factory(callsRepository)
                    )
                    val historyState by callsViewModel.historyState.collectAsStateWithLifecycle()

                    CallsScreen(
                        historyState = historyState,
                        onLoadNextPage = callsViewModel::loadNextPageHistory,
                        onRefresh = callsViewModel::refreshHistory,
                        onInitiateCall = callsViewModel::initiateCall,
                        onNavigateToConversation = { chatId ->
                            navController.navigate(Destinations.conversation(chatId.toString()))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Destinations.SETTINGS,
                    enterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    }
                ) {
                    val settingsViewModel: SettingsViewModel = viewModel()
                    val user by settingsViewModel.currentUser.collectAsStateWithLifecycle()
                    val confirm by settingsViewModel.confirmLogout.collectAsStateWithLifecycle()
                    SettingsScreen(
                        currentUser = user,
                        confirmLogout = confirm,
                        onBack = { navController.popBackStack() },
                        onNavigateToAppearance = { navController.navigate(Destinations.APPEARANCE) },
                        onRequestLogout = settingsViewModel::requestLogout,
                        onConfirmLogout = settingsViewModel::confirmLogout,
                        onDismissLogout = settingsViewModel::dismissLogout
                    )
                }

                composable(
                    route = Destinations.APPEARANCE,
                    enterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    }
                ) {
                    AppearanceScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    route = Destinations.CHAT_APPEARANCE,
                    arguments = listOf(navArgument("chatId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
                    ChatAppearanceScreen(chatId = chatId, onBack = { navController.popBackStack() })
                }
            }
        }

        val callsRepository = (application as AetherApplication).callsRepository
        val activeCall by callsRepository.activeCallState.collectAsStateWithLifecycle()
        val navScope = rememberCoroutineScope()

        if (activeCall != null) {
            if (activeCall!!.isMinimized) {
                OngoingCallBar(
                    activeCall = activeCall!!,
                    onExpand = { callsRepository.setMinimized(false) }
                )
            } else {
                FullCallScreen(
                    activeCall = activeCall,
                    onAcceptCall = { callId ->
                        navScope.launch { callsRepository.acceptCall(callId) }
                    },
                    onDiscardCall = { callId ->
                        navScope.launch { callsRepository.discardCall(callId) }
                    },
                    onToggleMute = callsRepository::toggleMute,
                    onToggleSpeaker = callsRepository::toggleSpeaker,
                    onMinimize = { callsRepository.setMinimized(true) }
                )
            }
        }
    }
}

@Composable
private fun ConversationRoute(
    application: Application,
    target: com.foresightlabs.aether.domain.model.ConversationTarget,
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToChatAppearance: (Long) -> Unit
) {
    val key = when (target) {
        is com.foresightlabs.aether.domain.model.ConversationTarget.Chat -> "conversation-chat-${target.chatId}"
        is com.foresightlabs.aether.domain.model.ConversationTarget.User -> "conversation-user-${target.userId}"
        is com.foresightlabs.aether.domain.model.ConversationTarget.Topic -> "conversation-topic-${target.chatId}-${target.topicId}"
    }
    val viewModel: ConversationViewModel = viewModel(
        key = key,
        factory = ConversationViewModel.Factory(application, target)
    )
    val isResolving by viewModel.isResolving.collectAsStateWithLifecycle()
    val resolveError by viewModel.resolveError.collectAsStateWithLifecycle()
    val header by viewModel.header.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val canSend by viewModel.composerEnabled.collectAsStateWithLifecycle()
    val messageCapabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val forwardTargets by viewModel.forwardTargets.collectAsStateWithLifecycle()
    val searchState by viewModel.search.collectAsStateWithLifecycle()
    val jumpTarget by viewModel.jumpTarget.collectAsStateWithLifecycle()
    val pinnedMessages by viewModel.pinnedMessages.collectAsStateWithLifecycle()
    val repository = LocalAppearanceRepository.current
    val chatId = header?.id?.toLongOrNull()
    val resolvedAppearance by (chatId?.let(repository::getResolvedChatAppearanceFlow)
        ?: kotlinx.coroutines.flow.flowOf(null)).collectAsStateWithLifecycle(initialValue = null)
    val resolvedAtmosphere = resolvedAppearance?.let { buildAtmosphere(it.palette, WeatherReading.Idle) }
    val atmosphere = resolvedAtmosphere ?: LocalAtmosphere.current
    val baseColors = LocalAetherColors.current
    val conversationColors = resolvedAppearance?.let { appearance ->
        val outgoing = when (appearance.bubbleStyle) {
            ChatBubbleStyle.ATMOSPHERE, ChatBubbleStyle.EMBER -> atmosphere.accent
            ChatBubbleStyle.GLASS -> Color(0xB82D3648)
            ChatBubbleStyle.MIDNIGHT -> Color(0xFF1A1A20)
        }
        baseColors.copy(
            accent = appearance.fixedAccent ?: atmosphere.accent,
            accentSubtle = (appearance.fixedAccent ?: atmosphere.accent).copy(alpha = .18f),
            bubbleOutgoing = outgoing,
            bubbleOutgoingEnd = when (appearance.bubbleStyle) {
                ChatBubbleStyle.GLASS -> Color(0x8A56647A)
                ChatBubbleStyle.MIDNIGHT -> Color(0xFF25252D)
                else -> atmosphere.accentStrong
            }
        )
    } ?: baseColors
    CompositionLocalProvider(
        LocalAtmosphere provides atmosphere,
        LocalAetherColors provides conversationColors
    ) {
        ConversationScreen(
            chat = header, messages = messages, canSend = canSend, onBack = onBack,
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToChatAppearance = { header?.id?.toLongOrNull()?.let(onNavigateToChatAppearance) },
            onSendMessage = { text, reply, formatting, quote ->
                viewModel.send(text, reply?.id, formatting, quote)
            },
            onSendPhoto = { path, caption, reply -> viewModel.sendPhoto(path, caption, reply?.id) },
            onSendDocument = { path, caption, reply -> viewModel.sendDocument(path, caption, reply?.id) },
            onSendVoiceNote = { path, duration, wave, reply -> viewModel.sendVoiceNote(path, duration, wave, reply?.id) },
            onEditMessage = viewModel::editMessage, onAddReaction = viewModel::addReaction,
            onPinMessage = viewModel::pinMessage, onComposerChanged = viewModel::onComposerChanged,
            onLoadOlder = viewModel::loadOlder, onDeleteMessage = viewModel::delete,
            onForwardMessage = { message, toChatId, sendCopy, removeCaption ->
                viewModel.forwardMessage(message, toChatId, sendCopy, removeCaption)
            },
            forwardTargets = forwardTargets,
            messageCapabilities = messageCapabilities,
            onRequestCapabilities = viewModel::loadCapabilities,
            onRetryMessage = viewModel::retry, onVisibleMessages = viewModel::markVisible,
            isResolving = isResolving, resolveError = resolveError, onRetryResolve = viewModel::retryResolve,
            onStartVoiceCall = viewModel::initiateAudioCall,
            searchState = searchState,
            onOpenSearch = viewModel::openSearch,
            onCloseSearch = viewModel::closeSearch,
            onSearchQueryChange = viewModel::searchMessages,
            onSearchOlder = viewModel::searchOlder,
            onSearchNewer = viewModel::searchNewer,
            onSendContact = { phone, first, last, reply ->
                viewModel.sendContact(phone, first, last, reply?.id)
            },
            onSendLocation = { lat, lon, reply -> viewModel.sendLocation(lat, lon, reply?.id) },
            onPollVote = viewModel::voteOnPoll,
            pinnedFromServer = pinnedMessages,
            onJumpToMessage = viewModel::jumpTo,
            onUnpinMessage = viewModel::unpinMessage,
            // Unpinning is offered only where Telegram says the account may pin.
            canUnpin = messageCapabilities.values.any { it.canBePinned },
            jumpTarget = jumpTarget,
            onJumpConsumed = viewModel::consumeJumpTarget
        )
    }
}


/**
 * Where tapping a conversation row should go.
 *
 * A forum supergroup is a container of topics, not a conversation, so it opens its
 * topic list. Sending it to a conversation screen would post into the forum's root
 * chat and interleave every topic's history.
 */
private fun destinationFor(chat: com.foresightlabs.aether.domain.model.Chat): String =
    if (chat.isForum) Destinations.forumTopics(chat.id) else Destinations.conversation(chat.id)
