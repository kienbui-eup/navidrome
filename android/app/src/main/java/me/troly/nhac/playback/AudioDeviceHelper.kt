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
    val isLossless: Boolean
)

object AudioDeviceHelper {

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
            
            val desc = when {
                isShanling -> {
                    "Thiết bị giải mã Shanling UP5 được kết nối qua USB. Toàn bộ dữ liệu âm thanh vượt qua bộ trộn Android (Android Mixer), được truyền tải hoàn hảo, không suy hao (Bit-Perfect) trực tiếp tới phần cứng giải mã Dual ES9219C DAC."
                }
                isTopping -> {
                    "Bộ giải mã Topping E30 được phát hiện kết nối qua USB. Ứng dụng kích hoạt chế độ xuất Bit-Perfect bỏ qua hoàn toàn bộ trộn hệ thống (Android Mixer), truyền tín hiệu số nguyên bản trực tiếp đến chip DAC AK4493, kết hợp với Pre Suca T5C (đã nâng cấp bóng Mullard 403b, tụ chất lượng cao và op-amp Muses02) cho chất âm đèn ấm áp, dải động cực rộng và nhạc tính đỉnh cao!"
                }
                else -> {
                    "Thiết bị giải mã USB DAC ngoài được kết nối trực tiếp. Ứng dụng kích hoạt chế độ xuất Bit-Perfect bỏ qua bộ trộn hệ thống (Android Mixer), giữ nguyên vẹn dải động và tần số lấy mẫu của bản nhạc gốc."
                }
            }
            
            val maxQuality = if (isTopping) {
                "32-bit / 768kHz & DSD512 (Lossless Bit-Perfect)"
            } else {
                "32-bit / 384kHz (Lossless Direct Output)"
            }

            return ActiveDeviceDetails(
                name = name,
                type = usbDevice.type,
                typeLabel = "USB DAC",
                techLabel = "USB Audio Class 2.0 (Direct Bit-Perfect)",
                maxQualityForecast = maxQuality,
                description = desc,
                isHiResCapable = true,
                isLossless = true
            )
        }

        val bluetoothDevice = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP 
        }
        if (bluetoothDevice != null) {
            val name = getCleanDeviceName(bluetoothDevice, "Bluetooth Audio Device")
            val isBuds2Pro = name.contains("Buds2 Pro", ignoreCase = true) || name.contains("Samsung", ignoreCase = true) && name.contains("Buds", ignoreCase = true)
            val isShanling = name.contains("Shanling", ignoreCase = true) || name.contains("UP5", ignoreCase = true)
            
            val desc = when {
                isBuds2Pro -> {
                    "Tai nghe Samsung Galaxy Buds 2 Pro đang kết nối. Để đạt chất lượng âm thanh 24-bit/96kHz (Seamless Codec - SSC), hãy đảm bảo ứng dụng phát nhạc gốc chất lượng cao và bật 'Seamless Codec' trong ứng dụng Galaxy Wearable."
                }
                isShanling -> {
                    "Thiết bị Shanling UP5 được kết nối qua Bluetooth. Hỗ trợ codec LDAC chuẩn Audiophile với tốc độ truyền lên tới 990kbps (24-bit/96kHz). Hãy chắc chắn bạn đã chọn 'Ưu tiên chất lượng âm thanh' trong Cài đặt Bluetooth để có trải nghiệm đỉnh cao."
                }
                else -> {
                    "Kết nối không dây Bluetooth. Chất lượng truyền dẫn phụ thuộc vào Codec đang hoạt động (LDAC: 24-bit/96kHz, aptX HD: 24-bit/48kHz, AAC/SBC: 16-bit/44.1kHz). Khuyên dùng LDAC hoặc aptX HD để nghe nhạc Hi-Res."
                }
            }

            return ActiveDeviceDetails(
                name = name,
                type = bluetoothDevice.type,
                typeLabel = "Bluetooth",
                techLabel = "Bluetooth Wireless Audio (A2DP Hi-Res)",
                maxQualityForecast = "24-bit / 96kHz (Compressed High-Resolution)",
                description = desc,
                isHiResCapable = true,
                isLossless = false
            )
        }

        val wiredDevice = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || 
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || 
            it.type == AudioDeviceInfo.TYPE_AUX_LINE 
        }
        if (wiredDevice != null) {
            val name = getCleanDeviceName(wiredDevice, "Wired Headphones")
            return ActiveDeviceDetails(
                name = name,
                type = wiredDevice.type,
                typeLabel = "Tai nghe dây",
                techLabel = "Cổng Analog 3.5mm (High-Definition)",
                maxQualityForecast = "24-bit / 192kHz (Lossless Analog Output)",
                description = "Kết nối dây truyền thống qua giắc 3.5mm hoặc đầu chuyển analog. Luồng tín hiệu âm thanh analog không bị nén, giữ nguyên độ trung thực cao, méo hài thấp và độ trễ bằng 0.",
                isHiResCapable = true,
                isLossless = true
            )
        }

        val hdmiDevice = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_HDMI || 
            it.type == AudioDeviceInfo.TYPE_HDMI_ARC || 
            it.type == AudioDeviceInfo.TYPE_HDMI_EARC 
        }
        if (hdmiDevice != null) {
            val name = getCleanDeviceName(hdmiDevice, "HDMI Digital Out")
            return ActiveDeviceDetails(
                name = name,
                type = hdmiDevice.type,
                typeLabel = "HDMI",
                techLabel = "HDMI Bitstream Digital Out",
                maxQualityForecast = "24-bit / 192kHz (Lossless Digital Multi-channel)",
                description = "Xuất tín hiệu số chất lượng cao qua cổng HDMI tới Receiver hoặc Soundbar. Đảm bảo âm thanh số được truyền đi nguyên bản không nén.",
                isHiResCapable = true,
                isLossless = true
            )
        }

        val speakerDevice = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || 
            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE 
        }
        if (speakerDevice != null) {
            return ActiveDeviceDetails(
                name = "Loa thiết bị (Built-in Speaker)",
                type = speakerDevice.type,
                typeLabel = "Loa ngoài",
                techLabel = "Loa tích hợp trên thiết bị",
                maxQualityForecast = "16-bit / 48kHz (Standard Audio)",
                description = "Phát qua loa ngoài tích hợp của điện thoại. Phù hợp nghe tạm thời, bị giới hạn về dải tần số, độ tách bạch âm thanh nổi (stereo width) và chất lượng dải trầm.",
                isHiResCapable = false,
                isLossless = true
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
            isLossless = true
        )
    }
}
