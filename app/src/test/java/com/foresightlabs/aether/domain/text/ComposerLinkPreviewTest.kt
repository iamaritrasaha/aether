package com.foresightlabs.aether.domain.text

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Composer's link preview, asserted where all of its rules actually live.
 *
 * Detection, the request/keep/clear decision, debouncing, dismissal, and what a
 * send should tell Telegram are plain functions and a small coordinator, so all
 * of it is verified here without a device, a network or a running TDLib.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposerLinkPreviewTest {

    // -----------------------------------------------------------------------
    // Detection
    // -----------------------------------------------------------------------

    @Test
    fun detectsExplicitUrls() {
        assertEquals("https://example.com", ComposerLinks.firstUrl("https://example.com"))
        assertEquals(
            "http://example.com/a/b?c=d",
            ComposerLinks.firstUrl("look at http://example.com/a/b?c=d please")
        )
        assertEquals("www.example.com", ComposerLinks.firstUrl("www.example.com"))
    }

    @Test
    fun detectsBareHostTheWayTelegramDoes() {
        assertEquals("example.com/page", ComposerLinks.firstUrl("read example.com/page today"))
        assertEquals("telegram.org", ComposerLinks.firstUrl("telegram.org"))
    }

    @Test
    fun trailingSentencePunctuationIsNotPartOfTheUrl() {
        assertEquals("https://example.com", ComposerLinks.firstUrl("see https://example.com."))
        assertEquals("https://example.com/a", ComposerLinks.firstUrl("(https://example.com/a)"))
    }

    @Test
    fun plainTextHasNoUrl() {
        assertNull(ComposerLinks.firstUrl("just a normal message"))
        assertNull(ComposerLinks.firstUrl(""))
        assertNull(ComposerLinks.firstUrl("   "))
        assertNull(ComposerLinks.firstUrl("meet at 3.30 sharp"))
        assertNull(ComposerLinks.firstUrl("open notes.md later"))
        assertFalse(ComposerLinks.hasUrl("no links here at all"))
    }

    @Test
    fun severalUrlsPreviewTheFirst() {
        // TDLib itself uses the first URL in the text when link preview options
        // name none, so the Composer shows the same one Telegram would choose.
        val text = "https://one.example.com and https://two.example.com"
        assertEquals(listOf("https://one.example.com", "https://two.example.com"), ComposerLinks.urls(text))
        assertEquals("https://one.example.com", ComposerLinks.firstUrl(text))
    }

    // -----------------------------------------------------------------------
    // The request / keep / clear decision
    // -----------------------------------------------------------------------

    @Test
    fun aDraftWithALinkRequestsAPreview() {
        val action = ComposerLinkPreviewPolicy.onDraftChanged(
            ComposerLinkPreviewState.Empty,
            "look at https://example.com"
        )
        assertEquals(ComposerLinkPreviewAction.Request("https://example.com"), action)
    }

    @Test
    fun theSameLinkIsNeverRequestedTwice() {
        val loading = ComposerLinkPreviewPolicy.requested("https://example.com")
        assertEquals(
            ComposerLinkPreviewAction.Keep,
            ComposerLinkPreviewPolicy.onDraftChanged(loading, "look at https://example.com")
        )
        val answered = ComposerLinkPreviewPolicy.resolved(loading, "https://example.com", card())
        assertEquals(
            ComposerLinkPreviewAction.Keep,
            ComposerLinkPreviewPolicy.onDraftChanged(answered, "look at https://example.com now")
        )
    }

    @Test
    fun replacingTheUrlInvalidatesThePreviousPreview() {
        val answered = ComposerLinkPreviewPolicy.resolved(
            ComposerLinkPreviewPolicy.requested("https://example.com"),
            "https://example.com",
            card()
        )
        assertEquals(
            ComposerLinkPreviewAction.Request("https://other.example.org"),
            ComposerLinkPreviewPolicy.onDraftChanged(answered, "https://other.example.org")
        )
    }

    @Test
    fun losingTheUrlClearsThePreview() {
        val answered = ComposerLinkPreviewPolicy.resolved(
            ComposerLinkPreviewPolicy.requested("https://example.com"),
            "https://example.com",
            card()
        )
        assertEquals(
            ComposerLinkPreviewAction.Clear,
            ComposerLinkPreviewPolicy.onDraftChanged(answered, "no link any more")
        )
        // An empty state with no link has nothing to clear.
        assertEquals(
            ComposerLinkPreviewAction.Keep,
            ComposerLinkPreviewPolicy.onDraftChanged(ComposerLinkPreviewState.Empty, "plain text")
        )
    }

    @Test
    fun whenTelegramHasNoPreviewNothingIsShown() {
        val state = ComposerLinkPreviewPolicy.resolved(
            ComposerLinkPreviewPolicy.requested("https://example.com"),
            "https://example.com",
            null
        )
        assertFalse("An unavailable preview must fail quietly", state.isVisible)
        assertNull(state.card)
        assertFalse(state.isLoading)
    }

    @Test
    fun anAnswerForAnAbandonedLinkIsDiscarded() {
        val current = ComposerLinkPreviewPolicy.requested("https://second.example.com")
        val stale = ComposerLinkPreviewPolicy.resolved(current, "https://first.example.com", card())
        assertEquals(current, stale)
    }

    // -----------------------------------------------------------------------
    // Dismissal
    // -----------------------------------------------------------------------

    @Test
    fun dismissingHidesThePreviewAndKeepsItDismissed() {
        val answered = ComposerLinkPreviewPolicy.resolved(
            ComposerLinkPreviewPolicy.requested("https://example.com"),
            "https://example.com",
            card()
        )
        val dismissed = ComposerLinkPreviewPolicy.dismissed(answered)
        assertFalse(dismissed.isVisible)
        assertEquals("https://example.com", dismissed.dismissedUrl)
        // Typing on around the same link must not bring it back.
        assertEquals(
            ComposerLinkPreviewAction.Keep,
            ComposerLinkPreviewPolicy.onDraftChanged(dismissed, "https://example.com is worth reading")
        )
        // A different link is a different question.
        assertEquals(
            ComposerLinkPreviewAction.Request("https://other.example.org"),
            ComposerLinkPreviewPolicy.onDraftChanged(dismissed, "https://other.example.org")
        )
    }

    // -----------------------------------------------------------------------
    // What a send tells Telegram
    // -----------------------------------------------------------------------

    @Test
    fun sendingCarriesThePreviewTheUserSaw() {
        val answered = ComposerLinkPreviewPolicy.resolved(
            ComposerLinkPreviewPolicy.requested("https://example.com"),
            "https://example.com",
            card()
        )
        assertEquals(
            LinkPreviewIntent.Show("https://example.com"),
            ComposerLinkPreviewPolicy.intentFor(answered, "read https://example.com")
        )
    }

    @Test
    fun sendingADismissedPreviewDisablesIt() {
        val dismissed = ComposerLinkPreviewPolicy.dismissed(
            ComposerLinkPreviewPolicy.resolved(
                ComposerLinkPreviewPolicy.requested("https://example.com"),
                "https://example.com",
                card()
            )
        )
        assertEquals(
            LinkPreviewIntent.Disabled,
            ComposerLinkPreviewPolicy.intentFor(dismissed, "read https://example.com")
        )
    }

    @Test
    fun textWithoutALinkSendsExactlyAsBefore() {
        assertEquals(
            LinkPreviewIntent.Default,
            ComposerLinkPreviewPolicy.intentFor(ComposerLinkPreviewState.Empty, "plain message")
        )
        // Still loading when send is pressed: Telegram's own default applies.
        assertEquals(
            LinkPreviewIntent.Default,
            ComposerLinkPreviewPolicy.intentFor(
                ComposerLinkPreviewPolicy.requested("https://example.com"),
                "https://example.com"
            )
        )
    }

    // -----------------------------------------------------------------------
    // Debouncing and cancellation
    // -----------------------------------------------------------------------

    @Test
    fun typingAUrlOutCostsOneRequest() = runTest {
        val asked = mutableListOf<String>()
        val coordinator = ComposerLinkPreviewCoordinator(this, DEBOUNCE) { draft ->
            asked += draft
            card()
        }

        "https://example.com".forEachIndexed { index, _ ->
            coordinator.onDraftChanged("https://example.com".take(index + 1))
            advanceTimeBy(20)
        }
        advanceUntilIdle()

        assertEquals(listOf("https://example.com"), asked)
        assertEquals("https://example.com", coordinator.state.value.card?.url)
    }

    @Test
    fun keystrokesThatLeaveTheLinkAloneDoNotRequestAgain() = runTest {
        var requests = 0
        val coordinator = ComposerLinkPreviewCoordinator(this, DEBOUNCE) {
            requests++
            card()
        }

        coordinator.onDraftChanged("https://example.com")
        advanceUntilIdle()
        coordinator.onDraftChanged("https://example.com is worth reading")
        coordinator.onDraftChanged("https://example.com is worth reading today")
        advanceUntilIdle()

        assertEquals(1, requests)
    }

    @Test
    fun replacingTheUrlCancelsTheRequestInFlight() = runTest {
        val asked = mutableListOf<String>()
        val coordinator = ComposerLinkPreviewCoordinator(this, DEBOUNCE) { draft ->
            asked += draft
            card(url = ComposerLinks.firstUrl(draft).orEmpty())
        }

        coordinator.onDraftChanged("https://first.example.com")
        advanceTimeBy(DEBOUNCE / 2)
        coordinator.onDraftChanged("https://second.example.com")
        advanceUntilIdle()

        assertEquals(listOf("https://second.example.com"), asked)
        assertEquals("https://second.example.com", coordinator.state.value.card?.url)
    }

    @Test
    fun dismissingCancelsAndStopsAsking() = runTest {
        var requests = 0
        val coordinator = ComposerLinkPreviewCoordinator(this, DEBOUNCE) {
            requests++
            card()
        }

        coordinator.onDraftChanged("https://example.com")
        advanceUntilIdle()
        coordinator.dismiss()
        coordinator.onDraftChanged("https://example.com still here")
        advanceUntilIdle()

        assertEquals(1, requests)
        assertFalse(coordinator.state.value.isVisible)
        assertEquals(
            LinkPreviewIntent.Disabled,
            coordinator.intentFor("https://example.com still here")
        )
    }

    @Test
    fun aDraftWithNoLinkNeverAsks() = runTest {
        var requests = 0
        val coordinator = ComposerLinkPreviewCoordinator(this, DEBOUNCE) {
            requests++
            card()
        }

        coordinator.onDraftChanged("hello")
        coordinator.onDraftChanged("hello there")
        advanceUntilIdle()

        assertEquals(0, requests)
        assertFalse(coordinator.state.value.isVisible)
    }

    @Test
    fun theLoadingStateIsVisibleWhileTelegramIsAsked() = runTest {
        val coordinator = ComposerLinkPreviewCoordinator(this, DEBOUNCE) { card() }
        coordinator.onDraftChanged("https://example.com")

        assertTrue(coordinator.state.value.isLoading)
        assertTrue(coordinator.state.value.isVisible)
        assertNull(coordinator.state.value.card)

        advanceUntilIdle()
        assertFalse(coordinator.state.value.isLoading)
    }

    private fun card(url: String = "https://example.com") = LinkPreviewCard(
        url = url,
        displayUrl = url.removePrefix("https://"),
        siteName = "Example",
        title = "Example title",
        description = "A short description"
    )

    private companion object {
        const val DEBOUNCE = 400L
    }
}
