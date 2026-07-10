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

        val dacName = activeDevice.name
        val isTopping = dacName.contains("Topping", ignoreCase = true) || dacName.contains("E30", ignoreCase = true)

        val maxRate = activeDevice.maxSampleRate
        val maxBits = activeDevice.maxBitDepth

        return when {
            isUsbDac -> {
                // USB DAC -> Capability-driven upsampling and bit-perfect playback!
                if (isSourceDsd) {
                    if (selectedModulator == "PCM (Bit-Perfect)") {
                        // User chose to convert DSD to PCM
                        val targetRateHz = if (maxRate >= 352800) 352800 else maxRate
                        val targetRateStr = "${targetRateHz / 1000.0} kHz"
                        AudioPathReport(
                            actualOutputFormat = "PCM $maxBits-bit / $targetRateStr",
                            actualModulation = "DSD to PCM Conversion",
                            isDsdConvertedToPcm = true,
                            isUpsampled = true,
                            dspEngineStatus = "Giải mã DSD $suffix dồn hạ sang PCM độ nét cao (Studio Master $targetRateStr). Chạy bộ lọc [$selectedFilter] và dither [$selectedDither].",
                            ledColorHex = 0xFFBB86FC, // Purple (DSP Enhanced)
                            statusLabel = "DSP Enhanced (DSD to PCM)",
                            statusDesc = "Tín hiệu DSD được chuyển đổi sang PCM $targetRateStr chất lượng Studio trước khi đưa vào DAC [$dacName] qua USB.",
                            recommendation = if (isTopping) {
                                "Mẹo: Bạn có thể chọn modulator 'DSD512' để upsample trực tiếp lên DSD nguyên bản, phát huy tối đa khả năng giải mã của chip AK4493 trên Topping E30."
                            } else {
                                "Mẹo: Bạn có thể chọn modulator DSD phù hợp để trải nghiệm điều chế Sigma-Delta trực tiếp nếu thiết bị giải mã hỗ trợ Native DSD."
                            }
                        )
                    } else {
                        // Keep as DSD upsampled or bit-perfect DSD
                        // Cap DSD speed based on DAC's max hardware sample rate (DSD512 requires ~705.6kHz capability)
                        val dsdTarget = if (selectedModulator.contains("DSD512") && maxRate >= 705600) "DSD512" else "DSD64"
                        val dsdRate = if (dsdTarget == "DSD512") "22.58 MHz" else "2.82 MHz"
                        
                        val statusDescStr = if (isTopping) {
                            "Đường truyền DSD tinh khiết! Tín hiệu truyền trực tiếp (Native DSD) tới DAC Topping E30 qua USB bỏ qua bộ trộn Android. Đi qua Pre Suca T5C đèn Mullard 403b đầy ấm áp và ngọt ngào."
                        } else {
                            "Đường truyền DSD tinh khiết! Tín hiệu truyền trực tiếp (Native DSD) tới DAC [$dacName] qua USB bỏ qua bộ trộn Android hệ thống."
                        }

                        AudioPathReport(
                            actualOutputFormat = "$dsdTarget ($dsdRate / 1-bit)",
                            actualModulation = "Sigma-Delta Modulated (SDM)",
                            isDsdConvertedToPcm = false,
                            isUpsampled = selectedModulator.contains("DSD512") || isFilterEnabled,
                            dspEngineStatus = "Bộ giải mã SDM hoạt động: DSD $suffix gốc được upsample lên dòng siêu cao tần $dsdTarget thông qua bộ lọc [$selectedFilter].",
                            ledColorHex = 0xFF00E676, // Pristine Green
                            statusLabel = "Bit-Perfect $dsdTarget (Audiophile)",
                            statusDesc = statusDescStr,
                            recommendation = if (isTopping) "Cấu hình hoàn hảo cho Topping E30! Bạn đang được thưởng thức âm thanh analog chân thực nhất từ đĩa nguồn gốc." else "Đã tối ưu hóa cấu hình cho phần cứng [$dacName] của bạn."
                        )
                    }
                } else {
                    // Source is PCM (FLAC/MP3)
                    if (userWantsDsp) {
                        if (isModulatorDsd) {
                            // PCM to DSD upsampling! Cap at DSD64 if hardware sample rate is lower
                            val dsdTarget = if (selectedModulator.contains("DSD512") && maxRate >= 705600) "DSD512" else "DSD64"
                            val dsdRate = if (dsdTarget == "DSD512") "22.58 MHz" else "2.82 MHz"
                            
                            val statusDescStr = if (isTopping) {
                                "Tín hiệu PCM gốc được tái cấu trúc thành dòng siêu cao tần 1-bit mô phỏng HQPlayer-grade trước khi xuất ra phần cứng Topping E30. Giảm méo pha tối đa, nhạc tính tuyệt vời."
                            } else {
                                "Tín hiệu PCM gốc được tái cấu trúc thành dòng siêu cao tần 1-bit mô phỏng HQPlayer-grade trước khi xuất ra phần cứng [$dacName]. Giảm méo pha tối đa, loại bỏ răng cưa tần số."
                            }

                            val recommendationStr = if (isTopping) {
                                "Cấu hình upsampling cao cấp nhất! Phù hợp nhất cho nhạc trữ tình, Vocal và nhạc cụ mộc mạc phát qua Pre Suca T5C bóng Mullard."
                            } else {
                                "Cấu hình upsampling Sigma-Delta cao cấp mang đến chất âm mượt mà, loại bỏ gắt dải cao trên DAC [$dacName]."
                            }

                            AudioPathReport(
                                actualOutputFormat = "$dsdTarget ($dsdRate / 1-bit)",
                                actualModulation = "PCM to SDM Conversion",
                                isDsdConvertedToPcm = false,
                                isUpsampled = true,
                                dspEngineStatus = "Nâng mẫu kỹ thuật số: PCM [$selectedFilter] ➔ Dither [$selectedDither] ➔ Điều chế Sigma-Delta [$dsdTarget].",
                                ledColorHex = 0xFFBB86FC, // Purple (DSP Enhanced)
                                statusLabel = "DSP Enhanced ($dsdTarget)",
                                statusDesc = statusDescStr,
                                recommendation = recommendationStr
                            )
                        } else {
                            // PCM to PCM upsampling
                            // Dynamically select target upsampling rate capped by hardware max rate
                            val targetRateHz = if (selectedFilter == "poly-sinc-xtr-lp") maxRate else (if (maxRate >= 384000) 384000 else maxRate)
                            val targetRateStr = "${targetRateHz / 1000.0} kHz"
                            AudioPathReport(
                                actualOutputFormat = "PCM $maxBits-bit / $targetRateStr",
                                actualModulation = "PCM Upsampled",
                                isDsdConvertedToPcm = false,
                                isUpsampled = true,
                                dspEngineStatus = "Upsampling kỹ thuật số bằng bộ lọc [$selectedFilter] kết hợp Dither [$selectedDither] lên tần số lấy mẫu $targetRateStr.",
                                ledColorHex = 0xFFBB86FC, // Purple (DSP Enhanced)
                                statusLabel = "DSP Enhanced (PCM upscaled)",
                                statusDesc = "Nội suy tăng mẫu tuyến tính dốc đứng giúp đẩy nhiễu lượng tử lên dải siêu âm, tái tạo không gian sân khấu rộng mở, nhạc tính xuất sắc trên DAC [$dacName].",
                                recommendation = "Khuyên dùng bộ lọc 'poly-sinc-xtr-lp' kết hợp dither 'LNS15' để có dải động mượt mà và nền âm tĩnh nhất."
                            )
                        }
                    } else {
                        // PCM direct bit-perfect
                        val streamBitDepth = if (sourceBitDepth > 0) sourceBitDepth else 16
                        val streamSampleRate = if (sourceSamplingRate > 0) sourceSamplingRate else 44100
                        val targetBits = if (streamBitDepth > maxBits) maxBits else streamBitDepth
                        val targetRateHz = if (streamSampleRate > maxRate) maxRate else streamSampleRate

                        AudioPathReport(
                            actualOutputFormat = "PCM $targetBits-bit / ${targetRateHz / 1000.0} kHz",
                            actualModulation = "Bit-Perfect Direct Out",
                            isDsdConvertedToPcm = false,
                            isUpsampled = false,
                            dspEngineStatus = "Tắt mọi bộ lọc xử lý. Tín hiệu được truyền nguyên vẹn dải động và tần số gốc của tệp nhạc.",
                            ledColorHex = 0xFF00E676, // Pristine Green
                            statusLabel = "Bit-Perfect Lossless (Trực tiếp)",
                            statusDesc = if (isTopping) {
                                "Bỏ qua hoàn toàn bộ trộn hệ thống (Android Mixer). Dữ liệu nhạc gốc được truyền chính xác từng bit tới chip DAC AK4493 của Topping E30 thông qua driver USB Audio Class 2.0."
                            } else {
                                "Bỏ qua hoàn toàn bộ trộn hệ thống (Android Mixer). Dữ liệu nhạc gốc được truyền chính xác từng bit tới chip DAC của [$dacName] thông qua driver USB Audio Class 2.0 phát huy tối đa thông số phần cứng."
                            },
                            recommendation = "Trải nghiệm mộc mạc nguyên bản. Bạn có thể bật bộ lọc 'poly-sinc-xtr-lp' ở Tab DSP để so sánh dải trung mượt mà hơn."
                        )
                    }
                }
            }

            isBluetooth -> {
                // Bluetooth connected
                val finalMaxRate = if (maxRate > 96000) 96000 else maxRate
                val finalMaxBits = if (maxBits > 24) 24 else maxBits
                val finalRateStr = "${finalMaxRate / 1000.0} kHz"

                if (isSourceDsd) {
                    AudioPathReport(
                        actualOutputFormat = "PCM $finalMaxBits-bit / $finalRateStr (Capped)",
                        actualModulation = "DSD to PCM (Bluetooth optimized)",
                        isDsdConvertedToPcm = true,
                        isUpsampled = true,
                        dspEngineStatus = "Bluetooth không hỗ trợ DSD. Hệ thống tự động chuyển đổi DSD sang PCM $finalMaxBits-bit/$finalRateStr để truyền tải qua LDAC/SSC.",
                        ledColorHex = 0xFF29B6F6, // Blue (HD Wireless)
                        statusLabel = "HD Wireless (DSD Converted)",
                        statusDesc = "Đang truyền phát không dây qua codec chất lượng cao (LDAC/SSC). Tệp DSD gốc đã được giải nén và chuyển hóa thành PCM $finalMaxBits-bit/$finalRateStr phù hợp băng thông Bluetooth.",
                        recommendation = "Hãy kết nối USB DAC để giải mã nguyên bản DSD. Trên Bluetooth, cấu hình này là tối ưu nhất."
                    )
                } else {
                    // PCM source on Bluetooth
                    if (userWantsDsp) {
                        val finalFormat = if (isModulatorDsd) "PCM $finalMaxBits-bit / $finalRateStr (Capped)" else "PCM $finalMaxBits-bit / $finalRateStr"
                        val finalMod = if (isModulatorDsd) "PCM (SDM Disabled for BT)" else "PCM Upsampled"
                        val statusLabelStr = if (isModulatorDsd) "HD Wireless (SDM Capped)" else "DSP Enhanced Wireless"
                        val finalStatusDesc = if (isModulatorDsd) {
                            "Bộ điều chế DSD bị vô hiệu hóa vì Bluetooth không thể truyền tải dòng DSD. Hệ thống chuyển sang upsample PCM $finalMaxBits-bit/$finalRateStr thông qua bộ lọc [$selectedFilter] để tối ưu dải động."
                        } else {
                            "Tín hiệu được upsample đồng bộ lên $finalRateStr qua bộ lọc [$selectedFilter] phù hợp tần số lấy mẫu đỉnh của Bluetooth LDAC/SSC."
                        }
                        
                        AudioPathReport(
                            actualOutputFormat = finalFormat,
                            actualModulation = finalMod,
                            isDsdConvertedToPcm = false,
                            isUpsampled = true,
                            dspEngineStatus = "Nội suy tần số lấy mẫu lên $finalMaxBits-bit/$finalRateStr đồng bộ với codec truyền không dây Bluetooth. Bỏ qua DSD Modulator.",
                            ledColorHex = 0xFFBB86FC, // Purple (DSP Enhanced)
                            statusLabel = statusLabelStr,
                            statusDesc = finalStatusDesc,
                            recommendation = "Cấu hình lý tưởng cho Bluetooth LDAC/SSC! Giúp loại bỏ hiện tượng méo tiếng do bộ phát Bluetooth nén dải động."
                        )
                    } else {
                        // Bypassed PCM on Bluetooth
                        val streamBitDepth = if (sourceBitDepth > 0) sourceBitDepth else 16
                        val streamSampleRate = if (sourceSamplingRate > 0) sourceSamplingRate else 44100
                        val targetBits = if (streamBitDepth > finalMaxBits) finalMaxBits else streamBitDepth
                        val targetRateHz = if (streamSampleRate > finalMaxRate) finalMaxRate else streamSampleRate

                        AudioPathReport(
                            actualOutputFormat = "PCM $targetBits-bit / ${targetRateHz / 1000.0} kHz",
                            actualModulation = "Standard BT Broadcast",
                            isDsdConvertedToPcm = false,
                            isUpsampled = false,
                            dspEngineStatus = "Tắt xử lý số. Tín hiệu gốc truyền trực tiếp sang bộ mã hóa (encoder) Bluetooth.",
                            ledColorHex = 0xFF29B6F6, // Blue (HD Wireless)
                            statusLabel = "HD Wireless (LDAC/SSC)",
                            statusDesc = "Đang truyền tải không dây chất lượng cao. Khuyên dùng Samsung Seamless Codec (SSC) hoặc LDAC để giữ dải động 24-bit mượt mà.",
                            recommendation = "Mẹo: Bạn có thể bật bộ lọc 'sinc-L' và dither 'Gauss' ở Tab DSP để làm mượt dải cao trên tai nghe không dây."
                        )
                    }
                }
            }

            else -> {
                // Built-in Speaker / Wired Aux / HDMI
                // Dynamically respect maxRate and maxBits of the hardware device
                val targetBits = if (maxBits > 24) 24 else maxBits // cap at 24-bit unless USB
                val targetRateHz = if (maxRate > 192000) 192000 else maxRate // cap at 192kHz unless USB
                val finalRateStr = "${targetRateHz / 1000.0} kHz"
                val cappedFormat = "PCM $targetBits-bit / $finalRateStr"
                
                if (isSourceDsd) {
                    AudioPathReport(
                        actualOutputFormat = cappedFormat,
                        actualModulation = "DSD to PCM (Hardware Downgrade)",
                        isDsdConvertedToPcm = true,
                        isUpsampled = false,
                        dspEngineStatus = "Tự động tắt bộ lọc DSP & bộ điều chế SDM để tiết kiệm pin trên loa ngoài/cổng ra analog chuẩn.",
                        ledColorHex = 0xFFFFB300, // Amber (Standard)
                        statusLabel = "${activeDevice.typeLabel} (DSD Converted)",
                        statusDesc = "Tệp DSD gốc được tự động chuyển đổi và hạ mẫu về $cappedFormat tiêu chuẩn để phù hợp với giới hạn phần cứng [$dacName].",
                        recommendation = "Hãy kết nối USB DAC ngoài để trải nghiệm giải mã nguyên bản DSD tối ưu nhất."
                    )
                } else {
                    if (userWantsDsp) {
                        AudioPathReport(
                            actualOutputFormat = cappedFormat,
                            actualModulation = "PCM (DSP Disabled for Speaker/Aux)",
                            isDsdConvertedToPcm = false,
                            isUpsampled = false,
                            dspEngineStatus = "Bộ lọc và điều chế bị tạm dừng. Hệ thống không sử dụng bộ lọc upsampling để tiết kiệm pin và tài nguyên.",
                            ledColorHex = 0xFFFFB300, // Amber (Standard)
                            statusLabel = "${activeDevice.typeLabel} (DSP Bypass)",
                            statusDesc = "Hệ thống tự động tạm dừng bộ lọc upsampling để tránh nóng máy và tiết kiệm pin do cổng âm thanh hiện tại không yêu cầu nội suy siêu cao tần.",
                            recommendation = "Để trải nghiệm bộ lọc HQPlayer cao cấp nhất, hãy kết nối thiết bị với bộ giải mã USB DAC chuyên dụng."
                        )
                    } else {
                        val streamBitDepth = if (sourceBitDepth > 0) sourceBitDepth else 16
                        val streamSampleRate = if (sourceSamplingRate > 0) sourceSamplingRate else 44100
                        val finalBits = if (streamBitDepth > targetBits) targetBits else streamBitDepth
                        val finalRateHz = if (streamSampleRate > targetRateHz) targetRateHz else streamSampleRate

                        val manufacturer = android.os.Build.MANUFACTURER ?: ""
                        val isSamsungFold = dacName.contains("fold", ignoreCase = true)
                        
                        AudioPathReport(
                            actualOutputFormat = "PCM $finalBits-bit / ${finalRateHz / 1000.0} kHz",
                            actualModulation = "Android AudioTrack System",
                            isDsdConvertedToPcm = false,
                            isUpsampled = false,
                            dspEngineStatus = "Âm thanh được định tuyến qua luồng âm thanh hệ điều hành Android (Audio Flinger Mixer).",
                            ledColorHex = 0xFFFFB300, // Amber (Standard)
                            statusLabel = "Chất lượng tiêu chuẩn (Standard)",
                            statusDesc = if (isSamsungFold) {
                                "Đang phát qua loa ngoài tích hợp. Trên Galaxy Z Fold 5, âm thanh được tinh chỉnh bởi AKG có hỗ trợ giả lập Dolby Atmos."
                            } else {
                                "Đang phát qua thiết bị đầu ra [$dacName] sử dụng cấu hình phân bổ hệ thống."
                            },
                            recommendation = if (isSamsungFold) "Mẹo: Mở màn hình gập của Galaxy Z Fold 5 để tối ưu hóa không gian stereo kép tinh chỉnh bởi AKG!" else "Mẹo: Kết nối USB DAC ngoài để kích hoạt chế độ Bit-Perfect lossless cao cấp!"
                        )
                    }
                }
            }
        }
    }
}
