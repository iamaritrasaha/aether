package com.foresightlabs.aether.ui.conversation

import com.foresightlabs.aether.domain.model.Message

/** Resolves a selection against the conversation's chronological message order. */
fun orderedMessagesForForwarding(
    conversationMessages: List<Message>,
    selectedIds: Set<String>
): List<Message> = conversationMessages.filter { it.id in selectedIds }
