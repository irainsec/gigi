import Foundation

public class APIClient {
    public static let shared = APIClient()
    public var baseURL = "https://gigi.iamanraj.com"
    
    private init() {}
    
    public func fetchLoveCards() async throws -> [LoveCard] {
        guard let url = URL(string: "\(baseURL)/api/love-cards") else { return [] }
        let (data, _) = try await URLSession.shared.data(from: url)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode([LoveCard].self, from: data)
    }
    
    public func fetchReminders() async throws -> [ReminderItem] {
        guard let url = URL(string: "\(baseURL)/api/reminders") else { return [] }
        let (data, _) = try await URLSession.shared.data(from: url)
        return try JSONDecoder().decode([ReminderItem].self, from: data)
    }
}
