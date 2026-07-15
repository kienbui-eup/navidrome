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
        val dspPrefs = context.getSharedPreferences("trolynhac_dsp", Context.MODE_PRIVATE)
        val bufferSizeSetting = dspPrefs.getString("bufferSize", "Balanced Standard") ?: "Balanced Standard"

        val aaudioEnabled = dspPrefs.getBoolean("aaudioEnabled", false)

        // Luôn bật Float Output để giữ độ phân giải 24-bit/32-bit cho nguồn âm thanh chất lượng cao,
        // giúp hệ thống truyền tải Hi-Res nguyên bản qua Bluetooth (LDAC / SSC).
        val delegateBuilder = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(true)
            .setAudioCapabilities(AudioCapabilities.getCapabilities(context))

        if (bufferSizeSetting != "Balanced Standard") {
            val frameCount = bufferSizeSetting.replace(" frames", "").replace(" frame", "").toIntOrNull() ?: 1024
            delegateBuilder.setAudioTrackBufferSizeProvider { minBufferSizeInBytes, encoding, outputMode, pcmFrameSize, sampleRate, bitrate, maxSpeed ->
                val calculatedBytes = frameCount * pcmFrameSize
                calculatedBytes.coerceAtLeast(minBufferSizeInBytes)
            }
        }

        val delegate = delegateBuilder.build()

        val bitPerfectEnabled = dspPrefs.getBoolean("bitPerfectEnabled", true)
        val usbConfig = com.decent.usbaudio.media3.UsbAudioSinkConfig(bitPerfectEnabled = bitPerfectEnabled)

        return UsbAudioSink(delegate, context, usbConfig).also { currentUsbSink = it }
    }
}
