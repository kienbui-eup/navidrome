package me.troly.nhac.playback

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot

/**
 * Capture FFT spectrum data in real-time from the active ExoPlayer Audio Session ID.
 * Extracts frequency magnitudes and groups them into 16 bins for standard music visualizer displays.
 * Supports smooth transition states and safely releases hardware visualizer resources on track change.
 */
object AudioVisualizerHelper {
    private var visualizer: Visualizer? = null
    
    // 16 bands representing key frequencies (from sub-bass to high treble)
    private val _amplitudes = MutableStateFlow(FloatArray(16) { 0.1f })
    val amplitudes: StateFlow<FloatArray> = _amplitudes.asStateFlow()

    private var activeAudioSessionId: Int = 0

    @Synchronized
    fun start(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (activeAudioSessionId == audioSessionId && visualizer != null) {
            // Already running on this session
            return
        }
        
        stop()
        activeAudioSessionId = audioSessionId

        try {
            val vis = Visualizer(audioSessionId)
            
            // Set capture size to minimum range for low latency, rapid visual response
            val captureSize = Visualizer.getCaptureSizeRange()[0]
            vis.captureSize = captureSize

            vis.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    // Not utilizing waveform data for spectrum visualization
                }

                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft == null || fft.isEmpty()) return
                    
                    // FFT contains alternating real and imaginary parts: r[0], r[n], r[1], i[1], r[2], i[2]...
                    val size = fft.size / 2
                    val magnitudes = FloatArray(16)
                    val binSize = (size / 16).coerceAtLeast(1)

                    for (i in 0 until 16) {
                        var sum = 0f
                        val start = i * binSize
                        val end = (start + binSize).coerceAtMost(size - 1)
                        for (j in start..end) {
                            val rIndex = j * 2
                            val iIndex = j * 2 + 1
                            if (iIndex < fft.size) {
                                val r = fft[rIndex].toFloat()
                                val img = fft[iIndex].toFloat()
                                sum += hypot(r, img)
                            }
                        }
                        val avg = sum / (end - start + 1)
                        // Normalize 8-bit integer amplitude bytes (0-128 range) to a nice 0.1 - 1.0 float range
                        magnitudes[i] = (avg / 80f).coerceIn(0.1f, 1.0f)
                    }

                    // Apply mild exponential smoothing to avoid frantic jitter in the Compose UI
                    val current = _amplitudes.value
                    val smoothed = FloatArray(16)
                    for (i in 0 until 16) {
                        smoothed[i] = current[i] * 0.4f + magnitudes[i] * 0.6f
                    }
                    _amplitudes.value = smoothed
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)

            vis.enabled = true
            visualizer = vis
            Log.d("AudioVisualizerHelper", "Real-Time Visualizer bound to audio session: $audioSessionId")
        } catch (e: Exception) {
            Log.e("AudioVisualizerHelper", "Could not start native Visualizer on session $audioSessionId: ${e.message}")
            // Fallback: Clear to zeros so UI defaults to procedural visualizer safely
            _amplitudes.value = FloatArray(16) { 0.1f }
        }
    }

    @Synchronized
    fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            Log.e("AudioVisualizerHelper", "Error releasing Visualizer: ${e.message}")
        } finally {
            visualizer = null
            activeAudioSessionId = 0
        }
    }
}
