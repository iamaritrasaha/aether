package com.foresightlabs.aether.domain.text

import androidx.compose.runtime.Immutable

/**
 * Finding the link a draft is about.
 *
 * Detection only. Whether a link *has* a preview is Telegram's answer, never
 * Aether's: nothing here fetches a page, and no site content is read to decide
 * what to show. The single job is to know which URL — if any — is worth asking
 * Telegram about, so a draft with no link never costs a request.
 */
object ComposerLinks {

    // Scheme-carrying links, and the www-prefixed form people paste without one.
    private val EXPLICIT = Regex(
        "(?:https?|tg)://[^\\s<>\"']+|www\\.[^\\s<>\"']+",
        RegexOption.IGNORE_CASE
    )

    // A bare host, the way Telegram itself treats "example.com/page" as a link.
    private val BARE_HOST = Regex(
        "(?<![\\w@./-])[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}(?::\\d{1,5})?(?:/[^\\s<>\"']*)?",
        RegexOption.IGNORE_CASE
    )

    /** Punctuation that ends a sentence rather than a URL. */
    private const val TRAILING = ".,;:!?…'\"”’)]}"

    /**
     * The URL a preview would be generated for, or null when there is none.
     *
     * The *first* URL, because that is what TDLib itself uses when link preview
     * options name no URL explicitly. A draft with several links therefore
     * previews the same one Telegram would have chosen on its own.
     */
    fun firstUrl(text: String): String? = urls(text).firstOrNull()

    /** Every URL in [text], in the order they were typed. */
    fun urls(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val found = mutableListOf<Pair<IntRange, String>>()
        EXPLICIT.findAll(text).forEach { match ->
            trim(match.value)?.let { found += match.range to it }
        }
        BARE_HOST.findAll(text).forEach { match ->
            // An explicit match already covers this span; the bare-host pass is
            // only here for links written without a scheme.
            if (found.none { match.range.first >= it.first.first && match.range.first <= it.first.last }) {
                trim(match.value)?.let { found += match.range to it }
            }
        }
        return found.sortedBy { it.first.first }.map { it.second }
    }

    /** True when [text] contains something worth asking Telegram about. */
    fun hasUrl(text: String): Boolean = firstUrl(text) != null

    /**
     * Whether two drafts point at the same link.
     *
     * Keystrokes that leave the URL alone must not restart a request, and a URL
     * that genuinely changed must invalidate what was showing for the old one.
     */
    fun sameTarget(first: String?, second: String?): Boolean =
        normalise(first) == normalise(second)

    private fun normalise(url: String?): String? = url
        ?.trim()
        ?.removeSuffix("/")
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }

    private fun trim(raw: String): String? {
        var value = raw
        while (value.isNotEmpty() && TRAILING.contains(value.last())) {
            // A closing bracket is part of the URL when the URL opened it.
            val last = value.last()
            if (last == ')' && value.count { it == '(' } > value.count { it == ')' }) break
            value = value.dropLast(1)
        }
        if (value.isEmpty()) return null
        val host = value
            .substringAfter("://", value)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
        if (!host.contains('.')) return null
        val tld = host.substringAfterLast('.').lowercase()
        if (tld.length < 2 || !tld.all { it.isLetter() }) return null
        // A filename typed into a sentence is not a link anyone meant to share,
        // and asking Telegram about one would spend a request to be told so.
        val written = value.contains("://") || value.startsWith("www.", ignoreCase = true)
        if (!written && tld in FILE_SUFFIXES) return null
        return value
    }

    /**
     * Endings that are far likelier to be a file someone mentioned than a host
     * they meant to link. Consulted only for bare hosts -- a link written as
     * "https://notes.md" or "www.notes.md" is taken at its word.
     */
    private val FILE_SUFFIXES = setOf(
        "md", "txt", "png", "jpg", "jpeg", "gif", "webp", "pdf", "zip", "gz",
        "kt", "kts", "java", "json", "yaml", "yml", "xml", "html", "css",
        "js", "ts", "py", "sh", "rb", "go", "rs", "csv", "log", "apk", "aab"
    )
}

/**
 * What Telegram said about a link, reduced to what the Composer shows.
 *
 * Every field comes from TDLib's own `linkPreview`. Nothing is inferred from
 * the page, because Aether never reads the page.
 */
@Immutable
data class LinkPreviewCard(
    val url: String,
    val displayUrl: String,
    val siteName: String = "",
    val title: String = "",
    val description: String = "",
    /** A downloaded thumbnail on disk, when Telegram had one and it has arrived. */
    val thumbnailPath: String? = null,
    /** Telegram's tiny embedded preview, available the instant the preview is. */
    val thumbnailBase64: String? = null
) {
    val hasThumbnail: Boolean get() = thumbnailPath != null || thumbnailBase64 != null

    /** The strongest line of text Telegram gave for this link. */
    val heading: String get() = title.ifBlank { siteName }.ifBlank { displayUrl }
}

