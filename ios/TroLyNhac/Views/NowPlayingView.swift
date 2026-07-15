import SwiftUI
import AVKit
import MediaPlayer

// MARK: - AIRPLAY BUTTON COMPONENT
struct AirPlayButton: UIViewRepresentable {
    func makeUIView(context: Context) -> AVRoutePickerView {
        let picker = AVRoutePickerView()
        picker.activeTintColor = UIColor(AppTheme.primary)
        picker.tintColor = .white
        return picker
    }
    
    func updateUIView(_ uiView: AVRoutePickerView, context: Context) {}
}

// MARK: - NOW PLAYING VIEW
struct NowPlayingView: View {
    @ObservedObject var playerManager: PlayerManager
    @ObservedObject var repository: SubsonicRepository
    var onDismiss: () -> Void
    
    @State private var activeDevice = AudioDeviceHelper.getActiveDeviceDetails()
    @State private var showingSignalPathSheet = false
    @State private var showingQueueSheet = false
    @State private var showingDSPConfigSheet = false
    @State private var isDraggingSlider = false
    @State private var dragTime: Double = 0.0
    @State private var animateVisualizer = false
    @State private var isSuggestionLoading = false
    
    // Audiophile Review Panel State
    @State private var localStarred: String? = nil
    @State private var localUserRating: Int = 0
    @State private var localReviewNote: String = ""
    @State private var isEditingReview = false
    @State private var reviewInput: String = ""
    
    // Lyrics State
    @State private var showingLyricsSheet = false
    @State private var isLyricsLoading = false
    @State private var lyricsSynced = false
    @State private var lyricLines: [LyricLine] = []
    @State private var activeLyricId: UUID? = nil
    @State private var lastAutoScrollTime: Date = Date()
    
    private func syncLocalSongState(for song: Song) {
        self.localStarred = song.starred
        self.localUserRating = song.userRating ?? 0
        self.localReviewNote = UserDefaults.standard.string(forKey: "audiophile_review_\(song.id)") ?? ""
        self.reviewInput = self.localReviewNote
    }
    
    var body: some View {
        guard let song = playerManager.currentSong else {
            return AnyView(EmptyView())
        }
        
        return AnyView(
            ZStack {
                // Blur Artwork Background for deep premium atmosphere
                ambientBackground(song)
                
                VStack(spacing: 20) {
                    // Header Bar
                    headerBar
                    
                    Spacer()
                    
                    // Rotating Disc/Artwork
                    artworkView(song)
                    
                    Spacer()
                    
                    // Song Titles & Waveform Visualizer
                    songDetailsAndVisualizer(song)
                    
                    // Active Device Route Tag & AirPlay
                    routeControlsBar
                    
                    // Progress Slider
                    progressSliderView
                    
                    // Player Controls (Prev, Play, Next, Seek)
                    playbackControlsView
                    
                    // Bottom Utility Bar (Queue, Signal Path info)
                    utilityBarView
                    
                    Spacer().frame(height: 10)
                }
                .padding(.horizontal, 24)
            }
            .gesture(
                DragGesture(minimumDistance: 30)
                    .onEnded { value in
                        let threshold: CGFloat = 60
                        let horizontalDistance = value.translation.width
                        let verticalDistance = value.translation.height
                        
                        if abs(horizontalDistance) > abs(verticalDistance) {
                            if horizontalDistance < -threshold {
                                // Swipe Left -> Next song
                                withAnimation(.easeInOut) {
                                    playerManager.playNext()
                                }
                            } else if horizontalDistance > threshold {
                                // Swipe Right -> Previous song
                                withAnimation(.easeInOut) {
                                    playerManager.playPrevious()
                                }
                            }
                        } else {
                            if verticalDistance > threshold {
                                // Swipe Down -> Dismiss player
                                onDismiss()
                            }
                        }
                    }
            )
            .onAppear {
                startMonitoringRouteChanges()
                updateVisualizerState()
                if let song = playerManager.currentSong {
                    syncLocalSongState(for: song)
                    loadLyrics(for: song)
                }
            }
            .onDisappear {
                stopMonitoringRouteChanges()
            }
            .onChange(of: playerManager.isPlaying) { _ in
                updateVisualizerState()
            }
            .onChange(of: playerManager.currentSong) { newSong in
                if let song = newSong {
                    syncLocalSongState(for: song)
                    loadLyrics(for: song)
                }
            }
            .onChange(of: playerManager.currentTime) { _ in
                updateActiveLyricLine()
            }
            // Lyrics Bottom Sheet
            .sheet(isPresented: $showingLyricsSheet) {
                lyricsSheet(song)
            }
            // Signal Path Popover Sheet
            .sheet(isPresented: $showingSignalPathSheet) {
                signalPathSheet(song)
            }
            // Queue Bottom Sheet
            .sheet(isPresented: $showingQueueSheet) {
                queueSheet
            }
            // DSP / HQPlayer Configuration Sheet
            .sheet(isPresented: $showingDSPConfigSheet) {
                dspConfigSheet()
            }
        )
    }
    
