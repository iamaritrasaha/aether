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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
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
import com.foresightlabs.aether.ui.about.AboutScreen
import com.foresightlabs.aether.ui.calls.AetherCallScreen
import com.foresightlabs.aether.ui.home.HomeScreen
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
import com.foresightlabs.aether.ui.theme.buildAtmosphere
import com.foresightlabs.aether.data.preferences.ChatBubbleStyle

object Destinations {
    const val CHATS = "chats"
    const val PULSE = "pulse"
    const val CONTACTS = "contacts"
    const val CONVERSATION_CHAT = "conversation/chat/{chatId}?jump={messageId}"
    const val BOOKMARKS = "bookmarks"
    const val CONVERSATION_USER = "conversation/user/{userId}"
    const val CONVERSATION = "conversation/chat/{chatId}"
    const val PROFILE = "profile/{chatId}"
    const val SEARCH = "search"
    const val CALLS = "calls"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val APPEARANCE = "appearance"
    const val APP_LOCK_SETTINGS = "app-lock-settings"
    const val APP_LOCK_SETUP = "app-lock-setup"
    const val APP_LOCK_REAUTH = "app-lock-reauth/{purpose}"
    const val CHAT_APPEARANCE = "chat-appearance/{chatId}"
    const val FORUM_TOPICS = "forum/{chatId}"
    const val CONVERSATION_TOPIC = "conversation/topic/{chatId}/{topicId}"

    fun appLockReauth(purpose: String) = "app-lock-reauth/$purpose"
    fun conversation(chatId: String) = "conversation/chat/$chatId"
    /** Opens a conversation and jumps straight to [messageId] (bookmark entry). */
    fun conversationAtMessage(chatId: String, messageId: Long) =
        "conversation/chat/$chatId?jump=$messageId"

    /**
     * A conversation entry's pending jump, held in its SavedStateHandle so it
     * is consumed exactly once: it survives recreation until used, and never
     * re-fires when the entry is returned to (from Profile, say).
     */
    const val PENDING_JUMP_KEY = "pending_jump_message_id"
    const val JUMP_ARGUMENT_CONSUMED_KEY = "jump_argument_consumed"
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

    // Telegram's own service notifications (TdApi.UpdateServiceNotification) are
    // delivered out of band, not as chat messages, and TDLib documents that the
    // client must show their content. Shown here, above every route, because they
    // are account-level and belong to no conversation.
    TelegramServiceNoticePrompt()

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
            onStartOver = authViewModel::startOver,
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

