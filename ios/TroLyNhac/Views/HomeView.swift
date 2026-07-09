import SwiftUI

struct HomeView: View {
    @ObservedObject var repository: SubsonicRepository
    var onAlbumSelected: (String) -> Void
    var onNavigateToSettings: () -> Void
    
    @State private var newestAlbums: [Album] = []
    @State private var frequentAlbums: [Album] = []
    @State private var recentAlbums: [Album] = []
    @State private var randomAlbums: [Album] = []
    
    @State private var isLoading: Bool = true
    @State private var isRefreshing: Bool = false
    @State private var errorMessage: String? = nil
    
    var body: some View {
        NavigationView {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                
                if isLoading {
                    loadingShimmerState
                } else if let error = errorMessage {
                    errorState(message: error)
                } else {
                    mainScrollContent
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    HStack(spacing: 8) {
                        Image("brand_logo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 24, height: 24)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Color.appPrimary.opacity(0.3), lineWidth: 0.5))
                        Text("Vi2Play")
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                    }
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: onNavigateToSettings) {
                        Image(systemName: "gearshape.fill")
                            .foregroundColor(.appPrimary)
                            .font(.system(size: 16))
                    }
                }
            }
        }
        .navigationViewStyle(StackNavigationViewStyle())
        .onAppear {
            if newestAlbums.isEmpty {
                loadAllData()
            }
        }
    }
    
    private var mainScrollContent: some View {
        ScrollView {
            VStack(spacing: 24) {
                Spacer().frame(height: 10)
                
                // Beautiful promotional banner & technical specs badges
                heroBannerView
                techBadgesView
                
                // Mới thêm
                if !newestAlbums.isEmpty {
                    AlbumHorizontalRow(title: "Mới thêm", albums: newestAlbums, repository: repository, onSelect: onAlbumSelected)
                }
                
                // Nghe nhiều
                if !frequentAlbums.isEmpty {
                    AlbumHorizontalRow(title: "Nghe nhiều nhất", albums: frequentAlbums, repository: repository, onSelect: onAlbumSelected)
                }
                
                // Gần đây
                if !recentAlbums.isEmpty {
                    AlbumHorizontalRow(title: "Nghe gần đây", albums: recentAlbums, repository: repository, onSelect: onAlbumSelected)
                }
                
                // Ngẫu nhiên
                if !randomAlbums.isEmpty {
                    AlbumHorizontalRow(title: "Bộ sưu tập ngẫu nhiên", albums: randomAlbums, repository: repository, onSelect: onAlbumSelected)
                }
                
                Spacer().frame(height: 80) // Padding for player overlay
            }
        }
        .refreshable {
            await refreshData()
        }
    }
    
    // ── LOADING SHIMMER STATE ────────────────────────────────────────────────
    
    private var loadingShimmerState: some View {
        ScrollView {
            VStack(spacing: 24) {
                Spacer().frame(height: 10)
                ForEach(0..<3) { _ in
                    VStack(alignment: .leading, spacing: 12) {
                        // Title skeleton
                        RoundedRectangle(cornerRadius: 4)
                            .fill(Color.white.opacity(0.08))
                            .frame(width: 120, height: 18)
                            .padding(.leading, 16)
                        
                        // Horizontal row skeleton
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 16) {
                                ForEach(0..<4) { _ in
                                    VStack(alignment: .leading, spacing: 8) {
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(Color.white.opacity(0.08))
                                            .frame(width: 140, height: 140)
                                        
                                        RoundedRectangle(cornerRadius: 4)
                                            .fill(Color.white.opacity(0.08))
                                            .frame(width: 110, height: 12)
                                        
                                        RoundedRectangle(cornerRadius: 4)
                                            .fill(Color.white.opacity(0.05))
                                            .frame(width: 80, height: 10)
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                }
            }
        }
    }
    
    private func errorState(message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "wifi.slash")
                .font(.system(size: 48))
                .foregroundColor(.gray)
            
            Text("Không tải được dữ liệu")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
            
            Text(message)
                .font(.system(size: 14))
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            
            Button(action: loadAllData) {
                Text("Thử lại")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.vertical, 10)
                    .padding(.horizontal, 24)
                    .background(Color.appPrimary)
                    .cornerRadius(8)
            }
        }
    }
    
    // ── DATA LOADING LOGIC ───────────────────────────────────────────────────
    
    private func loadAllData() {
        isLoading = true
        errorMessage = nil
        
        Task {
            do {
                async let newest = repository.fetchAlbums(type: "newest", size: 15)
                async let frequent = repository.fetchAlbums(type: "frequent", size: 15)
                async let recent = repository.fetchAlbums(type: "recent", size: 15)
                async let random = repository.fetchAlbums(type: "random", size: 15)
                
                let (n, f, r, ra) = try await (newest, frequent, recent, random)
                
                await MainActor.run {
                    self.newestAlbums = n
                    self.frequentAlbums = f
                    self.recentAlbums = r
                    self.randomAlbums = ra
                    self.isLoading = false
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = error.localizedDescription
                    self.isLoading = false
                }
            }
        }
    }
    
    private func refreshData() async {
        do {
            async let newest = repository.fetchAlbums(type: "newest", size: 15)
            async let frequent = repository.fetchAlbums(type: "frequent", size: 15)
            async let recent = repository.fetchAlbums(type: "recent", size: 15)
            async let random = repository.fetchAlbums(type: "random", size: 15)
            
            let (n, f, r, ra) = try await (newest, frequent, recent, random)
            
            await MainActor.run {
                self.newestAlbums = n
                self.frequentAlbums = f
                self.recentAlbums = r
                self.randomAlbums = ra
            }
        } catch {
            print("Refresh failed: \(error)")
        }
    }
    
    // ── VI2PLAY HERO BANNER & PREMIUM TECH BADGES ───────────────────────────
    
    private var heroBannerView: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Feel Every Detail")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.appPrimary)
                .tracking(2.0)
                .textCase(.uppercase)
            
            Text("Experience\nThe Real Sound")
                .font(.system(size: 26, weight: .bold))
                .foregroundColor(.white)
                .lineSpacing(4)
            
            Text("Âm nhạc chất lượng cao cho từng khoảnh khắc của bạn.")
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.7))
                .lineLimit(2)
            
            Button(action: {}) {
                Text("KHÁM PHÁ NGAY")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Color(red: 36/255, green: 25/255, blue: 0/255)) // dark bronze text
                    .padding(.vertical, 10)
                    .padding(.horizontal, 20)
                    .background(Color.appPrimary)
                    .cornerRadius(8)
            }
            .padding(.top, 4)
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            ZStack {
                Color.appSurfaceVariant
                
                // Simulated golden waves in background
                GeometryReader { geo in
                    Path { path in
                        path.move(to: CGPoint(x: 0, y: geo.size.height * 0.8))
                        path.addCurve(
                            to: CGPoint(x: geo.size.width, y: geo.size.height * 0.4),
                            control1: CGPoint(x: geo.size.width * 0.3, y: geo.size.height * 0.95), control2: CGPoint(x: geo.size.width * 0.6, y: geo.size.height * 0.1)
                        )
                        path.addLine(to: CGPoint(x: geo.size.width, y: geo.size.height))
                        path.addLine(to: CGPoint(x: 0, y: geo.size.height))
                        path.closeSubpath()
                    }
                    .fill(
                        LinearGradient(
                            colors: [Color.appPrimary.opacity(0.12), Color.appPrimary.opacity(0.0)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    
                    Path { path in
                        path.move(to: CGPoint(x: 0, y: geo.size.height * 0.8))
                        path.addCurve(
                            to: CGPoint(x: geo.size.width, y: geo.size.height * 0.4),
                            control1: CGPoint(x: geo.size.width * 0.3, y: geo.size.height * 0.95), control2: CGPoint(x: geo.size.width * 0.6, y: geo.size.height * 0.1)
                        )
                    }
                    .stroke(
                        Color.appPrimary.opacity(0.25),
                        lineWidth: 1.5
                    )
                }
            }
        )
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.appPrimary.opacity(0.15), lineWidth: 1)
        )
        .padding(.horizontal, 16)
    }
    
    private var techBadgesView: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 16) {
                techBadge(icon: "checkmark.seal.fill", title: "HI-RES AUDIO", desc: "Chất lượng Hi-Res")
                techBadge(icon: "waveform", title: "LOSSLESS", desc: "Âm thanh nguyên bản")
                techBadge(icon: "diamond.fill", title: "PREMIUM SOUND", desc: "Âm trường sân khấu")
                techBadge(icon: "headphones", title: "IMMERSIVE", desc: "Trải nghiệm đắm chìm")
            }
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 8)
    }
    
    private func techBadge(icon: String, title: String, desc: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundColor(.appPrimary)
                .frame(width: 40, height: 40)
                .background(Color.appPrimary.opacity(0.12))
                .clipShape(Circle())
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundColor(.white)
                Text(desc)
                    .font(.system(size: 10))
                    .foregroundColor(.appPrimary.opacity(0.8))
            }
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 14)
        .background(Color.white.opacity(0.03))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.white.opacity(0.05), lineWidth: 1)
        )
    }
}