/**
 * The Composer's link preview, as one value.
 *
 * Held in the ViewModel rather than in composition, so a recomposition can
 * never restart a request, and so the answer survives the Curtain changing
 * state underneath it.
 */
@Immutable
data class ComposerLinkPreviewState(
    /** The URL this state is about; null when the draft has no link. */
    val url: String? = null,
    val isLoading: Boolean = false,
    val card: LinkPreviewCard? = null,
    /**
     * A URL the user closed the preview for. The URL stays in the draft; only
     * the preview is gone, and it stays gone until the draft points elsewhere.
     */
    val dismissedUrl: String? = null
) {
    val isVisible: Boolean get() = isLoading || card != null

    companion object {
        val Empty = ComposerLinkPreviewState()
    }
}

/** What the draft's current text means for the preview. */
sealed interface ComposerLinkPreviewAction {
    /** Ask Telegram about [url]; anything showing for another URL is stale. */
    data class Request(val url: String) : ComposerLinkPreviewAction

    /** The draft has no link, or none worth previewing: show nothing. */
    data object Clear : ComposerLinkPreviewAction

    /** Already asking, already answered, or deliberately dismissed. */
    data object Keep : ComposerLinkPreviewAction
}

/**
 * The rules the Composer preview follows, with no coroutines, no TDLib and no
 * Compose in sight — so every one of them can be asserted directly.
 */
object ComposerLinkPreviewPolicy {

    /** Long enough that typing a URL out is one request, short enough to feel live. */
    const val DEBOUNCE_MS: Long = 420L

    fun onDraftChanged(state: ComposerLinkPreviewState, text: String): ComposerLinkPreviewAction {
        val url = ComposerLinks.firstUrl(text)
        if (url == null) {
            return if (state == ComposerLinkPreviewState.Empty) {
                ComposerLinkPreviewAction.Keep
            } else {
                ComposerLinkPreviewAction.Clear
            }
        }
        // Dismissed stays dismissed while the draft still points at that link.
        if (ComposerLinks.sameTarget(url, state.dismissedUrl)) return ComposerLinkPreviewAction.Keep
        // The same link as last keystroke: in flight or answered, either way
        // there is nothing new to ask.
        if (ComposerLinks.sameTarget(url, state.url) && (state.isLoading || state.card != null)) {
            return ComposerLinkPreviewAction.Keep
        }
        return ComposerLinkPreviewAction.Request(url)
    }

    /** The state while Telegram is being asked about [url]. */
    fun requested(url: String): ComposerLinkPreviewState =
        ComposerLinkPreviewState(url = url, isLoading = true)

    /**
     * Telegram's answer.
     *
     * A reply for a URL the draft has since moved off is dropped: the preview
     * showing must always be the one for the link that is actually there.
     * A null [card] means Telegram has no preview for this link, and that fails
     * quietly — no error, no empty frame.
     */
    fun resolved(
        state: ComposerLinkPreviewState,
        url: String,
        card: LinkPreviewCard?
    ): ComposerLinkPreviewState {
        if (!ComposerLinks.sameTarget(url, state.url)) return state
        return state.copy(isLoading = false, card = card)
    }

    /** The user closed the preview. The draft text is not touched. */
    fun dismissed(state: ComposerLinkPreviewState): ComposerLinkPreviewState {
        val url = state.url ?: return state
        return ComposerLinkPreviewState(dismissedUrl = url)
    }

    /**
     * What sending this draft should tell Telegram about its link preview.
     *
     * @see LinkPreviewIntent
     */
    fun intentFor(state: ComposerLinkPreviewState, text: String): LinkPreviewIntent {
        val url = ComposerLinks.firstUrl(text) ?: return LinkPreviewIntent.Default
        if (ComposerLinks.sameTarget(url, state.dismissedUrl)) return LinkPreviewIntent.Disabled
        val card = state.card ?: return LinkPreviewIntent.Default
        if (!ComposerLinks.sameTarget(url, state.url)) return LinkPreviewIntent.Default
        return LinkPreviewIntent.Show(card.url)
    }
}

/** How a message should be sent with respect to its link preview. */
sealed interface LinkPreviewIntent {
    /** Let Telegram decide, exactly as it did before previews were shown. */
    data object Default : LinkPreviewIntent

    /** The user closed the preview: send the link without one. */
    data object Disabled : LinkPreviewIntent

    /** Send the preview the user actually saw, named explicitly. */
    data class Show(val url: String) : LinkPreviewIntent
}
