package me.troly.nhac.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.decent.usbaudio.media3.UsbAudioSink

/**
 * ExoPlayer renderers factory that outputs bit-perfect audio to a USB DAC via
 * decent-player's [UsbAudioSink]. Wiring follows decent-player's INTEGRATION_GUIDE.
 *
 * The sink is a ForwardingAudioSink: with no USB DAC attached it forwards to the
 * default AudioTrack sink; with a DAC attached it takes over and writes PCM
 * straight to the DAC (no resample, no mixer, no system volume).
 *
 * FFmpeg is preferred so 24-bit sources deliver genuine float precision; libFLAC
 * (media3-decoder-flac) delivers raw integer PCM for FLAC with zero float math.
 */
@UnstableApi
class BitPerfectRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    /** Set inside [buildAudioSink]; used by the LoadControl wrapper in the service. */
    var currentUsbSink: UsbAudioSink? = null
        private set

    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        // With libFLAC on the classpath, FLAC is decoded to raw int at the
        // extractor level → float output off. Without it, FFmpeg needs float.
        val hasLibFlac = try {
            Class.forName("androidx.media3.decoder.flac.LibflacAudioRenderer")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        val useFloat = !hasLibFlac

        val delegate = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(useFloat)
            .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
            .build()

        return UsbAudioSink(delegate, context).also { currentUsbSink = it }
    }
}
