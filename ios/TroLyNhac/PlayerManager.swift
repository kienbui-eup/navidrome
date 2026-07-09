import Foundation
import AVFoundation
import MediaPlayer
import Combine

public class PlayerManager: ObservableObject {
    @Published public var queue: [Song] = []
    @Published public var currentIndex: Int = -1
    @Published public var isPlaying: Bool = false
    @Published public var duration: Double = 0.0
    @Published public var currentTime: Double = 0.0
    @Published public var volume: Float = 1.0
    
    @Published public var activeFilter: String = "Bypass" {
        didSet {
            UserDefaults.standard.set(activeFilter, forKey: "activeFilter")
        }
    }
    @Published public var activeDither: String = "None" {
        didSet {
            UserDefaults.standard.set(activeDither, forKey: "activeDither")
        }
    }
    @Published public var activeModulator: String = "PCM (Bit-Perfect)" {
        didSet {
            UserDefaults.standard.set(activeModulator, forKey: "activeModulator")
        }
    }
    
    public var currentSong: Song? {
        guard currentIndex >= 0 && currentIndex < queue.count else { return nil }
        return queue[currentIndex]
    }
    
    private var player: AVPlayer?
    private var repository: SubsonicRepository
    private var timeObserverToken: Any?
    private var cancellables = Set<AnyCancellable>()
    private var currentArtImage: UIImage? = nil
    
    public init(repository: SubsonicRepository) {
        self.repository = repository
        
        // Load persisted DSP configurations
        self.activeFilter = UserDefaults.standard.string(forKey: "activeFilter") ?? "Bypass"
        self.activeDither = UserDefaults.standard.string(forKey: "activeDither") ?? "None"
        self.activeModulator = UserDefaults.standard.string(forKey: "activeModulator") ?? "PCM (Bit-Perfect)"
        
        setupAudioSession()
        setupRemoteCommandCenter()
    }
    
    deinit {
        removeTimeObserver()
        NotificationCenter.default.removeObserver(self)
    }
    
