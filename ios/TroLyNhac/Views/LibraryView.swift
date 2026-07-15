import SwiftUI

struct LibraryView: View {
    @ObservedObject var repository: SubsonicRepository
    var onArtistSelected: (Artist) -> Void
    var onPlaylistSelected: (Playlist) -> Void
    var onSongSelected: (Song) -> Void
    
    @State private var selectedTab: Int
    
    init(repository: SubsonicRepository, onArtistSelected: @escaping (Artist) -> Void, onPlaylistSelected: @escaping (Playlist) -> Void, onSongSelected: @escaping (Song) -> Void, initialTab: Int = 0) {
        self.repository = repository
        self.onArtistSelected = onArtistSelected
        self.onPlaylistSelected = onPlaylistSelected
        self.onSongSelected = onSongSelected
        self._selectedTab = State(initialValue: initialTab)
    }
    @State private var playlists: [Playlist] = []
    @State private var artists: [Artist] = []
    @State private var starredSongs: [Song] = []
    
    @State private var isLoading: Bool = false
    @State private var showingCreatePlaylist = false
    @State private var newPlaylistName = ""
    
    var body: some View {
        NavigationView {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                
                VStack(spacing: 16) {
                    // Segmented Tabs
                    Picker("Thư viện", selection: $selectedTab) {
                        Text("Yêu thích").tag(0)
                        Text("Danh sách phát").tag(1)
                        Text("Nghệ sĩ").tag(2)
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                    
                    if isLoading {
                        Spacer()
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .appPrimary))
                        Spacer()
                    } else {
                        tabContent
                    }
                }
            }
            .simultaneousGesture(
                DragGesture(minimumDistance: 40)
                    .onEnded { value in
                        let threshold: CGFloat = 50
                        let horizontalDistance = value.translation.width
                        let verticalDistance = value.translation.height
                        
                        if abs(horizontalDistance) > abs(verticalDistance) {
                            if horizontalDistance < -threshold {
                                // Swipe Left -> select next tab
                                if selectedTab < 2 {
                                    withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                                        selectedTab += 1
                                    }
                                }
                            } else if horizontalDistance > threshold {
                                // Swipe Right -> select previous tab
                                if selectedTab > 0 {
                                    withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                                        selectedTab -= 1
                                    }
                                }
                            }
                        }
                    }
            )
            .navigationTitle("Thư viện")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    if selectedTab == 1 {
                        Button(action: { showingCreatePlaylist = true }) {
                            Image(systemName: "plus")
                                .foregroundColor(.appPrimary)
                                .font(.system(size: 16, weight: .bold))
                        }
                    }
                }
            }
            .alert("Tạo Danh sách phát", isPresented: $showingCreatePlaylist) {
                TextField("Tên danh sách phát", text: $newPlaylistName)
                Button("Huỷ", role: .cancel) { newPlaylistName = "" }
                Button("Tạo") { createPlaylist() }
            } message: {
                Text("Nhập tên danh sách phát mới muốn tạo trên máy chủ Navidrome.")
            }
        }
        .navigationViewStyle(StackNavigationViewStyle())
        .onAppear {
            loadLibraryData()
        }
        .onChange(of: selectedTab) { _ in
            loadLibraryData()
        }
    }
    
    @ViewBuilder
    private var tabContent: some View {
        switch selectedTab {
        case 0:
            starredSongsView
        case 1:
            playlistsView
        default:
            artistsView
        }
    }
    
    // ── STARRED SONGS TAB ───────────────────────────────────────────────────
    
    private var starredSongsView: some View {
        Group {
            if starredSongs.isEmpty {
                emptyTabState(systemImage: "heart.slash", title: "Chưa có bài hát yêu thích", subtitle: "Hãy thả tim các bài hát bạn thích trên web hoặc ứng dụng để xem ở đây.")
            } else {
                List {
                    ForEach(starredSongs) { song in
                        Button(action: { onSongSelected(song) }) {
                            SearchSongRow(song: song, repository: repository)
                        }
                    }
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                    
                    Section(header: Spacer().frame(height: 80)) { EmptyView() }.listRowBackground(Color.clear)
                }
                .listStyle(PlainListStyle())
                .background(Color.clear)
            }
        }
    }
    
    // ── PLAYLISTS TAB ───────────────────────────────────────────────────────
    
    private var playlistsView: some View {
        Group {
            if playlists.isEmpty {
                emptyTabState(systemImage: "music.note.list", title: "Không có danh sách phát", subtitle: "Hãy tạo một danh sách phát mới bằng cách nhấn vào dấu cộng góc trên bên phải.")
            } else {
                List {
                    ForEach(playlists) { playlist in
                        Button(action: { onPlaylistSelected(playlist) }) {
                            PlaylistRow(playlist: playlist, repository: repository)
                        }
                    }
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    
                    Section(header: Spacer().frame(height: 80)) { EmptyView() }.listRowBackground(Color.clear)
                }
                .listStyle(PlainListStyle())
                .background(Color.clear)
            }
        }
    }
    
    // ── ARTISTS TAB ─────────────────────────────────────────────────────────
    
    private var artistsView: some View {
        Group {
            if artists.isEmpty {
                emptyTabState(systemImage: "music.mic", title: "Không tìm thấy nghệ sĩ", subtitle: "Máy chủ của bạn hiện chưa cập nhật dữ liệu nghệ sĩ.")
            } else {
                List {
                    ForEach(artists) { artist in
                        Button(action: { onArtistSelected(artist) }) {
                            ArtistRow(artist: artist, repository: repository)
                        }
                    }
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    
                    Section(header: Spacer().frame(height: 80)) { EmptyView() }.listRowBackground(Color.clear)
                }
                .listStyle(PlainListStyle())
                .background(Color.clear)
            }
        }
    }
    
    private func emptyTabState(systemImage: String, title: String, subtitle: String) -> some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: systemImage)
                .font(.system(size: 44))
                .foregroundColor(.gray.opacity(0.6))
            
            Text(title)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)
            
            Text(subtitle)
                .font(.system(size: 13))
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Spacer()
        }
    }
    
    // ── DATA UTILITIES ───────────────────────────────────────────────────────
    
    private func loadLibraryData() {
        isLoading = true
        
        Task {
            do {
                if selectedTab == 0 {
                    let songs = try await repository.fetchStarredSongs()
                    await MainActor.run {
                        self.starredSongs = songs
                        self.isLoading = false
                    }
                } else if selectedTab == 1 {
                    let list = try await repository.fetchPlaylists()
                    await MainActor.run {
                        self.playlists = list
                        self.isLoading = false
                    }
                } else {
                    let art = try await repository.fetchArtists()
                    await MainActor.run {
                        self.artists = art.sorted(by: { $0.name.lowercased() < $1.name.lowercased() })
                        self.isLoading = false
                    }
                }
            } catch {
                print("Failed loading library tab data: \(error)")
                await MainActor.run {
                    self.isLoading = false
                }
            }
        }
    }
    
    private func createPlaylist() {
        guard !newPlaylistName.isEmpty else { return }
        
        isLoading = true
        Task {
            do {
                try await repository.createPlaylist(name: newPlaylistName, songIds: [])
                await MainActor.run {
                    newPlaylistName = ""
                    loadLibraryData()
                }
            } catch {
                print("Create playlist failed: \(error)")
                await MainActor.run {
                    isLoading = false
                }
            }
        }
    }
}

