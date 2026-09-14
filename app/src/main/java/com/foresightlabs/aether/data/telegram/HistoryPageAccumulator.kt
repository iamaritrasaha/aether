package com.foresightlabs.aether.data.telegram

/**
 * Collects one stable-id history page while TDLib is allowed to repeat the
 * boundary message at the start of a request. The caller owns the TDLib
 * request; this class only defines the ordering and progress rules.
 */
internal class HistoryPageAccumulator<T>(
    private val idOf: (T) -> Long
) {
    private val entries = LinkedHashMap<Long, T>()

    val size: Int get() = entries.size
    val values: List<T> get() = entries.values.toList()

    fun merge(batch: Iterable<T>, requestBoundary: Long): HistoryMergeResult {
        var newUniqueCount = 0
        var oldestId = 0L
        batch.forEach { item ->
            val id = idOf(item)
            // GetChatHistory(offset=0) starts at the boundary, so this overlap
            // is not progress and must not move the cursor forever.
            if (id == requestBoundary) return@forEach
            val isNew = entries.put(id, item) == null
            if (isNew) {
                newUniqueCount++
                if (oldestId == 0L || id < oldestId) oldestId = id
            }
        }
        return HistoryMergeResult(newUniqueCount, oldestId)
    }
}

internal data class HistoryMergeResult(
    val newUniqueCount: Int,
    val oldestId: Long
)
