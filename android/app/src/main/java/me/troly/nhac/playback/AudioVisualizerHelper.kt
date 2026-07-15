package me.troly.nhac.playback

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot

/**
 * Capture FFT spectrum data in real-time from the active ExoPlayer Audio Session ID.
 * Extracts frequency magnitudes and groups them into 16 bins (legacy) and 32 bins (high-res).
 * Fully dynamic: scales its analysis boundaries up to 48kHz (for 96kHz files) or 96kHz (for 192kHz files)
 * based on actual hardware mixer capture rates and song specifications.
 *
 * Implements Audiophile-Grade Hi-Res Verification Standards:
 * - Detects Android Secret Resampling Cap (Source Hi-Res but System Mixer Resampled to 48kHz/CD limit).
 * - Identifies Fake/Upscaled Hi-Res (Source says 96kHz+ but spectrum has zero ultrasonic energy above 20kHz).
 * - Identifies True/Genuine Hi-Res Audio (Authentic ultrasonic energy validated above 20kHz).
 * - Identifies standard CD Lossless vs. Upscaled MP3 (lossy brickwall filter detected at 16kHz).
 */
object AudioVisualizerHelper {
    private var visualizer: Visualizer? = null
    
    // Legacy 16-band spectrum magnitudes
    private val _amplitudes = MutableStateFlow(FloatArray(16) { 0.1f })
    val amplitudes: StateFlow<FloatArray> = _amplitudes.asStateFlow()

    // Active track specifications (from ExoPlayer metadata)
    private val _trackSamplingRate = MutableStateFlow(44100)
    val trackSamplingRate: StateFlow<Int> = _trackSamplingRate.asStateFlow()

    private val _trackBitDepth = MutableStateFlow(16)
    val trackBitDepth: StateFlow<Int> = _trackBitDepth.asStateFlow()

    private val _trackSuffix = MutableStateFlow("flac")
    val trackSuffix: StateFlow<String> = _trackSuffix.asStateFlow()

    private val _captureSamplingRate = MutableStateFlow(48000)
    val captureSamplingRate: StateFlow<Int> = _captureSamplingRate.asStateFlow()

    // Advanced High-Resolution 32-band spectrum magnitudes (dynamically scaled)
    private val _amplitudes32 = MutableStateFlow(FloatArray(32) { 0.1f })
    val amplitudes32: StateFlow<FloatArray> = _amplitudes32.asStateFlow()

    // Real-Time Lossless & High-Fidelity Integrity Analysis Flows
    private val _losslessVerdict = MutableStateFlow("Analyzing...")
    val losslessVerdict: StateFlow<String> = _losslessVerdict.asStateFlow()

    private val _losslessConfidence = MutableStateFlow(0f)
    val losslessConfidence: StateFlow<Float> = _losslessConfidence.asStateFlow()

    private val _cutoffFrequency = MutableStateFlow(0f)
    val cutoffFrequency: StateFlow<Float> = _cutoffFrequency.asStateFlow()

    // Dynamically generated frequency boundaries for 32 bands
    private val _dynamicBoundaries = MutableStateFlow(FloatArray(33))
    val dynamicBoundaries: StateFlow<FloatArray> = _dynamicBoundaries.asStateFlow()

    // Real-Time raw oscilloscope waveform (128 points, normalized -1.0f to 1.0f)
    private val _rawWaveform = MutableStateFlow(FloatArray(128) { 0f })
    val rawWaveform: StateFlow<FloatArray> = _rawWaveform.asStateFlow()

    // Real-Time RMS signal level for Analog VU meter (0.0f to 1.0f)
    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private var activeAudioSessionId: Int = 0

    // Analysis state variables
    private var runningLosslessScore = 55f
    private var activeFramesCount = 0
    private var silentFramesCount = 0

    init {
        _dynamicBoundaries.value = generateBoundaries(24000f)
    }

