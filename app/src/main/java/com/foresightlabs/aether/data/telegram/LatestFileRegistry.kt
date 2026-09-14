package com.foresightlabs.aether.data.telegram

import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

/**
 * The latest TDLib [TdApi.File] snapshot for each file id.
 *
 * A file embedded in a message is only a snapshot. TDLib sends later
 * [TdApi.UpdateFile] objects when the local path, transfer flags, or byte
 * counters change. Message mapping must therefore resolve through this table
 * instead of trusting the object that happened to arrive with the message.
 */
class LatestFileRegistry {
    private val files = ConcurrentHashMap<Int, TdApi.File>()

    /** Seeds a file id once from a message without allowing an old message to
     * overwrite a newer UpdateFile snapshot. */
    fun seed(file: TdApi.File?) {
        if (file == null || file.id == 0) return
        files.putIfAbsent(file.id, file)
    }

    /** Stores the authoritative snapshot delivered by TDLib's UpdateFile. */
    fun update(file: TdApi.File?) {
        if (file == null || file.id == 0) return
        files[file.id] = file
    }

    fun get(fileId: Int): TdApi.File? = files[fileId]

    fun resolve(file: TdApi.File?): TdApi.File? {
        if (file == null) return null
        seed(file)
        return files[file.id] ?: file
    }

    fun clear() = files.clear()
}
