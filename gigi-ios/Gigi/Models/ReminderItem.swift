import Foundation

public struct ReminderItem: Codable, Identifiable {
    public var id: String
    public var title: String
    public var timeString: String
    public var isCompleted: Bool
    public var category: String
    
    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case title
        case timeString
        case isCompleted
        case category
    }
}
