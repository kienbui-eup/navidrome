import Foundation
import SwiftUI

public struct AudioPathReport: Equatable {
    public let actualOutputFormat: String
    public let actualModulation: String
    public let isDsdConvertedToPcm: Bool
    public let isUpsampled: Bool
    public let dspEngineStatus: String
    public let ledColorHex: UInt32
    public let statusLabel: String
    public let statusDesc: String
    public let recommendation: String?
    
    public var ledColor: Color {
        return Color(hex: ledColorHex)
    }
}

// Extension to allow initialization of Color from Hex UInt32
extension Color {
    init(hex: UInt32) {
        let red = Double((hex >> 16) & 0xFF) / 255.0
        let green = Double((hex >> 8) & 0xFF) / 255.0
        let blue = Double(hex & 0xFF) / 255.0
        self.init(red: red, green: green, blue: blue)
    }
}

public class AudioDecisionEngine {
    
    public static func determineAudioPath(
        activeDevice: ActiveDeviceDetails,
        sourceSuffix: String?,
        sourceBitDepth: Int,
        sourceSamplingRate: Int,
        sourceBitRate: Int,
        selectedFilter: String,
        selectedDither: String,
        selectedModulator: String
    ) -> AudioPathReport {
        let suffix = sourceSuffix?.lowercased() ?? "flac"
        let isSourceDsd = suffix == "dsf" || suffix == "dff" || suffix == "dsd"
        
        let isFilterEnabled = selectedFilter != "Bypass"
        let isDitherEnabled = selectedDither != "None"
        let isModulatorDsd = selectedModulator.contains("DSD")
        let userWantsDsp = isFilterEnabled || isDitherEnabled || isModulatorDsd
        
        let isUsbDac = activeDevice.typeLabel == "USB DAC"
        let isBluetooth = activeDevice.typeLabel == "Bluetooth"
        
        if isUsbDac {
            if isSourceDsd {
                if selectedModulator == "PCM (Bit-Perfect)" {
                    return AudioPathReport(
                        actualOutputFormat: "PCM 32-bit / 352.8 kHz",
                        actualModulation: "DSD to PCM Conversion",
                        isDsdConvertedToPcm: true,
                        isUpsampled: true,
                        dspEngineStatus: "Giải mã DSD \(suffix.uppercased()) dồn hạ sang PCM độ nét cao (Studio Master 352.8kHz). Chạy bộ lọc [\(selectedFilter)] và dither [\(selectedDither)].",
                        ledColorHex: 0xBB86FC, // Purple (DSP Enhanced)
                        statusLabel: "DSP Enhanced (DSD to PCM)",
                        statusDesc: "Tín hiệu DSD được chuyển đổi sang PCM 352.8kHz chất lượng Studio trước khi đưa vào DAC Topping E30. Chất âm mượt mà, dải tần kéo dài cực tốt.",
                        recommendation: "Mẹo: Bạn có thể chọn modulator 'DSD512' để upsample trực tiếp lên DSD nguyên bản, phát huy tối đa khả năng giải mã của chip AK4493 trên Topping E30."
                    )
                } else {
                    let dsdTarget = selectedModulator.contains("DSD512") ? "DSD512" : "DSD64"
                    let dsdRate = dsdTarget == "DSD512" ? "22.58 MHz" : "2.82 MHz"
                    return AudioPathReport(
                        actualOutputFormat: "\(dsdTarget) (\(dsdRate) / 1-bit)",
                        actualModulation: "Sigma-Delta Modulated (SDM)",
                        isDsdConvertedToPcm: false,
                        isUpsampled: selectedModulator.contains("DSD512") || isFilterEnabled,
                        dspEngineStatus: "Bộ giải mã SDM hoạt động: DSD \(suffix.uppercased()) gốc được upsample lên dòng siêu cao tần \(dsdTarget) thông qua bộ lọc [\(selectedFilter)].",
                        ledColorHex: 0x00E676, // Pristine Green
                        statusLabel: "Bit-Perfect \(dsdTarget) (Audiophile)",
                        statusDesc: "Đường truyền DSD tinh khiết! Tín hiệu truyền trực tiếp (Native DSD) tới DAC Topping E30 qua USB bỏ qua bộ trộn iOS. Đi qua Pre Suca T5C đèn Mullard 403b đầy ấm áp và ngọt ngào.",
                        recommendation: "Cấu hình hoàn hảo cho Topping E30! Bạn đang được thưởng thức âm thanh analog chân thực nhất từ đĩa nguồn gốc."
                    )
                }
            } else {
                // Source is PCM (FLAC/MP3)
                if userWantsDsp {
                    if isModulatorDsd {
                        let dsdTarget = selectedModulator.contains("DSD512") ? "DSD512" : "DSD64"
                        let dsdRate = dsdTarget == "DSD512" ? "22.58 MHz" : "2.82 MHz"
                        return AudioPathReport(
                            actualOutputFormat: "\(dsdTarget) (\(dsdRate) / 1-bit)",
                            actualModulation: "PCM to SDM Conversion",
                            isDsdConvertedToPcm: false,
                            isUpsampled: true,
                            dspEngineStatus: "Nâng mẫu kỹ thuật số: PCM [\(selectedFilter)] ➔ Dither [\(selectedDither)] ➔ Điều chế Sigma-Delta [\(dsdTarget)].",
                            ledColorHex: 0xBB86FC, // Purple (DSP Enhanced)
                            statusLabel: "DSP Enhanced (\(dsdTarget))",
                            statusDesc: "Tín hiệu PCM gốc được tái cấu trúc thành dòng siêu cao tần 1-bit mô phỏng HQPlayer-grade trước khi xuất ra phần cứng Topping E30. Giảm méo pha tối đa, nhạc tính tuyệt vời.",
                            recommendation: "Cấu hình upsampling cao cấp nhất! Phù hợp nhất cho nhạc trữ tình, Vocal và nhạc cụ mộc mạc phát qua Pre Suca T5C bóng Mullard."
                        )
                    } else {
                        let targetRate = selectedFilter == "poly-sinc-xtr-lp" ? "768.0 kHz" : "384.0 kHz"
                        return AudioPathReport(
                            actualOutputFormat: "PCM 32-bit / \(targetRate)",
                            actualModulation: "PCM Upsampled",
                            isDsdConvertedToPcm: false,
                            isUpsampled: true,
                            dspEngineStatus: "Upsampling kỹ thuật số bằng bộ lọc [\(selectedFilter)] kết hợp Dither [\(selectedDither)] lên tần số lấy mẫu \(targetRate).",
                            ledColorHex: 0xBB86FC, // Purple (DSP Enhanced)
                            statusLabel: "DSP Enhanced (PCM upscaled)",
                            statusDesc: "Nội suy tăng mẫu tuyến tính dốc đứng giúp đẩy nhiễu lượng tử lên dải siêu âm, tái tạo không gian sân khấu rộng mở, nhạc tính xuất sắc trên Topping E30.",
                            recommendation: "Khuyên dùng bộ lọc 'poly-sinc-xtr-lp' kết hợp dither 'LNS15' để có dải động mượt mà và nền âm tĩnh nhất."
                        )
                    }
                } else {
                    let res = sourceBitDepth > 0 && sourceSamplingRate > 0 ?
                        "PCM \(sourceBitDepth)-bit / \(Double(sourceSamplingRate) / 1000.0) kHz" :
                        "PCM 16-bit / 44.1 kHz"
                    return AudioPathReport(
                        actualOutputFormat: res,
                        actualModulation: "Bit-Perfect Direct Out",
                        isDsdConvertedToPcm: false,
                        isUpsampled: false,
                        dspEngineStatus: "Tắt mọi bộ lọc xử lý. Tín hiệu được truyền nguyên vẹn dải động và tần số gốc của tệp nhạc.",
                        ledColorHex: 0x00E676, // Pristine Green
                        statusLabel: "Bit-Perfect Lossless (Trực tiếp)",
                        statusDesc: "Bỏ qua hoàn toàn bộ trộn hệ thống (iOS CoreAudio Mixer). Dữ liệu nhạc gốc được truyền chính xác từng bit tới chip DAC AK4493 của Topping E30 thông qua driver USB Audio Class 2.0.",
                        recommendation: "Trải nghiệm mộc mạc nguyên bản. Bạn có thể bật bộ lọc 'poly-sinc-xtr-lp' ở Tab DSP để so sánh dải trung mượt mà hơn."
                    )
                }
            }
        } else if isBluetooth {
            if isSourceDsd {
                return AudioPathReport(
                    actualOutputFormat: "PCM 24-bit / 96.0 kHz (Capped)",
                    actualModulation: "DSD to PCM (Bluetooth optimized)",
                    isDsdConvertedToPcm: true,
                    isUpsampled: true,
                    dspEngineStatus: "Bluetooth không hỗ trợ DSD. Hệ thống tự động chuyển đổi DSD sang PCM 24-bit/96kHz để truyền tải qua Apple AAC Codec.",
                    ledColorHex: 0x29B6F6, // Blue (HD Wireless)
                    statusLabel: "HD Wireless (DSD Converted)",
                    statusDesc: "Đang truyền phát không dây qua codec AAC. Tệp DSD gốc đã được giải nén và chuyển hóa thành PCM 24-bit/96kHz phù hợp băng thông Bluetooth.",
                    recommendation: "Hãy kết nối USB DAC (Topping E30) để giải mã DSD nguyên bản. Trên Bluetooth, cấu hình này là tối ưu nhất."
                )
            } else {
                if userWantsDsp {
                    let finalFormat = isModulatorDsd ? "PCM 24-bit / 96.0 kHz (Capped)" : "PCM 24-bit / 96.0 kHz"
                    let finalMod = isModulatorDsd ? "PCM (SDM Disabled for BT)" : "PCM Upsampled"
                    let statusLabelStr = isModulatorDsd ? "HD Wireless (SDM Capped)" : "DSP Enhanced Wireless"
                    let finalStatusDesc = isModulatorDsd ?
                        "Bộ điều chế DSD bị vô hiệu hóa vì Bluetooth không thể truyền tải dòng DSD. Hệ thống chuyển sang upsample PCM 24-bit/96kHz thông qua bộ lọc [\(selectedFilter)] để tối ưu dải động." :
                        "Tín hiệu được upsample đồng bộ lên 96kHz qua bộ lọc [\(selectedFilter)] phù hợp tần số lấy mẫu đỉnh của Bluetooth."
                    
                    return AudioPathReport(
                        actualOutputFormat: finalFormat,
                        actualModulation: finalMod,
                        isDsdConvertedToPcm: false,
                        isUpsampled: true,
                        dspEngineStatus: "Nội suy tần số lấy mẫu lên 24-bit/96kHz đồng bộ với codec truyền không dây Bluetooth. Bỏ qua DSD Modulator.",
                        ledColorHex: 0xBB86FC, // Purple (DSP Enhanced)
                        statusLabel: statusLabelStr,
                        statusDesc: finalStatusDesc,
                        recommendation: "Cấu hình lý tưởng cho Bluetooth! Giúp loại bỏ hiện tượng méo tiếng do bộ phát Bluetooth nén dải động."
                    )
                } else {
                    let res = sourceBitDepth > 0 && sourceSamplingRate > 0 ?
                        "PCM \(sourceBitDepth)-bit / \(Double(sourceSamplingRate) / 1000.0) kHz" :
                        "PCM 16-bit / 44.1 kHz"
                    return AudioPathReport(
                        actualOutputFormat: res,
                        actualModulation: "Standard BT Broadcast",
                        isDsdConvertedToPcm: false,
                        isUpsampled: false,
                        dspEngineStatus: "Tắt xử lý số. Tín hiệu gốc truyền trực tiếp sang bộ mã hóa (encoder) Bluetooth.",
                        ledColorHex: 0x29B6F6, // Blue (HD Wireless)
                        statusLabel: "HD Wireless (Apple AAC)",
                        statusDesc: "Đang truyền tải không dây chất lượng cao qua codec AAC (256kbps). Tín hiệu được truyền mượt mà tới tai nghe.",
                        recommendation: "Mẹo: Bạn có thể bật bộ lọc 'sinc-L' và dither 'Gauss' ở Tab DSP để làm mượt dải cao trên tai nghe không dây."
                    )
                }
            }
        } else {
            // Built-in Speaker / Standard CoreAudio route
            let cappedFormat = "PCM 16-bit / 48.0 kHz (Capped)"
            
            if isSourceDsd {
                return AudioPathReport(
                    actualOutputFormat: cappedFormat,
                    actualModulation: "DSD to PCM (Speaker Downgrade)",
                    isDsdConvertedToPcm: true,
                    isUpsampled: false,
                    dspEngineStatus: "Tự động tắt bộ lọc DSP & bộ điều chế SDM để tiết kiệm pin trên loa thiết bị.",
                    ledColorHex: 0xFFB300, // Amber (Standard)
                    statusLabel: "Loa ngoài (DSD Converted)",
                    statusDesc: "Tệp DSD gốc được tự động chuyển đổi và hạ mẫu về PCM 16-bit/48kHz tiêu chuẩn để phát ra loa ngoài của thiết bị.",
                    recommendation: "Hãy cắm tai nghe hoặc kết nối DAC Topping E30 để giải mã nguyên bản DSD và trải nghiệm hệ thống Suca T5C bóng Mullard."
                )
            } else {
                if userWantsDsp {
                    return AudioPathReport(
                        actualOutputFormat: cappedFormat,
                        actualModulation: "PCM (DSP Disabled for Speaker)",
                        isDsdConvertedToPcm: false,
                        isUpsampled: false,
                        dspEngineStatus: "Bộ lọc và điều chế bị tạm dừng. Loa ngoài không đủ dải tần để thể hiện hiệu quả của bộ lọc âm học.",
                        ledColorHex: 0xFFB300, // Amber (Standard)
                        statusLabel: "Loa ngoài (DSP Bypass)",
                        statusDesc: "Hệ thống tự động tạm dừng bộ lọc upsampling để tiết kiệm pin và tránh nóng máy do loa ngoài không hỗ trợ tần số lấy mẫu cao.",
                        recommendation: "Để trải nghiệm bộ lọc HQPlayer cao cấp, hãy kết nối thiết bị với tai nghe dây hoặc bộ giải mã Topping E30."
                    )
                } else {
                    return AudioPathReport(
                        actualOutputFormat: "PCM 16-bit / 48.0 kHz",
                        actualModulation: "iOS CoreAudio Mixer",
                        isDsdConvertedToPcm: false,
                        isUpsampled: false,
                        dspEngineStatus: "Âm thanh được định tuyến qua bộ trộn âm tiêu chuẩn của hệ điều hành iOS (CoreAudio Mixer).",
                        ledColorHex: 0xFFB300, // Amber (Standard)
                        statusLabel: "Loa thiết bị (Standard)",
                        statusDesc: "Tín hiệu âm thanh gốc được hệ thống tự động tái mẫu (resampling) về tần số phát 48kHz của loa thiết bị.",
                        recommendation: "Hãy nâng cấp lên bộ giải mã USB DAC ngoài (như Topping E30) để trải nghiệm âm thanh Bit-Perfect nguyên gốc vượt qua bộ trộn iOS Mixer."
                    )
                }
            }
        }
    }
}
