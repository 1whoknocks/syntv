package com.synocam.player

import android.content.Context
import org.videolan.libvlc.LibVLC

/**
 * Single process-wide LibVLC instance. VideoLAN recommends one LibVLC with many
 * MediaPlayers rather than one LibVLC per stream — important for the camera wall,
 * where several tiles play at once.
 */
object VlcEngine {

    @Volatile
    private var instance: LibVLC? = null

    fun get(context: Context): LibVLC =
        instance ?: synchronized(this) {
            instance ?: LibVLC(
                context.applicationContext,
                // NOTE: audio is NOT disabled globally here — that would make unmute
                // impossible. Each tile mutes itself via the `:no-audio` media option
                // (VlcCameraView allowAudio=false); fullscreen toggles volume instead.
                arrayListOf(
                    "--rtsp-tcp",            // RTSP over TCP: reliable on Wi-Fi, no UDP packet loss
                    "--network-caching=300", // low latency buffer (ms)
                    "--clock-jitter=0",
                    "--clock-synchro=0",
                    "--avcodec-skiploopfilter=4", // shave decode load so weak sticks can run a grid
                    "--avcodec-fast",
                ),
            ).also { instance = it }
        }
}
