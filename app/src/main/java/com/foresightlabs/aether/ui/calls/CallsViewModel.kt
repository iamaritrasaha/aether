package com.foresightlabs.aether.ui.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.domain.calls.CallsRepository
import com.foresightlabs.aether.domain.model.CallHistoryUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CallsViewModel(
    private val callsRepository: CallsRepository
) : ViewModel() {

    val historyState: StateFlow<CallHistoryUiState> = callsRepository.historyState

    init {
        loadInitialHistory()
    }

    fun loadInitialHistory() {
        viewModelScope.launch {
            callsRepository.loadInitialHistory()
        }
    }

    fun loadNextPageHistory() {
        viewModelScope.launch {
            callsRepository.loadNextPageHistory()
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            callsRepository.refreshHistory()
        }
    }

    fun initiateCall(userId: Long) {
        viewModelScope.launch {
            callsRepository.initiateCall(userId)
        }
    }

    class Factory(
        private val callsRepository: CallsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CallsViewModel(callsRepository) as T
        }
    }
}
