import SwiftUI

struct AppShellView: View {
    @StateObject private var repository = SubsonicRepository()
    @StateObject private var playerManager: PlayerManager
    
    @State private var selectedTab = 0
    @State private var showingNowPlaying = false
    @State private var activeDetailAlbumId: String? = nil
    @State private var activeDetailArtist: Artist? = nil
    @State private var activeDetailPlaylistId: String? = nil
    
    // For iPad sidebar navigation
    @State private var sidebarSelection: String? = "home"
    
    init() {
        let repo = SubsonicRepository()
        self._repository = StateObject(wrappedValue: repo)
        self._playerManager = StateObject(wrappedValue: PlayerManager(repository: repo))
    }
    
    var body: some View {
        Group {
            if !repository.isConfigured {
                LoginView(repository: repository) { _ in
                    // Callback on successful login
                    selectedTab = 0
                    sidebarSelection = "home"
                }
            } else {
                ZStack(alignment: .bottom) {
                    // Layout according to device type
                    if UIDevice.current.userInterfaceIdiom == .pad {
                        tabletSplitLayout
                    } else {
                        phoneTabLayout
                    }
                    
                    // Floating Mini Player & Full Screen Now Playing Sheet overlay
                    VStack(spacing: 0) {
                        if playerManager.currentSong != nil {
                            MiniPlayerView(playerManager: playerManager, repository: repository) {
                                showingNowPlaying = true
                            }
                            .padding(.bottom, UIDevice.current.userInterfaceIdiom == .pad ? 0 : 49) // Float above tabbar on phone
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                        }
                    }
                }
                .ignoresSafeArea(.keyboard, edges: .bottom)
                // Navigation detail overlays
                .sheet(isPresented: showingDetailBinding) {
                    detailOverlayContainer
                }
                // Full screen player presentation
                .sheet(isPresented: $showingNowPlaying) {
                    NowPlayingView(playerManager: playerManager, repository: repository) {
                        showingNowPlaying = false
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
    }
    
    // ── IPHONE TAB LAYOUT ────────────────────────────────────────────────────
    
    private var phoneTabLayout: some View {
        TabView(selection: $selectedTab) {
            HomeView(repository: repository, onAlbumSelected: { id in
                activeDetailAlbumId = id
            }, onNavigateToSettings: {
                selectedTab = 4
            })
            .tabItem {
                Label("Khám phá", systemImage: "music.note.house.fill")
            }
            .tag(0)
            
            SearchView(repository: repository, onAlbumSelected: { album in
                activeDetailAlbumId = album.id
            }, onSongSelected: { song in
                playerManager.play([song], startIndex: 0)
            })
            .tabItem {
                Label("Tìm kiếm", systemImage: "magnifyingglass")
            }
            .tag(1)
            
            LibraryView(repository: repository, onArtistSelected: { artist in
                activeDetailArtist = artist
            }, onPlaylistSelected: { playlist in
                activeDetailPlaylistId = playlist.id
            }, onSongSelected: { song in
                playerManager.play([song], startIndex: 0)
            }, initialTab: 1)
            .tabItem {
                Image("brand_logo")
                Text("Danh sách")
            }
            .tag(2)
            
            LibraryView(repository: repository, onArtistSelected: { artist in
                activeDetailArtist = artist
            }, onPlaylistSelected: { playlist in
                activeDetailPlaylistId = playlist.id
            }, onSongSelected: { song in
                playerManager.play([song], startIndex: 0)
            }, initialTab: 0)
            .tabItem {
                Label("Thư viện", systemImage: "music.note.list")
            }
            .tag(3)
            
            SettingsView(repository: repository, onLogout: {
                // Done inside repository, state will auto-trigger LoginView
            })
            .tabItem {
                Label("Cài đặt", systemImage: "gearshape.fill")
            }
            .tag(4)
        }
        .accentColor(.appPrimary)
    }
    
    // ── IPAD SIDEBAR LAYOUT ──────────────────────────────────────────────────
    
    private var tabletSplitLayout: some View {
        NavigationView {
            List {
                NavigationLink(
                    destination: HomeView(repository: repository, onAlbumSelected: { id in
                        activeDetailAlbumId = id
                    }, onNavigateToSettings: {
                        sidebarSelection = "settings"
                    }),
                    tag: "home",
                    selection: $sidebarSelection
                ) {
                    Label("Khám phá", systemImage: "music.note.house.fill")
                }
                
                NavigationLink(
                    destination: SearchView(repository: repository, onAlbumSelected: { album in
                        activeDetailAlbumId = album.id
                    }, onSongSelected: { song in
                        playerManager.play([song], startIndex: 0)
                    }),
                    tag: "search",
                    selection: $sidebarSelection
                ) {
                    Label("Tìm kiếm", systemImage: "magnifyingglass")
                }
                
                NavigationLink(
                    destination: LibraryView(repository: repository, onArtistSelected: { artist in
                        activeDetailArtist = artist
                    }, onPlaylistSelected: { playlist in
                        activeDetailPlaylistId = playlist.id
                    }, onSongSelected: { song in
                        playerManager.play([song], startIndex: 0)
                    }),
                    tag: "library",
                    selection: $sidebarSelection
                ) {
                    Label("Thư viện", systemImage: "music.note.list")
                }
                
                NavigationLink(
                    destination: SettingsView(repository: repository, onLogout: {}),
                    tag: "settings",
                    selection: $sidebarSelection
                ) {
                    Label("Cài đặt", systemImage: "gearshape.fill")
                }
            }
            .listStyle(SidebarListStyle())
            .navigationTitle("Vi2Play")
            
            // Default placeholder in detail view
            HomeView(repository: repository, onAlbumSelected: { id in
                activeDetailAlbumId = id
            }, onNavigateToSettings: {
                sidebarSelection = "settings"
            })
        }
        .accentColor(.appPrimary)
    }
    
    // ── DETAIL SHEET CONTROLLER ──────────────────────────────────────────────
    
    private var showingDetailBinding: Binding<Bool> {
        Binding(
            get: {
                activeDetailAlbumId != nil || activeDetailArtist != nil || activeDetailPlaylistId != nil
            },
            set: { newValue in
                if !newValue {
                    activeDetailAlbumId = nil
                    activeDetailArtist = nil
                    activeDetailPlaylistId = nil
                }
            }
        )
    }
    
    @ViewBuilder
    private var detailOverlayContainer: some View {
        NavigationView {
            ZStack(alignment: .bottom) {
                Group {
                    if let albumId = activeDetailAlbumId {
                        AlbumDetailView(albumId: albumId, repository: repository, playerManager: playerManager)
                    } else if let artist = activeDetailArtist {
                        ArtistDetailView(artist: artist, repository: repository) { nextAlbumId in
                            // Drill down from Artist to Album!
                            activeDetailAlbumId = nextAlbumId
                            activeDetailArtist = nil
                        }
                    } else if let playlistId = activeDetailPlaylistId {
                        PlaylistDetailView(playlistId: playlistId, repository: repository, playerManager: playerManager)
                    } else {
                        EmptyView()
                    }
                }
                
                if playerManager.currentSong != nil {
                    MiniPlayerView(playerManager: playerManager, repository: repository) {
                        showingNowPlaying = true
                    }
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Đóng") {
                        activeDetailAlbumId = nil
                        activeDetailArtist = nil
                        activeDetailPlaylistId = nil
                    }
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.appPrimary)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}
