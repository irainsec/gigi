import Foundation

public struct UserProfile: Codable, Identifiable {
    public var id: String
    public var name: String
    public var partnerId: String?
    public var partnerName: String?
    public var avatarUrl: String?
    public var tier: String
    
    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case name
        case partnerId
        case partnerName
        case avatarUrl
        case tier
    }
}

public struct WSMessage: Codable {
    public var type: String
    public var payload: [String: String]?
    
    public init(type: String, payload: [String: String]? = nil) {
        self.type = type
        self.payload = payload
    }
}

public struct HeartbeatState: Codable {
    public var partnerName: String
    public var isBeating: Bool
    public var bpm: Int
}
