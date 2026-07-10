import SwiftUI

struct MiniPlayerView: View {
    @ObservedObject var playerManager: PlayerManager
    @ObservedObject var repository: SubsonicRepository
    var onTap: () -> Void
    
    var body: some View {
        guard let song = playerManager.currentSong else {
            return AnyView(EmptyView())
        }
        
        return AnyView(
            VStack(spacing: 0) {
                // Highly Premium thin progress bar at the very top edge!
                if playerManager.duration > 0 {
                    GeometryReader { geo in
                        let width = geo.size.width * CGFloat(playerManager.currentTime / playerManager.duration)
                        Rectangle()
                            .fill(Color.appPrimary)
                            .frame(width: max(0, min(width, geo.size.width)), height: 2.5)
                    }
                    .frame(height: 2.5)
                }
                
                // Content Bar
                HStack(spacing: 12) {
                    // Tap area containing art + titles
                    Button(action: onTap) {
                        HStack(spacing: 12) {
                            // Cover art
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
                            .frame(width: 44, height: 44)
                            .cornerRadius(6)
                            .shadow(color: Color.black.opacity(0.2), radius: 4, x: 0, y: 2)
                            
                            // Title & artist
                            VStack(alignment: .leading, spacing: 2) {
                                Text(song.title)
                                    .font(.system(size: 13.5, weight: .bold))
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                                
                                Text(song.artist ?? "Không rõ Nghệ sĩ")
                                    .font(.system(size: 11.5, weight: .medium))
                                    .foregroundColor(.gray)
                                    .lineLimit(1)
                            }
                        }
                    }
                    .buttonStyle(PlainButtonStyle())
                    
                    Spacer()
                    
                    // Controls
                    HStack(spacing: 16) {
                        // Play / Pause button
                        Button(action: { playerManager.togglePlay() }) {
                            Image(systemName: playerManager.isPlaying ? "pause.fill" : "play.fill")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(width: 36, height: 36)
                        }
                        
                        // Next button
                        Button(action: { playerManager.playNext() }) {
                            Image(systemName: "forward.fill")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(width: 36, height: 36)
                        }
                    }
                    .padding(.trailing, 4)
                }
                .padding(.horizontal, 12)
                .frame(height: 60)
            }
            .background(
                Color.appSurface.opacity(0.85)
                    .background(.ultraThinMaterial)
                    .shadow(color: Color.black.opacity(0.4), radius: 10, x: 0, y: -4)
            )
            .overlay(
                VStack {
                    Divider().background(Color.white.opacity(0.06))
                    Spacer()
                }
            )
        )
    }
}
