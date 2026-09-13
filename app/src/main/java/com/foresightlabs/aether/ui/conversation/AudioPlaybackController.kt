package com.foresightlabs.aether.ui.conversation

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
class AudioPlaybackController(private val context: Context) {

    /** Playback state for the one active note; null when nothing is active. */
    data class Playback(
        val key: String,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        /** ExoPlayer speed, surfaced so the note's speed control reflects reality. */
        val speed: Float = 1f
    )

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
     * the download completion re-map supplies the path.
     */
    fun toggle(key: String, filePath: String?, requestDownload: (() -> Unit)?) {
        val path = filePath?.takeIf { it.isNotBlank() && File(it).exists() && File(it).length() > 0L }
        if (path == null) {
            if (requestDownload != null) {
                _pendingDownloadKey.value = key
                requestDownload()
            }
            return
        }

        val current = _playback.value
        if (current?.key == key) {
            val p = player ?: return
            if (p.isPlaying) p.pause() else p.play()
            return
        }
        start(key, path)
    }

    /** Called by the bubble when a download completes and [filePath] appears. */
    fun onPathArrived(key: String, filePath: String?) {
        if (_pendingDownloadKey.value != key) return
        _pendingDownloadKey.value = null
        val path = filePath?.takeIf { it.isNotBlank() && File(it).exists() && File(it).length() > 0L } ?: return
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

    private fun start(key: String, path: String) {
        releasePlayer()
        activeKey = key
        val p = ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        publish(ended = true)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    publishNow()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    // A file that exists but cannot be decoded (truncated
                    // download, unsupported codec) must not leave the bubble
                    // in limbo: return to the rest state so the control is
                    // tappable again instead of frozen mid-"playing".
                    if (com.foresightlabs.aether.BuildConfig.DEBUG) {
                        android.util.Log.w("AetherTd", "AUDIO_PLAYBACK_ERROR code=${error.errorCode}")
                    }
                    player?.seekTo(0)
                    player?.pause()
                    publishNow()
                }
            })
            prepare()
            play()
        }
        player = p
        publishNow()
        startTicker()
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
        _playback.value = Playback(
            key = key,
            isPlaying = if (ended) false else p.isPlaying,
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
     * (or simply surprise the user by playing on some later re-map).
     */
    fun onDownloadFailed(key: String) {
        if (_pendingDownloadKey.value == key) {
            _pendingDownloadKey.value = null
        }
    }

    private companion object {
        const val TICK_MS = 100L
    }
}
