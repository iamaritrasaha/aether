package com.foresightlabs.aether.data.telegram

import java.util.concurrent.ConcurrentHashMap

data class MessageMediaReference(val chatId: Long, val messageId: Long)

/** Thread-safe reverse index from TDLib file ids to messages that display them. */
class MediaReferenceIndex {
    private val filesByMessage = ConcurrentHashMap<MessageMediaReference, Set<Int>>()
    private val messagesByFile = ConcurrentHashMap<Int, MutableSet<MessageMediaReference>>()

    fun replace(reference: MessageMediaReference, fileIds: Set<Int>) {
        val previous = filesByMessage.put(reference, fileIds)
        previous.orEmpty().forEach { fileId ->
            messagesByFile[fileId]?.let { refs ->
                refs.remove(reference)
                if (refs.isEmpty()) messagesByFile.remove(fileId, refs)
            }
        }
        fileIds.forEach { fileId ->
            messagesByFile.computeIfAbsent(fileId) { ConcurrentHashMap.newKeySet() }.add(reference)
        }
    }

    fun remove(reference: MessageMediaReference) {
        replace(reference, emptySet())
        filesByMessage.remove(reference)
    }

    fun referencesFor(fileId: Int): Set<MessageMediaReference> =
        messagesByFile[fileId]?.toSet().orEmpty()

    fun clear() {
        filesByMessage.clear()
        messagesByFile.clear()
    }
}
