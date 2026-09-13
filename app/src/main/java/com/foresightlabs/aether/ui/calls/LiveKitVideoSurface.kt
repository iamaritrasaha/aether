package com.foresightlabs.aether.ui.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.VideoTrack

/**
 * Renders one LiveKit [VideoTrack] into the call surface.
 *
 * LiveKit ships its own `TextureViewRenderer` (its WebRTC is shaded under
 * `livekit.org.webrtc`, so Aether must not construct org.webrtc renderers
 * itself -- this also satisfies "no second camera pipeline": the track is
 * LiveKit's, we only attach a sink). The renderer is initialized without a
 * shared EGL context in v1 (correct, slightly less efficient); the track
 * binding swaps in `update`, and release happens on disposal.
 *
 * [mirror] flips horizontally -- used for the local front-camera preview.
 * Purely a display transform; the transmitted track is untouched.
 */
@Composable
fun LiveKitVideoSurface(
    trackProvider: () -> VideoTrack?,
    modifier: Modifier = Modifier,
    mirror: Boolean = false
) {
    val currentTrack = remember { androidx.compose.runtime.mutableStateOf<VideoTrack?>(null) }
    AndroidView(
        modifier = modifier.graphicsLayer { scaleX = if (mirror) -1f else 1f },
        factory = { context ->
            TextureViewRenderer(context).apply {
                init(null, null)
            }
        },
        update = { view ->
            val track = trackProvider()
            if (track !== currentTrack.value) {
                currentTrack.value?.removeRenderer(view)
                currentTrack.value = track
                track?.addRenderer(view)
            }
        },
        onRelease = { view ->
            currentTrack.value?.removeRenderer(view)
            currentTrack.value = null
            view.release()
        }
    )
    DisposableEffect(Unit) { onDispose { } }
}
