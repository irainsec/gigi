import Foundation
import Combine

public class MainViewModel: ObservableObject {
    @Published public var selectedNavIndex: Int = 1 // 0: Reminders, 1: Sweet Corner, 2: Music
    @Published public var isNavVisible: Bool = true
    @Published public var userProfile: UserProfile?
    
    public init() {}
}
