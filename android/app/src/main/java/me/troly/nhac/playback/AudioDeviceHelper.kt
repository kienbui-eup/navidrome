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
    val maxBitDepth: Int = 16
) {
    val hardwareNote: String get() {
        val manufacturer = android.os.Build.MANUFACTURER ?: "Samsung"
        val model = android.os.Build.MODEL ?: "Fold 5"
        val isFold = model.contains("fold", ignoreCase = true) || manufacturer.contains("fold", ignoreCase = true)
        
        return when (typeLabel) {
            "USB DAC" -> {
                val isTopping = name.contains("Topping", ignoreCase = true) || name.contains("E30", ignoreCase = true)
                if (isTopping) {
                    "💡 Lưu ý phần cứng: USB DAC Topping E30 đang được kết nối. Chip giải mã AK4493 hỗ trợ gốc DSD512. Hãy kết hợp với Pre Suca T5C bóng Mullard 403b và op-amp Muses02 để trải nghiệm âm thanh analog cực mượt, dải âm ấm dày và nhạc tính đỉnh cao!"
                } else {
                    "💡 Lưu ý phần cứng: USB DAC [$name] đang được kết nối. Thiết bị hỗ trợ chất lượng tối đa $maxBitDepth-bit / ${maxSampleRate / 1000.0} kHz. Đã kích hoạt chế độ Bit-Perfect trực tiếp qua cổng USB để giữ nguyên vẹn tín hiệu số nguyên bản!"
                }
            }
            "Bluetooth" -> {
                "💡 Lưu ý phần cứng: Đang phát qua tai nghe/thiết bị Bluetooth không dây [$name]. Tự động tối ưu hóa luồng truyền tải không dây codec chất lượng cao (LDAC/SSC) lên tới 24-bit/96kHz, vô hiệu hóa upsampling siêu tần DSD để tránh hao pin và suy hao băng thông."
            }
            else -> {
                if (isFold) {
                    "💡 Lưu ý phần cứng: Đang phát ra Loa ngoài của Galaxy Z Fold 5. Để bảo vệ thời lượng pin và tránh quá nhiệt, các bộ lọc upsampling nặng được bypass. Mẹo: Hãy mở màn hình gập của Fold 5 để tối ưu hóa không gian stereo kép tinh chỉnh bởi AKG!"
                } else {
                    "💡 Lưu ý phần cứng: Đang phát ra Loa ngoài hoặc ngõ ra analog của thiết bị [$model] ($manufacturer). Hệ thống tự động bypass các bộ lọc upsampling để bảo vệ thời lượng pin và tránh nóng máy."
                }
            }
        }
    }
}

object AudioDeviceHelper {

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

        // Prioritize devices according to Android's default routing behavior:
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
            val isShanling = name.contains("Shanling", ignoreCase = true) || name.contains("UP5", ignoreCase = true)
            val isTopping = name.contains("Topping", ignoreCase = true) || name.contains("E30", ignoreCase = true)
            
            val (maxRate, maxBits) = getDeviceCapabilities(usbDevice)
            val rateListStr = getDeviceRatesString(usbDevice)