    // A share from another application, waiting for a recipient. It takes the
    // whole screen the way onboarding and authentication do, rather than opening
    // a second surface over the app; choosing someone hands it to that
    // conversation, where it is sent from the Composer like anything else.
    val pendingShare by com.foresightlabs.aether.data.sharing.SharedContentInbox.pending.collectAsStateWithLifecycle()
    val shareDelivery by com.foresightlabs.aether.data.sharing.SharedContentInbox.delivery.collectAsStateWithLifecycle()
    pendingShare?.let { share ->
        com.foresightlabs.aether.ui.sharing.ShareTargetScreen(
            content = share,
            // The same rule the rest of Aether applies to who can be written to:
            // personal conversations the account may send text in. Groups,
            // channels, bots, forums and Telegram's own service account are not
            // offered, because a share to them is not deliverable here.
            targets = com.foresightlabs.aether.domain.sharing.ShareRecipients.eligible(chats),
            onDismiss = { com.foresightlabs.aether.data.sharing.SharedContentInbox.clear() },
            onChooseRecipient = { chat ->
                chat.id.toLongOrNull()?.let { chatId ->
                    com.foresightlabs.aether.data.sharing.SharedContentInbox.addressTo(chatId)
                }
            }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 720.dp

        // Once a share has a recipient, that conversation opens through the
        // same navigation every other conversation is opened by. Form-factor
        // gated: on a tablet-width window an addressed share SELECTS the pane
        // (see the two-pane branch below); this effect used to run there too,
        // pushing a full-screen conversation OVER the two-pane layout -- a
        // double instance of the same conversation whose disposal also tore
        // down active-conversation tracking for the pane still showing it.
        androidx.compose.runtime.LaunchedEffect(shareDelivery?.chatId, isTablet) {
            val addressed = shareDelivery?.chatId ?: return@LaunchedEffect
            if (!isTablet) {
                navController.navigate(Destinations.conversation(addressed.toString()))
            }
        }

        // The persistent rear layer. It lives above the navigation graph and
        // outlives every route change, so the black surface Home shows the
        // conversations on and the one a conversation shows its composer on
        // are not two surfaces that hand over — they are this one, uncovered
        // to different heights.
        //
        // Mounted unconditionally -- for both form factors -- because it owns
        // the app's ONE NavHost/NavController pairing. Every peripheral screen
        // (Settings, Calls, a profile, ...), phone or tablet, navigates on the
        // same navController, and that controller has no graph at all until a
        // NavHost using it is actually composed. Gating this behind isTablet
        // used to mean the graph simply never existed on a tablet-width
        // window, so the very first navigate() call from that layout -- e.g.
        // tapping Settings -- crashed with "Navigation graph has not been set
        // for NavController."
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

            // Tablet's two-pane layout is a persistent sibling of the nav graph
            // below, not a destination inside it: it is never pushed, popped or
            // disposed by navigation, so the selected conversation and its
            // ChatScopedViewModelStoreOwner survive a trip to Settings/Calls/a
            // profile and back exactly as they did before. It only reaches into
            // the graph to open those peripheral screens, on the same
            // navController the NavHost below binds.
            if (isTablet) {
                                // Saveable: the selected pane must survive rotation --
                // isTablet flips with width, and any recreation used to drop
                // the user back to the folder's first chat.
                var selectedChatId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(folderChats.firstOrNull()?.id) }
                // Two panes rather than a back stack, so an addressed share
                // selects the conversation here instead of navigating to it.
                androidx.compose.runtime.LaunchedEffect(shareDelivery?.chatId) {
                    shareDelivery?.chatId?.let { selectedChatId = it.toString() }
                }
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
                            // This Box sits outside NavHost, so there is no NavBackStackEntry
                            // to scope ConversationRoute's viewModel() call to -- without this,
                            // it resolves against the Activity's own store, and every chat ever
                            // opened on tablet stays alive (and its collectors keep running) for
                            // the rest of the process. Scoping a store to the selected chat id
                            // and clearing it on change gives tablet the same per-chat lifecycle
                            // phone gets for free from its back-stack entry.
                            ChatScopedViewModelStoreOwner(key = activeChat.id) {
                                ConversationRoute(
                                    application = application,
                                    target = com.foresightlabs.aether.domain.model.ConversationTarget.Chat(activeChat.id.toLongOrNull() ?: return@ChatScopedViewModelStoreOwner),
                                    onBack = { },
                                    onNavigateToProfile = { navController.navigate(Destinations.profile(activeChat.id)) },
                                    onNavigateToChatAppearance = { navController.navigate(Destinations.chatAppearance(it)) }
                                )
                            }
                        }
                    }
                }
            }

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
                    // Tablet already shows Home in the persistent left pane
                    // above -- this destination exists only so the shared
                    // navController has a start destination to rest on and
                    // return to. Rendering nothing here lets that pane show
                    // through whenever the back stack is at rest on CHATS.
                    if (!isTablet) {
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
                    arguments = listOf(
                        navArgument("chatId") { type = NavType.StringType },
                        // Optional jump target (bookmarks): 0 = none.
                        navArgument("messageId") { type = NavType.LongType; defaultValue = 0L }
                    ),
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
                    val initialJump = backStackEntry.arguments?.getLong("messageId") ?: 0L
                    val entryState = backStackEntry.savedStateHandle
                    // The ?jump= argument is a one-time request; move it into the
                    // entry's saved state on first composition so neither
                    // recreation nor coming back from Profile repeats it.
                    remember(backStackEntry.id) {
                        if (initialJump != 0L && entryState.get<Boolean>(Destinations.JUMP_ARGUMENT_CONSUMED_KEY) != true) {
                            entryState[Destinations.JUMP_ARGUMENT_CONSUMED_KEY] = true
                            entryState[Destinations.PENDING_JUMP_KEY] = initialJump
                        }
                        Unit
                    }
                    val pendingJump by entryState
                        .getStateFlow(Destinations.PENDING_JUMP_KEY, 0L)
                        .collectAsStateWithLifecycle()
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
                            onNavigateToChatAppearance = { navController.navigate(Destinations.chatAppearance(it)) },
                            pendingJumpMessageId = pendingJump,
                            onPendingJumpConsumed = { entryState[Destinations.PENDING_JUMP_KEY] = 0L }
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
                        // A TDLib private chat's id IS its user id, so the
                        // profile route the chat-based conversations use
                        // resolves here too. This was an empty callback: the
                        // header is visibly tappable and tapping did nothing.
                        onNavigateToProfile = { navController.navigate(Destinations.profile(id.toString())) },
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
                    // Profiles are reachable for chats outside the loaded list
                    // (search results, a chat the list has not paginated to
                    // yet, or a deep entry right after process death). Without
                    // this fetch the route rendered a blank screen with no
                    // back button for exactly those cases. ensureChatLoaded
                    // publishes into the chat list, so recomposition renders
                    // the profile once TDLib answers; a genuinely unknown id
                    // simply stays on the (harmless) empty route.
                    val telegram = (application as AetherApplication).telegram
                    LaunchedEffect(chatId) {
                        if (chat == null && chatId?.toLongOrNull() != null) {
                            runCatching { telegram.ensureChatLoaded(chatId.toLong()) }
                        }
                    }
                    // Null with calling held: the profile then offers no call
                    // action and never builds the call stack.
                    val profileCalls = if (com.foresightlabs.aether.AetherFeatureFlags.CALLS_ENABLED) {
                        (application as AetherApplication).callsRepository
                    } else {
                        null
                    }
                    val coroutineScope = rememberCoroutineScope()
                    var videoNotice by remember { mutableStateOf<String?>(null) }
                    // A call is never placed before the OS grants what it needs;
                    // see CallPermissionGate for why that is a crash-level rule.
                    val startProfileCall = com.foresightlabs.aether.ui.calls.rememberCallStarter { isVideo ->
                        val targetUserId = chat?.directUser?.id?.toLongOrNull() ?: chat?.id?.toLongOrNull() ?: 0L
                        if (targetUserId != 0L) {
                            coroutineScope.launch {
                                profileCalls?.initiateCall(targetUserId, isVideo = isVideo)
                                    ?.exceptionOrNull()?.message
                                    ?.let { videoNotice = it }
                            }
                        }
                    }
                    if (chat != null) {
                        ProfileScreen(
                            chat = chat,
                            onBack = { navController.popBackStack() },
                            onNavigateToConversation = { navController.popBackStack() },
                            onChatAction = chatsViewModel::perform,
                            onLoadSharedMedia = { targetChatId, category, offset ->
                                (application as AetherApplication).telegram.getSharedMedia(targetChatId, category, offset)
                            },
                            onOpenMessageInConversation = { targetChatId, messageId ->
                                // Profile is normally opened FROM this conversation:
                                // go back to it and jump there, rather than stacking
                                // a second copy of the same chat above Profile.
                                val previous = navController.previousBackStackEntry
                                val returnsToSameChat =
                                    previous?.destination?.route == Destinations.CONVERSATION_CHAT &&
                                        previous.arguments?.getString("chatId") == targetChatId.toString()
                                if (returnsToSameChat) {
                                    previous!!.savedStateHandle[Destinations.PENDING_JUMP_KEY] = messageId
                                    navController.popBackStack()
                                } else {
                                    navController.navigate(Destinations.conversationAtMessage(targetChatId.toString(), messageId))
                                }
                            },
                            onRequestMediaDownload = { fileId, isRetry ->
                                val tg = (application as AetherApplication).telegram
                                if (isRetry) tg.retryMediaDownload(fileId) else tg.requestFullMediaDownload(fileId)
                            },
                            canCallAudio = profileCalls?.isCallMediaAvailable == true,
                            canCallVideo = profileCalls?.isCallMediaAvailable == true,
                            onStartVoiceCall = { startProfileCall(false) },
                            onStartVideoCall = { startProfileCall(true) }
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

                // Calling held: the Calls destination is not registered at all,
                // so no link, stale back stack or deep entry can reach it.
                if (com.foresightlabs.aether.AetherFeatureFlags.CALLS_ENABLED) composable(
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
                        onNavigateToAbout = { navController.navigate(Destinations.ABOUT) },
                        onNavigateToAppLock = { navController.navigate(Destinations.APP_LOCK_SETTINGS) },
                        onNavigateToBookmarks = { navController.navigate(Destinations.BOOKMARKS) },
                        onRequestLogout = settingsViewModel::requestLogout,
                        onConfirmLogout = settingsViewModel::confirmLogout,
                        onDismissLogout = settingsViewModel::dismissLogout
                    )
                }

                composable(
                    route = Destinations.BOOKMARKS,
                    enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                    exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                    popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                    popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
                ) {
                    com.foresightlabs.aether.ui.bookmarks.BookmarksScreen(
                        onBack = { navController.popBackStack() },
                        onOpenBookmark = { chatId, messageId ->
                            navController.navigate(Destinations.conversationAtMessage(chatId.toString(), messageId))
                        }
                    )
                }

                // App Lock is held for this milestone (AetherFeatureFlags.
                // APP_LOCK_ENABLED): these three destinations are not
                // registered in the graph at all while it is off, so they are
                // unreachable by route name (deep link included), not merely
                // hidden from the Settings entry point.
                if (com.foresightlabs.aether.AetherFeatureFlags.APP_LOCK_ENABLED) {
                    composable(
                        route = Destinations.APP_LOCK_SETTINGS,
                        enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                        exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                        popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                        popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
                    ) {
                        com.foresightlabs.aether.ui.security.AppLockSettingsScreen(
                            onBack = { navController.popBackStack() },
                            onRequestSetup = { navController.navigate(Destinations.APP_LOCK_SETUP) },
                            onRequestChangePasscode = { navController.navigate(Destinations.appLockReauth("change")) },
                            onRequestDisable = { navController.navigate(Destinations.appLockReauth("disable")) }
                        )
                    }

                    composable(
                        route = Destinations.APP_LOCK_SETUP,
                        enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                        exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                        popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                        popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
                    ) {
                        com.foresightlabs.aether.ui.security.AppLockSetupScreen(
                            onCancel = { navController.popBackStack() },
                            onCompleted = {
                                navController.navigate(Destinations.APP_LOCK_SETTINGS) {
                                    popUpTo(Destinations.APP_LOCK_SETTINGS) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(
                        route = Destinations.APP_LOCK_REAUTH,
                        arguments = listOf(navArgument("purpose") { type = NavType.StringType }),
                        enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                        exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                        popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                        popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
                    ) { backStackEntry ->
                        val purpose = backStackEntry.arguments?.getString("purpose").orEmpty()
                        val reauthScope = rememberCoroutineScope()
                        com.foresightlabs.aether.ui.security.AppLockReauthScreen(
                            reason = if (purpose == "disable") "Confirm passcode to turn off App Lock" else "Confirm your current passcode",
                            onCancel = { navController.popBackStack() },
                            onVerified = {
                                if (purpose == "disable") {
                                    // popBackStack() must wait for disable() to actually
                                    // finish writing: it disposes this composable (and
                                    // reauthScope with it), so calling it before the
                                    // suspend completes could cancel the DataStore write
                                    // mid-flight -- the passcode verifies, the screen
                                    // pops back, but App Lock silently stays on.
                                    reauthScope.launch {
                                        (application as AetherApplication).appLockRepository.disable()
                                        navController.popBackStack()
                                    }
                                } else {
                                    navController.navigate(Destinations.APP_LOCK_SETUP) {
                                        popUpTo(Destinations.APP_LOCK_SETTINGS)
                                    }
                                }
                            }
                        )
                    }
                }

                composable(
                    route = Destinations.ABOUT,
                    enterTransition = { AetherNavigationMotion.secondaryForwardEnter(calm) },
                    exitTransition = { AetherNavigationMotion.secondaryForwardExit(calm) },
                    popEnterTransition = { AetherNavigationMotion.secondaryBackEnter(calm) },
                    popExitTransition = { AetherNavigationMotion.secondaryBackExit(calm) }
                ) {
                    AboutScreen(onBack = { navController.popBackStack() })
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
        // The ONE canonical call across both backends (Aether/LiveKit and
        // Telegram Beta). Surfaces observe the hub, never a backend flow.
        // Reading callHub builds the call stack, so a build with calling held
        // never reads it.
        val activeCall by (application as? AetherApplication)
            ?.takeIf { com.foresightlabs.aether.AetherFeatureFlags.CALLS_ENABLED }
            ?.callHub?.activeCall
            ?.collectAsStateWithLifecycle(initialValue = null)
            ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.foresightlabs.aether.domain.model.ActiveCall?>(null) }
        val navScope = rememberCoroutineScope()

        var remoteVideoFrame by remember { mutableStateOf<com.foresightlabs.aether.calls.media.DecodedVideoFrame?>(null) }
        var localVideoFrame by remember { mutableStateOf<com.foresightlabs.aether.calls.media.DecodedVideoFrame?>(null) }
        LaunchedEffect(callsRepository) {
            callsRepository?.videoFrames?.collect { frame ->
                when (frame.origin) {
                    com.foresightlabs.aether.calls.media.VideoFrameOrigin.REMOTE -> remoteVideoFrame = frame
                    com.foresightlabs.aether.calls.media.VideoFrameOrigin.LOCAL -> localVideoFrame = frame
                }
            }
        }
        LaunchedEffect(activeCall?.callId) {
            if (activeCall == null) {
                remoteVideoFrame = null
                localVideoFrame = null
            }
        }

        // Aether Calls: poll the dev call service for incoming invites while
        // a real app surface is up (dev prototype has no push; data-layer
        // tests never reach composition, so no network churn under test).
        LaunchedEffect(callsRepository) {
            if (com.foresightlabs.aether.AetherFeatureFlags.AETHER_CALLS_ENABLED) {
                (application as? AetherApplication)?.aetherCallsRepository?.startIncomingPolling()
            }
        }

        // The active call renders as this full-screen surface or NOT AT ALL:
        // there is deliberately no floating call bar/pill anywhere else in the
        // app. Back from the call screen leaves the call running in the
        // background (foreground-service notification persists); the owning
        // conversation's animated call icon is the way back in.
        if (com.foresightlabs.aether.AetherFeatureFlags.CALLS_ENABLED && activeCall != null && !activeCall!!.isMinimized) {
            run {
                // ONE CallScreen, TWO backends: callbacks and video surfaces
                // dispatch on the call's backend. The screen itself never
                // learns which transport it is showing beyond a small label.
                val call = activeCall!!
                val aetherBackend = call.backend == com.foresightlabs.aether.domain.calls.CallBackend.AETHER
                val aetherRepo = (application as? AetherApplication)?.aetherCallsRepository
                AetherCallScreen(
                    activeCall = call,
                    onAcceptCall = { callId ->
                        navScope.launch {
                            if (aetherBackend) aetherRepo?.acceptCall() else callsRepository?.acceptCall(callId)
                        }
                    },
                    onDiscardCall = { callId ->
                        navScope.launch {
                            if (aetherBackend) {
                                if (call.state == com.foresightlabs.aether.domain.model.CallStateEnum.PENDING) {
                                    aetherRepo?.declineCall()
                                } else {
                                    aetherRepo?.endCall()
                                }
                            } else {
                                callsRepository?.discardCall(callId)
                            }
                        }
                    },
                    onToggleMute = {
                        if (aetherBackend) aetherRepo?.setMuted(!call.isMuted) else callsRepository?.toggleMute()
                    },
                    onToggleSpeaker = {
                        if (aetherBackend) aetherRepo?.toggleSpeaker() else callsRepository?.toggleSpeaker()
                    },
                    onMinimize = { (application as? AetherApplication)?.callHub?.minimizeActiveCall() },
                    remoteVideoFrame = if (aetherBackend) null else remoteVideoFrame,
                    localVideoFrame = if (aetherBackend) null else localVideoFrame,
                    // Camera intent lives on ActiveCall itself (synced from the
                    // media engine and the user's toggles) -- never in screen-local
                    // state, which used to drift from what the engine actually did.
                    isCameraEnabled = call.cameraIntentOn,
                    onToggleCamera = {
                        if (aetherBackend) aetherRepo?.setCameraEnabled(!call.cameraIntentOn)
                        else callsRepository?.setCameraEnabled(!call.cameraIntentOn)
                    },
                    onSwitchCamera = {
                        if (aetherBackend) aetherRepo?.switchCamera() else callsRepository?.switchCamera()
                    },
                    mediaHealthProvider = { callsRepository?.mediaHealth },
                    remoteVideoContent = if (aetherBackend && call.isVideo) {
                        {
                            val track = aetherRepo?.remoteVideoTrack?.collectAsStateWithLifecycle()?.value
                            if (track != null) {
                                com.foresightlabs.aether.ui.calls.LiveKitVideoSurface(
                                    trackProvider = { aetherRepo.remoteVideoTrack.value },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        null
                    },
                    localVideoContent = if (aetherBackend && call.isVideo) {
                        {
                            val track = aetherRepo?.localVideoTrack?.collectAsStateWithLifecycle()?.value
                            val front = aetherRepo?.localFrontFacing?.collectAsStateWithLifecycle()?.value ?: true
                            if (track != null) {
                                com.foresightlabs.aether.ui.calls.LiveKitVideoSurface(
                                    trackProvider = { aetherRepo.localVideoTrack.value },
                                    modifier = Modifier.fillMaxSize(),
                                    mirror = front
                                )
                            }
                        }
                    } else {
                        null
                    }
                )
            }
        }
            }
            }
            }
    }
}

/**
 * Gives [content] its own [ViewModelStore], scoped to [key], instead of whatever
 * [LocalViewModelStoreOwner] it would otherwise inherit. A new [key] clears the
 * previous store -- and with it every ViewModel created inside, via the normal
 * onCleared() path -- so a caller outside NavHost's own back-stack scoping still
 * gets a ViewModel lifecycle tied to which [key] is current, not to the process.
 */
@Composable
internal fun ChatScopedViewModelStoreOwner(key: Any, content: @Composable () -> Unit) {
    val owner = remember(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(key) {
        onDispose { owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content()
    }
}

@Composable
private fun ConversationRoute(
    application: Application,
    target: com.foresightlabs.aether.domain.model.ConversationTarget,
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToChatAppearance: (Long) -> Unit,
    /** Jump to this message once the conversation is open (bookmark / shared content); 0 = none. */
    pendingJumpMessageId: Long = 0L,
    onPendingJumpConsumed: () -> Unit = {}
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
    // A bookmark or shared-content entry that opens the conversation at a
    // specific message. jumpTo defers until the chat is open and loads the
    // surrounding window when needed; the request is consumed at once so it
    // can never fire twice.
    androidx.compose.runtime.LaunchedEffect(pendingJumpMessageId) {
        if (pendingJumpMessageId != 0L) {
            viewModel.jumpTo(pendingJumpMessageId.toString())
            onPendingJumpConsumed()
        }
    }
    val isResolving by viewModel.isResolving.collectAsStateWithLifecycle()
    val resolveError by viewModel.resolveError.collectAsStateWithLifecycle()
    val header by viewModel.header.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val activeCallForThisChat by viewModel.activeCallForThisChat.collectAsStateWithLifecycle()
    // Same rule as Profile: the microphone grant is obtained before the call is
    // placed, never after it has already connected.
    val startConversationCall = com.foresightlabs.aether.ui.calls.rememberCallStarter { isVideo ->
        if (isVideo) viewModel.initiateVideoCall() else viewModel.initiateAudioCall()
    }

    // Backend chooser: when the recipient is ALSO an Aether-calling user,
    // both systems can reach them and the user picks. No directory hit ->
    // Telegram goes directly (an Aether-unavailable contact is normal).
    val routeScope = rememberCoroutineScope()
    var chooserRequest by remember {
        androidx.compose.runtime.mutableStateOf<CallChooserRequest?>(null)
    }
    // Constructing the LiveKit repository is itself call initialization; a
    // build with Aether Calls held must never do it on a conversation open.
    val aetherRepo = if (com.foresightlabs.aether.AetherFeatureFlags.AETHER_CALLS_ENABLED) {
        (application as? AetherApplication)?.aetherCallsRepository
    } else {
        null
    }
    // Aether start pending its mic-permission gate: the chooser's request is
    // snapshotted HERE, never re-read from chooserRequest inside the start
    // coroutine -- the sheet's hide() also fires onDismissRequest (which drops
    // chooserRequest), so a starter that read the shared state could see null
    // and silently start nothing.
    var pendingAetherRequest by remember {
        androidx.compose.runtime.mutableStateOf<CallChooserRequest?>(null)
    }
    val startAetherCall = com.foresightlabs.aether.ui.calls.rememberCallStarter { isVideo ->
        val request = pendingAetherRequest
        pendingAetherRequest = null
        if (request != null) {
            routeScope.launch {
                aetherRepo?.initiateCall(
                    target = request.target,
                    isVideo = isVideo,
                    callerName = request.calleeName
                )
            }
        }
    }
    val onCallIntent: (Boolean) -> Unit = { isVideo ->
        routeScope.launch {
            val contactUserId = header?.directUser?.id?.toLongOrNull()
            val target = if (contactUserId != null && contactUserId > 0) {
                aetherRepo?.aetherIdentityFor(contactUserId)
            } else {
                null
            }
            when {
                target == null -> startConversationCall(isVideo)
                else -> chooserRequest = CallChooserRequest(header?.title ?: "contact", isVideo, target)
            }
        }
    }
    val canSend by viewModel.composerEnabled.collectAsStateWithLifecycle()
    val messageCapabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val forwardTargets by viewModel.forwardTargets.collectAsStateWithLifecycle()
    val forwardState by viewModel.forwardState.collectAsStateWithLifecycle()
    val searchState by viewModel.search.collectAsStateWithLifecycle()
    val jumpTarget by viewModel.jumpTarget.collectAsStateWithLifecycle()
    val messageMotionEvents by viewModel.messageMotionEvents.collectAsStateWithLifecycle()
    val sendError by viewModel.sendError.collectAsStateWithLifecycle()
    val pinnedMessages by viewModel.pinnedMessages.collectAsStateWithLifecycle()
    val installedStickerSets by viewModel.installedStickerSets.collectAsStateWithLifecycle()
    val recentStickers by viewModel.recentStickers.collectAsStateWithLifecycle()
    val favoriteStickers by viewModel.favoriteStickers.collectAsStateWithLifecycle()
    val savedAnimations by viewModel.savedAnimations.collectAsStateWithLifecycle()
    val linkPreview by viewModel.linkPreview.collectAsStateWithLifecycle()
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
    val resolvedAtmosphere = resolvedAppearance?.let { buildAtmosphere(it.palette) }
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
            onTogglePin = viewModel::toggleChatPinned,
            onSendMessage = { text, reply, formatting, quote ->
                viewModel.send(text, reply?.id, formatting, quote)
            },
            onSendPhoto = { path, caption, reply, viewOnce -> viewModel.sendPhoto(path, caption, reply?.id, viewOnce) },
            onSendVideo = { path, caption, duration, reply, viewOnce -> viewModel.sendVideo(path, caption, duration, reply?.id, viewOnce) },
            onSendDocument = { path, caption, reply -> viewModel.sendDocument(path, caption, reply?.id) },
            onSendAudio = { path, title, performer, duration, caption, reply ->
                viewModel.sendAudio(path, title, performer, duration, caption, reply?.id)
            },
            onSendPhotoAlbum = { paths, caption, reply -> viewModel.sendPhotoAlbum(paths, caption, reply?.id) },
            onSendMixedBatch = { items, caption, reply -> viewModel.sendSharedBatch(items, caption, reply?.id) },
            onResolveMessage = { id -> viewModel.resolveReplyEditTarget(id) },
            onSendVoiceNote = { path, duration, wave, replyId, onResult ->
                viewModel.sendVoiceNote(path, duration, wave, replyId, onResult)
            },
            onVoiceRecordingStarted = viewModel::onVoiceRecordingStarted,
            onVoiceRecordingEnded = viewModel::onActivityEnded,
            onEditMessage = viewModel::editMessage, onAddReaction = viewModel::addReaction,
            onPinMessage = viewModel::pinMessage, onComposerChanged = viewModel::onComposerChanged,
            onToggleBookmark = viewModel::toggleBookmark,
            bookmarkedMessageIds = viewModel.bookmarkedMessageIds.collectAsStateWithLifecycle().value,
            restoredDraft = viewModel.restoredDraft.collectAsStateWithLifecycle().value,
            onReplyDraftChanged = viewModel::onDraftReplyChanged,
            linkPreview = linkPreview, onDismissLinkPreview = viewModel::dismissLinkPreview,
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
            onStartVoiceCall = { onCallIntent(false) },
            onStartVideoCall = { onCallIntent(true) },
            activeCall = activeCallForThisChat,
            onResumeCall = { (application as? AetherApplication)?.callHub?.resumeActiveCall() },
            ownsActiveCall = activeCallForThisChat?.isMinimized == true,
            isCallMediaAvailable = viewModel.isCallMediaAvailable,
            onToggleCallMute = viewModel::toggleCallMute,
            onToggleCallSpeaker = viewModel::toggleCallSpeaker,
            onToggleCallCamera = viewModel::setCallCameraEnabled,
            onSwitchCallCamera = viewModel::switchCallCamera,
            onEndCall = viewModel::endActiveCall,
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
            unreadBoundaryId = viewModel.unreadBoundaryId.collectAsStateWithLifecycle().value,
            jumpFailures = viewModel.jumpFailures,
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
            },
            onRedownloadMedia = viewModel::redownloadMedia,
            errorMessage = sendError,
            onErrorConsumed = viewModel::consumeSendError,
            onOpenMessageContent = viewModel::openMessageContent
        )

        // The backend chooser: small bottom sheet, only when BOTH systems can
        // reach the recipient. Aether first (Recommended), Telegram clearly
        // Beta. The sheet hides itself BEFORE invoking onChoose (its window
        // must detach or it eats all input); hide() also fires onDismiss, so
        // the choice is snapshotted before that can drop anything.
        chooserRequest?.let { request ->
            com.foresightlabs.aether.ui.calls.CallChooserSheet(
                calleeName = request.calleeName,
                isVideo = request.isVideo,
                onChoose = { backend ->
                    val chosen = chooserRequest
                    chooserRequest = null
                    if (chosen != null) {
                        when (backend) {
                            com.foresightlabs.aether.domain.calls.CallBackend.AETHER -> {
                                pendingAetherRequest = chosen
                                startAetherCall(chosen.isVideo)
                            }
                            com.foresightlabs.aether.domain.calls.CallBackend.TELEGRAM_BETA ->
                                startConversationCall(chosen.isVideo)
                        }
                    }
                },
                onDismiss = { chooserRequest = null }
            )
        }
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

/**
 * Shows the most recent Telegram service notification, if any.
 *
 * A plain platform dialog rather than a Curtain surface: this is not part of a
 * conversation, it interrupts whatever route is showing, and TDLib's contract for
 * these is explicitly a popup.
 *
 * The auth-key-drop variant is labelled but deliberately offers no "log out and
 * destroy local data" button -- see [ServiceNotice.requiresAuthKeyDropPrompt].
 */
@Composable
private fun TelegramServiceNoticePrompt() {
    val application = LocalContext.current.applicationContext as? AetherApplication ?: return
    val telegram = application.telegram
    val notice by telegram.serviceNotice.collectAsStateWithLifecycle()
    val current = notice ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { telegram.acknowledgeServiceNotice() },
        title = {
            androidx.compose.material3.Text(
                if (current.requiresAuthKeyDropPrompt) "Telegram security notice" else "Telegram"
            )
        },
        text = { androidx.compose.material3.Text(current.text) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { telegram.acknowledgeServiceNotice() }) {
                androidx.compose.material3.Text("OK")
            }
        }
    )
}


/** A pending backend choice for a call the user is about to place. */
data class CallChooserRequest(
    val calleeName: String,
    val isVideo: Boolean,
    val target: com.foresightlabs.aether.data.calls.aether.AetherCallingIdentity
)
