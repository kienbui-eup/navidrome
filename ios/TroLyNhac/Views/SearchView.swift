import SwiftUI

struct SearchView: View {
    @ObservedObject var repository: SubsonicRepository
    var onAlbumSelected: (Album) -> Void
    var onSongSelected: (Song) -> Void
    
    @State private var searchQuery: String = ""
    @State private var searchResult: SearchResult3? = nil
    @State private var isLoading: Bool = false
    @State private var searchTask: Task<Void, Never>? = nil
    
    var body: some View {
        NavigationView {
            ZStack {
                Color.appBackground.ignoresSafeArea()
                
                VStack(spacing: 0) {
                    searchBar
                    
                    if isLoading {
                        VStack {
                            Spacer()
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .appPrimary))
                                .scaleEffect(1.2)
                            Spacer()
                        }
                    } else if let results = searchResult, hasResults(results) {
                        resultsList(results)
                    } else if !searchQuery.isEmpty {
                        emptyState
                    } else {
                        suggestedState
                    }
                }
            }
            .navigationTitle("Tìm kiếm")
            .navigationBarHidden(true)
        }
        .navigationViewStyle(StackNavigationViewStyle())
    }
    
    private var searchBar: some View {
        HStack {
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.gray)
                
                TextField("Bài hát, album, nghệ sĩ...", text: $searchQuery)
                    .foregroundColor(.white)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                    .onChange(of: searchQuery) { newValue in
                        triggerSearch(query: newValue)
                    }
                
                if !searchQuery.isEmpty {
                    Button(action: {
                        searchQuery = ""
                        searchResult = nil
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.gray)
                    }
                }
            }
            .padding(.vertical, 10)
            .padding(.horizontal, 12)
            .background(Color.white.opacity(0.04))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
            )
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
    
    private func resultsList(_ results: SearchResult3) -> some View {
        List {
            // Songs Section
            if let songs = results.song, !songs.isEmpty {
                Section(header: Text("Bài hát").foregroundColor(.appPrimary).font(.system(size: 13, weight: .bold))) {
                    ForEach(songs) { song in
                        Button(action: { onSongSelected(song) }) {
                            SearchSongRow(song: song, repository: repository)
                        }
                    }
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                }
            }
            
            // Albums Section
            if let albums = results.album, !albums.isEmpty {
                Section(header: Text("Album").foregroundColor(.appPrimary).font(.system(size: 13, weight: .bold))) {
                    ForEach(albums) { album in
                        Button(action: { onAlbumSelected(album) }) {
                            SearchAlbumRow(album: album, repository: repository)
                        }
                    }
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                }
            }
            
            // Padding at the bottom for player
            Section(header: Spacer().frame(height: 80)) {
                EmptyView()
            }
            .listRowBackground(Color.clear)
        }
        .listStyle(PlainListStyle())
        .background(Color.clear)
    }
    
    private var emptyState: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "magnifyingglass.circle")
                .font(.system(size: 48))
                .foregroundColor(.gray)
            
            Text("Không tìm thấy kết quả")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)
            
            Text("Thử tìm kiếm với từ khoá khác hoặc kiểm tra chính tả.")
                .font(.system(size: 13))
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Spacer()
        }
    }
    
    private var suggestedState: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "music.note.house")
                .font(.system(size: 52))
                .foregroundColor(.appPrimary.opacity(0.4))
            
            Text("Tìm nhạc chất lượng cao")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)
            
            Text("Nhập từ khóa phía trên để tìm kiếm các bản nhạc Lossless nguyên bản từ máy chủ của bạn.")
                .font(.system(size: 13))
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Spacer()
        }
    }
    
    private func hasResults(_ results: SearchResult3) -> Bool {
        let songsCount = results.song?.count ?? 0
        let albumsCount = results.album?.count ?? 0
        let artistsCount = results.artist?.count ?? 0
        return (songsCount + albumsCount + artistsCount) > 0
    }
    
    private func triggerSearch(query: String) {
        searchTask?.cancel()
        
        let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedQuery.isEmpty else {
            self.searchResult = nil
            self.isLoading = false
            return
        }
        
        // Debounce search requests
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 400_000_000) // 400ms delay
            
            guard !Task.isCancelled else { return }
            
            await MainActor.run {
                self.isLoading = true
            }
            
            do {
                let results = try await repository.search(query: trimmedQuery)
                guard !Task.isCancelled else { return }
                
                await MainActor.run {
                    self.searchResult = results
                    self.isLoading = false
                }
            } catch {
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    self.searchResult = nil
                    self.isLoading = false
                }
            }
        }
    }
}

// ── SEARCH LIST ROWS ────────────────────────────────────────────────────────

struct SearchSongRow: View {
    let song: Song
    let repository: SubsonicRepository
    
    var body: some View {
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
            .frame(width: 44, height: 44)
            .cornerRadius(6)
            
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
            
            if song.bitDepth ?? 0 >= 24 {
                Text("Hi-Res")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundColor(.appPrimary)
                    .padding(.vertical, 2)
                    .padding(.horizontal, 4)
                    .background(Color.appPrimary.opacity(0.12))
                    .cornerRadius(4)
            }
        }
        .padding(.vertical, 4)
    }
}

struct SearchAlbumRow: View {
    let album: Album
    let repository: SubsonicRepository
    
    var body: some View {
        HStack(spacing: 12) {
            AsyncImage(url: repository.coverArtUrl(coverArtId: album.coverArt, size: 120)) { image in
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
                Text(album.name)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                
                Text(album.artist ?? "Không rõ Nghệ sĩ")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.gray)
                    .lineLimit(1)
                
                if let year = album.year {
                    Text(String(year))
                        .font(.system(size: 10))
                        .foregroundColor(.gray.opacity(0.6))
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
