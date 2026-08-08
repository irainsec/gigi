import Foundation
import AVFoundation
import Combine

public class MusicViewModel: ObservableObject {
    @Published public var isPlaying: Bool = false
    @Published public var currentSong: SongItem?
    @Published public var queue: [SongItem] = []
    @Published public var isAlbumBrowserOpen: Bool = false
    @Published public var isMusicSettingsOpen: Bool = false
    
    private var audioPlayer: AVPlayer?
    
    public init() {
        setupDemoSongs()
    }
    
    private func setupDemoSongs() {
        let sample = [
            SongItem(id: "1", title: "Kabhi Kabhi Aditi", artist: "Jaane Tu... Ya Jaane Na", album: "Jaane Tu", duration: 220),
            SongItem(id: "2", title: "Tum Se Hi", artist: "Pritam, Mohit Chauhan", album: "Jab We Met", duration: 320),
            SongItem(id: "3", title: "Tera Ban Jaunga", artist: "Akhil Sachdeva, Tulsi Kumar", album: "Kabir Singh", duration: 236)
        ]
        self.queue = sample
        self.currentSong = sample.first
    }
    
    public func togglePlayback() {
        isPlaying.toggle()
    }
    
    public func setAlbumBrowserOpen(_ open: Bool) {
        isAlbumBrowserOpen = open
    }
    
    public func setMusicSettingsOpen(_ open: Bool) {
        isMusicSettingsOpen = open
    }
}