            val desc = when {
                isShanling -> {
                    "Thiết bị giải mã Shanling UP5 được kết nối qua USB. Toàn bộ dữ liệu âm thanh vượt qua bộ trộn Android (Android Mixer), được truyền tải hoàn hảo, không suy hao (Bit-Perfect) trực tiếp tới phần cứng giải mã Dual ES9219C DAC.\n\n" +
                    "Hỗ trợ phần cứng hiện tại: $maxBits-bit / ${maxRate / 1000}kHz (Tần số khả dụng: $rateListStr)."
                }
                isTopping -> {
                    "Bộ giải mã Topping E30 được phát hiện kết nối qua USB. Ứng dụng kích hoạt chế độ xuất Bit-Perfect bỏ qua hoàn toàn bộ trộn hệ thống (Android Mixer), truyền tín hiệu số nguyên bản trực tiếp đến chip DAC AK4493, kết hợp với Pre Suca T5C (đã nâng cấp bóng Mullard 403b, tụ chất lượng cao và op-amp Muses02) cho chất âm đèn ấm áp, dải động cực rộng và nhạc tính đỉnh cao!\n\n" +
                    "Hỗ trợ phần cứng hiện tại: $maxBits-bit / ${maxRate / 1000}kHz (Tần số khả dụng: $rateListStr)."
                }
                else -> {
                    "Thiết bị giải mã USB DAC ngoài [$name] được kết nối trực tiếp. Ứng dụng kích hoạt chế độ xuất Bit-Perfect bỏ qua bộ trộn hệ thống (Android Mixer), giữ nguyên vẹn dải động và tần số lấy mẫu của bản nhạc gốc.\n\n" +
                    "Hỗ trợ phần cứng hiện tại: $maxBits-bit / ${maxRate / 1000}kHz (Tần số khả dụng: $rateListStr)."
                }
            }
            
