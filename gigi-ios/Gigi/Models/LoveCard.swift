import Foundation

public struct LoveCard: Codable, Identifiable {
    public var id: String
    public var senderName: String
    public var message: String
    public var theme: String
    public var audioUrl: String?
    public var createdAt: Date
    public var sparkles: Int
    
    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case senderName
        case message
        case theme
        case audioUrl
        case createdAt
        case sparkles
    }
}
