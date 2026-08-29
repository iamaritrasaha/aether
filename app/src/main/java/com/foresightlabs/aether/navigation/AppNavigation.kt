@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.foresightlabs.aether.navigation

import android.app.Application
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.background
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.NavHost
import com.foresightlabs.aether.ui.design.AetherDockDefaults
import com.foresightlabs.aether.ui.design.AetherNavigationMotion
import com.foresightlabs.aether.ui.theme.LocalReducedMotion
import com.foresightlabs.aether.ui.design.LocalDockAnimatedScope
import com.foresightlabs.aether.ui.design.LocalSceneHeightCache
import com.foresightlabs.aether.ui.design.LocalSceneOwnsDock
import com.foresightlabs.aether.ui.design.LocalSceneTransitionProgress
import com.foresightlabs.aether.ui.design.SceneHeightCache
import com.foresightlabs.aether.ui.design.LocalSharedTransitionScope
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.data.contacts.DefaultContactsRepository
import com.foresightlabs.aether.domain.model.AuthUiState
import com.foresightlabs.aether.ui.auth.AuthViewModel
import com.foresightlabs.aether.ui.home.ChatsViewModel
import com.foresightlabs.aether.ui.forum.ForumTopicsViewModel
import com.foresightlabs.aether.ui.forum.ForumTopicsScreen
import com.foresightlabs.aether.ui.contacts.ContactsViewModel
import com.foresightlabs.aether.ui.conversation.ConversationViewModel
import androidx.compose.runtime.rememberCoroutineScope
import com.foresightlabs.aether.ui.calls.CallsViewModel
import com.foresightlabs.aether.ui.appearance.AppearanceScreen
import com.foresightlabs.aether.ui.auth.AuthScreen
import com.foresightlabs.aether.ui.onboarding.OnboardingScreen
import com.foresightlabs.aether.ui.calls.CallsScreen
import com.foresightlabs.aether.ui.appearance.ChatAppearanceScreen
import com.foresightlabs.aether.ui.contacts.ContactsScreen
import com.foresightlabs.aether.ui.calls.FullCallScreen
import com.foresightlabs.aether.ui.home.HomeScreen
import com.foresightlabs.aether.ui.calls.OngoingCallBar
import com.foresightlabs.aether.ui.conversation.ConversationScreen
import com.foresightlabs.aether.ui.profile.ProfileScreen
import com.foresightlabs.aether.ui.pulse.PulseScreen
import com.foresightlabs.aether.ui.search.SearchScreen
import com.foresightlabs.aether.ui.settings.SettingsScreen
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
    val onboardingCompleted by authViewModel.onboardingCompleted.collectAsStateWithLifecycle(
        initialValue = (LocalContext.current.applicationContext as AetherApplication)
            .onboardingRepository.initialCompleted
    )

    androidx.compose.runtime.LaunchedEffect(authState) {
        if (authState is AuthUiState.Ready) authViewModel.markOnboardingCompleted()
    }

    if (!onboardingCompleted && authState !is AuthUiState.Ready) {
        OnboardingScreen(onComplete = authViewModel::markOnboardingCompleted)
        return
    }

    if (authState !is AuthUiState.Ready) {
        AuthScreen(
            state = authState,
            busy = busy,
            error = error,
            onSubmitPhone = authViewModel::submitPhone,
            onSubmitCode = authViewModel::submitCode,
            onSubmitPassword = authViewModel::submitPassword,
            onRegister = authViewModel::register,
            onResendCode = authViewModel::resendCode,
            onRequestQrCode = authViewModel::requestQrCodeAuthentication,
            onSubmitEmailAddress = authViewModel::submitEmailAddress,
            onSubmitEmailCode = authViewModel::submitEmailCode,
            onResetEmailAddress = authViewModel::resetEmailAddress,
            onRequestPasswordRecovery = authViewModel::requestPasswordRecovery,
            onUsePasskey = authViewModel::usePasskey,
            passwordRecoveryRequested = authViewModel.passwordRecoveryRequested.collectAsStateWithLifecycle().value
        )
        return
    }

    val chatsViewModel: ChatsViewModel = viewModel()
    val chats by chatsViewModel.chats.collectAsStateWithLifecycle()
    val currentUser by chatsViewModel.currentUser.collectAsStateWithLifecycle()
    val connection by chatsViewModel.connection.collectAsStateWithLifecycle()
    val loadingChats by chatsViewModel.isLoading.collectAsStateWithLifecycle()
    val folders by chatsViewModel.folders.collectAsStateWithLifecycle()
    val selectedFolder by chatsViewModel.selectedFolder.collectAsStateWithLifecycle()
    val folderChats by chatsViewModel.folderChats.collectAsStateWithLifecycle()
    val colors = LocalAetherColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val application = context.applicationContext as Application

    val pendingChatId by com.foresightlabs.aether.data.notifications.ActiveConversationTracker.pendingNavigationChatId.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(pendingChatId) {
        val targetChatId = pendingChatId
        if (targetChatId != null) {
            com.foresightlabs.aether.data.notifications.ActiveConversationTracker.consumePendingNavigationChatId()
            navController.navigate(Destinations.conversation(targetChatId.toString()))
        }
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val permissionCoordinator = (application as? AetherApplication)?.permissionCoordinator
        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) {
            permissionCoordinator?.refresh()
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 720.dp

        if (isTablet) {
            var selectedChatId by remember { mutableStateOf(folderChats.firstOrNull()?.id) }
            val activeChat = folderChats.firstOrNull { it.id == selectedChatId } ?: folderChats.firstOrNull()
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                    HomeScreen(
                        chats = folderChats,
                        currentUser = currentUser,
                        connection = connection,
                        isLoading = loadingChats,
                        folders = folders,
                        selectedFolder = selectedFolder,
                        onSelectFolder = chatsViewModel::selectFolder,
                        onCreateFolder = chatsViewModel::createChatFolder,
                        onEditFolder = chatsViewModel::editChatFolder,
                        onDeleteFolder = chatsViewModel::deleteChatFolder,
                        onReorderFolders = chatsViewModel::reorderChatFolders,
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
            // The persistent rear layer. It lives above the navigation graph and
            // outlives every route change, so the black surface Home shows the
            // conversations on and the one a conversation shows its composer on
            // are not two surfaces that hand over — they are this one, uncovered
            // to different heights.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background)
            ) {
            CompositionLocalProvider(LocalSceneOwnsDock provides true) {
            SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            val sharedScope = this
            // Route transitions are built once here so reduced motion can flatten
            // them without every destination re-deriving the same decision.
            val calm = LocalReducedMotion.current
            val morph = if (calm) 0 else AetherDockDefaults.MorphMillis
            // The one pair of rest heights the whole morph interpolates between.
            // Lives above the graph so it survives Home/Conversation unmounting.
            val heightCache = remember { SceneHeightCache() }
            // AnimatedContent holds both the leaving and entering destination at
            // alpha 1 for the whole transition instead of cross-fading them
            // itself — Home and Conversation drive their own visual change from
            // the shared progress below, and a second, independent fade on top of
            // that would fight it and reintroduce the dip this was built to fix.
            val hold = tween<Float>(morph, easing = LinearEasing)
            NavHost(
                navController = navController,
                startDestination = Destinations.CHATS,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(
                    route = Destinations.CHATS,
                    enterTransition = { fadeIn(animationSpec = hold, initialAlpha = 1f) },
                    exitTransition = { fadeOut(animationSpec = hold, targetAlpha = 1f) },
                    popEnterTransition = { fadeIn(animationSpec = hold, initialAlpha = 1f) },
                    popExitTransition = { fadeOut(animationSpec = hold, targetAlpha = 1f) }
                ) {
                    // 0 at rest on Home, moving toward 1 as a conversation covers
                    // it — read from the same underlying transition Navigation
                    // Compose drives for predictive back, so a live gesture moves
                    // this in step with the finger rather than waiting for it to
                    // finish.
                    val progress by transition.animateFloat(
                        label = "home_progress",
                        transitionSpec = { if (calm) snap() else tween(morph, easing = FastOutSlowInEasing) }
                    ) { state -> if (state == EnterExitState.Visible) 0f else 1f }
                    CompositionLocalProvider(
                        LocalSharedTransitionScope provides sharedScope,
                        LocalDockAnimatedScope provides this@composable,
                        LocalSceneTransitionProgress provides progress,
                        LocalSceneHeightCache provides heightCache
                    ) {
                    HomeScreen(
                        chats = folderChats,
                        currentUser = currentUser,
                        connection = connection,
                        isLoading = loadingChats,
                        folders = folders,
                        selectedFolder = selectedFolder,
                        onSelectFolder = chatsViewModel::selectFolder,
                        onCreateFolder = chatsViewModel::createChatFolder,
                        onEditFolder = chatsViewModel::editChatFolder,
                        onDeleteFolder = chatsViewModel::deleteChatFolder,
                        onReorderFolders = chatsViewModel::reorderChatFolders,
                        onChatClick = { chat -> navController.navigate(destinationFor(chat)) },
                        onNavigateToCalls = { navController.navigate(Destinations.CALLS) },
                        onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) },
                        onNewMessageClick = { navController.navigate(Destinations.CONTACTS) },
                        onChatAction = chatsViewModel::perform,
                        onNavigateToPulse = { navController.navigate(Destinations.PULSE) }
                    )
                    }
                }

                composable(
                    route = Destinations.CONTACTS,
                    enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                    exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                    popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                    popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
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
                    enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                    exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                    popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                    popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
                ) {
                    val pulseViewModel: com.foresightlabs.aether.ui.pulse.PulseViewModel = viewModel()
                    val myPulse by pulseViewModel.myPulse.collectAsStateWithLifecycle()
                    val pulses by pulseViewModel.pulses.collectAsStateWithLifecycle()
                    val canPostPulse by pulseViewModel.canPostPulse.collectAsStateWithLifecycle()
                    val viewerState by pulseViewModel.viewerState.collectAsStateWithLifecycle()
                    val isPosting by pulseViewModel.isPosting.collectAsStateWithLifecycle()
                    val postError by pulseViewModel.postError.collectAsStateWithLifecycle()

                    com.foresightlabs.aether.ui.pulse.PulseScreen(
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
                    // No page slide: the dock collapsing from the conversation list
                    // into the composer is the transition, driven below by the same
                    // progress Home reads — not an independent animation of this
                    // route's own opacity or position.
                    enterTransition = { fadeIn(animationSpec = hold, initialAlpha = 1f) },
                    exitTransition = { fadeOut(animationSpec = hold, targetAlpha = 1f) },
                    popEnterTransition = { fadeIn(animationSpec = hold, initialAlpha = 1f) },
                    popExitTransition = { fadeOut(animationSpec = hold, targetAlpha = 1f) }
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
                    val id = chatId.toLongOrNull() ?: return@composable
                    // 1 at rest in the conversation, moving toward 0 as it is
                    // uncovered by Home returning underneath it. Defined on the
                    // *same underlying transition* as Home's — both destinations
                    // share one AnimatedContent swap, so at any instant the two
                    // progress values agree exactly; there is no clock to drift.
                    val progress by transition.animateFloat(
                        label = "conversation_progress",
                        transitionSpec = { if (calm) snap() else tween(morph, easing = FastOutSlowInEasing) }
                    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
                    CompositionLocalProvider(
                        LocalSharedTransitionScope provides sharedScope,
                        LocalDockAnimatedScope provides this@composable,
                        LocalSceneTransitionProgress provides progress,
                        LocalSceneHeightCache provides heightCache
                    ) {
                        ConversationRoute(
                            application = application,
                            target = com.foresightlabs.aether.domain.model.ConversationTarget.Chat(id),
                            onBack = { navController.popBackStack() },
                            onNavigateToProfile = { navController.navigate(Destinations.profile(chatId)) },
                            onNavigateToChatAppearance = { navController.navigate(Destinations.chatAppearance(it)) }
                        )
                    }
                }

                composable(
                    route = Destinations.FORUM_TOPICS,
                    arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
                    enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                    exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                    popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                    popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
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
                    ),
                    enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                    exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                    popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                    popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
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
                    enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                    exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                    popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                    popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
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
                    enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                    exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                    popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                    popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
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
                            onChatAction = chatsViewModel::perform,
                            onLoadSharedMedia = { targetChatId, category, offset ->
                                (application as AetherApplication).telegram.getSharedMedia(targetChatId, category, offset)
                            },
                            onRequestMediaDownload = { fileId, isRetry ->
                                val tg = (application as AetherApplication).telegram
                                if (isRetry) tg.retryMediaDownload(fileId) else tg.requestFullMediaDownload(fileId)
                            },
                            canCallAudio = profileCalls.isCallMediaAvailable,
                            canCallVideo = false,
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
                    enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                    exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                    popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                    popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
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
                    enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                    exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                    popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                    popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
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
                    enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                    exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                    popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                    popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
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
                    enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                    exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                    popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                    popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
                ) {
                    AppearanceScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    route = Destinations.CHAT_APPEARANCE,
                    arguments = listOf(navArgument("chatId") { type = NavType.LongType }),
                    enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                    exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                    popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                    popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
                    ChatAppearanceScreen(chatId = chatId, onBack = { navController.popBackStack() })
            }
        }

        val callsRepository = if (com.foresightlabs.aether.AetherFeatureFlags.CALLS_ENABLED) {
            (application as AetherApplication).callsRepository
        } else {
            null
        }
        val activeCall by (callsRepository?.activeCallState
            ?: kotlinx.coroutines.flow.flowOf<com.foresightlabs.aether.domain.model.ActiveCall?>(null))
            .collectAsStateWithLifecycle(initialValue = null)
        val navScope = rememberCoroutineScope()

        if (com.foresightlabs.aether.AetherFeatureFlags.CALLS_ENABLED && activeCall != null) {
            if (activeCall!!.isMinimized) {
                OngoingCallBar(
                    activeCall = activeCall!!,
                    onExpand = { callsRepository?.setMinimized(false) }
                )
            } else {
                FullCallScreen(
                    activeCall = activeCall,
                    onAcceptCall = { callId ->
                        navScope.launch { callsRepository?.acceptCall(callId) }
                    },
                    onDiscardCall = { callId ->
                        navScope.launch { callsRepository?.discardCall(callId) }
                    },
                    onToggleMute = { callsRepository?.toggleMute() },
                    onToggleSpeaker = { callsRepository?.toggleSpeaker() },
                    onMinimize = { callsRepository?.setMinimized(true) }
                )
            }
        }
            }
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
    val forwardState by viewModel.forwardState.collectAsStateWithLifecycle()
    val searchState by viewModel.search.collectAsStateWithLifecycle()
    val jumpTarget by viewModel.jumpTarget.collectAsStateWithLifecycle()
    val messageMotionEvents by viewModel.messageMotionEvents.collectAsStateWithLifecycle()
    val pinnedMessages by viewModel.pinnedMessages.collectAsStateWithLifecycle()
    val installedStickerSets by viewModel.installedStickerSets.collectAsStateWithLifecycle()
    val recentStickers by viewModel.recentStickers.collectAsStateWithLifecycle()
    val favoriteStickers by viewModel.favoriteStickers.collectAsStateWithLifecycle()
    val savedAnimations by viewModel.savedAnimations.collectAsStateWithLifecycle()
    val repository = LocalAppearanceRepository.current
    val chatId = header?.id?.toLongOrNull()

    val app = application as? AetherApplication
    val targetChatId = when (target) {
        is com.foresightlabs.aether.domain.model.ConversationTarget.Chat -> target.chatId
        is com.foresightlabs.aether.domain.model.ConversationTarget.Topic -> target.chatId
        is com.foresightlabs.aether.domain.model.ConversationTarget.User -> chatId
    }
    val targetTopicId = (target as? com.foresightlabs.aether.domain.model.ConversationTarget.Topic)?.topicId

    androidx.compose.runtime.DisposableEffect(targetChatId, targetTopicId) {
        if (targetChatId != null) {
            app?.notificationManager?.onConversationOpened(targetChatId, targetTopicId)
        }
        onDispose {
            app?.notificationManager?.onConversationClosed()
        }
    }

    val resolvedAppearance by (chatId?.let(repository::getResolvedChatAppearanceFlow)
        ?: kotlinx.coroutines.flow.flowOf(null)).collectAsStateWithLifecycle(initialValue = null)
    val resolvedAtmosphere = resolvedAppearance?.let { buildAtmosphere(it.palette, WeatherReading.Idle) }
    val atmosphere = resolvedAtmosphere ?: LocalAtmosphere.current
    val baseColors = LocalAetherColors.current
    val conversationColors = resolvedAppearance?.let { appearance ->
        // Outgoing messages are dark graphite in every style — that tonal
        // inversion against the pale incoming bubble is the conversation's
        // structure, not a per-chat preference. A style only decides how much of
        // the atmosphere's hue is allowed to smoke through the graphite, so
        // "Atmosphere" still adapts without ever becoming an accent-coloured slab.
        val outgoing = when (appearance.bubbleStyle) {
            ChatBubbleStyle.ATMOSPHERE -> smokeGraphite(atmosphere.accent, 0.14f)
            ChatBubbleStyle.EMBER -> smokeGraphite(atmosphere.accentStrong, 0.18f)
            ChatBubbleStyle.GLASS -> smokeGraphite(atmosphere.accent, 0.10f).copy(alpha = 0.82f)
            ChatBubbleStyle.MIDNIGHT -> Color(0xFF16161B)
        }
        baseColors.copy(
            accent = appearance.fixedAccent ?: atmosphere.accent,
            accentSubtle = (appearance.fixedAccent ?: atmosphere.accent).copy(alpha = .18f),
            bubbleOutgoing = outgoing,
            bubbleOutgoingEnd = outgoing
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
            onForwardMessages = { selectedMessages, toChatId, sendCopy, removeCaption ->
                viewModel.forwardMessages(selectedMessages, toChatId, sendCopy, removeCaption)
            },
            forwardTargets = forwardTargets,
            forwardState = forwardState,
            onForwardStateConsumed = viewModel::consumeForwardState,
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
            onSendLiveLocation = { lat, lon, dur, reply ->
                viewModel.sendLiveLocation(lat, lon, dur, heading = 0, replyToId = reply?.id)
            },
            onStopLiveLocation = viewModel::stopLiveLocation,
            onSendVenue = { lat, lon, title, address, reply ->
                viewModel.sendVenue(lat, lon, title, address, reply?.id)
            },
            onSendVideoNote = { path, dur, len, reply ->
                viewModel.sendVideoNote(path, dur, len, reply?.id)
            },
            onSendSticker = { fileId, emoji ->
                viewModel.sendStickerFile(fileId, emoji)
            },
            onReplaceMedia = { message, mediaPath, type ->
                viewModel.replaceMedia(message, mediaPath, type)
            },
            installedStickerSets = installedStickerSets,
            recentStickers = recentStickers,
            favoriteStickers = favoriteStickers,
            onLoadStickers = viewModel::loadStickers,
            onLoadStickerSetDetails = viewModel::loadStickerSetDetails,
            savedAnimations = savedAnimations,
            onLoadSavedAnimations = viewModel::loadSavedAnimations,
            onSendAnimation = { fileId -> viewModel.sendAnimationFile(fileId) },
            onLoadScheduled = viewModel::getScheduledMessages,
            onSendScheduledNow = { msg -> viewModel.sendScheduledMessageNow(msg) },
            onRescheduleMessage = { msg, secs -> viewModel.rescheduleMessage(msg, secs) },
            onPollVote = viewModel::voteOnPoll,
            onCopyMessageLink = { message ->
                val clipboard = application.getSystemService(android.content.ClipboardManager::class.java)
                viewModel.copyMessageLink(message) { link ->
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Message Link", link))
                }
            },
            pinnedFromServer = pinnedMessages,
            onJumpToMessage = viewModel::jumpTo,
            onReplyPreviewClick = { replyChatId, replyMessageId ->
                if (replyChatId == targetChatId) viewModel.jumpTo(replyMessageId.toString())
            },
            onUnpinMessage = viewModel::unpinMessage,
            // Unpinning is offered only where Telegram says the account may pin.
            canUnpin = messageCapabilities.values.any { it.canBePinned },
            jumpTarget = jumpTarget,
            onJumpConsumed = viewModel::consumeJumpTarget,
            messageMotionEvents = messageMotionEvents,
            onRequestMediaDownload = { fileId, isRetry ->
                if (isRetry) viewModel.retryMediaDownload(fileId) else viewModel.requestFullMediaDownload(fileId)
            }
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

/**
 * A neutral smoky charcoal carrying a trace of the surrounding sky.
 *
 * The hue is mixed into a near-black base at a low weight, so the result stays
 * firmly in the graphite family however saturated the atmosphere becomes.
 */
private fun smokeGraphite(hue: Color, weight: Float): Color {
    val base = 0.09f
    fun mix(channel: Float) = (base * (1f - weight) + channel * weight).coerceIn(0f, 1f)
    return Color(
        red = mix(hue.red),
        green = mix(hue.green),
        blue = mix(hue.blue),
        alpha = 1f
    )
}
