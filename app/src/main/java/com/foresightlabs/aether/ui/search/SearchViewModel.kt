package com.foresightlabs.aether.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foresightlabs.aether.AetherApplication
import com.foresightlabs.aether.domain.model.Chat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val telegram = (application as AetherApplication).telegram

    private val _results = MutableStateFlow<List<Chat>>(emptyList())
    val results: StateFlow<List<Chat>> = _results.asStateFlow()

    private var job: Job? = null

    fun query(text: String) {
        job?.cancel()
        if (text.isBlank()) {
            _results.value = emptyList()
            return
        }
        job = viewModelScope.launch {
            delay(200)
            _results.value = telegram.searchChats(text)
        }
    }
}
