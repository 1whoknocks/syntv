package com.synocam.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.synocam.player.PlayerState
import com.synocam.player.VlcCameraView

/**
 * One camera, full screen. Left/Right switch cameras; Back returns to the wall.
 * The [index] is keyed into VlcCameraView so switching tears down and rebuilds the
 * player, releasing the previous decoder.
 */
@Composable
fun FullscreenScreen(
    cameras: List<CameraStream>,
    startIndex: Int,
    onBack: () -> Unit,
    onIndexChange: (Int) -> Unit = {},
    onEnterPip: () -> Unit = {},
) {
    if (cameras.isEmpty()) {
        onBack()
        return
    }
    var index by remember { mutableStateOf(startIndex.coerceIn(0, cameras.lastIndex)) }
    var playerState by remember { mutableStateOf(PlayerState.Idle) }
    var muted by remember { mutableStateOf(true) } // everything starts muted
    val focusRequester = remember { FocusRequester() }
    val stream = cameras[index]

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    // Keep the ViewModel's "current camera" in sync so PiP-on-leave pops the right one.
    LaunchedEffect(index) { onIndexChange(index) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> { index = (index + 1) % cameras.size; true }
                    Key.DirectionLeft -> { index = (index - 1 + cameras.size) % cameras.size; true }
                    Key.DirectionCenter, Key.Enter -> { muted = !muted; true }
                    Key.Menu, Key.MediaPlayPause -> { onEnterPip(); true }
                    else -> false
                }
            },
    ) {
        VlcCameraView(
            rtspUrl = stream.rtspUrl,
            modifier = Modifier.fillMaxSize(),
            allowAudio = true,
            muted = muted,
            onState = { playerState = it },
        )

        if (stream.rtspUrl == null || playerState == PlayerState.Error) {
            Text("No stream available", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
        } else if (playerState == PlayerState.Buffering || playerState == PlayerState.Idle) {
            Text("Connecting…", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        Text(
            text = "${if (muted) "🔇" else "🔊"} ${stream.camera.displayName}   ·   ${index + 1}/${cameras.size}   ·   ◀ ▶ switch · OK = mute/unmute · Menu = pop out (PiP) · Back to grid",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