    // ── BLUR AMBIENT BACKGROUND ──────────────────────────────────────────────
    
    private func ambientBackground(_ song: Song) -> some View {
        GeometryReader { geo in
            ZStack {
                Color.appBackground.ignoresSafeArea()
                
                AsyncImage(url: repository.coverArtUrl(coverArtId: song.coverArt, size: 32)) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.appBackground
                }
                .frame(width: geo.size.width, height: geo.size.height)
                .clipped()
                .blur(radius: 40)
                .opacity(0.35)
                
                // Overlay Vignette
                RadialGradient(
                    colors: [Color.clear, Color.appBackground.opacity(0.8), Color.appBackground],
                    center: .center,
                    startRadius: geo.size.width * 0.1,
                    endRadius: geo.size.height * 0.8
                )
                .ignoresSafeArea()
            }
        }
    }
    
    // ── HEADER BAR ───────────────────────────────────────────────────────────
    
    private var headerBar: some View {
        HStack {
            Button(action: onDismiss) {
                Image(systemName: "chevron.down")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white.opacity(0.8))
                    .frame(width: 44, height: 44)
                    .background(Color.white.opacity(0.04))
                    .clipShape(Circle())
            }
            
            Spacer()
            
            Text("Đang phát")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.white.opacity(0.6))
                .tracking(1.5)
            
            Spacer()
            
            Button(action: { showingSignalPathSheet = true }) {
                Image(systemName: "waveform.path")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(activeDeviceLEDColor)
                    .frame(width: 44, height: 44)
                    .background(activeDeviceLEDColor.opacity(0.1))
                    .clipShape(Circle())
            }
        }
        .padding(.top, 10)
    }
    
    // ── ARTWORK COMPONENT ───────────────────────────────────────────────────
    
    private func artworkView(_ song: Song) -> some View {
        AsyncImage(url: repository.coverArtUrl(coverArtId: song.coverArt, size: 600)) { image in
            image
                .resizable()
                .aspectRatio(contentMode: .fill)
        } placeholder: {
            ZStack {
                Color.white.opacity(0.04)
                Image(systemName: "music.note")
                    .font(.system(size: 64))
                    .foregroundColor(.white.opacity(0.2))
            }
        }
        .frame(width: 260, height: 260)
        .cornerRadius(24)
        .shadow(color: activeDeviceLEDColor.opacity(0.2), radius: 24, x: 0, y: 10)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(Color.white.opacity(0.1), lineWidth: 1.5)
        )
    }
    
    // ── TITLES & WAVEFORM ────────────────────────────────────────────────────
    
    private func songDetailsAndVisualizer(_ song: Song) -> some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 6) {
                Text(song.title)
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                    .lineLimit(1)
                
                Text(song.artist ?? "Không rõ Nghệ sĩ")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.appPrimary)
                    .lineLimit(1)
                
                if let album = song.album {
                    Text(album)
                        .font(.system(size: 13))
                        .foregroundColor(.white.opacity(0.4))
                        .lineLimit(1)
                }
            }
            
            Spacer()
            
            // Audio Waveform micro-animations
            HStack(spacing: 3) {
                ForEach(0..<5) { index in
                    RoundedRectangle(cornerRadius: 2)
                        .fill(Color.appPrimary)
                        .frame(width: 3.5, height: animateVisualizer ? CGFloat.random(in: 6...28) : 6)
                        .animation(animateVisualizer ? Animation.easeInOut(duration: Double.random(in: 0.3...0.6)).repeatForever() : .default, value: animateVisualizer)
                }
            }
            .frame(height: 30)
            .padding(.bottom, 6)
        }
    }
    
    // ── ROUTE CONTROLS BAR ───────────────────────────────────────────────────
    
    private var routeControlsBar: some View {
        HStack {
            // Signal Route Badge
            Button(action: { showingSignalPathSheet = true }) {
                HStack(spacing: 6) {
                    Circle()
                        .fill(activeDeviceLEDColor)
                        .frame(width: 6, height: 8)
                        .shadow(color: activeDeviceLEDColor, radius: 4)
                    
                    Text(activeDevice.typeLabel)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text("•")
                        .foregroundColor(.white.opacity(0.3))
                    
                    Text(activeDevice.name)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(.white.opacity(0.8))
                        .lineLimit(1)
                }
                .padding(.vertical, 6)
                .padding(.horizontal, 12)
                .background(Color.white.opacity(0.04))
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color.white.opacity(0.08), lineWidth: 1)
                )
            }
            
            Spacer()
            
            // Lyrics Button
            Button(action: { showingLyricsSheet = true }) {
                Image(systemName: "quote.bubble.fill")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(lyricLines.isEmpty ? .white.opacity(0.4) : .appPrimary)
                    .frame(width: 36, height: 36)
                    .background(Color.white.opacity(0.04))
                    .cornerRadius(18)
            }
            .padding(.trailing, 4)
            
            // DSP / HQPlayer Button
            Button(action: { showingDSPConfigSheet = true }) {
                Image(systemName: "slider.horizontal.3")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white.opacity(0.8))
                    .frame(width: 36, height: 36)
                    .background(Color.white.opacity(0.04))
                    .cornerRadius(18)
            }
            .padding(.trailing, 4)
            
            // AirPlay Button
            AirPlayButton()
                .frame(width: 36, height: 36)
                .background(Color.white.opacity(0.04))
                .cornerRadius(18)
        }
    }
    
    // ── PROGRESS SLIDER ──────────────────────────────────────────────────────
    
    private var progressSliderView: some View {
        VStack(spacing: 6) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    // Track background
                    Capsule()
                        .fill(Color.white.opacity(0.1))
                        .frame(height: 6)
                    
                    // Highlight progress
                    let ratio = playerManager.duration > 0 ? (isDraggingSlider ? dragTime : playerManager.currentTime) / playerManager.duration : 0.0
                    let width = geo.size.width * CGFloat(ratio)
                    Capsule()
                        .fill(Color.appPrimary)
                        .frame(width: max(0, min(width, geo.size.width)), height: 6)
                }
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            isDraggingSlider = true
                            let percentage = Double(value.location.x / geo.size.width)
                            let boundPercent = max(0, min(percentage, 1))
                            dragTime = boundPercent * playerManager.duration
                        }
                        .onEnded { value in
                            playerManager.seek(to: dragTime)
                            isDraggingSlider = false
                        }
                )
            }
            .frame(height: 6)
            
            HStack {
                Text(formatTime(isDraggingSlider ? dragTime : playerManager.currentTime))
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundColor(.white.opacity(0.5))
                
                Spacer()
                
                Text(formatTime(playerManager.duration))
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundColor(.white.opacity(0.5))
            }
        }
    }
    
    // ── PLAYBACK CONTROLS ────────────────────────────────────────────────────
    
    private var playbackControlsView: some View {
        HStack(spacing: 32) {
            // Previous Button
            Button(action: { playerManager.playPrevious() }) {
                Image(systemName: "backward.fill")
                    .font(.system(size: 26))
                    .foregroundColor(.white)
            }
            
            // Play/Pause Button
            Button(action: { playerManager.togglePlay() }) {
                ZStack {
                    Circle()
                        .fill(Color.appPrimary)
                        .frame(width: 72, height: 72)
                        .shadow(color: Color.appPrimary.opacity(0.4), radius: 12)
                    
                    Image(systemName: playerManager.isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.white)
                        .offset(x: playerManager.isPlaying ? 0 : 2)
                }
            }
            
            // Next Button
            Button(action: { playerManager.playNext() }) {
                Image(systemName: "forward.fill")
                    .font(.system(size: 26))
                    .foregroundColor(.white)
            }
        }
        .padding(.vertical, 10)
    }
    
    // ── UTILITY BAR ──────────────────────────────────────────────────────────
    
    private var utilityBarView: some View {
        HStack(spacing: 12) {
            Button(action: { showingQueueSheet = true }) {
                HStack(spacing: 6) {
                    Image(systemName: "list.bullet")
                    Text("Hàng chờ")
                }
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.white.opacity(0.8))
                .padding(.vertical, 8)
                .padding(.horizontal, 12)
                .background(Color.white.opacity(0.04))
                .cornerRadius(10)
            }
            
            // Smart AI suggestions menu
            Menu {
                Button(action: playMoreFromArtist) {
                    Label("Nghe thêm từ ca sĩ này", systemImage: "music.mic")
                }
                Button(action: playSimilarTracks) {
                    Label("Phát các bản nhạc tương tự", systemImage: "sparkles")
                }
            } label: {
                HStack(spacing: 6) {
                    if isSuggestionLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .appPrimary))
                            .scaleEffect(0.7)
                    } else {
                        Image(systemName: "sparkles")
                    }
                    Text("Gợi ý")
                }
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.appPrimary)
                .padding(.vertical, 8)
                .padding(.horizontal, 12)
                .background(Color.appPrimary.opacity(0.12))
                .cornerRadius(10)
            }
            .disabled(isSuggestionLoading)
            
            Spacer()
            
            // Quality tag
            if let br = playerManager.currentSong?.bitRate {
                HStack(spacing: 4) {
                    Image(systemName: "chart.bar.doc.horizontal")
                        .font(.system(size: 10))
                    Text("\(br) kbps")
                }
                .font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundColor(.appPrimary)
                .padding(.vertical, 6)
                .padding(.horizontal, 10)
                .background(Color.appPrimary.opacity(0.12))
                .cornerRadius(6)
            }
        }
        .padding(.top, 10)
    }
    
    // ── SIGNAL PATH DETAIL PANEL ─────────────────────────────────────────────
    
    private func signalPathSheet(_ song: Song) -> some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            
            VStack(alignment: .leading, spacing: 20) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Đường truyền tín hiệu (Signal Path)")
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                        Text("Phân tích luồng phát thời gian thực chuẩn Roon")
                            .font(.system(size: 11))
                            .foregroundColor(.gray)
                    }
                    Spacer()
                    Button("Đóng") { showingSignalPathSheet = false }
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.appPrimary)
                }
                .padding(.bottom, 10)
                
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        audiophileReviewPanel(song)
                        
                        Divider()
                            .background(Color.white.opacity(0.08))
                            .padding(.vertical, 8)
                        
                        let suffix = song.suffix?.lowercased() ?? "flac"
                        let isTranscoded = repository.isServerTranscodeSuffix(song.suffix)
                        
                        // ── BƯỚC 1: SOURCE ──────────────────────────────────
                        signalStep(
                            icon: "music.note",
                            color: .blue,
                            title: "1. NGUỒN NHẠC GỐC (ORIGINAL FILE)",
                            subtitle: "Tải trực tiếp từ máy chủ",
                            details: [
                                "Định dạng tệp: \(suffix.uppercased())",
                                "Tần số lấy mẫu: \(song.samplingRate != nil ? "\(song.samplingRate! / 1000) kHz" : "44.1 kHz")",
                                "Độ sâu bit: \(song.bitDepth != nil ? "\(song.bitDepth!) bit" : "16 bit")",
                                "Tốc độ truyền: \(song.bitRate != nil ? "\(song.bitRate!) kbps" : "Mặc định")"
                            ]
                        )
                        
                        connectorLine(.blue)
                        
                        // ── BƯỚC 2: STREAMING PATH ──────────────────────────
                        signalStep(
                            icon: "icloud.and.arrow.down",
                            color: isTranscoded ? .orange : .appPrimary,
                            title: "2. PHƯƠNG THỨC TRUYỀN PHÁT (STREAMING)",
                            subtitle: isTranscoded ? "Server Transcoded (FLAC 24-bit)" : "Direct Stream (Nguyên bản)",
                            details: isTranscoded ? [
                                "Dữ liệu gốc định dạng \(suffix.uppercased()) được máy chủ tự động chuyển mã không hao tổn sang FLAC 24-bit PCM với tần số lấy mẫu nguyên bản, giúp ứng dụng iOS giải mã tối ưu."
                            ] : [
                                "Tệp âm thanh \(suffix.uppercased()) được truyền phát trực tiếp ở chất lượng nguyên gốc từ máy chủ, không qua bất kỳ khâu xử lý hay tái nén nào."
                            ]
                        )
                        
                        connectorLine(isTranscoded ? .orange : .appPrimary)
                        
                        // ── BƯỚC 3: AUDIO ENGINE / DSP ──────────────────────
                        let isUpsampled = audioReport.isUpsampled
                        signalStep(
                            icon: "cpu",
                            color: isUpsampled ? .purple : .appPrimary,
                            title: "3. BỘ GIẢI MÃ SỐ (AUDIO ENGINE / DSP)",
                            subtitle: isUpsampled ? "HQPlayer-grade Audiophile DSP" : "iOS CoreAudio Engine (Float 32-bit)",
                            details: [
                                audioReport.dspEngineStatus
                            ]
                        )
                        
                        connectorLine(isUpsampled ? .purple : .appPrimary)
                        
                        // ── BƯỚC 4: OUTPUT HARDWARE ─────────────────────────
                        signalStep(
                            icon: "speaker.wave.2",
                            color: activeDeviceLEDColor,
                            title: "4. THIẾT BỊ ĐẦU RA (OUTPUT HARDWARE)",
                            subtitle: activeDevice.techLabel,
                            details: [
                                "Tên cổng: \(activeDevice.name)",
                                "Định dạng thực tế: \(audioReport.actualOutputFormat)",
                                "Bộ điều chế: \(audioReport.actualModulation)",
                                "Dự báo cổng: \(activeDevice.maxQualityForecast)",
                                "Chi tiết: \(activeDevice.description)"
                            ]
                        )
                        
                        connectorLine(activeDeviceLEDColor)
                        
                        // ── BƯỚC 5: FORECAST VERDICT ────────────────────────
                        signalStep(
                            icon: "checkmark.seal",
                            color: activeDeviceLEDColor,
                            title: "5. ĐÁNH GIÁ CHẤT LƯỢNG (AUDIO FORECAST)",
                            subtitle: audioReport.statusLabel,
                            details: [
                                audioReport.statusDesc,
                                audioReport.recommendation
                            ].compactMap { $0 }
                        )
                        
                        // ── DYNAMIC HARDWARE SPECS MATCHING NOTE ────────────
                        VStack(alignment: .leading, spacing: 8) {
                            Text(activeDevice.hardwareNote)
                                .font(.system(size: 11, weight: .regular))
                                .foregroundColor(Color(red: 0.77, green: 0.73, blue: 0.65))
                                .lineSpacing(4)
                        }
                        .padding(12)
                        .background(Color.white.opacity(0.04))
                        .cornerRadius(8)
                        .padding(.top, 8)
                    }
                    .padding(.vertical, 10)
                }
            }
            .padding(24)
        }
    }
    
    // ── AUDIOPHILE REVIEW PANEL ──────────────────────────────────────────────
    
    private func audiophileReviewPanel(_ song: Song) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            // Section Header
            HStack(spacing: 6) {
                Image(systemName: "star.fill")
                    .font(.system(size: 14))
                    .foregroundColor(.orange)
                
                Text("ĐÁNH GIÁ CHẤT LƯỢNG MASTERING")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.white)
                    .tracking(0.5)
            }
            
            // Rating Stars & Favorite Heart Row
            HStack {
                // Stars
                HStack(spacing: 8) {
                    ForEach(1...5, id: \.self) { starIndex in
                        let active = starIndex <= localUserRating
                        Image(systemName: active ? "star.fill" : "star")
                            .font(.system(size: 28))
                            .foregroundColor(active ? Color.orange : Color.white.opacity(0.15))
                            .onTapGesture {
                                let newRating = (localUserRating == starIndex) ? 0 : starIndex
                                Task {
                                    do {
                                        try await repository.setRating(id: song.id, rating: newRating)
                                        await MainActor.run {
                                            self.localUserRating = newRating
                                        }
                                    } catch {
                                        print("Lỗi cập nhật đánh giá: \(error)")
                                    }
                                }
                            }
                    }
                }
                
                Spacer()
                
                // Favorite Heart Button
                let isStarred = localStarred != nil
                Button(action: {
                    Task {
                        do {
                            if isStarred {
                                try await repository.unstar(id: song.id)
                                await MainActor.run {
                                    self.localStarred = nil
                                }
                            } else {
                                try await repository.star(id: song.id)
                                await MainActor.run {
                                    self.localStarred = "starred"
                                }
                            }
                        } catch {
                            print("Lỗi cập nhật yêu thích: \(error)")
                        }
                    }
                }) {
                    ZStack {
                        Circle()
                            .fill(isStarred ? Color.red.opacity(0.12) : Color.white.opacity(0.04))
                            .frame(width: 40, height: 40)
                            .overlay(
                                Circle()
                                    .stroke(isStarred ? Color.red.opacity(0.3) : Color.white.opacity(0.08), lineWidth: 0.5)
                            )
                        
                        Image(systemName: isStarred ? "heart.fill" : "heart")
                            .font(.system(size: 18))
                            .foregroundColor(isStarred ? .red : .white.opacity(0.3))
                    }
                }
            }
            
            Divider()
                .background(Color.white.opacity(0.06))
            
            // Review Notes Header
            HStack {
                Text("GHI CHÚ TRẢI NGHIỆM NGHE NHẠC")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.gray)
                
                Spacer()
                
                if !isEditingReview {
                    Button(action: {
                        self.reviewInput = localReviewNote
                        self.isEditingReview = true
                    }) {
                        Image(systemName: "pencil")
                            .font(.system(size: 12))
                            .foregroundColor(.appPrimary)
                    }
                }
            }
            
            if isEditingReview {
                VStack(spacing: 10) {
                    TextField(
                        "Ví dụ: Bản thu SACD chất lượng cao, dải trầm ấm áp, sân khấu rộng mở, độ động cực kỳ chi tiết...",
                        text: $reviewInput
                    )
                    .font(.system(size: 13))
                    .foregroundColor(.white)
                    .padding(10)
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.appPrimary.opacity(0.3), lineWidth: 1)
                    )
                    
                    HStack {
                        Spacer()
                        
                        Button("Huỷ") {
                            self.isEditingReview = false
                        }
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white.opacity(0.6))
                        
                        Spacer().frame(width: 16)
                        
                        Button("Lưu") {
                            UserDefaults.standard.set(reviewInput, forKey: "audiophile_review_\(song.id)")
                            self.localReviewNote = reviewInput
                            self.isEditingReview = false
                        }
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.appPrimary)
                    }
                }
            } else {
                Button(action: {
                    self.reviewInput = localReviewNote
                    self.isEditingReview = true
                }) {
                    HStack {
                        if !localReviewNote.isEmpty {
                            Text(localReviewNote)
                                .font(.system(size: 13).italic())
                                .foregroundColor(Color(red: 0.77, green: 0.73, blue: 0.65))
                                .multilineTextAlignment(.leading)
                                .lineSpacing(4)
                        } else {
                            Text("Chưa có đánh giá cho bản thu này. Nhấp để ghi lại cảm nhận âm trường, chi tiết, hay thiết bị phối ghép phù hợp...")
                                .font(.system(size: 11))
                                .foregroundColor(.white.opacity(0.25))
                                .multilineTextAlignment(.leading)
                                .lineSpacing(4)
                        }
                        Spacer()
                    }
                    .padding(12)
                    .background(Color.white.opacity(0.03))
                    .cornerRadius(8)
                }
                .buttonStyle(PlainButtonStyle())
            }
        }
        .padding(16)
        .background(Color.white.opacity(0.04))
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.white.opacity(0.08), lineWidth: 0.5)
        )
    }
    
    private func signalStep(icon: String, color: Color, title: String, subtitle: String, details: [String]) -> some View {
        HStack(alignment: .top, spacing: 16) {
            ZStack {
                Circle()
                    .fill(color.opacity(0.12))
                    .frame(width: 44, height: 44)
                
                Image(systemName: icon)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(color)
            }
            
            VStack(alignment: .leading, spacing: 6) {
                Text(title)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.white.opacity(0.6))
                    .tracking(0.5)
                
                Text(subtitle)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(color)
                
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(details, id: \.self) { detail in
                        Text("• " + detail)
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.5))
                            .lineSpacing(2)
                    }
                }
                .padding(.top, 4)
            }
        }
    }
    
    private func connectorLine(_ color: Color) -> some View {
        VStack {
            Rectangle()
                .fill(color.opacity(0.3))
                .frame(width: 2, height: 20)
        }
        .padding(.leading, 21)
    }
    
    // ── DSP CONFIG SHEET ──────────────────────────────────────────────────────
    
    private func dspConfigSheet() -> some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            
            VStack(alignment: .leading, spacing: 20) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Cấu hình DSP / HQPlayer Filters")
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                        Text("Giả lập nâng mẫu âm học và bộ điều chế SDM")
                            .font(.system(size: 11))
                            .foregroundColor(.gray)
                    }
                    Spacer()
                    Button("Đóng") { showingDSPConfigSheet = false }
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.appPrimary)
                }
                .padding(.bottom, 10)
                
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        // Section 1: Filters
                        dspSection(
                            title: "1. BỘ LỌC UPSAMPLING (PCM/DSD FILTER)",
                            subtitle: "Thuật toán nội suy tăng tần số lấy mẫu mẫu kỹ thuật số",
                            options: ["Bypass", "sinc-S", "polyphase FIR", "poly-sinc-xtr-lp"],
                            selected: $playerManager.activeFilter,
                            descriptions: [
                                "Bypass": "Tắt lọc upsampling, xuất dữ liệu nguyên bản mộc mạc.",
                                "sinc-S": "Bộ lọc Sinc hữu hạn (Finite Sinc) tái tạo dải trung ấm áp và hài âm tự nhiên.",
                                "polyphase FIR": "Bộ lọc pha tuyến tính hiệu năng cao, tối ưu độ động và không gian sân khấu.",
                                "poly-sinc-xtr-lp": "Thuật toán tuyến tính dốc đứng cực cao mô phỏng HQPlayer, triệt tiêu méo pha dải cao tuyệt hảo."
                            ]
                        )
                        
                        Divider().background(Color.white.opacity(0.08))
                        
                        // Section 2: Dither
                        dspSection(
                            title: "2. BỘ TẠO DITHER & NOISE SHAPER",
                            subtitle: "Ngăn ngừa méo lượng tử hóa và hạ tiếng ồn dải nghe thấy",
                            options: ["None", "TPDF", "Gauss", "LNS15"],
                            selected: $playerManager.activeDither,
                            descriptions: [
                                "None": "Không thêm nhiễu dither (Bypass dither).",
                                "TPDF": "Mô hình nhiễu lượng tử hình tam giác tiêu chuẩn công nghiệp âm thanh Studio.",
                                "Gauss": "Nhiễu lượng tử phân phối chuẩn Gaussian mượt mà, tối ưu dải tần số cao.",
                                "LNS15": "Noise Shaper bậc 15 siêu việt, đẩy toàn bộ nhiễu lượng tử lên dải siêu âm cực tĩnh."
                            ]
                        )
                        
                        Divider().background(Color.white.opacity(0.08))
                        
                        // Section 3: Modulator
                        dspSection(
                            title: "3. BỘ ĐIỀU CHẾ SDM (MODULATOR)",
                            subtitle: "Chuyển đổi PCM sang định dạng luồng 1-bit DSD độ nét cao",
                            options: ["PCM (Bit-Perfect)", "DSD64", "DSD512"],
                            selected: $playerManager.activeModulator,
                            descriptions: [
                                "PCM (Bit-Perfect)": "Giữ định dạng PCM nguyên vẹn, không mã hóa chuyển đổi DSD.",
                                "DSD64": "Mã hóa PCM sang luồng DSD64 (Super Audio CD 2.82MHz / 1-bit) ấm áp, mượt mà.",
                                "DSD512": "Chuyển đổi dòng siêu cao tần DSD512 (22.58MHz / 1-bit) đỉnh cao, phát huy tối đa DAC AK4493."
                            ]
                        )
                    }
                    .padding(.vertical, 10)
                }
            }
            .padding(24)
        }
    }
    
    private func dspSection(title: String, subtitle: String, options: [String], selected: Binding<String>, descriptions: [String: String]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.appPrimary)
                    .tracking(0.5)
                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.5))
            }
            
            VStack(spacing: 8) {
                ForEach(options, id: \.self) { option in
                    let isSelected = selected.wrappedValue == option
                    Button(action: { selected.wrappedValue = option }) {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(option)
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(isSelected ? .appPrimary : .white)
                                Spacer()
                                if isSelected {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(.appPrimary)
                                }
                            }
                            if let desc = descriptions[option] {
                                Text(desc)
                                    .font(.system(size: 11))
                                    .foregroundColor(.white.opacity(0.4))
                                    .multilineTextAlignment(.leading)
                            }
                        }
                        .padding(12)
                        .background(isSelected ? Color.appPrimary.opacity(0.08) : Color.white.opacity(0.02))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(isSelected ? Color.appPrimary.opacity(0.3) : Color.white.opacity(0.04), lineWidth: 1)
                        )
                    }
                }
            }
        }
    }
    
    // ── QUEUE BOTTOM SHEET ───────────────────────────────────────────────────
    
    private var queueSheet: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            
            VStack(spacing: 20) {
                // Header
                HStack {
                    Text("Danh sách hàng đợi")
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                    Spacer()
                    Button("Đóng") { showingQueueSheet = false }
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.appPrimary)
                }
                .padding(.bottom, 10)
                
                if playerManager.queue.isEmpty {
                    VStack {
                        Spacer()
                        Text("Hàng đợi trống")
                            .foregroundColor(.gray)
                        Spacer()
                    }
                } else {
                    List {
                        ForEach(Array(playerManager.queue.enumerated()), id: \.element.id) { index, song in
                            HStack {
                                Button(action: {
                                    playerManager.skipToQueueItem(index: index)
                                }) {
                                    HStack(spacing: 12) {
                                        AsyncImage(url: repository.coverArtUrl(coverArtId: song.coverArt, size: 100)) { image in
                                            image
                                                .resizable()
                                                .aspectRatio(contentMode: .fill)
                                        } placeholder: {
                                            ZStack {
                                                Color.white.opacity(0.04)
                                                Image(systemName: "music.note")
                                                    .font(.system(size: 14))
                                                    .foregroundColor(.white.opacity(0.2))
                                            }
                                        }
                                        .frame(width: 36, height: 36)
                                        .cornerRadius(4)
                                        
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(song.title)
                                                .font(.system(size: 13, weight: .bold))
                                                .foregroundColor(playerManager.currentIndex == index ? .appPrimary : .white)
                                                .lineLimit(1)
                                            
                                            Text(song.artist ?? "Không rõ Nghệ sĩ")
                                                .font(.system(size: 11))
                                                .foregroundColor(.gray)
                                                .lineLimit(1)
                                        }
                                    }
                                }
                                .buttonStyle(PlainButtonStyle())
                                
                                Spacer()
                                
                                // Drag indicator / remove button
                                Button(action: {
                                    playerManager.removeQueueItem(index: index)
                                }) {
                                    Image(systemName: "minus.circle.fill")
                                        .foregroundColor(.red.opacity(0.8))
                                }
                            }
                            .listRowBackground(Color.clear)
                        }
                    }
                    .listStyle(PlainListStyle())
                    .background(Color.clear)
                }
            }
            .padding(24)
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
                dspEngineStatus: "No audio stream",
                ledColorHex: 0xFFFFB300,
                statusLabel: "Standard Capped",
                statusDesc: "Hệ thống đang hoạt động ở chế độ truyền phát chuẩn.",
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
    
    private var activeDeviceLEDColor: Color {
        Color(hex: audioReport.ledColorHex)
    }
    
    private func updateVisualizerState() {
        withAnimation {
            animateVisualizer = playerManager.isPlaying
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
    
    private func formatTime(_ seconds: Double) -> String {
        guard !seconds.isNaN && !seconds.isInfinite else { return "0:00" }
        let m = Int(seconds) / 60
        let s = Int(seconds) % 60
        return String(format: "%d:%02d", m, s)
    }
    
    private func playMoreFromArtist() {
        guard let song = playerManager.currentSong else { return }
        let artistId = song.artistId ?? ""
        let artistName = song.artist ?? ""
        
        isSuggestionLoading = true
        Task {
            do {
                let songs = try await repository.fetchSongsByArtist(artistId: artistId, artistName: artistName)
                await MainActor.run {
                    isSuggestionLoading = false
                    if !songs.isEmpty {
                        playerManager.play(songs)
                    }
                }
            } catch {
                print("Failed to fetch more songs by artist: \(error)")
                await MainActor.run {
                    isSuggestionLoading = false
                }
            }
        }
    }
    
    private func playSimilarTracks() {
        guard let song = playerManager.currentSong else { return }
        
        isSuggestionLoading = true
        Task {
            do {
                let songs = try await repository.fetchSimilarSongs(songId: song.id)
                await MainActor.run {
                    isSuggestionLoading = false
                    if !songs.isEmpty {
                        playerManager.play(songs)
                    }
                }
            } catch {
                print("Failed to fetch similar songs: \(error)")
                await MainActor.run {
                    isSuggestionLoading = false
                }
            }
        }
    }
    
    // ── LYRICS COMPONENT ──────────────────────────────────────────────────────
    
    private func loadLyrics(for song: Song) {
        isLyricsLoading = true
        lyricLines = []
        lyricsSynced = false
        
        Task {
            let result = await repository.fetchLyrics(
                songId: song.id,
                artist: song.artist ?? "",
                title: song.title,
                duration: playerManager.duration
            )
            
            await MainActor.run {
                self.lyricLines = result.lines
                self.lyricsSynced = result.synced
                self.isLyricsLoading = false
                self.updateActiveLyricLine()
            }
        }
    }
    
    private func updateActiveLyricLine() {
        guard lyricsSynced, !lyricLines.isEmpty else { return }
        let currentTimeMs = playerManager.currentTime * 1000
        
        var activeLine: LyricLine? = nil
        for line in lyricLines {
            if line.timeMs >= 0 && line.timeMs <= currentTimeMs {
                activeLine = line
            }
        }
        
        if let activeLine = activeLine, activeLine.id != activeLyricId {
            if Date().timeIntervalSince(lastAutoScrollTime) > 2.0 {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                    self.activeLyricId = activeLine.id
                }
            } else {
                self.activeLyricId = activeLine.id
            }
        }
    }
    
    private func lyricsSheet(_ song: Song) -> some View {
        ZStack {
            ambientBackground(song)
            
            VStack(spacing: 0) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(song.title)
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                        
                        Text(song.artist ?? "")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white.opacity(0.6))
                            .lineLimit(1)
                    }
                    
                    Spacer()
                    
                    Button(action: { showingLyricsSheet = false }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.white.opacity(0.5))
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 24)
                .padding(.bottom, 16)
                
                if isLyricsLoading {
                    Spacer()
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .appPrimary))
                        .scaleEffect(1.5)
                    Text("Đang tải lời bài hát...")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white.opacity(0.6))
                        .padding(.top, 16)
                    Spacer()
                } else if lyricLines.isEmpty {
                    Spacer()
                    VStack(spacing: 12) {
                        Image(systemName: "quote.bubble.fill")
                            .font(.system(size: 48))
                            .foregroundColor(.white.opacity(0.2))
                        Text("Không tìm thấy lời bài hát")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white.opacity(0.6))
                    }
                    Spacer()
                } else {
                    ScrollViewReader { proxy in
                        ScrollView(.vertical, showsIndicators: false) {
                            VStack(alignment: .leading, spacing: 24) {
                                Color.clear.frame(height: 150)
                                
                                ForEach(lyricLines) { line in
                                    let isActive = lyricsSynced && (line.id == activeLyricId)
                                    
                                    Button(action: {
                                        if lyricsSynced && line.timeMs >= 0 {
                                            let generator = UIImpactFeedbackGenerator(style: .light)
                                            generator.impactOccurred()
                                            
                                            playerManager.seek(to: line.timeMs / 1000)
                                            lastAutoScrollTime = Date()
                                            
                                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                                self.activeLyricId = line.id
                                            }
                                        }
                                    }) {
                                        Text(line.text)
                                            .font(.system(size: isActive ? 26 : 22, weight: isActive ? .bold : .semibold))
                                            .foregroundColor(isActive ? .white : .white.opacity(0.4))
                                            .multilineTextAlignment(.leading)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                            .shadow(color: isActive ? Color.white.opacity(0.3) : Color.clear, radius: isActive ? 8 : 0)
                                            .scaleEffect(isActive ? 1.02 : 0.98)
                                            .animation(.spring(response: 0.3, dampingFraction: 0.85), value: isActive)
                                    }
                                    .buttonStyle(PlainButtonStyle())
                                    .id(line.id)
                                }
                                
                                Color.clear.frame(height: 250)
                            }
                            .padding(.horizontal, 24)
                        }
                        .onChange(of: activeLyricId) { targetId in
                            if let id = targetId {
                                withAnimation(.spring(response: 0.45, dampingFraction: 0.82)) {
                                    proxy.scrollTo(id, anchor: .center)
                                }
                            }
                        }
                        .onAppear {
                            if let activeId = activeLyricId {
                                proxy.scrollTo(activeId, anchor: .center)
                            }
                        }
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}
