import SwiftUI

// ── AUXILIARY DETAILED VIEWS FOR NAVIGATION ──────────────────────────────────

// MARK: - ALBUM DETAIL VIEW
struct AlbumDetailView: View {
    let albumId: String
    @ObservedObject var repository: SubsonicRepository
    @ObservedObject var playerManager: PlayerManager
    @Environment(\.presentationMode) var presentationMode
    
    @State private var album: Album? = nil
    @State private var isLoading: Bool = true
    @State private var errorMessage: String? = nil
    @State private var showingDeleteConfirm = false
    @State private var songToDelete: Song? = nil
    @State private var showingAlbumDeleteConfirm = false
    
    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            
            if let album = album, !isLoading {
                // Ambient blurred artwork background
                AsyncImage(url: repository.coverArtUrl(coverArtId: album.coverArt, size: 300)) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .blur(radius: 60)
                        .opacity(0.24)
                } placeholder: {
                    Color.clear
                }
                .ignoresSafeArea()
            }
            
            if isLoading {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .appPrimary))
                    .scaleEffect(1.2)
            } else if let error = errorMessage {
                VStack(spacing: 16) {
                    Text("Lỗi: \(error)")
                        .foregroundColor(.gray)
                    Button("Thử lại") { loadAlbum() }
                        .padding(.vertical, 8)
                        .padding(.horizontal, 16)
                        .background(Color.appPrimary)
                        .cornerRadius(8)
                }
            } else if let album = album {
                ScrollView {
                    VStack(spacing: 20) {
                        albumHeader(album)
                            .contentShape(Rectangle())
                            .gesture(
                                DragGesture(minimumDistance: 30)
                                    .onEnded { value in
                                        let threshold: CGFloat = 60
                                        if value.translation.height > threshold && abs(value.translation.height) > abs(value.translation.width) {
                                            presentationMode.wrappedValue.dismiss()
                                        }
                                    }
                            )
                        actionButtons(album)
                        tracksList(album)
                        Spacer().frame(height: 80)
                    }
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if repository.isAdmin {
                    Button(action: { showingAlbumDeleteConfirm = true }) {
                        Image(systemName: "trash")
                            .foregroundColor(.red)
                    }
                }
            }
        }
        .alert("Xoá Album", isPresented: $showingAlbumDeleteConfirm) {
            Button("Huỷ", role: .cancel) {}
            Button("Xoá vĩnh viễn", role: .destructive) { deleteAlbum() }
        } message: {
            Text("Bạn có chắc chắn muốn xoá vĩnh viễn toàn bộ album này khỏi ổ đĩa máy chủ không? Hành động này không thể hoàn tác.")
        }
        .alert("Xoá bài hát", isPresented: $showingDeleteConfirm) {
            Button("Huỷ", role: .cancel) { songToDelete = nil }
            Button("Xoá", role: .destructive) { deleteSong() }
        } message: {
            if let song = songToDelete {
                Text("Bạn có muốn xoá vĩnh viễn bài hát '\(song.title)' khỏi ổ đĩa máy chủ không?")
            }
        }
        .onAppear {
            loadAlbum()
        }
    }
    
    private func albumHeader(_ album: Album) -> some View {
        VStack(spacing: 12) {
            AsyncImage(url: repository.coverArtUrl(coverArtId: album.coverArt, size: 400)) { image in
                image
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } placeholder: {
                ZStack {
                    Color.white.opacity(0.04)
                    Image(systemName: "music.note.list")
                        .font(.system(size: 56))
                        .foregroundColor(.white.opacity(0.2))
                }
            }
            .frame(width: 200, height: 200)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.white.opacity(0.12), lineWidth: 0.5)
            )
            .shadow(color: Color.black.opacity(0.45), radius: 12, x: 0, y: 6)
            
            Text(album.name)
                .font(.system(size: 20, weight: .bold, design: .rounded))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            
            Text(album.artist ?? "Không rõ Nghệ sĩ")
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(.appPrimary)
            
            HStack(spacing: 8) {
                if let year = album.year {
                    Text(String(year))
                }
                if album.year != nil && album.songCount != nil {
                    Text("•")
                }
                if let count = album.songCount {
                    Text("\(count) bài hát")
                }
            }
            .font(.system(size: 12))
            .foregroundColor(.gray)
        }
        .padding(.top, 20)
    }
    
    private func actionButtons(_ album: Album) -> some View {
        HStack(spacing: 16) {
            Button(action: {
                if let songs = album.song, !songs.isEmpty {
                    playerManager.play(songs, startIndex: 0)
                }
            }) {
                HStack {
                    Image(systemName: "play.fill")
                    Text("Phát tất cả")
                }
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(Color.appPrimary)
                .cornerRadius(10)
            }
            
            Button(action: {
                if let songs = album.song, !songs.isEmpty {
                    playerManager.play(songs.shuffled(), startIndex: 0)
                }
            }) {
                HStack {
                    Image(systemName: "shuffle")
                    Text("Trộn bài")
                }
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(Color.white.opacity(0.06))
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.white.opacity(0.12), lineWidth: 1)
                )
            }
        }
        .padding(.horizontal, 24)
    }
    
    private func tracksList(_ album: Album) -> some View {
        VStack(spacing: 0) {
            if let songs = album.song {
                ForEach(Array(songs.enumerated()), id: \.element.id) { index, song in
                    Button(action: {
                        playerManager.play(songs, startIndex: index)
                    }) {
                        let isPlaying = playerManager.currentSong?.id == song.id
                        HStack(spacing: 14) {
                            Text(String(song.track ?? (index + 1)))
                                .font(.system(size: 14, weight: isPlaying ? .bold : .medium, design: .monospaced))
                                .foregroundColor(isPlaying ? .appPrimary : .gray.opacity(0.6))
                                .frame(width: 24, alignment: .leading)
                            
                            VStack(alignment: .leading, spacing: 4) {
                                Text(song.title)
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(isPlaying ? .appPrimary : .white)
                                    .lineLimit(1)
                                
                                HStack(spacing: 6) {
                                    // Suffix/Codec Tag
                                    CodecBadgeView(suffix: song.suffix, bitDepth: song.bitDepth)
                                    
                                    // Bitrate info
                                    if let br = song.bitRate {
                                        Text("\(br) kbps")
                                            .font(.system(size: 9))
                                            .foregroundColor(.gray.opacity(0.6))
                                    }
                                    
                                    // Hi-Res Audio Tag
                                    HiResBadgeView(bitDepth: song.bitDepth, suffix: song.suffix, repository: repository)
                                }
                            }
                            
                            Spacer()
                            
                            if let duration = song.duration {
                                Text(formatTime(Double(duration)))
                                    .font(.system(size: 13, design: .monospaced))
                                    .foregroundColor(.gray.opacity(0.8))
                            }
                        }
                        .padding(.vertical, 10)
                        .padding(.horizontal, 24)
                        .background(playerManager.currentSong?.id == song.id ? Color.appPrimary.opacity(0.1) : Color.clear)
                    }
                    .buttonStyle(PlainButtonStyle())
                    .contextMenu {
                        if repository.isAdmin {
                            Button(role: .destructive) {
                                songToDelete = song
                                showingDeleteConfirm = true
                            } label: {
                                Label("Xoá bài hát khỏi máy chủ", systemImage: "trash")
                            }
                        }
                    }
                    
                    Divider()
                        .background(Color.white.opacity(0.05))
                        .padding(.leading, 60)
                }
            }
            
            Spacer().frame(height: 80)
        }
    }
    
    private func loadAlbum() {
        isLoading = true
        errorMessage = nil
        Task {
            do {
                let fullAlbum = try await repository.fetchAlbum(id: albumId)
                await MainActor.run {
                    self.album = fullAlbum
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
    
    private func deleteSong() {
        guard let song = songToDelete else { return }
        Task {
            do {
                let ok = try await repository.deleteSong(id: song.id)
                await MainActor.run {
                    if ok {
                        loadAlbum() // Reload
                    }
                    songToDelete = nil
                }
            } catch {
                print("Delete failed: \(error)")
                await MainActor.run { songToDelete = nil }
            }
        }
    }
    
    private func deleteAlbum() {
        Task {
            do {
                let ok = try await repository.deleteAlbum(id: albumId)
                await MainActor.run {
                    if ok {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            } catch {
                print("Delete album failed: \(error)")
            }
        }
    }
    
    private func formatTime(_ seconds: Double) -> String {
        let m = Int(seconds) / 60
        let s = Int(seconds) % 60
        return String(format: "%d:%02d", m, s)
    }
}

// MARK: - ARTIST DETAIL VIEW
struct ArtistDetailView: View {
    let artist: Artist
    @ObservedObject var repository: SubsonicRepository
    var onAlbumSelected: (String) -> Void
    @Environment(\.presentationMode) var presentationMode
    
    @State private var albums: [Album] = []
    @State private var isLoading = true
    
    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            
            if isLoading {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .appPrimary))
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        artistHeader
                            .contentShape(Rectangle())
                            .gesture(
                                DragGesture(minimumDistance: 30)
                                    .onEnded { value in
                                        let threshold: CGFloat = 60
                                        if value.translation.height > threshold && abs(value.translation.height) > abs(value.translation.width) {
                                            presentationMode.wrappedValue.dismiss()
                                        }
                                    }
                            )
                        
                        Text("Album")
                            .font(.system(size: 18, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                            .padding(.horizontal, 24)
                        
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                            ForEach(albums) { album in
                                Button(action: { onAlbumSelected(album.id) }) {
                                    VStack(alignment: .leading, spacing: 6) {
                                        AsyncImage(url: repository.coverArtUrl(coverArtId: album.coverArt, size: 280)) { image in
                                            image
                                                .resizable()
                                                .aspectRatio(contentMode: .fill)
                                        } placeholder: {
                                            ZStack {
                                                Color.white.opacity(0.04)
                                                Image(systemName: "music.note.list")
                                                    .font(.system(size: 24))
                                                    .foregroundColor(.white.opacity(0.2))
                                            }
                                        }
                                        .frame(width: (UIScreen.main.bounds.width - 64) / 2, height: (UIScreen.main.bounds.width - 64) / 2)
                                        .clipShape(RoundedRectangle(cornerRadius: 10))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 10)
                                                .stroke(Color.white.opacity(0.1), lineWidth: 0.5)
                                        )
                                        
                                        Text(album.name)
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(.white)
                                            .lineLimit(1)
                                        
                                        if let year = album.year {
                                            Text(String(year))
                                                .font(.system(size: 11))
                                                .foregroundColor(.gray)
                                        }
                                    }
                                }
                                .buttonStyle(PlainButtonStyle())
                            }
                        }
                        .padding(.horizontal, 24)
                        
                        Spacer().frame(height: 80)
                    }
                }
            }
        }
        .navigationTitle(artist.name)
        .onAppear {
            loadArtistAlbums()
        }
    }
    
    private var artistHeader: some View {
        HStack(spacing: 20) {
            AsyncImage(url: repository.coverArtUrl(coverArtId: artist.coverArt, size: 200)) { image in
                image
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } placeholder: {
                ZStack {
                    Color.white.opacity(0.04)
                    Image(systemName: "person.crop.circle")
                        .font(.system(size: 40))
                        .foregroundColor(.white.opacity(0.2))
                }
            }
            .frame(width: 80, height: 80)
            .clipShape(Circle())
            
            VStack(alignment: .leading, spacing: 4) {
                Text(artist.name)
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                
                Text("\(albums.count) Album")
                    .font(.system(size: 14))
                    .foregroundColor(.appPrimary)
            }
            Spacer()
        }
        .padding(24)
        .background(Color.appSurface.opacity(0.4))
        .cornerRadius(16)
        .padding(.horizontal, 24)
        .padding(.top, 10)
    }
    
    private func loadArtistAlbums() {
        isLoading = true
        Task {
            do {
                let fullArtist = try await repository.fetchArtist(id: artist.id)
                await MainActor.run {
                    self.albums = fullArtist.album ?? []
                    self.isLoading = false
                }
            } catch {
                print("Failed loading albums: \(error)")
                await MainActor.run { self.isLoading = false }
            }
        }
    }
}