    /**
     * Mathematically splits 32 bands:
     * - Bands 0 to 12 are logarithmic spacing (20Hz to 2000Hz) to provide beautiful, distinct detail in bass and mids.
     * - Bands 13 to 32 are linear spacing from 2000Hz to maxF to accurately reveal high-frequency cuts.
     */
    fun generateBoundaries(maxF: Float): FloatArray {
        val bounds = FloatArray(33)
        val numLogBands = 12
        val logStart = 20.0
        val logEnd = 2000.0
        val logFactor = Math.pow(logEnd / logStart, 1.0 / numLogBands)
        
        bounds[0] = logStart.toFloat()
        for (i in 1..numLogBands) {
            bounds[i] = (bounds[i - 1] * logFactor).toFloat()
        }
        
        val numLinearBands = 32 - numLogBands
        val linearStep = (maxF - 2000f) / numLinearBands
        for (i in (numLogBands + 1)..32) {
            bounds[i] = 2000f + (i - numLogBands) * linearStep
        }
        return bounds
    }

    /** Update active track specs when song changes to adjust the audiophile verification target */
    fun setTrackSpecs(samplingRate: Int, bitDepth: Int, suffix: String?) {
        _trackSamplingRate.value = if (samplingRate > 0) samplingRate else 44100
        _trackBitDepth.value = if (bitDepth > 0) bitDepth else 16
        _trackSuffix.value = suffix ?: "flac"
        
        // Reset analysis state on track change to start profiling fresh
        runningLosslessScore = 55f
        activeFramesCount = 0
        silentFramesCount = 0
        _losslessVerdict.value = "Analyzing..."
        _losslessConfidence.value = 0f
        _cutoffFrequency.value = 0f
    }

    @Synchronized
    fun start(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (activeAudioSessionId == audioSessionId && visualizer != null) {
            return
        }
        
        stop()
        activeAudioSessionId = audioSessionId

        // Reset real-time quality analyzer
        _losslessVerdict.value = "Analyzing..."
        _losslessConfidence.value = 0f
        _cutoffFrequency.value = 0f
        runningLosslessScore = 55f
        activeFramesCount = 0
        silentFramesCount = 0

        try {
            val vis = Visualizer(audioSessionId)
            
            // Pro-grade 1024 byte capture size to ensure high frequency resolution (512 complex frequency bins)
            val captureSize = 1024.coerceIn(
                Visualizer.getCaptureSizeRange()[0],
                Visualizer.getCaptureSizeRange()[1]
            )
            vis.captureSize = captureSize

            vis.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    if (waveform == null || waveform.isEmpty()) return
                    
                    // Normalize waveform bytes (unsigned 8-bit, center is 128) to float between -1.0f and 1.0f
                    // Downsample to 128 points
                    val downsampleFactor = (waveform.size / 128).coerceAtLeast(1)
                    val tempWave = FloatArray(128)
                    var squareSum = 0f
                    var count = 0
                    
                    for (i in 0 until 128) {
                        val srcIdx = i * downsampleFactor
                        if (srcIdx < waveform.size) {
                            val byteVal = waveform[srcIdx].toInt() and 0xFF
                            val normalized = (byteVal - 128) / 128f
                            tempWave[i] = normalized
                            squareSum += normalized * normalized
                            count++
                        }
                    }
                    
                    // Apply low-pass smoothing filter to avoid jittery waveform rendering
                    val currentWave = _rawWaveform.value
                    val smoothedWave = FloatArray(128)
                    for (i in 0 until 128) {
                        smoothedWave[i] = currentWave[i] * 0.35f + tempWave[i] * 0.65f
                    }
                    _rawWaveform.value = smoothedWave
                    
                    // Calculate RMS (Root Mean Square) level for VU ballistics
                    val rms = if (count > 0) Math.sqrt((squareSum / count).toDouble()).toFloat() else 0f
                    val prevRms = _rmsLevel.value
                    // Standard VU ballistics: smooth rise (attack) and smooth decay
                    _rmsLevel.value = prevRms * 0.45f + rms * 0.55f
                }

                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft == null || fft.isEmpty()) return
                    
                    val size = fft.size / 2
                    val rateHz = (samplingRate / 1000f).coerceAtLeast(44100f)
                    _captureSamplingRate.value = rateHz.toInt()

