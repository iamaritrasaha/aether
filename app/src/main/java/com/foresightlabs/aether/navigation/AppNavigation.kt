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
import com.foresightlabs.aether.ui.contacts.ContactsViewModel
import com.foresightlabs.aether.ui.conversation.ConversationViewModel
import com.foresightlabs.aether.ui.screens.AppearanceScreen
import com.foresightlabs.aether.ui.screens.ChatAppearanceScreen
import com.foresightlabs.aether.ui.screens.AuthScreen
import com.foresightlabs.aether.ui.screens.CallsScreen
import com.foresightlabs.aether.ui.screens.ContactsScreen
import com.foresightlabs.aether.ui.screens.HomeScreen
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

    fun conversation(chatId: String) = "conversation/chat/$chatId"
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
                        onChatClick = { chat -> selectedChatId = chat.id },
                        onNavigateToCalls = { navController.navigate(Destinations.CALLS) },
                        onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) },
                        onNewMessageClick = { navController.navigate(Destinations.SEARCH) },
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
                        onChatClick = { chat -> navController.navigate(Destinations.conversation(chat.id)) },
                        onNavigateToCalls = { navController.navigate(Destinations.CALLS) },
                        onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) },
                        onNewMessageClick = { navController.navigate(Destinations.CONTACTS) },
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
                    if (chat != null) {
                        ProfileScreen(
                            chat = chat,
                            onBack = { navController.popBackStack() },
                            onNavigateToConversation = { navController.popBackStack() }
                        )
                    }
                }

                composable(
                    route = Destinations.SEARCH,
                    enterTransition = { fadeIn(animationSpec = tween(200)) },
                    exitTransition = { fadeOut(animationSpec = tween(150)) }
                ) {
                    val searchViewModel: SearchViewModel = viewModel()
                    val results by searchViewModel.results.collectAsStateWithLifecycle()
                    SearchScreen(
                        results = results,
                        onQueryChange = searchViewModel::query,
                        onBack = { navController.popBackStack() },
                        onChatClick = { chat -> navController.navigate(Destinations.conversation(chat.id)) }
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
                    CallsScreen(onBack = { navController.popBackStack() })
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
            onSendMessage = { text, reply -> viewModel.send(text, reply?.id) },
            onSendPhoto = { path, caption, reply -> viewModel.sendPhoto(path, caption, reply?.id) },
            onSendDocument = { path, caption, reply -> viewModel.sendDocument(path, caption, reply?.id) },
            onEditMessage = viewModel::editMessage, onAddReaction = viewModel::addReaction,
            onPinMessage = viewModel::pinMessage, onComposerChanged = viewModel::onComposerChanged,
            onLoadOlder = viewModel::loadOlder, onDeleteMessage = viewModel::delete,
            onRetryMessage = viewModel::retry, onVisibleMessages = viewModel::markVisible,
            isResolving = isResolving, resolveError = resolveError, onRetryResolve = viewModel::retryResolve
        )
    }
}
