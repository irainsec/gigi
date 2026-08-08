import Foundation

public struct SongItem: Codable, Identifiable {
    public var id: String
    public var title: String
    public var artist: String
    public var album: String
    public var duration: TimeInterval
    public var artworkUrl: String?
    
    public init(id: String, title: String, artist: String, album: String, duration: TimeInterval, artworkUrl: String? = nil) {
        self.id = id
        self.title = title
        self.artist = artist
        self.album = album
        self.duration = duration
        self.artworkUrl = artworkUrl
    }
}
