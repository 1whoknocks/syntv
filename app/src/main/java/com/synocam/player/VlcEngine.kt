package com.synocam.player

import android.content.Context
import com.synocam.BuildConfig
import org.videolan.libvlc.LibVLC

/**
 * Single process-wide LibVLC instance. VideoLAN recommends one LibVLC with many
 * MediaPlayers rather than one LibVLC per stream — important for the camera wall,
 * where several tiles play at once.
 */
object VlcEngine {

    /**
     * RTSP jitter buffer in ms. The Synology live-view proxy (LIVE555) does a digest-auth
     * handshake and then relays the camera; 300ms was too tight and could stall before the
     * first frame arrived. 1500ms trades a little latency for a reliable start on Wi-Fi cameras.
     */
    const val NETWORK_CACHING_MS = 1500

    @Volatile
    private var instance: LibVLC? = null

    fun get(context: Context): LibVLC =
        instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

    private fun build(context: Context): LibVLC {
        val args = arrayListOf(
            "--rtsp-tcp",                 // RTSP over TCP: reliable on Wi-Fi, no UDP packet loss
            "--network-caching=$NETWORK_CACHING_MS",
            "--clock-jitter=0",
            "--clock-synchro=0",
            "--avcodec-skiploopfilter=4", // shave decode load so weak sticks can run a grid
            "--avcodec-fast",
        )
        if (BuildConfig.DEBUG) {
            // Verbose libVLC logging on debug builds. libVLC routes native logs straight to
            // Android logcat under the "VLC" tag, so `adb logcat -s VLC` shows the RTSP
            // handshake (OPTIONS/DESCRIBE/401 digest) and decoder selection — how the
            // "stuck on Connecting" stall was diagnosed.
            args.add("--verbose=2")
        }
        return LibVLC(context.applicationContext, args)
    }
}
