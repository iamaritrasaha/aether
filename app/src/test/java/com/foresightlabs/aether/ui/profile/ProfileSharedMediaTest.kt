package com.foresightlabs.aether.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foresightlabs.aether.data.telegram.TelegramClient
import com.foresightlabs.aether.domain.model.Chat
import com.foresightlabs.aether.domain.model.ChatType
import com.foresightlabs.aether.ui.theme.AetherTheme
import com.foresightlabs.aether.ui.theme.AppThemeState
import com.foresightlabs.aether.ui.theme.AtmosphereMode
import com.foresightlabs.aether.ui.theme.LocalAppThemeState
import com.foresightlabs.aether.ui.theme.TimeAtmospherePalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Profile shared media must be fetched once per category per profile open.
 *
 * The regression: two `LaunchedEffect`s both loaded the MEDIA category on entry --
 * one keyed on the chat, one on the selected tab, which starts at Media. Both
 * start at first composition, so the `isMediaLoaded` guard the second one used was
 * still false when it ran, and every profile open issued two identical
 * SearchChatMessages requests to TDLib.
 *
 * Counted through the real `onLoadSharedMedia` seam rather than asserted against
 * source text, so this fails if the duplicate is ever reintroduced by any route.
 */
@RunWith(AndroidJUnit4::class)
class ProfileSharedMediaTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val testChat = Chat(
        id = "201",
        title = "Kavita Rao",
        type = ChatType.DIRECT,
        lastMessageText = "See you tomorrow!",
        lastMessageTime = "11:00 AM",
        avatarInitials = "KR",
        avatarGradient = listOf(Color(0xFF6366F1), Color(0xFF4F46E5)),
        blockableUserId = 201L
    )

    private fun renderProfile(
        requests: ConcurrentLinkedQueue<TelegramClient.SharedMediaCategory>
    ) {
        composeRule.setContent {
            val theme = AppThemeState().apply {
                atmosphereMode = AtmosphereMode.MANUAL
                manualAtmosphere = TimeAtmospherePalette.DAY
            }
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAppThemeState provides theme
            ) {
                AetherTheme(themeState = theme) {
                    ProfileScreen(
                        chat = testChat,
                        onBack = {},
                        onNavigateToConversation = {},
                        onLoadSharedMedia = { _, category, _ ->
                            requests.add(category)
                            TelegramClient.SharedMediaPage(emptyList(), 0L, 0)
                        }
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun opening_a_profile_requests_shared_media_exactly_once() {
        val requests = ConcurrentLinkedQueue<TelegramClient.SharedMediaCategory>()
        renderProfile(requests)

        val mediaRequests = requests.count { it == TelegramClient.SharedMediaCategory.MEDIA }
        assertEquals(
            "Opening a profile must issue exactly one MEDIA shared-media request, not two",
            1,
            mediaRequests
        )
    }

    @Test
    fun opening_a_profile_does_not_prefetch_the_other_categories() {
        // Files/Links/Voice belong to tabs the user has not opened. Fetching them
        // eagerly would be three more TDLib round trips per profile open.
        val requests = ConcurrentLinkedQueue<TelegramClient.SharedMediaCategory>()
        renderProfile(requests)

        assertEquals(
            listOf(TelegramClient.SharedMediaCategory.MEDIA),
            requests.toList()
        )
    }
}
