package me.troly.nhac.playback

import androidx.compose.ui.graphics.Color

data class AudioPathReport(
    val actualOutputFormat: String,
    val actualModulation: String,
    val isDsdConvertedToPcm: Boolean,
    val isUpsampled: Boolean,
    val dspEngineStatus: String,
    val ledColorHex: Long,
    val statusLabel: String,
    val statusDesc: String,
    val recommendation: String?
) {
    val ledColor: Color get() = Color(ledColorHex)
}

object AudioDecisionEngine {

    fun determineAudioPath(
        activeDevice: ActiveDeviceDetails,
        sourceSuffix: String?,
        sourceBitDepth: Int,
        sourceSamplingRate: Int,
        sourceBitRate: Int,
        selectedFilter: String,
        selectedDither: String,
        selectedModulator: String
    ): AudioPathReport {
        val suffix = sourceSuffix?.lowercase() ?: "flac"
        val isSourceDsd = suffix in setOf("dsf", "dff", "dsd")
        
        // Check if any DSP is chosen by user
        val isFilterEnabled = selectedFilter != "Bypass"
        val isDitherEnabled = selectedDither != "None"
        val isModulatorDsd = selectedModulator.contains("DSD")
        val userWantsDsp = isFilterEnabled || isDitherEnabled || isModulatorDsd

        // Device categories
        val isUsbDac = activeDevice.typeLabel == "USB DAC"
        val isBluetooth = activeDevice.typeLabel == "Bluetooth"
        val isSpeaker = activeDevice.typeLabel == "Loa ngoài"

        return when {
            isUsbDac -> {
                // USB DAC (Topping E30) -> Fully capable of everything!
                if (isSourceDsd) {
                    if (selectedModulator == "PCM (Bit-Perfect)") {
                        // User chose to convert DSD to PCM
                        AudioPathReport(
                            actualOutputFormat = "PCM 32-bit / 352.8 kHz",
                            actualModulation = "DSD to PCM Conversion",
                            isDsdConvertedToPcm = true,
                            isUpsampled = true,
                            dspEngineStatus = "Giải mã DSD $suffix dồn hạ sang PCM độ nét cao (Studio Master 352.8kHz). Chạy bộ lọc [$selectedFilter] và dither [$selectedDither].",
                            ledColorHex = 0xFFBB86FC, // Purple (DSP Enhanced)
                            statusLabel = "DSP Enhanced (DSD to PCM)",
                            statusDesc = "Tín hiệu DSD được chuyển đổi sang PCM 352.8kHz chất lượng Studio trước khi đưa vào DAC Topping E30. Chất âm mượt mà, dải tần kéo dài cực tốt.",
                            recommendation = "Mẹo: Bạn có thể chọn modulator 'DSD512' để upsample trực tiếp lên DSD nguyên bản, phát huy tối đa khả năng giải mã của chip AK4493 trên Topping E30."
                        )
                    } else {
                        // Keep as DSD upsampled or bit-perfect DSD
                        val dsdTarget = if (selectedModulator.contains("DSD512")) "DSD512" else "DSD64"
                        val dsdRate = if (dsdTarget == "DSD512") "22.58 MHz" else "2.82 MHz"
                        AudioPathReport(
                            actualOutputFormat = "$dsdTarget ($dsdRate / 1-bit)",
                            actualModulation = "Sigma-Delta Modulated (SDM)",
                            isDsdConvertedToPcm = false,
                            isUpsampled = selectedModulator.contains("DSD512") || isFilterEnabled,
                            dspEngineStatus = "Bộ giải mã SDM hoạt động: DSD $suffix gốc được upsample lên dòng siêu cao tần $dsdTarget thông qua bộ lọc [$selectedFilter].",
                            ledColorHex = 0xFF00E676, // Pristine Green
                            statusLabel = "Bit-Perfect $dsdTarget (Audiophile)",
                            statusDesc = "Đường truyền DSD tinh khiết! Tín hiệu truyền trực tiếp (Native DSD) tới DAC Topping E30 qua USB bỏ qua bộ trộn Android. Đi qua Pre Suca T5C đèn Mullard 403b đầy ấm áp và ngọt ngào.",
                            recommendation = "Cấu hình hoàn hảo cho Topping E30! Bạn đang được thưởng thức âm thanh analog chân thực nhất từ đĩa nguồn gốc."
                        )
                    }
                } else {
                    // Source is PCM (FLAC/MP3)
                    if (userWantsDsp) {
                        if (isModulatorDsd) {
                            // PCM to DSD upsampling!
                            val dsdTarget = if (selectedModulator.contains("DSD512")) "DSD512" else "DSD64"
                            val dsdRate = if (dsdTarget == "DSD512") "22.58 MHz" else "2.82 MHz"
                            AudioPathReport(
                                actualOutputFormat = "$dsdTarget ($dsdRate / 1-bit)",
                                actualModulation = "PCM to SDM Conversion",
                                isDsdConvertedToPcm = false,
                                isUpsampled = true,
                                dspEngineStatus = "Nâng mẫu kỹ thuật số: PCM [$selectedFilter] ➔ Dither [$selectedDither] ➔ Điều chế Sigma-Delta [$dsdTarget].",
                                ledColorHex = 0xFFBB86FC, // Purple (DSP Enhanced)
                                statusLabel = "DSP Enhanced ($dsdTarget)",
                                statusDesc = "Tín hiệu PCM gốc được tái cấu trúc thành dòng siêu cao tần 1-bit mô phỏng HQPlayer-grade trước khi xuất ra phần cứng Topping E30. Giảm méo pha tối đa, nhạc tính tuyệt vời.",
                                recommendation = "Cấu hình upsampling cao cấp nhất! Phù hợp nhất cho nhạc trữ tình, Vocal và nhạc cụ mộc mạc phát qua Pre Suca T5C bóng Mullard."
                            )
                        } else {
                            // PCM to PCM upsampling
                            val targetRate = if (selectedFilter == "poly-sinc-xtr-lp") "768.0 kHz" else "384.0 kHz"
                            AudioPathReport(
                                actualOutputFormat = "PCM 32-bit / $targetRate",
                                actualModulation = "PCM Upsampled",
                                isDsdConvertedToPcm = false,
                                isUpsampled = true,
                                dspEngineStatus = "Upsampling kỹ thuật số bằng bộ lọc [$selectedFilter] kết hợp Dither [$selectedDither] lên tần số lấy mẫu $targetRate.",
                                ledColorHex = 0xFFBB86FC, // Purple (DSP Enhanced)
                                statusLabel = "DSP Enhanced (PCM upscaled)",
                                statusDesc = "Nội suy tăng mẫu tuyến tính dốc đứng giúp đẩy nhiễu lượng tử lên dải siêu âm, tái tạo không gian sân khấu rộng mở, nhạc tính xuất sắc trên Topping E30.",
                                recommendation = "Khuyên dùng bộ lọc 'poly-sinc-xtr-lp' kết hợp dither 'LNS15' để có dải động mượt mà và nền âm tĩnh nhất."
                            )
                        }
                    } else {
                        // PCM direct bit-perfect
                        val res = if (sourceBitDepth > 0 && sourceSamplingRate > 0) {
                            "PCM ${sourceBitDepth}-bit / ${sourceSamplingRate / 1000.0} kHz"
                        } else {
                            "PCM 16-bit / 44.1 kHz"
                        }
                        AudioPathReport(
                            actualOutputFormat = res,
                            actualModulation = "Bit-Perfect Direct Out",
                            isDsdConvertedToPcm = false,
                            isUpsampled = false,
                            dspEngineStatus = "Tắt mọi bộ lọc xử lý. Tín hiệu được truyền nguyên vẹn dải động và tần số gốc của tệp nhạc.",
                            ledColorHex = 0xFF00E676, // Pristine Green
                            statusLabel = "Bit-Perfect Lossless (Trực tiếp)",
                            statusDesc = "Bỏ qua hoàn toàn bộ trộn hệ thống (Android Mixer). Dữ liệu nhạc gốc được truyền chính xác từng bit tới chip DAC AK4493 của Topping E30 thông qua driver USB Audio Class 2.0.",
                            recommendation = "Trải nghiệm mộc mạc nguyên bản. Bạn có thể bật bộ lọc 'poly-sinc-xtr-lp' ở Tab DSP để so sánh dải trung mượt mà hơn."
                        )
                    }
                }
            }

            isBluetooth -> {
                // Bluetooth connected (e.g. Galaxy Buds 2 Pro or Shanling UP5 via BT)
                // Bluetooth hard limit is 24-bit/96kHz (LDAC/SSC). Modulator DSD cannot be transmitted.
                if (isSourceDsd) {
                    AudioPathReport(
                        actualOutputFormat = "PCM 24-bit / 96.0 kHz (Capped)",
                        actualModulation = "DSD to PCM (Bluetooth optimized)",
                        isDsdConvertedToPcm = true,
                        isUpsampled = true,
                        dspEngineStatus = "Bluetooth không hỗ trợ DSD. Hệ thống tự động chuyển đổi DSD sang PCM 24-bit/96kHz để truyền tải qua LDAC/SSC.",
                        ledColorHex = 0xFF29B6F6, // Blue (HD Wireless)
                        statusLabel = "HD Wireless (DSD Converted)",
                        statusDesc = "Đang truyền phát không dây qua codec chất lượng cao (LDAC/SSC). Tệp DSD gốc đã được giải nén và chuyển hóa thành PCM 24-bit/96kHz phù hợp băng thông Bluetooth.",
                        recommendation = "Hãy kết nối USB DAC (Topping E30) để giải mã DSD nguyên bản. Trên Bluetooth, cấu hình này là tối ưu nhất."
                    )
                } else {
                    // PCM source on Bluetooth
                    if (userWantsDsp) {
                        // Warn or adjust if user selected DSD Modulators
                        val finalFormat = if (isModulatorDsd) "PCM 24-bit / 96.0 kHz (Capped)" else "PCM 24-bit / 96.0 kHz"
                        val finalMod = if (isModulatorDsd) "PCM (SDM Disabled for BT)" else "PCM Upsampled"
                        val statusLabelStr = if (isModulatorDsd) "HD Wireless (SDM Capped)" else "DSP Enhanced Wireless"
                        val finalStatusDesc = if (isModulatorDsd) {
                            "Bộ điều chế DSD bị vô hiệu hóa vì Bluetooth không thể truyền tải dòng DSD. Hệ thống chuyển sang upsample PCM 24-bit/96kHz thông qua bộ lọc [$selectedFilter] để tối ưu dải động."
                        } else {
                            "Tín hiệu được upsample đồng bộ lên 96kHz qua bộ lọc [$selectedFilter] phù hợp tần số lấy mẫu đỉnh của Bluetooth LDAC."
                        }
                        
                        AudioPathReport(
                            actualOutputFormat = finalFormat,
                            actualModulation = finalMod,
                            isDsdConvertedToPcm = false,
                            isUpsampled = true,
                            dspEngineStatus = "Nội suy tần số lấy mẫu lên 24-bit/96kHz đồng bộ với codec truyền không dây Bluetooth. Bỏ qua DSD Modulator.",
                            ledColorHex = 0xFFBB86FC, // Purple (DSP Enhanced)
                            statusLabel = statusLabelStr,
                            statusDesc = finalStatusDesc,
                            recommendation = "Cấu hình lý tưởng cho Bluetooth LDAC! Giúp loại bỏ hiện tượng méo tiếng do bộ phát Bluetooth nén dải động."
                        )
                    } else {
                        // Bypassed PCM on Bluetooth
                        val res = if (sourceBitDepth > 0 && sourceSamplingRate > 0) {
                            "PCM ${sourceBitDepth}-bit / ${sourceSamplingRate / 1000.0} kHz"
                        } else {
                            "PCM 16-bit / 44.1 kHz"
                        }
                        AudioPathReport(
                            actualOutputFormat = res,
                            actualModulation = "Standard BT Broadcast",
                            isDsdConvertedToPcm = false,
                            isUpsampled = false,
                            dspEngineStatus = "Tắt xử lý số. Tín hiệu gốc truyền trực tiếp sang bộ mã hóa (encoder) Bluetooth.",
                            ledColorHex = 0xFF29B6F6, // Blue (HD Wireless)
                            statusLabel = "HD Wireless (LDAC/SSC)",
                            statusDesc = "Đang truyền tải không dây chất lượng cao. Khuyên dùng Samsung Seamless Codec (cho Buds 2 Pro trên Fold 5) hoặc LDAC để giữ dải động 24-bit mượt mà.",
                            recommendation = "Mẹo: Bạn có thể bật bộ lọc 'sinc-L' và dither 'Gauss' ở Tab DSP để làm mượt dải cao trên tai nghe không dây."
                        )
                    }
                }
            }

            else -> {
                // Built-in Speaker / Standard Output (e.g. Galaxy Z Fold 5 internal speaker or basic adapter)
                // Hardware cap is 16-bit/48kHz. High-rate DSP or DSD is completely wasted and drains battery.
                val cappedFormat = "PCM 16-bit / 48.0 kHz (Capped)"
                
                if (isSourceDsd) {
                    AudioPathReport(
                        actualOutputFormat = cappedFormat,
                        actualModulation = "DSD to PCM (Speaker Downgrade)",
                        isDsdConvertedToPcm = true,
                        isUpsampled = false,
                        dspEngineStatus = "Tự động tắt bộ lọc DSP & bộ điều chế SDM để tiết kiệm pin trên loa thiết bị.",
                        ledColorHex = 0xFFFFB300, // Amber (Standard)
                        statusLabel = "Loa ngoài (DSD Converted)",
                        statusDesc = "Tệp DSD gốc được tự động chuyển đổi và hạ mẫu về PCM 16-bit/48kHz tiêu chuẩn để phát ra loa ngoài của thiết bị.",
                        recommendation = "Hãy cắm tai nghe hoặc kết nối DAC Topping E30 để giải mã nguyên bản DSD và trải nghiệm hệ thống Suca T5C bóng Mullard."
                    )
                } else {
                    if (userWantsDsp) {
                        AudioPathReport(
                            actualOutputFormat = cappedFormat,
                            actualModulation = "PCM (DSP Disabled for Speaker)",
                            isDsdConvertedToPcm = false,
                            isUpsampled = false,
                            dspEngineStatus = "Bộ lọc và điều chế bị tạm dừng. Loa ngoài không đủ dải tần để thể hiện hiệu quả của bộ lọc âm học.",
                            ledColorHex = 0xFFFFB300, // Amber (Standard)
                            statusLabel = "Loa ngoài (DSP Bypass)",
                            statusDesc = "Hệ thống tự động tạm dừng bộ lọc upsampling để tiết kiệm pin và tránh nóng máy do loa ngoài không hỗ trợ tần số lấy mẫu cao.",
                            recommendation = "Để trải nghiệm bộ lọc HQPlayer cao cấp, hãy kết nối thiết bị với tai nghe dây hoặc bộ giải mã Topping E30."
                        )
                    } else {
                        AudioPathReport(
                            actualOutputFormat = "PCM 16-bit / 48.0 kHz",
                            actualModulation = "Android AudioTrack System",
                            isDsdConvertedToPcm = false,
                            isUpsampled = false,
                            dspEngineStatus = "Âm thanh được định tuyến qua bộ trộn âm tiêu chuẩn của hệ điều hành Android (Audio Flinger Mixer).",
                            ledColorHex = 0xFFFFB300, // Amber (Standard)
                            statusLabel = "Chất lượng tiêu chuẩn (Standard)",
                            statusDesc = "Đang phát qua loa ngoài tích hợp. Trên Galaxy Z Fold 5, âm thanh được tinh chỉnh bởi AKG có hỗ trợ giả lập Dolby Atmos.",
                            recommendation = "Mẹo: Mở màn hình gập của Galaxy Z Fold 5 để tối ưu hóa không gian stereo kép tinh chỉnh bởi AKG!"
                        )
                    }
                }
            }
        }
    }
}
