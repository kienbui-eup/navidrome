package me.troly.nhac.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.decent.usbaudio.media3.UsbAudioSink

/**
 * Foreground playback service backed by Media3/ExoPlayer with bit-perfect USB
 * output. A MediaLibrarySession exposes the engine to the UI, Android TV (X96
 * box) and, later, Android Auto.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()

        val renderers = BitPerfectRenderersFactory(this)

        // Stop ExoPlayer from reading the source while decent-player's native FLAC
        // engine is decoding + driving USB directly (avoids I/O contention).
        val loadControl = UsbAudioSink.wrapLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(5000, 15000, 2000, 3000)
                .build(),
        ) { renderers.currentUsbSink?.isNativeEngineActive == true }

        // Định cấu hình Audio Attributes tối ưu cho Âm nhạc chất lượng cao
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this, renderers)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Registers an internal Player.Listener that manages the USB stream and
        // native engine lifecycle automatically across track transitions.
        renderers.currentUsbSink?.attachToPlayer(player)

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** Minimal browse tree; flesh out with Subsonic albums/artists/playlists. */
    private class LibraryCallback : MediaLibrarySession.Callback
}
