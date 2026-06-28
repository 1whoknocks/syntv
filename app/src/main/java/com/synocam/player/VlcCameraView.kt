package com.synocam.player

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

enum class PlayerState { Idle, Buffering, Playing, Error, Ended }

/**
 * Renders one RTSP stream with libVLC. Each instance owns a MediaPlayer (cheap) but
 * shares the process-wide LibVLC from [VlcEngine]. The player is fully torn down when
 * the composable leaves composition, which is what makes drilling in/out of the wall
 * release decoder resources.
 */
@Composable
fun VlcCameraView(
    rtspUrl: String?,
    modifier: Modifier = Modifier,
    allowAudio: Boolean = false,
    muted: Boolean = true,
    onState: (PlayerState) -> Unit = {},
) {
    val context = LocalContext.current
    val libVlc = remember { VlcEngine.get(context) }
    val mediaPlayer = remember { MediaPlayer(libVlc) }
    val videoLayout = remember { VLCVideoLayout(context) }
    val stateCallback = rememberUpdatedState(onState)
    val mutedState = rememberUpdatedState(muted)
    val audioState = rememberUpdatedState(allowAudio)

    // Apply mute state to a player that is already running (live toggle from the remote).
    LaunchedEffect(muted, allowAudio) {
        if (allowAudio) mediaPlayer.volume = if (muted) 0 else 100
    }

    DisposableEffect(Unit) {
        mediaPlayer.attachViews(videoLayout, null, false, false)
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    // Volume only takes effect once output exists, so (re)apply it here.
                    if (audioState.value) mediaPlayer.volume = if (mutedState.value) 0 else 100
                    stateCallback.value(PlayerState.Playing)
                }
                MediaPlayer.Event.Buffering ->
                    if (event.buffering < 100f) stateCallback.value(PlayerState.Buffering)
                MediaPlayer.Event.EncounteredError -> stateCallback.value(PlayerState.Error)
                MediaPlayer.Event.EndReached -> stateCallback.value(PlayerState.Ended)
            }
        }
        onDispose {
            mediaPlayer.setEventListener(null)
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
        }
    }

    LaunchedEffect(rtspUrl) {
        val url = rtspUrl
        if (url.isNullOrBlank()) {
            stateCallback.value(PlayerState.Error)
            return@LaunchedEffect
        }
        stateCallback.value(PlayerState.Buffering)
        val media = Media(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=300")
            addOption(":rtsp-tcp")
            if (!allowAudio) addOption(":no-audio") // wall tiles: silent + skip audio decode
        }
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    AndroidView(factory = { videoLayout }, modifier = modifier)
}
