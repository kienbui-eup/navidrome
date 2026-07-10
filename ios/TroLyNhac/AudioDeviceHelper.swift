import AVFoundation
import UIKit

public struct ActiveDeviceDetails: Equatable {
    public let name: String
    public let typeLabel: String
    public let techLabel: String
    public let maxQualityForecast: String
    public let description: String
    public let isHiResCapable: Bool
    public let isLossless: Bool
    
    public var hardwareNote: String {
        switch typeLabel {
        case "USB DAC":
            let isTopping = name.localizedCaseInsensitiveContains("Topping") || name.localizedCaseInsensitiveContains("E30")
            if isTopping {
                return "💡 Lưu ý phần cứng: USB DAC Topping E30 đang được kết nối. Chip giải mã AK4493 hỗ trợ gốc DSD512. Hãy kết hợp với Pre Suca T5C bóng Mullard 403b và op-amp Muses02 để trải nghiệm âm thanh analog cực mượt, dải âm ấm dày và nhạc tính đỉnh cao!"
            } else {
                return "💡 Lưu ý phần cứng: USB DAC [\(name)] đang được kết nối. Thiết bị hỗ trợ chất lượng tối đa \(maxQualityForecast). Đã kích hoạt chế độ Bit-Perfect trực tiếp qua cổng USB để giữ nguyên vẹn tín hiệu số nguyên bản!"
            }
        case "Bluetooth":
            return "💡 Lưu ý phần cứng: Đang phát qua tai nghe/thiết bị Bluetooth không dây [\(name)]. Tự động tối ưu hóa luồng truyền tải không dây codec chất lượng cao (Apple AAC) lên tới 16-bit/44.1kHz, vô hiệu hóa upsampling siêu tần DSD để tránh hao pin và suy hao băng thông."
        default:
            let isIpad = UIDevice.current.userInterfaceIdiom == .pad
            if isIpad {
                return "💡 Lưu ý phần cứng: Đang phát ra Loa ngoài của iPad. Hệ thống tự động bypass các bộ lọc upsampling để bảo vệ thời lượng pin và tránh nóng máy."
            } else {
                return "💡 Lưu ý phần cứng: Đang phát ra Loa ngoài của iPhone. Để bảo vệ thời lượng pin và tránh quá nhiệt, các bộ lọc upsampling nặng được bypass. Hãy cắm USB DAC Topping E30 qua cổng Lightning/Type-C để thưởng thức âm thanh Roon-grade!"
            }
        }
    }
}

