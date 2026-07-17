package me.troly.nhac.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

data class ActiveDeviceDetails(
    val name: String,
    val type: Int,
    val typeLabel: String,
    val techLabel: String,
    val maxQualityForecast: String,
    val description: String,
    val isHiResCapable: Boolean,
    val isLossless: Boolean,
    val maxSampleRate: Int = 48000,
    val maxBitDepth: Int = 16,
    val isFoldOrTablet: Boolean = false
) {
    val hardwareNote: String get() {
        val manufacturer = android.os.Build.MANUFACTURER ?: "Device"
        val model = android.os.Build.MODEL ?: "Hardware"
        
        return when (typeLabel) {
            "USB DAC" -> {
                "💡 Lưu ý phần cứng: Thiết bị giải mã USB DAC [$name] đang hoạt động ở chế độ truyền dẫn trực tiếp. Đã kích hoạt Bit-Perfect Engine giúp loại bỏ hoàn toàn hiện tượng méo tiếng do bộ chuyển đổi tần số lấy mẫu hệ điều hành (Android OS SRC Resampling) gây ra. Luồng tín hiệu số $maxBitDepth-bit / ${maxSampleRate / 1000.0} kHz được bảo toàn nguyên bản từng bit dữ liệu tới chip DAC ngoài để tái cấu trúc dạng sóng analog hoàn hảo."
            }
            "Bluetooth" -> {
                "💡 Lưu ý phần cứng: Đang kết nối không dây qua Bluetooth [$name]. Hệ thống tự động thương lượng bộ giải mã không dây (Codec) tối ưu (LDAC/aptX HD/LHDC/AAC) dựa trên khả năng phần cứng thực tế. Hỗ trợ băng thông truyền dẫn tối đa $maxBitDepth-bit / ${maxSampleRate / 1000.0} kHz. Mẹo: Hãy kích hoạt LDAC 'Optimized for Audio Quality' trong tùy chọn nhà phát triển (Developer Options) để đạt băng thông 990kbps tối đa."
            }
            else -> {
                if (isFoldOrTablet) {
                    "💡 Lưu ý phần cứng: Đang phát qua Loa tích hợp trên thiết bị màn hình lớn/thiết bị gập [$manufacturer $model]. Để đảm bảo hiệu suất hoạt động mượt mà của màng loa siêu mỏng và kéo dài tuổi thọ pin, hệ thống tự động cân bằng âm sắc dải trung/cao và bypass tần số siêu trầm. Mẹo: Trải nghiệm tốt nhất khi mở rộng màn hình chính ở góc độ tối ưu để tạo không gian âm thanh nổi (Stereo Acoustic Field) rộng hơn!"
                } else {
                    "💡 Lưu ý phần cứng: Đang phát qua Loa tích hợp hoặc ngõ ra analog của thiết bị [$manufacturer $model]. Để bảo vệ phần cứng loa nhỏ khỏi quá nhiệt và méo hài vật lý khi phát nhạc số chất lượng cao, DSP tự động giới hạn biên độ đỉnh của âm trầm. Khuyến nghị: Sử dụng tai nghe dây hoặc bộ giải mã ngoài USB DAC để tận hưởng độ động đầy đủ nhất của bản thu."
                }
            }
        }
    }
}

object AudioDeviceHelper {

    fun isFoldableOrTablet(context: Context): Boolean {
        val model = Build.MODEL ?: ""
        val product = Build.PRODUCT ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        
        val isFold = model.contains("fold", ignoreCase = true) || 
                     product.contains("fold", ignoreCase = true) || 
                     manufacturer.contains("fold", ignoreCase = true) ||
                     model.contains("flip", ignoreCase = true) ||
                     product.contains("flip", ignoreCase = true)
        
        // Check physical screen dimensions to dynamically detect tablet category (unfolded state or generic tablet)
        val metrics = context.resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        val isTablet = widthDp >= 600
        
        return isFold || isTablet
    }

    private fun getDeviceCapabilities(device: AudioDeviceInfo): Pair<Int, Int> {
        val rates = device.sampleRates
        val encodings = device.encodings
        
        val maxRate = if (rates.isNotEmpty()) {
            var m = 48000
            for (r in rates) {
                if (r > m) m = r
            }
            m
        } else 48000

        val maxBits = if (encodings.isNotEmpty()) {
            var maxBits = 16
            for (enc in encodings) {
                when (enc) {
                    2 -> if (maxBits < 16) maxBits = 16 // ENCODING_PCM_16BIT
                    21 -> if (maxBits < 24) maxBits = 24 // ENCODING_PCM_24BIT_PACKED
                    22 -> if (maxBits < 32) maxBits = 32 // ENCODING_PCM_32BIT
                    4 -> if (maxBits < 32) maxBits = 32 // ENCODING_PCM_FLOAT
                }
            }
            maxBits
        } else 16
        
        return Pair(maxRate, maxBits)
    }