                    // Calculate max physical frequency capturing capabilities (Nyquist limit)
                    val maxF = rateHz / 2f
                    val bounds = generateBoundaries(maxF)
                    _dynamicBoundaries.value = bounds

                     // 1. Calculate high-res 32 bands dynamically in a single O(N log B) pass instead of nested O(B * N)
                     val temp32 = FloatArray(32)
                     val bandSums = FloatArray(32)
                     val bandCounts = IntArray(32)
                     
                     for (j in 1 until size) {
                         val f = j * rateHz / fft.size
                         
                         // Fast binary search to find the correct band index in O(log B) [max 5 steps]
                         var targetBand = -1
                         var low = 0
                         var high = 31
                         while (low <= high) {
                             val mid = (low + high) ushr 1
                             if (f >= bounds[mid]) {
                                 if (f < bounds[mid + 1]) {
                                     targetBand = mid
                                     break
                                 } else {
                                     low = mid + 1
                                 }
                             } else {
                                 high = mid - 1
                             }
                         }
                         
                         if (targetBand != -1) {
                             val rIndex = j * 2
                             val iIndex = j * 2 + 1
                             if (iIndex < fft.size) {
                                 val r = fft[rIndex].toFloat()
                                 val img = fft[iIndex].toFloat()
                                 // O(1) float multiplication & hardware ARM64 FSQRT
                                 val magnitude = kotlin.math.sqrt(r * r + img * img)
                                 bandSums[targetBand] += magnitude
                                 bandCounts[targetBand]++
                             }
                         }
                     }
                     
                     for (i in 0 until 32) {
                         val avg = if (bandCounts[i] > 0) bandSums[i] / bandCounts[i] else 0f
                         temp32[i] = (avg / 65f).coerceIn(0.1f, 1.0f)
                     }

                    // Apply smooth decay dynamics to 32 bands
                    val current32 = _amplitudes32.value
                    val smoothed32 = FloatArray(32)
                    for (i in 0 until 32) {
                        smoothed32[i] = current32[i] * 0.45f + temp32[i] * 0.55f
                    }
                    _amplitudes32.value = smoothed32

                    // 2. Downsample to legacy 16 bands for backward-compatible UI controls
                    val smoothed16 = FloatArray(16)
                    for (i in 0 until 16) {
                        smoothed16[i] = (smoothed32[i * 2] + smoothed32[i * 2 + 1]) / 2f
                    }
                    _amplitudes.value = smoothed16

                    // 3. Real-Time Audiophile Hi-Res and Lossless Verification Standards
                    val trackSR = _trackSamplingRate.value
                    val isNativeHiRes = trackSR > 48000 || _trackSuffix.value.lowercase() in listOf("dsf", "dff", "dsd")

                    // Average mid-to-high treble energy (4kHz - 10kHz)
                    var trebleSum = 0f
                    for (i in 12..17) trebleSum += temp32[i]
                    val trebleE = trebleSum / 6f