public class AudioDeviceHelper {
    public static func getActiveDeviceDetails() -> ActiveDeviceDetails {
        let session = AVAudioSession.sharedInstance()
        guard let output = session.currentRoute.outputs.first else {
            return createFallbackDetails()
        }
        
        let portType = output.portType
        let name = output.portName
        
        switch portType {
        case .usbAudio:
            let isShanling = name.localizedCaseInsensitiveContains("Shanling") || name.localizedCaseInsensitiveContains("UP5")
            let isTopping = name.localizedCaseInsensitiveContains("Topping") || name.localizedCaseInsensitiveContains("E30")
            
            let desc: String
            if isShanling {
                desc = "Thiết bị giải mã Shanling UP5 được kết nối qua cổng USB của iPhone/iPad. Toàn bộ dữ liệu âm thanh số vượt qua bộ trộn iOS Mixer, truyền tải hoàn hảo, không suy hao (Bit-Perfect) trực tiếp tới phần cứng giải mã Dual ES9219C DAC bên ngoài."
            } else if isTopping {
                desc = "Bộ giải mã Topping E30 được phát hiện kết nối qua USB. Tín hiệu âm thanh bỏ qua hoàn toàn bộ trộn hệ thống (iOS Mixer), truyền tín hiệu số nguyên bản trực tiếp đến chip DAC AK4493, kết hợp với Pre Suca T5C (đã nâng cấp bóng Mullard 403b, tụ chất lượng cao và op-amp Muses02) cho chất âm đèn ấm áp, dải động cực rộng và nhạc tính đỉnh cao!"
            } else {
                desc = "Thiết bị giải mã USB DAC ngoài được kết nối trực tiếp. Kích hoạt chế độ xuất Bit-Perfect bỏ qua bộ trộn hệ thống của điện thoại (iOS CoreAudio Mixer), giữ nguyên vẹn dải động và tần số lấy mẫu của bản nhạc gốc."
            }
            
            let maxQuality = isTopping ? "32-bit / 768kHz & DSD512 (Lossless Bit-Perfect)" : "32-bit / 384kHz (Lossless Direct Output)"
            
            return ActiveDeviceDetails(
                name: name,
                typeLabel: "USB DAC",
                techLabel: "USB Audio Class 2.0 (Direct Bit-Perfect)",
                maxQualityForecast: maxQuality,
                description: desc,
                isHiResCapable: true,
                isLossless: true
            )
            
        case .bluetoothA2DP, .bluetoothLE, .bluetoothHFP:
            let isBuds2Pro = name.localizedCaseInsensitiveContains("Buds2 Pro") || name.localizedCaseInsensitiveContains("Buds")
            let isShanling = name.localizedCaseInsensitiveContains("Shanling") || name.localizedCaseInsensitiveContains("UP5")
            let isAirPods = name.localizedCaseInsensitiveContains("AirPods") || name.localizedCaseInsensitiveContains("Apple")
            
            let desc: String
            if isBuds2Pro {
                desc = "Tai nghe Samsung Galaxy Buds 2 Pro đang kết nối qua Bluetooth. Để đạt chất lượng âm thanh 24-bit/96kHz (Seamless Codec - SSC), hãy đảm bảo nguồn nhạc gốc là Lossless, tuy nhiên trên hệ điều hành iOS, thiết bị sẽ phát ở cấu hình AAC chất lượng tiêu chuẩn (16-bit/44.1kHz)."
            } else if isShanling {
                desc = "Thiết bị Shanling UP5 được kết nối qua Bluetooth. Trên các thiết bị iOS, tín hiệu sẽ được mã hóa và truyền tải qua codec AAC 256kbps chất lượng tiêu chuẩn của Apple thay vì LDAC như trên Android."
            } else if isAirPods {
                desc = "Tai nghe Apple AirPods/AirPods Pro đang kết nối qua Bluetooth. Hệ điều hành tự động đồng bộ hóa và phát ở codec AAC 256kbps độ phân giải cao của Apple, được tinh chỉnh hoàn hảo cho tai nghe của hãng."
            } else {
                desc = "Kết nối không dây Bluetooth. Trên thiết bị iOS, luồng âm thanh không dây sẽ luôn được truyền tải bằng codec AAC (256kbps, 16-bit/44.1kHz). Đảm bảo thiết bị nhận hỗ trợ tốt AAC để có âm trường rộng và chi tiết dải cao mượt mà nhất."
            }
            
            return ActiveDeviceDetails(
                name: name,
                typeLabel: "Bluetooth",
                techLabel: "Bluetooth Wireless Audio (Apple AAC Codec)",
                maxQualityForecast: "16-bit / 44.1kHz (Compressed Lossy AAC)",
                description: desc,
                isHiResCapable: false,
                isLossless: false
            )
            
        case .headphones:
            return ActiveDeviceDetails(
                name: name,
                typeLabel: "Tai nghe dây",
                techLabel: "Cổng Analog 3.5mm (High-Definition)",
                maxQualityForecast: "24-bit / 192kHz (Lossless Analog Output)",
                description: "Kết nối dây truyền thống qua giắc 3.5mm hoặc đầu chuyển analog Lightning/USB-C của Apple. Luồng tín hiệu âm thanh analog không bị nén, giữ nguyên độ trung thực cao, méo hài thấp và độ trễ bằng 0.",
                isHiResCapable: true,
                isLossless: true
            )
            
        case .HDMI:
            return ActiveDeviceDetails(
                name: name,
                typeLabel: "HDMI",
                techLabel: "HDMI Bitstream Digital Out",
                maxQualityForecast: "24-bit / 192kHz (Lossless Digital Multi-channel)",
                description: "Xuất tín hiệu số chất lượng cao qua cổng HDMI tới Receiver hoặc Soundbar. Đảm bảo âm thanh số được truyền đi nguyên bản không nén.",
                isHiResCapable: true,
                isLossless: true
            )
            
        case .builtInSpeaker:
            let isIpad = UIDevice.current.userInterfaceIdiom == .pad
            let nameLabel = isIpad ? "Loa kép Stereo iPad (Hi-Fi)" : "Loa iPhone (Built-in Speaker)"
            let tech = isIpad ? "Hệ thống loa đa kênh Stereo tích hợp" : "Loa tích hợp trên thiết bị"
            let desc = isIpad ? 
                "Đang phát qua hệ thống loa Stereo cao cấp tích hợp của iPad với trường âm rộng, độ rõ nét dải cao tốt và hỗ trợ Dolby Atmos sống động." :
                "Phát qua loa ngoài tích hợp của iPhone. Phù hợp nghe tạm thời, bị giới hạn về dải tần số, độ tách bạch âm thanh nổi (stereo width) và chất lượng dải trầm."
                
            return ActiveDeviceDetails(
                name: nameLabel,
                typeLabel: "Loa ngoài",
                techLabel: tech,
                maxQualityForecast: "16-bit / 48kHz (Standard Audio)",
                description: desc,
                isHiResCapable: isIpad,
                isLossless: true
            )
            
        default:
            return ActiveDeviceDetails(
                name: name,
                typeLabel: "Thiết bị âm thanh",
                techLabel: "iOS CoreAudio Output",
                maxQualityForecast: "16-bit / 44.1kHz (Standard Audio)",
                description: "Đang phát qua luồng âm thanh mặc định của hệ điều hành. Hệ thống tự động tối ưu hóa tần số lấy mẫu và dải động tùy theo thiết bị phát đang kết nối.",
                isHiResCapable: false,
                isLossless: true
            )
        }
    }
    
    private static func createFallbackDetails() -> ActiveDeviceDetails {
        return ActiveDeviceDetails(
            name: "Thiết bị mặc định (Default Output)",
            typeLabel: "Default",
            techLabel: "iOS CoreAudio System",
            maxQualityForecast: "16-bit / 44.1kHz (Standard Audio)",
            description: "Đang phát qua luồng âm thanh mặc định của hệ điều hành. Âm thanh sẽ đi qua bộ trộn tiêu chuẩn iOS CoreAudio Mixer.",
            isHiResCapable: false,
            isLossless: true
        )
    }
}