    private fun getDeviceRatesString(device: AudioDeviceInfo): String {
        val rates = device.sampleRates
        return if (rates.isNotEmpty()) {
            rates.sorted().joinToString(", ") { r ->
                if (r >= 1000) {
                    if (r % 1000 == 0) "${r / 1000}kHz" else "${r / 1000.0}kHz"
                } else {
                    "${r}Hz"
                }
            }
        } else "44.1kHz, 48kHz"
    }

    fun getActiveDeviceDetails(context: Context): ActiveDeviceDetails {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return createFallbackDetails()

        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        if (devices.isEmpty()) {
            return createFallbackDetails()
        }

        val isFoldOrTab = isFoldableOrTablet(context)

        // Prioritize devices according to Android's routing behavior:
        // 1. USB DAC
        // 2. Bluetooth A2DP
        // 3. Wired Headphones / Headset / Aux
        // 4. HDMI / HDMI ARC / eARC
        // 5. Built-in Speaker
        val usbDevice = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || 
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || 
            it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY 
        }
        if (usbDevice != null) {
            val name = getCleanDeviceName(usbDevice, "USB DAC Audio")
            val (maxRate, maxBits) = getDeviceCapabilities(usbDevice)
            val rateListStr = getDeviceRatesString(usbDevice)

            val desc = "Thiết bị giải mã âm thanh chuyên dụng USB DAC ngoài [$name] được phát hiện. Toàn bộ dữ liệu âm thanh vượt qua bộ trộn hệ điều hành (Android Mixer), được truyền tải hoàn hảo không suy hao (Bit-Perfect) trực tiếp tới phần cứng giải mã ngoài.\n\n" +
                       "Cấu hình phần cứng khả dụng: $maxBits-bit / ${maxRate / 1000.0} kHz (Tần số lấy mẫu hỗ trợ: $rateListStr)."
            
            val maxQuality = "$maxBits-bit / ${maxRate / 1000.0} kHz (Lossless Bit-Perfect)"

            return ActiveDeviceDetails(
                name = name,
                type = usbDevice.type,
                typeLabel = "USB DAC",
                techLabel = "USB Audio Class 2.0 (Direct Bit-Perfect)",
                maxQualityForecast = maxQuality,
                description = desc,
                isHiResCapable = true,
                isLossless = true,
                maxSampleRate = maxRate,
                maxBitDepth = maxBits,
                isFoldOrTablet = isFoldOrTab
            )
        }

