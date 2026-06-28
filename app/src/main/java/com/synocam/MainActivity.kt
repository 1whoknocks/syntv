package com.synocam

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.synocam.player.VlcCameraView
import com.synocam.ui.AppViewModel
import com.synocam.ui.CameraWallScreen
import com.synocam.ui.FullscreenScreen
import com.synocam.ui.Screen
import com.synocam.ui.SetupScreen
import com.synocam.ui.SynoCamTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val supportsPip: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SynoCamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val state = vm.state

                    // In PiP the window is tiny: show only the single chosen camera, no chrome.
                    if (state.inPipMode) {
                        val stream = state.cameras.getOrNull(state.currentIndex)
                        VlcCameraView(rtspUrl = stream?.rtspUrl, modifier = Modifier.fillMaxSize())
                        return@Surface
                    }

                    when (val screen = state.screen) {
                        is Screen.Setup -> SetupScreen(
                            initial = vm.savedConfig(),
                            loading = state.loading,
                            error = state.error,
                            onConnect = { vm.connect(it) },
                        )

                        is Screen.Wall -> CameraWallScreen(
                            cameras = state.cameras,
                            columns = state.gridColumns,
                            onOpenCamera = { vm.openFullscreen(it) },
                            onOpenSettings = { vm.openSetup() },
                        )

                        is Screen.Fullscreen -> FullscreenScreen(
                            cameras = state.cameras,
                            startIndex = screen.index,
                            onBack = { vm.openWall() },
                            onIndexChange = { vm.setCurrentIndex(it) },
                            onEnterPip = { enterPip() },
                        )
                    }
                }
            }
        }
    }

    /** Pop the currently-open camera into a corner PiP window. */
    private fun enterPip() {
        if (!supportsPip) {
            Toast.makeText(this, "This Fire TV doesn't support picture-in-picture.", Toast.LENGTH_LONG).show()
            return
        }
        enterPipApi26()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPipApi26() {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        runCatching { enterPictureInPictureMode(params) }
            .onFailure { Toast.makeText(this, "Couldn't start picture-in-picture.", Toast.LENGTH_SHORT).show() }
    }

    /** Pressing Home (or switching apps) while a camera is open auto-pops it into PiP. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (supportsPip && vm.cameraIsOpen() && !vm.state.inPipMode) {
            enterPipApi26()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        vm.setPipMode(isInPictureInPictureMode)
    }
}