            val maxQuality = "$maxBits-bit / ${maxRate / 1000}kHz (Lossless Bit-Perfect)"

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
                maxBitDepth = maxBits
            )
        }

        val bluetoothDevice = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP 
        }
        if (bluetoothDevice != null) {
            val name = getCleanDeviceName(bluetoothDevice, "Bluetooth Audio Device")
            val isBuds2Pro = name.contains("Buds2 Pro", ignoreCase = true) || name.contains("Samsung", ignoreCase = true) && name.contains("Buds", ignoreCase = true)
            val isShanling = name.contains("Shanling", ignoreCase = true) || name.contains("UP5", ignoreCase = true)
            
            val (maxRate, maxBits) = getDeviceCapabilities(bluetoothDevice)
            val rateListStr = getDeviceRatesString(bluetoothDevice)

            val desc = when {
                isBuds2Pro -> {
                    "Tai nghe Samsung Galaxy Buds 2 Pro đang kết nối. Để đạt chất lượng âm thanh 24-bit/96kHz (Seamless Codec - SSC), hãy đảm bảo ứng dụng phát nhạc gốc chất lượng cao và bật 'Seamless Codec' trong ứng dụng Galaxy Wearable.\n\n" +
                    "Hỗ trợ cấu hình hiện tại: $maxBits-bit / ${maxRate / 1000}kHz (Khả dụng: $rateListStr)."
                }
                isShanling -> {
                    "Thiết bị Shanling UP5 được kết nối qua Bluetooth. Hỗ trợ codec LDAC chuẩn Audiophile với tốc độ truyền lên tới 990kbps (24-bit/96kHz). Hãy chắc chắn bạn đã chọn 'Ưu tiên chất lượng âm thanh' trong Cài đặt Bluetooth để có trải nghiệm đỉnh cao.\n\n" +
                    "Hỗ trợ cấu hình hiện tại: $maxBits-bit / ${maxRate / 1000}kHz (Khả dụng: $rateListStr)."
                }
                else -> {
                    "Kết nối không dây Bluetooth. Chất lượng truyền dẫn phụ thuộc vào Codec đang hoạt động (LDAC: 24-bit/96kHz, aptX HD: 24-bit/48kHz, AAC/SBC: 16-bit/44.1kHz). Khuyên dùng LDAC hoặc aptX HD để nghe nhạc Hi-Res.\n\n" +
                    "Hỗ trợ cấu hình hiện tại: $maxBits-bit / ${maxRate / 1000}kHz (Khả dụng: $rateListStr)."
                }
            }

            return ActiveDeviceDetails(
                name = name,
                type = bluetoothDevice.type,
                typeLabel = "Bluetooth",
                techLabel = "Bluetooth Wireless Audio (A2DP Hi-Res)",
                maxQualityForecast = "$maxBits-bit / ${maxRate / 1000}kHz (Compressed High-Resolution)",
                description = desc,
                isHiResCapable = maxRate > 48000,
                isLossless = false,
                maxSampleRate = maxRate,
                maxBitDepth = maxBits
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
                maxQualityForecast = "$maxBits-bit / ${maxRate / 1000}kHz (Lossless Analog Output)",
                description = "Kết nối dây truyền thống qua giắc 3.5mm hoặc đầu chuyển analog. Luồng tín hiệu âm thanh analog không bị nén, giữ nguyên độ trung thực cao, méo hài thấp và độ trễ bằng 0.\n\n" +
                "Khả năng cổng ra analog: $maxBits-bit / ${maxRate / 1000}kHz (Khả dụng: $rateListStr).",
                isHiResCapable = maxRate > 48000,
                isLossless = true,
                maxSampleRate = maxRate,
                maxBitDepth = maxBits
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
                maxQualityForecast = "$maxBits-bit / ${maxRate / 1000}kHz (Lossless Digital Multi-channel)",
                description = "Xuất tín hiệu số chất lượng cao qua cổng HDMI tới Receiver hoặc Soundbar. Đảm bảo âm thanh số được truyền đi nguyên bản không nén.\n\n" +
                "Hỗ trợ HDMI hiện tại: $maxBits-bit / ${maxRate / 1000}kHz (Khả dụng: $rateListStr).",
                isHiResCapable = maxRate > 48000,
                isLossless = true,
                maxSampleRate = maxRate,
                maxBitDepth = maxBits
            )
        }

        val speakerDevice = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || 
            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE 
        }
        if (speakerDevice != null) {
            val model = Build.MODEL ?: ""
            val product = Build.PRODUCT ?: ""
            val manufacturer = Build.MANUFACTURER ?: ""
            val isSamsungFold = model.contains("fold", ignoreCase = true) || 
                    product.contains("fold", ignoreCase = true) || 
                    (manufacturer.contains("samsung", ignoreCase = true) && Build.DEVICE?.contains("fold", ignoreCase = true) == true)
            
            val (maxRate, maxBits) = getDeviceCapabilities(speakerDevice)
            val rateListStr = getDeviceRatesString(speakerDevice)

            val name = if (isSamsungFold) {
                "Loa kép Stereo Galaxy Z Fold (AKG Tuned)"
            } else {
                "Loa thiết bị (Built-in Speaker)"
            }
            val techLabel = if (isSamsungFold) {
                "Hệ thống loa kép Stereo cân bằng tinh chỉnh bởi AKG"
            } else {
                "Loa tích hợp trên thiết bị"
            }
            val desc = if (isSamsungFold) {
                "Đang phát qua hệ thống loa kép Stereo cao cấp của Samsung Galaxy Z Fold được tinh chỉnh kỹ lưỡng bởi AKG với công nghệ Dolby Atmos, mang lại trường âm thanh sống động, dải âm trung rõ nét và âm cao trong trẻo vượt trội khi mở màn hình chính.\n\n" +
                "Khả năng loa tích hợp: $maxBits-bit / ${maxRate / 1000}kHz (Khả dụng: $rateListStr)."
            } else {
                "Phát qua loa ngoài tích hợp của điện thoại. Phù hợp nghe tạm thời, bị giới hạn về dải tần số, độ tách bạch âm thanh nổi (stereo width) và chất lượng dải trầm.\n\n" +
                "Khả năng loa tích hợp: $maxBits-bit / ${maxRate / 1000}kHz (Khả dụng: $rateListStr)."
            }
            
            return ActiveDeviceDetails(
                name = name,
                type = speakerDevice.type,
                typeLabel = "Loa ngoài",
                techLabel = techLabel,
                maxQualityForecast = "$maxBits-bit / ${maxRate / 1000}kHz (Standard Audio)",
                description = desc,
                isHiResCapable = isSamsungFold || maxRate > 48000,
                isLossless = true,
                maxSampleRate = maxRate,
                maxBitDepth = maxBits
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