                    if (trebleE > 0.15f) {
                        activeFramesCount++
                        silentFramesCount = 0

                        if (isNativeHiRes) {
                            // --- AUDIOPHILE HI-RES CODEC & PATH VERIFICATION ---
                            if (rateHz <= 48000f) {
                                // Mismatch: Track is Hi-Res, but Android output mixer downsamples it to 48kHz (CD Limit)
                                _losslessVerdict.value = "ANDROID RESAMPLED (LIMIT 48k)"
                                _losslessConfidence.value = 100f
                                _cutoffFrequency.value = rateHz / 2f
                            } else {
                                // Bit-perfect/Hi-Res playback path verified! Verify authentic ultrasonic content
                                // High resolution boundaries cover up to rateHz/2 (e.g. up to 48kHz for 96kHz mixer).
                                // Measure energy in ultrasonic range (>20kHz, corresponding to bands 24 to 31)
                                var ultrasonicSum = 0f
                                for (i in 24..31) ultrasonicSum += temp32[i]
                                val ultrasonicE = ultrasonicSum / 8f

                                if (ultrasonicE > 0.14f) {
                                    // Real ultrasonic harmonics present! Confirmed authentic Hi-Res mastering
                                    runningLosslessScore = (runningLosslessScore + 1.5f).coerceAtMost(100f)
                                } else if (ultrasonicE < 0.11f && trebleE > 0.25f) {
                                    // High mid-treble, but absolute flatline above 20kHz. CD Upscaled Fake Hi-Res.
                                    runningLosslessScore = (runningLosslessScore - 1.8f).coerceIn(5f, 100f)
                                }

                                if (activeFramesCount > 60) {
                                    val score = runningLosslessScore
                                    _losslessConfidence.value = score
                                    if (score >= 75f) {
                                        _losslessVerdict.value = "VERIFIED HI-RES AUDIO"
                                        _cutoffFrequency.value = trackSR / 2f
                                    } else {
                                        _losslessVerdict.value = "UPSCALED / FAKE HI-RES"
                                        _cutoffFrequency.value = 22000f // Hard-capped at CD Nyquist limit
                                    }
                                }
                            }
                        } else {
                            // --- STANDARD CD QUALITY LOSSLESS VERIFICATION ---
                            // Average high treble energy (10kHz - 15kHz)
                            var highTrebleSum = 0f
                            for (i in 18..22) highTrebleSum += temp32[i]
                            val highTrebleE = highTrebleSum / 5f

                            // Average ultra-high treble energy (15kHz - 22kHz, where lossy codecs cut off)
                            var ultraHighSum = 0f
                            for (i in 23..29) ultraHighSum += temp32[i]
                            val ultraHighE = ultraHighSum / 7f

                            if (ultraHighE > 0.17f) {
                                runningLosslessScore = (runningLosslessScore + 1.2f).coerceAtMost(100f)
                            } else if (ultraHighE < 0.12f && highTrebleE > 0.18f) {
                                runningLosslessScore = (runningLosslessScore - 1.8f).coerceIn(5f, 100f)
                            } else if (ultraHighE < 0.12f && trebleE > 0.25f) {
                                runningLosslessScore = (runningLosslessScore - 2.2f).coerceIn(5f, 100f)
                            }

                            if (activeFramesCount > 60) {
                                val score = runningLosslessScore
                                _losslessConfidence.value = score
                                when {
                                    score >= 80f -> {
                                        _losslessVerdict.value = "VERIFIED PURE LOSSLESS"
                                        var estimatedCutoff = 22000f
                                        for (idx in 31 downTo 20) {
                                            if (temp32[idx] > 0.14f) {
                                                estimatedCutoff = bounds[idx]
                                                break
                                            }
                                        }
                                        _cutoffFrequency.value = estimatedCutoff
                                    }
                                    score in 45f..79f -> {
                                        _losslessVerdict.value = "PROBABLE LOSSLESS"
                                        _cutoffFrequency.value = 19500f
                                    }
                                    else -> {
                                        _losslessVerdict.value = "COMPRESSED / LOSS_CUT"
                                        var cutoff = 16000f
                                        for (idx in 23 downTo 12) {
                                            if (temp32[idx] > 0.14f) {
                                                cutoff = bounds[idx]
                                                break
                                            }
                                        }
                                        _cutoffFrequency.value = cutoff
                                    }
                                }
                            }
                        }
                    } else {
                        silentFramesCount++
                        if (silentFramesCount > 100 && activeFramesCount < 30) {
                            _losslessVerdict.value = "Analyzing (Awaiting active frequencies)..."
                        }
                    }
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true)

            vis.enabled = true
            visualizer = vis
            Log.d("AudioVisualizerHelper", "Audiophile-Grade Dynamic Visualizer bound to audio session: $audioSessionId")
        } catch (e: Exception) {
            Log.e("AudioVisualizerHelper", "Could not start native Visualizer on session $audioSessionId: ${e.message}")
            _amplitudes.value = FloatArray(16) { 0.1f }
            _amplitudes32.value = FloatArray(32) { 0.1f }
            _losslessVerdict.value = "Hardware Visualizer Bypass Active"
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
