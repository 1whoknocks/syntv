package com.synocam.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.synocam.api.Camera
import com.synocam.api.SurveillanceClient
import com.synocam.data.NasConfig
import com.synocam.data.Settings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface Screen {
    data object Setup : Screen
    data object Wall : Screen
    data class Fullscreen(val index: Int) : Screen
}

data class CameraStream(val camera: Camera, val rtspUrl: String?)

data class UiState(
    val screen: Screen = Screen.Setup,
    val cameras: List<CameraStream> = emptyList(),
    val gridColumns: Int = 2,
    val loading: Boolean = false,
    val error: String? = null,
    /** Camera last viewed fullscreen; the one that pops into PiP when you leave the app. */
    val currentIndex: Int = 0,
    val inPipMode: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)

    var state by mutableStateOf(UiState())
        private set

    init {
        val saved = settings.load()
        if (saved != null) {
            state = state.copy(gridColumns = saved.gridColumns)
            connect(saved, persist = false)
        }
    }

    fun savedConfig(): NasConfig? = settings.load()

    fun connect(config: NasConfig, persist: Boolean = true) {
        state = state.copy(loading = true, error = null, gridColumns = config.gridColumns)
        viewModelScope.launch {
            try {
                val client = SurveillanceClient(config)
                client.login()
                val cameras = client.listCameras().filter { it.enabled }
                if (cameras.isEmpty()) {
                    state = state.copy(loading = false, error = "Connected, but no cameras were found.")
                    return@launch
                }
                val streams = coroutineScope {
                    cameras.map { cam ->
                        async {
                            // Per-camera cap so a single slow/unresponsive GetLiveViewPath cannot
                            // stall the whole wall load. A null url just renders as "No stream".
                            val url = runCatching {
                                withTimeoutOrNull(PATH_FETCH_TIMEOUT_MS) { client.liveViewRtsp(cam.id) }
                            }.getOrNull()
                            CameraStream(cam, url)
                        }
                    }.awaitAll()
                }
                if (persist) settings.save(config)
                state = state.copy(
                    screen = Screen.Wall,
                    cameras = streams,
                    gridColumns = config.gridColumns,
                    loading = false,
                    error = null,
                )
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "Connection failed.")
            }
        }
    }

    fun retry() {
        savedConfig()?.let { connect(it, persist = false) }
    }

    fun openSetup() { state = state.copy(screen = Screen.Setup) }
    fun openWall() { state = state.copy(screen = Screen.Wall) }
    fun openFullscreen(index: Int) { state = state.copy(screen = Screen.Fullscreen(index), currentIndex = index) }

    /** FullscreenScreen reports the camera currently shown so PiP-on-leave knows which one. */
    fun setCurrentIndex(index: Int) {
        if (index != state.currentIndex) state = state.copy(currentIndex = index)
    }

    fun setPipMode(on: Boolean) {
        if (on != state.inPipMode) state = state.copy(inPipMode = on)
    }

    /** True only when a single camera is open (Fullscreen) — the case where leaving should pop PiP. */
    fun cameraIsOpen(): Boolean = state.screen is Screen.Fullscreen && state.cameras.isNotEmpty()

    private companion object {
        const val PATH_FETCH_TIMEOUT_MS = 20_000L
    }
}