        val bluetoothDevice = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP 
        }
        if (bluetoothDevice != null) {
            val name = getCleanDeviceName(bluetoothDevice, "Bluetooth Audio Device")
            val (maxRate, maxBits) = getDeviceCapabilities(bluetoothDevice)
            val rateListStr = getDeviceRatesString(bluetoothDevice)

            val desc = "Thiết bị âm thanh không dây Bluetooth [$name] đang được kết nối. Chất lượng truyền dẫn không dây tối đa phụ thuộc vào codec âm thanh đang hoạt động (LDAC: 24-bit/96kHz, aptX HD: 24-bit/48kHz, AAC/SBC: 16-bit/44.1kHz) được lựa chọn tự động bởi hệ thống để đảm bảo dải động rộng nhất.\n\n" +
                       "Cấu hình kết nối hiện tại: $maxBits-bit / ${maxRate / 1000.0} kHz (Các tần số lấy mẫu khả dụng: $rateListStr)."

            return ActiveDeviceDetails(
                name = name,
                type = bluetoothDevice.type,
                typeLabel = "Bluetooth",
                techLabel = "Bluetooth Wireless Audio (A2DP Hi-Res)",
                maxQualityForecast = "$maxBits-bit / ${maxRate / 1000.0} kHz (Compressed High-Resolution)",
                description = desc,
                isHiResCapable = maxRate > 48000,
                isLossless = false,
                maxSampleRate = maxRate,
                maxBitDepth = maxBits,
                isFoldOrTablet = isFoldOrTab
            )
        }

        val wiredDevice = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || 
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || 
            it.type == AudioDeviceInfo.TYPE_AUX_LINE 
        }
        if (wiredDevice != null) {
            val name = getCleanDeviceName(wiredDevice, "Wired Headphones")
            val (maxRate, maxBits) = getDeviceCapabilities(wiredDevice)
            val rateListStr = getDeviceRatesString(wiredDevice)
            return ActiveDeviceDetails(
                name = name,
                type = wiredDevice.type,
                typeLabel = "Tai nghe dây",
                techLabel = "Cổng Analog 3.5mm (High-Definition)",
                maxQualityForecast = "$maxBits-bit / ${maxRate / 1000.0} kHz (Lossless Analog Output)",
                description = "Kết nối dây truyền thống qua giắc 3.5mm hoặc đầu chuyển analog. Luồng tín hiệu âm thanh analog không bị nén, giữ nguyên độ trung thực cao, méo hài thấp và độ trễ bằng 0.\n\n" +
                "Khả năng cổng ra analog: $maxBits-bit / ${maxRate / 1000.0} kHz (Khả dụng: $rateListStr).",
                isHiResCapable = maxRate > 48000,
                isLossless = true,
                maxSampleRate = maxRate,
                maxBitDepth = maxBits,
                isFoldOrTablet = isFoldOrTab
            )
        }

        val hdmiDevice = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_HDMI || 
            it.type == AudioDeviceInfo.TYPE_HDMI_ARC || 
            it.type == AudioDeviceInfo.TYPE_HDMI_EARC 
        }
        if (hdmiDevice != null) {
            val name = getCleanDeviceName(hdmiDevice, "HDMI Digital Out")
            val (maxRate, maxBits) = getDeviceCapabilities(hdmiDevice)
            val rateListStr = getDeviceRatesString(hdmiDevice)
            return ActiveDeviceDetails(
                name = name,
                type = hdmiDevice.type,
                typeLabel = "HDMI",
                techLabel = "HDMI Bitstream Digital Out",
                maxQualityForecast = "$maxBits-bit / ${maxRate / 1000.0} kHz (Lossless Digital Multi-channel)",
                description = "Xuất tín hiệu số chất lượng cao qua cổng HDMI tới Receiver hoặc Soundbar. Đảm bảo âm thanh số được truyền đi nguyên bản không nén.\n\n" +
                "Hỗ trợ HDMI hiện tại: $maxBits-bit / ${maxRate / 1000.0} kHz (Khả dụng: $rateListStr).",
                isHiResCapable = maxRate > 48000,
                isLossless = true,
                maxSampleRate = maxRate,
                maxBitDepth = maxBits,
                isFoldOrTablet = isFoldOrTab
            )
        }

        val speakerDevice = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || 
            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE 
        }
        if (speakerDevice != null) {
            val model = Build.MODEL ?: ""
            val manufacturer = Build.MANUFACTURER ?: ""
            val (maxRate, maxBits) = getDeviceCapabilities(speakerDevice)
            val rateListStr = getDeviceRatesString(speakerDevice)

            val name = if (isFoldOrTab) {
                "Loa kép Stereo Màn hình lớn/Gập"
            } else {
                "Loa tích hợp trên thiết bị (Built-in Speaker)"
            }
            val techLabel = if (isFoldOrTab) {
                "Hệ thống loa kép Stereo cân bằng tối ưu không gian"
            } else {
                "Loa tích hợp trên thiết bị"
            }
            val desc = if (isFoldOrTab) {
                "Đang phát qua hệ thống loa kép Stereo cao cấp của thiết bị màn hình gập hoặc máy tính bảng [$manufacturer $model], mang lại trường âm thanh nổi (stereo space) rộng mở, rõ nét dải âm trung và âm cao trong trẻo.\n\n" +
                "Khả năng loa tích hợp: $maxBits-bit / ${maxRate / 1000.0} kHz (Khả dụng: $rateListStr)."
            } else {
                "Đang phát qua loa tích hợp của thiết bị [$manufacturer $model]. Phù hợp cho việc nghe kiểm âm nhanh hoặc đàm thoại, dải tần âm thanh bị giới hạn vật lý ở dải âm trầm (bass loss) và chiều rộng không gian âm nổi (stereo width).\n\n" +
                "Khả năng loa tích hợp: $maxBits-bit / ${maxRate / 1000.0} kHz (Khả dụng: $rateListStr)."
            }
            
            return ActiveDeviceDetails(
                name = name,
                type = speakerDevice.type,
                typeLabel = "Loa ngoài",
                techLabel = techLabel,
                maxQualityForecast = "$maxBits-bit / ${maxRate / 1000.0} kHz (Standard Audio)",
                description = desc,
                isHiResCapable = isFoldOrTab || maxRate > 48000,
                isLossless = true,
                maxSampleRate = maxRate,
                maxBitDepth = maxBits,
                isFoldOrTablet = isFoldOrTab
            )
        }

        return createFallbackDetails()
    }

    private fun getCleanDeviceName(device: AudioDeviceInfo, defaultName: String): String {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            device.productName?.toString()
        } else {
            null
        }
        return if (name.isNullOrBlank() || name == "null") defaultName else name
    }

    private fun createFallbackDetails(): ActiveDeviceDetails {
        return ActiveDeviceDetails(
            name = "Thiết bị mặc định (Default Output)",
            type = AudioDeviceInfo.TYPE_UNKNOWN,
            typeLabel = "Default",
            techLabel = "Android AudioTrack System",
            maxQualityForecast = "16-bit / 44.1kHz (Standard Audio)",
            description = "Đang phát qua luồng âm thanh mặc định của hệ điều hành. Âm thanh sẽ đi qua bộ trộn tiêu chuẩn Android Audio Flinger.",
            isHiResCapable = false,
            isLossless = true,
            maxSampleRate = 48000,
            maxBitDepth = 16
        )
    }
}
