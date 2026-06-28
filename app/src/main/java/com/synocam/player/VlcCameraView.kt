package com.synocam.player

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

enum class PlayerState { Idle, Buffering, Playing, Error, Ended }

/**
 * Renders one RTSP stream with libVLC. Each instance owns a MediaPlayer (cheap) but
 * shares the process-wide LibVLC from [VlcEngine]. The player is fully torn down when
 * the composable leaves composition, which is what makes drilling in/out of the wall
 * release decoder resources.
 *
 * Slow-start handling: a stream that does not reach [PlayerState.Playing] within
 * [connectTimeoutMs] is retried (up to [maxRetries] times) and then reported as
 * [PlayerState.Error] — so a dead camera or an undecodable stream shows a real error
 * instead of an infinite "Connecting…".
 */
@Composable
fun VlcCameraView(
    rtspUrl: String?,
    modifier: Modifier = Modifier,
    allowAudio: Boolean = false,
    muted: Boolean = true,
    connectTimeoutMs: Long = 12_000L,
    maxRetries: Int = 2,
    onState: (PlayerState) -> Unit = {},
) {
    val context = LocalContext.current
    val libVlc = remember { VlcEngine.get(context) }
    val mediaPlayer = remember { MediaPlayer(libVlc) }
    val videoLayout = remember { VLCVideoLayout(context) }
    val stateCallback = rememberUpdatedState(onState)
    val mutedState = rememberUpdatedState(muted)
    val audioState = rememberUpdatedState(allowAudio)

    // Bumping [attempt] re-runs the play effect (a retry). Reset to 0 when the url changes.
    var attempt by remember(rtspUrl) { mutableStateOf(0) }
    // Cross-checked by the watchdog. Mutable holders so the event listener can update them
    // without re-subscribing.
    val reachedPlaying = remember { mutableStateOf(false) }
    val encounteredError = remember { mutableStateOf(false) }

    // Apply mute state to a player that is already running (live toggle from the remote).
    LaunchedEffect(muted, allowAudio) {
        if (allowAudio) mediaPlayer.volume = if (muted) 0 else 100
    }

    DisposableEffect(Unit) {
        mediaPlayer.attachViews(videoLayout, null, false, false)
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    reachedPlaying.value = true
                    encounteredError.value = false
                    // Volume only takes effect once output exists, so (re)apply it here.
                    if (audioState.value) mediaPlayer.volume = if (mutedState.value) 0 else 100
                    stateCallback.value(PlayerState.Playing)
                }
                MediaPlayer.Event.Buffering ->
                    if (event.buffering < 100f && !reachedPlaying.value) {
                        stateCallback.value(PlayerState.Buffering)
                    }
                MediaPlayer.Event.EncounteredError -> encounteredError.value = true
                MediaPlayer.Event.EndReached -> encounteredError.value = true
            }
        }
        onDispose {
            mediaPlayer.setEventListener(null)
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
        }
    }

    LaunchedEffect(rtspUrl, attempt) {
        val url = rtspUrl
        if (url.isNullOrBlank()) {
            stateCallback.value(PlayerState.Error)
            return@LaunchedEffect
        }
        reachedPlaying.value = false
        encounteredError.value = false
        stateCallback.value(PlayerState.Buffering)

        val media = Media(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)            // HW decode, allow SW fallback
            addOption(":network-caching=${VlcEngine.NETWORK_CACHING_MS}")
            addOption(":rtsp-tcp")                       // keep TCP at the media level too
            addOption(":rtsp-frame-buffer-size=1000000") // headroom for high-bitrate HEVC keyframes
            if (!allowAudio) addOption(":no-audio")      // wall tiles: silent + skip audio decode
        }
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()

        // Watchdog: wait for Playing; bail out early on a hard error.
        var waited = 0L
        val step = 250L
        while (waited < connectTimeoutMs && !reachedPlaying.value && !encounteredError.value) {
            delay(step)
            waited += step
        }
        if (reachedPlaying.value) return@LaunchedEffect

        if (attempt < maxRetries) {
            mediaPlayer.stop()
            attempt++ // recompose -> this effect re-runs for a fresh attempt
        } else {
            stateCallback.value(PlayerState.Error)
        }
    }

    AndroidView(factory = { videoLayout }, modifier = modifier)
}