// ── HORIZONTAL ALBUM ROW VIEW ───────────────────────────────────────────────

struct AlbumHorizontalRow: View {
    let title: String
    let albums: [Album]
    let repository: SubsonicRepository
    let onSelect: (String) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundColor(.white)
                .padding(.leading, 16)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    ForEach(albums) { album in
                        Button(action: { onSelect(album.id) }) {
                            AlbumCard(album: album, repository: repository)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }
}

struct AlbumCard: View {
    let album: Album
    let repository: SubsonicRepository
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Album Artwork
            AsyncImage(url: repository.coverArtUrl(coverArtId: album.coverArt, size: 280)) { image in
                image
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } placeholder: {
                ZStack {
                    Color.white.opacity(0.04)
                    Image(systemName: "music.note.list")
                        .font(.system(size: 32))
                        .foregroundColor(.white.opacity(0.2))
                }
            }
            .frame(width: 140, height: 140)
            .cornerRadius(12)
            .shadow(color: Color.black.opacity(0.3), radius: 6, x: 0, y: 3)
            
            // Text Details
            VStack(alignment: .leading, spacing: 2) {
                Text(album.name)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                
                Text(album.artist ?? "Không rõ Nghệ sĩ")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.gray)
                    .lineLimit(1)
            }
            .frame(width: 140, alignment: .leading)
        }
    }
}
