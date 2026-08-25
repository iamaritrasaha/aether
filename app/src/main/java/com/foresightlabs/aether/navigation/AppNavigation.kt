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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.ui.auth.AuthViewModel
import com.foresightlabs.aether.ui.chats.ChatsViewModel
import com.foresightlabs.aether.ui.conversation.ConversationViewModel
import com.foresightlabs.aether.ui.screens.AppearanceScreen
import com.foresightlabs.aether.ui.screens.AuthScreen
import com.foresightlabs.aether.ui.screens.CallsScreen
import com.foresightlabs.aether.ui.screens.ChatsScreen
import com.foresightlabs.aether.ui.screens.ConversationScreen
import com.foresightlabs.aether.ui.screens.ProfileScreen
import com.foresightlabs.aether.ui.screens.SearchScreen
import com.foresightlabs.aether.ui.screens.SettingsScreen
import com.foresightlabs.aether.ui.search.SearchViewModel
import com.foresightlabs.aether.ui.settings.SettingsViewModel
import com.foresightlabs.aether.ui.theme.LocalAetherColors

object Destinations {
    const val CHATS = "chats"
    const val CONVERSATION = "conversation/{chatId}"
    const val PROFILE = "profile/{chatId}"
    const val SEARCH = "search"
    const val CALLS = "calls"
    const val SETTINGS = "settings"
    const val APPEARANCE = "appearance"

    fun conversation(chatId: String) = "conversation/$chatId"
    fun profile(chatId: String) = "profile/$chatId"
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
                    ChatsScreen(
                        chats = chats,
                        currentUser = currentUser,
                        connection = connection,
                        isLoading = loadingChats,
                        onChatClick = { chat -> selectedChatId = chat.id },
                        onNavigateToSearch = { navController.navigate(Destinations.SEARCH) },
                        onNavigateToCalls = { navController.navigate(Destinations.CALLS) },
                        onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) },
                        onNewMessageClick = { navController.navigate(Destinations.SEARCH) }
                    )
                }
                VerticalDivider(color = colors.border, thickness = 0.5.dp)
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (activeChat != null) {
                        ConversationRoute(
                            application = application,
                            chatId = activeChat.id,
                            onBack = { },
                            onNavigateToProfile = { navController.navigate(Destinations.profile(activeChat.id)) }
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
                    ChatsScreen(
                        chats = chats,
                        currentUser = currentUser,
                        connection = connection,
                        isLoading = loadingChats,
                        onChatClick = { chat -> navController.navigate(Destinations.conversation(chat.id)) },
                        onNavigateToSearch = { navController.navigate(Destinations.SEARCH) },
                        onNavigateToCalls = { navController.navigate(Destinations.CALLS) },
                        onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) },
                        onNewMessageClick = { navController.navigate(Destinations.SEARCH) }
                    )
                }

                composable(
                    route = Destinations.CONVERSATION,
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
                    ConversationRoute(
                        application = application,
                        chatId = chatId,
                        onBack = { navController.popBackStack() },
                        onNavigateToProfile = { navController.navigate(Destinations.profile(chatId)) }
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
            }
        }
    }
}

@Composable
private fun ConversationRoute(
    application: Application,
    chatId: String,
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val id = chatId.toLongOrNull() ?: return
    val viewModel: ConversationViewModel = viewModel(
        key = "conversation-$chatId",
        factory = ConversationViewModel.Factory(application, id)
    )
    val header by viewModel.header.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val canSend by viewModel.composerEnabled.collectAsStateWithLifecycle()
    ConversationScreen(
        chat = header,
        messages = messages,
        canSend = canSend,
        onBack = onBack,
        onNavigateToProfile = onNavigateToProfile,
        onSendMessage = { text, reply -> viewModel.send(text, reply?.id) },
        onComposerChanged = viewModel::onComposerChanged,
        onLoadOlder = viewModel::loadOlder,
        onDeleteMessage = viewModel::delete,
        onRetryMessage = viewModel::retry,
        onVisibleMessages = viewModel::markVisible
    )
}