// MARK: - PLAYLIST DETAIL VIEW
struct PlaylistDetailView: View {
    let playlistId: String
    @ObservedObject var repository: SubsonicRepository
    @ObservedObject var playerManager: PlayerManager
    @Environment(\.presentationMode) var presentationMode
    
    @State private var playlist: Playlist? = nil
    @State private var isLoading = true
    @State private var songs: [Song] = []
    @State private var showingDeleteConfirm = false
    
    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            
            if isLoading {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .appPrimary))
            } else if let playlist = playlist {
                ScrollView {
                    VStack(spacing: 20) {
                        playlistHeader(playlist)
                            .contentShape(Rectangle())
                            .gesture(
                                DragGesture(minimumDistance: 30)
                                    .onEnded { value in
                                        let threshold: CGFloat = 60
                                        if value.translation.height > threshold && abs(value.translation.height) > abs(value.translation.width) {
                                            presentationMode.wrappedValue.dismiss()
                                        }
                                    }
                            )
                        playlistActions
                        playlistSongsList
                        Spacer().frame(height: 80)
                    }
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: { showingDeleteConfirm = true }) {
                    Image(systemName: "trash")
                        .foregroundColor(.red)
                }
            }
        }
        .alert("Xoá Playlist", isPresented: $showingDeleteConfirm) {
            Button("Huỷ", role: .cancel) {}
            Button("Xoá", role: .destructive) { deletePlaylist() }
        } message: {
            Text("Bạn có chắc chắn muốn xoá danh sách phát này không? Thao tác này chỉ xoá danh sách, KHÔNG ảnh hưởng đến các tệp nhạc gốc.")
        }
        .onAppear {
            loadPlaylist()
        }
    }
    
    private func playlistHeader(_ pl: Playlist) -> some View {
        VStack(spacing: 12) {
            AsyncImage(url: repository.coverArtUrl(coverArtId: pl.coverArt, size: 300)) { image in
                image
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } placeholder: {
                ZStack {
                    Color.white.opacity(0.04)
                    Image(systemName: "music.note.list")
                        .font(.system(size: 40))
                        .foregroundColor(.white.opacity(0.2))
                }
            }
            .frame(width: 140, height: 140)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.white.opacity(0.12), lineWidth: 0.5)
            )
            .shadow(color: Color.black.opacity(0.35), radius: 8, x: 0, y: 4)
            
            Text(pl.name)
                .font(.system(size: 20, weight: .bold, design: .rounded))
                .foregroundColor(.white)
            
            Text("\(songs.count) bài hát")
                .font(.system(size: 13))
                .foregroundColor(.gray)
        }
        .padding(.top, 20)
    }
    
    private var playlistActions: some View {
        HStack {
            Button(action: {
                if !songs.isEmpty {
                    playerManager.play(songs, startIndex: 0)
                }
            }) {
                HStack {
                    Image(systemName: "play.fill")
                    Text("Phát tất cả")
                }
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(Color.appPrimary)
                .cornerRadius(10)
            }
            .disabled(songs.isEmpty)
        }
        .padding(.horizontal, 24)
    }
    
    private var playlistSongsList: some View {
        VStack(spacing: 0) {
            ForEach(Array(songs.enumerated()), id: \.element.id) { index, song in
                Button(action: {
                    playerManager.play(songs, startIndex: index)
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
                                    .font(.system(size: 16))
                                    .foregroundColor(.white.opacity(0.2))
                            }
                        }
                        .frame(width: 40, height: 40)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .overlay(
                            RoundedRectangle(cornerRadius: 6)
                                .stroke(Color.white.opacity(0.08), lineWidth: 0.5)
                        )
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text(song.title)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                            
                            Text(song.artist ?? "Không rõ Nghệ sĩ")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(.gray)
                                .lineLimit(1)
                        }
                        
                        Spacer()
                        
                        if let duration = song.duration {
                            Text(formatTime(Double(duration)))
                                .font(.system(size: 13, design: .monospaced))
                                .foregroundColor(.gray.opacity(0.8))
                        }
                    }
                    .padding(.vertical, 8)
                    .padding(.horizontal, 24)
                    .background(playerManager.currentSong?.id == song.id ? Color.appPrimary.opacity(0.1) : Color.clear)
                }
                .buttonStyle(PlainButtonStyle())
                
                Divider()
                    .background(Color.white.opacity(0.05))
                    .padding(.leading, 76)
            }
            
            Spacer().frame(height: 80)
        }
    }
    
    private func loadPlaylist() {
        isLoading = true
        Task {
            do {
                let fullPlaylist = try await repository.fetchPlaylist(id: playlistId)
                await MainActor.run {
                    self.playlist = fullPlaylist
                    self.songs = fullPlaylist.entry ?? []
                    self.isLoading = false
                }
            } catch {
                print("Failed loading playlist: \(error)")
                await MainActor.run { self.isLoading = false }
            }
        }
    }
    
    private func deletePlaylist() {
        Task {
            do {
                try await repository.deletePlaylist(id: playlistId)
                await MainActor.run {
                    presentationMode.wrappedValue.dismiss()
                }
            } catch {
                print("Failed delete playlist: \(error)")
            }
        }
    }
    
    private func formatTime(_ seconds: Double) -> String {
        let m = Int(seconds) / 60
        let s = Int(seconds) % 60
        return String(format: "%d:%02d", m, s)
    }
}

