package com.foresightlabs.aether.domain.calls

import com.foresightlabs.aether.domain.model.ActiveCall
import com.foresightlabs.aether.domain.model.CallHistoryUiState
import kotlinx.coroutines.flow.StateFlow

interface CallsRepository {

    /**
     * Whether a call started right now could actually carry audio.
     *
     * Call affordances are driven by this rather than by whether TDLib would accept
     * a `CreateCall`: TDLib will happily ring the other device for a call that has
     * no media path, which is worse than not offering the call at all.
     */
    val isCallMediaAvailable: Boolean

    /** Why calling is unavailable, for display. Null when [isCallMediaAvailable]. */
    val callMediaUnavailableReason: String?

    val activeCallState: StateFlow<ActiveCall?>
    val historyState: StateFlow<CallHistoryUiState>

    suspend fun initiateCall(userId: Long): Result<Int>
    suspend fun acceptCall(callId: Int): Result<Unit>
    suspend fun discardCall(callId: Int): Result<Unit>
    fun toggleMute()
    fun toggleSpeaker()
    fun setMinimized(minimized: Boolean)

    suspend fun loadInitialHistory()
    suspend fun loadNextPageHistory()
    suspend fun refreshHistory()
}
