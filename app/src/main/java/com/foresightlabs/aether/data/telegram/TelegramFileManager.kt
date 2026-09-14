package com.foresightlabs.aether.data.telegram

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/**
 * Account-scoped owner of TDLib file truth.
 *
 * Files embedded in messages are snapshots. Only [update] may replace a file
 * already learned from UpdateFile; [seed] exists so a newly mapped message can
 * introduce an id without rolling a later UpdateFile back to stale data.
 */
class TelegramFileManager(
    private val scope: CoroutineScope,
    private val request: suspend (TdApi.Function<*>) -> TdApi.Object
) {
    sealed interface State {
        val fileId: Int
        val file: TdApi.File?

        data class NotDownloaded(override val fileId: Int, override val file: TdApi.File?) : State
        data class Downloading(override val fileId: Int, override val file: TdApi.File) : State
        data class Downloaded(
            override val fileId: Int,
            override val file: TdApi.File,
            val path: String
        ) : State
        data class Failed(
            override val fileId: Int,
            override val file: TdApi.File?,
            val reason: Failure
        ) : State
    }

    enum class Failure { TD_LIB, COMPLETED_FILE_MISSING }

    private val files = ConcurrentHashMap<Int, TdApi.File>()
    private val updateVersions = ConcurrentHashMap<Int, Long>()
    private val states = ConcurrentHashMap<Int, MutableStateFlow<State>>()
    private val requested = ConcurrentHashMap.newKeySet<Int>()
    private val started = ConcurrentHashMap.newKeySet<Int>()
    private val cancelled = ConcurrentHashMap.newKeySet<Int>()

    fun observe(fileId: Int): StateFlow<State> = stateFlow(fileId).asStateFlow()

    fun get(fileId: Int): State = stateFlow(fileId).value

    fun file(fileId: Int): TdApi.File? = files[fileId]

    fun resolve(snapshot: TdApi.File?): TdApi.File? {
        if (snapshot == null || snapshot.id == 0) return snapshot
        seed(snapshot)
        return files[snapshot.id] ?: snapshot
    }

    fun seed(file: TdApi.File?) {
        if (file == null || file.id == 0) return
        val accepted = files.putIfAbsent(file.id, file) == null
        if (accepted) publish(file)
    }

    /** Called for every independent TDLib UpdateFile. */
    fun update(file: TdApi.File?) {
        if (file == null || file.id == 0) return
        synchronized(updateVersions) {
            updateVersions[file.id] = (updateVersions[file.id] ?: 0L) + 1L
            files[file.id] = file
            publish(file)
        }
    }

    suspend fun refresh(fileId: Int): State {
        if (fileId == 0) return State.NotDownloaded(0, null)
        val version = updateVersions[fileId] ?: 0L
        val result = request(TdApi.GetFile(fileId))
        if (result is TdApi.File) acceptRequestResult(result, version)
        return get(fileId)
    }

    suspend fun download(fileId: Int, priority: Int = DEFAULT_PRIORITY) {
        if (fileId == 0) return
        cancelled.remove(fileId)
        if (!requested.add(fileId)) return
        if (file(fileId) == null) refresh(fileId)
        val current = file(fileId)
        if (current?.local?.isDownloadingCompleted == true) {
            publish(current)
            return
        }
        current?.let { stateFlow(fileId).value = State.Downloading(fileId, it) }
        val version = updateVersions[fileId] ?: 0L
        when (val result = request(TdApi.DownloadFile(fileId, priority, 0, 0, false))) {
            is TdApi.File -> acceptRequestResult(result, version)
            is TdApi.Error -> fail(fileId, Failure.TD_LIB)
        }
    }

    fun downloadAsync(fileId: Int, priority: Int = DEFAULT_PRIORITY) {
        scope.launch { download(fileId, priority) }
    }

    suspend fun cancel(fileId: Int) {
        if (fileId == 0) return
        cancelled.add(fileId)
        requested.remove(fileId)
        request(TdApi.CancelDownloadFile(fileId, false))
        val current = file(fileId)
        stateFlow(fileId).value = State.NotDownloaded(fileId, current)
    }

    suspend fun retry(fileId: Int, priority: Int = RETRY_PRIORITY) {
        requested.remove(fileId)
        stateFlow(fileId).value = State.NotDownloaded(fileId, file(fileId))
        download(fileId, priority)
    }

    /** Forget a deleted/corrupt local snapshot before asking TDLib for it again. */
    fun invalidate(fileId: Int) {
        if (fileId == 0) return
        files.remove(fileId)
        requested.remove(fileId)
        started.remove(fileId)
        stateFlow(fileId).value = State.NotDownloaded(fileId, null)
    }

    /** Full-download policy used by the first file-id Media3 source. */
    suspend fun awaitDownloaded(fileId: Int, priority: Int = PLAYBACK_PRIORITY): State.Downloaded {
        val initial = refresh(fileId)
        if (initial is State.Downloaded) return initial
        download(fileId, priority)
        return when (val terminal = observe(fileId).first { it is State.Downloaded || it is State.Failed }) {
            is State.Downloaded -> terminal
            is State.Failed -> throw TelegramFileException(fileId, terminal.reason)
            else -> error("unreachable")
        }
    }

    fun clear() {
        files.clear()
        updateVersions.clear()
        states.clear()
        requested.clear()
        started.clear()
        cancelled.clear()
    }

    private fun stateFlow(fileId: Int): MutableStateFlow<State> =
        states.getOrPut(fileId) { MutableStateFlow(State.NotDownloaded(fileId, files[fileId])) }

    private fun publish(file: TdApi.File) {
        val local = file.local
        val next = when {
            local.isDownloadingCompleted -> {
                val finalPath = local.path.takeIf { it.isNotBlank() }
                val valid = finalPath?.let { path -> File(path).let { it.isFile && it.length() > 0L } } == true
                if (valid) State.Downloaded(file.id, file, finalPath!!) else {
                    State.Failed(file.id, file, Failure.COMPLETED_FILE_MISSING)
                }
            }
            local.isDownloadingActive -> {
                started.add(file.id)
                State.Downloading(file.id, file)
            }
            file.id in started && file.id !in cancelled -> State.Failed(file.id, file, Failure.TD_LIB)
            file.id in requested -> State.Downloading(file.id, file)
            else -> State.NotDownloaded(file.id, file)
        }
        if (next is State.Downloaded || next is State.Failed) {
            requested.remove(file.id)
            started.remove(file.id)
        }
        stateFlow(file.id).value = next
    }

    private fun fail(fileId: Int, failure: Failure) {
        requested.remove(fileId)
        stateFlow(fileId).value = State.Failed(fileId, file(fileId), failure)
    }

    /** A request response may not roll back an UpdateFile that raced it. */
    private fun acceptRequestResult(file: TdApi.File, versionBeforeRequest: Long) {
        synchronized(updateVersions) {
            if ((updateVersions[file.id] ?: 0L) != versionBeforeRequest) return
            if (files[file.id]?.local?.isDownloadingCompleted == true && !file.local.isDownloadingCompleted) return
            files[file.id] = file
            publish(file)
        }
    }

    companion object {
        const val DEFAULT_PRIORITY = 16
        const val RETRY_PRIORITY = 32
        const val PLAYBACK_PRIORITY = 32
    }
}

class TelegramFileException(fileId: Int, failure: TelegramFileManager.Failure) :
    java.io.IOException("TDLib file $fileId unavailable: $failure")
