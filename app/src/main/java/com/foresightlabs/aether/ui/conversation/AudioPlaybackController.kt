package com.foresightlabs.aether.ui.conversation

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.foresightlabs.aether.data.telegram.TelegramDataSource
import com.foresightlabs.aether.data.telegram.TelegramFileManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one audio playback spot for a conversation screen.
 *
 * Voice notes and audio messages used to have no player at all -- the bubble's
 * play button animated a timer in silence. This controller owns a single
 * [ExoPlayer] so at most one note plays at a time, survives bubble
 * recomposition (list scrolling must not restart playback), and reports real
 * position/duration so progress is the audio's truth, not an animation.
 *
 * A screen-scoped instance releases the player when the conversation leaves
 * composition; audio focus is handled by ExoPlayer itself via
 * [handleAudioFocus][ExoPlayer.setAudioAttributes].
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioPlaybackController(
    private val context: Context,
    private val telegramFiles: TelegramFileManager? = null
) {

    /** Where the one active note is. No [Playback] at all means idle. */
    enum class Status { DOWNLOADING, PLAYING, PAUSED, FAILED }

    /** Playback state for the one active note; null when nothing is active. */
    data class Playback(
        val key: String,
        val status: Status,
        val positionMs: Long,
        val durationMs: Long,
        /** ExoPlayer speed, surfaced so the note's speed control reflects reality. */
        val speed: Float = 1f,
        /** The [PlaybackException.errorCode] behind [Status.FAILED], when the player raised one. */
        val errorCode: Int? = null
    ) {
        val isPlaying: Boolean get() = status == Status.PLAYING
        val isFailed: Boolean get() = status == Status.FAILED
        val isDownloading: Boolean get() = status == Status.DOWNLOADING

        /**
         * The bytes themselves would not play (unreadable container, codec
         * failure, the file gone from disk) -- retrying the same copy cannot
         * help; fetching it again can.
         */
        val needsFreshCopy: Boolean
            get() = errorCode != null && (
                errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                    errorCode in PARSING_AND_DECODING_ERRORS
                )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: ExoPlayer? = null
    private var ticker: Job? = null

    /**
     * The key of the active note, held separately from the published state:
     * the state must be derivable from the player alone (publish() runs when
     * the state is still null, i.e. exactly when the FIRST state is being
     * established -- deriving the key from the state there would deadlock it
     * at null forever and the UI would never leave the rest state).
     */
    private var activeKey: String? = null

    /**
     * The file the player was built on. A tap on the same note with a
     * different path -- the download finished and TDLib moved the bytes from
     * its temporary part to the final file -- must rebuild the player; resuming
     * the old item would reopen a path that no longer exists.
     */
    private var activePath: String? = null
    private var activeTelegramFileId: Int? = null
    private var activeClip: LongRange? = null

    /** The file the current player was built on; exposed so tests can see a rebuild. */
    @androidx.annotation.VisibleForTesting
    internal val activeFilePath: String? get() = activePath.takeIf { player != null }

    @androidx.annotation.VisibleForTesting
    internal val activeFileId: Int? get() = activeTelegramFileId.takeIf { player != null }

    private val _playback = MutableStateFlow<Playback?>(null)

    /** The active note's playback state, or null. */
    val playback: StateFlow<Playback?> = _playback.asStateFlow()

    /**
     * The key of a note whose download was requested from its play button, so
     * the note can start playing the moment the download completes (the path
     * arrives as a prop; the bubble calls [onPathArrived]).
     */
    private val _pendingDownloadKey = MutableStateFlow<String?>(null)
    val pendingDownloadKey: StateFlow<String?> = _pendingDownloadKey.asStateFlow()

    /** Whether [key] is the currently active (possibly paused) note. */
    fun isActive(key: String): Boolean = _playback.value?.key == key

    /**
     * Play/pause toggle for [key]. A note without local bytes routes to
     * [requestDownload] instead -- the bubble calls back with the file id and
     * the download completion re-map supplies the path. A note that failed
     * because its bytes would not play routes to [requestFreshCopy] when one
     * is given, so the retry fetches the file again instead of failing on the
     * same copy.
     */
    fun toggle(key: String, filePath: String?, requestDownload: (() -> Unit)?) {
        togglePlayback(key, filePath, requestDownload, requestFreshCopy = null, clipMs = null)
    }

    /** [toggle], plus the way to fetch a fresh copy when the bytes on disk would not play. */
    fun toggle(
        key: String,
        filePath: String?,
        requestDownload: (() -> Unit)?,
        requestFreshCopy: (() -> Unit)?
    ) {
        togglePlayback(key, filePath, requestDownload, requestFreshCopy, clipMs = null)
    }

    /**
     * Play/pause for one stretch of a local file -- a voice note being reviewed
     * and trimmed. Reported positions are then relative to the stretch's start.
     */
    fun toggleClip(key: String, filePath: String, clipMs: LongRange) {
        togglePlayback(key, filePath, requestDownload = null, requestFreshCopy = null, clipMs = clipMs)
    }

    /** Plays Telegram media by stable TDLib file id through [TelegramDataSource]. */
    fun toggleTelegram(key: String, fileId: Int, requestFreshCopy: (() -> Unit)? = null) {
        val manager = telegramFiles ?: run {
            fail(key, errorCode = null)
            return
        }
        val current = _playback.value?.takeIf { it.key == key }
        val p = player
        if (current?.isFailed == true && requestFreshCopy != null) requestFreshCopy()
        if (current != null && p != null && !current.isFailed && activeTelegramFileId == fileId) {
            if (p.isPlaying) p.pause() else {
                if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
                p.play()
            }
            return
        }
        startTelegram(key, fileId, manager)
    }

    private fun togglePlayback(
        key: String,
        filePath: String?,
        requestDownload: (() -> Unit)?,
        requestFreshCopy: (() -> Unit)?,
        clipMs: LongRange?
    ) {
        val path = filePath?.takeIf(::isPlayable)
        val current = _playback.value?.takeIf { it.key == key }
        log("AUDIO_TOGGLE key=$key localPathPresent=${path != null} ${describe(filePath)} status=${current?.status}")

        if (current?.isFailed == true && current.needsFreshCopy && path == activePath && requestFreshCopy != null) {
            log("AUDIO_REDOWNLOAD key=$key errorCode=${current.errorCode}")
            awaitDownload(key)
            requestFreshCopy()
            return
        }
        if (path == null) {
            if (requestDownload != null) {
                log("AUDIO_DOWNLOAD_REQUEST key=$key")
                awaitDownload(key)
                requestDownload()
            }
            return
        }
        val p = player
        if (current != null && p != null && !current.isFailed && path == activePath && clipMs == activeClip) {
            if (p.isPlaying) {
                p.pause()
            } else {
                if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
                p.play()
            }
            return
        }
        // A new note, a path that changed since the player was built, or a
        // retry after a failure: all start from a fresh player.
        start(key, path, clipMs)
    }

    /** Called by the bubble when a download completes and [filePath] appears. */
    fun onPathArrived(key: String, filePath: String?) {
        if (_pendingDownloadKey.value != key) return
        _pendingDownloadKey.value = null
        val path = filePath?.takeIf(::isPlayable)
        log("AUDIO_PATH_ARRIVED key=$key localPathPresent=${path != null} ${describe(filePath)}")
        if (path == null) {
            fail(key, errorCode = null)
            return
        }
        start(key, path)
    }

    fun seekToFraction(key: String, fraction: Float) {
        val p = player ?: return
        if (_playback.value?.key != key) return
        val duration = p.duration.takeIf { it > 0 } ?: return
        p.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        publishNow()
    }

    fun setSpeed(key: String, speed: Float) {
        val p = player ?: return
        if (_playback.value?.key != key) return
        p.setPlaybackSpeed(speed)
        publishNow()
    }

    /** Stops and releases the active note when its key starts with [keyPrefix]. */
    fun stop(keyPrefix: String) {
        if (activeKey?.startsWith(keyPrefix) == true) releasePlayer()
    }

    private fun awaitDownload(key: String) {
        releasePlayer()
        _pendingDownloadKey.value = key
        _playback.value = Playback(key, Status.DOWNLOADING, positionMs = 0L, durationMs = 0L)
    }

    private fun start(key: String, path: String, clipMs: LongRange? = null) {
        releasePlayer()
        activeKey = key
        activePath = path
        activeTelegramFileId = null
        activeClip = clipMs
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(File(path)))
            .apply {
                if (clipMs != null) {
                    setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clipMs.first)
                            .setEndPositionMs(clipMs.last)
                            .build()
                    )
                }
            }
            .build()
        log("AUDIO_PREPARE key=$key ${describe(path)} clipped=${clipMs != null}")
        val p = buildPlayer().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
            setMediaItem(item)
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    log("AUDIO_PLAYER_STATE key=$key state=${stateName(playbackState)}")
                    if (playbackState == Player.STATE_ENDED) {
                        publish(ended = true)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    publishNow()
                }

                override fun onPlayerError(error: PlaybackException) {
                    handlePlayerError(key, error.errorCode, error.errorCodeName, error.cause?.javaClass?.simpleName)
                }
            })
            prepare()
            play()
        }
        player = p
        publishNow()
        startTicker()
    }

    private fun startTelegram(key: String, fileId: Int, manager: TelegramFileManager) {
        releasePlayer()
        activeKey = key
        activeTelegramFileId = fileId
        _playback.value = Playback(key, Status.DOWNLOADING, 0L, 0L)
        val item = MediaItem.fromUri(TelegramDataSource.uri(fileId))
        log("AUDIO_PREPARE key=$key fileId=$fileId source=tdlib")
        val mediaSourceFactory = DefaultMediaSourceFactory(TelegramDataSource.Factory(manager))
        val p = ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true
            )
            setMediaItem(item)
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(playerListener(key, fileId))
            prepare()
            play()
        }
        player = p
        startTicker()
    }

    private fun buildPlayer(): ExoPlayer = ExoPlayer.Builder(context).build()

    private fun playerListener(key: String, fileId: Int? = null) = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            log("AUDIO_PLAYER_STATE key=$key fileId=${fileId ?: 0} state=${stateName(playbackState)}")
            if (playbackState == Player.STATE_ENDED) publish(ended = true)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishNow()
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlayerError(key, error.errorCode, error.errorCodeName, error.cause?.javaClass?.simpleName)
        }
    }

    /**
     * Never a silent dead end: say which note failed and why, hand the control
     * back (Retry), and drop the player so the next tap starts clean.
     */
    @androidx.annotation.VisibleForTesting
    internal fun handlePlayerError(key: String, errorCode: Int, errorCodeName: String, causeName: String?) {
        log("AUDIO_PLAYER_ERROR key=$key code=$errorCodeName ($errorCode) cause=$causeName ${describe(activePath)}")
        fail(key, errorCode)
    }

    private fun fail(key: String, errorCode: Int?) {
        val failedPath = activePath.takeIf { activeKey == key }
        releasePlayer()
        // Remembered so a retry on the same bytes can ask for a fresh copy.
        activePath = failedPath
        _playback.value = Playback(key, Status.FAILED, positionMs = 0L, durationMs = 0L, errorCode = errorCode)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                if (player?.isPlaying == true) publishNow()
                delay(TICK_MS)
            }
        }
    }

    private fun publishNow() {
        publish(ended = false)
    }

    private fun publish(ended: Boolean) {
        val p = player ?: return
        val key = activeKey ?: return
        if (ended) {
            p.seekTo(0)
            p.pause()
        }
        val playing = !ended && p.isPlaying
        _playback.value = Playback(
            key = key,
            status = if (playing) Status.PLAYING else Status.PAUSED,
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = p.duration.takeIf { it > 0 } ?: 0L,
            speed = p.playbackParameters.speed
        )
    }

    private fun releasePlayer() {
        ticker?.cancel()
        player?.release()
        player = null
        activeKey = null
        activePath = null
        activeTelegramFileId = null
        activeClip = null
        _playback.value = null
    }

    /** Releases the player; the controller instance is dead after this. */
    fun release() {
        releasePlayer()
        scope.cancel()
        _pendingDownloadKey.value = null
    }

    /**
     * A pending autoplay whose download FAILED must not stay armed: without
     * this, the key would fire the moment that note ever gained a local file
     * (or simply surprise the user by playing on some later re-map). The note
     * shows as failed, so its control reads as Retry instead of doing nothing.
     */
    fun onDownloadFailed(key: String) {
        if (_pendingDownloadKey.value == key) {
            _pendingDownloadKey.value = null
            log("AUDIO_DOWNLOAD_FAILED key=$key")
            fail(key, errorCode = null)
        }
    }

    private fun isPlayable(path: String): Boolean =
        path.isNotBlank() && File(path).let { it.exists() && it.length() > 0L }

    private companion object {
        const val TICK_MS = 100L

        val PARSING_AND_DECODING_ERRORS = 3000..4999

        /**
         * Safe metadata only: whether bytes exist, how many, and the container
         * extension. Never the path itself -- it carries the account's media.
         */
        fun describe(path: String?): String {
            if (path.isNullOrBlank()) return "file=absent"
            val file = File(path)
            val extension = path.substringAfterLast('.', "").lowercase().take(5).ifBlank { "none" }
            return "fileExists=${file.exists()} fileSize=${if (file.exists()) file.length() else -1} ext=$extension"
        }

        fun stateName(state: Int): String = when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> state.toString()
        }

        fun log(message: String) {
            if (com.foresightlabs.aether.BuildConfig.DEBUG) android.util.Log.d("AetherTd", message)
        }
    }
}
