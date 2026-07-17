import SwiftUI
import AVFoundation

struct SettingsView: View {
    @ObservedObject var repository: SubsonicRepository
    @ObservedObject var playerManager: PlayerManager
    var onLogout: () -> Void
    
    @State private var serverStatus: String = "Đang kiểm tra..."
    @State private var serverStatusColor: Color = .gray
    @State private var isScanning: Bool = false
    @State private var showingLogoutAlert = false
    @State private var showingScanStartedAlert = false
    
    // Monitoring route changes for active hardware details
    @State private var activeDevice = AudioDeviceHelper.getActiveDeviceDetails()
    @State private var isPulsing = false
    
    // Persisted Device Optimization configurations
    @AppStorage("hiResLosslessEnabled") private var hiResLosslessEnabled = true
    @AppStorage("ultraNoiseShaperEnabled") private var ultraHighNoiseShaper = true
    @AppStorage("streamingBufferSize") private var streamingBufferSize = "Balanced"
    
    var body: some View {
        NavigationView {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                
                ScrollView {
                    VStack(spacing: 20) {
                        
                        // ── 1. ĐƯỜNG TRUYỀN TÍN HIỆU HIỆN TẠI (AUDIO SIGNAL PATH) ──
                        VStack(alignment: .leading, spacing: 14) {
                            HStack {
                                Image(systemName: "waveform.path")
                                    .foregroundColor(.appPrimary)
                                    .font(.system(size: 16, weight: .bold))
                                Text("ĐƯỜNG TRUYỀN TÍN HIỆU HIỆN TẠI")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.gray)
                                Spacer()
                                Text(playerManager.currentSong == nil ? "Chế độ Demo" : "Thời gian thực")
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundColor(.appPrimary.opacity(0.8))
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.appPrimary.opacity(0.1))
                                    .cornerRadius(4)
                            }
                            .padding(.horizontal, 4)
                            
                            // Live Signal Path flow map
                            signalPathFlowMap
                            
                            // Signal steps detail list
                            VStack(alignment: .leading, spacing: 0) {
                                signalPathStep(
                                    title: "1. NGUỒN NHẠC GỐC (ORIGINAL FILE)",
                                    value: formatOriginalSpecs().0,
                                    subValue: formatOriginalSpecs().1,
                                    isFirst: true,
                                    color: Color(hex: audioReport.ledColorHex)
                                )
                                
                                signalPathStep(
                                    title: "2. PHƯƠNG THỨC TRUYỀN PHÁT (STREAMING)",
                                    value: computedStreamingValue,
                                    subValue: computedStreamingSub,
                                    color: Color(hex: audioReport.ledColorHex)
                                )
                                
                                signalPathStep(
                                    title: "3. BỘ GIẢI MÃ SỐ (AUDIO ENGINE / DSP)",
                                    value: computedEngineValue,
                                    subValue: audioReport.dspEngineStatus,
                                    color: Color(hex: audioReport.ledColorHex)
                                ) {
                                    interactiveDspSelectors
                                }
                                
                                signalPathStep(
                                    title: "4. THIẾT BỊ ĐẦU RA (OUTPUT HARDWARE)",
                                    value: activeDevice.name,
                                    subValue: computedDeviceDetails,
                                    color: Color(hex: audioReport.ledColorHex)
                                )
                                
                                signalPathStep(
                                    title: "5. DỰ BÁO CHẤT LƯỢNG (AUDIO FORECAST)",
                                    value: audioReport.statusLabel,
                                    subValue: audioReport.statusDesc,
                                    isLast: true,
                                    color: Color(hex: audioReport.ledColorHex)
                                )
                            }
                            .padding(14)
                            .background(Color.appSurface.opacity(0.5))
                            .cornerRadius(12)
                            .border(Color.white.opacity(0.04), width: 0.5)
                        }
                        .padding(16)
                        .background(Color.appSurface.opacity(0.3))
                        .cornerRadius(16)
                        .padding(.horizontal, 16)
                        
                        // ── 2. TỐI ƯU HÓA ÂM THANH THIẾT BỊ (DEVICE AUDIO OPTIMIZATION) ──
                        VStack(alignment: .leading, spacing: 14) {
                            HStack {
                                Image(systemName: "slider.horizontal.3")
                                    .foregroundColor(.appPrimary)
                                    .font(.system(size: 16, weight: .bold))
                                Text("TỐI ƯU HÓA ÂM THANH THIẾT BỊ")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.gray)
                            }
                            .padding(.horizontal, 4)
                            
                            VStack(spacing: 0) {
                                // Hi-Res Lossless toggle
                                Toggle(isOn: $hiResLosslessEnabled) {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text("Truyền phát Hi-Res Lossless")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(.white)
                                        Text("Kích hoạt băng thông rộng tối đa cho âm thanh độ nét cao qua mạng Wi-Fi.")
                                            .font(.system(size: 11))
                                            .foregroundColor(.gray)
                                    }
                                }
                                .tint(.appPrimary)
                                .padding(.vertical, 12)
                                
                                Divider().background(Color.white.opacity(0.05))
                                
                                // Ultra Noise Shaper
                                Toggle(isOn: $ultraHighNoiseShaper) {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text("Bộ giảm nhiễu dải siêu âm (Ultra Noise Shaper)")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(.white)
                                        Text("Định hình dải nghe thấy cực sạch, đẩy nhiễu lượng tử lên dải siêu âm dốc phẳng.")
                                            .font(.system(size: 11))
                                            .foregroundColor(.gray)
                                    }
                                }
                                .tint(.appPrimary)
                                .padding(.vertical, 12)
                                
                                Divider().background(Color.white.opacity(0.05))
                                
                                // Streaming Buffer size picker
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text("Bộ đệm dòng phát âm thanh")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(.white)
                                        Text("Tối ưu hóa độ đệm luồng phát nhạc để chống vấp nhạc.")
                                            .font(.system(size: 11))
                                            .foregroundColor(.gray)
                                    }
                                    Spacer()
                                    Picker("Buffer Size", selection: $streamingBufferSize) {
                                        Text("Thấp (2s)").tag("Low")
                                        Text("Chuẩn (5s)").tag("Balanced")
                                        Text("Lớn (10s)").tag("High")
                                    }
                                    .pickerStyle(MenuPickerStyle())
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(.appPrimary)
                                }
                                .padding(.vertical, 12)
                            }
                            .padding(.horizontal, 16)
                            .background(Color.appSurface.opacity(0.5))
                            .cornerRadius(12)
                            .border(Color.white.opacity(0.04), width: 0.5)
                        }
                        .padding(16)
                        .background(Color.appSurface.opacity(0.3))
                        .cornerRadius(16)
                        .padding(.horizontal, 16)
                        
                        // ── 3. THÔNG TIN KẾT NỐI (SERVER CONFIG) ──
                        VStack(alignment: .leading, spacing: 14) {
                            HStack {
                                Image(systemName: "network")
                                    .foregroundColor(.appPrimary)
                                    .font(.system(size: 16, weight: .bold))
                                Text("THÔNG TIN KẾT NỐI")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.gray)
                            }
                            .padding(.horizontal, 4)
                            
                            VStack(spacing: 12) {
                                HStack {
                                    Text("Máy chủ")
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(.gray)
                                    Spacer()
                                    Text(repository.config.baseUrl)
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                        .truncationMode(.middle)
                                }
                                
                                Divider().background(Color.white.opacity(0.05))
                                
                                HStack {
                                    Text("Tài khoản")
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(.gray)
                                    Spacer()
                                    Text(repository.config.username)
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundColor(.white)
                                }
                                
                                Divider().background(Color.white.opacity(0.05))
                                
                                HStack {
                                    Text("Trạng thái kết nối")
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(.gray)
                                    Spacer()
                                    Circle()
                                        .fill(serverStatusColor)
                                        .frame(width: 8, height: 8)
                                    Text(serverStatus)
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundColor(serverStatusColor)
                                }
                            }
                            .padding(16)
                            .background(Color.appSurface.opacity(0.5))
                            .cornerRadius(12)
                            .border(Color.white.opacity(0.04), width: 0.5)
                        }
                        .padding(16)
                        .background(Color.appSurface.opacity(0.3))
                        .cornerRadius(16)
                        .padding(.horizontal, 16)
                        
                        // ── 4. ADMIN PANEL (BẢNG ĐIỀU KHIỂN) ──
                        if repository.isAdmin {
                            VStack(alignment: .leading, spacing: 14) {
                                HStack {
                                    Image(systemName: "crown.fill")
                                        .foregroundColor(.yellow)
                                        .font(.system(size: 15, weight: .bold))
                                    Text("BẢNG ĐIỀU KHIỂN ADMIN")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundColor(.gray)
                                }
                                .padding(.horizontal, 4)
                                
                                Button(action: triggerScan) {
                                    HStack {
                                        if isScanning {
                                            ProgressView()
                                                .progressViewStyle(CircularProgressViewStyle(tint: .appPrimary))
                                                .padding(.trailing, 8)
                                        } else {
                                            Image(systemName: "arrow.clockwise.icloud.fill")
                                                .foregroundColor(.appPrimary)
                                        }
                                        
                                        Text(isScanning ? "Đang yêu cầu quét..." : "Quét lại toàn bộ thư viện")
                                            .font(.system(size: 14, weight: .semibold))
                                            .foregroundColor(.white)
                                        Spacer()
                                        Image(systemName: "chevron.right")
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundColor(.gray)
                                    }
                                    .padding(16)
                                    .background(Color.appSurface.opacity(0.5))
                                    .cornerRadius(12)
                                    .border(Color.white.opacity(0.04), width: 0.5)
                                }
                                .disabled(isScanning)
                            }
                            .padding(16)
                            .background(Color.appSurface.opacity(0.3))
                            .cornerRadius(16)
                            .padding(.horizontal, 16)
                        }
                        
                        // ── 5. PHẦN MỀM & ĐĂNG XUẤT ──
                        VStack(spacing: 12) {
                            HStack {
                                Text("Phiên bản")
                                    .font(.system(size: 13, weight: .medium))
                                    .foregroundColor(.gray)
                                Spacer()
                                Text("1.1.0 (Vi2Play iOS)")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.appPrimary)
                            }
                            .padding(.horizontal, 4)
                            
                            Button(action: { showingLogoutAlert = true }) {
                                HStack {
                                    Image(systemName: "power")
                                        .font(.system(size: 15, weight: .bold))
                                    Text("Đăng xuất / Ngắt kết nối")
                                        .font(.system(size: 14, weight: .bold))
                                    Spacer()
                                }
                                .foregroundColor(.red)
                                .padding(16)
                                .background(Color.red.opacity(0.1))
                                .cornerRadius(12)
                                .border(Color.red.opacity(0.2), width: 0.5)
                            }
                        }
                        .padding(16)
                        .padding(.horizontal, 16)
                        
                        Spacer(minLength: 40)
                    }
                    .padding(.top, 10)
                }
            }
            .navigationTitle("Cài đặt")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                checkConnection()
                startMonitoringRouteChanges()
                withAnimation(Animation.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                    isPulsing = true
                }
            }
            .onDisappear {
                stopMonitoringRouteChanges()
            }
            .alert("Đăng xuất", isPresented: $showingLogoutAlert) {
                Button("Huỷ", role: .cancel) {}
                Button("Xác nhận", role: .destructive) {
                    repository.clearConfig()
                    onLogout()
                }
            } message: {
                Text("Bạn có chắc chắn muốn ngắt kết nối khỏi máy chủ Navidrome hiện tại không? Mọi thông tin tài khoản sẽ bị xoá.")
            }
            .alert("Thông báo", isPresented: $showingScanStartedAlert) {
                Button("Đồng ý", role: .cancel) {}
            } message: {
                Text("Tiến trình quét lại nhạc đã được kích hoạt thành công trên máy chủ! Thư viện nhạc mới sẽ tự động cập nhật sau vài phút.")
            }
        }
        .navigationViewStyle(StackNavigationViewStyle())
    }
    
    // ── INTERACTIVE DSP SELECTIONS ───────────────────────────────────────────
    
    private var interactiveDspSelectors: some View {
        VStack(spacing: 12) {
            dspSelector(
                title: "BỘ LỌC UPSAMPLING (FILTER)",
                options: ["Bypass", "sinc-S", "polyphase FIR", "poly-sinc-xtr-lp"],
                selected: $playerManager.activeFilter,
                descriptions: [
                    "Bypass": "Tắt lọc upsampling, xuất dữ liệu gốc.",
                    "sinc-S": "Bộ lọc Sinc hữu hạn tái tạo dải trung ấm áp và hài âm tự nhiên.",
                    "polyphase FIR": "Bộ lọc pha tuyến tính tối ưu độ động và trường âm sân khấu.",
                    "poly-sinc-xtr-lp": "Thuật toán tuyến tính dốc đứng cực cao mô phỏng HQPlayer, triệt tiêu méo dải cao."
                ]
            )
            
            dspSelector(
                title: "BỘ TẠO DITHER (NOISE SHAPER)",
                options: ["None", "TPDF", "Gauss", "LNS15"],
                selected: $playerManager.activeDither,
                descriptions: [
                    "None": "Không thêm nhiễu dither (Bypass dither).",
                    "TPDF": "Mô hình nhiễu lượng tử lượng cực phổ thông dùng trong Studio.",
                    "Gauss": "Nhiễu lượng tử Gauss mượt mà, tối ưu dải tần số cao.",
                    "LNS15": "Noise Shaper bậc 15 siêu việt, đẩy nhiễu lượng tử lên dải siêu âm dốc đứng."
                ]
            )
            
            dspSelector(
                title: "BỘ ĐIỀU CHẾ SDM (MODULATOR)",
                options: ["PCM (Bit-Perfect)", "DSD64", "DSD512"],
                selected: $playerManager.activeModulator,
                descriptions: [
                    "PCM (Bit-Perfect)": "Giữ nguyên vẹn định dạng PCM, không biến đổi mã hóa DSD.",
                    "DSD64": "Mã hóa sang luồng DSD64 (Super Audio CD 2.82MHz) ấm mượt và dịu ngọt.",
                    "DSD512": "Mã hóa upsample đỉnh cao lên 22.58MHz (dành riêng cho USB DAC Topping E30)."
                ]
            )
        }
        .padding(.vertical, 8)
    }
    
    @ViewBuilder
    private func dspSelector(
        title: String,
        options: [String],
        selected: Binding<String>,
        descriptions: [String: String]
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.gray)
            
            Menu {
                ForEach(options, id: \.self) { option in
                    Button(action: {
                        withAnimation {
                            selected.wrappedValue = option
                        }
                    }) {
                        HStack {
                            Text(option)
                            if option == selected.wrappedValue {
                                Image(systemName: "checkmark")
                            }
                        }
                    }
                }
            } label: {
                HStack {
                    Text(selected.wrappedValue)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.appPrimary)
                    Spacer()
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.system(size: 11))
                        .foregroundColor(.gray)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.white.opacity(0.04))
                .cornerRadius(8)
                .border(Color.white.opacity(0.06), width: 0.5)
            }
            
            if let desc = descriptions[selected.wrappedValue] {
                Text(desc)
                    .font(.system(size: 11))
                    .foregroundColor(.gray)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
    
    // ── LIVE SIGNAL PATH FLOW MAP ────────────────────────────────────────────
    
    private var signalPathFlowMap: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                let reportColor = Color(hex: audioReport.ledColorHex)
                
                pipelineNode(
                    stage: "SOURCE",
                    title: formatOriginalSpecs().0.contains("DSD") ? "DSD" : "FLAC/WAV",
                    desc: formatOriginalSpecs().1.components(separatedBy: "•").first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "Lossless",
                    ledColor: .white,
                    glow: false
                )
                
                flowConnector(color: reportColor)
                
                let isTranscoded = repository.isServerTranscodeSuffix(playerManager.currentSong?.suffix ?? "flac")
                pipelineNode(
                    stage: "STREAM",
                    title: isTranscoded ? "Transcoded" : "Direct",
                    desc: isTranscoded ? "24-bit FLAC" : "Original",
                    ledColor: isTranscoded ? .blue : .green,
                    glow: false
                )
                
                flowConnector(color: reportColor)
                
                let isDspEnabled = audioReport.isUpsampled
                pipelineNode(
                    stage: "ENGINE",
                    title: isDspEnabled ? "HQ DSP" : "Standard",
                    desc: isDspEnabled ? playerManager.activeFilter.prefix(8).description : "Bypassed",
                    ledColor: isDspEnabled ? .purple : .green,
                    glow: false
                )
                
                flowConnector(color: reportColor)
                
                let driverTitle = activeDevice.typeLabel == "USB DAC" ? "Direct USB" : "iOS Mixer"
                let driverDesc = activeDevice.typeLabel == "USB DAC" ? "Bit-Perfect" : "Resample 48k"
                let driverColor: Color = activeDevice.typeLabel == "USB DAC" ? .yellow : .orange
                pipelineNode(
                    stage: "DRIVER",
                    title: driverTitle,
                    desc: driverDesc,
                    ledColor: driverColor,
                    glow: false
                )
                
                flowConnector(color: reportColor)
                
                pipelineNode(
                    stage: "OUTPUT",
                    title: String(activeDevice.name.prefix(12)),
                    desc: activeDevice.typeLabel == "USB DAC" ? "Topping DAC" : "Speaker/BT",
                    ledColor: reportColor,
                    glow: true
                )
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 4)
        }
    }
    
    @ViewBuilder
    private func pipelineNode(
        stage: String,
        title: String,
        desc: String,
        ledColor: Color,
        glow: Bool
    ) -> some View {
        VStack(spacing: 4) {
            Text(stage)
                .font(.system(size: 8, weight: .bold))
                .foregroundColor(.gray)
            
            VStack(spacing: 4) {
                HStack(spacing: 4) {
                    Circle()
                        .fill(ledColor)
                        .frame(width: 6, height: 6)
                        .shadow(color: ledColor, radius: glow && isPulsing ? 6 : 0)
                        .scaleEffect(glow && isPulsing ? 1.3 : 1.0)
                    
                    Text(title)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.white)
                }
                
                Text(desc)
                    .font(.system(size: 9))
                    .foregroundColor(.gray)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .frame(width: 100, height: 48)
            .background(Color.white.opacity(0.04))
            .cornerRadius(8)
            .border(Color.white.opacity(0.06), width: 0.5)
        }
    }
    
    private func flowConnector(color: Color) -> some View {
        HStack(spacing: 2) {
            ForEach(0..<3) { i in
                Circle()
                    .fill(color.opacity(0.6))
                    .frame(width: 2, height: 2)
                    .opacity(isPulsing ? 0.3 : 1.0)
                    .animation(Animation.linear(duration: 0.6).delay(Double(i) * 0.2).repeatForever(autoreverses: true), value: isPulsing)
            }
        }
        .frame(width: 16)
    }
    
    // ── SIGNAL STEP ROW VIEW ──────────────────────────────────────────────────
    
    @ViewBuilder
    private func signalPathStep<Content: View>(
        title: String,
        value: String,
        subValue: String,
        isFirst: Bool = false,
        isLast: Bool = false,
        color: Color,
        @ViewBuilder content: () -> Content = { EmptyView() }
    ) -> some View {
        HStack(alignment: .top, spacing: 14) {
            // Flow indicator chain line
            VStack(spacing: 0) {
                if !isFirst {
                    Rectangle()
                        .fill(Color.gray.opacity(0.2))
                        .frame(width: 1.5, height: 10)
                } else {
                    Spacer().frame(height: 10)
                }
                
                Circle()
                    .fill(color)
                    .frame(width: 8, height: 8)
                    .shadow(color: color, radius: isPulsing ? 4 : 0)
                
                if !isLast {
                    Rectangle()
                        .fill(Color.gray.opacity(0.2))
                        .frame(width: 1.5)
                } else {
                    Spacer().frame(height: 10)
                }
            }
            .frame(width: 8)
            
            // Content
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(color)
                
                Text(value)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                
                Text(subValue)
                    .font(.system(size: 11))
                    .foregroundColor(.gray)
                    .lineLimit(4)
                    .fixedSize(horizontal: false, vertical: true)
                
                content()
            }
            .padding(.bottom, isLast ? 0 : 16)
        }
    }
    
    // ── HELPER UTILITIES ─────────────────────────────────────────────────────
    
    private var audioReport: AudioPathReport {
        guard let song = playerManager.currentSong else {
            return AudioPathReport(
                actualOutputFormat: "PCM 16-bit / 44.1 kHz",
                actualModulation: "Standard",
                isDsdConvertedToPcm: false,
                isUpsampled: false,
                dspEngineStatus: "Hệ thống đang hoạt động ở chế độ truyền phát chuẩn.",
                ledColorHex: 0xFFB300,
                statusLabel: "Cấu hình chuẩn (Standard)",
                statusDesc: "Thực hiện định tuyến luồng âm thanh trực tiếp bỏ qua xử lý DSP.",
                recommendation: nil
            )
        }
        return AudioDecisionEngine.determineAudioPath(
            activeDevice: activeDevice,
            sourceSuffix: song.suffix,
            sourceBitDepth: song.bitDepth ?? 16,
            sourceSamplingRate: song.samplingRate ?? 44100,
            sourceBitRate: song.bitRate ?? 320,
            selectedFilter: playerManager.activeFilter,
            selectedDither: playerManager.activeDither,
            selectedModulator: playerManager.activeModulator
        )
    }
    
    private var isTranscoded: Bool {
        repository.isServerTranscodeSuffix(playerManager.currentSong?.suffix ?? "flac")
    }
    
    private var computedStreamingValue: String {
        isTranscoded ? "Server Transcoded (FLAC 24-bit)" : "Direct Stream (Nguyên bản)"
    }
    
    private var computedStreamingSub: String {
        if isTranscoded {
            let suffix = playerManager.currentSong?.suffix?.uppercased() ?? "FLAC"
            return "Dữ liệu gốc định dạng \(suffix) được máy chủ tự động chuyển mã không hao tổn sang FLAC 24-bit PCM giúp iOS giải mã tối ưu."
        } else {
            return "Truyền phát trực tiếp ở chất lượng nguyên gốc từ máy chủ, không qua xử lý hay tái nén."
        }
    }
    
    private var isDspEnabled: Bool {
        audioReport.isUpsampled
    }
    
    private var computedEngineValue: String {
        isDspEnabled ? "HQPlayer-grade Audiophile DSP" : "iOS CoreAudio Engine (Float 32-bit)"
    }
    
    private var computedDeviceDetails: String {
        "\(activeDevice.techLabel) • Định dạng thực tế: \(audioReport.actualOutputFormat)\n\(activeDevice.description)"
    }
    
    private func formatOriginalSpecs() -> (String, String) {
        guard let song = playerManager.currentSong else {
            return ("FLAC (Lossless)", "PCM 16-bit / 44.1 kHz • CD Quality")
        }
        
        let fmt = song.suffix?.lowercased() ?? "flac"
        let isDsd = fmt == "dsf" || fmt == "dff" || fmt == "dsd"
        
        let bitDepth = song.bitDepth ?? 16
        let rate = Double(song.samplingRate ?? 44100) / 1000.0
        let br = song.bitRate ?? 1411
        
        let formatName = isDsd ? "DSD Gốc (\(fmt.uppercased()))" : "FLAC (Lossless)"
        let resolution = isDsd ? "1-bit / \(rate == 2822.4 ? "2.82 MHz (DSD64)" : "\(rate) MHz")" : "\(bitDepth)-bit / \(rate) kHz"
        
        let brString = br > 0 ? "\(br) kbps" : ""
        let category = isDsd || bitDepth > 16 || rate > 48.0 ? "Studio Quality (Hi-Res)" : "Standard Quality (CD)"
        
        let details = [resolution, brString, category].filter { !$0.isEmpty }.joined(separator: " • ")
        return (formatName, details)
    }
    
    private func checkConnection() {
        Task {
            let ok = await repository.ping()
            await MainActor.run {
                if ok {
                    serverStatus = "Trực tuyến (Online)"
                    serverStatusColor = .appPrimary
                } else {
                    serverStatus = "Ngoại tuyến (Offline)"
                    serverStatusColor = .red
                }
            }
        }
    }
    
    private func triggerScan() {
        guard !isScanning else { return }
        
        isScanning = true
        Task {
            do {
                let success = try await repository.triggerLibraryScan()
                await MainActor.run {
                    isScanning = false
                    if success {
                        showingScanStartedAlert = true
                    }
                }
            } catch {
                print("Failed to start scan: \(error)")
                await MainActor.run {
                    isScanning = false
                }
            }
        }
    }
    
    private func startMonitoringRouteChanges() {
        NotificationCenter.default.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { _ in
            self.activeDevice = AudioDeviceHelper.getActiveDeviceDetails()
        }
    }
    
    private func stopMonitoringRouteChanges() {
        NotificationCenter.default.removeObserver(self, name: AVAudioSession.routeChangeNotification, object: nil)
    }
}