// ── LIBRARY ROWS ────────────────────────────────────────────────────────────

struct PlaylistRow: View {
    let playlist: Playlist
    let repository: SubsonicRepository
    
    var body: some View {
        HStack(spacing: 12) {
            AsyncImage(url: repository.coverArtUrl(coverArtId: playlist.coverArt, size: 120)) { image in
                image
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } placeholder: {
                ZStack {
                    Color.white.opacity(0.04)
                    Image(systemName: "music.note.list")
                        .font(.system(size: 20))
                        .foregroundColor(.white.opacity(0.2))
                }
            }
            .frame(width: 52, height: 52)
            .cornerRadius(8)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(playlist.name)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                
                Text("\(playlist.songCount ?? 0) bài hát")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.gray)
            }
            
            Spacer()
            
            Image(systemName: "chevron.right")
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.2))
        }
        .padding(.vertical, 4)
    }
}

struct ArtistRow: View {
    let artist: Artist
    let repository: SubsonicRepository
    
    var body: some View {
        HStack(spacing: 12) {
            AsyncImage(url: repository.coverArtUrl(coverArtId: artist.coverArt, size: 120)) { image in
                image
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } placeholder: {
                ZStack {
                    Color.white.opacity(0.04)
                    Image(systemName: "person.circle")
                        .font(.system(size: 24))
                        .foregroundColor(.white.opacity(0.2))
                }
            }
            .frame(width: 52, height: 52)
            .clipShape(Circle())
            
            VStack(alignment: .leading, spacing: 2) {
                Text(artist.name)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                
                if let albums = artist.albumCount {
                    Text("\(albums) album")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.gray)
                }
            }
            
            Spacer()
            
            Image(systemName: "chevron.right")
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.2))
        }
        .padding(.vertical, 4)
    }
}