// MARK: - PREMIUM AUDIO QUALITY BADGES (ROON / SUBSTREAM STYLE)
struct CodecBadgeView: View {
    let suffix: String?
    let bitDepth: Int?
    
    var body: some View {
        if let s = suffix?.uppercased() {
            let isHiRes = ["DSD", "DSF", "DIFF", "FLAC"].contains(s) && (bitDepth ?? 16) >= 24
            let isDsd = ["DSD", "DSF", "DIFF"].contains(s)
            let isLossless = ["WAV", "ALAC", "AIF", "AIFF", "APE", "FLAC"].contains(s) && !isHiRes
            
            if isHiRes || isDsd {
                Text(s)
                    .font(.system(size: 8, weight: .bold))
                    .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.0)) // Royal Gold
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(
                        LinearGradient(
                            colors: [Color(red: 0.18, green: 0.14, blue: 0.05), Color(red: 0.26, green: 0.2, blue: 0.06)],
                            startPoint: .leading, endPoint: .trailing
                        )
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 3))
                    .overlay(
                        RoundedRectangle(cornerRadius: 3)
                            .stroke(Color(red: 1.0, green: 0.84, blue: 0.0).opacity(0.35), lineWidth: 0.5)
                    )
            } else if isLossless {
                Text(s)
                    .font(.system(size: 8, weight: .bold))
                    .foregroundColor(Color(red: 0.0, green: 0.9, blue: 1.0)) // Cyan
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(
                        LinearGradient(
                            colors: [Color(red: 0.03, green: 0.15, blue: 0.18), Color(red: 0.04, green: 0.23, blue: 0.27)],
                            startPoint: .leading, endPoint: .trailing
                        )
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 3))
                    .overlay(
                        RoundedRectangle(cornerRadius: 3)
                            .stroke(Color(red: 0.0, green: 0.9, blue: 1.0).opacity(0.35), lineWidth: 0.5)
                    )
            } else {
                Text(s)
                    .font(.system(size: 8, weight: .bold))
                    .foregroundColor(.gray)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(
                        LinearGradient(
                            colors: [Color(red: 0.11, green: 0.11, blue: 0.13), Color(red: 0.15, green: 0.14, blue: 0.17)],
                            startPoint: .leading, endPoint: .trailing
                        )
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 3))
                    .overlay(
                        RoundedRectangle(cornerRadius: 3)
                            .stroke(Color.gray.opacity(0.15), lineWidth: 0.5)
                    )
            }
        }
    }
}

struct HiResBadgeView: View {
    let bitDepth: Int?
    let suffix: String?
    let repository: SubsonicRepository
    
    var body: some View {
        if (bitDepth ?? 0) >= 24 || repository.isServerTranscodeSuffix(suffix) {
            Text("Hi-Res")
                .font(.system(size: 8, weight: .bold))
                .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.0)) // Royal Gold
                .padding(.horizontal, 5)
                .padding(.vertical, 2)
                .background(
                    LinearGradient(
                        colors: [Color(red: 0.22, green: 0.18, blue: 0.06), Color(red: 0.32, green: 0.25, blue: 0.08)],
                        startPoint: .leading, endPoint: .trailing
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: 3))
                .overlay(
                    RoundedRectangle(cornerRadius: 3)
                        .stroke(Color(red: 1.0, green: 0.84, blue: 0.0).opacity(0.4), lineWidth: 0.5)
                )
        }
    }
}
