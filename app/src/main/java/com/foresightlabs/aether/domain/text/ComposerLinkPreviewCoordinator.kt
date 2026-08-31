package com.foresightlabs.aether.domain.text

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runs [ComposerLinkPreviewPolicy] against a real source of previews.
 *
 * The whole timing story lives here: one request per settled link rather than
 * one per keystroke, the previous request cancelled the moment the draft points
 * somewhere else, and an answer that arrives for a link the draft has since left
 * discarded rather than shown. [fetch] is the only thing that talks to Telegram,
 * which is what lets all of that be asserted without a network or a device.
 */
class ComposerLinkPreviewCoordinator(
    private val scope: CoroutineScope,
    private val debounceMs: Long = ComposerLinkPreviewPolicy.DEBOUNCE_MS,
    private val fetch: suspend (String) -> LinkPreviewCard?
) {
    private val _state = MutableStateFlow(ComposerLinkPreviewState.Empty)
    val state: StateFlow<ComposerLinkPreviewState> = _state.asStateFlow()

    private var job: Job? = null

    /** The draft changed: ask, stop asking, or leave things exactly as they are. */
    fun onDraftChanged(text: String) {
        when (val action = ComposerLinkPreviewPolicy.onDraftChanged(_state.value, text)) {
            ComposerLinkPreviewAction.Keep -> Unit
            ComposerLinkPreviewAction.Clear -> reset()
            is ComposerLinkPreviewAction.Request -> {
                job?.cancel()
                _state.value = ComposerLinkPreviewPolicy.requested(action.url)
                job = scope.launch {
                    delay(debounceMs)
                    // Telegram is asked about the draft exactly as it stands, so
                    // the preview generated is the preview the message would
                    // carry when sent.
                    val card = fetch(text)
                    _state.update { ComposerLinkPreviewPolicy.resolved(it, action.url, card) }
                }
            }
        }
    }

    /** The user closed the preview. The draft, including its link, is untouched. */
    fun dismiss() {
        job?.cancel()
        job = null
        _state.update { ComposerLinkPreviewPolicy.dismissed(it) }
    }

    /** Nothing is being previewed any more: after a send, or a cleared draft. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = ComposerLinkPreviewState.Empty
    }

    /** What sending [text] should tell Telegram about its link preview. */
    fun intentFor(text: String): LinkPreviewIntent =
        ComposerLinkPreviewPolicy.intentFor(_state.value, text)
}
