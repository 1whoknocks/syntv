package com.synocam.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.synocam.player.PlayerState
import com.synocam.player.VlcCameraView

@Composable
fun CameraWallScreen(
    cameras: List<CameraStream>,
    columns: Int,
    onOpenCamera: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceIn(1, 4)),
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(cameras, key = { _, item -> item.camera.id }) { index, stream ->
                CameraTile(
                    stream = stream,
                    requestInitialFocus = index == 0,
                    onClick = { onOpenCamera(index) },
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onOpenSettings) { Text("Settings") }
        }
    }
}

@Composable
private fun CameraTile(
    stream: CameraStream,
    requestInitialFocus: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var playerState by remember { mutableStateOf(PlayerState.Idle) }
    val borderColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "tileBorder",
    )
    val focusRequester = remember { FocusRequester() }

    if (requestInitialFocus) {
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .border(if (focused) 4.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        if (stream.rtspUrl != null) {
            VlcCameraView(
                rtspUrl = stream.rtspUrl,
                modifier = Modifier.fillMaxSize(),
                onState = { playerState = it },
            )
        }

        if (stream.rtspUrl == null || playerState == PlayerState.Error) {
            Text(
                "No stream",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (playerState == PlayerState.Buffering || playerState == PlayerState.Idle) {
            Text(
                "Connecting…",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Text(
            text = stream.camera.displayName,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