    private func setupAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.allowAirPlay, .defaultToSpeaker])
            try session.setActive(true)
        } catch {
            print("Failed to set audio session category: \(error)")
        }
    }
    
    // ── PLAYBACK COMMANDS ──────────────────────────────────────────────────
    
    public func play(_ songs: [Song], startIndex: Int = 0) {
        guard !songs.isEmpty else { return }
        self.queue = songs
        self.currentIndex = (startIndex >= 0 && startIndex < songs.count) ? startIndex : 0
        playCurrentSong()
    }
    
    private func playCurrentSong() {
        guard let song = currentSong else { return }
        
        removeTimeObserver()
        player?.pause()
        
        // Check if we need to request flac transcode for DSD files
        let format = repository.isServerTranscodeSuffix(song.suffix) ? "flac" : nil
        guard let url = repository.streamUrl(songId: song.id, format: format) else { return }
        
        let playerItem = AVPlayerItem(url: url)
        
        if player == nil {
            player = AVPlayer(playerItem: playerItem)
            player?.automaticallyWaitsToMinimizeStalling = true
        } else {
            player?.replaceCurrentItem(with: playerItem)
        }
        
        // Listen for track ending
        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemDidPlayToEndTime, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(playerItemDidReachEnd), name: .AVPlayerItemDidPlayToEndTime, object: playerItem)
        
        // Reset state
        self.currentTime = 0.0
        // Use Subsonic duration if available, else wait for AVPlayer item duration
        self.duration = Double(song.duration ?? 0)
        self.isPlaying = true
        self.currentArtImage = nil
        
        player?.play()
        addTimeObserver()
        updateNowPlayingInfo()
        
        // Trigger background art prefetch
        if let coverArtId = song.coverArt, let artUrl = repository.coverArtUrl(coverArtId: coverArtId, size: 600) {
            fetchArtworkImage(url: artUrl) { [weak self] image in
                guard let self = self, self.currentSong?.id == song.id else { return }
                self.currentArtImage = image
                self.updateNowPlayingInfo()
            }
        }
    }
    
    public func togglePlay() {
        guard player != nil else {
            // If we have a queue but player is not playing, play first song
            if !queue.isEmpty {
                if currentIndex < 0 { currentIndex = 0 }
                playCurrentSong()
            }
            return
        }
        
        if isPlaying {
            player?.pause()
            isPlaying = false
        } else {
            player?.play()
            isPlaying = true
        }
        updateNowPlayingInfo()
    }
    
    public func playNext() {
        guard !queue.isEmpty else { return }
        if currentIndex + 1 < queue.count {
            currentIndex += 1
            playCurrentSong()
        } else {
            // Loop back to start or stop
            currentIndex = 0
            playCurrentSong()
        }
    }
    
    public func playPrevious() {
        // If we are more than 3 seconds into the song, restart it instead of going back
        if currentTime > 3.0 {
            seek(to: 0.0)
            return
        }
        
        guard !queue.isEmpty else { return }
        if currentIndex - 1 >= 0 {
            currentIndex -= 1
            playCurrentSong()
        } else {
            // Go to end
            currentIndex = queue.count - 1
            playCurrentSong()
        }
    }
    
    public func seek(to seconds: Double) {
        guard let player = player else { return }
        let targetTime = CMTime(seconds: seconds, preferredTimescale: 1000)
        player.seek(to: targetTime, toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] finished in
            if finished {
                DispatchQueue.main.async {
                    self?.currentTime = seconds
                    self?.updateNowPlayingInfo()
                }
            }
        }
    }
    
    public func skipToQueueItem(index: Int) {
        guard index >= 0 && index < queue.count else { return }
        currentIndex = index
        playCurrentSong()
    }
    
    public func removeQueueItem(index: Int) {
        guard index >= 0 && index < queue.count else { return }
        queue.remove(at: index)
        
        if queue.isEmpty {
            player?.pause()
            player = nil
            currentIndex = -1
            isPlaying = false
            duration = 0.0
            currentTime = 0.0
            updateNowPlayingInfo()
        } else if currentIndex == index {
            // Playing song was removed, skip to next or previous
            if currentIndex >= queue.count {
                currentIndex = queue.count - 1
            }
            playCurrentSong()
        } else if currentIndex > index {
            currentIndex -= 1
        }
    }
    
    @objc private func playerItemDidReachEnd(notification: Notification) {
        DispatchQueue.main.async { [weak self] in
            self?.playNext()
        }
    }
    
    // ── OBSERVERS ────────────────────────────────────────────────────────────
    
    private func addTimeObserver() {
        guard let player = player else { return }
        let interval = CMTime(seconds: 0.5, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        timeObserverToken = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self = self else { return }
            let current = time.seconds
            self.currentTime = current
            
            // Sync duration if not set or CMTime reports a better duration
            if let durationCM = player.currentItem?.duration, durationCM.isNumeric {
                let dur = durationCM.seconds
                if dur > 0 && abs(self.duration - dur) > 2.0 {
                    self.duration = dur
                }
            }
        }
    }
    
    private func removeTimeObserver() {
        if let token = timeObserverToken {
            player?.removeTimeObserver(token)
            timeObserverToken = nil
        }
    }
    
    // ── REMOTE COMMANDS & LOCK SCREEN ────────────────────────────────────────
    
    private func setupRemoteCommandCenter() {
        let commandCenter = MPRemoteCommandCenter.shared()
        
        commandCenter.playCommand.isEnabled = true
        commandCenter.playCommand.addTarget { [weak self] event in
            self?.togglePlay()
            return .success
        }
        
        commandCenter.pauseCommand.isEnabled = true
        commandCenter.pauseCommand.addTarget { [weak self] event in
            self?.togglePlay()
            return .success
        }
        
        commandCenter.nextTrackCommand.isEnabled = true
        commandCenter.nextTrackCommand.addTarget { [weak self] event in
            self?.playNext()
            return .success
        }
        
        commandCenter.previousTrackCommand.isEnabled = true
        commandCenter.previousTrackCommand.addTarget { [weak self] event in
            self?.playPrevious()
            return .success
        }
        
        commandCenter.changePlaybackPositionCommand.isEnabled = true
        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            if let positionEvent = event as? MPChangePlaybackPositionCommandEvent {
                self?.seek(to: positionEvent.positionTime)
                return .success
            }
            return .commandFailed
        }
    }
    
    private func updateNowPlayingInfo() {
        guard let song = currentSong else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }
        
        var nowPlayingInfo = [String: Any]()
        nowPlayingInfo[MPMediaItemPropertyTitle] = song.title
        nowPlayingInfo[MPMediaItemPropertyArtist] = song.artist ?? "Không rõ Nghệ sĩ"
        nowPlayingInfo[MPMediaItemPropertyAlbumTitle] = song.album ?? "Không rõ Album"
        nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
        nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = duration > 0 ? duration : Double(song.duration ?? 0)
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? 1.0 : 0.0
        
        if let image = currentArtImage {
            let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            nowPlayingInfo[MPMediaItemPropertyArtwork] = artwork
        }
        
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
    }
    
    private func fetchArtworkImage(url: URL, completion: @escaping (UIImage?) -> Void) {
        URLSession.shared.dataTask(with: url) { data, _, _ in
            guard let data = data, let image = UIImage(data: data) else {
                completion(nil)
                return
            }
            DispatchQueue.main.async {
                completion(image)
            }
        }.resume()
    }
}
